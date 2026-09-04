package com.gtv2stream;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Clean-room behavioral capture of Google TV launcher recommendations. Only click
 * and entity-window events are accepted; missing titles fail closed until the
 * launcher exposes its stable detail-title row.
 */
public final class TvRecommendationService extends AccessibilityService {
    private static final String TAG = "GTV2STREAM";
    private static final String PREFS = "gtv2stream";
    private static final String LAUNCHER_PACKAGE = "com.google.android.apps.tv.launcherx";
    private static final String DETAIL_ACTIVITY_SUFFIX = ".entity.EntityActivity";
    private static final String DETAIL_TITLE_ID =
            "com.google.android.apps.tv.launcherx:id/entity_details_title_row";
    private static final long TITLE_RETRY_DELAY_MS = 600L;
    private static final long DUPLICATE_WINDOW_MS = 2000L;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingTitleRetry;
    private String lastDispatchedTitle = "";
    private long lastDispatchedAt;


    @Override public void onServiceConnected() {
        Log.i(TAG, "Accessibility service connected for " + LAUNCHER_PACKAGE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null
                || !LAUNCHER_PACKAGE.contentEquals(event.getPackageName())) return;

        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            handleLauncherClick(event);
        } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && isEntityWindow(event.getClassName())) {
            handleEntityWindow();
        }
    }

    private void handleLauncherClick(AccessibilityEvent event) {
        cancelTitleRetry();
        String title = RecommendationTitleParser.fromEventText(event.getText());
        if (title.isEmpty()) {
            title = RecommendationTitleParser.fromDescription(toString(event.getContentDescription()));
        }
        if (!title.isEmpty()) {
            dispatchTitle(title);
        } else {
            scheduleTitleRetry();
        }
    }

    private void handleEntityWindow() {
        cancelTitleRetry();
        String title = titleFromDetailRoot();
        if (!title.isEmpty()) {
            dispatchTitle(title);
        } else {
            scheduleTitleRetry();
        }
    }

    /** Retries against a fresh root; no AccessibilityNodeInfo is retained across events. */
    private void scheduleTitleRetry() {
        cancelTitleRetry();
        pendingTitleRetry = () -> {
            pendingTitleRetry = null;
            String title = titleFromDetailRoot();
            if (!title.isEmpty()) dispatchTitle(title);
        };
        handler.postDelayed(pendingTitleRetry, TITLE_RETRY_DELAY_MS);
    }

    /** Uses the launcher's stable ID, then scans only that row's live subtree. */
    private String titleFromDetailRoot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        try {
            List<AccessibilityNodeInfo> rows = root.findAccessibilityNodeInfosByViewId(DETAIL_TITLE_ID);
            if (rows == null) return "";
            for (AccessibilityNodeInfo row : rows) {
                if (row == null) continue;
                try {
                    String title = titleFromDetailRow(row);
                    if (!title.isEmpty()) return title;
                } finally {
                    row.recycle();
                }
            }
            return "";
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not read entity title row: " + error.getMessage());
            return "";
        } finally {
            root.recycle();
        }
    }

    private String titleFromDetailRow(AccessibilityNodeInfo node) {
        String title = RecommendationTitleParser.fromDirectText(toString(node.getText()));
        if (!title.isEmpty()) return title;
        title = RecommendationTitleParser.fromDescription(toString(node.getContentDescription()));
        if (!title.isEmpty()) return title;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            try {
                title = titleFromDetailRow(child);
                if (!title.isEmpty()) return title;
            } finally {
                child.recycle();
            }
        }
        return "";
    }

    private void dispatchTitle(String title) {
        String cleaned = RecommendationTitleParser.fromDirectText(title);
        if (cleaned.isEmpty()) cleaned = RecommendationTitleParser.fromDescription(title);
        if (cleaned.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (TitleResultHelper.normalizedTitleMatches(lastDispatchedTitle, cleaned)
                && now - lastDispatchedAt < DUPLICATE_WINDOW_MS) return;
        lastDispatchedTitle = cleaned;
        lastDispatchedAt = now;
        Log.i(TAG, "Google TV recommendation title: " + cleaned);
        final String resolvedTitle = cleaned;
        worker.execute(() -> resolveAndOpen(resolvedTitle));
    }

    private void resolveAndOpen(String title) {
        String savedKey = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("tmdb_key", "").trim();
        String key = savedKey.isEmpty() ? BuildConfig.TMDB_API_KEY.trim() : savedKey;
        if (key.length() < 10) {
            Log.w(TAG, "TMDB key missing or too short; configure GTV2STREAM first");
            notifyUser(R.string.status_key_missing);
            return;
        }
        try {
            TitleMatch match = new TmdbClient(key).searchBest(title);
            if (match == null) {
                Log.i(TAG, "No TMDB movie or series match for: " + title);
                return;
            }
            Log.i(TAG, "TMDB match: " + match.title + " -> " + TitleResultHelper.nuvioUri(match));
            NuvioLauncher.open(this, match);
        } catch (TmdbClient.InvalidApiKeyException error) {
            Log.w(TAG, "TMDB rejected the configured key");
            notifyUser(R.string.key_rejected);
        } catch (Exception error) {
            Log.w(TAG, "Recommendation lookup failed: " + error.getMessage());
        }
    }

    private void notifyUser(int message) {
        handler.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private static boolean isEntityWindow(CharSequence className) {
        return className != null && className.toString().endsWith(DETAIL_ACTIVITY_SUFFIX);
    }

    private static String toString(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private void cancelTitleRetry() {
        if (pendingTitleRetry != null) {
            handler.removeCallbacks(pendingTitleRetry);
            pendingTitleRetry = null;
        }
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        cancelTitleRetry();
        worker.shutdownNow();
        super.onDestroy();
    }
}

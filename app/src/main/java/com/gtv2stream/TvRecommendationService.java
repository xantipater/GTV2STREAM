package com.gtv2stream;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Clean-room behavioral capture of Google TV launcher recommendations. Only click
 * and entity-window events are accepted; missing titles fail closed until the
 * launcher exposes its stable detail-title row. YouTube-marked payloads are routed
 * to the selected YouTube app; all other titles resolve through TMDB and open in
 * the selected film/series app.
 */
public final class TvRecommendationService extends AccessibilityService {
    private static final String TAG = "GTV2STREAM";
    private static final String LAUNCHER_PACKAGE = "com.google.android.apps.tv.launcherx";
    private static final String STOCK_YOUTUBE_PACKAGE = "com.google.android.youtube.tv";
    private static final String HOME_ACTIVITY_SUFFIX = ".home.HomeActivity";
    private static final String DETAIL_ACTIVITY_SUFFIX = ".entity.EntityActivity";
    private static final String DETAIL_TITLE_ID =
            "com.google.android.apps.tv.launcherx:id/entity_details_title_row";
    private static final long TITLE_EARLY_RETRY_DELAY_MS = 250L;
    private static final long TITLE_FINAL_RETRY_DELAY_MS = 600L;
    private static final long DUPLICATE_WINDOW_MS = 2000L;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gtv2stream-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingTitleRetry;
    private Runnable pendingHeroCapture;
    private RecommendationTitleParser.Source focusedHeroSource =
            RecommendationTitleParser.Source.NONE;
    private long focusedHeroCapturedAt;
    private String lastDispatchedTitle = "";
    private boolean lastDispatchedYoutube;
    private long lastDispatchedAt;

    /**
     * Periodic connection heartbeat: Settings treats the service as Ready only
     * while the heartbeat is fresh, so a force-stopped or vendor-blocked process
     * can never leave a stale "connected" state behind.
     */
    private static final long HEARTBEAT_INTERVAL_MS = 15000L;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit()
                    .putLong(AppPrefs.SERVICE_CONNECTED_AT, System.currentTimeMillis()).apply();
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());


    @Override public void onServiceConnected() {
        heartbeat.run();
        // A service rebind can occur while a launcher card is already focused,
        // in which case Android sends no new focus event for that card.
        scheduleHeroCapture(100L, 12);
        Log.i(TAG, "Accessibility service connected for " + LAUNCHER_PACKAGE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        int type = event.getEventType();
        boolean launcherEvent = LAUNCHER_PACKAGE.contentEquals(event.getPackageName());
        if (!launcherEvent) {
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && STOCK_YOUTUBE_PACKAGE.contentEquals(event.getPackageName())
                    && hasFreshFocusedHeroSource()) {
                RecommendationTitleParser.Source source = focusedHeroSource;
                clearFocusedHeroSource();
                Log.i(TAG, "Stock YouTube opened after recommendation; redirecting cached title");
                dispatchTitle(source.title, true);
                return;
            }
            return;
        }
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            handleLauncherClick(event);
        } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && isEntityWindow(event.getClassName())) {
            handleEntityWindow();
        } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && isHomeWindow(event.getClassName())) {
            // The launcher's already-focused card may not emit a new focus event
            // when Home opens. Capture its hero payload before a quick click can
            // hand the recommendation to stock YouTube.
            scheduleHeroCapture(100L, 12);
        } else if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                || type == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            handleLauncherFocus(event);
        }
    }

    private void handleLauncherFocus(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            try {
                android.graphics.Rect bounds = new android.graphics.Rect();
                source.getBoundsInScreen(bounds);
                scheduleFocusedHeroCapture(bounds);
            } finally {
                source.recycle();
            }
        } else {
            clearFocusedHeroSource();
        }
    }

    private void scheduleFocusedHeroCapture(android.graphics.Rect bounds) {
        // Recommendation cards on this 1080p launcher occupy the lower hero row.
        if (bounds.top < 650 || bounds.top > 950 || bounds.height() < 100) {
            clearFocusedHeroSource();
            return;
        }
        scheduleHeroCapture(100L, 12);
    }

    private void scheduleHeroCapture(long delayMs, int attemptsRemaining) {
        if (pendingHeroCapture != null) handler.removeCallbacks(pendingHeroCapture);
        pendingHeroCapture = () -> {
            pendingHeroCapture = null;
            RecommendationTitleParser.Source source = sourceFromWindowPayloads();
            focusedHeroSource = source;
            focusedHeroCapturedAt = source.isEmpty() ? 0 : System.currentTimeMillis();
            if (!source.isEmpty()) {
                Log.i(TAG, "Cached focused YouTube title: " + source.title);
            } else if (attemptsRemaining > 1) {
                // Hero metadata arrives after the focus event. Poll briefly so a
                // click made as soon as it becomes visible still has the title.
                scheduleHeroCapture(100L, attemptsRemaining - 1);
            }
        };
        handler.postDelayed(pendingHeroCapture, delayMs);
    }

    private void clearFocusedHeroSource() {
        focusedHeroSource = RecommendationTitleParser.Source.NONE;
        focusedHeroCapturedAt = 0;
    }

    private boolean hasFreshFocusedHeroSource() {
        return !focusedHeroSource.isEmpty()
                && System.currentTimeMillis() - focusedHeroCapturedAt < 15000L;
    }

    private void handleLauncherClick(AccessibilityEvent event) {
        cancelTitleRetry();
        RecommendationTitleParser.Source source =
                RecommendationTitleParser.fromEventTextSource(event.getText());
        if (source.isEmpty()) {
            source = RecommendationTitleParser.fromDescriptionSource(
                    toString(event.getContentDescription()));
        }
        if (source.isEmpty()) {
            // Some cards (notably YouTube) emit click events with no text or
            // description; the payload lives on the card container, an ancestor
            // of the clicked view.
            source = sourceFromClickedNode(event);
        }
        if (!source.isEmpty()) {
            dispatchTitle(source.title, source.youtube);
        } else {
            logRejectedPayload(event);
            source = sourceFromWindowPayloads();
            if (source.isEmpty() && hasFreshFocusedHeroSource()) {
                source = focusedHeroSource;
            }
            if (!source.isEmpty()) {
                clearFocusedHeroSource();
                dispatchTitle(source.title, source.youtube);
                return;
            }
            scheduleTitleRetry();
        }
    }

    /**
     * Extracts a card payload around the clicked view. Some launcher builds host
     * card content in a sibling branch of the accessibility tree, so a plain
     * subtree scan of the clicked view only sees the column label. The fix scans
     * the active window for text nodes whose screen bounds intersect the clicked
     * card's region, feeds those to the classifier, and falls back to the
     * ancestor chain. Nodes are recycled immediately.
     */
    private RecommendationTitleParser.Source sourceFromClickedNode(AccessibilityEvent event) {
        AccessibilityNodeInfo owned = event.getSource();
        if (owned == null) return RecommendationTitleParser.Source.NONE;
        try {
            android.graphics.Rect clickBounds = new android.graphics.Rect();
            owned.getBoundsInScreen(clickBounds);
            List<CharSequence> collected = new ArrayList<>();
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                try {
                    collectNearbyPayloadTexts(root, 0, clickBounds, collected);
                } finally {
                    root.recycle();
                }
            }
            RecommendationTitleParser.Source found =
                    RecommendationTitleParser.fromEventTextSource(collected);
            if (!found.isEmpty()) return found;
            if (!collected.isEmpty()) {
                Log.i(TAG, "Card region payload: " + collected);
            }
            return cardSourceFromAncestors(owned);
        } catch (RuntimeException walkError) {
            Log.w(TAG, "Could not read clicked card payload: " + walkError.getMessage());
            return RecommendationTitleParser.Source.NONE;
        } finally {
            owned.recycle();
        }
    }

    /** Depth- and count-bounded scan collecting payloads near the clicked card's bounds. */
    private void collectNearbyPayloadTexts(AccessibilityNodeInfo node, int depth,
            android.graphics.Rect clickBounds, List<CharSequence> out) {
        if (node == null || depth > 7 || out.size() > 60) return;
        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        android.graphics.Rect region = new android.graphics.Rect(clickBounds);
        region.inset(-60, -60);
        if (android.graphics.Rect.intersects(bounds, region)) {
            String text = toString(node.getText());
            if (!text.isEmpty()) out.add(text);
            String description = toString(node.getContentDescription());
            if (!description.isEmpty()) out.add(description);
        }
        for (int index = 0; index < node.getChildCount() && out.size() <= 60; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            try {
                collectNearbyPayloadTexts(child, depth + 1, clickBounds, out);
            } finally {
                child.recycle();
            }
        }
    }

    /**
     * Walks the clicked view's ancestor chain (bounded) looking for the card's
     * rich content description or text. Nodes are recycled immediately.
     */
    private RecommendationTitleParser.Source cardSourceFromAncestors(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo owned = root;
        try {
            for (int depth = 0; depth < 8; depth++) {
                RecommendationTitleParser.Source found = cardSourceFromNode(owned);
                if (!found.isEmpty()) return found;
                AccessibilityNodeInfo parent = owned.getParent();
                owned.recycle();
                owned = parent;
                if (owned == null) break;
            }
            return RecommendationTitleParser.Source.NONE;
        } catch (RuntimeException walkError) {
            Log.w(TAG, "Could not read clicked card payload: " + walkError.getMessage());
            return RecommendationTitleParser.Source.NONE;
        } finally {
            if (owned != null) owned.recycle();
        }
    }

    /** Tries the node's content description, text, and first child for a card payload. */
    private RecommendationTitleParser.Source cardSourceFromNode(AccessibilityNodeInfo node) {
        RecommendationTitleParser.Source found = RecommendationTitleParser.fromDescriptionSource(
                toString(node.getContentDescription()));
        if (found.isEmpty()) {
            found = RecommendationTitleParser.fromDirectTextSource(toString(node.getText()));
        }
        if (found.isEmpty() && node.getChildCount() > 0) {
            AccessibilityNodeInfo child = node.getChild(0);
            if (child != null) {
                try {
                    found = RecommendationTitleParser.fromDescriptionSource(
                            toString(child.getContentDescription()));
                } finally {
                    child.recycle();
                }
            }
        }
        return found;
    }

    private void handleEntityWindow() {
        cancelTitleRetry();
        String title = titleFromDetailRoot();
        if (!title.isEmpty()) {
            // Entity detail windows carry no provider marker, so they always follow
            // the film/series path; YouTube routing is click-payload driven.
            dispatchTitle(title, false);
        } else {
            scheduleTitleRetry();
        }
    }

    /** Retries against a fresh root; no AccessibilityNodeInfo is retained across events. */
    private void scheduleTitleRetry() {
        cancelTitleRetry();
        scheduleTitleRetry(TITLE_EARLY_RETRY_DELAY_MS, true);
    }

    private void scheduleTitleRetry(long delayMs, boolean allowFinalRetry) {
        pendingTitleRetry = () -> {
            pendingTitleRetry = null;
            String title = titleFromDetailRoot();
            if (!title.isEmpty()) {
                dispatchTitle(title, false);
            } else if (allowFinalRetry) {
                scheduleTitleRetry(
                        TITLE_FINAL_RETRY_DELAY_MS - TITLE_EARLY_RETRY_DELAY_MS, false);
            }
        };
        handler.postDelayed(pendingTitleRetry, delayMs);
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

    private void dispatchTitle(String title, boolean youtube) {
        String cleaned = youtube
                ? RecommendationTitleParser.youtubeSource(title).title
                : RecommendationTitleParser.fromDirectText(title);
        if (cleaned.isEmpty() && !youtube) {
            cleaned = RecommendationTitleParser.fromDescription(title);
        }
        if (cleaned.isEmpty()) return;
        if (matchesInstalledAppLabel(cleaned)) {
            // Launcher app tiles read like single-word titles; fail closed.
            Log.i(TAG, "Rejected launcher app tile label: " + cleaned);
            return;
        }

        long now = System.currentTimeMillis();
        if (TitleResultHelper.normalizedTitleMatches(lastDispatchedTitle, cleaned)
                && youtube == lastDispatchedYoutube
                && now - lastDispatchedAt < DUPLICATE_WINDOW_MS) return;
        lastDispatchedTitle = cleaned;
        lastDispatchedYoutube = youtube;
        lastDispatchedAt = now;
        Log.i(TAG, "Google TV recommendation title: " + cleaned
                + (youtube ? " (YouTube)" : ""));
        final String resolvedTitle = cleaned;
        worker.execute(() -> resolveAndOpen(resolvedTitle, youtube));
    }

    private void resolveAndOpen(String title, boolean youtube) {
        if (youtube) {
            // YouTube cards search by title; no TMDB lookup or key is needed.
            if (YouTubeLauncher.open(this, title)) {
                RedirectBadge.show(this);
            }
            return;
        }

        String key = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
                .getString(AppPrefs.TMDB_KEY, "").trim();
        if (key.length() < 10) {
            Log.w(TAG, "TMDB key missing or too short; configure GTV2STREAM first");
            notifyUser(R.string.status_key_missing);
            return;
        }
        try {
            boolean preferStremio = AppPrefs.MOVIES_STREMIO.equals(AppPrefs.moviesTarget(this));
            // Repeated selections reuse the recent match instead of re-querying TMDB.
            TitleMatch match = MatchCache.get(title);
            if (match == null) {
                match = new TmdbClient(key).searchBest(title);
                if (match == null) {
                    Log.i(TAG, "No TMDB movie or series match for: " + title);
                    return;
                }
                MatchCache.put(title, match);
            } else {
                Log.i(TAG, "Cached TMDB match reused for: " + title);
            }
            boolean opened = preferStremio
                    ? StremioLauncher.open(this, match)
                    : NuvioLauncher.open(this, match);
            Log.i(TAG, "TMDB match: " + match.title + " -> "
                    + (preferStremio
                            ? TitleResultHelper.stremioUri(match)
                            : TitleResultHelper.nuvioUri(match)));
            if (opened) {
                RedirectBadge.show(this);
            }
        } catch (TmdbClient.InvalidApiKeyException error) {
            Log.w(TAG, "TMDB rejected the configured key");
            notifyUser(R.string.key_rejected);
        } catch (Exception error) {
            Log.w(TAG, "Recommendation lookup failed: " + error.getMessage());
        }
    }

    private void collectWindowTexts(AccessibilityWindowInfo window, List<String> out) {
        AccessibilityNodeInfo root = window.getRoot();
        if (root == null) return;
        try {
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            collectNodeTexts(root, 0, seen);
            out.addAll(seen);
        } finally {
            root.recycle();
        }
    }

    private void collectNodeTexts(AccessibilityNodeInfo node, int depth,
            java.util.LinkedHashSet<String> out) {
        if (node == null || depth > 12 || out.size() > 100) return;
        String text = toString(node.getText());
        if (!text.isEmpty() && out.size() <= 100) {
            out.add((text.length() > 200 ? text.substring(0, 300) : text)
                    + "@y=" + boundsTop(node));
        }
        String description = toString(node.getContentDescription());
        if (!description.isEmpty() && out.size() <= 100) {
            out.add("[desc]" + description + "@y=" + boundsTop(node));
        }
        for (int index = 0; index < node.getChildCount() && out.size() <= 100; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            try {
                collectNodeTexts(child, depth + 1, out);
            } finally {
                child.recycle();
            }
        }
    }

    private int boundsTop(AccessibilityNodeInfo node) {
        try {
            android.graphics.Rect bounds = new android.graphics.Rect();
            node.getBoundsInScreen(bounds);
            return bounds.top;
        } catch (RuntimeException error) {
            return -1;
        }
    }

    /** YouTube cards expose their title and provider in the hero panel. */
    private RecommendationTitleParser.Source sourceFromWindowPayloads() {
        List<String> entries = new ArrayList<>();
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return RecommendationTitleParser.Source.NONE;
        try {
            for (AccessibilityWindowInfo window : windows) {
                collectWindowTexts(window, entries);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not read window payload: " + error.getMessage());
            return RecommendationTitleParser.Source.NONE;
        }

        int youtubeTop = -1;
        for (String entry : entries) {
            String payload = entry;
            if (payload.startsWith("[desc]")) payload = payload.substring(6);
            int yMarker = payload.lastIndexOf("@y=");
            if (yMarker < 0) continue;
            int top = parsePayloadTop(payload, yMarker);
            payload = payload.substring(0, yMarker).trim();
            // Ignore the YouTube app tile at y~1000 and unrelated lower rows.
            if (top >= 300 && top < 650 && "youtube".equalsIgnoreCase(payload)) youtubeTop = top;
        }
        if (youtubeTop < 0) return RecommendationTitleParser.Source.NONE;

        String heroTitle = "";
        int heroTitleTop = -1;
        for (String entry : entries) {
            if (entry.startsWith("[desc]")) continue;
            int yMarker = entry.lastIndexOf("@y=");
            if (yMarker < 0) continue;
            int top = parsePayloadTop(entry, yMarker);
            if (top < 150 || top >= youtubeTop || top <= heroTitleTop) continue;
            String payload = entry.substring(0, yMarker).trim();
            RecommendationTitleParser.Source found = RecommendationTitleParser.youtubeSource(payload);
            if (!found.isEmpty()) {
                heroTitle = found.title;
                heroTitleTop = top;
            }
        }
        return heroTitle.isEmpty()
                ? RecommendationTitleParser.Source.NONE
                : RecommendationTitleParser.youtubeSource(heroTitle);
    }

    private int parsePayloadTop(String payload, int yMarker) {
        try {
            return Integer.parseInt(payload.substring(yMarker + 3));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void logRejectedPayload(AccessibilityEvent event) {
        String text = String.join(" | ", event.getText() == null
                ? List.of() : event.getText());
        String description = toString(event.getContentDescription());
        String payload = !text.isBlank() ? text : description;
        String extra = "";
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            try {
                extra = " (view=" + source.getViewIdResourceName()
                        + ", class=" + source.getClassName() + ")";
            } finally {
                source.recycle();
            }
        }
        if (payload.isBlank()) {
            if (!extra.isBlank()) {
                Log.i(TAG, "No credible title in launcher payload:" + extra);
            }
            return;
        }
        if (payload.length() > 300) payload = payload.substring(0, 300) + "…";
        Log.i(TAG, "No credible title in launcher payload: " + payload + extra);
    }

    private void notifyUser(int message) {
        handler.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    /** True when the title exactly matches an installed app's display label. */
    private boolean matchesInstalledAppLabel(String title) {
        try {
            Set<String> labels = installedAppLabels;
            if (labels == null) {
                labels = new HashSet<>();
                PackageManager packageManager = getPackageManager();
                List<android.content.pm.ApplicationInfo> apps =
                        packageManager.getInstalledApplications(0);
                for (android.content.pm.ApplicationInfo app : apps) {
                    String label = String.valueOf(packageManager.getApplicationLabel(app)).trim();
                    if (!label.isEmpty()) labels.add(label.toLowerCase(java.util.Locale.US));
                }
                installedAppLabels = labels;
            }
            return labels.contains(title.toLowerCase(java.util.Locale.US));
        } catch (RuntimeException labelError) {
            return false;
        }
    }

    private volatile Set<String> installedAppLabels;

    private static boolean isEntityWindow(CharSequence className) {
        return className != null && className.toString().endsWith(DETAIL_ACTIVITY_SUFFIX);
    }

    private static boolean isHomeWindow(CharSequence className) {
        return className != null && className.toString().endsWith(HOME_ACTIVITY_SUFFIX);
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
        heartbeatHandler.removeCallbacks(heartbeat);
        if (pendingHeroCapture != null) handler.removeCallbacks(pendingHeroCapture);
        getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit()
                .remove(AppPrefs.SERVICE_CONNECTED_AT).apply();
        cancelTitleRetry();
        worker.shutdownNow();
        super.onDestroy();
    }
}

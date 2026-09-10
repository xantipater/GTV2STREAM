package com.gtv2stream;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

/**
 * Opens a YouTube title search in the configured target.
 */
public final class YouTubeLauncher {
    private static final String TAG = "GTV2STREAM";

    // SmartTube package order lives in LaunchPolicy.SMART_TUBE_ORDER (single
    // source of truth for the single-query resolution in resolveSmartTube).
    // The Cobalt package/activity/action contract lives in YouTubeTarget.
    static final String TIZENTUBE_COBALT = YouTubeTarget.COBALT_PACKAGE;

    /** Fixed smoke-test query, the same one the README documents for direct testing. */
    public static final String TEST_QUERY = "Big Buck Bunny";

    private YouTubeLauncher() { }

    /** Called from the service worker after the launcher title is captured. */
    public static boolean open(AccessibilityService service, String title) {
        String uri = TitleResultHelper.youtubeSearchUri(title);
        if (uri == null) {
            Log.w(TAG, "Empty YouTube search title; nothing to open");
            return false;
        }
        boolean tizentube = YouTubeTarget.isTizenTube(AppPrefs.youtubeTarget(service));
        if (tizentube) {
            return openCobalt(service, uri);
        }
        String cacheKey = LaunchPolicy.cacheKey(YouTubeTarget.SMARTTUBE, uri);
        LaunchSupport.Target target = LaunchSupport.cachedTarget(cacheKey);
        if (target == null) {
            target = resolveSmartTube(service, uri);
            if (target == null) {
                Log.w(TAG, "SmartTube is not installed");
                showMissingTarget(service, false);
                return false;
            }
            LaunchSupport.cacheTarget(cacheKey, target);
        }
        boolean opened = LaunchSupport.launchFresh(
                service, uri, target, target.packageName, "Fresh YouTube", cacheKey);
        if (!opened) {
            showMissingTarget(service, false);
        }
        return opened;
    }

    /**
     * Verified on-device Cobalt contract: MEDIA_PLAY_FROM_SEARCH with the
     * YouTube search URI at the explicit Cobalt component, flagged
     * NEW_TASK|MULTIPLE_TASK|EXCLUDE_FROM_RECENTS (no CLEAR_TASK). Live A/B
     * proved CLEAR_TASK loses the query on a warm Cobalt (home feed, empty
     * search) while these flags deliver it to the live instance via
     * onNewIntent. There is no SmartTube fallback: the explicit TizenTube
     * selection is honored, and a missing Cobalt fails visibly.
     */
    private static boolean openCobalt(Context context, String uri) {
        if (!isCobaltInstalled(context)) {
            Log.w(TAG, "TizenTube Cobalt is not installed");
            showMissingTarget(context, true);
            return false;
        }
        boolean opened = launchCobalt(context, uri);
        if (!opened) {
            showMissingTarget(context, true);
        }
        return opened;
    }

    /** True when the Cobalt package is installed (manifest queries allow the check). */
    @SuppressWarnings("deprecation")
    static boolean isCobaltInstalled(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(
                    YouTubeTarget.COBALT_PACKAGE, 0) != null;
        } catch (PackageManager.NameNotFoundException notInstalled) {
            return false;
        } catch (RuntimeException error) {
            Log.w(TAG, "Cobalt install check failed: " + error.getMessage());
            return false;
        }
    }

    /** The explicit Cobalt search component; no VIEW probing, no fallback. */
    static LaunchSupport.Target cobaltTarget() {
        return new LaunchSupport.Target(new ComponentName(
                YouTubeTarget.COBALT_PACKAGE, YouTubeTarget.COBALT_ACTIVITY));
    }

    /**
     * Launches the cached explicit Cobalt target with the MPFS contract and
     * the Cobalt warm-start flags (NEW_TASK|MULTIPLE_TASK|EXCLUDE_FROM_RECENTS,
     * never CLEAR_TASK: tearing the task down drops the query on a warm
     * Cobalt instead of delivering it via onNewIntent). Reports no UI itself;
     * callers surface failures.
     */
    private static boolean launchCobalt(Context context, String uri) {
        String cacheKey = LaunchPolicy.cacheKey(YouTubeTarget.TIZENTUBE, uri);
        LaunchSupport.Target target = LaunchSupport.cachedTarget(cacheKey);
        if (target == null) {
            target = cobaltTarget();
            LaunchSupport.cacheTarget(cacheKey, target);
        }
        return LaunchSupport.launchFresh(context,
                YouTubeTarget.ACTION_MEDIA_PLAY_FROM_SEARCH, uri, target,
                target.packageName, "Fresh TizenTube", cacheKey,
                LaunchSupport.COBALT_FRESH_FLAGS);
    }

    private static void showMissingTarget(Context context, boolean tizentube) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                context, tizentube ? R.string.status_tizentube_missing : R.string.status_smarttube_missing,
                Toast.LENGTH_LONG).show());
    }

    /** Callbacks for the settings test button; invoked on a worker thread. */
    public interface TestCallback {
        void onMissingTarget();
        void onResult(boolean opened);
    }

    /** The settings test follows the same fresh-task behavior as service launches. */
    public static boolean openTest(Context context) {
        return openTest(context, null);
    }

    /** Callback variant: posts no UI itself; results go to {@code callback}. */
    public static boolean openTest(Context context, TestCallback callback) {
        if (context == null) return false;
        final Context applicationContext = context.getApplicationContext();
        final String uri = TitleResultHelper.youtubeSearchUri(TEST_QUERY);
        final boolean tizentube = YouTubeTarget.isTizenTube(AppPrefs.youtubeTarget(applicationContext));
        if (tizentube) {
            // No SmartTube fallback: only Cobalt itself satisfies the selection.
            if (!isCobaltInstalled(applicationContext)) {
                Log.w(TAG, "TizenTube Cobalt is not installed");
                if (callback != null) callback.onMissingTarget();
                else Toast.makeText(context, R.string.status_tizentube_missing, Toast.LENGTH_LONG).show();
                return false;
            }
        } else if (resolveSmartTube(applicationContext, uri) == null) {
            Log.w(TAG, "SmartTube is not installed");
            if (callback != null) callback.onMissingTarget();
            else Toast.makeText(context, R.string.status_smarttube_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        Thread launchThread = new Thread(() -> {
            boolean opened;
            if (tizentube) {
                opened = launchCobalt(applicationContext, uri);
            } else {
                LaunchSupport.Target smartTube = resolveSmartTube(applicationContext, uri);
                opened = smartTube != null && LaunchSupport.launchFresh(applicationContext, uri, smartTube,
                        smartTube.packageName, "Fresh YouTube");
            }
            if (callback != null) {
                callback.onResult(opened);
            } else if (!opened) {
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(applicationContext,
                        tizentube ? R.string.status_tizentube_missing
                                : R.string.status_smarttube_missing,
                        Toast.LENGTH_LONG).show());
            }
        }, "gtv2stream-youtube-test");
        launchThread.start();
        return true;
    }

    static LaunchSupport.Target resolveSmartTube(Context context, String uri) {
        // Scoped per-package queries in stable-before-beta preference order.
        // A single generic VIEW query was tried and reverted: on some systems
        // it omits installed handlers (emulator proven: generic returned only
        // the framework browser stub while scoped found SmartTube), so the
        // v1.1 query shape stays. Cache above keeps repeat clicks cheap.
        PackageManager packageManager = context.getPackageManager();
        for (String packageName : LaunchPolicy.SMART_TUBE_ORDER) {
            Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(packageName);
            List<ResolveInfo> resolved = packageManager.queryIntentActivities(
                    probe, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved == null) continue;
            for (ResolveInfo candidate : resolved) {
                if (candidate != null && candidate.activityInfo != null
                        && candidate.activityInfo.packageName != null
                        && candidate.activityInfo.name != null) {
                    return new LaunchSupport.Target(new ComponentName(
                            candidate.activityInfo.packageName, candidate.activityInfo.name));
                }
            }
        }
        return null;
    }
}

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

    /** Current and legacy SmartTube packages declare VIEW handlers for YouTube URLs. */
    static final String SMARTTUBE_STABLE = "org.smarttube.stable";
    static final String SMARTTUBE_BETA = "org.smarttube.beta";
    static final String TIZENTUBE_COBALT = "io.gh.reisxd.tizentube.cobalt";

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
        LaunchSupport.Target target = tizentube ? resolveTizenTube(service, uri) : resolveSmartTube(service, uri);
        if (target == null) {
            Log.w(TAG, tizentube ? "TizenTube Cobalt is not installed" : "SmartTube is not installed");
            showMissingTarget(service, tizentube);
            return false;
        }
        boolean opened = tizentube ? launchTizenTube(service, uri, target)
                : LaunchSupport.launchFresh(service, uri, target, target.packageName, "Fresh YouTube");
        if (!opened) {
            showMissingTarget(service, tizentube);
        }
        return opened;
    }

    private static void showMissingTarget(Context context, boolean tizentube) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                context, tizentube ? R.string.status_tizentube_missing : R.string.status_smarttube_missing,
                Toast.LENGTH_LONG).show());
    }

    /** The settings test follows the same fresh-task behavior as service launches. */
    public static boolean openTest(Context context) {
        if (context == null) return false;
        final Context applicationContext = context.getApplicationContext();
        final String uri = TitleResultHelper.youtubeSearchUri(TEST_QUERY);
        final boolean tizentube = YouTubeTarget.isTizenTube(AppPrefs.youtubeTarget(applicationContext));
        final LaunchSupport.Target target = tizentube ? resolveTizenTube(applicationContext, uri)
                : resolveSmartTube(applicationContext, uri);
        if (target == null) {
            Log.w(TAG, tizentube ? "TizenTube Cobalt is not installed" : "SmartTube is not installed");
            Toast.makeText(context, tizentube ? R.string.status_tizentube_missing
                    : R.string.status_smarttube_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        Thread launchThread = new Thread(() -> {
            boolean opened = tizentube ? launchTizenTube(applicationContext, uri, target)
                    : LaunchSupport.launchFresh(applicationContext, uri, target, target.packageName,
                    "Fresh YouTube");
            if (!opened) {
                Toast.makeText(applicationContext, tizentube ? R.string.status_tizentube_missing
                        : R.string.status_smarttube_missing,
                        Toast.LENGTH_LONG).show();
            }
        }, "gtv2stream-youtube-test");
        launchThread.start();
        return true;
    }

    private static LaunchSupport.Target resolveSmartTube(Context context, String uri) {
        PackageManager packageManager = context.getPackageManager();
        for (String packageName : new String[] {
                SMARTTUBE_STABLE,
                SMARTTUBE_BETA,
                "com.teamsmart.videomanager.tv",
                "com.liskovsoft.smartyoutubetv2.beta"
        }) {
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

    /** Cobalt currently has no reliable external VIEW contract (issue #129). Probe VIEW,
     * then use its declared Leanback/launcher activity as the documented safe fallback. */
    static LaunchSupport.Target resolveTizenTube(Context context, String uri) {
        LaunchSupport.Target view = LaunchSupport.resolveHandler(context, uri,
                java.util.Collections.singletonList(TIZENTUBE_COBALT), true);
        if (view != null) return view;
        PackageManager pm = context.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setPackage(TIZENTUBE_COBALT);
        List<ResolveInfo> resolved = pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null || resolved.isEmpty()) {
            probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(TIZENTUBE_COBALT);
            resolved = pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY);
        }
        if (resolved == null) return null;
        for (ResolveInfo candidate : resolved) if (candidate != null && candidate.activityInfo != null
                && candidate.activityInfo.name != null)
            return new LaunchSupport.Target(new ComponentName(TIZENTUBE_COBALT, candidate.activityInfo.name));
        return null;
    }

    private static boolean launchTizenTube(Context context, String uri, LaunchSupport.Target target) {
        // A launcher fallback target is a MAIN activity, so do not send it a VIEW URI.
        LaunchSupport.Target view = LaunchSupport.resolveHandler(context, uri,
                java.util.Collections.singletonList(TIZENTUBE_COBALT), true);
        if (view != null) return LaunchSupport.launchFresh(context, uri, view, TIZENTUBE_COBALT,
                "Fresh TizenTube");
        try {
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(target.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(launch);
            return true;
        } catch (Exception error) {
            Log.w(TAG, "TizenTube launcher fallback failed: " + error.getMessage());
            return false;
        }
    }
}

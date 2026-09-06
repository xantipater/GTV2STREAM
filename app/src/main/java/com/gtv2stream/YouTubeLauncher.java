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
 * Opens a YouTube title search in SmartTube using its declared VIEW handler.
 */
public final class YouTubeLauncher {
    private static final String TAG = "GTV2STREAM";

    /** Current and legacy SmartTube packages declare VIEW handlers for YouTube URLs. */
    static final String SMARTTUBE_STABLE = "org.smarttube.stable";
    static final String SMARTTUBE_BETA = "org.smarttube.beta";

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
        LaunchSupport.Target target = resolveSmartTube(service, uri);
        if (target == null) {
            Log.w(TAG, "SmartTube is not installed");
            showMissingTarget(service);
            return false;
        }
        boolean opened = LaunchSupport.launchFresh(
                service, uri, target, target.packageName, "Fresh YouTube");
        if (!opened) {
            showMissingTarget(service);
        }
        return opened;
    }

    private static void showMissingTarget(Context context) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                context, R.string.status_smarttube_missing, Toast.LENGTH_LONG).show());
    }

    /** The settings test follows the same fresh-task behavior as service launches. */
    public static boolean openTest(Context context) {
        if (context == null) return false;
        final Context applicationContext = context.getApplicationContext();
        final String uri = TitleResultHelper.youtubeSearchUri(TEST_QUERY);
        final LaunchSupport.Target target = resolveSmartTube(applicationContext, uri);
        if (target == null) {
            Log.w(TAG, "SmartTube is not installed");
            Toast.makeText(context, R.string.status_smarttube_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        Thread launchThread = new Thread(() -> {
            boolean opened = LaunchSupport.launchFresh(
                    applicationContext, uri, target, target.packageName, "Fresh YouTube");
            if (!opened) {
                Toast.makeText(applicationContext, R.string.status_smarttube_missing,
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
}

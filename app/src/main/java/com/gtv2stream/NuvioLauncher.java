package com.gtv2stream;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

/** Resolves a Nuvio URI handler and performs an explicit fresh-task launch. */
public final class NuvioLauncher {
    private static final String TAG = "GTV2STREAM";
    private static final List<String> PREFERRED_PACKAGES = Arrays.asList(
            "com.nuviodebug.com", "com.nuvio.tv", "com.nuvio.app");
    public static final String TEST_URI = "nuvio://movie/tt0371746";

    private NuvioLauncher() { }

    /** Called from the service worker after title resolution. */
    public static boolean open(AccessibilityService service, TitleMatch match) {
        String uri = TitleResultHelper.nuvioUri(match);
        if (uri == null) {
            Toast.makeText(service, R.string.missing_match_id, Toast.LENGTH_SHORT).show();
            return false;
        }
        LaunchSupport.Target target = LaunchSupport.resolveHandler(service, uri, PREFERRED_PACKAGES, false);
        if (target == null) {
            Log.w(TAG, "No Nuvio URI handler resolved for " + uri);
            Toast.makeText(service, R.string.status_nuvio_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        boolean opened = LaunchSupport.launchFresh(
                service, uri, target, null, "Fresh Nuvio");
        if (!opened) {
            Toast.makeText(service, R.string.status_nuvio_missing, Toast.LENGTH_LONG).show();
        }
        return opened;
    }

    /** The settings test follows the same fresh-task behavior as service launches. */
    public static boolean openTest(Context context) {
        if (context == null) return false;
        final Context applicationContext = context.getApplicationContext();
        final LaunchSupport.Target target = resolveTestTarget(applicationContext);
        if (target == null) {
            Log.w(TAG, "No Nuvio URI handler resolved for " + TEST_URI);
            Toast.makeText(context, R.string.status_nuvio_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        Thread launchThread = new Thread(() -> {
            boolean opened = LaunchSupport.launchFresh(
                    applicationContext, TEST_URI, target, null, "Fresh Nuvio");
            if (!opened) {
                Toast.makeText(applicationContext, R.string.status_nuvio_missing,
                        Toast.LENGTH_LONG).show();
            }
        }, "gtv2stream-nuvio-test");
        launchThread.start();
        return true;
    }

    private static LaunchSupport.Target resolveTestTarget(Context context) {
        return LaunchSupport.resolveHandler(context, TEST_URI, PREFERRED_PACKAGES, false);
    }
}

package com.gtv2stream;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

/** Resolves a Stremio deep-link handler and performs an explicit fresh-task launch. */
public final class StremioLauncher {
    private static final String TAG = "GTV2STREAM";
    public static final String TEST_URI = "stremio:///detail/movie/tt0371746";
    /**
     * Strict Stremio packages: Nuvio also registers the stremio:// scheme (it is a
     * Stremio TV fork), so resolution must never fall back to an arbitrary handler.
     */
    private static final List<String> STREMIO_PACKAGES = Arrays.asList(
            "com.stremio.one", "io.stremio.app", "com.stremio");

    private StremioLauncher() { }

    /** Called from the service worker after title resolution. */
    public static boolean open(AccessibilityService service, TitleMatch match) {
        String uri = TitleResultHelper.stremioUri(match);
        if (uri == null) {
            Toast.makeText(service, R.string.missing_match_id, Toast.LENGTH_SHORT).show();
            return false;
        }
        LaunchSupport.Target target = LaunchSupport.resolveHandler(
                service, uri, STREMIO_PACKAGES, true);
        if (target == null) {
            Log.w(TAG, "No Stremio URI handler resolved for " + uri);
            Toast.makeText(service, R.string.status_stremio_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        boolean opened = LaunchSupport.launchFresh(
                service, uri, target, target.packageName, "Fresh Stremio");
        if (!opened) {
            Toast.makeText(service, R.string.status_stremio_missing, Toast.LENGTH_LONG).show();
        }
        return opened;
    }

    /** The settings test follows the same fresh-task behavior as service launches. */
    public static boolean openTest(Context context) {
        if (context == null) return false;
        final Context applicationContext = context.getApplicationContext();
        final LaunchSupport.Target target = LaunchSupport.resolveHandler(
                applicationContext, TEST_URI, STREMIO_PACKAGES, true);
        if (target == null) {
            Log.w(TAG, "No Stremio URI handler resolved for " + TEST_URI);
            Toast.makeText(context, R.string.status_stremio_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        Thread launchThread = new Thread(() -> {
            boolean opened = LaunchSupport.launchFresh(
                    applicationContext, TEST_URI, target, target.packageName, "Fresh Stremio");
            if (!opened) {
                Toast.makeText(applicationContext, R.string.status_stremio_missing,
                        Toast.LENGTH_LONG).show();
            }
        }, "gtv2stream-stremio-test");
        launchThread.start();
        return true;
    }
}

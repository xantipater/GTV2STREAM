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
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(service, R.string.missing_match_id, Toast.LENGTH_SHORT).show());
            return false;
        }
        String cacheKey = LaunchPolicy.cacheKey(AppPrefs.MOVIES_STREMIO, uri);
        LaunchSupport.Target target = LaunchSupport.cachedTarget(cacheKey);
        if (target == null) {
            target = LaunchSupport.resolveHandler(
                    service, uri, STREMIO_PACKAGES, true);
            if (target == null) {
                Diagnostics.debug("No Stremio URI handler resolved for " + uri);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(service, R.string.status_stremio_missing, Toast.LENGTH_LONG).show());
                return false;
            }
            LaunchSupport.cacheTarget(cacheKey, target);
        }
        boolean opened = LaunchSupport.launchFresh(
                service, uri, target, target.packageName, "Fresh Stremio", cacheKey);
        if (!opened) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    Toast.makeText(service, R.string.status_stremio_missing, Toast.LENGTH_LONG).show());
        }
        return opened;
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
        final LaunchSupport.Target target = LaunchSupport.resolveHandler(
                applicationContext, TEST_URI, STREMIO_PACKAGES, true);
        if (target == null) {
            Diagnostics.debug("No Stremio URI handler resolved for " + TEST_URI);
            if (callback != null) callback.onMissingTarget();
            else Toast.makeText(context, R.string.status_stremio_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        Thread launchThread = new Thread(() -> {
            boolean opened = LaunchSupport.launchFresh(
                    applicationContext, TEST_URI, target, target.packageName, "Fresh Stremio");
            if (callback != null) {
                callback.onResult(opened);
            } else if (!opened) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(
                        applicationContext, R.string.status_stremio_missing,
                        Toast.LENGTH_LONG).show());
            }
        }, "gtv2stream-stremio-test");
        launchThread.start();
        return true;
    }
}

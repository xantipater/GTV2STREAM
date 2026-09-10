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
        String cacheKey = LaunchPolicy.cacheKey(AppPrefs.MOVIES_NUVIO, uri);
        LaunchSupport.Target target = LaunchSupport.cachedTarget(cacheKey);
        if (target == null) {
            target = LaunchSupport.resolveHandler(service, uri, PREFERRED_PACKAGES, false);
            if (target == null) {
                Log.w(TAG, "No Nuvio URI handler resolved for " + uri);
                Toast.makeText(service, R.string.status_nuvio_missing, Toast.LENGTH_LONG).show();
                return false;
            }
            LaunchSupport.cacheTarget(cacheKey, target);
        }
        boolean opened = LaunchSupport.launchFresh(
                service, uri, target, null, "Fresh Nuvio", cacheKey);
        if (!opened) {
            Toast.makeText(service, R.string.status_nuvio_missing, Toast.LENGTH_LONG).show();
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
        final LaunchSupport.Target target = resolveTestTarget(applicationContext);
        if (target == null) {
            Log.w(TAG, "No Nuvio URI handler resolved for " + TEST_URI);
            if (callback != null) callback.onMissingTarget();
            else Toast.makeText(context, R.string.status_nuvio_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        Thread launchThread = new Thread(() -> {
            boolean opened = LaunchSupport.launchFresh(
                    applicationContext, TEST_URI, target, null, "Fresh Nuvio");
            if (callback != null) {
                callback.onResult(opened);
            } else if (!opened) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(
                        applicationContext, R.string.status_nuvio_missing,
                        Toast.LENGTH_LONG).show());
            }
        }, "gtv2stream-nuvio-test");
        launchThread.start();
        return true;
    }

    private static LaunchSupport.Target resolveTestTarget(Context context) {
        return LaunchSupport.resolveHandler(context, TEST_URI, PREFERRED_PACKAGES, false);
    }
}

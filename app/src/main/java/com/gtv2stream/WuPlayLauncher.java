package com.gtv2stream;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

/**
 * Resolves WuPlay's deep-link handler and performs an explicit fresh-task launch.
 *
 * <p>WuPlay ({@code app.wuplay.androidtv}) is IMDb-backed like Stremio:
 * {@code wuplay://movie/<imdbId>} and {@code wuplay://series/<imdbId>}. Verified
 * live on the test TV against WuPlay 0.9.0-beta: both forms resolve to
 * {@code app.wuplay.androidtv/.MainActivity}, and the scheme is declared in
 * WuPlay's own manifest. The flat shape (no {@code /detail}) is WuPlay's own,
 * not a Stremio fork, so it gets its own resolver and its own cache key.
 */
public final class WuPlayLauncher {
    private static final String TAG = "GTV2STREAM";
    public static final String TEST_URI = "wuplay://movie/tt0371746";

    /**
     * Strict WuPlay packages. The scheme is exclusive to WuPlay in practice, but
     * resolution still never falls back to an arbitrary handler for the scheme.
     */
    private static final List<String> WUPLAY_PACKAGES = Arrays.asList(
            "app.wuplay.androidtv");

    private WuPlayLauncher() { }

    /** Called from the service worker after title resolution. */
    public static boolean open(AccessibilityService service, TitleMatch match) {
        String uri = TitleResultHelper.wuplayUri(match);
        if (uri == null) {
            Toast.makeText(service, R.string.missing_match_id, Toast.LENGTH_SHORT).show();
            return false;
        }
        String cacheKey = LaunchPolicy.cacheKey(AppPrefs.MOVIES_WUPLAY, uri);
        LaunchSupport.Target target = LaunchSupport.cachedTarget(cacheKey);
        if (target == null) {
            target = LaunchSupport.resolveHandler(service, uri, WUPLAY_PACKAGES, true);
            if (target == null) {
                Log.w(TAG, "No WuPlay URI handler resolved for " + uri);
                Toast.makeText(service, R.string.status_wuplay_missing, Toast.LENGTH_LONG).show();
                return false;
            }
            LaunchSupport.cacheTarget(cacheKey, target);
        }
        boolean opened = LaunchSupport.launchFresh(
                service, uri, target, target.packageName, "Fresh WuPlay", cacheKey);
        if (!opened) {
            Toast.makeText(service, R.string.status_wuplay_missing, Toast.LENGTH_LONG).show();
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
                applicationContext, TEST_URI, WUPLAY_PACKAGES, true);
        if (target == null) {
            Log.w(TAG, "No WuPlay URI handler resolved for " + TEST_URI);
            if (callback != null) callback.onMissingTarget();
            else Toast.makeText(context, R.string.status_wuplay_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        Thread launchThread = new Thread(() -> {
            boolean opened = LaunchSupport.launchFresh(
                    applicationContext, TEST_URI, target, target.packageName, "Fresh WuPlay");
            if (callback != null) {
                callback.onResult(opened);
            } else if (!opened) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(
                        applicationContext, R.string.status_wuplay_missing,
                        Toast.LENGTH_LONG).show());
            }
        }, "gtv2stream-wuplay-test");
        launchThread.start();
        return true;
    }
}

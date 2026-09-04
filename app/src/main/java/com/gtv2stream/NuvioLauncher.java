package com.gtv2stream;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

/** Resolves a Nuvio URI handler and performs an explicit fresh-task launch. */
public final class NuvioLauncher {
    private static final String TAG = "GTV2STREAM";
    private static final long FRESH_TASK_SETTLE_MS = 200L;
    private static final List<String> PREFERRED_PACKAGES = Arrays.asList(
            "com.nuviodebug.com", "com.nuvio.tv", "com.nuvio.app");
    public static final String TEST_URI = "nuvio://movie/tt0371746";

    private NuvioLauncher() { }

    /** Called from the service worker, so the fresh-task wait is off the main thread. */
    public static boolean open(AccessibilityService service, TitleMatch match) {
        String uri = TitleResultHelper.nuvioUri(match);
        if (uri == null) {
            Toast.makeText(service, R.string.missing_match_id, Toast.LENGTH_SHORT).show();
            return false;
        }
        return openFresh(service, uri);
    }

    /** The settings test follows the same fresh-task behavior as service launches. */
    public static boolean openTest(Context context) {
        if (context == null) return false;
        final Context applicationContext = context.getApplicationContext();
        final ResolvedTarget target = resolveTarget(applicationContext, TEST_URI);
        if (target == null) {
            Log.w(TAG, "No Nuvio URI handler resolved for " + TEST_URI);
            Toast.makeText(context, R.string.status_nuvio_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        Thread launchThread = new Thread(
                () -> openFresh(applicationContext, TEST_URI, target), "gtv2stream-nuvio-test");
        launchThread.start();
        return true;
    }

    private static boolean openFresh(Context context, String uri) {
        ResolvedTarget target = resolveTarget(context, uri);
        if (target == null) {
            Log.w(TAG, "No Nuvio URI handler resolved for " + uri);
            Toast.makeText(context, R.string.status_nuvio_missing, Toast.LENGTH_LONG).show();
            return false;
        }
        return openFresh(context, uri, target);
    }

    private static boolean openFresh(Context context, String uri, ResolvedTarget target) {
        try {
            ActivityManager activityManager = context.getSystemService(ActivityManager.class);
            if (activityManager != null) {
                Log.i(TAG, "Fresh Nuvio launch: stopping " + target.packageName);
                activityManager.killBackgroundProcesses(target.packageName);
            }
            SystemClock.sleep(FRESH_TASK_SETTLE_MS);

            int freshFlags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;
            Intent launch = new Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .setComponent(target.component)
                    .setPackage(target.packageName)
                    .addFlags(freshFlags);
            try {
                context.startActivity(launch);
                Log.i(TAG, "Fresh Nuvio activity launch: " + target.component.flattenToShortString());
                return true;
            } catch (ActivityNotFoundException | SecurityException explicitError) {
                // Some handlers resolve during probing but reject the explicit
                // component at launch. Retry the same URI generically, retaining
                // the fresh-task flags and without repeating the stop/delay.
                Log.w(TAG, "Explicit Nuvio launch failed; trying generic VIEW: "
                        + explicitError.getMessage());
                Intent generic = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(freshFlags);
                try {
                    context.startActivity(generic);
                    Log.i(TAG, "Fresh Nuvio generic VIEW launch");
                    return true;
                } catch (ActivityNotFoundException | SecurityException fallbackError) {
                    Log.w(TAG, "Generic Nuvio launch failed: " + fallbackError.getMessage());
                    Toast.makeText(context, R.string.status_nuvio_missing, Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        } catch (SecurityException error) {
            Log.w(TAG, "Fresh Nuvio launch setup failed: " + error.getMessage());
            Toast.makeText(context, R.string.status_nuvio_missing, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    static ResolvedTarget resolveTarget(Context context, String uri) {
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolved = packageManager.queryIntentActivities(
                probe, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null || resolved.isEmpty()) return null;

        ResolveInfo selected = null;
        for (String preferred : PREFERRED_PACKAGES) {
            for (ResolveInfo candidate : resolved) {
                ActivityInfo info = candidate == null ? null : candidate.activityInfo;
                if (info != null && preferred.equals(info.packageName)) {
                    selected = candidate;
                    break;
                }
            }
            if (selected != null) break;
        }
        if (selected == null) {
            for (ResolveInfo candidate : resolved) {
                if (candidate != null && candidate.activityInfo != null) {
                    selected = candidate;
                    break;
                }
            }
        }
        if (selected == null || selected.activityInfo == null
                || selected.activityInfo.packageName == null
                || selected.activityInfo.name == null) return null;

        ActivityInfo info = selected.activityInfo;
        return new ResolvedTarget(new ComponentName(info.packageName, info.name));
    }

    static final class ResolvedTarget {
        final ComponentName component;
        final String packageName;

        ResolvedTarget(ComponentName component) {
            this.component = component;
            this.packageName = component.getPackageName();
        }
    }
}

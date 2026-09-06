package com.gtv2stream;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import java.util.List;

/**
 * Shared fresh-task launch machinery. Targets launch the URI with
 * NEW_TASK|CLEAR_TASK on the explicit component. A failed explicit launch
 * retries with the target package (or fully generically for scheme-owned URIs
 * such as nuvio://).
 */
final class LaunchSupport {
    private static final String TAG = "GTV2STREAM";
    private LaunchSupport() { }

    /**
     * Resolves a VIEW handler for the URI, preferring the listed packages in order.
     * With {@code strictPreferred}, only the preferred packages are accepted — used
     * when the URI scheme is shared by unrelated apps (Nuvio also registers
     * stremio://) and a fallback would route to the wrong target.
     */
    static Target resolveHandler(Context context, String uri, List<String> preferredPackages,
            boolean strictPreferred) {
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolved = packageManager.queryIntentActivities(
                probe, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null || resolved.isEmpty()) return null;

        ResolveInfo selected = null;
        if (preferredPackages != null) {
            for (String preferred : preferredPackages) {
                for (ResolveInfo candidate : resolved) {
                    ActivityInfo info = candidate == null ? null : candidate.activityInfo;
                    if (info != null && preferred.equals(info.packageName)) {
                        selected = candidate;
                        break;
                    }
                }
                if (selected != null) break;
            }
        }
        if (selected == null && !strictPreferred) {
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
        return new Target(new ComponentName(info.packageName, info.name));
    }

    /**
     * Performs the fresh-task launch. {@code target} may be null for a generic scheme
     * launch; {@code fallbackPackage} may be null to fall back to a fully generic VIEW.
     */
    static boolean launchFresh(Context context, String uri, Target target,
            String fallbackPackage, String logLabel) {
        try {
            int freshFlags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;
            Intent launch = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(freshFlags);
            if (target != null) {
                launch.setComponent(target.component).setPackage(target.packageName);
            }
            try {
                context.startActivity(launch);
                Log.i(TAG, logLabel + " explicit launch: "
                        + (target != null ? target.component.flattenToShortString() : "generic"));
                return true;
            } catch (ActivityNotFoundException | SecurityException explicitError) {
                // Some handlers resolve during probing but reject the explicit
                // component at launch. Retry the same URI without repeating the
                // stop/delay, constrained to the target package when provided.
                Log.w(TAG, logLabel + " explicit launch failed; retrying: "
                        + explicitError.getMessage());
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(freshFlags);
                if (fallbackPackage != null) fallback.setPackage(fallbackPackage);
                try {
                    context.startActivity(fallback);
                    Log.i(TAG, logLabel + " fallback launch");
                    return true;
                } catch (ActivityNotFoundException | SecurityException fallbackError) {
                    Log.w(TAG, logLabel + " fallback launch failed: " + fallbackError.getMessage());
                    return false;
                }
            }
        } catch (SecurityException error) {
            Log.w(TAG, logLabel + " launch setup failed: " + error.getMessage());
            return false;
        }
    }

    /** A resolved destination: the activity component and its owning package. */
    static final class Target {
        final ComponentName component;
        final String packageName;

        Target(ComponentName component) {
            this.component = component;
            this.packageName = component.getPackageName();
        }
    }
}

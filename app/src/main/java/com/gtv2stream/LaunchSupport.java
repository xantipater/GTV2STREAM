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
 * Shared fresh-task launch machinery. Most targets launch the URI with
 * NEW_TASK|CLEAR_TASK on the explicit component. TizenTube Cobalt is the
 * exception: it launches with NEW_TASK|MULTIPLE_TASK|EXCLUDE_FROM_RECENTS so
 * a warm start delivers MEDIA_PLAY_FROM_SEARCH to the live instance via
 * onNewIntent instead of tearing the task down (CLEAR_TASK shows the home
 * feed with an empty search on a warm Cobalt). A failed explicit launch
 * retries with the target package (or fully generically for scheme-owned URIs
 * such as nuvio://).
 */
final class LaunchSupport {
    private static final String TAG = "GTV2STREAM";
    private LaunchSupport() { }

    /**
     * Default fresh-task flags for SmartTube/Nuvio/Stremio: NEW_TASK|CLEAR_TASK.
     */
    static final int DEFAULT_FRESH_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;

    /**
     * Cobalt warm-start flags: NEW_TASK|MULTIPLE_TASK|EXCLUDE_FROM_RECENTS
     * (0x18800000). Live on-device A/B proved CLEAR_TASK (0x10008000) loses
     * the query on a warm Cobalt (home feed, empty search 0/2) while these
     * flags inject the search into the live top instance (2/2). Cold start
     * works either way. Never add CLEAR_TASK here.
     */
    static final int COBALT_FRESH_FLAGS =
            Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;

    /**
     * Cache of resolved VIEW handlers keyed by target preference plus URI
     * scheme. Resolution is a PackageManager IPC on every click; the cache
     * turns repeat clicks into a map lookup. Entries are evicted below
     * whenever resolution returns null or a launch fails, so an install,
     * uninstall, or handler change is picked up on the next attempt.
     */
    private static final java.util.LinkedHashMap<String, Target> RESOLVED =
            new java.util.LinkedHashMap<>(16, 0.75f, true);
    private static final int MAX_CACHED_TARGETS = 16;

    static synchronized Target cachedTarget(String cacheKey) {
        if (cacheKey == null || cacheKey.isEmpty()) return null;
        return RESOLVED.get(cacheKey);
    }

    static synchronized void cacheTarget(String cacheKey, Target target) {
        if (cacheKey == null || cacheKey.isEmpty() || target == null) return;
        RESOLVED.put(cacheKey, target);
        while (RESOLVED.size() > MAX_CACHED_TARGETS) {
            java.util.Iterator<String> oldest = RESOLVED.keySet().iterator();
            if (!oldest.hasNext()) break;
            oldest.next();
            oldest.remove();
        }
    }

    static synchronized void evictTarget(String cacheKey) {
        if (cacheKey == null || cacheKey.isEmpty()) return;
        RESOLVED.remove(cacheKey);
    }

    /**
     * Intentional debug seam: clears the whole resolution cache (no
     * production caller; kept for manual testing of handler changes).
     */
    @SuppressWarnings("unused")
    static synchronized void clearCache() {
        RESOLVED.clear();
    }

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
        return launchFresh(context, Intent.ACTION_VIEW, uri, target, fallbackPackage, logLabel, null);
    }

    /**
     * Performs the fresh-task launch. {@code target} may be null for a generic scheme
     * launch; {@code fallbackPackage} may be null to fall back to a fully generic VIEW.
     * When {@code cacheKey} is provided, a failed launch evicts that entry so the
     * next click re-resolves instead of reusing a stale handler.
     */
    static boolean launchFresh(Context context, String uri, Target target,
            String fallbackPackage, String logLabel, String cacheKey) {
        return launchFresh(context, Intent.ACTION_VIEW, uri, target, fallbackPackage, logLabel, cacheKey);
    }

    /**
     * Performs the fresh-task launch with a supplied intent action. VIEW callers
     * above are unchanged; Cobalt uses this with its MEDIA_PLAY_FROM_SEARCH
     * contract while keeping the same explicit-component plus constrained
     * package-retry shape. Uses {@link #DEFAULT_FRESH_FLAGS}; Cobalt callers
     * must use the flags overload below with {@link #COBALT_FRESH_FLAGS}.
     */
    static boolean launchFresh(Context context, String action, String uri, Target target,
            String fallbackPackage, String logLabel, String cacheKey) {
        return launchFresh(context, action, uri, target, fallbackPackage,
                logLabel, cacheKey, DEFAULT_FRESH_FLAGS);
    }

    /**
     * Performs the launch with explicit activity flags. The explicit and
     * fallback intents both carry {@code flags} so the retry preserves the
     * caller's task semantics (notably: Cobalt's warm-start flags survive the
     * package-constrained retry instead of falling back to CLEAR_TASK).
     */
    static boolean launchFresh(Context context, String action, String uri, Target target,
            String fallbackPackage, String logLabel, String cacheKey, int flags) {
        try {
            Intent launch = new Intent(action, Uri.parse(uri)).addFlags(flags);
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
                Intent fallback = new Intent(action, Uri.parse(uri)).addFlags(flags);
                if (fallbackPackage != null) fallback.setPackage(fallbackPackage);
                try {
                    context.startActivity(fallback);
                    Log.i(TAG, logLabel + " fallback launch");
                    return true;
                } catch (ActivityNotFoundException | SecurityException fallbackError) {
                    Log.w(TAG, logLabel + " fallback launch failed: " + fallbackError.getMessage());
                    evictTarget(cacheKey);
                    return false;
                }
            }
        } catch (SecurityException error) {
            Log.w(TAG, logLabel + " launch setup failed: " + error.getMessage());
            evictTarget(cacheKey);
            return false;
        }
    }

    /** A resolved destination: the activity component and its owning package. */
    static final class Target {
        final ComponentName component;
        final String packageName;
        /**
         * True when the component accepts the VIEW URI it was resolved for.
         * False for MAIN/launcher fallback activities, which must never be
         * sent a VIEW URI. Persisted with the cache entry so a cached
         * TizenTube target keeps its VIEW-vs-MAIN behavior.
         */
        final boolean viewUri;

        Target(ComponentName component) {
            this(component, true);
        }

        Target(ComponentName component, boolean viewUri) {
            this.component = component;
            this.packageName = component.getPackageName();
            this.viewUri = viewUri;
        }
    }
}

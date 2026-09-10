package com.gtv2stream;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure launch-routing policy shared by the launchers and the helper tests.
 * Keeps PackageManager results, preference order, and cache-key derivation
 * deterministic without touching Android APIs.
 */
public final class LaunchPolicy {
    /**
     * SmartTube package preference order: stable before beta, then legacy
     * package names. A single generic VIEW query is filtered in memory against
     * this order instead of issuing one scoped query per package.
     */
    public static final List<String> SMART_TUBE_ORDER = Collections.unmodifiableList(Arrays.asList(
            "org.smarttube.stable",
            "org.smarttube.beta",
            "com.teamsmart.videomanager.tv",
            "com.liskovsoft.smartyoutubetv2.beta"));

    private LaunchPolicy() { }

    /**
     * Resolves the film/series target from an already-read preference value.
     * Unknown values default to Nuvio, preserving existing behavior.
     */
    public static String moviesTarget(String stored) {
        if (AppPrefs.MOVIES_STREMIO.equals(stored)) return AppPrefs.MOVIES_STREMIO;
        if (AppPrefs.MOVIES_WUPLAY.equals(stored)) return AppPrefs.MOVIES_WUPLAY;
        return AppPrefs.MOVIES_NUVIO;
    }

    /** Picker cycle order for the film/series destination. */
    public static final List<String> MOVIES_TARGET_ORDER = Collections.unmodifiableList(Arrays.asList(
            AppPrefs.MOVIES_NUVIO,
            AppPrefs.MOVIES_STREMIO,
            AppPrefs.MOVIES_WUPLAY));

    /** Next destination in the picker cycle; an unknown stored value restarts the cycle. */
    public static String nextMoviesTarget(String stored) {
        int index = MOVIES_TARGET_ORDER.indexOf(moviesTarget(stored));
        return MOVIES_TARGET_ORDER.get((index + 1) % MOVIES_TARGET_ORDER.size());
    }

    /**
     * Returns the first preferred package present in the resolved set, or null
     * when none of the preferred packages resolved a handler.
     */
    public static String selectPreferred(List<String> preferenceOrder, Collection<String> resolved) {
        if (preferenceOrder == null || resolved == null || resolved.isEmpty()) return null;
        for (String preferred : preferenceOrder) {
            if (preferred != null && resolved.contains(preferred)) return preferred;
        }
        return null;
    }

    /**
     * Resolution-cache key: the selecting target preference plus the URI
     * scheme, so Nuvio, Stremio, and each YouTube target cache independently.
     */
    public static String cacheKey(String targetPref, String uri) {
        String target = targetPref == null ? "" : targetPref.trim().toLowerCase(Locale.US);
        return target + "|" + schemeOf(uri);
    }

    /** Lowercase URI scheme ("nuvio", "stremio", "https"), or "" when absent. */
    public static String schemeOf(String uri) {
        if (uri == null) return "";
        String value = uri.trim();
        int end = value.indexOf("://");
        String scheme = end >= 0 ? value.substring(0, end) : value;
        if (end < 0) {
            int colon = scheme.indexOf(':');
            if (colon >= 0) scheme = scheme.substring(0, colon);
        }
        return scheme.toLowerCase(Locale.US);
    }
}

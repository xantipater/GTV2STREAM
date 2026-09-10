package com.gtv2stream;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Set;

/** Central preference-file keys and target selection defaults. */
final class AppPrefs {
    static final String PREFS = "gtv2stream";
    static final String TMDB_KEY = "tmdb_key";
    static final String TARGET_MOVIES = "target_movies";
    static final String TARGET_YOUTUBE = "target_youtube";
    static final String SHOW_BADGE = "show_badge";
    static final String SERVICE_CONNECTED_AT = "service_connected_at";
    /** Comma-separated canonical provider identities bypassing the redirect. */
    static final String WHITELIST_PROVIDERS = "whitelist_providers";
    /**
     * Release version the update dialog has already been shown for. The prompt is
     * deliberately once per release: the inline notice stays for anyone who taps
     * Later, so an available update is never nagged and never hidden.
     */
    static final String UPDATE_PROMPTED_VERSION = "update_prompted_version";

    static final String MOVIES_NUVIO = "nuvio";
    static final String MOVIES_STREMIO = "stremio";
    /**
     * WuPlay is IMDb-backed like Stremio: {@code wuplay://movie/<imdbId>} and
     * {@code wuplay://series/<imdbId>}. Verified live against
     * {@code app.wuplay.androidtv} 0.9.0-beta on the test TV (both forms resolve
     * to its {@code .MainActivity}), and independently dated by TVRelay's notes
     * as added in WuPlay's own v0.8.3-beta.
     */
    static final String MOVIES_WUPLAY = "wuplay";
    static final String YOUTUBE_SMARTTUBE = YouTubeTarget.SMARTTUBE;
    static final String YOUTUBE_TIZENTUBE = YouTubeTarget.TIZENTUBE;

    private AppPrefs() { }

    /** Selected film/series destination; defaults to Nuvio. */
    static String moviesTarget(Context context) {
        return LaunchPolicy.moviesTarget(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(TARGET_MOVIES, MOVIES_NUVIO));
    }

    /** Selected YouTube destination; SmartTube preserves the v1.1 default. */
    static String youtubeTarget(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(TARGET_YOUTUBE, YOUTUBE_SMARTTUBE);
    }

    /** Redirect badge display preference; defaults to on. */
    static boolean badgeEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(SHOW_BADGE, true);
    }

    /**
     * Whitelisted provider identities. Empty by default so v1.1 users keep exact
     * v1.1 behaviour; unknown stored tokens are dropped on read (safe migration).
     */
    static Set<String> whitelist(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains(WHITELIST_PROVIDERS)) return ProviderWhitelist.parse(null);
        return ProviderWhitelist.parse(prefs.getString(WHITELIST_PROVIDERS, ""));
    }

    /** Persists the sanitized whitelist; survives restarts via SharedPreferences. */
    static void setWhitelist(Context context, Set<String> whitelist) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(WHITELIST_PROVIDERS, ProviderWhitelist.serialize(whitelist)).apply();
    }

    /** True when the parsed card's provider identity is whitelisted. */
    static boolean isWhitelisted(Context context, RecommendationTitleParser.Source parsed) {
        return RecommendationTitleParser.isWhitelisted(parsed, whitelist(context));
    }
}

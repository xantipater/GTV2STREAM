package com.gtv2stream;

import android.content.Context;

/** Central preference-file keys and target selection defaults. */
final class AppPrefs {
    static final String PREFS = "gtv2stream";
    static final String TMDB_KEY = "tmdb_key";
    static final String TARGET_MOVIES = "target_movies";
    static final String SHOW_BADGE = "show_badge";
    static final String SERVICE_CONNECTED_AT = "service_connected_at";

    static final String MOVIES_NUVIO = "nuvio";
    static final String MOVIES_STREMIO = "stremio";

    private AppPrefs() { }

    /** Selected film/series destination; defaults to Nuvio. */
    static String moviesTarget(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(TARGET_MOVIES, MOVIES_NUVIO);
    }

    /** Redirect badge display preference; defaults to on. */
    static boolean badgeEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(SHOW_BADGE, true);
    }
}

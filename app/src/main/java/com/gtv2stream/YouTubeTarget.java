package com.gtv2stream;

/** Pure target-selection seam shared by production routing and helper tests. */
final class YouTubeTarget {
    static final String SMARTTUBE = "smarttube";
    static final String TIZENTUBE = "tizentube";

    /**
     * Verified on-device Cobalt contract (Android 14):
     * {@code android.media.action.MEDIA_PLAY_FROM_SEARCH} with a YouTube
     * search URI addressed at the explicit Cobalt component, flagged
     * NEW_TASK|MULTIPLE_TASK|EXCLUDE_FROM_RECENTS (0x18800000). Live A/B
     * proved NEW_TASK|CLEAR_TASK (0x10008000) loses the query on a warm
     * Cobalt (home feed, empty search 0/2): Cobalt only honors the query on
     * the live instance via onNewIntent, so the task must not be torn down.
     * The warm-start flags deliver the search to the live top instance
     * (2/2, screenshots verified); cold start works either way. Cobalt has
     * no reliable external VIEW contract (issue #129).
     */
    static final String COBALT_PACKAGE = "io.gh.reisxd.tizentube.cobalt";
    static final String COBALT_ACTIVITY = "dev.cobalt.app.MainActivity";
    static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private YouTubeTarget() { }

    static boolean isTizenTube(String value) {
        return TIZENTUBE.equals(value);
    }

    /**
     * Outcome of TizenTube-selected routing. There is deliberately no
     * SmartTube outcome: the user's explicit TizenTube selection must be
     * honored, so a missing Cobalt fails visibly instead of silently opening
     * another app.
     */
    enum Route {
        /** Cobalt is installed; launch MPFS at its explicit component. */
        TIZENTUBE_VIEW,
        /** Cobalt is missing; the caller must fail closed with a visible error. */
        FAIL_CLOSED,
    }

    /**
     * Pure routing policy for a TizenTube-selected launch. Cobalt wins when
     * present; otherwise fail closed so a missing Cobalt is never silently
     * replaced by SmartTube.
     */
    static Route pickTizenTubeRoute(boolean cobaltReady) {
        return cobaltReady ? Route.TIZENTUBE_VIEW : Route.FAIL_CLOSED;
    }
}

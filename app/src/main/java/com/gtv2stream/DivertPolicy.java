package com.gtv2stream;

/**
 * Pure decision seam for the stock-YouTube divert.
 *
 * <p>The Google TV launcher starts stock YouTube itself with an explicit,
 * package-targeted {@code VIEW} intent when a YouTube recommendation card is
 * clicked, so GTV2STREAM can never intercept that click. The only guaranteed
 * redirect is therefore a divert on top of the stock-YouTube window change,
 * and that divert is only as good as the title already held in memory. This
 * class decides which of the two candidate caches drives the divert.
 *
 * <p>Order matters. The card that was actually clicked wins, because it is
 * read from the click event itself. The ambient/hero panel is only a fallback:
 * it is refreshed by a timer-driven poll, so it can be empty at click time
 * (nothing to divert with, which leaves stock YouTube on screen) or lag behind
 * the cursor (the wrong video gets searched).
 *
 * <p>Kept free of Android imports so the logic is unit-testable offline.
 */
final class DivertPolicy {
    /**
     * Freshness window for a title taken from the click event itself. The
     * divert happens within a few hundred milliseconds of the click, so this
     * only has to absorb a slow launcher hand-off. Kept short deliberately: a
     * stale clicked-card title must never be replayed onto a later, unrelated
     * manual YouTube launch.
     */
    static final long CLICKED_CARD_TTL_MS = 6000L;

    /**
     * Freshness window for the ambient/hero panel title. Longer than the
     * clicked-card window because this is the fallback for clicks whose own
     * payload was empty, and the panel is polled rather than instantaneous. It
     * matches the service's focused-hero window.
     */
    static final long HERO_PANEL_TTL_MS = 15000L;

    /** Which cache the divert should read its title from. */
    enum Choice {
        /** The card payload captured from the click event itself. */
        CLICKED_CARD,
        /** The ambient/hero panel title refreshed by the poll. */
        HERO_PANEL,
        /** Nothing usable: the divert must fail visibly instead of guessing. */
        NONE
    }

    private DivertPolicy() { }

    /** True when a captured-at timestamp is present and inside the window. */
    static boolean isFresh(long capturedAt, long now, long windowMs) {
        if (capturedAt <= 0L) return false;
        long age = now - capturedAt;
        return age >= 0L && age < windowMs;
    }

    /**
     * Picks the divert title source. {@code clickedUsable} and
     * {@code heroUsable} are the caller's already-classified "this cached
     * source can drive a YouTube search" answers, kept out of here so title
     * classification stays in one place.
     *
     * @param clickedCapturedAt when the clicked-card payload was captured
     * @param clickedUsable     whether the clicked-card payload is a usable YouTube title
     * @param heroCapturedAt    when the hero/ambient panel title was captured
     * @param heroUsable        whether the hero-panel title is a usable YouTube title
     * @param now               current time in milliseconds
     */
    static Choice choose(long clickedCapturedAt, boolean clickedUsable,
            long heroCapturedAt, boolean heroUsable, long now) {
        if (clickedUsable && isFresh(clickedCapturedAt, now, CLICKED_CARD_TTL_MS)) {
            return Choice.CLICKED_CARD;
        }
        if (heroUsable && isFresh(heroCapturedAt, now, HERO_PANEL_TTL_MS)) {
            return Choice.HERO_PANEL;
        }
        return Choice.NONE;
    }
}

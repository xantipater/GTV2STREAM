package com.gtv2stream;

/**
 * Pure dispatch de-duplication policy shared by the service and the helper tests.
 *
 * <p>A whitelisted bypass must suppress the immediate providerless
 * EntityActivity fallback for the same title (live sequence: bypass for a
 * Prime Video card, then the same title re-dispatched with {@code provider=""}
 * ~241ms later), while a later explicit click carrying a provider still
 * dispatches — e.g. after the whitelist settings change. Only providerless
 * candidates are ever suppressed, the whitelist check keeps running first in
 * the service, and bypasses never touch the normal duplicate bookkeeping.
 */
public final class DispatchPolicy {
    private DispatchPolicy() { }

    /**
     * True when a providerless candidate for {@code candidateTitle} arrives
     * inside {@code windowMs} of a whitelisted bypass for the same title.
     * Title comparison is punctuation/case-insensitive; a non-empty candidate
     * provider (an explicit click) always returns false so it can still
     * dispatch.
     */
    public static boolean shouldSuppressProviderlessFallback(
            String bypassedTitle, boolean bypassedYoutube, long bypassedAt,
            String candidateTitle, boolean candidateYoutube, String candidateProvider,
            long now, long windowMs) {
        if (bypassedTitle == null || bypassedTitle.isEmpty()) return false;
        if (candidateTitle == null || candidateTitle.isEmpty()) return false;
        if (candidateProvider != null && !candidateProvider.trim().isEmpty()) return false;
        if (bypassedYoutube != candidateYoutube) return false;
        if (windowMs <= 0) return false;
        long elapsed = now - bypassedAt;
        if (elapsed < 0 || elapsed >= windowMs) return false;
        return TitleResultHelper.normalizedTitleMatches(bypassedTitle, candidateTitle);
    }

    /**
     * Selects the cached focused-card provider for an authoritative detail
     * title. Returns the focused provider only when every guard holds: the
     * focus cache is fresh under {@code windowMs}, the cached source is
     * non-YouTube with a non-empty provider, and the cached title
     * normalized-matches the detail title. Otherwise returns {@code ""} so
     * the caller dispatches providerless and fail-closed behaviour is
     * preserved.
     */
    public static String focusedProviderForDetail(
            String focusedTitle, boolean focusedYoutube, String focusedProvider, long focusedAt,
            String detailTitle, long now, long windowMs) {
        if (focusedTitle == null || focusedTitle.isEmpty()) return "";
        if (detailTitle == null || detailTitle.isEmpty()) return "";
        if (focusedProvider == null || focusedProvider.trim().isEmpty()) return "";
        if (focusedYoutube) return "";
        if (windowMs <= 0) return "";
        long elapsed = now - focusedAt;
        if (elapsed < 0 || elapsed >= windowMs) return "";
        if (!TitleResultHelper.normalizedTitleMatches(focusedTitle, detailTitle)) return "";
        return focusedProvider.trim();
    }
}

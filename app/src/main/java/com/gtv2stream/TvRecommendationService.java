package com.gtv2stream;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Clean-room behavioral capture of Google TV launcher recommendations. Only click
 * and entity-window events are accepted; missing titles fail closed until the
 * launcher exposes its stable detail-title row. YouTube-marked payloads are routed
 * to the selected YouTube app; all other titles resolve through TMDB and open in
 * the selected film/series app.
 */
public class TvRecommendationService extends AccessibilityService {
    private static final String TAG = "GTV2STREAM";
    private static final String LAUNCHER_PACKAGE = "com.google.android.apps.tv.launcherx";
    private static final String STOCK_YOUTUBE_PACKAGE = "com.google.android.youtube.tv";
    private static final String HOME_ACTIVITY_SUFFIX = ".home.HomeActivity";
    private static final String DETAIL_ACTIVITY_SUFFIX = ".entity.EntityActivity";
    private static final String DETAIL_TITLE_ID =
            "com.google.android.apps.tv.launcherx:id/entity_details_title_row";
    private static final long TITLE_EARLY_RETRY_DELAY_MS = 250L;
    private static final long TITLE_FINAL_RETRY_DELAY_MS = 600L;
    private static final long DUPLICATE_WINDOW_MS = 2000L;
    /** Freshness window for focused-card provider context (focus -> detail dispatch). */
    private static final long FOCUSED_HERO_WINDOW_MS = 15000L;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gtv2stream-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingTitleRetry;
    private Runnable pendingHeroCapture;
    private final LauncherInteractionPolicy.Session interactionSession =
            new LauncherInteractionPolicy.Session();
    private RecommendationTitleParser.Source focusedHeroSource =
            RecommendationTitleParser.Source.NONE;
    private long focusedHeroCapturedAt;
    /**
     * The last real card payload, captured either when a card was focused or
     * from a click event that actually carried one. This is the primary source
     * for both the stock-YouTube divert and the click fallback, because the
     * ambient panel poll in {@link #focusedHeroSource} is weaker evidence than
     * the selected card itself. Never written by
     * the panel poll.
     */
    private RecommendationTitleParser.Source lastCardSource =
            RecommendationTitleParser.Source.NONE;
    private long lastCardCapturedAt;
    /** Window in which stock YouTube resurfacing may be answered with one re-assert. */
    private static final long DIVERT_REASSERT_WINDOW_MS = 6000L;
    private String lastDivertTitle = "";
    private long lastDivertAt;
    private int divertReassertsLeft;
    private long lastDivertGeneration;
    private String lastDivertTarget = "";
    private String lastDispatchedTitle = "";
    private boolean lastDispatchedYoutube;
    private long lastDispatchedAt;
    private long lastDispatchedGeneration = -1L;
    private long lastLauncherClickAt;
    /**
     * Last whitelisted bypass, kept apart from the dispatch bookkeeping above
     * so the whitelist check can stay first without a bypass ever suppressing
     * an explicit provider-carrying click. Only the immediate providerless
     * EntityActivity fallback for the same title is suppressed.
     */
    private String lastBypassedTitle = "";
    private boolean lastBypassedYoutube;
    private long lastBypassedAt;

    /**
     * Periodic connection heartbeat: Settings treats the service as Ready only
     * while the heartbeat is fresh, so a force-stopped or vendor-blocked process
     * can never leave a stale "connected" state behind.
     */
    private static final long HEARTBEAT_INTERVAL_MS = 15000L;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit()
                    .putLong(AppPrefs.SERVICE_CONNECTED_AT, System.currentTimeMillis()).apply();
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());


    @Override public void onServiceConnected() {
        heartbeat.run();
        // Warm the installed-app label set on the worker so the first redirect
        // never pays the PackageManager enumeration on the accessibility thread.
        worker.execute(this::loadInstalledAppLabels);
        // A service rebind can occur while a launcher card is already focused,
        // in which case Android sends no new focus event for that card.
        scheduleHeroCapture(100L, 12);
        Log.i(TAG, "Accessibility service connected");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        int type = event.getEventType();
        boolean launcherEvent = LAUNCHER_PACKAGE.contentEquals(event.getPackageName());
        if (!launcherEvent) {
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && STOCK_YOUTUBE_PACKAGE.contentEquals(event.getPackageName())) {
                divertStockYouTube();
            }
            // Opening our Settings cancels an outstanding selection; native
            // provider windows are not cancelled because the launcher may open
            // them while a legitimate TMDB lookup is in flight.
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && getPackageName().contentEquals(event.getPackageName())) {
                interactionSession.invalidate();
                clearRecommendationContext();
            }
            return;
        }
        logRawLauncherEvent(event);
        if (type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            interactionSession.beginEditing();
            clearRecommendationContext();
            return;
        }
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                || type == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            LauncherInteractionPolicy.Assessment interaction = assessInteraction(event);
            if (!interactionSession.accept(interaction,
                    type == AccessibilityEvent.TYPE_VIEW_CLICKED)) {
                // Rejected controls are terminal, not missing card payloads.
                // Otherwise Move/app tiles fall through to a stale YouTube panel.
                clearRecommendationContext();
                return;
            }
        }
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            lastLauncherClickAt = SystemClock.elapsedRealtime();
            clearDivert();
            handleLauncherClick(event);
        } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && isEntityWindow(event.getClassName())) {
            interactionSession.showDetail();
            handleEntityWindow();
        } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && isHomeWindow(event.getClassName())) {
            interactionSession.returnHome();
            clearRecommendationContext();
            // Refresh activity labels when returning from an install/update.
            worker.execute(this::loadInstalledAppLabels);
            // The launcher's already-focused card may not emit a new focus event
            // when Home opens. Capture its hero payload before a quick click can
            // hand the recommendation to stock YouTube.
            scheduleHeroCapture(100L, 12);
        } else if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                || type == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            handleLauncherFocus(event);
        }
    }

    private LauncherInteractionPolicy.Assessment assessInteraction(AccessibilityEvent event) {
        Set<String> labels = installedAppLabels;
        // Do not guess while PackageManager's initial worker-side load is pending.
        if (labels == null) return new LauncherInteractionPolicy.Assessment(true, false, false, false);
        AccessibilityNodeInfo source = event.getSource();
        try {
            // Read all direct evidence before classifying: a provider-only event
            // can belong to a real card whose title exists only on the node.
            return LauncherInteractionPolicy.assess(event.getText(),
                    toString(event.getContentDescription()),
                    source == null ? "" : toString(source.getText()),
                    source == null ? "" : toString(source.getContentDescription()), labels);
        } finally {
            if (source != null) source.recycle();
        }
    }

    private void clearRecommendationContext() {
        cancelTitleRetry();
        if (pendingHeroCapture != null) {
            handler.removeCallbacks(pendingHeroCapture);
            pendingHeroCapture = null;
        }
        clearFocusedHeroSource();
        clearLastCardSource();
        clearDivert();
        lastDispatchedTitle = "";
        lastDispatchedAt = 0L;
        lastDispatchedGeneration = -1L;
        lastLauncherClickAt = 0L;
        lastBypassedTitle = "";
        lastBypassedAt = 0L;
    }

    private void clearDivert() {
        lastDivertTitle = "";
        lastDivertAt = 0L;
        lastDivertTarget = "";
        divertReassertsLeft = 0;
    }

    private void handleLauncherFocus(AccessibilityEvent event) {
        // Synchronous provider bridge: the focused card's event text,
        // content description, or source-node description already carries the
        // provider edge (e.g. "The Tomorrow War, requires Prime Video
        // subscription, ..."). Cache a provider-bearing source immediately so
        // the YouTube-only window poll below can never overwrite it.
        RecommendationTitleParser.Source immediate =
                RecommendationTitleParser.fromEventTextSource(event.getText());
        if (immediate.isEmpty() || !immediate.hasProvider()) {
            immediate = RecommendationTitleParser.fromDescriptionSource(
                    toString(event.getContentDescription()));
        }
        if (!immediate.isEmpty() && !lastCardSource.isEmpty()
                && (!TitleResultHelper.compatibleTitles(immediate.lookupTitle(), lastCardSource.lookupTitle())
                    || (immediate.hasProvider() && immediate.youtube != lastCardSource.youtube))) {
            clearLastCardSource();
            clearFocusedHeroSource();
            clearDivert();
        }
        if (isUsableYouTubeSource(immediate)) {
            // The focused card's payload is the card the user is about to press,
            // and it is the only source that survives this launcher's click
            // events, which sometimes expose just the grid label of the card's
            // container ("Column 3") and nothing else.
            rememberCard(immediate);
        }
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            try {
                if (immediate.isEmpty() || !immediate.hasProvider()) {
                    RecommendationTitleParser.Source nodeSource =
                            RecommendationTitleParser.fromDescriptionSource(
                                    toString(source.getContentDescription()));
                    if (!nodeSource.isEmpty() && nodeSource.hasProvider()) {
                        immediate = nodeSource;
                    }
                }
                if (!immediate.isEmpty() && immediate.hasProvider()) {
                    if (pendingHeroCapture != null) handler.removeCallbacks(pendingHeroCapture);
                    pendingHeroCapture = null;
                    if (immediate.youtube) rememberCard(immediate);
                    else clearLastCardSource();
                    focusedHeroSource = immediate;
                    focusedHeroCapturedAt = SystemClock.elapsedRealtime();
                    Diagnostics.debug("Cached focused provider title: " + immediate.title
                            + " (" + immediate.provider + ")");
                    return;
                }
                android.graphics.Rect bounds = new android.graphics.Rect();
                source.getBoundsInScreen(bounds);
                scheduleFocusedHeroCapture(bounds);
            } finally {
                source.recycle();
            }
        } else if (!immediate.isEmpty() && immediate.hasProvider()) {
            if (pendingHeroCapture != null) handler.removeCallbacks(pendingHeroCapture);
            pendingHeroCapture = null;
            if (!immediate.youtube) clearLastCardSource();
            focusedHeroSource = immediate;
            focusedHeroCapturedAt = SystemClock.elapsedRealtime();
            Diagnostics.debug("Cached focused provider title: " + immediate.title
                    + " (" + immediate.provider + ")");
        } else {
            clearFocusedHeroSource();
        }
    }

    private void scheduleFocusedHeroCapture(android.graphics.Rect bounds) {
        // This used to reject any focused card outside a hardcoded hero band
        // (top 650..950) and clear the cache for it. That band is wrong for real
        // YouTube cards on this launcher, which were measured at
        // [116,591][508,828]: focusing one wiped the very title the divert needs.
        // The panel poll below is already narrow (it only yields a title when a
        // visible "YouTube" marker exists in the panel), so the poll is now
        // always scheduled and a valid cache is never cleared on focus alone.
        scheduleHeroCapture(100L, 12);
    }

    private void scheduleHeroCapture(long delayMs, int attemptsRemaining) {
        if (pendingHeroCapture != null) handler.removeCallbacks(pendingHeroCapture);
        pendingHeroCapture = () -> {
            pendingHeroCapture = null;
            RecommendationTitleParser.Source source = sourceFromWindowPayloads();
            if (source.isEmpty()) {
                // Never clear a title that is still fresh. On this launcher the
                // bottom "Top picks for you" row exposes only "Column N" on both
                // focus and click, so the hero panel is the only place the card's
                // title ever appears, and it is read on a timer. Clearing it on an
                // empty poll is what left the divert with nothing to use, which is
                // how a click ended up back in stock YouTube.
                if (!hasFreshFocusedHeroSource()) {
                    focusedHeroSource = source;
                    focusedHeroCapturedAt = 0;
                }
                if (attemptsRemaining > 1) {
                    // Hero metadata arrives after the focus event. Poll briefly so a
                    // click made as soon as it becomes visible still has the title.
                    scheduleHeroCapture(100L, attemptsRemaining - 1);
                }
                return;
            }
            focusedHeroSource = source;
            focusedHeroCapturedAt = SystemClock.elapsedRealtime();
            Diagnostics.debug("Cached focused YouTube title: " + source.title);
        };
        handler.postDelayed(pendingHeroCapture, delayMs);
    }

    private void clearFocusedHeroSource() {
        focusedHeroSource = RecommendationTitleParser.Source.NONE;
        focusedHeroCapturedAt = 0;
    }

    private boolean hasFreshFocusedHeroSource() {
        return !focusedHeroSource.isEmpty() && DivertPolicy.isFresh(
                focusedHeroCapturedAt, SystemClock.elapsedRealtime(), FOCUSED_HERO_WINDOW_MS);
    }

    /** Records the card payload as the primary divert source. */
    private void rememberCard(RecommendationTitleParser.Source source) {
        if (source == null || source.isEmpty()) return;
        lastCardSource = source;
        lastCardCapturedAt = SystemClock.elapsedRealtime();
    }

    private void clearLastCardSource() {
        lastCardSource = RecommendationTitleParser.Source.NONE;
        lastCardCapturedAt = 0L;
    }

    /** True when a cached source is a YouTube-marked title we can search for. */
    private static boolean isUsableYouTubeSource(RecommendationTitleParser.Source source) {
        return source != null && !source.isEmpty() && source.youtube;
    }

    /**
     * Diverts the stock-YouTube window change to the configured YouTube target.
     *
     * <p>The launcher fires stock YouTube itself with an explicit,
     * package-targeted intent, so this divert is the only guaranteed redirect,
     * and it is only possible when a card title was captured before the click
     * landed. The clicked card wins over the ambient panel. When neither is
     * usable the miss is logged rather than guessed at: searching YouTube for
     * an unrelated video is worse than leaving stock YouTube on screen.
     */
    private void divertStockYouTube() {
        if (isWhitelistedProvider("youtube")) {
            clearDivert();
            clearLastCardSource();
            clearFocusedHeroSource();
            return;
        }
        if (reassertLastDivert()) return;
        // Focusing a recommendation is not authorisation to hijack a later
        // manual/remote-button YouTube launch. A launcher click must precede it.
        if (!DivertPolicy.isFresh(lastLauncherClickAt, SystemClock.elapsedRealtime(),
                DivertPolicy.CLICKED_CARD_TTL_MS)) return;
        long now = SystemClock.elapsedRealtime();
        boolean clickedUsable = isUsableYouTubeSource(lastCardSource);
        boolean heroUsable = isUsableYouTubeSource(focusedHeroSource);
        DivertPolicy.Choice choice = DivertPolicy.choose(
                lastCardCapturedAt, clickedUsable,
                focusedHeroCapturedAt, heroUsable, now);
        if (choice == DivertPolicy.Choice.CLICKED_CARD) {
            RecommendationTitleParser.Source source = lastCardSource;
            long age = now - lastCardCapturedAt;
            clearLastCardSource();
            Diagnostics.debug("Stock YouTube divert: clicked card (age " + age + "ms) "
                    + source.title);
            dispatchTitle(source.lookupTitle(), true, source.provider);
            return;
        }
        if (choice == DivertPolicy.Choice.HERO_PANEL) {
            RecommendationTitleParser.Source source = focusedHeroSource;
            long age = now - focusedHeroCapturedAt;
            clearFocusedHeroSource();
            Diagnostics.debug("Stock YouTube divert: ambient panel (age " + age + "ms) "
                    + source.title);
            dispatchTitle(source.lookupTitle(), true, source.provider);
            return;
        }
        Diagnostics.debug("Divert missed: no card title cached (clicked="
                + describeCache(lastCardSource, lastCardCapturedAt, now)
                + ", hero=" + describeCache(focusedHeroSource, focusedHeroCapturedAt, now) + ")");
    }

    /** Records a successful divert so a resurfacing stock YouTube can be answered once. */
    private void noteDiverted(String title, String target, long generation) {
        lastDivertTitle = title;
        lastDivertAt = SystemClock.elapsedRealtime();
        lastDivertTarget = target;
        lastDivertGeneration = generation;
        divertReassertsLeft = 1;
        // This selection is consumed. Further window changes can use only the
        // single guarded reassert, not start a new dispatch from the old card.
        lastLauncherClickAt = 0L;
        clearLastCardSource();
        clearFocusedHeroSource();
    }

    /**
     * The launcher's own stock YouTube launch can still land after the redirect,
     * especially when it was already warm. When its window comes forward again
     * straight after a divert, re-assert the same search once: the title is
     * already known from that divert, so nothing is guessed and a manual YouTube
     * open long afterwards is never hijacked.
     */
    private boolean reassertLastDivert() {
        if (divertReassertsLeft <= 0 || lastDivertTitle.isEmpty()) return false;
        if (!DivertPolicy.isFresh(lastDivertAt, SystemClock.elapsedRealtime(), DIVERT_REASSERT_WINDOW_MS)
                || !canLaunch(lastDivertGeneration, "youtube")
                || !lastDivertTarget.equals(AppPrefs.youtubeTarget(this))) {
            clearDivert();
            return false;
        }
        divertReassertsLeft--;
        final String title = lastDivertTitle;
        final String target = lastDivertTarget;
        final long generation = lastDivertGeneration;
        handler.post(() -> {
            if (!canLaunch(generation, "youtube") || !target.equals(AppPrefs.youtubeTarget(this))) return;
            try {
                // Do not arm another reassert: one successful redirect permits
                // at most one attempt to answer a late stock-YouTube window.
                if (openYouTubeTarget(title)) showRedirectBadge();
            } catch (RuntimeException error) {
                notifyUser(R.string.status_redirect_failed);
            }
        });
        return true;
    }

    /** Compact cache state, for diagnosing a divert miss. */
    private static String describeCache(RecommendationTitleParser.Source source,
            long capturedAt, long now) {
        if (source == null || source.isEmpty()) return "empty";
        return "\"" + source.title + "\" age=" + (now - capturedAt)
                + "ms youtube=" + source.youtube;
    }

    /**
     * Debug builds only: inspect an event without retaining its source node.
     * Each getSource() result is a separate owned acquisition.
     */
    private void logRawLauncherEvent(AccessibilityEvent event) {
        if (!BuildConfig.DEBUG) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_SELECTED) {
            return;
        }
        StringBuilder line = new StringBuilder("RAW ").append(eventTypeName(type))
                .append(" class=").append(toString(event.getClassName()))
                .append(" text=").append(clip(joinEventText(event.getText())))
                .append(" desc=").append(clip(toString(event.getContentDescription())));
        AccessibilityNodeInfo source = null;
        try {
            source = event.getSource();
            if (source != null) {
                android.graphics.Rect bounds = new android.graphics.Rect();
                source.getBoundsInScreen(bounds);
                line.append(" node[class=").append(toString(source.getClassName()))
                        .append(" id=").append(source.getViewIdResourceName())
                        .append(" text=").append(clip(toString(source.getText())))
                        .append(" desc=").append(clip(toString(source.getContentDescription())))
                        .append(" bounds=").append(bounds.toShortString())
                        .append(']');
            }
        } catch (RuntimeException rawError) {
            line.append(" node[unreadable]");
        } finally {
            if (source != null) source.recycle();
        }
        Diagnostics.debug(line.toString());
    }

    private static String eventTypeName(int type) {
        switch (type) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED: return "CLICKED";
            case AccessibilityEvent.TYPE_VIEW_FOCUSED: return "FOCUSED";
            case AccessibilityEvent.TYPE_VIEW_SELECTED: return "SELECTED";
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: return "WINDOW_STATE";
            default: return String.valueOf(type);
        }
    }

    private static String joinEventText(List<CharSequence> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder joined = new StringBuilder();
        for (CharSequence value : values) {
            if (joined.length() > 0) joined.append(" | ");
            joined.append(value);
        }
        return joined.toString();
    }

    private static String clip(String value) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), 160));
    }

    /**
     * Carries the cached focused-card provider into an authoritative detail
     * title. Returns the provider only when the cache is fresh, non-YouTube,
     * provider-bearing, and title-matched; otherwise {@code ""} so detail
     * dispatches stay providerless exactly as before.
     */
    private String focusedProviderForDetail(String detailTitle) {
        if (detailTitle == null || detailTitle.isEmpty()) return "";
        if (!hasFreshFocusedHeroSource()) return "";
        return DispatchPolicy.focusedProviderForDetail(
                focusedHeroSource.lookupTitle(), focusedHeroSource.youtube, focusedHeroSource.provider,
                focusedHeroCapturedAt, detailTitle,
                SystemClock.elapsedRealtime(), FOCUSED_HERO_WINDOW_MS);
    }

    private void handleLauncherClick(AccessibilityEvent event) {
        cancelTitleRetry();
        RecommendationTitleParser.Source source =
                RecommendationTitleParser.fromEventTextSource(event.getText());
        RecommendationTitleParser.Source described = RecommendationTitleParser.fromDescriptionSource(
                toString(event.getContentDescription()));
        source = RecommendationTitleParser.withProviderContext(source, described);
        if (!source.isEmpty() && !source.hasProvider() && hasFreshFocusedHeroSource()
                && TitleResultHelper.compatibleTitles(source.lookupTitle(), focusedHeroSource.lookupTitle())) {
            source = RecommendationTitleParser.withProviderContext(source, focusedHeroSource);
        }
        if (source.isEmpty()) {
            // Some cards (notably YouTube) emit click events with no text or
            // description; the payload lives on the card container, an ancestor
            // of the clicked view.
            source = sourceFromClickedNode(event);
        }
        if (!source.isEmpty()) {
            // This payload came from the click event or the clicked node, so it
            // describes the card the user actually pressed: remember it as the
            // primary source for a stock-YouTube divert before any panel cache
            // can be consulted.
            rememberCard(source);
            if (!source.youtube && focusedHeroSource.youtube) clearFocusedHeroSource();
            dispatchTitle(source.lookupTitle(), source.youtube, source.provider);
        } else {
            logRejectedPayload(event);
            // Detail-action fix (live Shang-Chi no-op): a click on an entity
            // detail action carries no direct payload while the detail tree
            // already exposes the exact title row. Consult it first; only when
            // absent do the existing window/hero fallbacks run.
            String detailTitle = titleFromDetailRoot();
            if (!detailTitle.isEmpty()) {
                String detailProvider = focusedProviderForDetail(detailTitle);
                if (!detailProvider.isEmpty()) clearFocusedHeroSource();
                dispatchTitle(detailTitle, false, detailProvider);
                return;
            }
            // The click carried nothing usable, but the card being pressed was
            // captured when it gained focus. Prefer that over the ambient panel,
            // whose scan reads launcher chrome and once searched YouTube for
            // "Column 3" instead of the card's video.
            if (isUsableYouTubeSource(lastCardSource)
                    && DivertPolicy.isFresh(lastCardCapturedAt,
                            SystemClock.elapsedRealtime(), FOCUSED_HERO_WINDOW_MS)) {
                RecommendationTitleParser.Source cached = lastCardSource;
                clearLastCardSource();
                Diagnostics.debug("Click payload unusable; using the focused card title");
                dispatchTitle(cached.lookupTitle(), cached.youtube, cached.provider);
                return;
            }
            source = sourceFromWindowPayloads();
            if (source.isEmpty() && hasFreshFocusedHeroSource()) {
                source = focusedHeroSource;
            }
            if (!source.isEmpty()) {
                clearFocusedHeroSource();
                dispatchTitle(source.lookupTitle(), source.youtube, source.provider);
                return;
            }
            scheduleTitleRetry();
        }
    }

    /**
     * Extracts a card payload around the clicked view. Some launcher builds host
     * card content in a sibling branch of the accessibility tree, so a plain
     * subtree scan of the clicked view only sees the column label. The fix scans
     * the active window for text nodes whose screen bounds intersect the clicked
     * card's region, feeds those to the classifier, and falls back to the
     * ancestor chain. Nodes are recycled immediately.
     */
    private RecommendationTitleParser.Source sourceFromClickedNode(AccessibilityEvent event) {
        AccessibilityNodeInfo owned = event.getSource();
        if (owned == null) return RecommendationTitleParser.Source.NONE;
        try {
            android.graphics.Rect clickBounds = new android.graphics.Rect();
            owned.getBoundsInScreen(clickBounds);
            List<CharSequence> collected = new ArrayList<>();
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                try {
                    if (LAUNCHER_PACKAGE.contentEquals(toString(root.getPackageName()))
                            && (event.getWindowId() < 0 || root.getWindowId() == event.getWindowId())) {
                        collectNearbyPayloadTexts(root, 0, clickBounds, collected);
                    }
                } finally {
                    root.recycle();
                }
            }
            RecommendationTitleParser.Source found =
                    RecommendationTitleParser.fromEventTextSource(collected);
            if (!found.isEmpty()) return found;
            if (!collected.isEmpty()) {
                Diagnostics.debug("Card region payload: " + collected);
            }
            return cardSourceFromAncestors(owned);
        } catch (RuntimeException walkError) {
            Diagnostics.debug("Could not read clicked card payload: " + walkError.getMessage());
            return RecommendationTitleParser.Source.NONE;
        } finally {
            owned.recycle();
        }
    }

    /** Depth- and count-bounded scan collecting payloads near the clicked card's bounds. */
    private void collectNearbyPayloadTexts(AccessibilityNodeInfo node, int depth,
            android.graphics.Rect clickBounds, List<CharSequence> out) {
        if (node == null || depth > 7 || out.size() > 60) return;
        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        android.graphics.Rect region = new android.graphics.Rect(clickBounds);
        region.inset(-60, -60);
        if (android.graphics.Rect.intersects(bounds, region)) {
            String text = toString(node.getText());
            if (!text.isEmpty()) out.add(text);
            String description = toString(node.getContentDescription());
            if (!description.isEmpty()) out.add(description);
        }
        for (int index = 0; index < node.getChildCount() && out.size() <= 60; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            try {
                collectNearbyPayloadTexts(child, depth + 1, clickBounds, out);
            } finally {
                child.recycle();
            }
        }
    }

    /**
     * Borrows the clicked root; owns and recycles only parents acquired here.
     * The caller remains the sole owner of the root, including on exceptions.
     */
    private RecommendationTitleParser.Source cardSourceFromAncestors(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo owned = root;
        try {
            for (int depth = 0; depth < 8; depth++) {
                RecommendationTitleParser.Source found = cardSourceFromNode(owned);
                if (!found.isEmpty()) return found;
                AccessibilityNodeInfo parent = owned.getParent();
                if (owned != root) owned.recycle();
                owned = parent;
                if (owned == null) break;
            }
            return RecommendationTitleParser.Source.NONE;
        } catch (RuntimeException walkError) {
            Diagnostics.debug("Could not read clicked card payload: " + walkError.getMessage());
            return RecommendationTitleParser.Source.NONE;
        } finally {
            if (owned != null && owned != root) owned.recycle();
        }
    }

    /** Tries the node's content description, text, and first child for a card payload. */
    private RecommendationTitleParser.Source cardSourceFromNode(AccessibilityNodeInfo node) {
        RecommendationTitleParser.Source found = RecommendationTitleParser.fromDescriptionSource(
                toString(node.getContentDescription()));
        if (found.isEmpty()) {
            found = RecommendationTitleParser.fromDirectTextSource(toString(node.getText()));
        }
        if (found.isEmpty() && node.getChildCount() > 0) {
            AccessibilityNodeInfo child = node.getChild(0);
            if (child != null) {
                try {
                    found = RecommendationTitleParser.fromDescriptionSource(
                            toString(child.getContentDescription()));
                } finally {
                    child.recycle();
                }
            }
        }
        return found;
    }

    private void handleEntityWindow() {
        cancelTitleRetry();
        String title = titleFromDetailRoot();
        if (!title.isEmpty()) {
            // Entity detail windows carry no provider marker themselves; the
            // focused-card provider (cached at focus time) is carried in only
            // on a fresh title match. YouTube routing stays click-payload driven.
            String detailProvider = focusedProviderForDetail(title);
            if (!detailProvider.isEmpty()) clearFocusedHeroSource();
            dispatchTitle(title, false, detailProvider);
        } else {
            scheduleTitleRetry();
        }
    }

    /** Retries against a fresh root; no AccessibilityNodeInfo is retained across events. */
    private void scheduleTitleRetry() {
        cancelTitleRetry();
        scheduleTitleRetry(TITLE_EARLY_RETRY_DELAY_MS, true);
    }

    private void scheduleTitleRetry(long delayMs, boolean allowFinalRetry) {
        pendingTitleRetry = () -> {
            pendingTitleRetry = null;
            String title = titleFromDetailRoot();
            if (!title.isEmpty()) {
                String detailProvider = focusedProviderForDetail(title);
                if (!detailProvider.isEmpty()) clearFocusedHeroSource();
                dispatchTitle(title, false, detailProvider);
            } else if (allowFinalRetry) {
                scheduleTitleRetry(
                        TITLE_FINAL_RETRY_DELAY_MS - TITLE_EARLY_RETRY_DELAY_MS, false);
            }
        };
        handler.postDelayed(pendingTitleRetry, delayMs);
    }

    /** Uses the launcher's stable ID, then scans only that row's live subtree. */
    private String titleFromDetailRoot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        try {
            if (!LAUNCHER_PACKAGE.contentEquals(toString(root.getPackageName()))) return "";
            List<AccessibilityNodeInfo> rows = root.findAccessibilityNodeInfosByViewId(DETAIL_TITLE_ID);
            if (rows == null) return "";
            try {
                for (AccessibilityNodeInfo row : rows) {
                    if (row == null) continue;
                    String title = titleFromDetailRow(row);
                    if (!title.isEmpty()) return title;
                }
                return "";
            } finally {
                for (AccessibilityNodeInfo row : rows) if (row != null) row.recycle();
            }
        } catch (RuntimeException error) {
            Diagnostics.debug("Could not read entity title row: " + error.getMessage());
            return "";
        } finally {
            root.recycle();
        }
    }

    private String titleFromDetailRow(AccessibilityNodeInfo node) {
        // Authoritative detail title row: parse with the dedicated bounded
        // detail method (same guards, 15-word cap) so long exact titles such
        // as the 8-word Shang-Chi title survive. General fail-closed limits
        // elsewhere are untouched.
        String title = detailLookupTitle(toString(node.getText()));
        if (!title.isEmpty()) return title;
        title = detailLookupTitle(toString(node.getContentDescription()));
        if (!title.isEmpty()) return title;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            try {
                title = titleFromDetailRow(child);
                if (!title.isEmpty()) return title;
            } finally {
                child.recycle();
            }
        }
        return "";
    }

    private String detailLookupTitle(String raw) {
        RecommendationTitleParser.Source detail = RecommendationTitleParser.fromDetailTitleSource(raw);
        if (detail.isEmpty()) return "";
        if (detail.year.isEmpty() && hasFreshFocusedHeroSource() && !focusedHeroSource.youtube
                && TitleResultHelper.compatibleTitles(detail.title, focusedHeroSource.lookupTitle())) {
            return focusedHeroSource.lookupTitle();
        }
        return detail.lookupTitle();
    }

    /** Routes a resolved title to the configured film/series destination. */
    boolean openMoviesTarget(String target, TitleMatch match) {
        if (AppPrefs.MOVIES_STREMIO.equals(target)) return StremioLauncher.open(this, match);
        if (AppPrefs.MOVIES_WUPLAY.equals(target)) return WuPlayLauncher.open(this, match);
        return NuvioLauncher.open(this, match);
    }

    boolean openYouTubeTarget(String title) { return YouTubeLauncher.open(this, title); }

    TitleMatch lookupTitle(String key, String title) throws java.io.IOException {
        return new TmdbClient(key).searchBest(title);
    }

    void showRedirectBadge() { RedirectBadge.show(this); }

    /** Called on the main thread immediately before a launch, including reasserts. */
    private boolean canLaunch(long generation, String provider) {
        return interactionSession.isCurrent(generation) && !isWhitelistedProvider(provider);
    }

    private void dispatchTitle(String title, boolean youtube, String provider) {
        RecommendationTitleParser.Source parsed = youtube
                ? RecommendationTitleParser.youtubeSource(title)
                : RecommendationTitleParser.fromDetailTitleSource(title);
        if (parsed.isEmpty()) return;
        String query = parsed.lookupTitle();
        long now = SystemClock.elapsedRealtime();
        // A different entity window also supersedes a pending lookup, even on a
        // launcher build that did not deliver its click event.
        if (!lastDispatchedTitle.isEmpty() && lastDispatchedGeneration == interactionSession.ticket()
                && (!TitleResultHelper.compatibleTitles(lastDispatchedTitle, query)
                    || youtube != lastDispatchedYoutube)) {
            interactionSession.invalidate();
            clearDivert();
        }
        if (isWhitelistedProvider(provider)) {
            clearDivert();
            lastBypassedTitle = query;
            lastBypassedYoutube = youtube;
            lastBypassedAt = now;
            return;
        }
        if (DispatchPolicy.shouldSuppressProviderlessFallback(
                lastBypassedTitle, lastBypassedYoutube, lastBypassedAt,
                query, youtube, provider, now, DUPLICATE_WINDOW_MS)) return;
        long generation = interactionSession.ticket();
        if (generation == lastDispatchedGeneration
                && TitleResultHelper.compatibleTitles(lastDispatchedTitle, query)
                && youtube == lastDispatchedYoutube
                && now - lastDispatchedAt < DUPLICATE_WINDOW_MS) return;
        lastDispatchedTitle = query;
        lastDispatchedYoutube = youtube;
        lastDispatchedAt = now;
        lastDispatchedGeneration = generation;
        Diagnostics.debug("Recommendation: " + query);
        worker.execute(() -> resolveAndOpen(query, youtube, provider, generation));
    }

    private void resolveAndOpen(String title, boolean youtube, String provider, long generation) {
        if (!interactionSession.isCurrent(generation)) return;
        if (matchesInstalledAppLabel(TitleResultHelper.cleanTitle(title))) return;
        if (youtube) {
            String target = AppPrefs.youtubeTarget(this);
            handler.post(() -> {
                if (!canLaunch(generation, provider) || !target.equals(AppPrefs.youtubeTarget(this))) return;
                try {
                    if (openYouTubeTarget(title)) {
                        noteDiverted(title, target, generation);
                        showRedirectBadge();
                    }
                } catch (RuntimeException error) {
                    notifyUser(R.string.status_redirect_failed);
                }
            });
            return;
        }
        android.content.SharedPreferences prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE);
        String key = prefs.getString(AppPrefs.TMDB_KEY, "").trim();
        String target = AppPrefs.moviesTarget(this);
        if (key.length() < 10) {
            notifyCurrent(generation, provider, R.string.status_key_missing);
            return;
        }
        try {
            if (MatchCache.isMiss(title)) {
                notifyCurrent(generation, provider, R.string.status_no_match);
                return;
            }
            TitleMatch match = MatchCache.get(title);
            if (match == null) {
                match = lookupTitle(key, title);
                if (!interactionSession.isCurrent(generation)) return;
                if (match == null) {
                    MatchCache.putMiss(title);
                    notifyCurrent(generation, provider, R.string.status_no_match);
                    return;
                }
                MatchCache.put(title, match);
            }
            final TitleMatch resolved = match;
            handler.post(() -> {
                if (!canLaunch(generation, provider) || !target.equals(AppPrefs.moviesTarget(this))) return;
                try {
                    if (openMoviesTarget(target, resolved)) showRedirectBadge();
                } catch (RuntimeException error) {
                    notifyUser(R.string.status_redirect_failed);
                }
            });
        } catch (TmdbClient.InvalidApiKeyException error) {
            if (key.equals(prefs.getString(AppPrefs.TMDB_KEY, "").trim())) {
                notifyCurrent(generation, provider, R.string.key_rejected);
            }
        } catch (java.net.SocketTimeoutException error) {
            notifyCurrent(generation, provider, R.string.status_lookup_timeout);
        } catch (Exception error) {
            // Do not log exception messages: transport errors can contain URLs
            // with title queries or the user's TMDB credential.
            notifyCurrent(generation, provider, R.string.status_lookup_failed);
        }
    }

    private void notifyCurrent(long generation, String provider, int message) {
        handler.post(() -> {
            if (canLaunch(generation, provider)) notifyUser(message);
        });
    }

    private void collectNodeTexts(AccessibilityNodeInfo node, int depth,
            java.util.LinkedHashSet<String> out) {
        collectNodeTexts(node, depth, out, new int[] {256});
    }

    private void collectNodeTexts(AccessibilityNodeInfo node, int depth,
            java.util.LinkedHashSet<String> out, int[] budget) {
        if (node == null || depth > 12 || out.size() > 100 || budget[0]-- <= 0
                || !node.isVisibleToUser()) return;
        String text = toString(node.getText());
        if (!text.isEmpty() && out.size() <= 100) {
            out.add(text.substring(0, Math.min(text.length(), 300))
                    + "@y=" + boundsTop(node));
        }
        String description = toString(node.getContentDescription());
        if (!description.isEmpty() && out.size() <= 100) {
            out.add("[desc]" + description.substring(0, Math.min(description.length(), 300))
                    + "@y=" + boundsTop(node));
        }
        for (int index = 0; index < node.getChildCount() && out.size() <= 100; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            try {
                collectNodeTexts(child, depth + 1, out, budget);
            } finally {
                child.recycle();
            }
        }
    }

    private int boundsTop(AccessibilityNodeInfo node) {
        try {
            android.graphics.Rect bounds = new android.graphics.Rect();
            node.getBoundsInScreen(bounds);
            return bounds.top;
        } catch (RuntimeException error) {
            return -1;
        }
    }

    /** YouTube cards expose their title and provider in the hero panel. */
    private RecommendationTitleParser.Source sourceFromWindowPayloads() {
        if (interactionSession.isEditing()) return RecommendationTitleParser.Source.NONE;
        List<String> entries = new ArrayList<>();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return RecommendationTitleParser.Source.NONE;
        try {
            if (!LAUNCHER_PACKAGE.contentEquals(toString(root.getPackageName()))) {
                return RecommendationTitleParser.Source.NONE;
            }
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            collectNodeTexts(root, 0, seen);
            entries.addAll(seen);
        } catch (RuntimeException error) {
            return RecommendationTitleParser.Source.NONE;
        } finally {
            root.recycle();
        }

        int youtubeTop = -1;
        for (String entry : entries) {
            String payload = entry;
            if (payload.startsWith("[desc]")) payload = payload.substring(6);
            int yMarker = payload.lastIndexOf("@y=");
            if (yMarker < 0) continue;
            int top = parsePayloadTop(payload, yMarker);
            payload = payload.substring(0, yMarker).trim();
            // Ignore the YouTube app tile at y~1000 and unrelated lower rows.
            if (top >= 300 && top < 650 && "youtube".equalsIgnoreCase(payload)) youtubeTop = top;
        }
        if (youtubeTop < 0) return RecommendationTitleParser.Source.NONE;

        String heroTitle = "";
        int heroTitleTop = -1;
        for (String entry : entries) {
            if (entry.startsWith("[desc]")) continue;
            int yMarker = entry.lastIndexOf("@y=");
            if (yMarker < 0) continue;
            int top = parsePayloadTop(entry, yMarker);
            if (top < 150 || top >= youtubeTop || top <= heroTitleTop) continue;
            String payload = entry.substring(0, yMarker).trim();
            RecommendationTitleParser.Source found = RecommendationTitleParser.youtubeSource(payload);
            if (!found.isEmpty()) {
                heroTitle = found.title;
                heroTitleTop = top;
            }
        }
        return heroTitle.isEmpty()
                ? RecommendationTitleParser.Source.NONE
                : RecommendationTitleParser.youtubeSource(heroTitle);
    }

    private int parsePayloadTop(String payload, int yMarker) {
        try {
            return Integer.parseInt(payload.substring(yMarker + 3));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void logRejectedPayload(AccessibilityEvent event) {
        if (!BuildConfig.DEBUG) return;
        String text = String.join(" | ", event.getText() == null
                ? java.util.Collections.emptyList() : event.getText());
        String description = toString(event.getContentDescription());
        String payload = !text.trim().isEmpty() ? text : description;
        String extra = "";
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            try {
                extra = " (view=" + source.getViewIdResourceName()
                        + ", class=" + source.getClassName() + ")";
            } finally {
                source.recycle();
            }
        }
        if (payload.trim().isEmpty()) {
            if (!extra.trim().isEmpty()) {
                Diagnostics.debug("No credible title in launcher payload:" + extra);
            }
            return;
        }
        if (payload.length() > 300) payload = payload.substring(0, 300) + "…";
        Diagnostics.debug("No credible title in launcher payload: " + payload + extra);
    }

    void notifyUser(int message) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } else {
            handler.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        }
    }

    /**
     * True when the card's canonical provider identity is whitelisted. Matching is
     * by exact provider identity from a recognised provider edge or watch action;
     * the title itself never participates. Entity-window retries carry a provider
     * only via the fresh title-matched focus bridge, otherwise "" which bypasses
     * nothing.
     */
    private boolean isWhitelistedProvider(String provider) {
        String id = RecommendationTitleParser.canonicalWhitelistId(provider);
        if (id.isEmpty()) return false;
        try {
            return ProviderWhitelist.contains(AppPrefs.whitelist(this), id);
        } catch (RuntimeException prefsError) {
            // An unreadable policy is not authorisation to redirect this provider.
            return true;
        }
    }
    /** True when the title exactly matches an installed app's display label. */
    private boolean matchesInstalledAppLabel(String title) {
        Set<String> labels = installedAppLabels;
        if (labels == null) {
            // Worker-side fallback when a dispatch beats the connect-time warm:
            // load synchronously here (still off the accessibility thread, since
            // every caller runs on the worker) so the check is never skipped.
            labels = loadInstalledAppLabels();
        }
        return AppLabelPolicy.matches(labels, title);
    }

    /**
     * Loads and publishes the installed-app label set. Runs on the worker via
     * {@link #onServiceConnected} warmup or lazily above; never on the
     * accessibility callback thread. Matching semantics are unchanged.
     */
    private Set<String> loadInstalledAppLabels() {
        try {
            Set<String> loaded = new HashSet<>();
            PackageManager packageManager = getPackageManager();
            List<android.content.pm.ApplicationInfo> apps =
                    packageManager.getInstalledApplications(0);
            java.util.List<String> raw = new java.util.ArrayList<>(apps.size());
            for (android.content.pm.ApplicationInfo app : apps) {
                raw.add(String.valueOf(packageManager.getApplicationLabel(app)));
            }
            // The tile can use an activity/alias label instead of the app label
            // (e.g. a file manager's launcher name). Both launcher categories
            // have matching visibility declarations in AndroidManifest.xml.
            for (String category : new String[] {
                    Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER }) {
                Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
                for (android.content.pm.ResolveInfo activity
                        : packageManager.queryIntentActivities(intent, 0)) {
                    raw.add(String.valueOf(activity.loadLabel(packageManager)));
                }
            }
            loaded.addAll(AppLabelPolicy.normalizeAll(raw));
            installedAppLabels = loaded;
            return loaded;
        } catch (RuntimeException labelError) {
            return null;
        }
    }

    private volatile Set<String> installedAppLabels;

    private static boolean isEntityWindow(CharSequence className) {
        return className != null && className.toString().endsWith(DETAIL_ACTIVITY_SUFFIX);
    }

    private static boolean isHomeWindow(CharSequence className) {
        return className != null && className.toString().endsWith(HOME_ACTIVITY_SUFFIX);
    }

    private static String toString(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private void cancelTitleRetry() {
        if (pendingTitleRetry != null) {
            handler.removeCallbacks(pendingTitleRetry);
            pendingTitleRetry = null;
        }
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        interactionSession.invalidate();
        heartbeatHandler.removeCallbacks(heartbeat);
        if (pendingHeroCapture != null) handler.removeCallbacks(pendingHeroCapture);
        getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit()
                .remove(AppPrefs.SERVICE_CONNECTED_AT).apply();
        cancelTitleRetry();
        worker.shutdownNow();
        super.onDestroy();
    }
}

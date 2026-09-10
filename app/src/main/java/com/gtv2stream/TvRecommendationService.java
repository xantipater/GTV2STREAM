package com.gtv2stream;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.content.pm.PackageManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
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
public final class TvRecommendationService extends AccessibilityService {
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
    /** Build identifier, logged on connect so a test run can name its own APK. */
    private static final String BUILD_STAMP = "shangchi-1";

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gtv2stream-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingTitleRetry;
    private Runnable pendingHeroCapture;
    private RecommendationTitleParser.Source focusedHeroSource =
            RecommendationTitleParser.Source.NONE;
    private long focusedHeroCapturedAt;
    /**
     * The last real card payload, captured either when a card was focused or
     * from a click event that actually carried one. This is the primary source
     * for both the stock-YouTube divert and the click fallback, because the
     * ambient panel poll in {@link #focusedHeroSource} reads every text node in
     * every window and is not trustworthy enough to be first. Never written by
     * the panel poll.
     */
    private RecommendationTitleParser.Source lastCardSource =
            RecommendationTitleParser.Source.NONE;
    private long lastCardCapturedAt;
    /** Throttle for the focused-YouTube-card kill, so scrolling cannot spam the framework. */
    private static final long STOCK_YOUTUBE_KILL_THROTTLE_MS = 2000L;
    private long lastStockYoutubeKillAt;
    /** Window in which stock YouTube resurfacing may be answered with one re-assert. */
    private static final long DIVERT_REASSERT_WINDOW_MS = 6000L;
    private String lastDivertTitle = "";
    private long lastDivertAt;
    private int divertReassertsLeft;
    private String lastDispatchedTitle = "";
    private boolean lastDispatchedYoutube;
    private long lastDispatchedAt;
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
        Log.i(TAG, "Accessibility service connected for " + LAUNCHER_PACKAGE
                + " build=" + BUILD_STAMP);
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
            return;
        }
        logRawLauncherEvent(event);
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            handleLauncherClick(event);
        } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && isEntityWindow(event.getClassName())) {
            handleEntityWindow();
        } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && isHomeWindow(event.getClassName())) {
            // The launcher's already-focused card may not emit a new focus event
            // when Home opens. Capture its hero payload before a quick click can
            // hand the recommendation to stock YouTube.
            scheduleHeroCapture(100L, 12);
        } else if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                || type == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            handleLauncherFocus(event);
        }
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
                    focusedHeroSource = immediate;
                    focusedHeroCapturedAt = System.currentTimeMillis();
                    Log.i(TAG, "Cached focused provider title: " + immediate.title
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
            focusedHeroSource = immediate;
            focusedHeroCapturedAt = System.currentTimeMillis();
            Log.i(TAG, "Cached focused provider title: " + immediate.title
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
            focusedHeroCapturedAt = System.currentTimeMillis();
            Log.i(TAG, "Cached focused YouTube title: " + source.title);
            noteYoutubeCardFocused();
        };
        handler.postDelayed(pendingHeroCapture, delayMs);
    }

    private void clearFocusedHeroSource() {
        focusedHeroSource = RecommendationTitleParser.Source.NONE;
        focusedHeroCapturedAt = 0;
    }

    private boolean hasFreshFocusedHeroSource() {
        return !focusedHeroSource.isEmpty()
                && System.currentTimeMillis() - focusedHeroCapturedAt < FOCUSED_HERO_WINDOW_MS;
    }

    /** Records the card payload as the primary divert source. */
    private void rememberCard(RecommendationTitleParser.Source source) {
        if (source == null || source.isEmpty()) return;
        lastCardSource = source;
        lastCardCapturedAt = System.currentTimeMillis();
        if (isUsableYouTubeSource(source)) noteYoutubeCardFocused();
    }

    /**
     * Drops a backgrounded stock YouTube as soon as we know the focused card is
     * a YouTube video.
     *
     * <p>This is the only moment the kill can work. Once the card is clicked the
     * launcher has already brought its own copy of stock YouTube forward, and
     * {@code killBackgroundProcesses} cannot touch a foreground app, so a
     * post-click kill is a no-op. Killing it while it is still backgrounded makes
     * the launcher's own launch a cold start, which cannot out-race the redirect,
     * and leaves nothing resident to take the foreground back afterwards.
     * Throttled so scrolling across the row does not hammer the framework.
     */
    private void noteYoutubeCardFocused() {
        long now = System.currentTimeMillis();
        if (now - lastStockYoutubeKillAt < STOCK_YOUTUBE_KILL_THROTTLE_MS) return;
        lastStockYoutubeKillAt = now;
        worker.execute(() -> dropStockYouTube("focused YouTube card"));
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
        if (reassertLastDivert()) return;
        long now = System.currentTimeMillis();
        boolean clickedUsable = isUsableYouTubeSource(lastCardSource);
        boolean heroUsable = isUsableYouTubeSource(focusedHeroSource);
        DivertPolicy.Choice choice = DivertPolicy.choose(
                lastCardCapturedAt, clickedUsable,
                focusedHeroCapturedAt, heroUsable, now);
        if (choice == DivertPolicy.Choice.CLICKED_CARD) {
            RecommendationTitleParser.Source source = lastCardSource;
            long age = now - lastCardCapturedAt;
            clearLastCardSource();
            noteDiverted(source.title);
            Log.i(TAG, "Stock YouTube divert: clicked card (age " + age + "ms) "
                    + source.title);
            dispatchTitle(source.title, true, source.provider);
            return;
        }
        if (choice == DivertPolicy.Choice.HERO_PANEL) {
            RecommendationTitleParser.Source source = focusedHeroSource;
            long age = now - focusedHeroCapturedAt;
            clearFocusedHeroSource();
            noteDiverted(source.title);
            Log.i(TAG, "Stock YouTube divert: ambient panel (age " + age + "ms) "
                    + source.title);
            dispatchTitle(source.title, true, source.provider);
            return;
        }
        Log.w(TAG, "Divert missed: no card title cached (clicked="
                + describeCache(lastCardSource, lastCardCapturedAt, now)
                + ", hero=" + describeCache(focusedHeroSource, focusedHeroCapturedAt, now) + ")");
    }

    /** Records a successful divert so a resurfacing stock YouTube can be answered once. */
    private void noteDiverted(String title) {
        lastDivertTitle = title == null ? "" : title;
        lastDivertAt = System.currentTimeMillis();
        divertReassertsLeft = 1;
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
        long now = System.currentTimeMillis();
        if (now - lastDivertAt > DIVERT_REASSERT_WINDOW_MS) return false;
        divertReassertsLeft--;
        lastDivertAt = now;
        final String title = lastDivertTitle;
        Log.i(TAG, "Stock YouTube resurfaced after the redirect; re-asserting the search");
        worker.execute(() -> {
            if (YouTubeLauncher.open(this, title)) RedirectBadge.show(this);
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
     * Diagnostic: the raw truth of every click/focus event the launcher sends,
     * so a live test can prove what a card click actually carries instead of
     * inferring it from downstream behaviour. The event's source node is read
     * but never recycled here: the click handler runs moments later on the same
     * dispatch and reads that same node.
     */
    private void logRawLauncherEvent(AccessibilityEvent event) {
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
        try {
            AccessibilityNodeInfo source = event.getSource();
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
            line.append(" node[unreadable ").append(rawError.getMessage()).append(']');
        }
        Log.i(TAG, line.toString());
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
        return value.length() > 160 ? value.substring(0, 160) + "…" : value;
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
                focusedHeroSource.title, focusedHeroSource.youtube, focusedHeroSource.provider,
                focusedHeroCapturedAt, detailTitle,
                System.currentTimeMillis(), FOCUSED_HERO_WINDOW_MS);
    }

    private void handleLauncherClick(AccessibilityEvent event) {
        cancelTitleRetry();
        RecommendationTitleParser.Source source =
                RecommendationTitleParser.fromEventTextSource(event.getText());
        if (source.isEmpty()) {
            source = RecommendationTitleParser.fromDescriptionSource(
                    toString(event.getContentDescription()));
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
            dispatchTitle(source.title, source.youtube, source.provider);
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
                            System.currentTimeMillis(), FOCUSED_HERO_WINDOW_MS)) {
                RecommendationTitleParser.Source cached = lastCardSource;
                clearLastCardSource();
                Log.i(TAG, "Click payload unusable; using the focused card title");
                dispatchTitle(cached.title, cached.youtube, cached.provider);
                return;
            }
            source = sourceFromWindowPayloads();
            if (source.isEmpty() && hasFreshFocusedHeroSource()) {
                source = focusedHeroSource;
            }
            if (!source.isEmpty()) {
                clearFocusedHeroSource();
                dispatchTitle(source.title, source.youtube, source.provider);
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
                    collectNearbyPayloadTexts(root, 0, clickBounds, collected);
                } finally {
                    root.recycle();
                }
            }
            RecommendationTitleParser.Source found =
                    RecommendationTitleParser.fromEventTextSource(collected);
            if (!found.isEmpty()) return found;
            if (!collected.isEmpty()) {
                Log.i(TAG, "Card region payload: " + collected);
            }
            return cardSourceFromAncestors(owned);
        } catch (RuntimeException walkError) {
            Log.w(TAG, "Could not read clicked card payload: " + walkError.getMessage());
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
     * Walks the clicked view's ancestor chain (bounded) looking for the card's
     * rich content description or text. Nodes are recycled immediately.
     */
    private RecommendationTitleParser.Source cardSourceFromAncestors(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo owned = root;
        try {
            for (int depth = 0; depth < 8; depth++) {
                RecommendationTitleParser.Source found = cardSourceFromNode(owned);
                if (!found.isEmpty()) return found;
                AccessibilityNodeInfo parent = owned.getParent();
                owned.recycle();
                owned = parent;
                if (owned == null) break;
            }
            return RecommendationTitleParser.Source.NONE;
        } catch (RuntimeException walkError) {
            Log.w(TAG, "Could not read clicked card payload: " + walkError.getMessage());
            return RecommendationTitleParser.Source.NONE;
        } finally {
            if (owned != null) owned.recycle();
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
            List<AccessibilityNodeInfo> rows = root.findAccessibilityNodeInfosByViewId(DETAIL_TITLE_ID);
            if (rows == null) return "";
            for (AccessibilityNodeInfo row : rows) {
                if (row == null) continue;
                try {
                    String title = titleFromDetailRow(row);
                    if (!title.isEmpty()) return title;
                } finally {
                    row.recycle();
                }
            }
            return "";
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not read entity title row: " + error.getMessage());
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
        String title = RecommendationTitleParser.fromDetailTitle(toString(node.getText()));
        if (!title.isEmpty()) return title;
        title = RecommendationTitleParser.fromDetailTitle(toString(node.getContentDescription()));
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

    /**
     * Drops the stock YouTube background process. Runs on the worker, never on
     * the accessibility callback thread. {@code KILL_BACKGROUND_PROCESSES} is a
     * normal permission and cannot touch a foreground app, so this is safe at
     * any point: it only ever removes a backgrounded stock YouTube, which is
     * exactly the resident copy that can otherwise win the foreground back after
     * a redirect, or that makes the launcher's own launch fast enough to beat it.
     */
    private void dropStockYouTube(String phase) {
        try {
            android.app.ActivityManager manager =
                    (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (manager == null) return;
            manager.killBackgroundProcesses(STOCK_YOUTUBE_PACKAGE);
            Log.i(TAG, "Dropped stock YouTube background process (" + phase + ")");
        } catch (RuntimeException killError) {
            Log.w(TAG, "Could not drop stock YouTube (" + phase + "): "
                    + killError.getMessage());
        }
    }

    /** Routes a resolved title to the configured film/series destination. */
    private boolean openMoviesTarget(String target, TitleMatch match) {
        if (AppPrefs.MOVIES_STREMIO.equals(target)) return StremioLauncher.open(this, match);
        if (AppPrefs.MOVIES_WUPLAY.equals(target)) return WuPlayLauncher.open(this, match);
        return NuvioLauncher.open(this, match);
    }

    /** The deep link the selected destination will be given, for the log line only. */
    private static String moviesTargetUri(String target, TitleMatch match) {
        if (AppPrefs.MOVIES_STREMIO.equals(target)) return TitleResultHelper.stremioUri(match);
        if (AppPrefs.MOVIES_WUPLAY.equals(target)) return TitleResultHelper.wuplayUri(match);
        return TitleResultHelper.nuvioUri(match);
    }

    private void dispatchTitle(String title, boolean youtube) {
        dispatchTitle(title, youtube, "");
    }

    private void dispatchTitle(String title, boolean youtube, String provider) {
        String cleaned = youtube
                ? RecommendationTitleParser.youtubeSource(title).title
                : RecommendationTitleParser.fromDirectText(title);
        if (cleaned.isEmpty() && !youtube) {
            cleaned = RecommendationTitleParser.fromDescription(title);
        }
        if (cleaned.isEmpty() && !youtube) {
            // Authoritative detail-row titles allow up to 15 words with the
            // same guards; re-validate here so an exact detail title (e.g. the
            // 8-word Shang-Chi title) survives dispatch. Inputs are already
            // parsed titles, so general fail-closed limits stay intact.
            cleaned = RecommendationTitleParser.fromDetailTitle(title);
        }
        if (cleaned.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (isWhitelistedProvider(provider)) {
            // Whitelisted providers keep normal Google TV behaviour: no redirect,
            // no badge, no TMDB lookup. The bypass is recorded apart from the
            // dispatch bookkeeping below so the immediate providerless
            // EntityActivity fallback for the same title is suppressed without
            // ever blocking an explicit provider-carrying click.
            Log.i(TAG, "Whitelisted provider bypass: " + provider + " for " + cleaned);
            lastBypassedTitle = cleaned;
            lastBypassedYoutube = youtube;
            lastBypassedAt = now;
            return;
        }
        if (DispatchPolicy.shouldSuppressProviderlessFallback(
                lastBypassedTitle, lastBypassedYoutube, lastBypassedAt,
                cleaned, youtube, provider, now, DUPLICATE_WINDOW_MS)) {
            Log.i(TAG, "Suppressed providerless fallback after whitelisted bypass: " + cleaned);
            return;
        }
        if (TitleResultHelper.normalizedTitleMatches(lastDispatchedTitle, cleaned)
                && youtube == lastDispatchedYoutube
                && now - lastDispatchedAt < DUPLICATE_WINDOW_MS) return;
        lastDispatchedTitle = cleaned;
        lastDispatchedYoutube = youtube;
        lastDispatchedAt = now;
        Log.i(TAG, "Google TV recommendation title: " + cleaned
                + (youtube ? " (YouTube)" : ""));
        final String resolvedTitle = cleaned;
        worker.execute(() -> resolveAndOpen(resolvedTitle, youtube));
    }

    private void resolveAndOpen(String title, boolean youtube) {
        // Launcher app tiles read like single-word titles; fail closed. This
        // runs on the worker (not the accessibility callback) and the label
        // set is warmed on connect, so the first redirect never blocks event
        // delivery on a PackageManager enumeration.
        if (matchesInstalledAppLabel(title)) {
            Log.i(TAG, "Rejected launcher app tile label: " + title);
            return;
        }
        if (youtube) {
            // YouTube cards search by title; no TMDB lookup or key is needed.
            // The launcher starts its own copy of stock YouTube for these cards,
            // so drop any resident instance on both sides of the redirect: the
            // one before it is still backgrounded and forces the launcher's own
            // launch to cold-start, and the one after it is the launcher's copy,
            // now backgrounded by this redirect and no longer able to take the
            // foreground back.
            dropStockYouTube("before redirect");
            if (YouTubeLauncher.open(this, title)) {
                dropStockYouTube("after redirect");
                RedirectBadge.show(this);
            }
            return;
        }
        // One prefs read per click: the TMDB key and the film/series target come
        // from the same snapshot instead of two separate getSharedPreferences calls.
        android.content.SharedPreferences prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE);
        String key = prefs.getString(AppPrefs.TMDB_KEY, "").trim();
        if (key.length() < 10) {
            Log.w(TAG, "TMDB key missing or too short; configure GTV2STREAM first");
            notifyUser(R.string.status_key_missing);
            return;
        }
        try {
            String moviesTarget = LaunchPolicy.moviesTarget(
                    prefs.getString(AppPrefs.TARGET_MOVIES, AppPrefs.MOVIES_NUVIO));
            // Repeated selections reuse the recent match instead of re-querying TMDB.
            // Recent misses fail closed fast without a second network lookup.
            if (MatchCache.isMiss(title)) {
                Log.i(TAG, "Cached TMDB miss reused for: " + title);
                return;
            }
            TitleMatch match = MatchCache.get(title);
            if (match == null) {
                match = new TmdbClient(key).searchBest(title);
                if (match == null) {
                    Log.i(TAG, "No TMDB movie or series match for: " + title);
                    MatchCache.putMiss(title);
                    return;
                }
                MatchCache.put(title, match);
            } else {
                Log.i(TAG, "Cached TMDB match reused for: " + title);
            }
            boolean opened = openMoviesTarget(moviesTarget, match);
            Log.i(TAG, "TMDB match: " + match.title + " -> "
                    + moviesTargetUri(moviesTarget, match));
            if (opened) {
                RedirectBadge.show(this);
            }
        } catch (TmdbClient.InvalidApiKeyException error) {
            Log.w(TAG, "TMDB rejected the configured key");
            notifyUser(R.string.key_rejected);
        } catch (Exception error) {
            Log.w(TAG, "Recommendation lookup failed: " + error.getMessage());
        }
    }

    private void collectWindowTexts(AccessibilityWindowInfo window, List<String> out) {
        AccessibilityNodeInfo root = window.getRoot();
        if (root == null) return;
        try {
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            collectNodeTexts(root, 0, seen);
            out.addAll(seen);
        } finally {
            root.recycle();
        }
    }

    private void collectNodeTexts(AccessibilityNodeInfo node, int depth,
            java.util.LinkedHashSet<String> out) {
        if (node == null || depth > 12 || out.size() > 100) return;
        String text = toString(node.getText());
        if (!text.isEmpty() && out.size() <= 100) {
            out.add((text.length() > 200 ? text.substring(0, 300) : text)
                    + "@y=" + boundsTop(node));
        }
        String description = toString(node.getContentDescription());
        if (!description.isEmpty() && out.size() <= 100) {
            out.add("[desc]" + description + "@y=" + boundsTop(node));
        }
        for (int index = 0; index < node.getChildCount() && out.size() <= 100; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            try {
                collectNodeTexts(child, depth + 1, out);
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
        List<String> entries = new ArrayList<>();
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return RecommendationTitleParser.Source.NONE;
        try {
            for (AccessibilityWindowInfo window : windows) {
                collectWindowTexts(window, entries);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not read window payload: " + error.getMessage());
            return RecommendationTitleParser.Source.NONE;
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
        String text = String.join(" | ", event.getText() == null
                ? List.of() : event.getText());
        String description = toString(event.getContentDescription());
        String payload = !text.isBlank() ? text : description;
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
        if (payload.isBlank()) {
            if (!extra.isBlank()) {
                Log.i(TAG, "No credible title in launcher payload:" + extra);
            }
            return;
        }
        if (payload.length() > 300) payload = payload.substring(0, 300) + "…";
        Log.i(TAG, "No credible title in launcher payload: " + payload + extra);
    }

    private void notifyUser(int message) {
        handler.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
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
            return false;
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
            Set<String> labels = installedAppLabels;
            if (labels != null) return labels;
            Set<String> loaded = new HashSet<>();
            PackageManager packageManager = getPackageManager();
            List<android.content.pm.ApplicationInfo> apps =
                    packageManager.getInstalledApplications(0);
            java.util.List<String> raw = new java.util.ArrayList<>(apps.size());
            for (android.content.pm.ApplicationInfo app : apps) {
                raw.add(String.valueOf(packageManager.getApplicationLabel(app)));
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
        heartbeatHandler.removeCallbacks(heartbeat);
        if (pendingHeroCapture != null) handler.removeCallbacks(pendingHeroCapture);
        getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit()
                .remove(AppPrefs.SERVICE_CONNECTED_AT).apply();
        cancelTitleRetry();
        worker.shutdownNow();
        super.onDestroy();
    }
}

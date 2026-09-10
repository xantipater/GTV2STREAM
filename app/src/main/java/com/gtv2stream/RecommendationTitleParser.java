package com.gtv2stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Clean-room, dependency-free interpretation of the small title payloads emitted by
 * Google TV launcher views. It deliberately returns no answer for UI chrome, ads, or
 * synopsis-only text; the service can then wait for a detail window instead.
 *
 * Source classification: a "Watch on YouTube"/"Stream on YouTube" payload suffix (or a
 * YouTube provider-first item) marks the card as YouTube content so the service can
 * route it to a YouTube app instead of a film/series target. Sponsored and
 * advertisement payloads stay hard-rejected regardless of classification.
 */
public final class RecommendationTitleParser {
    private static final Pattern ACTION_SUFFIX = Pattern.compile(
            "(?i)(?:(?:watch|stream|streaming|new)(?:\\s+now)?\\s+on"
                    + "|included\\s+with)\\s+[^.,]+[.!?]?\\s*$");
    private static final Pattern AVAILABLE_ACTION_SUFFIX = Pattern.compile(
            "(?i)\\bavailable\\s+on\\s+([^.,]+)[.!?]?\\s*$");
    private static final Pattern BRACKETED = Pattern.compile("\\[[^]]*]");
    private static final Pattern YEAR_PAREN = Pattern.compile("\\((?:19|20)\\d{2}\\)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** Captures the provider name of any recognised watch-action suffix. */
    private static final Pattern ACTION_PROVIDER = Pattern.compile(
            "(?i)(?:(?:watch|stream|streaming|new)(?:\\s+now)?\\s+on"
                    + "|included\\s+with|available\\s+on)\\s+([^.,]+)[.!?]?\\s*$");
    /**
     * Captures the provider named by a mid-payload subscription requirement,
     * e.g. "The Tomorrow War, requires Prime Video subscription, ...".
     * Observed verbatim on a live Google TV launcher card.
     */
    private static final Pattern REQUIRES_PROVIDER = Pattern.compile(
            "(?i)\\brequires\\s+([^.,]+?)\\s+subscription\\b");
    /**
     * Matches exactly the subscription-requirement clause above, including its
     * leading comma, so the clause can be dropped before title segmentation.
     * The trailing metadata separator is deliberately left in place so the
     * remaining title/metadata segments keep their boundaries.
     */
    private static final Pattern REQUIRES_CLAUSE = Pattern.compile(
            "(?i)\\s*,\\s*\\brequires\\s+[^.,]+?\\s+subscription\\b");
    private static final Pattern GRID_LABEL = Pattern.compile("(?i)^(?:column|row)\\s+\\d+$");
    private static final Pattern INITIALISM = Pattern.compile("(?i)^(?:[a-z]\\.){2,}$");
    /**
     * Live Google TV YouTube recommendation card shape, captured verbatim from
     * the TCL launcher:
     * {@code "Gemini 3.8 Flash Is HERE – Testing Google's BEST Model Yet!, YouTube • Bijan Bowen"}.
     * The channel name after the bullet matches no provider identity, and the
     * whole payload is far too long to survive the title guards, so the card
     * was rejected outright and the redirect fell back to whatever the ambient
     * panel happened to hold. Anchored on the exact ", YouTube •" marker with a
     * non-empty channel, so nothing else can match it.
     */
    private static final Pattern YOUTUBE_CARD = Pattern.compile(
            "^(.*?)\\s*,\\s*youtube\\s*•\\s*\\S.*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    /**
     * Word bound for a payload with no card evidence: node text, ambient panel
     * values, a bare accessibility string. Prose is common here, so the bound is
     * tight.
     */
    private static final int DEFAULT_TITLE_MAX_WORDS = 7;
    /**
     * Word bound for a payload that carries card evidence: a recognised provider
     * on either edge, or a recognised watch action. Those shapes come from a real
     * recommendation card, and real film titles run long: "Shang-Chi and the
     * Legend of the Ten Rings" is eight words, and the entity-detail row and
     * YouTube card paths have allowed fifteen for the same reason. The
     * sentence-case, internal-period, ad, rating-metadata and UI-chrome guards in
     * {@link #directWithMaxWords} remain in force and are what actually reject
     * prose; this bound is a length sanity check, not the prose defence.
     */
    private static final int PROVIDER_TITLE_MAX_WORDS = 15;

    private static final Set<String> PROVIDERS = setOf(
            "itvx", "bbc iplayer", "netflix", "prime video", "amazon prime video",
            "amazon prime", "disney+", "disney plus", "hulu", "max", "hbo max",
            "paramount+", "apple tv+", "apple tv", "apple tv plus", "youtube",
            "google tv", "peacock", "channel 4", "my5", "iplayer", "starz",
            "showtime", "amc+", "discovery+", "mgm+", "britbox", "shudder",
            "tubi", "pluto tv", "freevee", "crunchyroll");
    /**
     * Canonical identity for each recognised provider name. Aliases collapse to one
     * key ("disney plus" -&gt; "disney+") so whitelist matching compares provider
     * identity, never the card title. Every alias key is an exact {@link #PROVIDERS}
     * entry; the bypass in {@link TvRecommendationService} only trusts these values.
     */
    private static final Map<String, String> PROVIDER_IDS = providerIds();
    /** Canonical whitelistable provider identities, in stable display order. */
    static final List<String> PROVIDER_ID_ORDER = providerIdOrder();
    private static final Set<String> UI_WORDS = setOf(
            "home", "search", "settings", "apps", "movies", "shows", "play", "pause",
            "open", "back", "more", "see all", "continue", "continue watching", "watch now",
            "watch", "trailer", "browse", "add to watchlist", "remove from watchlist",
            "watchlist", "advertisement", "sponsored", "ad", "learn more", "install", "download",
            "synopsis", "description", "overview", "just added", "recommendations",
            "recommended for you", "for you", "home screen", "main user home screen",
            // Navigation tabs and content rows.
            "library", "live", "free", "news", "kids", "sport", "sports", "on now",
            "collections", "channels", "store", "shop", "play next", "your apps",
            "apps library", "get more apps", "recently used", "trending", "top picks",
            "top picks for you", "because you watched", "new releases", "coming soon",
            "top charts", "popular", "popular on google tv",
            // Profiles.
            "profile", "profiles", "account", "accounts", "switch profile", "add profile",
            "manage profiles", "who's watching", "sign in", "sign out",
            // Launcher quick-settings sheet and Android TV settings tree.
            "notifications", "do not disturb", "screen cast", "cast", "all settings",
            "network & internet", "accounts & sign-in", "device preferences",
            "remotes & accessories", "display & sound", "ambient mode", "screensaver",
            "wallpaper", "system update", "system updates", "check for update",
            "restart", "reboot", "sleep", "power & energy", "quick start", "language",
            "keyboard", "input", "inputs", "date & time", "storage", "memory",
            "factory reset", "security & restrictions", "captions", "talkback",
            "accessibility", "google assistant", "assistant", "privacy", "location",
            "usage & diagnostics", "picture", "sound", "advanced display settings",
            "resolution", "color", "colors", "remotes", "accessories",
            // Deeper settings rows (About, apps management, audio/video, safety).
            "status information", "device name", "model", "android version",
            "android tv os build", "software version", "build number",
            "kernel version", "legal", "licenses", "see all apps",
            "app permissions", "special app access", "permissions", "force stop",
            "uninstall", "clear data", "clear cache", "open by default",
            "disable app", "channels & inputs", "hdmi control", "cec",
            "picture mode", "advanced picture settings", "advanced sound settings",
            "picture in picture", "pip", "sleep timer", "parental controls",
            "restricted profile", "screen lock", "reset", "reset to defaults",
            "data usage", "data saver", "vpn", "airplane mode", "screenshot",
            "pair new remote", "revert", "power on", "energy saver",
            // Launcher edit mode.
            "display", "move", "move up", "move down", "move to top", "move to front",
            "remove", "remove from row", "edit", "rename", "delete", "add", "arrange",
            "rearrange", "reorder", "customize", "hide", "show", "select", "close",
            "menu", "next", "previous", "skip", "done", "cancel", "ok", "apply",
            "save", "sort", "filter", "share",
            // Toggle labels and common action words.
            "on", "off", "auto", "yes", "no", "none", "enable", "disable",
            "enabled", "disabled", "more info", "info", "details", "cast & crew",
            "related", "more like this", "add to list", "remove from list",
            "own", "own it", "buy", "rent", "pre-order", "subscribe", "replay");
    private static final Set<String> TITLE_STOP_WORDS = setOf(
            "a", "an", "and", "as", "at", "by", "for", "from", "in", "of", "on", "or", "the",
            "to", "with");
    private static final Set<String> ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "st", "jr", "sr", "prof", "rev", "lt", "sgt", "no");

    private RecommendationTitleParser() { }

    /** A parsed payload: the title (possibly empty) and whether the card is YouTube content. */
    public static final class Source {
        public static final Source NONE = new Source("", false, "");
        public final String title;
        public final boolean youtube;
        /**
         * Canonical recognised provider identity for this card ("" when the payload
         * carries no provider edge or watch action). Never derived from the title.
         */
        public final String provider;

        private Source(String title, boolean youtube, String provider) {
            this.title = title;
            this.youtube = youtube;
            this.provider = provider == null ? "" : provider;
        }

        public boolean isEmpty() {
            return title.isEmpty();
        }

        public boolean hasProvider() {
            return !provider.isEmpty();
        }
    }

    private static Source source(String title, boolean youtube) {
        return source(title, youtube, youtube ? "youtube" : "");
    }

    private static Source source(String title, boolean youtube, String provider) {
        if (title.isEmpty()) return Source.NONE;
        String id = canonicalProviderId(provider);
        if (id.isEmpty() && youtube) id = "youtube";
        return new Source(title, youtube, id);
    }

    /** Canonical identity for a recognised provider name; "" for anything else. */
    public static String canonicalProviderId(String raw) {
        if (raw == null) return "";
        String cleaned = clean(raw).replaceFirst("[.,!?]+$", "").trim();
        if (cleaned.isEmpty()) return "";
        String mapped = PROVIDER_IDS.get(cleaned.toLowerCase(Locale.US));
        return mapped == null ? "" : mapped;
    }

    /** Canonical provider identity for a whitelistable stored value; "" when unknown. */
    public static String canonicalWhitelistId(String raw) {
        String id = canonicalProviderId(raw);
        return PROVIDER_ID_ORDER.contains(id) ? id : "";
    }

    private static Map<String, String> providerIds() {
        Map<String, String> ids = new HashMap<>();
        ids.put("itvx", "itvx");
        ids.put("bbc iplayer", "bbc iplayer");
        ids.put("iplayer", "bbc iplayer");
        ids.put("netflix", "netflix");
        ids.put("prime video", "prime video");
        ids.put("amazon prime video", "prime video");
        ids.put("amazon prime", "prime video");
        ids.put("disney+", "disney+");
        ids.put("disney plus", "disney+");
        ids.put("hulu", "hulu");
        ids.put("max", "max");
        ids.put("hbo max", "hbo max");
        ids.put("paramount+", "paramount+");
        ids.put("apple tv+", "apple tv+");
        ids.put("apple tv", "apple tv+");
        ids.put("apple tv plus", "apple tv+");
        ids.put("youtube", "youtube");
        ids.put("google tv", "google tv");
        ids.put("peacock", "peacock");
        ids.put("channel 4", "channel 4");
        ids.put("my5", "my5");
        ids.put("starz", "starz");
        ids.put("showtime", "showtime");
        ids.put("amc+", "amc+");
        ids.put("discovery+", "discovery+");
        ids.put("mgm+", "mgm+");
        ids.put("britbox", "britbox");
        ids.put("shudder", "shudder");
        ids.put("tubi", "tubi");
        ids.put("pluto tv", "pluto tv");
        ids.put("freevee", "freevee");
        ids.put("crunchyroll", "crunchyroll");
        return Collections.unmodifiableMap(ids);
    }

    private static List<String> providerIdOrder() {
        return Collections.unmodifiableList(Arrays.asList(
                "netflix", "prime video", "disney+", "itvx", "bbc iplayer",
                "hulu", "max", "hbo max", "paramount+", "apple tv+",
                "youtube", "google tv", "peacock", "channel 4", "my5",
                "starz", "showtime", "amc+", "discovery+", "mgm+",
                "britbox", "shudder", "tubi", "pluto tv", "freevee", "crunchyroll"));
    }

    /** Display name for a canonical provider identity ("" passes through as ""). */
    public static String providerDisplayName(String providerId) {
        if (providerId == null || providerId.isEmpty()) return "";
        switch (providerId) {
            case "disney+": return "Disney+";
            case "netflix": return "Netflix";
            case "prime video": return "Prime Video";
            case "itvx": return "ITVX";
            case "bbc iplayer": return "BBC iPlayer";
            case "hulu": return "Hulu";
            case "max": return "Max";
            case "hbo max": return "HBO Max";
            case "paramount+": return "Paramount+";
            case "apple tv+": return "Apple TV+";
            case "youtube": return "YouTube";
            case "google tv": return "Google TV";
            case "peacock": return "Peacock";
            case "channel 4": return "Channel 4";
            case "my5": return "My5";
            case "starz": return "Starz";
            case "showtime": return "Showtime";
            case "amc+": return "AMC+";
            case "discovery+": return "discovery+";
            case "mgm+": return "MGM+";
            case "britbox": return "BritBox";
            case "shudder": return "Shudder";
            case "tubi": return "Tubi";
            case "pluto tv": return "Pluto TV";
            case "freevee": return "Freevee";
            case "crunchyroll": return "Crunchyroll";
            default: return providerId;
        }
    }

    private static boolean isYoutubeAction(String lower) {
        // Card-level classification: any payload item mentioning YouTube marks the
        // card ("Watch on YouTube" actions, "YouTube • 2 weeks ago" video cards,
        // YouTube channel metadata, provider-first items).
        return lower.contains("youtube");
    }

    /** Selects the first credible item from the direct event text list. */
    public static Source fromEventTextSource(List<CharSequence> values) {
        if (values == null || values.isEmpty()) return Source.NONE;

        boolean youtube = false;
        for (CharSequence value : values) {
            String lower = value == null ? "" : value.toString().toLowerCase(Locale.US);
            // Sponsored and advertisement cards must never be redirected, even if their
            // first accessibility item happens to look like a title.
            if (lower.contains("sponsored") || lower.contains("advertisement")) return Source.NONE;
            if (isYoutubeAction(lower)) youtube = true;
        }

        String first = clean(values.get(0) == null ? "" : values.get(0).toString());
        boolean firstIsProvider = !first.isEmpty() && isProvider(first);
        if (firstIsProvider && "youtube".equals(first.toLowerCase(Locale.US))) youtube = true;
        // Provider identity comes only from a recognised provider edge or watch
        // action across the card items, never from the title under test.
        String provider = firstIsProvider ? canonicalProviderId(first) : eventActionProvider(values);
        if (!first.isEmpty() && !firstIsProvider) {
            // YouTube video titles run long ("Gemini 3.8 Flash Is HERE –
            // Testing Google's BEST Model Yet!"). When a card item names
            // YouTube the card is routed to a YouTube search, never to TMDB, so
            // the detail-row bound applies; every other payload stays at 7.
            return source(youtube ? directWithMaxWords(first, 15) : direct(first), youtube, provider);
        }
        if (values.size() < 2) return Source.NONE;

        // Provider-first payloads such as [ITVX, Trigger Point, ...] are common.
        // Do not scan farther: metadata and synopsis entries are not title fallbacks.
        String second = clean(values.get(1) == null ? "" : values.get(1).toString());
        return source(youtube ? directWithMaxWords(second, 15) : direct(second), youtube, provider);
    }

    /** First recognised watch-action provider across card items; "" when none. */
    private static String eventActionProvider(List<CharSequence> values) {
        for (CharSequence value : values) {
            String item = clean(value == null ? "" : value.toString());
            if (item.isEmpty()) continue;
            String id = actionProvider(item);
            if (!id.isEmpty()) return id;
            id = requiresProvider(item);
            if (!id.isEmpty()) return id;
        }
        return "";
    }

    /** Parses a rich content description or a single view text value. */
    public static Source fromDescriptionSource(String raw) {
        String value = clean(raw);
        if (value.isEmpty()) return Source.NONE;

        String lowerValue = value.toLowerCase(Locale.US);
        if (lowerValue.contains("sponsored") || lowerValue.contains("advertisement")) return Source.NONE;
        boolean youtube = isYoutubeAction(lowerValue);

        // Live YouTube card payloads arrive whole in the content description.
        // Nothing generic can parse them: the channel name after the bullet is
        // not a provider identity, and the payload is too long to be a title.
        // Before this branch, such a card was rejected outright, which is why
        // the redirect worked only when the ambient panel happened to hold a
        // parseable title, and why it searched the panel's video rather than the
        // card that was clicked.
        Source youtubeCard = youtubeCardSource(value);
        if (!youtubeCard.isEmpty()) return youtubeCard;

        String withoutAction = ACTION_SUFFIX.matcher(value).replaceFirst("").trim();
        String actionProvider = actionProvider(value);
        if (withoutAction.equals(value)) {
            java.util.regex.Matcher available = AVAILABLE_ACTION_SUFFIX.matcher(value);
            if (available.find() && isProvider(available.group(1).trim())) {
                withoutAction = value.substring(0, available.start()).trim();
            }
        }
        boolean hasWatchAction = !withoutAction.equals(value);
        if (hasWatchAction && canonicalProviderId(actionProvider).isEmpty()) {
            // An "Available on <unknown>" target never carries a whitelisted
            // provider: unknown action targets are rejected below like before.
            actionProvider = "";
        }
        if (withoutAction.isEmpty()) return Source.NONE;

        // Mid-payload subscription requirement, observed verbatim on a live
        // Google TV card ("The Tomorrow War, requires Prime Video
        // subscription, rotten rating: ..."): the clause is not an action
        // suffix, so drop exactly that clause and treat the requirement like
        // a watch action for the segments that remain. Only a recognised
        // provider reaches this branch, so unknown requirements stay
        // fail-closed exactly like unknown action targets.
        String requiresId = requiresProvider(value);
        boolean hasRequiresAction = false;
        String base = withoutAction;
        if (!requiresId.isEmpty()) {
            String stripped = clean(REQUIRES_CLAUSE.matcher(base).replaceFirst(""));
            if (!stripped.equals(base)) {
                base = stripped;
                hasRequiresAction = true;
            }
        }
        if (base.isEmpty()) return Source.NONE;
        String edgeProvider = !actionProvider.isEmpty() ? actionProvider : requiresId;

        // Provider positions are accepted the same way as an explicit watch action:
        // a trailing provider segment ("Title, Paramount+", "Title — Netflix",
        // "Title • ITVX.") or a leading provider segment ("Hulu. Title."). When
        // either is present, the exact attempt is skipped so provider text can
        // never leak into the returned title.
        String[] periods = splitPeriodSegments(base);
        String[] commas = base.split("\\s*,\\s*");
        String[] bullets = base.split("\\s*•\\s*");
        String[] dashes = base.split("\\s+[-–—]\\s+");
        boolean providerEdge = tailProvider(periods) || tailProvider(commas)
                || tailProvider(bullets) || tailProvider(dashes);

        // Bounded comma shape observed live on a Google TV card-region payload
        // ("Title, Provider, rating metadata"): the known provider sits
        // mid-payload, so tail detection misses it. Only the second comma
        // segment counts, and only as an exact known-provider identity, so
        // unknown providers and provider words inside the title grant nothing.
        String commaMiddleProvider = commaMiddleProvider(commas);
        if (!commaMiddleProvider.isEmpty()) {
            Source middle = sourceFromCommaMiddle(commas, youtube, commaMiddleProvider);
            if (!middle.isEmpty()) return middle;
        }

        if (!providerEdge) {
            // Try the complete value first. This is important for titles whose
            // punctuation resembles a sentence boundary, such as "Mr. Robot".
            // The strip leaves the separator before the action in place ("Title."),
            // so the trailing sentence punctuation is trimmed first and the
            // initialism guard keeps "S.W.A.T." intact.
            //
            // A recognised watch action is positive evidence that this is a card
            // rather than node text or synopsis prose, so a long title is allowed
            // here. At the 7-word default, real cards were rejected outright:
            // "Shang-Chi and the Legend of the Ten Rings. Watch on Disney+." is
            // eight words, and so is the Netflix/Prime equivalent. Every other
            // title guard (sentence-case, internal period, ads, rating metadata,
            // UI chrome) still applies and is independent of this bound.
            boolean cardEvidence = !edgeProvider.isEmpty();
            String exact = directWithMaxWords(trimEdgePunctuation(base),
                    cardEvidence ? PROVIDER_TITLE_MAX_WORDS : DEFAULT_TITLE_MAX_WORDS);
            if (!exact.isEmpty()) return source(exact, youtube, edgeProvider);
        }

        boolean segmentAction = hasWatchAction || hasRequiresAction;
        Source found = fromSegments(periods, segmentAction, youtube, edgeProvider);
        if (!found.isEmpty()) return found;
        found = fromSegments(commas, segmentAction, youtube, edgeProvider);
        if (!found.isEmpty()) return found;
        found = fromSegments(dashes, segmentAction, youtube, edgeProvider);
        if (!found.isEmpty()) return found;
        found = fromSegments(bullets, segmentAction, youtube, edgeProvider);
        if (!found.isEmpty()) return found;

        return Source.NONE;
    }

    /** Provider named by a recognised watch-action suffix; "" when absent or unknown. */
    private static String actionProvider(String value) {
        java.util.regex.Matcher action = ACTION_PROVIDER.matcher(value);
        if (!action.find()) return "";
        return canonicalProviderId(action.group(1).trim());
    }

    /** Provider named by a mid-payload subscription requirement; "" when absent or unknown. */
    private static String requiresProvider(String value) {
        java.util.regex.Matcher requires = REQUIRES_PROVIDER.matcher(value);
        if (!requires.find()) return "";
        return canonicalProviderId(requires.group(1).trim());
    }

    /** True when the final segment of a split payload is a known provider name. */
    private static boolean tailProvider(String[] segments) {
        return segments.length >= 2 && isProviderLoose(segments[segments.length - 1]);
    }

    /** Exact known-provider identity in the second comma segment; "" otherwise. */
    private static String commaMiddleProvider(String[] commas) {
        if (commas.length < 3) return "";
        return canonicalProviderId(commas[1]);
    }

    /**
     * Bounded "Title, Provider, metadata..." shape: the first comma segment is
     * the title, the second is an exact known provider, the rest is launcher
     * metadata. The title keeps every other direct() guard but allows up to
     * ten words so long titles (e.g. an eight-word film title) survive while
     * prose stays fail-closed.
     */
    private static Source sourceFromCommaMiddle(String[] commas, boolean youtube, String providerId) {
        String rawTitle = trimEdgePunctuation(commas[0]);
        if (rawTitle.isEmpty() || isProviderLoose(rawTitle)) return Source.NONE;
        String title = directWithMaxWords(rawTitle, 10);
        if (title.isEmpty()) return Source.NONE;
        return source(title, youtube, providerId);
    }

    /**
     * Selects the title from a split payload: the first credible segment, or the
     * segment after a leading provider. Two-segment payloads are accepted for an
     * explicit watch action, a provider edge, or three or more segments.
     */
    private static Source fromSegments(String[] segments, boolean hasWatchAction, boolean youtube,
            String actionProvider) {
        if (segments.length < 2) return Source.NONE;
        boolean leadingProvider = isProviderLoose(segments[0]);
        boolean tailIsProvider = tailProvider(segments);
        if (!(hasWatchAction || leadingProvider || tailIsProvider || segments.length >= 3)) {
            return Source.NONE;
        }
        // A provider on either edge, or an explicit watch action, is card evidence:
        // it cannot be node text or channel chrome, so long film titles are allowed.
        // Three-or-more-segment payloads with no provider keep the stricter default.
        boolean cardEvidence = hasWatchAction || leadingProvider || tailIsProvider;
        int maxWords = cardEvidence ? PROVIDER_TITLE_MAX_WORDS : DEFAULT_TITLE_MAX_WORDS;
        String provider = !actionProvider.isEmpty() ? actionProvider
                : leadingProvider ? canonicalProviderId(segments[0])
                : tailIsProvider ? canonicalProviderId(segments[segments.length - 1]) : "";
        String first = directWithMaxWords(trimEdgePunctuation(segments[0]), maxWords);
        if (!first.isEmpty()) return source(first, youtube, provider);
        if (leadingProvider) {
            return source(directWithMaxWords(trimEdgePunctuation(segments[1]), maxWords),
                    youtube, provider);
        }
        return Source.NONE;
    }

    /**
     * Removes the payload's trailing separator punctuation ("Title." or
     * "Title,") from a strip or split segment, keeping initialisms such as
     * "S.W.A.T." intact.
     */
    private static String trimEdgePunctuation(String raw) {
        String value = clean(raw);
        if (value.endsWith(",")) value = value.substring(0, value.length() - 1).trim();
        if (value.endsWith(".") && !INITIALISM.matcher(value).matches()) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }

    public static boolean isProviderLoose(String raw) {
        return isProvider(clean(raw).replaceFirst("[.,!?]+$", ""));
    }

    /** Parses a rich content description or a single view text value. */
    public static String fromDescription(String raw) {
        return fromDescriptionSource(raw).title;
    }

    /** Selects the first credible item from the direct event text list. */
    public static String fromEventText(List<CharSequence> values) {
        return fromEventTextSource(values).title;
    }

    /** Useful for node text, where a rich description should not be required. */
    public static String fromDirectText(String raw) {
        return direct(raw == null ? "" : raw);
    }

    /** Typed variant of {@link #fromDirectText}: node payloads never carry a provider marker. */
    public static Source fromDirectTextSource(String raw) {
        return source(direct(raw == null ? "" : raw), false);
    }

    /**
     * Authoritative entity-detail title row ({@code entity_details_title_row}):
     * the launcher exposes the exact title here, so the same cleaning, UI,
     * ad/sponsored, and prose guards as {@link #fromDirectText} apply, but up
     * to 15 words are allowed so long film titles (e.g. the 8-word Shang-Chi
     * title) survive. General event/description parsing stays at 7 words.
     */
    public static String fromDetailTitle(String raw) {
        return directWithMaxWords(raw == null ? "" : raw, 15);
    }

    /** Typed variant of {@link #fromDetailTitle}: detail rows never carry a provider marker. */
    public static Source fromDetailTitleSource(String raw) {
        return source(directWithMaxWords(raw == null ? "" : raw, 15), false);
    }

    /**
     * The live YouTube card shape as a typed source: the title before the
     * {@code ", YouTube •"} marker, the channel after it, and provider identity
     * "youtube". Long video titles are the norm, so this path allows the same
     * 15-word bound as the entity-detail row. Every other title guard still
     * applies, including the UI-chrome, ad, and sponsored rejections, so
     * movie/show parsing is untouched.
     */
    static Source youtubeCardSource(String raw) {
        String value = clean(raw == null ? "" : raw);
        if (value.isEmpty()) return Source.NONE;
        java.util.regex.Matcher matcher = YOUTUBE_CARD.matcher(value);
        if (!matcher.matches()) return Source.NONE;
        String title = directWithMaxWords(matcher.group(1), 15);
        if (title.isEmpty()) return Source.NONE;
        return source(title, true, "youtube");
    }

    /** Launcher chrome that must never be read as a title, whatever the source. */
    private static boolean isChromeValue(String value, String lower) {
        return UI_WORDS.contains(lower) || PROVIDERS.contains(lower)
                || GRID_LABEL.matcher(value).matches()
                || value.indexOf('•') >= 0 || value.indexOf('|') >= 0;
    }

    /** Creates a typed source for a title obtained from a YouTube hero panel. */
    public static Source youtubeSource(String raw) {
        String value = clean(raw == null ? "" : raw);
        String lower = value.toLowerCase(Locale.US);
        if (value.isEmpty() || value.length() > 150
                || lower.contains("sponsored") || lower.contains("advertisement")) {
            return Source.NONE;
        }
        // The panel scan reads every text node in every window, so launcher
        // chrome arrives here as ordinary text. It used to accept anything
        // non-empty, which is how a redirect searched YouTube for "Column 3"
        // instead of the card's video. Chrome is now rejected structurally, and
        // what survives still has to look like a title (bounded word count, not
        // all-lowercase synopsis prose).
        if (isChromeValue(value, lower) || !looksLikeTitle(value, 15)) return Source.NONE;
        return source(value, true, "youtube");
    }

    /**
     * True when a parsed source should bypass the redirect: its canonical provider
     * identity is non-empty and contained in the sanitized whitelist. Titles alone
     * never bypass; the identity comes only from a recognised provider edge or
     * watch action, so a film named like a provider cannot match.
     */
    public static boolean isWhitelisted(Source parsed, Set<String> whitelist) {
        if (parsed == null || parsed.isEmpty() || parsed.provider.isEmpty()) return false;
        if (whitelist == null || whitelist.isEmpty()) return false;
        return whitelist.contains(parsed.provider);
    }

    public static boolean isCredibleTitle(String raw) {
        return !direct(raw == null ? "" : raw).isEmpty();
    }

    private static String direct(String raw) {
        return directWithMaxWords(raw, DEFAULT_TITLE_MAX_WORDS);
    }

    private static String directWithMaxWords(String raw, int maxWords) {
        String value = clean(raw);
        if (value.isEmpty() || value.length() > 80) return "";
        String lower = value.toLowerCase(Locale.US);
        if (UI_WORDS.contains(lower) || PROVIDERS.contains(lower) || GRID_LABEL.matcher(value).matches()) return "";
        if (lower.contains("sponsored") || lower.contains("advertisement")
                || lower.startsWith("ad ") || lower.contains("learn more")
                || lower.contains("install app") || lower.contains("download app")) return "";
        if (lower.matches("season\\s+\\d+.*") || lower.matches("episode\\s+\\d+.*")
                || lower.contains("watch on") || lower.contains("stream on")
                || lower.contains("watch now on")) return "";
        // A bare subscription-requirement row ("requires X subscription") is
        // provider metadata, never a title.
        if (REQUIRES_PROVIDER.matcher(value).find()) return "";
        // Rating metadata tails ("fresh rating: 92% on Rotten Tomatoes") are
        // never titles, even when they happen to be title-cased.
        if (lower.contains("rotten tomatoes")) return "";
        // Settings-style payloads: suffixed chrome, port/input labels, price actions.
        if (lower.endsWith(" settings") || lower.endsWith(" options")
                || lower.endsWith(" preferences") || lower.endsWith(" management")
                || lower.endsWith(" mode") || lower.endsWith(" row")) return "";
        if (lower.matches("(?:input|hdmi|aux|av|usb)\\s*\\d*") || value.matches(".*\\$\\d.*")) return "";
        if (value.indexOf('•') >= 0 || value.indexOf('|') >= 0) return "";
        if (!looksLikeTitle(value, maxWords)) return "";
        return value;
    }

    private static boolean looksLikeTitle(String value) {
        return looksLikeTitle(value, DEFAULT_TITLE_MAX_WORDS);
    }

    private static boolean looksLikeTitle(String value, int maxWords) {
        // A period followed by a space is normally prose. Keep the small set of
        // conventional abbreviations title-safe, while still accepting Mr. Robot.
        String remainder = value;
        for (String abbreviation : ABBREVIATIONS) {
            remainder = remainder.replaceAll("(?i)\\b" + abbreviation + "\\.\\s+", "");
        }
        if (remainder.matches(".*\\.\\s+.*")) return false;
        if (value.endsWith(".") && !INITIALISM.matcher(value).matches()) return false;

        String[] words = value.split("\\s+");
        if (words.length > maxWords) return false;

        int titleCaseWords = 0;
        int lowerContentWords = 0;
        for (String word : words) {
            String letters = word.replaceAll("^[^A-Za-z0-9]+|[^A-Za-z0-9]+$", "");
            if (letters.isEmpty()) continue;
            String lower = letters.toLowerCase(Locale.US);
            boolean hasUpper = !letters.equals(letters.toLowerCase(Locale.US));
            boolean startsUpper = Character.isUpperCase(letters.charAt(0));
            if (startsUpper || hasUpper) titleCaseWords++;
            else if (!TITLE_STOP_WORDS.contains(lower)) lowerContentWords++;
        }

        // Long all-lowercase prose is the common accessibility/synopsis false positive.
        // Single-word titles remain valid, and normal title case remains punctuation-safe.
        if (titleCaseWords == 0) return false;
        return lowerContentWords == 0 || titleCaseWords >= 2;
    }

    private static String[] splitPeriodSegments(String value) {
        List<String> segments = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < value.length() - 1; i++) {
            if (value.charAt(i) != '.' || !Character.isWhitespace(value.charAt(i + 1))) continue;
            String before = value.substring(start, i).trim();
            String token = before;
            int lastSpace = token.lastIndexOf(' ');
            if (lastSpace >= 0) token = token.substring(lastSpace + 1);
            String tokenLower = token.toLowerCase(Locale.US);
            boolean initialism = before.matches("(?i).*(?:[a-z]\\.){2,}[a-z]?");
            if (ABBREVIATIONS.contains(tokenLower) || initialism) continue;
            if (!before.isEmpty()) segments.add(before);
            start = i + 1;
        }
        String tail = value.substring(start).replaceFirst("^\\s+", "").trim();
        if (!tail.isEmpty()) segments.add(tail);
        return segments.toArray(new String[0]);
    }

    private static boolean isProvider(String raw) {
        return PROVIDERS.contains(clean(raw).toLowerCase(Locale.US));
    }

    private static String clean(String raw) {
        if (raw == null) return "";
        String value = raw.replace('\n', ' ').replace('\r', ' ');
        value = BRACKETED.matcher(value).replaceAll(" ");
        value = YEAR_PAREN.matcher(value).replaceAll(" ");
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    private static Set<String> setOf(String... values) {
        Set<String> result = new HashSet<>();
        for (String value : values) result.add(value);
        return result;
    }
}

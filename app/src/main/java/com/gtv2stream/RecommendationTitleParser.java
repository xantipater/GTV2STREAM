package com.gtv2stream;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private static final Pattern GRID_LABEL = Pattern.compile("(?i)^(?:column|row)\\s+\\d+$");
    private static final Pattern INITIALISM = Pattern.compile("(?i)^(?:[a-z]\\.){2,}$");
    private static final Set<String> PROVIDERS = setOf(
            "itvx", "bbc iplayer", "netflix", "prime video", "amazon prime video",
            "amazon prime", "disney+", "disney plus", "hulu", "max", "hbo max",
            "paramount+", "apple tv+", "apple tv", "apple tv plus", "youtube",
            "google tv", "peacock", "channel 4", "my5", "iplayer", "starz",
            "showtime", "amc+", "discovery+", "mgm+", "britbox", "shudder",
            "tubi", "pluto tv", "freevee", "crunchyroll");
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
        public static final Source NONE = new Source("", false);
        public final String title;
        public final boolean youtube;

        private Source(String title, boolean youtube) {
            this.title = title;
            this.youtube = youtube;
        }

        public boolean isEmpty() {
            return title.isEmpty();
        }
    }

    private static Source source(String title, boolean youtube) {
        return title.isEmpty() ? Source.NONE : new Source(title, youtube);
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
        if (!first.isEmpty() && !firstIsProvider) return source(direct(first), youtube);
        if (values.size() < 2) return Source.NONE;

        // Provider-first payloads such as [ITVX, Trigger Point, ...] are common.
        // Do not scan farther: metadata and synopsis entries are not title fallbacks.
        String second = clean(values.get(1) == null ? "" : values.get(1).toString());
        return source(direct(second), youtube);
    }

    /** Parses a rich content description or a single view text value. */
    public static Source fromDescriptionSource(String raw) {
        String value = clean(raw);
        if (value.isEmpty()) return Source.NONE;

        String lowerValue = value.toLowerCase(Locale.US);
        if (lowerValue.contains("sponsored") || lowerValue.contains("advertisement")) return Source.NONE;
        boolean youtube = isYoutubeAction(lowerValue);

        String withoutAction = ACTION_SUFFIX.matcher(value).replaceFirst("").trim();
        boolean hasWatchAction = !withoutAction.equals(value);
        if (withoutAction.isEmpty()) return Source.NONE;

        // Provider positions are accepted the same way as an explicit watch action:
        // a trailing provider segment ("Title, Paramount+", "Title — Netflix",
        // "Title • ITVX.") or a leading provider segment ("Hulu. Title."). When
        // either is present, the exact attempt is skipped so provider text can
        // never leak into the returned title.
        String[] periods = splitPeriodSegments(withoutAction);
        String[] commas = withoutAction.split("\\s*,\\s*");
        String[] bullets = withoutAction.split("\\s*•\\s*");
        String[] dashes = withoutAction.split("\\s+[-–—]\\s+");
        boolean providerEdge = tailProvider(periods) || tailProvider(commas)
                || tailProvider(bullets) || tailProvider(dashes);

        if (!providerEdge) {
            // Try the complete value first. This is important for titles whose
            // punctuation resembles a sentence boundary, such as "Mr. Robot".
            // The strip leaves the separator before the action in place ("Title."),
            // so the trailing sentence punctuation is trimmed first and the
            // initialism guard keeps "S.W.A.T." intact.
            String exact = direct(trimEdgePunctuation(withoutAction));
            if (!exact.isEmpty()) return source(exact, youtube);
        }

        Source found = fromSegments(periods, hasWatchAction, youtube);
        if (!found.isEmpty()) return found;
        found = fromSegments(commas, hasWatchAction, youtube);
        if (!found.isEmpty()) return found;
        found = fromSegments(dashes, hasWatchAction, youtube);
        if (!found.isEmpty()) return found;
        found = fromSegments(bullets, hasWatchAction, youtube);
        if (!found.isEmpty()) return found;

        return Source.NONE;
    }

    /** True when the final segment of a split payload is a known provider name. */
    private static boolean tailProvider(String[] segments) {
        return segments.length >= 2 && isProviderLoose(segments[segments.length - 1]);
    }

    /**
     * Selects the title from a split payload: the first credible segment, or the
     * segment after a leading provider. Two-segment payloads are accepted for an
     * explicit watch action, a provider edge, or three or more segments.
     */
    private static Source fromSegments(String[] segments, boolean hasWatchAction, boolean youtube) {
        if (segments.length < 2) return Source.NONE;
        boolean leadingProvider = isProviderLoose(segments[0]);
        boolean tailIsProvider = tailProvider(segments);
        if (!(hasWatchAction || leadingProvider || tailIsProvider || segments.length >= 3)) {
            return Source.NONE;
        }
        String first = direct(trimEdgePunctuation(segments[0]));
        if (!first.isEmpty()) return source(first, youtube);
        if (leadingProvider) return source(direct(trimEdgePunctuation(segments[1])), youtube);
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

    /** Creates a typed source for a title obtained from a YouTube hero panel. */
    public static Source youtubeSource(String raw) {
        String value = clean(raw == null ? "" : raw);
        String lower = value.toLowerCase(Locale.US);
        if (value.isEmpty() || value.length() > 150
                || lower.contains("sponsored") || lower.contains("advertisement")) {
            return Source.NONE;
        }
        return source(value, true);
    }

    public static boolean isCredibleTitle(String raw) {
        return !direct(raw == null ? "" : raw).isEmpty();
    }

    private static String direct(String raw) {
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
        // Settings-style payloads: suffixed chrome, port/input labels, price actions.
        if (lower.endsWith(" settings") || lower.endsWith(" options")
                || lower.endsWith(" preferences") || lower.endsWith(" management")
                || lower.endsWith(" mode") || lower.endsWith(" row")) return "";
        if (lower.matches("(?:input|hdmi|aux|av|usb)\\s*\\d*") || value.matches(".*\\$\\d.*")) return "";
        if (value.indexOf('•') >= 0 || value.indexOf('|') >= 0) return "";
        if (!looksLikeTitle(value)) return "";
        return value;
    }

    private static boolean looksLikeTitle(String value) {
        // A period followed by a space is normally prose. Keep the small set of
        // conventional abbreviations title-safe, while still accepting Mr. Robot.
        String remainder = value;
        for (String abbreviation : ABBREVIATIONS) {
            remainder = remainder.replaceAll("(?i)\\b" + abbreviation + "\\.\\s+", "");
        }
        if (remainder.matches(".*\\.\\s+.*")) return false;
        if (value.endsWith(".") && !INITIALISM.matcher(value).matches()) return false;

        String[] words = value.split("\\s+");
        if (words.length > 7) return false;

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
        return raw.replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\[[^]]*]", " ")
                .replaceAll("\\((?:19|20)\\d{2}\\)", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static Set<String> setOf(String... values) {
        Set<String> result = new HashSet<>();
        for (String value : values) result.add(value);
        return result;
    }
}

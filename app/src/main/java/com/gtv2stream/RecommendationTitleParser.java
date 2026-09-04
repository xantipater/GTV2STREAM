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
 */
public final class RecommendationTitleParser {
    private static final Pattern WATCH_TRAILER = Pattern.compile(
            "(?i)(?:\\.|,)?\\s*watch(?:\\s+now)?\\s+on\\s+[^.,]+\\s*$");
    private static final Pattern GRID_LABEL = Pattern.compile("(?i)^(?:column|row)\\s+\\d+$");
    private static final Pattern INITIALISM = Pattern.compile("(?i)^(?:[a-z]\\.){2,}$");
    private static final Set<String> PROVIDERS = setOf(
            "itvx", "bbc iplayer", "netflix", "prime video", "amazon prime video",
            "disney+", "disney plus", "hulu", "max", "paramount+", "apple tv+",
            "youtube", "google tv", "peacock", "channel 4", "my5", "iplayer");
    private static final Set<String> UI_WORDS = setOf(
            "home", "search", "settings", "apps", "movies", "shows", "play", "pause",
            "open", "back", "more", "see all", "continue", "continue watching", "watch now",
            "watch", "trailer", "browse", "add to watchlist", "remove from watchlist",
            "watchlist", "advertisement", "sponsored", "ad", "learn more", "install", "download",
            "synopsis", "description", "overview", "just added", "recommendations",
            "recommended for you", "for you", "home screen", "main user home screen");
    private static final Set<String> TITLE_STOP_WORDS = setOf(
            "a", "an", "and", "as", "at", "by", "for", "from", "in", "of", "on", "or", "the",
            "to", "with");
    private static final Set<String> ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "st", "jr", "sr", "prof", "rev", "lt", "sgt", "no");

    private RecommendationTitleParser() { }

    /**
     * Selects the first credible item from the direct event text list. A second item is
     * considered only when the first is blank or an explicitly recognized provider.
     */
    public static String fromEventText(List<CharSequence> values) {
        if (values == null || values.isEmpty()) return "";

        // A sponsored or YouTube card must never be redirected, even if its first
        // accessibility item happens to look like a title.
        for (CharSequence value : values) {
            String lower = value == null ? "" : value.toString().toLowerCase(Locale.US);
            if (lower.contains("sponsored") || lower.contains("advertisement")
                    || lower.contains("watch on youtube")
                    || lower.contains("watch now on youtube")) return "";
        }

        String first = clean(values.get(0) == null ? "" : values.get(0).toString());
        if (!first.isEmpty() && !isProvider(first)) return direct(first);
        if (values.size() < 2) return "";

        // Provider-first payloads such as [ITVX, Trigger Point, ...] are common.
        // Do not scan farther: metadata and synopsis entries are not title fallbacks.
        String second = clean(values.get(1) == null ? "" : values.get(1).toString());
        return direct(second);
    }

    /** Parses a rich content description or a single view text value. */
    public static String fromDescription(String raw) {
        String value = clean(raw);
        if (value.isEmpty()) return "";

        String lowerValue = value.toLowerCase(Locale.US);
        if (lowerValue.contains("sponsored") || lowerValue.contains("advertisement")
                || lowerValue.contains("watch on youtube")
                || lowerValue.contains("watch now on youtube")) return "";

        String withoutAction = WATCH_TRAILER.matcher(value).replaceFirst("").trim();
        boolean hasWatchAction = !withoutAction.equals(value);
        if (withoutAction.isEmpty()) return "";

        // Try the complete value first. This is important for titles whose punctuation
        // resembles a sentence boundary, such as "Mr. Robot" or "S.W.A.T.".
        String exact = direct(withoutAction);
        if (!exact.isEmpty()) return exact;

        String[] periods = splitPeriodSegments(withoutAction);
        if (periods.length >= 2 && (hasWatchAction || periods.length >= 3)) {
            String first = direct(periods[0]);
            if (!first.isEmpty()) return first;
            if (isProvider(periods[0])) return direct(periods[1]);
        }

        String[] commas = withoutAction.split("\\s*,\\s*");
        if (commas.length >= 2 && (hasWatchAction || commas.length >= 3)) {
            String first = direct(commas[0]);
            if (!first.isEmpty()) return first;
            if (isProvider(commas[0])) return direct(commas[1]);
        }

        return "";
    }

    /** Useful for node text, where a rich description should not be required. */
    public static String fromDirectText(String raw) {
        return direct(raw == null ? "" : raw);
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
                || lower.contains("watch on") || lower.contains("watch now on")) return "";
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

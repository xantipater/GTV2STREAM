package com.gtv2stream;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure title matching and Nuvio URI logic, kept separate for deterministic tests. */
public final class TitleResultHelper {
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern BRACKETED = Pattern.compile("\\[[^]]*]");
    private static final Pattern YEAR_PAREN = Pattern.compile("\\((?:19|20)\\d{2}\\)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NON_ALNUM_RUN = Pattern.compile("[^a-z0-9]+");

    private TitleResultHelper() { }

    public static String cleanTitle(String raw) {
        if (raw == null) return "";
        String value = raw.replace('\n', ' ').replace('\r', ' ').trim();
        value = BRACKETED.matcher(value).replaceAll(" ");
        value = YEAR_PAREN.matcher(value).replaceAll(" ");
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    /** Compatibility entry point for callers that need the clean-room parser. */
    public static String extractLauncherTitle(String raw) {
        String parsed = RecommendationTitleParser.fromDescription(raw);
        return parsed.isEmpty() ? RecommendationTitleParser.fromDirectText(raw) : parsed;
    }

    public static String extractYear(String raw) {
        if (raw == null) return "";
        Matcher matcher = YEAR.matcher(raw);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static String normalize(String raw) {
        return normalizedTitle(raw);
    }

    /** Normalization used for UI title comparisons: punctuation and case are ignored. */
    public static String normalizedTitle(String raw) {
        return NON_ALNUM_RUN.matcher(cleanTitle(raw).toLowerCase(Locale.US)).replaceAll(" ").trim();
    }

    public static boolean normalizedTitleMatches(String expected, String observed) {
        String left = normalizedTitle(expected);
        String right = normalizedTitle(observed);
        return !left.isEmpty() && left.equals(right);
    }

    public static int score(String queryTitle, String queryYear, TmdbClient.Candidate candidate) {
        String query = normalize(queryTitle);
        String title = normalize(candidate.title);
        if (query.isEmpty() || title.isEmpty()) return Integer.MIN_VALUE;
        int result;
        if (query.equals(title)) result = 1000;
        else if (title.contains(query) || query.contains(title)) result = 650;
        else result = 250 * tokenOverlap(query, title);
        if (!queryYear.isEmpty() && queryYear.equals(candidate.year)) result += 220;
        if (candidate.popularity > 0) result += Math.min(50, (int) candidate.popularity);
        return result;
    }

    private static int tokenOverlap(String a, String b) {
        Set<String> left = new HashSet<>();
        Collections.addAll(left, a.split(" "));
        int count = 0;
        for (String token : b.split(" ")) if (token.length() > 1 && left.contains(token)) count++;
        return count;
    }

    public static TmdbClient.Candidate chooseBest(String rawQuery, List<TmdbClient.Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        String queryYear = extractYear(rawQuery);
        String queryTitle = cleanTitle(rawQuery);
        List<TmdbClient.Candidate> usable = new ArrayList<>();
        for (TmdbClient.Candidate c : candidates) {
            if (c != null && ("movie".equals(c.mediaType) || "tv".equals(c.mediaType))
                    && !normalize(c.title).isEmpty() && c.tmdbId > 0) usable.add(c);
        }
        if (usable.isEmpty()) return null;
        usable.sort(Comparator.comparingInt((TmdbClient.Candidate c) -> score(queryTitle, queryYear, c)).reversed());
        return usable.get(0);
    }

    public static String nuvioUri(TitleMatch match) {
        if (match == null || match.imdbId == null || !match.imdbId.matches("tt\\d+")) return null;
        String kind;
        if ("movie".equals(match.mediaType)) kind = "movie";
        else if ("tv".equals(match.mediaType)) return "nuvio://detail/tv/" + match.imdbId;
        else return null;
        return "nuvio://" + kind + "/" + match.imdbId;
    }

    /** Stremio's documented IMDb-backed detail deep link (three slashes are required). */
    public static String stremioUri(TitleMatch match) {
        if (match == null || match.imdbId == null || !match.imdbId.matches("tt\\d+")) return null;
        if ("movie".equals(match.mediaType)) return "stremio:///detail/movie/" + match.imdbId;
        if ("tv".equals(match.mediaType)) return "stremio:///detail/series/" + match.imdbId;
        return null;
    }

    /**
     * WuPlay's IMDb-backed detail deep link. WuPlay is a Stremio-family client
     * like Nuvio, but its own scheme is flat (no {@code /detail}) and it is NOT
     * a forked Stremio scheme, so it needs its own resolver rather than sharing
     * Stremio's.
     */
    public static String wuplayUri(TitleMatch match) {
        if (match == null || match.imdbId == null || !match.imdbId.matches("tt\\d+")) return null;
        if ("movie".equals(match.mediaType)) return "wuplay://movie/" + match.imdbId;
        if ("tv".equals(match.mediaType)) return "wuplay://series/" + match.imdbId;
        return null;
    }

    /** YouTube title search; the same deep-link shape YouTube voice search delivers cold. */
    public static String youtubeSearchUri(String title) {
        String query = cleanTitle(title);
        if (query.isEmpty()) return null;
        try {
            return "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException error) {
            // UTF-8 is always supported; unreachable in practice.
            return null;
        }
    }
}

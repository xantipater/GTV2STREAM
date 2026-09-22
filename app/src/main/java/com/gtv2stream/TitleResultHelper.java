package com.gtv2stream;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure title matching and Nuvio URI logic, kept separate for deterministic tests. */
public final class TitleResultHelper {
    private static final Pattern YEAR = Pattern.compile("\\(((?:19|20)\\d{2})\\)");
    /**
     * Presentation badges known to be metadata rather than part of a title.
     * Keep this list bounded: films such as [REC] and [REC] 2 use square
     * brackets as title punctuation and must reach TMDB unchanged.
     */
    private static final String PRESENTATION_BADGE_CONTENT =
            "(?:imax|4k|uhd|hd|hdr(?:10\\+?)?|dolby\\s+(?:vision|atmos)|atmos|cc)";
    private static final Pattern PRESENTATION_BADGE = Pattern.compile(
            "(?iu)\\[" + PRESENTATION_BADGE_CONTENT + "\\]");
    private static final Pattern YEAR_PAREN = Pattern.compile("\\((?:19|20)\\d{2}\\)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NON_ALNUM_RUN = Pattern.compile("[^\\p{L}\\p{N}]+");

    private TitleResultHelper() { }

    public static String cleanTitle(String raw) {
        if (raw == null) return "";
        String value = raw.replace('\n', ' ').replace('\r', ' ').trim();
        value = PRESENTATION_BADGE.matcher(value).replaceAll(" ");
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

    /** Identity for lookups/caches: never share a remake's entry with another year. */
    public static String matchKey(String raw) {
        String title = normalizedTitle(raw);
        return title.isEmpty() ? "" : title + "|" + extractYear(raw);
    }

    /** Missing metadata is compatible; two explicitly different years are not. */
    public static boolean compatibleTitles(String left, String right) {
        if (!normalizedTitleMatches(left, right)) return false;
        String a = extractYear(left), b = extractYear(right);
        return a.isEmpty() || b.isEmpty() || a.equals(b);
    }

    /**
     * Accept only a parenthesised year immediately after the parsed title. Years
     * in a synopsis/provider row, and numeric titles such as 1917, are not hints.
     */
    public static String yearForTitle(String raw, String title) {
        if (raw == null || title == null || title.isEmpty()) return "";
        String value = WHITESPACE.matcher(
                PRESENTATION_BADGE.matcher(raw).replaceAll(" ")).replaceAll(" ");
        Matcher occurrence = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(title)
                + "(?![\\p{L}\\p{N}])").matcher(value);
        if (occurrence.find()) {
            Matcher year = YEAR.matcher(value.substring(occurrence.end()).trim());
            if (year.lookingAt()) return year.group(1);
        }
        return "";
    }

    /**
     * A redirect is not a search-results page: an uncertain guess opens the wrong
     * film. Require an exact normalised title, an exact year when supplied, and
     * one distinct TMDB identity. Popularity cannot disambiguate remakes or a
     * same-name film/series. Duplicate rows for the same identity are harmless.
     */
    public static TmdbClient.Candidate chooseBest(String rawQuery, List<TmdbClient.Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        String queryYear = extractYear(rawQuery);
        String query = normalizedTitle(rawQuery);
        if (query.isEmpty()) return null;
        TmdbClient.Candidate selected = null;
        for (TmdbClient.Candidate candidate : candidates) {
            if (candidate == null || candidate.tmdbId <= 0
                    || !("movie".equals(candidate.mediaType) || "tv".equals(candidate.mediaType))
                    || !query.equals(normalizedTitle(candidate.title))
                    || (!queryYear.isEmpty() && !queryYear.equals(candidate.year))) continue;
            if (selected != null && (selected.tmdbId != candidate.tmdbId
                    || !selected.mediaType.equals(candidate.mediaType))) return null;
            selected = candidate;
        }
        return selected;
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

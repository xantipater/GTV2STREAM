package com.gtv2stream;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Minimal TMDB v3 client. It is called only from the service worker thread. */
public final class TmdbClient {
    private static final String API = "https://api.themoviedb.org/3";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_SEARCH_PAGES = 5;
    private final String apiKey;
    private final Transport transport;

    interface Transport {
        String get(String address) throws IOException;
    }

    public TmdbClient(String apiKey) {
        this(apiKey, null);
    }

    TmdbClient(String apiKey, Transport transport) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.transport = transport == null ? this::get : transport;
    }

    public static final class Candidate {
        public final String title;
        public final String year;
        public final String mediaType;
        public final long tmdbId;
        public final double popularity;

        public Candidate(String title, String year, String mediaType, long tmdbId, double popularity) {
            this.title = title;
            this.year = year == null ? "" : year;
            this.mediaType = mediaType == null ? "" : mediaType;
            this.tmdbId = tmdbId;
            this.popularity = popularity;
        }
    }

    public TitleMatch searchBest(String rawTitle) throws IOException {
        if (apiKey.length() < 10) throw new InvalidApiKeyException();
        String query = TitleResultHelper.cleanTitle(rawTitle);
        if (query.isEmpty()) return null;
        List<Candidate> candidates = new ArrayList<>();
        Set<String> receivedIdentities = new HashSet<>();
        int totalPages = -1, totalResults = -1;
        long receivedResults = 0;
        String search = API + "/search/multi?api_key=" + encode(apiKey)
                + "&query=" + encode(query) + "&include_adult=false&page=";
        for (int page = 1; page <= MAX_SEARCH_PAGES; page++) {
            final JSONObject response;
            try {
                response = new JSONObject(transport.get(search + page));
            } catch (org.json.JSONException error) {
                throw new IOException("TMDB returned invalid JSON", error);
            }
            JSONArray results = response.optJSONArray("results");
            int pages = paginationNumber(response, "total_pages");
            int count = paginationNumber(response, "total_results");
            if (results == null || paginationNumber(response, "page") != page
                    || pages < 0 || count < 0) throw invalidSearchResults();
            if (page == 1) {
                if (count == 0) {
                    if (results.length() != 0 || pages > 1) throw invalidSearchResults();
                    return null;
                }
                // The page cap is an intentional no-match policy, not a failed lookup.
                if (pages > MAX_SEARCH_PAGES) return null;
                totalPages = pages;
                totalResults = count;
            }
            // A partial or changing result set cannot establish a unique title.
            // Never redirect from page one when another page may contain a remake.
            if (pages != totalPages || count != totalResults || page > totalPages
                    || results.length() == 0) throw invalidSearchResults();
            receivedResults += results.length();
            if (receivedResults > totalResults
                    || (page < totalPages && receivedResults >= totalResults)) throw invalidSearchResults();
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) throw invalidSearchResults();
                String mediaType = item.optString("media_type", "");
                if (!("movie".equals(mediaType) || "tv".equals(mediaType) || "person".equals(mediaType))) {
                    throw invalidSearchResults();
                }
                Object idValue = item.opt("id");
                if (!(idValue instanceof Number)) throw invalidSearchResults();
                long id = ((Number) idValue).longValue();
                if (id <= 0 || ((Number) idValue).doubleValue() != id) throw invalidSearchResults();
                // Pages are separate requests, not a server snapshot. Ranking
                // changes can repeat a row while hiding another title without
                // changing total_results. Every counted row must be distinct,
                // including people that are not film/series match candidates.
                if (!receivedIdentities.add(mediaType + ":" + id)) throw invalidSearchResults();
                if ("person".equals(mediaType)) continue;
                String titleKey = "movie".equals(mediaType) ? "title" : "name";
                String dateKey = "movie".equals(mediaType) ? "release_date" : "first_air_date";
                Object titleValue = item.opt(titleKey);
                if (!(titleValue instanceof String)) throw invalidSearchResults();
                String title = ((String) titleValue).trim();
                if (title.isEmpty()) throw invalidSearchResults();
                String date = item.optString(dateKey, "");
                candidates.add(new Candidate(title, date.length() >= 4 ? date.substring(0, 4) : "",
                        mediaType, id, item.optDouble("popularity", 0.0)));
            }
            if (page == totalPages) break;
        }
        if (receivedResults != totalResults) throw invalidSearchResults();
        Candidate selected = TitleResultHelper.chooseBest(rawTitle, candidates);
        if (selected == null) return null;
        String external = transport.get(API + "/" + selected.mediaType + "/" + selected.tmdbId
                + "/external_ids?api_key=" + encode(apiKey));
        final String imdb;
        try {
            imdb = new JSONObject(external).optString("imdb_id", "").trim();
        } catch (org.json.JSONException error) {
            throw new IOException("TMDB returned invalid external IDs", error);
        }
        if (!imdb.matches("tt\\d+")) return null;
        return new TitleMatch(selected.title, selected.year, selected.mediaType, selected.tmdbId, imdb);
    }

    private static IOException invalidSearchResults() {
        // Null results are cached as misses. A partial or malformed response must
        // remain retryable; never include title, credential or response data here.
        return new IOException("TMDB returned invalid or incomplete search results");
    }

    private static int paginationNumber(JSONObject response, String name) {
        Object value = response.opt(name);
        if (!(value instanceof Number)) return -1;
        double number = ((Number) value).doubleValue();
        return number >= 0 && number <= Integer.MAX_VALUE && number == Math.rint(number)
                ? (int) number : -1;
    }

    private String get(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
                connection.disconnect();
                throw new InvalidApiKeyException();
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IOException("TMDB HTTP " + status);
            }
            // No disconnect on success: reading the body fully returns the socket
            // to the platform keep-alive pool, so the follow-up external_ids call
            // and later lookups reuse the connection instead of re-handshaking.
            return readAll(connection.getInputStream());
        } catch (IOException error) {
            connection.disconnect();
            throw error;
        }
    }

    private static String readAll(InputStream input) throws IOException {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    public static final class InvalidApiKeyException extends IOException {
        public InvalidApiKeyException() { super("TMDB key rejected"); }
    }
}

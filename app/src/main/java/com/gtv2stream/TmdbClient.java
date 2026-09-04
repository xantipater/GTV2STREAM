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
import java.util.List;

/** Minimal TMDB v3 client. It is called only from the service worker thread. */
public final class TmdbClient {
    private static final String API = "https://api.themoviedb.org/3";
    private final String apiKey;

    public TmdbClient(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
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
        String body = get(API + "/search/multi?api_key=" + encode(apiKey)
                + "&query=" + encode(query) + "&include_adult=false");
        final JSONArray results;
        try {
            results = new JSONObject(body).optJSONArray("results");
        } catch (org.json.JSONException error) {
            throw new IOException("TMDB returned invalid JSON", error);
        }
        if (results == null) return null;
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.optJSONObject(i);
            if (item == null) continue;
            String mediaType = item.optString("media_type", "");
            String titleKey = "movie".equals(mediaType) ? "title" : "name";
            String dateKey = "movie".equals(mediaType) ? "release_date" : "first_air_date";
            String title = item.optString(titleKey, "").trim();
            long id = item.optLong("id", -1L);
            if (("movie".equals(mediaType) || "tv".equals(mediaType)) && !title.isEmpty() && id > 0) {
                String date = item.optString(dateKey, "");
                candidates.add(new Candidate(title, date.length() >= 4 ? date.substring(0, 4) : "",
                        mediaType, id, item.optDouble("popularity", 0.0)));
            }
        }
        Candidate selected = TitleResultHelper.chooseBest(rawTitle, candidates);
        if (selected == null) return null;
        String external = get(API + "/" + selected.mediaType + "/" + selected.tmdbId
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

    private String get(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new InvalidApiKeyException();
            }
            if (status < 200 || status >= 300) throw new IOException("TMDB HTTP " + status);
            return readAll(connection.getInputStream());
        } finally {
            connection.disconnect();
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

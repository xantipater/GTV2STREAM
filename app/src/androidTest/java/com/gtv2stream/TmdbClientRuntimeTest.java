package com.gtv2stream;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runs actual TMDB response parsing and selection with scripted transport only. */
@RunWith(AndroidJUnit4.class)
public class TmdbClientRuntimeTest {
    private static final String KEY = "synthetic-test-key-not-a-credential";
    private static final String EXTERNAL = "{\"imdb_id\":\"tt0087182\"}";

    @Test public void uniqueFirstPageRequestsItsExternalIdentifier() throws Exception {
        ScriptedTransport network = new ScriptedTransport(page(1, 1, 1, movie("Dune", 1984, 841)), EXTERNAL);
        TitleMatch match = new TmdbClient(KEY, network).searchBest("Dune");
        assertNotNull(match);
        assertEquals(841, match.tmdbId);
        assertEquals("tt0087182", match.imdbId);
        assertEquals(Arrays.asList("/3/search/multi", "/3/movie/841/external_ids"), network.paths());
        assertTrue(network.requests.get(0).endsWith("&query=Dune&include_adult=false&page=1"));
    }

    @Test public void uniqueExactTitleOnSecondPageIsFoundBeforeExternalLookup() throws Exception {
        ScriptedTransport network = new ScriptedTransport(
                page(1, 2, 3, movie("Dune: Part Two", 2024, 693134)),
                page(2, 2, 3, movie("Dune", 1984, 841), movie("Alien", 1979, 348)), EXTERNAL);
        TitleMatch match = new TmdbClient(KEY, network).searchBest("Dune");
        assertNotNull(match);
        assertEquals(841, match.tmdbId);
        assertEquals(Arrays.asList("/3/search/multi", "/3/search/multi", "/3/movie/841/external_ids"),
                network.paths());
        assertTrue(network.requests.get(1).endsWith("&page=2"));
    }

    @Test public void secondPageRemakePreventsAnyExternalLookup() throws Exception {
        ScriptedTransport network = new ScriptedTransport(
                page(1, 2, 2, movie("Dune", 2021, 438631)),
                page(2, 2, 2, movie("Dune", 1984, 841)));
        assertNull(new TmdbClient(KEY, network).searchBest("Dune"));
        assertEquals(2, network.requests.size());
    }

    @Test public void explicitYearSelectsAcrossAllPagesAndIsNotPartOfSearchText() throws Exception {
        ScriptedTransport network = new ScriptedTransport(
                page(1, 2, 2, movie("Dune", 2021, 438631)),
                page(2, 2, 2, movie("Dune", 1984, 841)), EXTERNAL);
        TitleMatch match = new TmdbClient(KEY, network).searchBest("Dune (1984)");
        assertNotNull(match);
        assertEquals(841, match.tmdbId);
        assertEquals("1984", match.year);
        assertTrue(network.requests.get(0).contains("&query=Dune&"));
        assertEquals("/3/movie/841/external_ids", network.paths().get(2));
    }

    @Test public void duplicateIdentityAcrossPagesIsHarmlessButMovieAndSeriesAreDistinct() throws Exception {
        ScriptedTransport duplicate = new ScriptedTransport(
                page(1, 2, 2, movie("Dune", 1984, 841)),
                page(2, 2, 2, movie("Dune", 1984, 841)), EXTERNAL);
        assertNotNull(new TmdbClient(KEY, duplicate).searchBest("Dune"));
        JSONObject series = new JSONObject().put("media_type", "tv").put("id", 841)
                .put("name", "Dune").put("first_air_date", "1984-01-01");
        ScriptedTransport ambiguous = new ScriptedTransport(
                page(1, 2, 2, movie("Dune", 1984, 841)), page(2, 2, 2, series));
        assertNull(new TmdbClient(KEY, ambiguous).searchBest("Dune (1984)"));
        assertEquals(2, ambiguous.requests.size());
    }

    @Test public void overFivePagesFailsImmediatelyAndFiveCompletePagesCanMatch() throws Exception {
        ScriptedTransport overLimit = new ScriptedTransport(page(1, 6, 6, movie("Dune", 1984, 841)));
        assertNull(new TmdbClient(KEY, overLimit).searchBest("Dune"));
        assertEquals(1, overLimit.requests.size());
        ScriptedTransport bounded = new ScriptedTransport(
                page(1, 5, 5, movie("Alien", 1979, 1)),
                page(2, 5, 5, movie("Aliens", 1986, 2)),
                page(3, 5, 5, movie("Jaws", 1975, 3)),
                page(4, 5, 5, movie("Dune: Part Two", 2024, 4)),
                page(5, 5, 5, movie("Dune", 1984, 841)), EXTERNAL);
        assertNotNull(new TmdbClient(KEY, bounded).searchBest("Dune"));
        assertEquals(6, bounded.requests.size());
    }

    @Test public void malformedOrMissingPaginationNeverSelectsPartialCandidates() throws Exception {
        for (String key : Arrays.asList("page", "total_pages", "total_results")) {
            for (Object invalid : new Object[] {JSONObject.NULL, "1", -1, 1.5}) {
                JSONObject malformed = new JSONObject(page(1, 1, 1, movie("Dune", 1984, 841)));
                malformed.put(key, invalid);
                assertRejectedAfterFirstPage(malformed.toString());
            }
            JSONObject absent = new JSONObject(page(1, 1, 1, movie("Dune", 1984, 841)));
            absent.remove(key);
            assertRejectedAfterFirstPage(absent.toString());
        }
        assertRejectedAfterFirstPage(page(1, 0, 1, movie("Dune", 1984, 841)));
        assertRejectedAfterFirstPage(page(1, 2, 1, movie("Dune", 1984, 841)));
        assertRejectedAfterFirstPage(page(1, 2, 2));
        assertRejectedAfterFirstPage("{\"page\":1,\"total_pages\":1,\"total_results\":1}");
    }

    @Test public void inconsistentEmptyOrTruncatedLaterPageCannotResolveFirstPageMatch() throws Exception {
        String first = page(1, 2, 3, movie("Dune", 1984, 841));
        for (String later : Arrays.asList(
                page(2, 3, 3, movie("Alien", 1979, 348), movie("Aliens", 1986, 679)),
                page(2, 2, 4, movie("Alien", 1979, 348), movie("Aliens", 1986, 679)),
                page(1, 2, 3, movie("Alien", 1979, 348), movie("Aliens", 1986, 679)),
                page(2, 2, 3),
                page(2, 2, 3, movie("Alien", 1979, 348)),
                page(2, 2, 3, movie("Alien", 1979, 348), movie("Aliens", 1986, 679),
                        movie("Jaws", 1975, 578)),
                "{\"page\":2,\"total_pages\":2,\"total_results\":3}")) {
            ScriptedTransport network = new ScriptedTransport(first, later);
            assertNull(new TmdbClient(KEY, network).searchBest("Dune"));
            assertEquals(2, network.requests.size());
        }
    }

    @Test public void malformedCandidateCannotHideAnotherPotentialMatch() throws Exception {
        for (Object bad : new Object[] {JSONObject.NULL, "truncated row",
                new JSONObject().put("media_type", "movie").put("id", 2),
                new JSONObject().put("media_type", "movie").put("title", "Dune"),
                new JSONObject().put("media_type", "movie").put("title", JSONObject.NULL).put("id", 2),
                new JSONObject().put("media_type", "movie").put("title", new JSONObject()).put("id", 2),
                new JSONObject().put("media_type", "movie").put("title", "Dune").put("id", 2.5),
                new JSONObject().put("media_type", "movie").put("title", "Dune").put("id", "2"),
                new JSONObject().put("media_type", "unknown").put("title", "Dune").put("id", 2)}) {
            JSONObject response = new JSONObject(page(1, 1, 2, movie("Dune", 1984, 841)));
            response.getJSONArray("results").put(bad);
            assertRejectedAfterFirstPage(response.toString());
        }
        JSONObject person = new JSONObject().put("media_type", "person").put("id", 123).put("name", "Dune");
        ScriptedTransport network = new ScriptedTransport(
                page(1, 1, 2, movie("Dune", 1984, 841), person), EXTERNAL);
        assertNotNull(new TmdbClient(KEY, network).searchBest("Dune"));
    }

    @Test public void failedTransportNeverUsesAnEarlierPageAsCompleteResults() throws Exception {
        for (int failingRequest = 0; failingRequest < 3; failingRequest++) {
            IOException expected = new SocketTimeoutException("synthetic timeout");
            Object[] responses = {
                    page(1, 2, 2, movie("Dune", 1984, 841)),
                    page(2, 2, 2, movie("Alien", 1979, 348)), EXTERNAL
            };
            responses[failingRequest] = expected;
            ScriptedTransport network = new ScriptedTransport(responses);
            try {
                new TmdbClient(KEY, network).searchBest("Dune");
                fail("Incomplete transport must fail the lookup");
            } catch (IOException actual) {
                assertSame(expected, actual);
            }
            assertEquals(failingRequest + 1, network.requests.size());
        }
    }

    @Test public void invalidJsonAtAnyStageFailsWithoutReturningAMatch() throws Exception {
        for (int failingRequest = 0; failingRequest < 3; failingRequest++) {
            Object[] responses = {
                    page(1, 2, 2, movie("Dune", 1984, 841)),
                    page(2, 2, 2, movie("Alien", 1979, 348)), EXTERNAL
            };
            responses[failingRequest] = "{";
            ScriptedTransport network = new ScriptedTransport(responses);
            try {
                new TmdbClient(KEY, network).searchBest("Dune");
                fail("Malformed JSON must fail the lookup");
            } catch (IOException expected) {
                assertTrue(expected.getCause() instanceof org.json.JSONException);
            }
            assertEquals(failingRequest + 1, network.requests.size());
        }
    }

    @Test public void noResultsAndInvalidExternalIdentifierDoNotMatch() throws Exception {
        assertRejectedAfterFirstPage(page(1, 0, 0));
        ScriptedTransport invalidId = new ScriptedTransport(
                page(1, 1, 1, movie("Dune", 1984, 841)), "{\"imdb_id\":null}");
        assertNull(new TmdbClient(KEY, invalidId).searchBest("Dune"));
        assertEquals(2, invalidId.requests.size());
    }

    private static void assertRejectedAfterFirstPage(String body) throws Exception {
        ScriptedTransport network = new ScriptedTransport(body);
        assertNull(new TmdbClient(KEY, network).searchBest("Dune"));
        assertEquals(1, network.requests.size());
    }

    private static JSONObject movie(String title, int year, long id) throws Exception {
        return new JSONObject().put("media_type", "movie").put("title", title)
                .put("release_date", year + "-01-01").put("id", id);
    }

    private static String page(int page, int totalPages, int totalResults, JSONObject... rows) throws Exception {
        JSONArray results = new JSONArray();
        for (JSONObject row : rows) results.put(row);
        return new JSONObject().put("page", page).put("total_pages", totalPages)
                .put("total_results", totalResults).put("results", results).toString();
    }

    private static final class ScriptedTransport implements TmdbClient.Transport {
        final List<String> requests = new ArrayList<>();
        final Object[] responses;
        ScriptedTransport(Object... responses) { this.responses = responses; }

        @Override public String get(String address) throws IOException {
            int index = requests.size();
            requests.add(address);
            if (index >= responses.length) throw new AssertionError("Unexpected network request");
            Object response = responses[index];
            if (response instanceof IOException) throw (IOException) response;
            return (String) response;
        }

        List<String> paths() {
            List<String> paths = new ArrayList<>();
            for (String request : requests) paths.add(URI.create(request).getPath());
            return paths;
        }
    }
}
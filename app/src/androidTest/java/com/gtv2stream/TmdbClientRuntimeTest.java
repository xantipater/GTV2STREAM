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
        ScriptedTransport network = new ScriptedTransport(1, page(1, 1, 1, movie("Dune", 1984, 841)), EXTERNAL);
        TitleMatch match = new TmdbClient(KEY, network).searchBest("Dune");
        assertNotNull(match);
        assertEquals(841, match.tmdbId);
        assertEquals("tt0087182", match.imdbId);
        assertEquals(Arrays.asList("/3/search/multi", "/3/movie/841/external_ids"), network.paths());
        assertTrue(network.requests.get(0).contains("&query=Dune&include_adult=false"));
    }

    @Test public void queryTextIsEncodedWithoutChangingItsTitleIdentity() throws Exception {
        ScriptedTransport network = new ScriptedTransport(1,
                page(1, 1, 1, movie("Wall-E & Friends", 1984, 841)), EXTERNAL);
        TitleMatch match = new TmdbClient(KEY, network).searchBest("Wall-E & Friends");
        assertNotNull(match);
        assertEquals("Wall-E & Friends", match.title);
        assertTrue(network.requests.get(0).contains("&query=Wall-E+%26+Friends&include_adult=false"));
        assertEquals(Arrays.asList("/3/search/multi", "/3/movie/841/external_ids"), network.paths());
    }

    @Test public void bracketedTitleIsEncodedAndMatchedWithoutBecomingNumericTitle() throws Exception {
        ScriptedTransport network = new ScriptedTransport(1,
                page(1, 1, 2, movie("[REC] 2", 2009, 841), movie("2", 2007, 2)), EXTERNAL);
        TitleMatch match = new TmdbClient(KEY, network).searchBest("[REC] 2");
        assertNotNull(match);
        assertEquals(841, match.tmdbId);
        assertEquals("[REC] 2", match.title);
        assertTrue(network.requests.get(0).contains("&query=%5BREC%5D+2&include_adult=false"));
        assertEquals(Arrays.asList("/3/search/multi", "/3/movie/841/external_ids"), network.paths());
    }

    @Test public void uniqueExactTitleOnSecondPageIsFoundBeforeExternalLookup() throws Exception {
        ScriptedTransport network = new ScriptedTransport(2,
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
        ScriptedTransport network = new ScriptedTransport(2,
                page(1, 2, 2, movie("Dune", 2021, 438631)),
                page(2, 2, 2, movie("Dune", 1984, 841)));
        assertNull(new TmdbClient(KEY, network).searchBest("Dune"));
        assertEquals(Arrays.asList("/3/search/multi", "/3/search/multi"), network.paths());
    }

    @Test public void explicitYearSelectsAcrossAllPagesAndIsNotPartOfSearchText() throws Exception {
        ScriptedTransport network = new ScriptedTransport(2,
                page(1, 2, 2, movie("Dune", 2021, 438631)),
                page(2, 2, 2, movie("Dune", 1984, 841)), EXTERNAL);
        TitleMatch match = new TmdbClient(KEY, network).searchBest("Dune (1984)");
        assertNotNull(match);
        assertEquals(841, match.tmdbId);
        assertEquals("1984", match.year);
        assertTrue(network.requests.get(0).contains("&query=Dune&"));
        assertEquals("/3/movie/841/external_ids", network.paths().get(2));
    }

    @Test public void overlappingPagesAreIncompleteRatherThanAUniqueMatch() throws Exception {
        ScriptedTransport duplicate = new ScriptedTransport(2,
                page(1, 2, 2, movie("Dune", 1984, 841)),
                page(2, 2, 2, movie("Dune", 1984, 841)), EXTERNAL);
        assertRetryableSearch(duplicate);
        assertEquals(Arrays.asList("/3/search/multi", "/3/search/multi"), duplicate.paths());
    }

    @Test public void fullFirstPageCannotHideAMissingRemakeBehindARepeatedIdentity() throws Exception {
        JSONObject[] firstPage = new JSONObject[20];
        firstPage[0] = movie("Dune", 1984, 841);
        for (int i = 1; i < firstPage.length; i++) firstPage[i] = movie("Other Film " + i, 2000, 1000 + i);
        // Between requests a ranking change moves this existing item to page two
        // and the unseen remake to page one. Totals alone still appear complete.
        ScriptedTransport overlap = new ScriptedTransport(2,
                page(1, 2, 21, firstPage), page(2, 2, 21, movie("Dune", 1984, 841)), EXTERNAL);
        assertRetryableSearch(overlap);
        assertEquals(Arrays.asList("/3/search/multi", "/3/search/multi"), overlap.paths());
    }

    @Test public void overlappingPersonRowsAlsoMakeTheSearchIncomplete() throws Exception {
        JSONObject person = new JSONObject().put("media_type", "person").put("id", 123).put("name", "Actor");
        ScriptedTransport overlap = new ScriptedTransport(2,
                page(1, 2, 3, movie("Dune", 1984, 841), person), page(2, 2, 3, person), EXTERNAL);
        assertRetryableSearch(overlap);
        assertEquals(Arrays.asList("/3/search/multi", "/3/search/multi"), overlap.paths());
    }

    @Test public void malformedPersonIdentitiesCannotProveCompleteSearchResults() throws Exception {
        for (Object invalid : new Object[] {JSONObject.NULL, "123", -1, 0, 1.5}) {
            JSONObject person = new JSONObject().put("media_type", "person").put("id", invalid).put("name", "Actor");
            assertRetryableAfterFirstPage(page(1, 1, 2, movie("Dune", 1984, 841), person));
        }
        assertRetryableAfterFirstPage(page(1, 1, 2, movie("Dune", 1984, 841),
                new JSONObject().put("media_type", "person").put("name", "Actor")));
    }

    @Test public void duplicateRowsWithinOnePageAreIncompleteToo() throws Exception {
        assertRetryableAfterFirstPage(page(1, 1, 2, movie("Dune", 1984, 841), movie("Dune", 1984, 841)));
    }

    @Test public void equalNumericIdsInDifferentMediaTypesRemainDistinct() throws Exception {
        JSONObject series = new JSONObject().put("media_type", "tv").put("id", 841)
                .put("name", "Dune").put("first_air_date", "1984-01-01");
        ScriptedTransport ambiguous = new ScriptedTransport(2,
                page(1, 2, 2, movie("Dune", 1984, 841)), page(2, 2, 2, series));
        assertNull(new TmdbClient(KEY, ambiguous).searchBest("Dune (1984)"));
        assertEquals(Arrays.asList("/3/search/multi", "/3/search/multi"), ambiguous.paths());
        JSONObject person = new JSONObject().put("media_type", "person").put("id", 841).put("name", "Actor");
        ScriptedTransport valid = new ScriptedTransport(1,
                page(1, 1, 2, movie("Dune", 1984, 841), person), EXTERNAL);
        assertNotNull(new TmdbClient(KEY, valid).searchBest("Dune"));
    }

    @Test public void overFivePagesFailsImmediatelyAndFiveCompletePagesCanMatch() throws Exception {
        ScriptedTransport overLimit = new ScriptedTransport(1, page(1, 6, 6, movie("Dune", 1984, 841)));
        assertNull(new TmdbClient(KEY, overLimit).searchBest("Dune"));
        assertEquals(Arrays.asList("/3/search/multi"), overLimit.paths());
        ScriptedTransport bounded = new ScriptedTransport(5,
                page(1, 5, 5, movie("Alien", 1979, 1)),
                page(2, 5, 5, movie("Aliens", 1986, 2)),
                page(3, 5, 5, movie("Jaws", 1975, 3)),
                page(4, 5, 5, movie("Dune: Part Two", 2024, 4)),
                page(5, 5, 5, movie("Dune", 1984, 841)), EXTERNAL);
        assertNotNull(new TmdbClient(KEY, bounded).searchBest("Dune"));
        assertEquals(6, bounded.requests.size());
    }

    @Test public void malformedOrMissingPaginationFailsWithRetryableError() throws Exception {
        for (String key : Arrays.asList("page", "total_pages", "total_results")) {
            for (Object invalid : new Object[] {JSONObject.NULL, "1", -1, 1.5}) {
                JSONObject malformed = new JSONObject(page(1, 1, 1, movie("Dune", 1984, 841)));
                malformed.put(key, invalid);
                assertRetryableAfterFirstPage(malformed.toString());
            }
            JSONObject absent = new JSONObject(page(1, 1, 1, movie("Dune", 1984, 841)));
            absent.remove(key);
            assertRetryableAfterFirstPage(absent.toString());
        }
        assertRetryableAfterFirstPage(page(1, 0, 1, movie("Dune", 1984, 841)));
        assertRetryableAfterFirstPage(page(1, 2, 1, movie("Dune", 1984, 841)));
        assertRetryableAfterFirstPage(page(1, 2, 2));
        assertRetryableAfterFirstPage(page(1, 1, 2, movie("Dune", 1984, 841)));
        assertRetryableAfterFirstPage("{\"page\":1,\"total_pages\":1,\"total_results\":1}");
    }

    @Test public void inconsistentEmptyOrTruncatedLaterPageFailsWithRetryableError() throws Exception {
        String first = page(1, 2, 3, movie("Dune", 1984, 841));
        for (String later : Arrays.asList(
                page(2, 3, 3, movie("Alien", 1979, 348), movie("Aliens", 1986, 679)),
                page(2, 6, 6, movie("Alien", 1979, 348), movie("Aliens", 1986, 679)),
                page(2, 2, 4, movie("Alien", 1979, 348), movie("Aliens", 1986, 679)),
                page(1, 2, 3, movie("Alien", 1979, 348), movie("Aliens", 1986, 679)),
                page(2, 2, 3),
                page(2, 2, 3, movie("Alien", 1979, 348)),
                page(2, 2, 3, movie("Alien", 1979, 348), movie("Aliens", 1986, 679),
                        movie("Jaws", 1975, 578)),
                "{\"page\":2,\"total_pages\":2,\"total_results\":3}")) {
            ScriptedTransport network = new ScriptedTransport(2, first, later);
            assertRetryableSearch(network);
            assertEquals(Arrays.asList("/3/search/multi", "/3/search/multi"), network.paths());
        }
    }

    @Test public void malformedCandidateFailsWithRetryableError() throws Exception {
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
            assertRetryableAfterFirstPage(response.toString());
        }
        JSONObject person = new JSONObject().put("media_type", "person").put("id", 123).put("name", "Dune");
        ScriptedTransport network = new ScriptedTransport(1,
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
            ScriptedTransport network = new ScriptedTransport(2, responses);
            try {
                new TmdbClient(KEY, network).searchBest("Dune");
                fail("Incomplete transport must fail the lookup");
            } catch (IOException actual) {
                assertSame(expected, actual);
            }
            assertEquals(Arrays.asList("/3/search/multi", "/3/search/multi",
                    "/3/movie/841/external_ids").subList(0, failingRequest + 1), network.paths());
        }
    }

    @Test public void invalidJsonAtAnyStageFailsWithoutReturningAMatch() throws Exception {
        for (int failingRequest = 0; failingRequest < 3; failingRequest++) {
            Object[] responses = {
                    page(1, 2, 2, movie("Dune", 1984, 841)),
                    page(2, 2, 2, movie("Alien", 1979, 348)), EXTERNAL
            };
            responses[failingRequest] = "{";
            ScriptedTransport network = new ScriptedTransport(2, responses);
            try {
                new TmdbClient(KEY, network).searchBest("Dune");
                fail("Malformed JSON must fail the lookup");
            } catch (IOException expected) {
                assertTrue(expected.getCause() instanceof org.json.JSONException);
            }
            assertEquals(Arrays.asList("/3/search/multi", "/3/search/multi",
                    "/3/movie/841/external_ids").subList(0, failingRequest + 1), network.paths());
        }
    }

    @Test public void noResultsAndInvalidExternalIdentifierDoNotMatch() throws Exception {
        assertRejectedAfterFirstPage(page(1, 0, 0));
        assertRejectedAfterFirstPage(page(1, 1, 0));
        for (String external : Arrays.asList("{\"imdb_id\":null}", "{\"imdb_id\":\"\"}",
                "{\"imdb_id\":\"not-imdb\"}", "{}")) {
            ScriptedTransport invalidId = new ScriptedTransport(1,
                    page(1, 1, 1, movie("Dune", 1984, 841)), external);
            assertNull(new TmdbClient(KEY, invalidId).searchBest("Dune"));
            assertEquals(Arrays.asList("/3/search/multi", "/3/movie/841/external_ids"), invalidId.paths());
        }
    }

    @Test public void zeroResultCountWithRowsOrAdditionalPagesIsRetryable() throws Exception {
        assertRetryableAfterFirstPage(page(1, 0, 0, movie("Dune", 1984, 841)));
        assertRetryableAfterFirstPage(page(1, 1, 0, movie("Dune", 1984, 841)));
        assertRetryableAfterFirstPage(page(1, 2, 0));
    }

    @Test public void completeUnmatchedTitleRemainsAnAuthoritativeMiss() throws Exception {
        assertRejectedAfterFirstPage(page(1, 1, 1, movie("Alien", 1979, 348)));
    }

    private static void assertRejectedAfterFirstPage(String body) throws Exception {
        ScriptedTransport network = new ScriptedTransport(1, body);
        assertNull(new TmdbClient(KEY, network).searchBest("Dune"));
        assertEquals(Arrays.asList("/3/search/multi"), network.paths());
    }

    private static void assertRetryableAfterFirstPage(String body) throws Exception {
        ScriptedTransport network = new ScriptedTransport(1, body);
        assertRetryableSearch(network);
        assertEquals(Arrays.asList("/3/search/multi"), network.paths());
    }

    private static void assertRetryableSearch(ScriptedTransport network) throws Exception {
        try {
            new TmdbClient(KEY, network).searchBest("Dune");
            fail("Incomplete search results must remain retryable, not become a cached miss");
        } catch (IOException expected) {
            assertEquals(IOException.class, expected.getClass());
            assertNotNull(expected.getMessage());
            assertFalse(expected.getMessage().contains(KEY));
            assertFalse(expected.getMessage().contains("Dune"));
            assertFalse(expected.getMessage().contains("truncated row"));
            assertNull(expected.getCause());
        }
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
        final int searchPages;
        ScriptedTransport(int searchPages, Object... responses) {
            this.searchPages = searchPages;
            this.responses = responses;
        }

        @Override public String get(String address) throws IOException {
            int index = requests.size();
            requests.add(address);
            if (index >= responses.length) throw new AssertionError("Unexpected network request");
            URI request = URI.create(address);
            // Bind each response to its intended endpoint. Otherwise a broken
            // client can receive page-two JSON from an external_ids request,
            // return null, and falsely satisfy a no-match assertion.
            assertEquals(index < searchPages ? "/3/search/multi" : "/3/movie/841/external_ids",
                    request.getPath());
            if (index < searchPages) {
                String requestedPage = parameter(request, "page");
                if (index == 0 && requestedPage == null) requestedPage = "1";
                assertEquals("Search page", Integer.toString(index + 1), requestedPage);
            }
            Object response = responses[index];
            if (response instanceof IOException) throw (IOException) response;
            return (String) response;
        }

        private static String parameter(URI request, String name) {
            for (String parameter : request.getRawQuery().split("&")) {
                if (parameter.startsWith(name + "=")) return parameter.substring(name.length() + 1);
            }
            return null;
        }

        List<String> paths() {
            List<String> paths = new ArrayList<>();
            for (String request : requests) paths.add(URI.create(request).getPath());
            return paths;
        }
    }
}

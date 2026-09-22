package com.gtv2stream;

import java.util.Arrays;

/** Production parser/matcher regressions for evidence enrichment and numeric title slots. */
public final class MatchingBoundaryTest {
    private static int checks;

    public static void main(String[] args) {
        checks = 0;
        if (args.length == 0 || "years".equals(args[0])) yearEvidence();
        if (args.length == 0 || "numeric".equals(args[0])) numericTitleSlots();
        if (args.length == 0 || "brackets".equals(args[0])) bracketedTitles();
        System.out.println("MatchingBoundaryTest: PASS (" + checks + " checks)");
    }

    static int run() {
        checks = 0;
        yearEvidence();
        numericTitleSlots();
        bracketedTitles();
        return checks;
    }

    private static void yearEvidence() {
        RecommendationTitleParser.Source event = RecommendationTitleParser.fromEventTextSource(
                Arrays.asList("Dune", "Watch on Netflix"));
        RecommendationTitleParser.Source description = RecommendationTitleParser.fromDescriptionSource(
                "Dune (1984). Watch on Netflix");
        RecommendationTitleParser.Source enriched = RecommendationTitleParser.withProviderContext(event, description);
        check("Dune (1984)".equals(enriched.lookupTitle()), "provider-bearing event retains description year");
        check("netflix".equals(enriched.provider), "year enrichment preserves the agreed provider");
        TmdbClient.Candidate original = new TmdbClient.Candidate("Dune", "1984", "movie", 841, 1);
        TmdbClient.Candidate remake = new TmdbClient.Candidate("Dune", "2021", "movie", 438631, 100);
        check(TitleResultHelper.chooseBest(enriched.lookupTitle(), Arrays.asList(original, remake)) == original,
                "description year resolves the intended remake through the production matcher");

        RecommendationTitleParser.Source bare = RecommendationTitleParser.fromDirectTextSource("Dune");
        RecommendationTitleParser.Source dated = RecommendationTitleParser.fromDirectTextSource("Dune (1984)");
        check("Dune (1984)".equals(RecommendationTitleParser.withProviderContext(bare, dated).lookupTitle()),
                "year enrichment does not require a new provider");
        check("Dune (1984)".equals(RecommendationTitleParser.withProviderContext(dated, event).lookupTitle()),
                "provider enrichment does not discard the primary year");
        check("Dune (1984)".equals(RecommendationTitleParser.withProviderContext(description, event).lookupTitle()),
                "yearless context cannot erase an explicit primary year");
        check(RecommendationTitleParser.withProviderContext(description,
                RecommendationTitleParser.fromDescriptionSource("Dune (2021). Watch on Netflix")).isEmpty(),
                "conflicting explicit years are not combined");
        check(RecommendationTitleParser.withProviderContext(description,
                RecommendationTitleParser.fromDescriptionSource("Dune (1984). Watch on Prime Video")).isEmpty(),
                "conflicting providers are not combined");
        check(RecommendationTitleParser.withProviderContext(event,
                RecommendationTitleParser.fromDescriptionSource("Dune. Watch on YouTube")).isEmpty(),
                "conflicting routes are not combined");
        check(RecommendationTitleParser.withProviderContext(event,
                RecommendationTitleParser.fromDescriptionSource("Alien (1979). Watch on Netflix")) == event,
                "unrelated context cannot lend its year to the selected title");
        check(RecommendationTitleParser.withProviderContext(event, RecommendationTitleParser.Source.NONE) == event,
                "empty context preserves the selection");
    }

    private static void numericTitleSlots() {
        for (String title : Arrays.asList("9", "65", "300", "1917", "2012")) {
            check(title.equals(RecommendationTitleParser.fromDescription(title + ". Watch on Netflix")),
                    "recognized provider action accepts numeric title " + title);
            check(title.equals(RecommendationTitleParser.fromDescription("Netflix. " + title + ".")),
                    "provider-first title slot accepts numeric title " + title);
            check(title.equals(RecommendationTitleParser.fromDescription(title + ", Prime Video")),
                    "provider-tail title slot accepts numeric title " + title);
            check(title.equals(RecommendationTitleParser.fromEventText(Arrays.asList(title, "Watch on Netflix"))),
                    "provider-bearing event accepts numeric title " + title);
            check(title.equals(RecommendationTitleParser.fromEventText(Arrays.asList("Netflix", title))),
                    "provider-first event accepts numeric title " + title);
            check(title.equals(RecommendationTitleParser.fromDetailTitleSource(title).title),
                    "authoritative detail accepts numeric title " + title);
            check(title.equals(RecommendationTitleParser.fromDetailTitle(title)),
                    "untyped authoritative detail keeps the same numeric-title contract " + title);
            check(RecommendationTitleParser.fromDirectText(title).isEmpty()
                            && RecommendationTitleParser.fromDescription(title).isEmpty()
                            && RecommendationTitleParser.fromEventText(Arrays.asList(title)).isEmpty(),
                    "untrusted numeric metadata is not a title " + title);
            check(RecommendationTitleParser.youtubeSource(title).isEmpty(),
                    "ambient hero scanning still rejects bare numbers " + title);
        }
        check("1917 (2019)".equals(RecommendationTitleParser.fromDescriptionSource(
                "1917 (2019). Watch on Netflix").lookupTitle()), "numeric title keeps a separate explicit year");
        check("1917".equals(RecommendationTitleParser.fromDescription(
                "1917, Netflix, fresh rating: 89% on Rotten Tomatoes")), "numeric comma-middle title slot");
        check("1917".equals(RecommendationTitleParser.fromDescription(
                "1917, requires Prime Video subscription")), "numeric subscription-card title slot");
        for (String metadata : Arrays.asList("0", "0001", "12345", "1.5", "12%", "1:30", "3/10",
                "-12", "+12", "1917 2019", "１２３", "Column 3", "Row 1", "Season 1", "Episode 2")) {
            check(RecommendationTitleParser.fromDetailTitle(metadata).isEmpty(),
                    "detail numeric exception rejects metadata or out-of-bound values: " + metadata);
            check(RecommendationTitleParser.fromDescription(metadata + ". Watch on Netflix").isEmpty(),
                    "provider numeric exception rejects metadata or out-of-bound values: " + metadata);
        }
        check(RecommendationTitleParser.fromDescription("1917. Watch on Unknown Provider").isEmpty(),
                "an unrecognized provider cannot authorize a numeric title");
        check(RecommendationTitleParser.fromDescription("1917. Film. Drama").isEmpty(),
                "segmentation without provider evidence cannot authorize a numeric title");
        check(RecommendationTitleParser.fromDescription("Sponsored. 1917. Watch on Netflix").isEmpty(),
                "numeric provider card keeps sponsored rejection");
        check(RecommendationTitleParser.fromDescription("Advertisement. 1917. Watch on Netflix").isEmpty(),
                "numeric provider card keeps advertisement rejection");
        check(RecommendationTitleParser.isRejectedPayload("Ad"),
                "standalone ad badge is positive terminal evidence");
        check(RecommendationTitleParser.fromEventText(Arrays.asList("1917", "Sponsored", "Watch on Netflix")).isEmpty(),
                "numeric event title does not bypass rejection elsewhere in the event");
        check(RecommendationTitleParser.fromDetailTitle("Sponsored 1917").isEmpty(),
                "authoritative detail title keeps sponsored rejection");
    }

    private static void bracketedTitles() {
        check("[REC]".equals(RecommendationTitleParser.fromDescription(
                "[REC]. Watch on Netflix")), "bracketed film title survives provider parsing");
        check("[REC] 2".equals(RecommendationTitleParser.fromDescription(
                "[REC] 2. Watch on Netflix")), "bracketed sequel is not reduced to its number");
        check("[REC]".equals(RecommendationTitleParser.fromDetailTitle("[REC]")),
                "authoritative detail preserves bracketed title punctuation");
        check("[REC] 2".equals(RecommendationTitleParser.fromEventText(
                Arrays.asList("[REC] 2", "Watch on Netflix"))),
                "provider-bearing event preserves bracketed sequel");
        check("[REC] 2".equals(TitleResultHelper.cleanTitle("[REC] 2 (2009) [IMAX]")),
                "year and safe trailing presentation badge are removed without losing title brackets");
        check("[REC] 2 (2009)".equals(RecommendationTitleParser.fromDescriptionSource(
                "[REC] 2 [IMAX] (2009). Watch on Netflix").lookupTitle()),
                "safe presentation badge between title and year does not hide year metadata");
        check("Dune [Part Two]".equals(TitleResultHelper.cleanTitle("Dune [Part Two]")),
                "unknown trailing brackets remain title text");
        check(RecommendationTitleParser.fromDescription(
                "[REC] 2 [Sponsored]. Watch on Netflix").isEmpty(),
                "bracket preservation cannot hide sponsored content");
        check(RecommendationTitleParser.fromDetailTitle("[REC] [Advertisement]").isEmpty(),
                "authoritative detail keeps advertisement rejection terminal");
        check(RecommendationTitleParser.isRejectedPayload("[Ad] Dune. Watch on Netflix"),
                "bounded bracketed ad badge is terminal payload evidence");
        check(RecommendationTitleParser.fromDescription("[Ad] Dune. Watch on Netflix").isEmpty(),
                "bracket preservation cannot turn an ad-labelled card into Dune");
        check(RecommendationTitleParser.fromEventText(
                Arrays.asList("[Ad] Dune", "Watch on Netflix")).isEmpty(),
                "event parsing rejects a bracketed ad badge");
        check(RecommendationTitleParser.fromDetailTitle("[Ad] Dune").isEmpty(),
                "authoritative detail parsing rejects a bracketed ad badge");
        check(RecommendationTitleParser.youtubeSource("[Ad] Dune").isEmpty(),
                "ambient YouTube parsing rejects a bracketed ad badge");

        TmdbClient.Candidate sequel = new TmdbClient.Candidate("[REC] 2", "2009", "movie", 44363, 1);
        TmdbClient.Candidate unrelated = new TmdbClient.Candidate("2", "2007", "movie", 2, 100);
        check(TitleResultHelper.chooseBest("[REC] 2", Arrays.asList(sequel, unrelated)) == sequel,
                "exact matching cannot redirect a bracketed sequel to numeric title 2");
        check(TitleResultHelper.chooseBest("[REC]", Arrays.asList(
                new TmdbClient.Candidate("[REC]", "2007", "movie", 8329, 1))) != null,
                "exact matching retains a wholly bracketed title");
        check("https://www.youtube.com/results?search_query=%5BREC%5D+2".equals(
                TitleResultHelper.youtubeSearchUri("[REC] 2")),
                "outbound title query encodes rather than strips brackets");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}

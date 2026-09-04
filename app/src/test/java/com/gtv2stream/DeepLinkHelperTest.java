package com.gtv2stream;

import java.util.Arrays;
import java.util.Collections;

/** Dependency-free unit test harness; run with ./gradlew runHelperTests. */
public final class DeepLinkHelperTest {
    public static void main(String[] args) {
        int assertions = 0;
        TitleMatch movie = new TitleMatch("Iron Man", "2008", "movie", 1726, "tt0371746");
        TitleMatch show = new TitleMatch("Example Show", "2020", "tv", 1, "tt1234567");
        check("nuvio://movie/tt0371746".equals(TitleResultHelper.nuvioUri(movie)), "movie URI"); assertions++;
        check("nuvio://detail/tv/tt1234567".equals(TitleResultHelper.nuvioUri(show)), "series detail URI"); assertions++;
        check(TitleResultHelper.nuvioUri(new TitleMatch("x", "", "movie", 1, "bad")) == null, "invalid IMDb rejected"); assertions++;

        check("Dune".equals(TitleResultHelper.cleanTitle("Dune (2021)")), "year cleaned"); assertions++;
        check("Trigger Point".equals(RecommendationTitleParser.fromEventText(Arrays.asList(
                "Trigger Point", "Season 4 • Thriller", "Synopsis", "Watch on ITVX"))),
                "direct event item wins"); assertions++;
        check("Trigger Point".equals(RecommendationTitleParser.fromEventText(Arrays.asList(
                "ITVX", "Trigger Point", "Season 4 • Thriller"))),
                "provider-first event item wins"); assertions++;
        check(RecommendationTitleParser.fromEventText(Arrays.asList(
                "Season 4 • Thriller", "Trigger Point")).isEmpty(),
                "metadata-first event does not scan ahead"); assertions++;
        check("Vigil".equals(TitleResultHelper.extractLauncherTitle(
                "BBC iPlayer. Vigil. Just added. Military investigator DCI Silva fights to uncover conspiracies of silence. Watch Now on BBC iPlayer")),
                "period recommendation title"); assertions++;
        check("Trigger Point".equals(TitleResultHelper.extractLauncherTitle(
                "ITVX. Trigger Point. Season 4 • Thriller. Watch on ITVX")),
                "provider period recommendation title"); assertions++;
        check("Trigger Point".equals(TitleResultHelper.extractLauncherTitle(
                "Trigger Point. Season 4 • Thriller. Watch on ITVX")),
                "title-first period recommendation title"); assertions++;
        check("Trigger Point".equals(TitleResultHelper.extractLauncherTitle(
                "Trigger Point, Season 4 • Thriller, synopsis, Watch on ITVX")),
                "comma recommendation title"); assertions++;
        check("Dune".equals(TitleResultHelper.extractLauncherTitle("Dune")), "raw title fallback"); assertions++;
        check("Dune".equals(TitleResultHelper.extractLauncherTitle("Dune (2021)")), "raw year title fallback"); assertions++;
        check("Mr. Robot".equals(TitleResultHelper.extractLauncherTitle("Mr. Robot")), "punctuated title accepted"); assertions++;
        check("S.W.A.T.".equals(TitleResultHelper.extractLauncherTitle("S.W.A.T.")), "initialism title accepted"); assertions++;
        check("Spider-Man: No Way Home".equals(TitleResultHelper.extractLauncherTitle("Spider-Man: No Way Home")),
                "hyphenated colon title accepted"); assertions++;
        check(RecommendationTitleParser.fromDescription("Watch on ITVX").isEmpty(), "watch label rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Sponsored, Install app, Learn more").isEmpty(), "advert rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("A detective investigates a conspiracy. Watch on ITVX").isEmpty(), "synopsis rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("The Bear. Watch on YouTube").isEmpty(), "YouTube action rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Sponsored. Trigger Point. Watch on ITVX").isEmpty(), "sponsored recommendation rejected"); assertions++;
        check(RecommendationTitleParser.fromEventText(Arrays.asList("The Bear", "Watch on YouTube")).isEmpty(),
                "YouTube event card rejected"); assertions++;
        check(RecommendationTitleParser.fromEventText(Arrays.asList("Sponsored", "The Bear", "Watch on YouTube")).isEmpty(),
                "sponsored YouTube event card rejected"); assertions++;
        check(RecommendationTitleParser.fromEventText(Arrays.asList("Sponsored", "Trigger Point")).isEmpty(),
                "sponsored event card rejected"); assertions++;
        check(TitleResultHelper.normalizedTitleMatches("Dune: Part Two", "dune part two"),
                "normalized punctuation and case match"); assertions++;
        check(!TitleResultHelper.normalizedTitleMatches("Dune", "Dune Messiah"),
                "normalized title mismatch"); assertions++;
        check(RecommendationTitleParser.fromDescription("Home").isEmpty(), "launcher control rejected"); assertions++;
        check(TitleResultHelper.extractLauncherTitle("Column 6").isEmpty(), "column accessibility label rejected"); assertions++;
        check(TitleResultHelper.extractLauncherTitle("Row 3").isEmpty(), "row accessibility label rejected"); assertions++;
        check(TitleResultHelper.extractLauncherTitle("Main user home screen").isEmpty(), "home screen label rejected"); assertions++;
        check(TitleResultHelper.extractLauncherTitle("Recommended for you").isEmpty(), "recommendation chrome rejected"); assertions++;

        TmdbClient.Candidate chosen = TitleResultHelper.chooseBest("Dune (2021)", Arrays.asList(
                new TmdbClient.Candidate("Dune", "2021", "movie", 1, 20),
                new TmdbClient.Candidate("Dune", "1984", "movie", 2, 100)));
        check(chosen != null && chosen.tmdbId == 1, "year-aware result choice"); assertions++;
        check(RecommendationTitleParser.fromEventText(Collections.singletonList("Season 4 • Thriller")).isEmpty(),
                "metadata-only event rejected"); assertions++;
        System.out.println("DeepLinkHelperTest: PASS (" + assertions + " assertions)");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}

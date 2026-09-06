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
        check("stremio:///detail/movie/tt0371746".equals(TitleResultHelper.stremioUri(movie)), "Stremio movie URI"); assertions++;
        check("stremio:///detail/series/tt1234567".equals(TitleResultHelper.stremioUri(show)), "Stremio series URI"); assertions++;
        check(TitleResultHelper.stremioUri(new TitleMatch("x", "", "movie", 1, "bad")) == null, "Stremio invalid IMDb rejected"); assertions++;

        check("https://www.youtube.com/results?search_query=Big+Buck+Bunny"
                .equals(TitleResultHelper.youtubeSearchUri("Big Buck Bunny")),
                "YouTube search URI encodes spaces"); assertions++;
        check("https://www.youtube.com/results?search_query=Mr.+Robot%3A+Season+1"
                .equals(TitleResultHelper.youtubeSearchUri("Mr. Robot: Season 1")),
                "YouTube search URI encodes punctuation"); assertions++;
        check(TitleResultHelper.youtubeSearchUri("   ") == null, "blank search title rejected"); assertions++;

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

        // Provider-tail and stream-action payloads (Paramount+ failure reports).
        check("Trigger Point".equals(RecommendationTitleParser.fromDescription(
                "Trigger Point. Watch on Paramount+.")),
                "trailing provider punctuation stripped"); assertions++;
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Landman. Stream on Paramount+")),
                "stream action recognized"); assertions++;
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Landman, Paramount+")),
                "comma provider tail accepted"); assertions++;
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Landman • Paramount+")),
                "bullet provider tail accepted"); assertions++;
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Landman • Paramount+ • Drama")),
                "bullet metadata segments accepted"); assertions++;
        check("Dune".equals(RecommendationTitleParser.fromDescription(
                "Dune. Paramount+.")),
                "period provider tail accepted"); assertions++;
        check("Mr. Robot".equals(RecommendationTitleParser.fromDescription(
                "Mr. Robot. Watch on Paramount+")),
                "punctuated title with provider action"); assertions++;

        // Provider action-suffix variants across every provider name.
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Landman. Streaming on Hulu.")),
                "streaming action recognized"); assertions++;
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Landman. New on Netflix.")),
                "new-on action recognized"); assertions++;
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Landman. Included with Prime Video.")),
                "included-with action recognized"); assertions++;
        check(RecommendationTitleParser.fromDescription("Included with Prime Video").isEmpty(),
                "included-with label rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("New on Netflix").isEmpty(),
                "new-on label rejected"); assertions++;

        // Dash separators behave like commas and bullets.
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Landman - Netflix")),
                "hyphen provider tail accepted"); assertions++;
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Landman — Netflix")),
                "em dash provider tail accepted"); assertions++;
        check("The Terminal List".equals(RecommendationTitleParser.fromDescription(
                "The Terminal List – Prime Video")),
                "en dash provider tail accepted"); assertions++;
        check("Landman - Part Two".equals(RecommendationTitleParser.fromDescription(
                "Landman - Part Two")),
                "dash inside a title is preserved"); assertions++;

        // Provider name variants lead and tail across separators.
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Apple TV. Landman.")),
                "apple tv provider-first with trailing period"); assertions++;
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Hulu. Landman.")),
                "hulu provider-first two segments"); assertions++;
        check("Landman".equals(RecommendationTitleParser.fromEventText(Arrays.asList(
                "HBO Max", "Landman"))),
                "hbo max provider-first event item"); assertions++;
        check("Landman".equals(RecommendationTitleParser.fromDescription(
                "Landman. Disney+.")),
                "disney plus provider tail"); assertions++;
        check("S.W.A.T.".equals(RecommendationTitleParser.fromDescription(
                "S.W.A.T. Watch on Netflix.")),
                "initialism survives provider strip and trailing period"); assertions++;
        check(RecommendationTitleParser.fromDescription("Sponsored. Landman. Watch on Apple TV+.").isEmpty(),
                "sponsored apple tv payload rejected"); assertions++;
        check(RecommendationTitleParser.fromEventText(Arrays.asList(
                "Netflix", "Landman")).equals("Landman"),
                "netflix provider-first event item"); assertions++;
        check(RecommendationTitleParser.fromDescription("Watch on ITVX").isEmpty(), "watch label rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Watch on Paramount+").isEmpty(), "provider watch label rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("A gritty crime drama. Stream on Paramount+").isEmpty(), "stream synopsis rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("A detective investigates a conspiracy. Watch on ITVX").isEmpty(), "synopsis rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Sponsored, Install app, Learn more").isEmpty(), "advert rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Sponsored. Trigger Point. Watch on ITVX").isEmpty(), "sponsored recommendation rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Sponsored. Landman. Watch on Paramount+.").isEmpty(),
                "sponsored provider payload rejected"); assertions++;

        // YouTube payloads classify as YouTube content instead of being discarded.
        RecommendationTitleParser.Source described =
                RecommendationTitleParser.fromDescriptionSource("The Bear. Watch on YouTube");
        check("The Bear".equals(described.title) && described.youtube,
                "YouTube description classified"); assertions++;
        RecommendationTitleParser.Source events = RecommendationTitleParser.fromEventTextSource(
                Arrays.asList("The Bear", "Watch on YouTube"));
        check("The Bear".equals(events.title) && events.youtube,
                "YouTube event card classified"); assertions++;
        RecommendationTitleParser.Source providerFirst = RecommendationTitleParser.fromEventTextSource(
                Arrays.asList("YouTube", "The Bear"));
        check("The Bear".equals(providerFirst.title) && providerFirst.youtube,
                "YouTube provider-first card classified"); assertions++;
        RecommendationTitleParser.Source nowOn = RecommendationTitleParser.fromEventTextSource(
                Arrays.asList("The Bear", "Watch Now on YouTube"));
        check(nowOn.youtube, "watch-now-on-youtube variant classified"); assertions++;
        check(RecommendationTitleParser.fromEventText(Arrays.asList("The Bear", "Watch on YouTube")).equals("The Bear"),
                "YouTube event card title extracted"); assertions++;
        check(RecommendationTitleParser.fromEventTextSource(Arrays.asList("Sponsored", "The Bear", "Watch on YouTube")).isEmpty(),
                "sponsored YouTube event card rejected"); assertions++;
        check(RecommendationTitleParser.fromEventText(Arrays.asList("Sponsored", "Trigger Point")).isEmpty(),
                "sponsored event card rejected"); assertions++;
        check(RecommendationTitleParser.fromDescriptionSource("Watch on YouTube").isEmpty(),
                "YouTube watch label rejected"); assertions++;
        RecommendationTitleParser.Source longYouTubeTitle = RecommendationTitleParser.youtubeSource(
                "GPT-6 Astra Is INSANE – Is THIS Actually AGI?");
        check("GPT-6 Astra Is INSANE – Is THIS Actually AGI?".equals(longYouTubeTitle.title)
                        && longYouTubeTitle.youtube,
                "long YouTube hero title accepted"); assertions++;
        check(RecommendationTitleParser.youtubeSource("Sponsored video").isEmpty(),
                "sponsored YouTube hero title rejected"); assertions++;

        check(TitleResultHelper.normalizedTitleMatches("Dune: Part Two", "dune part two"),
                "normalized punctuation and case match"); assertions++;
        check(!TitleResultHelper.normalizedTitleMatches("Dune", "Dune Messiah"),
                "normalized title mismatch"); assertions++;
        check(RecommendationTitleParser.fromDescription("Home").isEmpty(), "launcher control rejected"); assertions++;
        // Launcher quick-settings and edit-mode buttons must never become titles.
        check(RecommendationTitleParser.fromDescription("Display").isEmpty(), "display button rejected"); assertions++;
        check(RecommendationTitleParser.fromEventText(Arrays.asList("Move")).isEmpty(), "move button rejected"); assertions++;
        check(RecommendationTitleParser.fromEventText(Arrays.asList("Move", "Dune")).isEmpty(),
                "UI button does not scan ahead"); assertions++;
        check(RecommendationTitleParser.fromDescription("Remove").isEmpty(), "remove button rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Restart").isEmpty(), "restart button rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Notifications").isEmpty(), "notifications rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Kids").isEmpty(), "navigation tab rejected"); assertions++;
        // Comprehensive Google TV UI vocabulary exclusion.
        check(RecommendationTitleParser.fromDescription("Network & Internet").isEmpty(), "quick-settings row rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Display & Sound").isEmpty(), "display & sound rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Device Preferences").isEmpty(), "device preferences rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Ambient Mode").isEmpty(), "ambient mode rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Move to Top").isEmpty(), "edit-mode verb rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("On").isEmpty(), "toggle label rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("HDMI 1").isEmpty(), "input port label rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Input 2").isEmpty(), "input number label rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Buy $3.99").isEmpty(), "price action rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Sound options").isEmpty(), "suffixed chrome rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Android TV OS build").isEmpty(), "about row rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("See all apps").isEmpty(), "apps row rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Parental controls").isEmpty(), "safety row rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Picture mode").isEmpty(), "picture row rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Force stop").isEmpty(), "app action rejected"); assertions++;
        check(TitleResultHelper.extractLauncherTitle("Column 6").isEmpty(), "column accessibility label rejected"); assertions++;
        check(TitleResultHelper.extractLauncherTitle("Row 3").isEmpty(), "row accessibility label rejected"); assertions++;
        check(TitleResultHelper.extractLauncherTitle("Main user home screen").isEmpty(), "home screen label rejected"); assertions++;
        check(TitleResultHelper.extractLauncherTitle("Recommended for you").isEmpty(), "recommendation chrome rejected"); assertions++;

        TmdbClient.Candidate chosen = TitleResultHelper.chooseBest("Dune (2021)", Arrays.asList(
                new TmdbClient.Candidate("Dune", "2021", "movie", 1, 20),
                new TmdbClient.Candidate("Dune", "1984", "movie", 2, 100)));
        check(chosen != null && chosen.tmdbId == 1, "year-aware result choice"); assertions++;

        MatchCache.clear();
        check(MatchCache.get("Landman") == null, "empty cache misses"); assertions++;
        MatchCache.put("Landman", movie);
        TitleMatch cached = MatchCache.get("landman  (2021)");
        check(cached == movie, "cache hit is normalized across punctuation, case, and year"); assertions++;
        check(MatchCache.get("Different Show") == null, "distinct title misses"); assertions++;
        MatchCache.put("Dune", show);
        check(MatchCache.get("Landman") == movie && MatchCache.get("Dune") == show, "multiple entries retained"); assertions++;
        MatchCache.put(null, movie);
        MatchCache.put("Dune", null);
        check(MatchCache.get("Dune") == show, "null stores are ignored"); assertions++;
        MatchCache.clear();
        check(MatchCache.get("Landman") == null, "clear evicts"); assertions++;
        check(RecommendationTitleParser.fromEventText(Collections.singletonList("Season 4 • Thriller")).isEmpty(),
                "metadata-only event rejected"); assertions++;
        System.out.println("DeepLinkHelperTest: PASS (" + assertions + " assertions)");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}

package com.gtv2stream;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Dependency-free unit test harness; run with ./gradlew runHelperTests. */
public final class DeepLinkHelperTest {
    public static void main(String[] args) throws Exception {
        int assertions = LauncherInteractionTest.run() + StabilisationTest.run();
        check(!YouTubeTarget.isTizenTube(null), "null YouTube target keeps SmartTube default"); assertions++;
        check(!YouTubeTarget.isTizenTube(YouTubeTarget.SMARTTUBE), "SmartTube target selected"); assertions++;
        check(YouTubeTarget.isTizenTube(YouTubeTarget.TIZENTUBE), "TizenTube target selected"); assertions++;
        check(!YouTubeTarget.isTizenTube("unexpected"), "unknown target safely keeps SmartTube"); assertions++;
        // Android 14 bound: TizenTube selection launches MPFS at the explicit
        // Cobalt component; a missing Cobalt fails closed, never SmartTube.
        check(YouTubeTarget.pickTizenTubeRoute(true)
                == YouTubeTarget.Route.TIZENTUBE_VIEW, "installed Cobalt launches"); assertions++;
        check(YouTubeTarget.pickTizenTubeRoute(false)
                == YouTubeTarget.Route.FAIL_CLOSED, "missing Cobalt fails closed, no SmartTube fallback"); assertions++;
        check("io.gh.reisxd.tizentube.cobalt".equals(YouTubeTarget.COBALT_PACKAGE),
                "Cobalt package contract"); assertions++;
        check("dev.cobalt.app.MainActivity".equals(YouTubeTarget.COBALT_ACTIVITY),
                "Cobalt activity contract"); assertions++;
        check("android.media.action.MEDIA_PLAY_FROM_SEARCH"
                        .equals(YouTubeTarget.ACTION_MEDIA_PLAY_FROM_SEARCH),
                "Cobalt MPFS action contract"); assertions++;
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
        // Precompiled-pattern refactor: identical outputs for every cleaning input shape.
        check("".equals(TitleResultHelper.cleanTitle(null)), "null title cleans to empty"); assertions++;
        check("".equals(TitleResultHelper.cleanTitle("   ")), "blank title cleans to empty"); assertions++;
        check("Dune".equals(TitleResultHelper.cleanTitle("Dune\n(2021)\r [IMAX]")),
                "newlines, years, and brackets cleaned"); assertions++;
        check("Trigger  Point".replace("  ", " ").equals(
                        TitleResultHelper.cleanTitle("Trigger\tPoint")),
                "tabs collapse to one space"); assertions++;
        check("dune part two".equals(TitleResultHelper.normalizedTitle("Dune: Part Two")),
                "normalization strips punctuation"); assertions++;
        check("".equals(TitleResultHelper.normalizedTitle(null)), "null normalizes to empty"); assertions++;
        check("landman".equals(TitleResultHelper.normalizedTitle("Landman!")),
                "trailing punctuation normalized"); assertions++;
        check("mr robot".equals(TitleResultHelper.normalizedTitle("Mr. Robot")),
                "periods normalize to spaces"); assertions++;
        check("dune".equals(TitleResultHelper.normalizedTitle("Dune (2021)")),
                "year parens normalize away"); assertions++;
        // Single-read prefs refactor: the pure overload resolves exactly like before.
        check(AppPrefs.MOVIES_NUVIO.equals(LaunchPolicy.moviesTarget(AppPrefs.MOVIES_NUVIO)),
                "nuvio target preserved"); assertions++;
        check(AppPrefs.MOVIES_STREMIO.equals(LaunchPolicy.moviesTarget(AppPrefs.MOVIES_STREMIO)),
                "stremio target preserved"); assertions++;
        check(AppPrefs.MOVIES_NUVIO.equals(LaunchPolicy.moviesTarget(null)),
                "null target defaults to nuvio"); assertions++;
        check(AppPrefs.MOVIES_NUVIO.equals(LaunchPolicy.moviesTarget("unexpected")),
                "unknown target defaults to nuvio"); assertions++;
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

        // Available-on actions are emitted by provider cards, including Disney+.
        check("Daredevil".equals(RecommendationTitleParser.fromDescription(
                "Daredevil. Available on Disney+")),
                "Disney+ available-on action recognized"); assertions++;
        check("Daredevil".equals(RecommendationTitleParser.fromDescription(
                "Disney+. Daredevil.")),
                "Disney+ provider-first title recognized"); assertions++;
        check("Daredevil".equals(RecommendationTitleParser.fromDescription(
                "Daredevil — Disney+")),
                "Disney+ dash provider recognized"); assertions++;
        check("The Witcher".equals(RecommendationTitleParser.fromDescription(
                "The Witcher. Available on Netflix")),
                "Netflix available-on action recognized"); assertions++;
        check("The Boys".equals(RecommendationTitleParser.fromDescription(
                "The Boys. Available on Prime Video")),
                "Prime available-on action recognized"); assertions++;
        check("Vigil".equals(RecommendationTitleParser.fromDescription(
                "Vigil. Available on ITVX")),
                "ITVX available-on action recognized"); assertions++;
        check(RecommendationTitleParser.fromDescription("Available on Disney+").isEmpty(),
                "available-on provider label rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Sponsored. Daredevil. Available on Disney+").isEmpty(),
                "sponsored available-on payload rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Dune. Available on Friday").isEmpty(),
                "unknown available-on target rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("Available on your home screen").isEmpty(),
                "prose available-on phrase rejected"); assertions++;

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
        TitleMatch cached = MatchCache.get("landman  ");
        check(cached == movie, "cache hit is normalized across punctuation and case"); assertions++;
        check(MatchCache.get("Different Show") == null, "distinct title misses"); assertions++;
        MatchCache.put("Dune", show);
        check(MatchCache.get("Landman") == movie && MatchCache.get("Dune") == show, "multiple entries retained"); assertions++;
        MatchCache.put(null, movie);
        MatchCache.put("Dune", null);
        check(MatchCache.get("Dune") == show, "null stores are ignored"); assertions++;
        MatchCache.clear();
        check(MatchCache.get("Landman") == null, "clear evicts"); assertions++;
        // Negative results fail closed fast: a recorded miss holds, a later
        // hit clears it so fresh data always wins, and misses never shadow hits.
        MatchCache.clear();
        check(!MatchCache.isMiss("Unknown Title"), "no miss recorded initially"); assertions++;
        MatchCache.putMiss("Unknown Title");
        check(MatchCache.isMiss("unknown title  "), "miss hit is normalized"); assertions++;
        check(MatchCache.get("Unknown Title") == null, "miss stores no match"); assertions++;
        MatchCache.put("Unknown Title", movie);
        check(!MatchCache.isMiss("Unknown Title"), "later hit clears the miss"); assertions++;
        check(MatchCache.get("Unknown Title") == movie, "later hit is returned"); assertions++;
        MatchCache.clear();
        MatchCache.put("Landman", movie);
        MatchCache.putMiss("Landman");
        check(MatchCache.get("Landman") == movie, "miss never shadows a hit"); assertions++;
        check(!MatchCache.isMiss("Landman"), "miss over a hit is dropped"); assertions++;
        MatchCache.clear();
        MatchCache.putMiss(null);
        check(!MatchCache.isMiss(""), "blank miss ignored"); assertions++;

        // Launch routing policy: preference-ordered selection, cache keys, schemes.
        check(LaunchPolicy.SMART_TUBE_ORDER.get(0).equals("org.smarttube.stable")
                        && LaunchPolicy.SMART_TUBE_ORDER.get(1).equals("org.smarttube.beta"),
                "SmartTube stable stays before beta"); assertions++;
        check("org.smarttube.stable".equals(LaunchPolicy.selectPreferred(
                        LaunchPolicy.SMART_TUBE_ORDER,
                        Arrays.asList("org.smarttube.beta", "org.smarttube.stable"))),
                "stable wins over beta when both resolve"); assertions++;
        check("org.smarttube.beta".equals(LaunchPolicy.selectPreferred(
                        LaunchPolicy.SMART_TUBE_ORDER,
                        Collections.singletonList("org.smarttube.beta"))),
                "beta selected when stable absent"); assertions++;
        check(LaunchPolicy.selectPreferred(LaunchPolicy.SMART_TUBE_ORDER,
                        Collections.singletonList("com.example.other")) == null,
                "unrelated handler never selected for SmartTube"); assertions++;
        check(LaunchPolicy.selectPreferred(LaunchPolicy.SMART_TUBE_ORDER,
                        Collections.<String>emptyList()) == null,
                "empty resolution selects nothing"); assertions++;
        check(LaunchPolicy.cacheKey("nuvio", "nuvio://movie/tt0371746")
                        .equals(LaunchPolicy.cacheKey("nuvio", "nuvio://movie/tt9999999")),
                "same target and scheme share a cache key"); assertions++;
        check(!LaunchPolicy.cacheKey("nuvio", "nuvio://movie/tt0371746")
                        .equals(LaunchPolicy.cacheKey("stremio", "nuvio://movie/tt0371746")),
                "Nuvio and Stremio cache independently"); assertions++;
        check(!LaunchPolicy.cacheKey("smarttube", "https://www.youtube.com/results?search_query=x")
                        .equals(LaunchPolicy.cacheKey("tizentube", "https://www.youtube.com/results?search_query=x")),
                "SmartTube and TizenTube cache independently"); assertions++;
        check("https".equals(LaunchPolicy.schemeOf("https://www.youtube.com/results?search_query=x")),
                "https scheme extracted"); assertions++;
        check("nuvio".equals(LaunchPolicy.schemeOf("nuvio://movie/tt0371746")),
                "custom scheme extracted"); assertions++;

        // Installed-app-label policy: exact match only, case/space-insensitive.
        java.util.Set<String> labels = AppLabelPolicy.normalizeAll(
                Arrays.asList("YouTube", " Netflix ", "", "  "));
        check(AppLabelPolicy.matches(labels, "youtube"), "label match is case-insensitive"); assertions++;
        check(AppLabelPolicy.matches(labels, "  Netflix  "), "label match trims spaces"); assertions++;
        check(!AppLabelPolicy.matches(labels, "YouTube Kids"), "prefix is not a label match"); assertions++;
        check(!AppLabelPolicy.matches(labels, "Tube"), "substring is not a label match"); assertions++;
        check(!AppLabelPolicy.matches(labels, ""), "blank title never matches"); assertions++;
        check(!AppLabelPolicy.matches(Collections.<String>emptySet(), "YouTube"),
                "empty label set never matches"); assertions++;

        check(RecommendationTitleParser.fromEventText(Collections.singletonList("Season 4 • Thriller")).isEmpty(),
                "metadata-only event rejected"); assertions++;
        // Table-driven fixtures keep representative launcher payloads reproducible and auditable.
        for (GoogleTvPayloadFixtures.Fixture fixture : GoogleTvPayloadFixtures.all()) {
            RecommendationTitleParser.Source parsed = fixture.eventText != null
                    ? RecommendationTitleParser.fromEventTextSource(fixture.eventText)
                    : RecommendationTitleParser.fromDescriptionSource(fixture.description);
            check(fixture.title.equals(parsed.title) && fixture.youtube == parsed.youtube,
                    "payload fixture: " + fixture.name);
            assertions++;
        }

        check(UpdateChecker.compareVersions("v1.2.0", "v1.1.0") > 0, "newer version"); assertions++;
        check(UpdateChecker.compareVersions("1.1.0", "v1.1.0") == 0, "equal version"); assertions++;
        check(UpdateChecker.compareVersions("v1.0.9", "1.1.0") < 0, "older version"); assertions++;
        check(UpdateChecker.compareVersions("release", "1.1.0") == 0, "malformed version safe"); assertions++;
        check(UpdateChecker.compareVersions("v1.2.0-rc1", "v1.1.0") == 0, "prerelease version rejected"); assertions++;
        String stable = "{\"draft\":false,\"prerelease\":false,\"tag_name\":\"v1.2.0\",\"html_url\":\"https://github.com/xantipater/GTV2STREAM/releases/tag/v1.2.0\"}";
        check("v1.2.0".equals(UpdateChecker.parseStableReleaseVersion(stable)), "stable release parsed"); assertions++;
        check(UpdateChecker.parseStableReleaseVersion(stable.replace("false,\"prerelease\":false", "false,\"prerelease\":true")) == null, "prerelease ignored"); assertions++;
        check(UpdateChecker.parseStableReleaseVersion("{\"draft\" : true, \"prerelease\" : false, \"tag_name\" : \"v1.3.0\"}") == null, "draft ignored"); assertions++;
        check(UpdateChecker.parseStableReleaseVersion("{\"draft\" : false, \"prerelease\" : true, \"tag_name\" : \"v1.3.0\"}") == null, "whitespace prerelease ignored"); assertions++;
        check(UpdateChecker.parseStableReleaseVersion("{\"tag_name\":\"next\"}") == null, "unexpected tag rejected"); assertions++;

        // Provider identity feeds the whitelist bypass; titles alone never bypass.
        RecommendationTitleParser.Source primeCard = RecommendationTitleParser.fromDescriptionSource(
                "The Boys. Available on Prime Video");
        check("The Boys".equals(primeCard.title) && "prime video".equals(primeCard.provider),
                "prime action exposes canonical provider"); assertions++;
        RecommendationTitleParser.Source netflixTail = RecommendationTitleParser.fromDescriptionSource(
                "Daredevil — Netflix");
        check("Daredevil".equals(netflixTail.title) && "netflix".equals(netflixTail.provider),
                "dash tail exposes canonical provider"); assertions++;
        RecommendationTitleParser.Source aliasCard = RecommendationTitleParser.fromDescriptionSource(
                "Daredevil. Available on Disney Plus");
        check("Daredevil".equals(aliasCard.title) && "disney+".equals(aliasCard.provider),
                "alias collapses to canonical provider"); assertions++;
        RecommendationTitleParser.Source providerFirstEvent = RecommendationTitleParser.fromEventTextSource(
                Arrays.asList("ITVX", "Trigger Point", "Season 4 • Thriller"));
        check("Trigger Point".equals(providerFirstEvent.title) && "itvx".equals(providerFirstEvent.provider),
                "provider-first event exposes provider"); assertions++;
        RecommendationTitleParser.Source actionEvent = RecommendationTitleParser.fromEventTextSource(
                Arrays.asList("Trigger Point", "Season 4 • Thriller", "Watch on ITVX"));
        check("Trigger Point".equals(actionEvent.title) && "itvx".equals(actionEvent.provider),
                "event action row exposes provider"); assertions++;
        RecommendationTitleParser.Source unknownAction = RecommendationTitleParser.fromDescriptionSource(
                "Dune. Available on Friday");
        check(unknownAction.isEmpty() && unknownAction.provider.isEmpty(),
                "unknown action target stays rejected without provider"); assertions++;
        RecommendationTitleParser.Source plainTitle = RecommendationTitleParser.fromDescriptionSource("Dune");
        check("Dune".equals(plainTitle.title) && plainTitle.provider.isEmpty(),
                "plain title carries no provider"); assertions++;
        RecommendationTitleParser.Source youtubeCard = RecommendationTitleParser.fromDescriptionSource(
                "The Bear. Watch on YouTube");
        check("The Bear".equals(youtubeCard.title) && youtubeCard.youtube
                        && "youtube".equals(youtubeCard.provider),
                "YouTube action exposes youtube provider"); assertions++;

        // Whitelist bypass: whitelisted provider skips the redirect, others redirect.
        Set<String> whitelist = new HashSet<>(Arrays.asList("prime video"));
        check(RecommendationTitleParser.isWhitelisted(primeCard, whitelist),
                "whitelisted Prime Video bypasses"); assertions++;
        check(!RecommendationTitleParser.isWhitelisted(RecommendationTitleParser.fromDescriptionSource(
                "Daredevil. Available on Netflix"), whitelist),
                "non-whitelisted Netflix still redirects"); assertions++;
        check(!RecommendationTitleParser.isWhitelisted(plainTitle, whitelist),
                "title without provider never bypasses"); assertions++;
        check(!RecommendationTitleParser.isWhitelisted(primeCard, new HashSet<>()),
                "empty whitelist preserves v1.1 behaviour"); assertions++;
        check(!RecommendationTitleParser.isWhitelisted(primeCard, null),
                "null whitelist preserves v1.1 behaviour"); assertions++;
        check(!RecommendationTitleParser.isWhitelisted(
                RecommendationTitleParser.fromDescriptionSource(
                        "Sponsored. Daredevil. Available on Disney+"),
                new HashSet<>(Arrays.asList("disney+"))),
                "sponsored payload bypasses nothing"); assertions++;
        // A title mentioning a provider is not a provider card: no bypass.
        check(!RecommendationTitleParser.isWhitelisted(
                RecommendationTitleParser.fromDirectTextSource("Netflix"), whitelist),
                "bare provider label carries no title to bypass"); assertions++;
        check(!ProviderWhitelist.contains(new HashSet<>(Arrays.asList("netflix")), "Netflix Special"),
                "title-like text never matches the whitelist"); assertions++;

        // Whitelist storage: sanitize, toggle, serialize/parse round-trip, display.
        check(ProviderWhitelist.sanitize(new HashSet<>(Arrays.asList("Prime Video", " friday ", null, "")))
                        .equals(new HashSet<>(Arrays.asList("prime video"))),
                "sanitize keeps only canonical identities"); assertions++;
        check(ProviderWhitelist.contains(new HashSet<>(Arrays.asList("disney+")), "Disney Plus"),
                "alias membership matches canonical identity"); assertions++;
        check(ProviderWhitelist.toggled(new HashSet<>(), "netflix")
                        .equals(new HashSet<>(Arrays.asList("netflix"))),
                "toggle adds a provider"); assertions++;
        check(ProviderWhitelist.toggled(new HashSet<>(Arrays.asList("netflix")), "Netflix").isEmpty(),
                "toggle removes a provider"); assertions++;
        check(ProviderWhitelist.toggled(new HashSet<>(), "Friday").isEmpty(),
                "toggle ignores unknown providers"); assertions++;
        Set<String> roundTrip = ProviderWhitelist.parse(
                ProviderWhitelist.serialize(new HashSet<>(Arrays.asList("prime video", "netflix"))));
        check(roundTrip.equals(new HashSet<>(Arrays.asList("prime video", "netflix"))),
                "serialize/parse round-trips the whitelist"); assertions++;
        check(ProviderWhitelist.serialize(new HashSet<>()).isEmpty()
                        && ProviderWhitelist.parse("").isEmpty()
                        && ProviderWhitelist.parse(null).isEmpty(),
                "empty/default whitelist serializes to empty"); assertions++;
        check(ProviderWhitelist.parse("netflix, Friday, ,disney plus")
                        .equals(new HashSet<>(Arrays.asList("netflix", "disney+"))),
                "parse drops unknown tokens on migration"); assertions++;
        check("None".equals(ProviderWhitelist.summary(new HashSet<>()))
                        && "None".equals(ProviderWhitelist.summary(null)),
                "empty whitelist summarizes as None"); assertions++;
        check("Prime Video".equals(ProviderWhitelist.summary(new HashSet<>(Arrays.asList("prime video")))),
                "summary names the whitelisted provider"); assertions++;
        // Whitelisted YouTube is exempt from film/series redirect like any provider.
        check(RecommendationTitleParser.isWhitelisted(youtubeCard,
                        new HashSet<>(Arrays.asList("youtube"))),
                "whitelisted YouTube bypasses"); assertions++;
        check(!RecommendationTitleParser.isWhitelisted(youtubeCard, whitelist),
                "YouTube still redirects when not whitelisted"); assertions++;
        // Live Prime Video "requires ... subscription" card: exact focused
        // launcher description captured on-device.
        RecommendationTitleParser.Source tomorrowWar = RecommendationTitleParser.fromDescriptionSource(
                "The Tomorrow War, requires Prime Video subscription, rotten rating: 51% on Rotten Tomatoes");
        check("The Tomorrow War".equals(tomorrowWar.title)
                        && "prime video".equals(tomorrowWar.provider) && !tomorrowWar.youtube,
                "requires-provider card exposes title and Prime Video"); assertions++;
        check(RecommendationTitleParser.isWhitelisted(tomorrowWar, whitelist),
                "whitelisted requires-provider card bypasses"); assertions++;
        check("The Tomorrow War".equals(RecommendationTitleParser.fromDescription(
                "The Tomorrow War, requires Prime Video subscription")),
                "trailing requires clause stripped to the title"); assertions++;
        RecommendationTitleParser.Source tomorrowWarEvent = RecommendationTitleParser.fromEventTextSource(
                Arrays.asList("The Tomorrow War", "requires Prime Video subscription"));
        check("The Tomorrow War".equals(tomorrowWarEvent.title)
                        && "prime video".equals(tomorrowWarEvent.provider),
                "event requires row exposes Prime Video"); assertions++;
        check(RecommendationTitleParser.isWhitelisted(tomorrowWarEvent, whitelist),
                "whitelisted event requires card bypasses"); assertions++;
        // Negatives: unknown requirements carry no provider (no bypass, prior
        // redirect behaviour otherwise unchanged), bare titles carry none, and
        // bare/sponsored requirement rows stay rejected.
        RecommendationTitleParser.Source unknownRequires = RecommendationTitleParser.fromDescriptionSource(
                "The Tomorrow War, requires Friday subscription, rotten rating: 51% on Rotten Tomatoes");
        check(unknownRequires.provider.isEmpty()
                        && !RecommendationTitleParser.isWhitelisted(unknownRequires, whitelist),
                "unknown requires provider never bypasses"); assertions++;
        RecommendationTitleParser.Source tomorrowTitleOnly = RecommendationTitleParser.fromDescriptionSource(
                "The Tomorrow War");
        check("The Tomorrow War".equals(tomorrowTitleOnly.title)
                        && tomorrowTitleOnly.provider.isEmpty()
                        && !RecommendationTitleParser.isWhitelisted(tomorrowTitleOnly, whitelist),
                "title without provider never bypasses"); assertions++;
        check(RecommendationTitleParser.fromDescription("requires Prime Video subscription").isEmpty(),
                "bare requires row rejected"); assertions++;
        check(RecommendationTitleParser.fromEventText(
                        Collections.singletonList("requires Prime Video subscription")).isEmpty(),
                "bare requires event item rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription(
                "Sponsored. The Tomorrow War, requires Prime Video subscription, rotten rating: 51% on Rotten Tomatoes")
                        .isEmpty(),
                "sponsored requires payload rejected"); assertions++;
        // Comma-delimited provider card observed live (card-region payload):
        // "Shang-Chi and the Legend of the Ten Rings, Disney+, fresh rating:
        // 92% on Rotten Tomatoes" carries the known provider mid-payload.
        RecommendationTitleParser.Source shangChi = RecommendationTitleParser.fromDescriptionSource(
                "Shang-Chi and the Legend of the Ten Rings, Disney+, fresh rating: 92% on Rotten Tomatoes");
        check("Shang-Chi and the Legend of the Ten Rings".equals(shangChi.title)
                        && "disney+".equals(shangChi.provider) && !shangChi.youtube,
                "comma-middle provider card exposes title and Disney+"); assertions++;
        RecommendationTitleParser.Source shangChiAlias = RecommendationTitleParser.fromDescriptionSource(
                "Shang-Chi and the Legend of the Ten Rings, Disney Plus, fresh rating: 92% on Rotten Tomatoes");
        check("Shang-Chi and the Legend of the Ten Rings".equals(shangChiAlias.title)
                        && "disney+".equals(shangChiAlias.provider) && !shangChiAlias.youtube,
                "comma-middle provider alias collapses to disney+"); assertions++;
        check("Shang-Chi and the Legend of the Ten Rings".equals(RecommendationTitleParser.fromDescription(
                "Shang-Chi and the Legend of the Ten Rings, Disney+, fresh rating: 92% on Rotten Tomatoes")),
                "comma-middle provider title extracted"); assertions++;
        check(RecommendationTitleParser.isWhitelisted(shangChi,
                        new HashSet<>(Arrays.asList("disney+"))),
                "whitelisted comma-middle card bypasses"); assertions++;
        check(!RecommendationTitleParser.isWhitelisted(shangChi, whitelist),
                "comma-middle Disney+ still redirects when not whitelisted"); assertions++;
        // Disney+ regression, reported from the TV as "the Disney movie Shang-Chi".
        // The plain card shapes for the same 8-word title were rejected outright:
        // the provider-edge and watch-action paths were capped at 7 words, while
        // only the comma-middle path allowed 10. Card evidence (a recognised
        // provider edge or watch action) now earns the long-title bound, which is
        // what makes these real shapes work. The bare 8-word string below is still
        // rejected, which is the fail-closed contract those paths depended on.
        String shangChiTitle = "Shang-Chi and the Legend of the Ten Rings";
        String[] shangChiShapes = {
                shangChiTitle + ". Watch on Disney+.",
                shangChiTitle + ". Watch Now on Disney+.",
                shangChiTitle + ", Disney+",
                shangChiTitle + " \u2022 Disney+",
                shangChiTitle + ". Available on Disney+",
                "Disney+. " + shangChiTitle + ".",
        };
        for (String shape : shangChiShapes) {
            RecommendationTitleParser.Source card = RecommendationTitleParser.fromDescriptionSource(shape);
            check(shangChiTitle.equals(card.title) && "disney+".equals(card.provider) && !card.youtube,
                    "Disney+ card shape parses: " + shape); assertions++;
        }
        check(RecommendationTitleParser.isWhitelisted(
                        RecommendationTitleParser.fromDescriptionSource(shangChiShapes[0]),
                        new HashSet<>(Arrays.asList("disney+"))),
                "newly parsed Disney+ card shape still honours the whitelist"); assertions++;
        // Not a Disney special case: 8-word Netflix and Prime cards were rejected
        // by the same bound, so prove the fix is provider-agnostic.
        check("The Long Title Here One Two Three Four Five".equals(
                        RecommendationTitleParser.fromDescription(
                                "The Long Title Here One Two Three Four Five. Watch on Netflix.")),
                "8-word provider card parses for a non-Disney provider"); assertions++;
        // The bound still bites past 15 words, prose is still rejected, and a bare
        // long title with no card evidence is still refused.
        check(RecommendationTitleParser.fromDescription(
                        "Red Blue Green Gold Black White Star Moon Sun Sky Sea Land Fire Ice Rock Stone. Watch on Disney+.")
                        .isEmpty(),
                "provider-attributed titles over 15 words stay rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription(
                        "a reluctant hero must save the world from an ancient evil. Watch on Disney+.")
                        .isEmpty(),
                "sentence-case prose with a provider action stays rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription(shangChiTitle).isEmpty(),
                "bare 8-word title with no card evidence stays rejected"); assertions++;
        check(RecommendationTitleParser.fromDirectText(shangChiTitle).isEmpty(),
                "node text keeps the strict 7-word bound"); assertions++;
        // Negatives: unknown middle providers grant nothing, provider words
        // inside the title grant nothing, sponsored stays rejected, and rating
        // metadata alone is never a title.
        RecommendationTitleParser.Source unknownMiddle = RecommendationTitleParser.fromDescriptionSource(
                "Dune, Friday, fresh rating: 92% on Rotten Tomatoes");
        check(unknownMiddle.provider.isEmpty()
                        && !RecommendationTitleParser.isWhitelisted(unknownMiddle, whitelist),
                "unknown comma-middle provider never bypasses"); assertions++;
        RecommendationTitleParser.Source providerWordTitle = RecommendationTitleParser.fromDescriptionSource(
                "The Disney+ Adventure, fresh rating: 90% on Rotten Tomatoes");
        check(providerWordTitle.provider.isEmpty(),
                "provider words inside the title grant no provider"); assertions++;
        check(RecommendationTitleParser.fromDescription(
                "Sponsored. Shang-Chi and the Legend of the Ten Rings, Disney+, fresh rating: 92% on Rotten Tomatoes")
                        .isEmpty(),
                "sponsored comma-middle payload rejected"); assertions++;
        check(RecommendationTitleParser.fromDescription("fresh rating: 92% on Rotten Tomatoes").isEmpty(),
                "rating metadata never a title"); assertions++;
        check(RecommendationTitleParser.fromDescription("Disney+").isEmpty(),
                "bare provider never a title"); assertions++;
        // Whitelist bypass suppresses only the immediate providerless fallback
        // for the same title (live Prime Video double-dispatch: bypass, then
        // the EntityActivity path with provider="" 241ms later). An explicit
        // provider-carrying click still dispatches, e.g. after whitelist
        // settings change.
        long bypassAt = 1000000L;
        check(DispatchPolicy.shouldSuppressProviderlessFallback(
                        "The Sheep Detectives", false, bypassAt,
                        "The Sheep Detectives", false, "", bypassAt + 241, 2000L),
                "immediate providerless fallback suppressed after bypass"); assertions++;
        check(DispatchPolicy.shouldSuppressProviderlessFallback(
                        "The Sheep Detectives", false, bypassAt,
                        "the sheep detectives!", false, "", bypassAt + 241, 2000L),
                "suppression matches across case and punctuation"); assertions++;
        check(!DispatchPolicy.shouldSuppressProviderlessFallback(
                        "The Sheep Detectives", false, bypassAt,
                        "The Sheep Detectives", false, "prime video", bypassAt + 241, 2000L),
                "explicit provider click still dispatches after whitelist change"); assertions++;
        check(!DispatchPolicy.shouldSuppressProviderlessFallback(
                        "The Sheep Detectives", false, bypassAt,
                        "A Different Title", false, "", bypassAt + 241, 2000L),
                "providerless fallback for another title still dispatches"); assertions++;
        check(!DispatchPolicy.shouldSuppressProviderlessFallback(
                        "The Sheep Detectives", false, bypassAt,
                        "The Sheep Detectives", false, "", bypassAt + 2000, 2000L),
                "suppression expires after the duplicate window"); assertions++;
        check(!DispatchPolicy.shouldSuppressProviderlessFallback(
                        "The Sheep Detectives", false, bypassAt,
                        "The Sheep Detectives", true, "", bypassAt + 241, 2000L),
                "youtube mismatch never suppresses"); assertions++;
        check(!DispatchPolicy.shouldSuppressProviderlessFallback(
                        "", false, bypassAt,
                        "The Sheep Detectives", false, "", bypassAt + 241, 2000L),
                "empty bypass record suppresses nothing"); assertions++;
        // Focused-provider bridge: a fresh title-matched non-YouTube focus
        // provider is carried into the EntityActivity detail dispatch;
        // anything else stays providerless.
        long focusAt = 2000000L;
        long focusWindow = 15000L;
        check("prime video".equals(DispatchPolicy.focusedProviderForDetail(
                        "The Tomorrow War", false, "prime video", focusAt,
                        "The Tomorrow War", focusAt + 1000, focusWindow)),
                "exact title match carries the focused provider"); assertions++;
        check("prime video".equals(DispatchPolicy.focusedProviderForDetail(
                        "The Tomorrow War", false, "prime video", focusAt,
                        "the tomorrow war!", focusAt + 1000, focusWindow)),
                "provider carry matches across case and punctuation"); assertions++;
        check(DispatchPolicy.focusedProviderForDetail(
                        "The Tomorrow War", false, "prime video", focusAt,
                        "A Different Title", focusAt + 1000, focusWindow).isEmpty(),
                "title mismatch carries no provider"); assertions++;
        check(DispatchPolicy.focusedProviderForDetail(
                        "The Tomorrow War", false, "prime video", focusAt,
                        "The Tomorrow War", focusAt + focusWindow, focusWindow).isEmpty(),
                "expired focus cache carries no provider"); assertions++;
        check(DispatchPolicy.focusedProviderForDetail(
                        "The Tomorrow War", false, "prime video", focusAt,
                        "The Tomorrow War", focusAt - 1, focusWindow).isEmpty(),
                "future focus timestamp carries no provider"); assertions++;
        check(DispatchPolicy.focusedProviderForDetail(
                        "The Bear", true, "youtube", focusAt,
                        "The Bear", focusAt + 1000, focusWindow).isEmpty(),
                "YouTube focus cache never carries into detail dispatch"); assertions++;
        check(DispatchPolicy.focusedProviderForDetail(
                        "Dune", false, "", focusAt,
                        "Dune", focusAt + 1000, focusWindow).isEmpty(),
                "empty provider carries nothing"); assertions++;
        check(DispatchPolicy.focusedProviderForDetail(
                        "", false, "prime video", focusAt,
                        "The Tomorrow War", focusAt + 1000, focusWindow).isEmpty(),
                "empty focus title carries nothing"); assertions++;
        check(DispatchPolicy.focusedProviderForDetail(
                        "The Tomorrow War", false, "prime video", focusAt,
                        "", focusAt + 1000, focusWindow).isEmpty(),
                "empty detail title carries nothing"); assertions++;
        check(DispatchPolicy.focusedProviderForDetail(
                        "The Tomorrow War", false, "prime video", focusAt,
                        "The Tomorrow War", focusAt + 1000, 0L).isEmpty(),
                "non-positive window carries nothing"); assertions++;
        check(DispatchPolicy.focusedProviderForDetail(
                        null, false, "prime video", focusAt,
                        "The Tomorrow War", focusAt + 1000, focusWindow).isEmpty()
                        && DispatchPolicy.focusedProviderForDetail(
                        "The Tomorrow War", false, null, focusAt,
                        "The Tomorrow War", focusAt + 1000, focusWindow).isEmpty()
                        && DispatchPolicy.focusedProviderForDetail(
                        "The Tomorrow War", false, "prime video", focusAt,
                        null, focusAt + 1000, focusWindow).isEmpty(),
                "null titles/provider carry nothing"); assertions++;
        // Live regression: the exact focused HOME card description captured
        // on-device parses to Prime Video, and that provider is carried into
        // the matching EntityActivity detail title.
        RecommendationTitleParser.Source tomorrowFocus = RecommendationTitleParser.fromDescriptionSource(
                "The Tomorrow War, requires Prime Video subscription, rotten rating: 51% on Rotten Tomatoes");
        check("prime video".equals(DispatchPolicy.focusedProviderForDetail(
                        tomorrowFocus.title, tomorrowFocus.youtube, tomorrowFocus.provider, focusAt,
                        "The Tomorrow War", focusAt + 333, focusWindow)),
                "live Tomorrow War focus carries Prime into detail dispatch"); assertions++;
        check(ProviderWhitelist.contains(whitelist, DispatchPolicy.focusedProviderForDetail(
                                tomorrowFocus.title, tomorrowFocus.youtube,
                                tomorrowFocus.provider, focusAt,
                                "The Tomorrow War", focusAt + 333, focusWindow)),
                "carried Tomorrow War provider bypasses the whitelist"); assertions++;
        check(DispatchPolicy.focusedProviderForDetail(
                        tomorrowFocus.title, tomorrowFocus.youtube, tomorrowFocus.provider, focusAt,
                        "A Different Title", focusAt + 333, focusWindow).isEmpty(),
                "live focus provider never leaks into another title"); assertions++;
        // Authoritative detail-title row: the exact 8-word Shang-Chi title is
        // accepted from the dedicated detail path while the general direct
        // parser stays fail-closed at 7 words.
        check("Shang-Chi and the Legend of the Ten Rings".equals(
                        RecommendationTitleParser.fromDetailTitle(
                                "Shang-Chi and the Legend of the Ten Rings")),
                "detail path accepts the exact Shang-Chi title"); assertions++;
        check("Shang-Chi and the Legend of the Ten Rings".equals(
                        RecommendationTitleParser.fromDetailTitleSource(
                                "Shang-Chi and the Legend of the Ten Rings").title),
                "detail source accepts the exact Shang-Chi title"); assertions++;
        check(RecommendationTitleParser.fromDirectText(
                        "Shang-Chi and the Legend of the Ten Rings").isEmpty(),
                "general direct parser still rejects the 8-word title"); assertions++;
        check(RecommendationTitleParser.fromDescription(
                        "Shang-Chi and the Legend of the Ten Rings").isEmpty(),
                "general description parser still rejects the bare 8-word title"); assertions++;
        // Detail-path guards: UI chrome, sponsored/ad text, and prose stay
        // rejected even within the larger word cap.
        check(RecommendationTitleParser.fromDetailTitle("Home").isEmpty(),
                "detail path rejects UI chrome"); assertions++;
        check(RecommendationTitleParser.fromDetailTitle("Display").isEmpty(),
                "detail path rejects settings chrome"); assertions++;
        check(RecommendationTitleParser.fromDetailTitle(
                        "Sponsored. Shang-Chi and the Legend of the Ten Rings").isEmpty(),
                "detail path rejects sponsored text"); assertions++;
        check(RecommendationTitleParser.fromDetailTitle(
                        "Sponsored, Install app, Learn more").isEmpty(),
                "detail path rejects ad text"); assertions++;
        check(RecommendationTitleParser.fromDetailTitle(
                        "A detective investigates a conspiracy across the city tonight").isEmpty(),
                "detail path rejects prose within the word cap"); assertions++;
        check(RecommendationTitleParser.fromDetailTitle(
                        "Red Blue Green Gold Black White Star Moon Sun Sky Sea Land Fire Ice Rock Stone")
                        .isEmpty(),
                "detail path rejects titles over 15 words"); assertions++;
        check(UpdateChecker.parseStableReleaseVersion("{\"draft\" : true, \"prerelease\" : false, \"tag_name\" : \"v1.3.0\"}") == null, "draft ignored"); assertions++;
        check(UpdateChecker.parseStableReleaseVersion("{\"draft\" : false, \"prerelease\" : true, \"tag_name\" : \"v1.3.0\"}") == null, "whitespace prerelease ignored"); assertions++;
        check(UpdateChecker.parseStableReleaseVersion("{\"tag_name\":\"next\"}") == null, "unexpected tag rejected"); assertions++;
        String apkRelease = "{\"draft\":false,\"prerelease\":false,\"tag_name\":\"v1.2.0\","
                + "\"html_url\":\"https://github.com/xantipater/GTV2STREAM/releases/tag/v1.2.0\","
                + "\"assets\":[{\"name\":\"checksums.txt\",\"browser_download_url\":"
                + "\"https://github.com/xantipater/GTV2STREAM/releases/download/v1.2.0/checksums.txt\",\"size\":64},"
                + "{\"name\":\"app-release.apk\",\"browser_download_url\":"
                + "\"https://github.com/xantipater/GTV2STREAM/releases/download/v1.2.0/app-release.apk\",\"size\":12345}]}";
        check("https://github.com/xantipater/GTV2STREAM/releases/download/v1.2.0/app-release.apk"
                .equals(UpdateChecker.parseApkDownloadUrl(apkRelease)), "release APK URL resolved"); assertions++;
        check(UpdateChecker.parseApkDownloadSize(apkRelease,
                "https://github.com/xantipater/GTV2STREAM/releases/download/v1.2.0/app-release.apk") == 12345L,
                "release APK size resolved"); assertions++;
        check(UpdateChecker.parseApkDownloadUrl("{\"draft\":false,\"prerelease\":false,\"tag_name\":\"v1.2.0\","
                + "\"html_url\":\"https://github.com/xantipater/GTV2STREAM/releases/tag/v1.2.0\","
                + "\"assets\":[{\"name\":\"notes.txt\",\"browser_download_url\":"
                + "\"https://github.com/xantipater/GTV2STREAM/releases/download/v1.2.0/notes.txt\",\"size\":9}]}") == null,
                "release without APK resolves no download"); assertions++;
        check(UpdateChecker.parseApkDownloadUrl("{\"assets\":[{\"name\":\"app-release.apk\","
                + "\"browser_download_url\":\"https://evil.example.com/app-release.apk\"}]}") == null,
                "off-origin APK URL rejected"); assertions++;
        check(UpdateChecker.parseApkDownloadUrl("{\"assets\":[{\"name\":\"app-release.apk\","
                + "\"browser_download_url\":\"http://github.com/x/app-release.apk\"}]}") == null,
                "non-https APK URL rejected"); assertions++;
        check(UpdateChecker.parseApkDownloadUrl("{\"assets\":[{\"name\":\"app-release.zip\","
                + "\"browser_download_url\":\"https://github.com/x/app-release.zip\"}]}") == null,
                "non-APK asset rejected"); assertions++;
        check(UpdateChecker.parseApkDownloadUrl(null) == null, "null release JSON has no APK"); assertions++;
        check(UpdateChecker.parseApkDownloadSize(apkRelease, null) == -1L, "null APK URL has no size"); assertions++;
        check(UpdateChecker.parseApkDownloadSize(apkRelease, "https://github.com/other.apk") == -1L,
                "unknown APK URL has no size"); assertions++;
        check(ApkUpdater.isAllowedDownloadUrl(
                "https://github.com/xantipater/GTV2STREAM/releases/download/v1.2.0/app-release.apk"),
                "release download URL allowed"); assertions++;
        check(ApkUpdater.isAllowedDownloadUrl(
                "https://objects.githubusercontent.com/u/123/app-release.apk"),
                "release redirect host allowed"); assertions++;
        check(!ApkUpdater.isAllowedDownloadUrl("http://github.com/x/app-release.apk"),
                "plain-http download rejected"); assertions++;
        check(!ApkUpdater.isAllowedDownloadUrl("https://evil.example.com/app-release.apk"),
                "foreign download host rejected"); assertions++;
        check(!ApkUpdater.isAllowedDownloadUrl("https://github.com.evil.example.com/app-release.apk"),
                "lookalike download host rejected"); assertions++;
        check(!ApkUpdater.isAllowedDownloadUrl(null), "null download URL rejected"); assertions++;
        check(UpdateChecker.isFailureRetryable(UpdateChecker.Failure.OFFLINE), "offline is retryable"); assertions++;
        check(UpdateChecker.isFailureRetryable(UpdateChecker.Failure.NETWORK), "network error is retryable"); assertions++;
        check(UpdateChecker.isFailureRetryable(UpdateChecker.Failure.TIMEOUT), "timeout is retryable"); assertions++;
        check(!UpdateChecker.isFailureRetryable(UpdateChecker.Failure.CORRUPT), "corrupt is not retryable"); assertions++;
        check(!UpdateChecker.isFailureRetryable(UpdateChecker.Failure.NO_APK), "no-APK is not retryable"); assertions++;
        check(!UpdateChecker.isFailureRetryable(UpdateChecker.Failure.UNKNOWN_SOURCES), "unknown-sources is not retryable"); assertions++;
        check(!UpdateChecker.isFailureRetryable(UpdateChecker.Failure.INSTALL_CANCELLED), "install cancel is not retryable"); assertions++;
        check(!UpdateChecker.isFailureRetryable(UpdateChecker.Failure.INSTALL_FAILED), "install failure is not retryable"); assertions++;

        // Stock-YouTube divert policy. The launcher starts stock YouTube itself
        // with an explicit intent we can never intercept, so this divert is the
        // only guaranteed redirect. The clicked card must beat the polled ambient
        // panel, and having nothing cached must stay an explicit miss rather than
        // a guess at some unrelated video.
        long now = 1_000_000L;
        check(DivertPolicy.CLICKED_CARD_TTL_MS < DivertPolicy.HERO_PANEL_TTL_MS,
                "clicked-card window is the tighter of the two"); assertions++;
        check(DivertPolicy.choose(now - 100L, true, now - 100L, true, now)
                == DivertPolicy.Choice.CLICKED_CARD, "clicked card beats the panel"); assertions++;
        check(DivertPolicy.choose(now - 5000L, true, now - 1L, true, now)
                == DivertPolicy.Choice.CLICKED_CARD,
                "clicked card wins even when the panel is fresher"); assertions++;
        check(DivertPolicy.choose(now - (DivertPolicy.CLICKED_CARD_TTL_MS + 1L), true,
                now - 100L, true, now) == DivertPolicy.Choice.HERO_PANEL,
                "stale clicked card falls back to the panel"); assertions++;
        check(DivertPolicy.choose(0L, false, now - 100L, true, now)
                == DivertPolicy.Choice.HERO_PANEL,
                "panel covers a click whose own payload was empty"); assertions++;
        check(DivertPolicy.choose(0L, true, now - 100L, true, now)
                == DivertPolicy.Choice.HERO_PANEL,
                "a never-captured clicked card is not usable"); assertions++;
        check(DivertPolicy.choose(now - 100L, true, 0L, false, now)
                == DivertPolicy.Choice.CLICKED_CARD,
                "clicked card alone is enough"); assertions++;
        check(DivertPolicy.choose(now - 100L, false, now - 100L, false, now)
                == DivertPolicy.Choice.NONE, "no usable title diverts nowhere"); assertions++;
        check(DivertPolicy.choose(now - (DivertPolicy.HERO_PANEL_TTL_MS + 1L), true,
                now - (DivertPolicy.HERO_PANEL_TTL_MS + 1L), true, now)
                == DivertPolicy.Choice.NONE, "both caches stale means an explicit miss"); assertions++;
        check(!DivertPolicy.isFresh(now - DivertPolicy.CLICKED_CARD_TTL_MS, now,
                        DivertPolicy.CLICKED_CARD_TTL_MS),
                "window boundary is exclusive: age == ttl is stale"); assertions++;
        check(DivertPolicy.isFresh(now - (DivertPolicy.CLICKED_CARD_TTL_MS - 1L), now,
                        DivertPolicy.CLICKED_CARD_TTL_MS),
                "just inside the window is fresh"); assertions++;
        check(!DivertPolicy.isFresh(now + 5000L, now, DivertPolicy.HERO_PANEL_TTL_MS),
                "a future timestamp is rejected, never treated as freshest"); assertions++;
        // Live YouTube recommendation card shape, captured verbatim from the TCL
        // launcher. This payload used to be rejected outright, which is why a
        // YouTube redirect only worked when the ambient panel happened to hold a
        // separately parseable title, and why it then searched the panel's video.
        String liveYoutubeCard = "Gemini 3.8 Flash Is HERE \u2013 Testing Google\u2019s "
                + "BEST Model Yet!, YouTube \u2022 Bijan Bowen";
        RecommendationTitleParser.Source card =
                RecommendationTitleParser.youtubeCardSource(liveYoutubeCard);
        check(!card.isEmpty(), "live YouTube card payload parses"); assertions++;
        check("Gemini 3.8 Flash Is HERE \u2013 Testing Google\u2019s BEST Model Yet!"
                        .equals(card.title),
                "YouTube card title is the segment before the marker"); assertions++;
        check(card.youtube, "YouTube card is classified as YouTube"); assertions++;
        check("youtube".equals(card.provider),
                "YouTube card carries the youtube provider identity"); assertions++;
        check(RecommendationTitleParser.fromDescriptionSource(liveYoutubeCard).title
                        .equals(card.title),
                "description parsing reaches the YouTube card path"); assertions++;
        check(RecommendationTitleParser.youtubeCardSource("Gemini Flash, YouTube \u2022").isEmpty(),
                "marker with no channel is rejected"); assertions++;
        check(RecommendationTitleParser.youtubeCardSource("YouTube").isEmpty(),
                "the bare YouTube app tile is not a card"); assertions++;
        check(RecommendationTitleParser.youtubeCardSource(
                        "Sponsored: Gemini Flash, YouTube \u2022 Bijan Bowen").isEmpty(),
                "a sponsored YouTube card still fails closed"); assertions++;
        check(RecommendationTitleParser.youtubeCardSource(
                        "One Two Three Four Five Six Seven Eight Nine Ten Eleven Twelve "
                                + "Thirteen Fourteen Fifteen Sixteen, YouTube \u2022 Bijan Bowen")
                        .isEmpty(),
                "an oversized YouTube title is still bounded"); assertions++;
        check(!RecommendationTitleParser.fromDescriptionSource("Dune, Prime Video").youtube,
                "a movie card is never classified as YouTube"); assertions++;
        check(!RecommendationTitleParser.isCredibleTitle(
                        "One Two Three Four Five Six Seven Eight"),
                "the 7-word general bound is unchanged"); assertions++;
        // The ambient panel scan reads every text node in every window, so
        // launcher chrome used to arrive as a "title". A live capture showed a
        // redirect searching YouTube for "Column 3"; none of this may pass.
        check(RecommendationTitleParser.youtubeSource("Column 3").isEmpty(),
                "grid label Column 3 is rejected as a panel title"); assertions++;
        check(RecommendationTitleParser.youtubeSource("Row 12").isEmpty(),
                "grid label Row 12 is rejected as a panel title"); assertions++;
        check(RecommendationTitleParser.youtubeSource("YouTube").isEmpty(),
                "the YouTube app tile name is rejected as a panel title"); assertions++;
        check(RecommendationTitleParser.youtubeSource("Netflix").isEmpty(),
                "a provider name is rejected as a panel title"); assertions++;
        check(RecommendationTitleParser.youtubeSource("Home").isEmpty(),
                "UI chrome is rejected as a panel title"); assertions++;
        check(RecommendationTitleParser.youtubeSource("youtube \u2022 2 weeks ago").isEmpty(),
                "channel metadata is rejected as a panel title"); assertions++;
        check(RecommendationTitleParser.youtubeSource("this is a long lowercase synopsis sentence "
                        + "that reads like accessibility prose and never a title").isEmpty(),
                "all-lowercase prose is rejected as a panel title"); assertions++;
        check(!RecommendationTitleParser.youtubeSource(
                        "DeepSeek V4.1 Flash Is INSANE \u2013 Is THIS the Best Open Model Yet?")
                        .isEmpty(),
                "a real panel video title still parses"); assertions++;
        check(RecommendationTitleParser.fromDescriptionSource("Column 3").isEmpty(),
                "chrome alone never reaches a dispatch"); assertions++;

        // WuPlay as a film/series destination. wuplay://movie/<imdb> and
        // wuplay://series/<imdb>, both verified live against WuPlay 0.9.0-beta on
        // the test TV (each resolves to app.wuplay.androidtv/.MainActivity), and
        // dated independently by TVRelay's notes as added in v0.8.3-beta.
        check("wuplay://movie/tt0371746".equals(TitleResultHelper.wuplayUri(movie)),
                "WuPlay movie URI"); assertions++;
        check("wuplay://series/tt1234567".equals(TitleResultHelper.wuplayUri(show)),
                "WuPlay series URI"); assertions++;
        check(TitleResultHelper.wuplayUri(new TitleMatch("x", "", "movie", 1, "bad")) == null,
                "WuPlay invalid IMDb rejected"); assertions++;
        check(TitleResultHelper.wuplayUri(null) == null, "WuPlay null match rejected"); assertions++;
        check(TitleResultHelper.wuplayUri(new TitleMatch("x", "", "person", 1, "tt1234567")) == null,
                "WuPlay non-movie/tv type rejected"); assertions++;
        check(AppPrefs.MOVIES_WUPLAY.equals(LaunchPolicy.moviesTarget(AppPrefs.MOVIES_WUPLAY)),
                "wuplay target preserved"); assertions++;
        check(AppPrefs.MOVIES_NUVIO.equals(LaunchPolicy.moviesTarget("jellyfin")),
                "an unknown movie target still defaults to nuvio"); assertions++;
        check(LaunchPolicy.MOVIES_TARGET_ORDER.size() == 3,
                "the picker offers three destinations"); assertions++;
        check(AppPrefs.MOVIES_STREMIO.equals(LaunchPolicy.nextMoviesTarget(AppPrefs.MOVIES_NUVIO))
                        && AppPrefs.MOVIES_WUPLAY.equals(
                                LaunchPolicy.nextMoviesTarget(AppPrefs.MOVIES_STREMIO))
                        && AppPrefs.MOVIES_NUVIO.equals(
                                LaunchPolicy.nextMoviesTarget(AppPrefs.MOVIES_WUPLAY)),
                "picker cycles nuvio, stremio, wuplay"); assertions++;
        check(AppPrefs.MOVIES_STREMIO.equals(LaunchPolicy.nextMoviesTarget("nonsense")),
                "an unknown stored target restarts the cycle"); assertions++;
        // Update awareness: the version comparison must never be defeatable by a
        // build suffix, or a release like "1.2.0-beta" silently prompts nobody.
        check(UpdateChecker.compareVersions("v1.2.0", "1.2.0") == 0,
                "equal versions compare equal"); assertions++;
        check(UpdateChecker.compareVersions("1.2.0", "1.2.0-testbuild") == 0,
                "a build suffix does not make a version look newer"); assertions++;
        check(UpdateChecker.compareVersions("v1.2.1", "1.2.0-testbuild") > 0,
                "a newer release is detected against a suffixed build"); assertions++;
        check(UpdateChecker.compareVersions("v1.1.0", "1.2.0-testbuild") < 0,
                "an older release is not offered to a suffixed build"); assertions++;
        check(UpdateChecker.compareVersions("1.2.0", "1.2.0-beta") == 0,
                "suffixes compare on the numeric core only"); assertions++;
        check(UpdateChecker.compareVersions("garbage", "1.2.0") == 0,
                "an unparseable version still compares safely"); assertions++;
        System.out.println("DeepLinkHelperTest: PASS (" + assertions + " assertions)");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}

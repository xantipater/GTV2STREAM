package com.gtv2stream;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.graphics.Rect;
import android.os.Looper;
import android.os.Parcel;
import android.widget.Button;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Real Android framework tests. Synthetic launcher events enter the actual
 * service; only network responses and external target launches are substituted.
 * These do not claim to reproduce proprietary Google TV payloads or target apps.
 */
@RunWith(AndroidJUnit4.class)
public class StabilisationRuntimeTest {
    private static final String HOME = "com.google.android.apps.tv.launcherx";
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private Instrumentation instrumentation;
    private Context context;
    private TestService service;
    private boolean destroyed;

    @Before public void setUp() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        prefs().edit().clear().putString(AppPrefs.TMDB_KEY, "synthetic-test-key-not-a-credential")
                .putString(AppPrefs.TARGET_MOVIES, AppPrefs.MOVIES_NUVIO)
                .putString(AppPrefs.TARGET_YOUTUBE, AppPrefs.YOUTUBE_SMARTTUBE).commit();
        MatchCache.clear();
        main(() -> service = new TestService(context));
        field(service, "installedAppLabels", AppLabelPolicy.normalizeAll(
                Arrays.asList("MiX Xplorer", "YouTube", "Netflix")));
    }

    @After public void tearDown() throws Exception {
        service.release.countDown();
        if (!destroyed) main(service::onDestroy);
        ApkUpdater.cancelActive();
        field(ApkUpdater.class, "pendingListener", null);
        UpdateInstallState.clear(context);
        instrumentation.waitForIdleSync();
    }

    @Test public void appAndEditActionsNeverReuseFocusedVideo() throws Exception {
        for (String control : Arrays.asList("MiX Xplorer", "Move", "Rearrange Apps", "Settings")) {
            focus("Big Buck Bunny", "Watch on YouTube");
            click(control);
            stockYoutube();
            drain();
            assertTrue(control, service.launched.isEmpty());
            click("Done");
        }
        click("Big Buck Bunny", "Watch on YouTube");
        drain();
        assertEquals(Collections.singletonList("youtube:Big Buck Bunny"), service.launched);
    }

    @Test public void sponsoredSecondaryPayloadIsTerminalNotMissing() throws Exception {
        focus("Big Buck Bunny", "Watch on YouTube");
        click("Dune", "Sponsored", "Watch on Netflix");
        stockYoutube();
        drain();
        assertTrue(service.launched.isEmpty());
        assertTrue(service.lookedUp.isEmpty());
    }

    @Test public void mereFocusDoesNotAuthoriseManualYoutubeOpen() throws Exception {
        focus("Big Buck Bunny", "Watch on YouTube");
        stockYoutube();
        drain();
        assertTrue(service.launched.isEmpty());
    }

    @Test public void titleOnlyFocusCannotReplayPreviousYoutubeCard() throws Exception {
        focus("Big Buck Bunny", "Watch on YouTube");
        focus("Alien");
        click("Column 3"); stockYoutube(); drain();
        assertTrue(service.launched.isEmpty());
        assertTrue(service.lookedUp.isEmpty());
    }

    @Test public void matchingTitleOnlyFocusKeepsUsableYoutubeCard() throws Exception {
        focus("Big Buck Bunny", "Watch on YouTube");
        focus("Big Buck Bunny");
        click("Column 3"); drain();
        assertEquals(Collections.singletonList("youtube:Big Buck Bunny"), service.launched);
    }

    @Test public void titleOnlyFocusRejectsStaleAmbientPanelOnImmediateClick() throws Exception {
        main(() -> service.windowPayload = RecommendationTitleParser.youtubeSource("Big Buck Bunny"));
        focus("Big Buck Bunny", "Watch on YouTube");
        focus("Alien");
        click("Column 3"); stockYoutube(); drain();
        assertTrue(service.launched.isEmpty());
        assertTrue(service.lookedUp.isEmpty());
    }

    @Test public void nodeFocusRejectsStaleAmbientPanelOnImmediateClick() throws Exception {
        main(() -> service.windowPayload = RecommendationTitleParser.youtubeSource("Big Buck Bunny"));
        focus("Big Buck Bunny", "Watch on YouTube");
        nodeEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED, "Alien", "", "Alien");
        click("Column 3"); stockYoutube(); drain();
        assertTrue(service.launched.isEmpty());
    }

    @Test public void ambientPanelStillWorksForMatchingFocusOrNoTitleEvidence() throws Exception {
        main(() -> service.windowPayload = RecommendationTitleParser.youtubeSource("Big Buck Bunny"));
        focus("Big Buck Bunny");
        click("Column 3"); drain();
        assertEquals(Collections.singletonList("youtube:Big Buck Bunny"), service.launched);
        window(HOME, "com.example.home.HomeActivity");
        focus("Column 3");
        click("Column 3"); drain();
        assertEquals(2, service.launched.size());
    }

    @Test public void conflictingFocusNodeCannotRestorePreviousYoutubeCard() throws Exception {
        focus("Big Buck Bunny", "Watch on YouTube");
        nodeEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED, "Alien",
                "Big Buck Bunny. Watch on YouTube", "Alien");
        click("Column 3"); stockYoutube(); drain();
        assertTrue(service.launched.isEmpty());
        click("Big Buck Bunny", "Watch on YouTube"); drain();
        assertEquals(Collections.singletonList("youtube:Big Buck Bunny"), service.launched);
    }

    @Test public void compatibleClickedNodeProviderHonoursWhitelist() throws Exception {
        whitelist("netflix");
        nodeEvent(AccessibilityEvent.TYPE_VIEW_CLICKED, "Alien", "Alien. Watch on Netflix", "Alien");
        drain();
        assertTrue(service.lookedUp.isEmpty());
        assertTrue(service.launched.isEmpty());
        whitelist("");
        nodeEvent(AccessibilityEvent.TYPE_VIEW_CLICKED, "Alien", "Alien. Watch on Netflix", "Alien");
        drain();
        assertEquals(Collections.singletonList("movie:Alien"), service.launched);
    }

    @Test public void compatibleClickedNodeYoutubeProviderChoosesYoutubeRoute() throws Exception {
        nodeEvent(AccessibilityEvent.TYPE_VIEW_CLICKED, "Big Buck Bunny",
                "Big Buck Bunny. Watch on YouTube", "Big Buck Bunny");
        drain();
        assertEquals(Collections.singletonList("youtube:Big Buck Bunny"), service.launched);
        assertTrue(service.lookedUp.isEmpty());
    }

    @Test public void providerBearingClickedNodeTextHonoursWhitelist() throws Exception {
        whitelist("netflix");
        nodeEvent(AccessibilityEvent.TYPE_VIEW_CLICKED, "Alien. Watch on Netflix", "", "Alien");
        drain();
        assertTrue(service.lookedUp.isEmpty());
        assertTrue(service.launched.isEmpty());
    }

    @Test public void clickedNodeEnrichmentPreservesExplicitYear() throws Exception {
        nodeEvent(AccessibilityEvent.TYPE_VIEW_CLICKED, "Dune", "Dune. Watch on Netflix", "Dune (1984)");
        drain();
        assertEquals(Collections.singletonList("Dune (1984)"), service.lookedUp);
        assertEquals(Collections.singletonList("1984"), service.launchedYears);
    }

    @Test public void conflictingClickedNodeEvidenceIsTerminal() throws Exception {
        focus("Big Buck Bunny", "Watch on YouTube");
        nodeEvent(AccessibilityEvent.TYPE_VIEW_CLICKED, "Alien", "Dune. Watch on Netflix", "Alien");
        stockYoutube(); drain();
        assertTrue(service.launched.isEmpty());
        assertTrue(service.lookedUp.isEmpty());
        nodeEvent(AccessibilityEvent.TYPE_VIEW_CLICKED, "Dune", "Dune. Watch on Prime Video",
                "Dune", "Watch on Netflix");
        drain();
        assertTrue(service.launched.isEmpty());
    }

    @Test public void entityPlaybackActionKeepsConsumedWhitelistAndYear() throws Exception {
        whitelist("prime video");
        focus("Dune (1984)", "Watch on Prime Video");
        main(() -> service.detailTitle = "Dune");
        window(HOME, "com.example.entity.EntityActivity");
        click("Watch now"); drain();
        assertTrue(service.lookedUp.isEmpty());
        assertTrue(service.launched.isEmpty());
        whitelist("");
        click("Play"); drain();
        assertEquals(Collections.singletonList("Dune (1984)"), service.lookedUp);
        assertEquals(Collections.singletonList("1984"), service.launchedYears);
    }

    @Test public void playbackNodeTitleStillRetainsAuthoritativeEntityPolicy() throws Exception {
        whitelist("prime video");
        focus("Dune (1984)", "Watch on Prime Video");
        main(() -> service.detailTitle = "Dune");
        window(HOME, "com.example.entity.EntityActivity");
        nodeEvent(AccessibilityEvent.TYPE_VIEW_CLICKED, "Play", "Dune", "Play");
        drain();
        assertTrue(service.lookedUp.isEmpty());
        assertTrue(service.launched.isEmpty());
    }

    @Test public void detailActionDoesNotInheritPolicyForDifferentTitleOrYear() throws Exception {
        whitelist("prime video");
        focus("Dune (1984)", "Watch on Prime Video");
        main(() -> service.detailTitle = "Dune");
        window(HOME, "com.example.entity.EntityActivity");
        main(() -> service.detailTitle = "Dune (2021)");
        click("Play"); drain();
        assertEquals(Collections.singletonList("Dune (2021)"), service.lookedUp);
        click("Alien", "Watch on Prime Video");
        main(() -> service.detailTitle = "Jaws");
        click("Watch now"); drain();
        assertEquals(Arrays.asList("Dune (2021)", "Jaws"), service.lookedUp);
    }

    @Test public void newCardCannotInheritPriorDetailActionPolicy() throws Exception {
        whitelist("prime video");
        focus("Dune (1984)", "Watch on Prime Video");
        main(() -> service.detailTitle = "Dune");
        window(HOME, "com.example.entity.EntityActivity");
        click("Dune"); drain();
        assertEquals(Collections.singletonList("Dune"), service.lookedUp);
        assertEquals(Collections.singletonList(""), service.launchedYears);
    }

    @Test public void whitelistedYoutubeNeverLaunchesIncludingRepeatedWindows() throws Exception {
        whitelist("youtube");
        focus("Big Buck Bunny", "Watch on YouTube");
        click("Big Buck Bunny", "Watch on YouTube");
        stockYoutube();
        stockYoutube();
        drain();
        assertTrue(service.launched.isEmpty());
        assertEquals(0, service.badges);
    }

    @Test public void onlySuccessfulYoutubeLaunchArmsOneReassert() throws Exception {
        click("Big Buck Bunny", "Watch on YouTube");
        drain();
        assertEquals(1, service.launched.size());
        stockYoutube(); drain();
        stockYoutube(); drain();
        stockYoutube(); drain();
        assertEquals(2, service.launched.size());
        assertEquals(2, service.badges);
    }

    @Test public void failedYoutubeLaunchDoesNotArmReassert() throws Exception {
        service.youtubeSuccess = false;
        click("Big Buck Bunny", "Watch on YouTube"); drain();
        stockYoutube(); stockYoutube(); drain();
        assertEquals(1, service.launched.size());
        assertEquals(0, service.badges);
        assertEquals(0, ((Integer) field(service, "divertReassertsLeft")).intValue());
    }

    @Test public void whitelistAndTargetChangesInvalidateReassert() throws Exception {
        click("Big Buck Bunny", "Watch on YouTube"); drain();
        whitelist("youtube");
        stockYoutube(); drain();
        assertEquals(1, service.launched.size());
        whitelist("");
        click("Big Buck Bunny", "Watch on YouTube"); drain();
        prefs().edit().putString(AppPrefs.TARGET_YOUTUBE, AppPrefs.YOUTUBE_TIZENTUBE).commit();
        stockYoutube(); drain();
        assertEquals(2, service.launched.size());
    }

    @Test public void delayedProviderlessDetailKeepsSelectedWhitelistPolicy() throws Exception {
        whitelist("prime video");
        click("Dune", "Watch on Prime Video");
        // Simulate a repeated detail callback after the old duplicate window.
        // Run the actual fallback dispatch and worker; no policy copy is tested.
        main(() -> {
            field(service, "lastBypassedAt", android.os.SystemClock.elapsedRealtime() - 2001L);
            invoke(service, "dispatchTitle", new Class<?>[] {String.class, boolean.class, String.class},
                    "Dune", false, "");
        });
        drain();
        assertTrue(service.lookedUp.isEmpty());
        assertTrue(service.launched.isEmpty());
        // An explicit selection carries its own provider, even for the same title.
        click("Dune", "Watch on Netflix"); drain();
        assertEquals(Collections.singletonList("movie:Dune"), service.launched);
    }

    @Test public void providerlessDetailStillRechecksPolicyDuringLookup() throws Exception {
        blockFirst();
        click("Alien", "Watch on Netflix"); awaitLookup();
        main(() -> {
            field(service, "lastDispatchedAt", android.os.SystemClock.elapsedRealtime() - 2001L);
            invoke(service, "dispatchTitle", new Class<?>[] {String.class, boolean.class, String.class},
                    "Alien", false, "");
        });
        whitelist("netflix");
        service.release.countDown(); drain();
        assertTrue(service.launched.isEmpty());
    }

    @Test public void lateWhitelistedProviderCancelsProviderlessLookup() throws Exception {
        whitelist("netflix");
        blockFirst();
        click("Alien"); awaitLookup();
        detail("Alien", "netflix");
        service.release.countDown(); drain();
        assertTrue(service.launched.isEmpty());
        assertTrue(service.messages.isEmpty());
        assertNull(MatchCache.get("Alien"));
        assertFalse(MatchCache.isMiss("Alien"));
        // Retained policy still applies to a later providerless callback.
        main(() -> field(service, "lastBypassedAt", android.os.SystemClock.elapsedRealtime() - 2001L));
        detail("Alien", ""); drain();
        assertEquals(Collections.singletonList("Alien"), service.lookedUp);
        // A genuine new selection must remain usable.
        click("Alien", "Watch on Prime Video"); drain();
        assertEquals(Collections.singletonList("movie:Alien"), service.launched);
    }

    @Test public void lateWhitelistEvidenceCancelsAlreadyPostedLaunch() throws Exception {
        whitelist("netflix");
        blockFirst();
        click("Alien"); awaitLookup();
        main(() -> {
            // Hold the main thread until the worker has posted its launch. The
            // detail callback then precedes that queued launch deterministically.
            service.release.countDown();
            awaitWorker();
            dispatchDetail("Alien", "netflix");
        });
        drain();
        assertTrue(service.launched.isEmpty());
        assertEquals(0, service.badges);
    }

    @Test public void lateProviderEvidenceUsesWhitelistAtCompletion() throws Exception {
        blockFirst();
        click("Alien"); awaitLookup();
        detail("Alien", "netflix");
        whitelist("netflix");
        service.release.countDown(); drain();
        assertTrue(service.launched.isEmpty());
        assertTrue(service.messages.isEmpty());
    }

    @Test public void lateAllowedProviderStillLaunchesCurrentSelection() throws Exception {
        blockFirst();
        click("Alien"); awaitLookup();
        detail("Alien", "netflix");
        service.release.countDown(); drain();
        assertEquals(Collections.singletonList("movie:Alien"), service.launched);
        assertEquals(1, service.badges);
        assertTrue(service.messages.isEmpty());
        assertNotNull(MatchCache.get("Alien"));
    }

    @Test public void lateWhitelistedProviderSuppressesOldLookupFailure() throws Exception {
        whitelist("netflix");
        service.firstFailure = new SocketTimeoutException("synthetic timeout");
        blockFirst();
        click("Alien"); awaitLookup();
        detail("Alien", "netflix");
        service.release.countDown(); drain();
        assertTrue(service.launched.isEmpty());
        assertTrue(service.messages.isEmpty());
        assertFalse(MatchCache.isMiss("Alien"));
    }

    @Test public void failedYoutubeLaunchCannotReplayAfterDuplicateWindow() throws Exception {
        service.youtubeSuccess = false;
        click("Big Buck Bunny", "Watch on YouTube"); drain();
        main(() -> field(service, "lastDispatchedAt", android.os.SystemClock.elapsedRealtime() - 2001L));
        stockYoutube(); drain();
        assertEquals(Collections.singletonList("youtube:Big Buck Bunny"), service.launched);
        // A later intentional click is still allowed to retry the selected target.
        service.youtubeSuccess = true;
        click("Big Buck Bunny", "Watch on YouTube"); drain();
        assertEquals(2, service.launched.size());
        assertEquals(1, service.badges);
    }

    @Test public void selectedProviderDoesNotCrossTitleOrExplicitYear() throws Exception {
        whitelist("prime video");
        click("Dune (1984)", "Watch on Prime Video");
        main(() -> invoke(service, "dispatchTitle",
                new Class<?>[] {String.class, boolean.class, String.class}, "Dune", false, ""));
        drain();
        assertTrue(service.lookedUp.isEmpty());
        main(() -> invoke(service, "dispatchTitle",
                new Class<?>[] {String.class, boolean.class, String.class}, "Dune (2021)", false, ""));
        drain();
        assertEquals(Collections.singletonList("Dune (2021)"), service.lookedUp);
        click("Alien", "Watch on Prime Video");
        main(() -> invoke(service, "dispatchTitle",
                new Class<?>[] {String.class, boolean.class, String.class}, "Jaws", false, ""));
        drain();
        assertEquals(Arrays.asList("Dune (2021)", "Jaws"), service.lookedUp);
    }

    @Test public void newProviderlessSelectionDoesNotInheritOldProvider() throws Exception {
        whitelist("prime video");
        click("Dune", "Watch on Prime Video");
        click("Dune"); drain();
        assertEquals(Collections.singletonList("movie:Dune"), service.launched);
    }

    @Test public void retainedProviderUsesCurrentWhitelistForPositiveRecovery() throws Exception {
        whitelist("prime video");
        click("Dune", "Watch on Prime Video");
        whitelist("");
        main(() -> invoke(service, "dispatchTitle",
                new Class<?>[] {String.class, boolean.class, String.class}, "Dune", false, ""));
        drain();
        assertEquals(Collections.singletonList("movie:Dune"), service.launched);
    }

    @Test public void yearlessDetailLookupKeepsSelectedYearBeyondDuplicateWindow() throws Exception {
        click("Dune (1984)", "Watch on Netflix"); drain();
        MatchCache.clear(); // Require another actual lookup rather than a cache hit.
        main(() -> {
            field(service, "lastDispatchedAt", android.os.SystemClock.elapsedRealtime() - 2001L);
            invoke(service, "dispatchTitle", new Class<?>[] {String.class, boolean.class, String.class},
                    "Dune", false, "");
        });
        drain();
        assertEquals(Arrays.asList("Dune (1984)", "Dune (1984)"), service.lookedUp);
    }

    @Test public void whitelistRecoveryKeepsSelectedYearForFirstLookup() throws Exception {
        whitelist("prime video");
        click("Dune (1984)", "Watch on Prime Video");
        whitelist("");
        main(() -> invoke(service, "dispatchTitle",
                new Class<?>[] {String.class, boolean.class, String.class}, "Dune", false, ""));
        drain();
        assertEquals(Collections.singletonList("Dune (1984)"), service.lookedUp);
    }

    @Test public void conflictingDetailYearCancelsOlderLookupAfterYearlessCallback() throws Exception {
        blockFirst();
        click("Dune (1984)", "Watch on Netflix"); awaitLookup();
        main(() -> {
            field(service, "lastDispatchedAt", android.os.SystemClock.elapsedRealtime() - 2001L);
            invoke(service, "dispatchTitle", new Class<?>[] {String.class, boolean.class, String.class},
                    "Dune", false, "");
            invoke(service, "dispatchTitle", new Class<?>[] {String.class, boolean.class, String.class},
                    "Dune (2021)", false, "");
        });
        service.release.countDown(); drain();
        assertEquals(Arrays.asList("Dune (1984)", "Dune (2021)"), service.lookedUp);
        assertEquals(Collections.singletonList("movie:Dune"), service.launched);
        assertNull(MatchCache.get("Dune (1984)"));
        assertNotNull(MatchCache.get("Dune (2021)"));
    }

    @Test public void explicitDetailYearSupersedesBareLookupInsideDuplicateWindow() throws Exception {
        assertRefinedYearSupersedesBareLookup(false);
    }

    @Test public void explicitDetailYearSupersedesBareLookupAfterDuplicateWindow() throws Exception {
        assertRefinedYearSupersedesBareLookup(true);
    }

    @Test public void explicitDetailYearSuppressesOldFailureAndStillLaunches() throws Exception {
        service.firstFailure = new SocketTimeoutException("synthetic stale timeout");
        assertRefinedYearSupersedesBareLookup(false);
    }

    @Test public void explicitDetailYearDoesNotCacheOldAmbiguousMiss() throws Exception {
        service.firstNoMatch = true;
        assertRefinedYearSupersedesBareLookup(false);
    }

    @Test public void explicitDetailYearCancelsAlreadyPostedBareLaunch() throws Exception {
        blockFirst();
        click("Dune", "Watch on Netflix"); awaitLookup();
        main(() -> {
            service.release.countDown();
            awaitWorker();
            dispatchDetail("Dune (1984)", "");
        });
        drain();
        assertEquals(Arrays.asList("Dune", "Dune (1984)"), service.lookedUp);
        assertEquals(Collections.singletonList("movie:Dune"), service.launched);
        assertEquals(Collections.singletonList("1984"), service.launchedYears);
        assertTrue(service.messages.isEmpty());
    }

    private void assertRefinedYearSupersedesBareLookup(boolean afterDuplicateWindow) throws Exception {
        blockFirst();
        click("Dune", "Watch on Netflix"); awaitLookup();
        if (afterDuplicateWindow) {
            main(() -> field(service, "lastDispatchedAt", android.os.SystemClock.elapsedRealtime() - 2001L));
        }
        detail("Dune (1984)", "");
        service.release.countDown(); drain();
        assertEquals(Arrays.asList("Dune", "Dune (1984)"), service.lookedUp);
        assertEquals(Collections.singletonList("movie:Dune"), service.launched);
        assertEquals(Collections.singletonList("1984"), service.launchedYears);
        assertNull(MatchCache.get("Dune"));
        assertFalse(MatchCache.isMiss("Dune"));
        assertNotNull(MatchCache.get("Dune (1984)"));
        assertTrue(service.messages.isEmpty());
    }

    @Test public void inconsistentTmdbPagesRemainRetryableOnNextSelection() throws Exception {
        final List<String> requests = Collections.synchronizedList(new ArrayList<>());
        final String row = "{\"media_type\":\"movie\",\"id\":841,\"title\":\"Dune\","
                + "\"release_date\":\"1984-01-01\"}";
        service.lookupClient = new TmdbClient("synthetic-test-key-not-a-credential", address -> {
            requests.add(address);
            switch (requests.size()) {
                case 1:
                    assertTrue(address.contains("/search/multi?"));
                    assertTrue(address.endsWith("&page=1"));
                    return "{\"page\":1,\"total_pages\":2,\"total_results\":3,\"results\":[" + row + "]}";
                case 2:
                    assertTrue(address.contains("/search/multi?"));
                    assertTrue(address.endsWith("&page=2"));
                    return "{\"page\":2,\"total_pages\":2,\"total_results\":4,\"results\":[" + row + "]}";
                case 3:
                    assertTrue(address.contains("/search/multi?"));
                    assertTrue(address.endsWith("&page=1"));
                    return "{\"page\":1,\"total_pages\":1,\"total_results\":1,\"results\":[" + row + "]}";
                case 4:
                    assertTrue(address.contains("/movie/841/external_ids?"));
                    return "{\"imdb_id\":\"tt0087182\"}";
                default: throw new AssertionError("Unexpected TMDB request");
            }
        });
        click("Dune", "Watch on Netflix"); drain();
        assertTrue(service.launched.isEmpty());
        assertEquals(Collections.singletonList(R.string.status_lookup_failed), service.messages);
        assertFalse(MatchCache.isMiss("Dune"));
        assertNull(MatchCache.get("Dune"));
        click("Dune", "Watch on Netflix"); drain();
        assertEquals(Arrays.asList("Dune", "Dune"), service.lookedUp);
        assertEquals(4, requests.size());
        assertEquals(Collections.singletonList("movie:Dune"), service.launched);
        assertEquals(Collections.singletonList("1984"), service.launchedYears);
        assertNotNull(MatchCache.get("Dune"));
    }

    @Test public void explicitYearSurvivesEventDescriptionEnrichmentAndLookup() throws Exception {
        main(() -> {
            AccessibilityEvent e = event(AccessibilityEvent.TYPE_VIEW_CLICKED, HOME, "Dune (1984)");
            e.setContentDescription("Dune. Watch on Netflix");
            try { deliverToService(e); } finally { e.recycle(); }
        });
        drain();
        assertEquals(Collections.singletonList("Dune (1984)"), service.lookedUp);
    }

    @Test public void newerContentSelectionCancelsInFlightOlderResult() throws Exception {
        blockFirst();
        click("Alien", "Watch on Netflix");
        awaitLookup();
        click("Dune", "Watch on Netflix");
        service.release.countDown(); drain();
        assertEquals(Collections.singletonList("movie:Dune"), service.launched);
    }

    @Test public void whitelistedSecondSelectionCancelsPreviousLookup() throws Exception {
        blockFirst();
        click("Alien", "Watch on Netflix"); awaitLookup();
        whitelist("prime video");
        click("Dune", "Watch on Prime Video");
        service.release.countDown(); drain();
        assertTrue(service.launched.isEmpty());
        assertEquals(Collections.singletonList("Alien"), service.lookedUp);
    }

    @Test public void policyChangeBeforeNetworkCompletionPreventsLaunch() throws Exception {
        blockFirst();
        click("Alien", "Watch on Netflix"); awaitLookup();
        whitelist("netflix");
        service.release.countDown(); drain();
        assertTrue(service.launched.isEmpty());
    }

    @Test public void targetChangeBeforeNetworkCompletionPreventsLaunch() throws Exception {
        blockFirst();
        click("Alien", "Watch on Netflix"); awaitLookup();
        prefs().edit().putString(AppPrefs.TARGET_MOVIES, AppPrefs.MOVIES_STREMIO).commit();
        service.release.countDown(); drain();
        assertTrue(service.launched.isEmpty());
    }

    @Test public void appClickAndHomeCancelLookupBeforeFinalLaunch() throws Exception {
        blockFirst();
        click("Alien", "Watch on Netflix"); awaitLookup();
        click("MiX Xplorer");
        window(HOME, "com.example.home.HomeActivity");
        service.release.countDown(); drain();
        assertTrue(service.launched.isEmpty());
    }

    @Test public void destructionCancelsUncooperativeNetworkCompletion() throws Exception {
        blockFirst();
        click("Alien", "Watch on Netflix"); awaitLookup();
        ExecutorService worker = worker();
        main(service::onDestroy); destroyed = true;
        service.release.countDown();
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        instrumentation.waitForIdleSync();
        assertTrue(service.launched.isEmpty());
    }

    @Test public void lookupFailuresReportActionableMainThreadOutcomes() throws Exception {
        service.failure = new SocketTimeoutException();
        click("Alien", "Watch on Netflix"); drain();
        assertEquals(Collections.singletonList(R.string.status_lookup_timeout), service.messages);
        service.failure = new IOException();
        click("Dune", "Watch on Netflix"); drain();
        assertEquals(R.string.status_lookup_failed, (int) service.messages.get(1));
        service.failure = null; service.noMatch = true;
        click("Jaws", "Watch on Netflix"); drain();
        assertEquals(R.string.status_no_match, (int) service.messages.get(2));
        prefs().edit().remove(AppPrefs.TMDB_KEY).commit();
        click("Aliens", "Watch on Netflix"); drain();
        assertEquals(R.string.status_key_missing, (int) service.messages.get(3));
    }

    @Test public void staleLookupFailureIsNotShownOverNewSelection() throws Exception {
        blockFirst(); service.failure = new SocketTimeoutException();
        click("Alien", "Watch on Netflix"); awaitLookup();
        click("MiX Xplorer");
        service.release.countDown(); drain();
        assertTrue(service.messages.isEmpty());
    }

    @Test public void borrowedNodeIsNotRecycledOnAncestorSuccessOrMiss() throws Exception {
        main(() -> {
            for (String value : Arrays.asList("Dune", "Column 3")) {
                AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
                node.setText(value);
                try {
                    RecommendationTitleParser.Source found = (RecommendationTitleParser.Source) invoke(
                            service, "cardSourceFromAncestors", new Class<?>[] {AccessibilityNodeInfo.class}, node);
                    assertEquals(value.equals("Dune") ? "Dune" : "", found.title);
                    // API 26 has real pooling: double release throws. API 34 no longer pools.
                    assertEquals(value, node.getText().toString());
                } finally { node.recycle(); }
            }
        });
    }

    @Test public void clippingBoundariesUseActualAccessibilityNodes() throws Exception {
        main(() -> {
            for (int length : new int[] {200, 201, 299, 300, 301}) {
                AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
                char[] text = new char[length]; Arrays.fill(text, 'x');
                node.setText(new String(text)); node.setVisibleToUser(true);
                node.setBoundsInScreen(new Rect(0, 180, 50, 200));
                LinkedHashSet<String> out = new LinkedHashSet<>();
                try {
                    invoke(service, "collectNodeTexts", new Class<?>[] {AccessibilityNodeInfo.class,
                            int.class, LinkedHashSet.class}, node, 0, out);
                    assertEquals(1, out.size());
                    assertEquals(Math.min(length, 300) + "@y=180".length(), out.iterator().next().length());
                    node.setVisibleToUser(false); out.clear();
                    invoke(service, "collectNodeTexts", new Class<?>[] {AccessibilityNodeInfo.class,
                            int.class, LinkedHashSet.class}, node, 0, out);
                    assertTrue(out.isEmpty());
                } finally { node.recycle(); }
            }
        });
    }

    @Test public void foreignActiveWindowIsNotReadAsLauncherContent() throws Exception {
        main(() -> {
            service.root = AccessibilityNodeInfo.obtain();
            service.root.setPackageName("another.application");
            service.root.setText("YouTube"); service.root.setVisibleToUser(true);
            RecommendationTitleParser.Source found = (RecommendationTitleParser.Source) invoke(
                    service, "sourceFromWindowPayloads", new Class<?>[0]);
            assertTrue(found.isEmpty());
            service.root = null; // the service owns/recycles the returned root
        });
    }

    @Test public void launchSupportPreservesRealIntentAndConstrainedFallback() {
        RecordingContext recording = new RecordingContext(context);
        LaunchSupport.Target target = new LaunchSupport.Target(new ComponentName("test.target", "test.target.Main"));
        recording.failFirst = true;
        assertTrue(LaunchSupport.launchFresh(recording,
                YouTubeTarget.ACTION_MEDIA_PLAY_FROM_SEARCH,
                "https://www.youtube.com/results?search_query=Big+Buck+Bunny", target,
                target.packageName, "test", "test", LaunchSupport.COBALT_FRESH_FLAGS));
        assertEquals(2, recording.intents.size());
        assertEquals(target.component, recording.intents.get(0).getComponent());
        assertNull(recording.intents.get(1).getComponent());
        for (Intent intent : recording.intents) {
            assertEquals(target.packageName, intent.getPackage());
            assertEquals(YouTubeTarget.ACTION_MEDIA_PLAY_FROM_SEARCH, intent.getAction());
            assertEquals(LaunchSupport.COBALT_FRESH_FLAGS, intent.getFlags());
            assertFalse((intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK) != 0);
        }
    }

    @Test public void missingMovieTargetsFromWorkerDoNotThrowLooperErrors() throws Exception {
        worker().submit(() -> {
            TitleMatch match = new TitleMatch("Iron Man", "2008", "movie", 1726, "tt0371746");
            assertFalse(NuvioLauncher.open(service, match));
            assertFalse(StremioLauncher.open(service, match));
            assertFalse(WuPlayLauncher.open(service, match));
        }).get(5, TimeUnit.SECONDS);
        instrumentation.waitForIdleSync();
    }

    @Test public void durableInstallerResultSurvivesListenerDetachment() throws Exception {
        assertTrue(UpdateInstallState.begin(context, 713, "9.0.0"));
        field(ApkUpdater.class, "pendingListener", null);
        ApkUpdater.onInstallResult(context, 713, PackageInstaller.STATUS_FAILURE_ABORTED, "test");
        Context fresh = context.createPackageContext(context.getPackageName(), 0);
        assertEquals(PackageInstaller.STATUS_FAILURE_ABORTED, UpdateInstallState.status(fresh));
        ApkUpdater.onInstallResult(context, 999, PackageInstaller.STATUS_SUCCESS, "wrong session");
        assertEquals(PackageInstaller.STATUS_FAILURE_ABORTED, UpdateInstallState.status(fresh));
    }

    @Test public void missingInstallerConfirmationFailsInsteadOfStayingPending() {
        assertTrue(UpdateInstallState.begin(context, 714, "9.0.0"));
        Intent result = new Intent(UpdateInstallReceiver.ACTION_STATUS)
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, 714)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION);
        new UpdateInstallReceiver().onReceive(context, result);
        assertEquals(PackageInstaller.STATUS_FAILURE, UpdateInstallState.status(context));
    }

    @Test public void selfReplacementReconcilesVersionWithoutAnActivityListener() {
        assertTrue(UpdateInstallState.begin(context, 715, UpdateChecker.currentVersion(context)));
        assertEquals(PackageInstaller.STATUS_SUCCESS, UpdateInstallState.status(context));
    }

    @Test public void androidPackageValidationRejectsFakeAndEqualVersionArchives() throws Exception {
        File bad = File.createTempFile("fake-apk", ".apk", context.getCacheDir());
        try {
            try (FileOutputStream out = new FileOutputStream(bad)) { out.write(new byte[] {80,75,3,4}); }
            assertFalse(ApkUpdater.isOwnNewerPackage(context, bad, "9.0.0"));
            File installed = new File(context.getApplicationInfo().sourceDir);
            assertTrue(ApkArchive.isValid(installed, installed.length()));
            assertFalse(ApkUpdater.isOwnNewerPackage(context, installed, UpdateChecker.currentVersion(context)));
        } finally { bad.delete(); }
    }

    @Test public void eventFixtureHasTheFrameworkDeliveryContract() {
        AccessibilityEvent original = event(AccessibilityEvent.TYPE_VIEW_CLICKED, HOME,
                "Dune (1984)", "Watch on Netflix");
        original.setContentDescription("Dune. Watch on Netflix");
        AccessibilityEvent delivered = sealedEventCopy(original);
        try {
            assertEquals(original.getEventType(), delivered.getEventType());
            assertEquals(original.getPackageName(), delivered.getPackageName());
            assertEquals(original.getClassName(), delivered.getClassName());
            assertEquals(original.getContentDescription(), delivered.getContentDescription());
            assertEquals(original.getText(), delivered.getText());
            assertNull(delivered.getSource()); // no live accessibility connection in this fixture
            try {
                delivered.setPackageName("should.not.be.mutable");
                fail("Framework-delivered events must be sealed");
            } catch (IllegalStateException expected) { }
        } finally {
            delivered.recycle();
            original.recycle();
        }
    }

    @Test public void settingsRestoresRetryButtonAfterPersistedInstallerFailure() throws Exception {
        // An uncommitted real session exercises pending UI without installing an APK.
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int session = installer.createSession(params);
        Activity activity = null;
        try {
            prefs().edit().putLong("update_check_at", System.currentTimeMillis())
                    .putString("update_version", "9.0.0")
                    .putString("update_url", "https://github.com/xantipater/GTV2STREAM/releases/tag/v9.0.0")
                    .putString("update_apk_url", "https://github.com/xantipater/GTV2STREAM/releases/download/v9.0.0/test.apk")
                    .putString(AppPrefs.UPDATE_PROMPTED_VERSION, "9.0.0").commit();
            assertTrue(UpdateInstallState.begin(context, session, "9.0.0"));
            // Model the live install worker owning this intentionally unsealed session.
            field(ApkUpdater.class, "handoffSession", session);
            activity = instrumentation.startActivitySync(new Intent(context, SettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity settings = activity;
            Field buttonField = SettingsActivity.class.getDeclaredField("downloadUpdateButton");
            buttonField.setAccessible(true);
            Button button = (Button) buttonField.get(settings);
            Method refresh = SettingsActivity.class.getDeclaredMethod("refreshInstallStatus");
            refresh.setAccessible(true);
            main(() -> {
                assertFalse(button.isEnabled());
                assertEquals(context.getString(R.string.update_confirm_install), button.getText().toString());
            });
            assertTrue(UpdateInstallState.record(context, session, PackageInstaller.STATUS_FAILURE_ABORTED));
            main(() -> {
                try { refresh.invoke(settings); }
                catch (Exception error) { throw new AssertionError(error); }
                assertTrue(button.isEnabled());
                assertEquals(context.getString(R.string.download_update, "9.0.0"), button.getText().toString());
            });
        } finally {
            if (activity != null) {
                final Activity settings = activity;
                main(settings::finish);
                instrumentation.waitForIdleSync();
            }
            field(ApkUpdater.class, "handoffSession", -1);
            installer.abandonSession(session);
        }
    }

    private void deliverToService(AccessibilityEvent original) {
        AccessibilityEvent delivered = sealedEventCopy(original);
        try { service.onAccessibilityEvent(delivered); }
        finally { delivered.recycle(); }
    }

    /**
     * Android seals events before delivering them to an accessibility service.
     * Synthetic obtain() events are writable and getSource() rejects them.
     *
     * The record-free Parcel format in the API 26 and 34 AOSP implementations
     * starts with the event sealed flag and ends with the record sealed flag,
     * then a zero record count. Keep all payload serialization in the framework;
     * change only those two flags. Assert the layout and delivery contract so a
     * platform format change fails the fixture, not silently the routing tests.
     * This avoids hidden-API reflection or relaxing emulator platform checks.
     * It is test-only; no live source node or launcher binding is claimed.
     */
    private static AccessibilityEvent sealedEventCopy(AccessibilityEvent original) {
        assertEquals("Fixture supports record-free events only", 0, original.getRecordCount());
        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            int recordFlag = parcel.dataSize() - 2 * Integer.BYTES;
            assertTrue(recordFlag > 0);
            parcel.setDataPosition(0);
            assertEquals("Expected writable event flag", 0, parcel.readInt());
            parcel.setDataPosition(recordFlag);
            assertEquals("Expected writable record flag", 0, parcel.readInt());
            assertEquals("Expected no appended records", 0, parcel.readInt());
            parcel.setDataPosition(0); parcel.writeInt(1);
            parcel.setDataPosition(recordFlag); parcel.writeInt(1);
            parcel.setDataPosition(0);
            return AccessibilityEvent.CREATOR.createFromParcel(parcel);
        } finally { parcel.recycle(); }
    }

    private SharedPreferences prefs() { return context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE); }
    private void whitelist(String ids) { prefs().edit().putString(AppPrefs.WHITELIST_PROVIDERS, ids).commit(); }
    private void blockFirst() { service.blockFirst = true; }
    private void awaitLookup() throws Exception { assertTrue(service.entered.await(5, TimeUnit.SECONDS)); }
    private void main(Runnable action) { instrumentation.runOnMainSync(action); }
    private void click(String... text) { deliver(AccessibilityEvent.TYPE_VIEW_CLICKED, HOME, text); }
    private void focus(String... text) { deliver(AccessibilityEvent.TYPE_VIEW_FOCUSED, HOME, text); }
    private void nodeEvent(int type, String nodeText, String nodeDescription, String... values) {
        main(() -> {
            AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain();
            node.setText(nodeText);
            node.setContentDescription(nodeDescription);
            service.eventNode = node;
            AccessibilityEvent event = event(type, HOME, values);
            try { deliverToService(event); }
            finally { service.eventNode = null; node.recycle(); event.recycle(); }
        });
    }
    // The unbound service fixture cannot expose a live entity title tree. Enter
    // its actual detail dispatch after extraction, as the earlier detail tests do.
    private void detail(String title, String provider) { main(() -> dispatchDetail(title, provider)); }
    private void dispatchDetail(String title, String provider) {
        invoke(service, "dispatchTitle", new Class<?>[] {String.class, boolean.class, String.class},
                title, false, provider);
    }
    private void awaitWorker() {
        try { worker().submit(() -> {}).get(5, TimeUnit.SECONDS); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private void stockYoutube() { window(YOUTUBE, "youtube.Activity"); }
    private void window(String pkg, String className) {
        main(() -> {
            AccessibilityEvent e = event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, pkg);
            e.setClassName(className);
            try { deliverToService(e); } finally { e.recycle(); }
        });
    }
    private void deliver(int type, String pkg, String... values) {
        main(() -> {
            AccessibilityEvent e = event(type, pkg, values);
            try { deliverToService(e); } finally { e.recycle(); }
        });
    }
    private static AccessibilityEvent event(int type, String pkg, String... values) {
        AccessibilityEvent e = AccessibilityEvent.obtain(type);
        e.setPackageName(pkg); e.setClassName("android.view.View");
        for (String value : values) e.getText().add(value);
        return e;
    }
    private ExecutorService worker() { return (ExecutorService) field(service, "worker"); }
    private void drain() throws Exception {
        worker().submit(() -> {}).get(5, TimeUnit.SECONDS);
        instrumentation.waitForIdleSync();
    }
    private static Object field(Object object, String name) {
        try { Field f = type(object).getDeclaredField(name); f.setAccessible(true); return f.get(instance(object)); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static void field(Object object, String name, Object value) {
        try { Field f = type(object).getDeclaredField(name); f.setAccessible(true); f.set(instance(object), value); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static Class<?> type(Object object) { return object instanceof Class ? (Class<?>) object : TvRecommendationService.class; }
    private static Object instance(Object object) { return object instanceof Class ? null : object; }
    private static Object invoke(Object object, String name, Class<?>[] params, Object... args) {
        try {
            Method m = TvRecommendationService.class.getDeclaredMethod(name, params); m.setAccessible(true);
            return m.invoke(object, args);
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private static class RecordingContext extends ContextWrapper {
        final List<Intent> intents = new ArrayList<>();
        boolean failFirst;
        RecordingContext(Context base) { super(base); }
        @Override public void startActivity(Intent intent) {
            intents.add(new Intent(intent));
            if (failFirst && intents.size() == 1) throw new ActivityNotFoundException("synthetic rejection");
        }
    }

    private static class TestService extends TvRecommendationService {
        final List<String> launched = Collections.synchronizedList(new ArrayList<>());
        final List<String> launchedYears = Collections.synchronizedList(new ArrayList<>());
        final List<String> lookedUp = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> messages = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean blockFirst, noMatch;
        volatile boolean firstNoMatch;
        volatile IOException failure;
        volatile IOException firstFailure;
        volatile TmdbClient lookupClient;
        boolean youtubeSuccess = true;
        int badges;
        AccessibilityNodeInfo root;
        AccessibilityNodeInfo eventNode;
        String detailTitle;
        RecommendationTitleParser.Source windowPayload;
        TestService(Context base) { attachBaseContext(base); }
        @Override public AccessibilityNodeInfo getRootInActiveWindow() { return root; }
        @Override AccessibilityNodeInfo eventSource(AccessibilityEvent event) {
            return eventNode == null ? super.eventSource(event) : AccessibilityNodeInfo.obtain(eventNode);
        }
        @Override String titleFromDetailRoot() {
            // Unbound framework fixtures cannot resolve live entity rows. Keep
            // production year enrichment, and replace only tree acquisition.
            return detailTitle == null ? super.titleFromDetailRoot() : (String) invoke(this,
                    "detailLookupTitle", new Class<?>[] {String.class}, detailTitle);
        }
        @Override RecommendationTitleParser.Source readWindowSource() {
            // Replace extraction from an unbound live window, never the focus
            // identity check or click/fallback orchestration under test.
            return windowPayload == null ? super.readWindowSource() : windowPayload;
        }
        @Override TitleMatch lookupTitle(String key, String title) throws IOException {
            assertNotSame(Looper.getMainLooper(), Looper.myLooper());
            lookedUp.add(title);
            if (blockFirst && lookedUp.size() == 1) {
                entered.countDown();
                boolean waiting = true;
                while (waiting) {
                    try { waiting = !release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { /* prove stale completion cannot launch */ }
                }
            }
            if (lookedUp.size() == 1 && firstFailure != null) throw firstFailure;
            if (lookedUp.size() == 1 && firstNoMatch) return null;
            if (failure != null) throw failure;
            if (lookupClient != null) return lookupClient.searchBest(title);
            return noMatch ? null : new TitleMatch(TitleResultHelper.cleanTitle(title),
                    TitleResultHelper.extractYear(title), "movie", 1, "tt1234567");
        }
        @Override boolean openMoviesTarget(String target, TitleMatch match) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            launchedYears.add(match.year);
            launched.add("movie:" + match.title); return true;
        }
        @Override boolean openYouTubeTarget(String title) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            launched.add("youtube:" + title); return youtubeSuccess;
        }
        @Override void showRedirectBadge() { badges++; }
        @Override void notifyUser(int message) {
            assertSame(Looper.getMainLooper(), Looper.myLooper()); messages.add(message);
        }
    }
}

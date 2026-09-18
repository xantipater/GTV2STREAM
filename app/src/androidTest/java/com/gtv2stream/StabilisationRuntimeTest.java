package com.gtv2stream;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.graphics.Rect;
import android.os.Looper;
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

    @Test public void explicitYearSurvivesEventDescriptionEnrichmentAndLookup() throws Exception {
        main(() -> {
            AccessibilityEvent e = event(AccessibilityEvent.TYPE_VIEW_CLICKED, HOME, "Dune (1984)");
            e.setContentDescription("Dune. Watch on Netflix");
            try { service.onAccessibilityEvent(e); } finally { e.recycle(); }
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

    private SharedPreferences prefs() { return context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE); }
    private void whitelist(String ids) { prefs().edit().putString(AppPrefs.WHITELIST_PROVIDERS, ids).commit(); }
    private void blockFirst() { service.blockFirst = true; }
    private void awaitLookup() throws Exception { assertTrue(service.entered.await(5, TimeUnit.SECONDS)); }
    private void main(Runnable action) { instrumentation.runOnMainSync(action); }
    private void click(String... text) { deliver(AccessibilityEvent.TYPE_VIEW_CLICKED, HOME, text); }
    private void focus(String... text) { deliver(AccessibilityEvent.TYPE_VIEW_FOCUSED, HOME, text); }
    private void stockYoutube() { window(YOUTUBE, "youtube.Activity"); }
    private void window(String pkg, String className) {
        main(() -> {
            AccessibilityEvent e = event(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, pkg);
            e.setClassName(className);
            try { service.onAccessibilityEvent(e); } finally { e.recycle(); }
        });
    }
    private void deliver(int type, String pkg, String... values) {
        main(() -> {
            AccessibilityEvent e = event(type, pkg, values);
            try { service.onAccessibilityEvent(e); } finally { e.recycle(); }
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
        final List<String> lookedUp = Collections.synchronizedList(new ArrayList<>());
        final List<Integer> messages = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean blockFirst, noMatch;
        volatile IOException failure;
        boolean youtubeSuccess = true;
        int badges;
        AccessibilityNodeInfo root;
        TestService(Context base) { attachBaseContext(base); }
        @Override public AccessibilityNodeInfo getRootInActiveWindow() { return root; }
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
            if (failure != null) throw failure;
            return noMatch ? null : new TitleMatch(TitleResultHelper.cleanTitle(title),
                    TitleResultHelper.extractYear(title), "movie", 1, "tt1234567");
        }
        @Override boolean openMoviesTarget(String target, TitleMatch match) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
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

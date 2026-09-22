package com.gtv2stream;

import static org.junit.Assert.*;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Executes real launchers with scripted package-query results and startActivity. */
@RunWith(AndroidJUnit4.class)
public class TargetLaunchRuntimeTest {
    private static final String SEARCH = "https://www.youtube.com/results?search_query=Big+Buck+Bunny";

    @Before public void clearBefore() { LaunchSupport.clearCache(); }
    @After public void clearAfter() { LaunchSupport.clearCache(); }

    @Test public void cobaltProductionLauncherBuildsItsDocumentedIntent() throws Exception {
        RecordingContext context = recordingContext();
        assertTrue(launchCobalt(context));
        assertEquals(1, context.intents.size());
        Intent launch = context.intents.get(0);
        assertEquals(new ComponentName("io.gh.reisxd.tizentube.cobalt",
                "dev.cobalt.app.MainActivity"), launch.getComponent());
        assertCobaltContract(launch);
    }

    @Test public void cobaltProductionFallbackKeepsItsPackageActionUriAndFlags() throws Exception {
        RecordingContext context = recordingContext();
        context.rejectCount = 1;
        assertTrue(launchCobalt(context));
        assertEquals(2, context.intents.size());
        assertNotNull(context.intents.get(0).getComponent());
        assertNull(context.intents.get(1).getComponent());
        for (Intent launch : context.intents) assertCobaltContract(launch);
    }

    @Test public void rejectedCobaltLaunchAndFallbackReportFailure() throws Exception {
        RecordingContext context = recordingContext();
        context.rejectCount = 2;
        assertFalse(launchCobalt(context));
        assertEquals(2, context.intents.size());
        for (Intent launch : context.intents) assertCobaltContract(launch);
        assertNull(LaunchSupport.cachedTarget(LaunchPolicy.cacheKey(YouTubeTarget.TIZENTUBE, SEARCH)));
    }

    @Test public void removedOrDisabledStableUsesAvailableBetaOnFirstNextClick() {
        SmartTubeEnvironment context = smartTubeEnvironment();
        context.handlers.put("org.smarttube.stable", "StableActivity");
        context.handlers.put("org.smarttube.beta", "BetaActivity");
        assertTrue(YouTubeLauncher.launchSmartTube(context, SEARCH, context));
        assertEquals("org.smarttube.stable", context.intents.get(0).getPackage());

        // Both uninstall and disabling a handler remove it from default-only queries.
        context.handlers.remove("org.smarttube.stable");
        context.intents.clear();
        assertTrue(YouTubeLauncher.launchSmartTube(context, SEARCH, context));
        assertEquals(1, context.intents.size());
        assertSmartTubeContract(context.intents.get(0), "org.smarttube.beta", "BetaActivity");
    }

    @Test public void newlyInstalledStableSupersedesPreviouslySelectedBeta() {
        SmartTubeEnvironment context = smartTubeEnvironment();
        context.handlers.put("org.smarttube.beta", "BetaActivity");
        assertTrue(YouTubeLauncher.launchSmartTube(context, SEARCH, context));
        assertEquals("org.smarttube.beta", context.intents.get(0).getPackage());

        context.handlers.put("org.smarttube.stable", "StableActivity");
        context.intents.clear();
        assertTrue(YouTubeLauncher.launchSmartTube(context, SEARCH, context));
        assertEquals(1, context.intents.size());
        assertSmartTubeContract(context.intents.get(0), "org.smarttube.stable", "StableActivity");
    }

    @Test public void changedSmartTubeActivityIsResolvedBeforeTheNextLaunch() {
        SmartTubeEnvironment context = smartTubeEnvironment();
        context.handlers.put("org.smarttube.stable", "OldActivity");
        assertTrue(YouTubeLauncher.launchSmartTube(context, SEARCH, context));

        context.handlers.put("org.smarttube.stable", "NewActivity");
        context.intents.clear();
        assertTrue(YouTubeLauncher.launchSmartTube(context, SEARCH, context));
        assertEquals(1, context.intents.size());
        assertSmartTubeContract(context.intents.get(0), "org.smarttube.stable", "NewActivity");
    }

    @Test public void removedSmartTubeNeverFallsBackToAnUnrelatedYouTubeHandler() {
        SmartTubeEnvironment context = smartTubeEnvironment();
        context.handlers.put("org.smarttube.stable", "StableActivity");
        assertTrue(YouTubeLauncher.launchSmartTube(context, SEARCH, context));

        context.handlers.remove("org.smarttube.stable");
        context.handlers.put("com.google.android.youtube.tv", "YouTubeActivity");
        context.handlers.put(YouTubeTarget.COBALT_PACKAGE, YouTubeTarget.COBALT_ACTIVITY);
        context.intents.clear();
        assertFalse(YouTubeLauncher.launchSmartTube(context, SEARCH, context));
        assertTrue(context.intents.isEmpty());
    }

    @Test public void rejectedSmartTubeComponentKeepsItsSelectedPackageOnRetry() {
        SmartTubeEnvironment context = smartTubeEnvironment();
        context.handlers.put("org.smarttube.stable", "StableActivity");
        context.handlers.put("org.smarttube.beta", "BetaActivity");
        context.rejectExplicit = true;
        assertTrue(YouTubeLauncher.launchSmartTube(context, SEARCH, context));
        assertEquals(2, context.intents.size());
        assertSmartTubeContract(context.intents.get(0), "org.smarttube.stable", "StableActivity");
        assertSmartTubeContract(context.intents.get(1), "org.smarttube.stable", null);
    }

    @Test public void legacySmartTubeRemainsAvailableWhenModernPackagesAreAbsent() {
        SmartTubeEnvironment context = smartTubeEnvironment();
        context.handlers.put("com.teamsmart.videomanager.tv", "LegacyActivity");
        assertTrue(YouTubeLauncher.launchSmartTube(context, SEARCH, context));
        assertEquals(1, context.intents.size());
        assertSmartTubeContract(context.intents.get(0),
                "com.teamsmart.videomanager.tv", "LegacyActivity");
    }

    private static void assertSmartTubeContract(Intent launch, String packageName, String activity) {
        assertEquals(packageName, launch.getPackage());
        assertEquals(activity == null ? null : new ComponentName(packageName, activity),
                launch.getComponent());
        assertEquals(Intent.ACTION_VIEW, launch.getAction());
        assertEquals(SEARCH, launch.getDataString());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK,
                launch.getFlags());
    }

    private static SmartTubeEnvironment smartTubeEnvironment() {
        return new SmartTubeEnvironment(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    private static final class SmartTubeEnvironment extends ContextWrapper
            implements YouTubeLauncher.ActivityQuery {
        final Map<String, String> handlers = new HashMap<>();
        final List<Intent> intents = new ArrayList<>();
        boolean rejectExplicit;

        SmartTubeEnvironment(Context base) { super(base); }

        @Override public List<ResolveInfo> query(Intent probe, int flags) {
            assertEquals(Intent.ACTION_VIEW, probe.getAction());
            assertEquals(SEARCH, probe.getDataString());
            assertEquals(PackageManager.MATCH_DEFAULT_ONLY, flags);
            assertTrue("Every query stays scoped to SmartTube", LaunchPolicy.SMART_TUBE_ORDER
                    .contains(probe.getPackage()));
            String activity = handlers.get(probe.getPackage());
            if (activity == null) return Collections.emptyList();
            ResolveInfo result = new ResolveInfo();
            result.activityInfo = new ActivityInfo();
            result.activityInfo.packageName = probe.getPackage();
            result.activityInfo.name = activity;
            return Collections.singletonList(result);
        }

        @Override public void startActivity(Intent intent) {
            intents.add(new Intent(intent));
            String activity = handlers.get(intent.getPackage());
            ComponentName explicit = intent.getComponent();
            if (activity == null || explicit != null
                    && (rejectExplicit || !activity.equals(explicit.getClassName()))) {
                throw new ActivityNotFoundException("synthetic unavailable handler");
            }
        }
    }

    private static void assertCobaltContract(Intent launch) {
        assertEquals("io.gh.reisxd.tizentube.cobalt", launch.getPackage());
        assertEquals("android.media.action.MEDIA_PLAY_FROM_SEARCH", launch.getAction());
        assertEquals(SEARCH, launch.getDataString());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS, launch.getFlags());
        assertEquals(0, launch.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    private static boolean launchCobalt(Context context) throws Exception {
        Method launch = YouTubeLauncher.class.getDeclaredMethod("launchCobalt", Context.class, String.class);
        launch.setAccessible(true);
        return (boolean) launch.invoke(null, context, SEARCH);
    }

    private static RecordingContext recordingContext() {
        return new RecordingContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    private static final class RecordingContext extends ContextWrapper {
        final List<Intent> intents = new ArrayList<>();
        int rejectCount;
        RecordingContext(Context base) { super(base); }

        @Override public void startActivity(Intent intent) {
            intents.add(new Intent(intent));
            if (intents.size() <= rejectCount) {
                throw new ActivityNotFoundException("synthetic target rejection");
            }
        }
    }
}
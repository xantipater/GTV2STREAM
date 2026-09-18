package com.gtv2stream;

import static org.junit.Assert.*;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Executes the real Cobalt launcher contract; only startActivity is substituted. */
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
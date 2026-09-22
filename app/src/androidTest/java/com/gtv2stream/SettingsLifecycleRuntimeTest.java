package com.gtv2stream;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Actual Settings, preferences, main-loop scheduling and lifecycle; no service is enabled. */
@RunWith(AndroidJUnit4.class)
public class SettingsLifecycleRuntimeTest {
    private static final String SENTINEL = "Waiting for a status refresh";
    private Instrumentation instrumentation;
    private Context context;
    private Activity activity;
    private Activity help;
    private TextView status;

    @Before public void setUp() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        prefs().edit().clear().putLong("update_check_at", System.currentTimeMillis())
                .putString("update_version", "9.0.0")
                .putString("update_url", "https://github.com/xantipater/GTV2STREAM/releases/tag/v9.0.0")
                .putString(AppPrefs.UPDATE_PROMPTED_VERSION, "9.0.0").commit();
        activity = instrumentation.startActivitySync(new Intent(context, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        status = (TextView) field(activity, "status");
        instrumentation.waitForIdleSync();
    }

    @After public void tearDown() {
        if (help != null) main(help::finish);
        if (activity != null) main(activity::finish);
        instrumentation.waitForIdleSync();
        prefs().edit().clear().commit();
    }

    @Test public void releaseRefreshRestoresTheReleaseLabelAndActionTogether() throws Exception {
        Button button = (Button) field(activity, "updateButton");
        IntentFilter releasePage = new IntentFilter(Intent.ACTION_VIEW);
        releasePage.addDataScheme("https");
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(releasePage,
                new Instrumentation.ActivityResult(Activity.RESULT_OK, null), true);
        try {
            main(() -> {
                invoke("showAllowInstallsAction");
                assertEquals(context.getString(R.string.update_allow_installs), button.getText().toString());
                invoke("showUpdate", new Class<?>[]{UpdateChecker.UpdateInfo.class},
                        new UpdateChecker.UpdateInfo("9.0.0",
                                "https://github.com/xantipater/GTV2STREAM/releases/tag/v9.0.0", null, -1L));
                assertEquals(context.getString(R.string.open_update), button.getText().toString());
                assertTrue(button.performClick());
            });
            assertEquals("The release label must open the release page", 1, monitor.getHits());
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    @Test public void lateHeartbeatAndDisconnectRefreshTheAlreadyVisibleStatus() {
        main(() -> status.setText(SENTINEL));
        prefs().edit().putLong(AppPrefs.SERVICE_CONNECTED_AT, System.currentTimeMillis()).commit();
        awaitStatusRefresh();
        assertTrue((Boolean) invoke("serviceActuallyConnected"));
        main(() -> status.setText(SENTINEL));
        prefs().edit().remove(AppPrefs.SERVICE_CONNECTED_AT).commit();
        awaitStatusRefresh();
        assertFalse((Boolean) invoke("serviceActuallyConnected"));
    }

    @Test public void heartbeatExpiresWithoutAnotherPreferenceWriteOrReopeningSettings() {
        // Give the existing heartbeat one second of its real sixty-second lifetime.
        prefs().edit().putLong(AppPrefs.SERVICE_CONNECTED_AT,
                System.currentTimeMillis() - 59000L).commit();
        instrumentation.waitForIdleSync();
        assertTrue((Boolean) invoke("serviceActuallyConnected"));
        main(() -> status.setText(SENTINEL));
        awaitStatusRefresh();
        assertFalse("The refresh must occur when the heartbeat actually expires",
                (Boolean) invoke("serviceActuallyConnected"));
    }

    @Test public void pausedSettingsStopsExpiryRefreshAndRefreshesWhenResumed() throws Exception {
        prefs().edit().putLong(AppPrefs.SERVICE_CONNECTED_AT,
                System.currentTimeMillis() - 59000L).commit();
        instrumentation.waitForIdleSync();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(HelpActivity.class.getName(), null, false);
        try {
            main(() -> activity.startActivity(new Intent(activity, HelpActivity.class)));
            help = instrumentation.waitForMonitorWithTimeout(monitor, 5000L);
            assertNotNull(help);
        } finally {
            instrumentation.removeMonitor(monitor);
        }
        instrumentation.waitForIdleSync();
        assertFalse((Boolean) field(activity, "resumed"));
        main(() -> status.setText(SENTINEL));
        long deadline = SystemClock.uptimeMillis() + 1400L;
        while (SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20L);
        instrumentation.waitForIdleSync();
        main(() -> assertEquals("Paused screens must cancel their expiry callback", SENTINEL,
                status.getText().toString()));
        // A late service connection must also wait for the screen to resume.
        prefs().edit().putLong(AppPrefs.SERVICE_CONNECTED_AT, System.currentTimeMillis()).commit();
        instrumentation.waitForIdleSync();
        main(() -> assertEquals(SENTINEL, status.getText().toString()));
        main(help::finish);
        awaitStatusRefresh();
        assertTrue((Boolean) field(activity, "resumed"));
        assertTrue((Boolean) invoke("serviceActuallyConnected"));
    }

    private void awaitStatusRefresh() {
        long deadline = SystemClock.uptimeMillis() + 5000L;
        AtomicBoolean refreshed = new AtomicBoolean();
        do {
            main(() -> refreshed.set(!SENTINEL.contentEquals(status.getText())));
            if (refreshed.get()) return;
            SystemClock.sleep(10L);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Visible Settings status never refreshed");
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE);
    }

    private void main(Runnable action) { instrumentation.runOnMainSync(action); }

    private Object invoke(String name) { return invoke(name, new Class<?>[0]); }

    private Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = SettingsActivity.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(activity, args);
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}

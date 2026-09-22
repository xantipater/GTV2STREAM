package com.gtv2stream;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Actual Settings, receiver and framework sessions; no APK is committed or installed. */
@RunWith(AndroidJUnit4.class)
public class UpdaterLifecycleRuntimeTest {
    private Instrumentation instrumentation;
    private Context context;
    private PackageInstaller installer;
    private final List<Integer> sessions = new ArrayList<>();
    private Activity activity;
    private final List<Activity> activities = new ArrayList<>();
    private CountDownLatch releaseDownload;

    @Before public void setUp() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        installer = context.getPackageManager().getPackageInstaller();
        ApkUpdater.cancelActive();
        field(ApkUpdater.class, "handoffSession", -1);
        field(ApkUpdater.class, "pendingListener", null);
        prefs().edit().clear().putLong("update_check_at", System.currentTimeMillis())
                .putString("update_version", "9.0.0")
                .putString("update_url", "https://github.com/xantipater/GTV2STREAM/releases/tag/v9.0.0")
                .putString("update_apk_url", "https://github.com/xantipater/GTV2STREAM/releases/download/v9.0.0/test.apk")
                .putString(AppPrefs.UPDATE_PROMPTED_VERSION, "9.0.0").commit();
    }

    @After public void tearDown() throws Exception {
        if (activity != null && !activities.contains(activity)) activities.add(activity);
        for (Activity settings : activities) main(settings::finish);
        instrumentation.waitForIdleSync();
        ApkUpdater.cancelActive();
        if (releaseDownload != null) {
            releaseDownload.countDown();
            ((ExecutorService) field(ApkUpdater.class, "EXECUTOR"))
                    .submit(() -> { }).get(30, TimeUnit.SECONDS);
        }
        field(ApkUpdater.class, "pendingListener", null);
        field(ApkUpdater.class, "handoffSession", -1);
        UpdateInstallState.clear(context);
        for (int session : sessions) {
            try { installer.abandonSession(session); }
            catch (RuntimeException ignored) { }
        }
        instrumentation.waitForIdleSync();
    }

    @Test public void refreshedReleaseNoticePreservesTheActiveCancelControl() throws Exception {
        holdDownloadWorker();
        activity = openSettings();
        Button button = (Button) field(activity, "downloadUpdateButton");
        main(() -> assertTrue(button.performClick()));
        ApkUpdater.DownloadHandle handle = (ApkUpdater.DownloadHandle) field(activity, "updateDownload");
        assertNotNull(handle);
        Method refreshed = SettingsActivity.class.getDeclaredMethod("showUpdate", UpdateChecker.UpdateInfo.class);
        refreshed.setAccessible(true);
        main(() -> {
            try {
                // A delayed GitHub check completes after its cached notice was used.
                refreshed.invoke(activity, new UpdateChecker.UpdateInfo("9.0.0",
                        "https://github.com/xantipater/GTV2STREAM/releases/tag/v9.0.0",
                        "https://not-allowed.invalid/update.apk", -1L));
            } catch (Exception error) { throw new AssertionError(error); }
            assertEquals(context.getString(R.string.cancel_download), button.getText().toString());
            assertTrue(button.performClick());
        });
        assertTrue("The control must cancel, not begin another download", handle.isCancelled());
        assertNull(field(activity, "updateDownload"));
    }

    @Test public void destroyingOlderSettingsCannotCancelTheNewerActivityDownload() throws Exception {
        holdDownloadWorker();
        Activity older = openSettings();
        Activity newer = openSettings();
        assertNotSame(older, newer);
        Button button = (Button) field(newer, "downloadUpdateButton");
        main(() -> assertTrue(button.performClick()));
        ApkUpdater.DownloadHandle handle = (ApkUpdater.DownloadHandle) field(newer, "updateDownload");
        assertNotNull(handle);
        main(older::finish);
        long deadline = android.os.SystemClock.uptimeMillis() + 5000L;
        while (!older.isDestroyed() && android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync();
            Thread.sleep(10L);
        }
        assertTrue("The older Activity must actually reach onDestroy", older.isDestroyed());
        assertFalse("Only the owning Activity may cancel this transfer", handle.isCancelled());
        main(() -> assertEquals(context.getString(R.string.cancel_download), button.getText().toString()));
    }
    @Test public void stoppedSettingsLeavesInstallerOutcomeForTheNextVisibleActivity() throws Exception {
        int session = pendingSession();
        Activity older = openSettings();
        Activity help = instrumentation.startActivitySync(new Intent(context, HelpActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        activities.add(help);
        main(() -> new UpdateInstallReceiver().onReceive(context,
                result(session, PackageInstaller.STATUS_FAILURE_ABORTED)));
        instrumentation.waitForIdleSync();
        Method refreshed = SettingsActivity.class.getDeclaredMethod("showUpdate", UpdateChecker.UpdateInfo.class);
        refreshed.setAccessible(true);
        main(() -> {
            try {
                // A metadata response can also arrive after Settings has paused.
                refreshed.invoke(older, new UpdateChecker.UpdateInfo("9.0.0",
                        "https://github.com/xantipater/GTV2STREAM/releases/tag/v9.0.0",
                        "https://not-allowed.invalid/update.apk", -1L));
            } catch (Exception error) { throw new AssertionError(error); }
        });
        assertEquals("A background screen must not consume an unseen installer outcome",
                PackageInstaller.STATUS_FAILURE_ABORTED, UpdateInstallState.status(context));
        main(older::finish);
        Activity current = openSettings();
        TextView status = (TextView) field(current, "updateStatus");
        Button button = (Button) field(current, "downloadUpdateButton");
        main(() -> {
            assertTrue(button.isEnabled());
            assertEquals(context.getString(R.string.update_install_cancelled), status.getText().toString());
        });
    }
    @Test public void foregroundSettingsRecoversWhenInstallerOutcomeHasNoOriginalListener() throws Exception {
        int session = pendingSession();
        activity = instrumentation.startActivitySync(new Intent(context, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Button button = (Button) field(activity, "downloadUpdateButton");
        TextView status = (TextView) field(activity, "updateStatus");
        main(() -> assertFalse(button.isEnabled()));

        // A recreated Activity has no callback; the live handoff worker still owns its session.
        assertNull(field(ApkUpdater.class, "pendingListener"));
        main(() -> new UpdateInstallReceiver().onReceive(context,
                result(session, PackageInstaller.STATUS_FAILURE_ABORTED)));
        instrumentation.waitForIdleSync();
        main(() -> {
            assertTrue("A foreground Activity must observe the durable outcome", button.isEnabled());
            assertEquals(context.getString(R.string.download_update, "9.0.0"), button.getText().toString());
            assertEquals(context.getString(R.string.update_install_cancelled), status.getText().toString());
        });
    }

    @Test public void currentListenerReceivesTheTerminalInstallerOutcome() throws Exception {
        int session = pendingSession();
        RecordingListener listener = new RecordingListener();
        field(ApkUpdater.class, "pendingListener", listener);
        main(() -> ApkUpdater.onInstallResult(context, session, PackageInstaller.STATUS_FAILURE_ABORTED, null));
        instrumentation.waitForIdleSync();
        assertEquals(java.util.Collections.singletonList("failure:INSTALL_CANCELLED"), listener.events);
        assertEquals(PackageInstaller.STATUS_FAILURE_ABORTED, UpdateInstallState.status(context));
        assertNull(field(ApkUpdater.class, "pendingListener"));
    }
    @Test public void detachedListenerDoesNotReceiveAnAlreadyQueuedInstallOutcome() throws Exception {
        int session = pendingSession();
        RecordingListener listener = new RecordingListener();
        field(ApkUpdater.class, "pendingListener", listener);
        main(() -> {
            ApkUpdater.onInstallResult(context, session, PackageInstaller.STATUS_FAILURE_ABORTED, null);
            ApkUpdater.detachListener(listener);
        });
        instrumentation.waitForIdleSync();
        assertTrue("Destroyed Settings must not receive queued outcomes", listener.events.isEmpty());
        assertEquals(PackageInstaller.STATUS_FAILURE_ABORTED, UpdateInstallState.status(context));
    }

    @Test public void replacedListenerDoesNotReceiveAnAlreadyQueuedInstallOutcome() throws Exception {
        int session = pendingSession();
        RecordingListener previous = new RecordingListener();
        RecordingListener replacement = new RecordingListener();
        field(ApkUpdater.class, "pendingListener", previous);
        main(() -> {
            ApkUpdater.onInstallResult(context, session, PackageInstaller.STATUS_FAILURE_ABORTED, null);
            // An immediate retry takes ownership before the queued old outcome is delivered.
            ApkUpdater.startUpdate(context, null, replacement);
        });
        instrumentation.waitForIdleSync();
        assertTrue(previous.events.isEmpty());
        assertEquals(java.util.Collections.singletonList("failure:NO_APK"), replacement.events);
        assertEquals("A retry must not inherit its predecessor's terminal status",
                UpdateInstallState.NONE, UpdateInstallState.status(context));
    }

    @Test public void detachedListenerDoesNotReceiveAnAlreadyQueuedPendingPrompt() throws Exception {
        pendingSession();
        RecordingListener listener = new RecordingListener();
        main(() -> {
            ApkUpdater.startUpdate(context, null, listener);
            ApkUpdater.detachListener(listener);
        });
        instrumentation.waitForIdleSync();
        assertTrue(listener.events.isEmpty());
    }
    @Test public void missingConfirmationAbandonsSessionAndPersistsFailure() throws Exception {
        int session = pendingSession();
        main(() -> new UpdateInstallReceiver().onReceive(context,
                result(session, PackageInstaller.STATUS_PENDING_USER_ACTION)));
        assertEquals(PackageInstaller.STATUS_FAILURE, UpdateInstallState.status(context));
        assertNull(installer.getSessionInfo(session));
    }

    @Test public void rejectedConfirmationAbandonsSessionAndPersistsFailure() throws Exception {
        int session = pendingSession();
        Context rejecting = new ContextWrapper(context) {
            @Override public void startActivity(Intent intent) {
                throw new ActivityNotFoundException("synthetic installer unavailable");
            }
        };
        main(() -> new UpdateInstallReceiver().onReceive(rejecting,
                result(session, PackageInstaller.STATUS_PENDING_USER_ACTION)
                        .putExtra(Intent.EXTRA_INTENT, new Intent("synthetic.confirm"))));
        assertEquals(PackageInstaller.STATUS_FAILURE, UpdateInstallState.status(context));
        assertNull(installer.getSessionInfo(session));
    }

    @Test public void confirmationIsLaunchedOnceAndForeignSessionCannotChangeState() throws Exception {
        int session = pendingSession();
        List<Intent> launched = new ArrayList<>();
        Context capturing = new ContextWrapper(context) {
            @Override public void startActivity(Intent intent) { launched.add(new Intent(intent)); }
        };
        main(() -> {
            new UpdateInstallReceiver().onReceive(capturing,
                    result(session + 1, PackageInstaller.STATUS_PENDING_USER_ACTION)
                            .putExtra(Intent.EXTRA_INTENT, new Intent("synthetic.foreign")));
            new UpdateInstallReceiver().onReceive(capturing,
                    result(session, PackageInstaller.STATUS_PENDING_USER_ACTION)
                            .putExtra(Intent.EXTRA_INTENT, new Intent("synthetic.confirm")));
        });
        assertEquals(1, launched.size());
        assertEquals("synthetic.confirm", launched.get(0).getAction());
        assertTrue((launched.get(0).getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, UpdateInstallState.status(context));
        assertNotNull(installer.getSessionInfo(session));
    }

    @Test public void cancelledHandoffNeverPersistsOrCommitsTheSession() throws Exception {
        int session = newSession();
        ApkUpdater.DownloadHandle handle = ownedHandle();
        ApkUpdater.cancel(handle);
        AtomicBoolean committed = new AtomicBoolean();
        assertFalse(ApkUpdater.handoffInstall(context, session, "9.0.0", handle,
                () -> committed.set(true)));
        assertFalse(committed.get());
        assertEquals(UpdateInstallState.NONE, UpdateInstallState.status(context));
    }

    @Test public void liveHandoffRejectsCancellationAndKeepsRetryOut() throws Exception {
        int session = newSession();
        ApkUpdater.DownloadHandle handle = ownedHandle();
        CountDownLatch reachedCommit = new CountDownLatch(1);
        CountDownLatch releaseCommit = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                assertTrue(ApkUpdater.handoffInstall(context, session, "9.0.0", handle, () -> {
                    reachedCommit.countDown();
                    try { assertTrue(releaseCommit.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                }));
            } catch (Throwable failure) { error.set(failure); }
        });
        worker.start();
        try {
            assertTrue(reachedCommit.await(5, TimeUnit.SECONDS));
            assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, UpdateInstallState.status(context));
            assertNotNull(installer.getSessionInfo(session));
            RecordingListener retry = new RecordingListener();
            main(() -> {
                ApkUpdater.cancel(handle);
                ApkUpdater.startUpdate(context, null, retry);
            });
            instrumentation.waitForIdleSync();
            assertFalse("Cancellation cannot revoke an already claimed handoff", handle.isCancelled());
            assertEquals(java.util.Collections.singletonList("prompt"), retry.events);
            assertEquals(session, UpdateInstallState.session(context));
        } finally {
            releaseCommit.countDown();
            worker.join(5000L);
        }
        assertFalse(worker.isAlive());
        if (error.get() != null) throw new AssertionError(error.get());
        assertEquals(-1, ((Integer) field(ApkUpdater.class, "handoffSession")).intValue());
    }

    @Test public void failedCommitReleasesOwnershipAndAnImmediateRetryCanStart() throws Exception {
        int session = newSession();
        ApkUpdater.DownloadHandle handle = ownedHandle();
        RuntimeException expected = new IllegalStateException("synthetic commit failure");
        try {
            ApkUpdater.handoffInstall(context, session, "9.0.0", handle, () -> { throw expected; });
            fail("Commit failure must propagate to the download failure handler");
        } catch (RuntimeException actual) {
            assertSame(expected, actual);
        }
        assertEquals(-1, ((Integer) field(ApkUpdater.class, "handoffSession")).intValue());
        assertEquals(PackageInstaller.STATUS_FAILURE, UpdateInstallState.status(context));
        assertNull(installer.getSessionInfo(session));
        RecordingListener retry = new RecordingListener();
        main(() -> ApkUpdater.startUpdate(context, null, retry));
        instrumentation.waitForIdleSync();
        assertEquals(java.util.Collections.singletonList("failure:NO_APK"), retry.events);
        assertEquals(UpdateInstallState.NONE, UpdateInstallState.status(context));
    }

    @Test public void restoredUnsealedSessionIsAbandonedAndSettingsAllowsRetry() throws Exception {
        int session = newSession();
        assertTrue(UpdateInstallState.begin(context, session, "9.0.0"));
        assertFalse(installer.getSessionInfo(session).isSealed());
        // No live owner survives process death between durable begin and commit.
        activity = openSettings();
        Button button = (Button) field(activity, "downloadUpdateButton");
        TextView status = (TextView) field(activity, "updateStatus");
        main(() -> {
            assertTrue(button.isEnabled());
            assertEquals(context.getString(R.string.download_update, "9.0.0"), button.getText().toString());
            assertEquals(context.getString(R.string.update_install_failed), status.getText().toString());
        });
        assertNull(installer.getSessionInfo(session));
        assertEquals(UpdateInstallState.NONE, UpdateInstallState.status(context));
    }

    private ApkUpdater.DownloadHandle ownedHandle() throws Exception {
        ApkUpdater.DownloadHandle handle = new ApkUpdater.DownloadHandle();
        field(ApkUpdater.class, "active", handle);
        field(ApkUpdater.class, "latest", handle);
        return handle;
    }

    private Activity openSettings() {
        Activity settings = instrumentation.startActivitySync(new Intent(context, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK));
        activities.add(settings);
        return settings;
    }

    private void holdDownloadWorker() throws Exception {
        // Hold only the network boundary; the real button and startUpdate path execute.
        // The disallowed URL guarantees no network even when cleanup releases the worker.
        prefs().edit().putString("update_apk_url", "https://not-allowed.invalid/update.apk").commit();
        CountDownLatch started = new CountDownLatch(1);
        releaseDownload = new CountDownLatch(1);
        ((ExecutorService) field(ApkUpdater.class, "EXECUTOR")).execute(() -> {
            started.countDown();
            try { releaseDownload.await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
    }
    private int pendingSession() throws Exception {
        int session = newSession();
        // Explicitly model a live worker, not an orphan restored after process death.
        field(ApkUpdater.class, "handoffSession", session);
        assertTrue(UpdateInstallState.begin(context, session, "9.0.0"));
        return session;
    }

    private int newSession() throws Exception {
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int session = installer.createSession(params);
        sessions.add(session);
        return session;
    }

    private static Intent result(int session, int status) {
        return new Intent(UpdateInstallReceiver.ACTION_STATUS)
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, session)
                .putExtra(PackageInstaller.EXTRA_STATUS, status);
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE);
    }

    private void main(Runnable action) { instrumentation.runOnMainSync(action); }

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target instanceof Class ? null : target);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target instanceof Class ? null : target, value);
    }

    private static final class RecordingListener implements ApkUpdater.Listener {
        final List<String> events = new ArrayList<>();
        @Override public void onProgress(long downloaded, long total) { events.add("progress"); }
        @Override public void onFailure(UpdateChecker.Failure failure) { events.add("failure:" + failure); }
        @Override public void onCancelled() { events.add("cancelled"); }
        @Override public void onInstallPrompt() { events.add("prompt"); }
        @Override public void onInstalled() { events.add("installed"); }
    }
}

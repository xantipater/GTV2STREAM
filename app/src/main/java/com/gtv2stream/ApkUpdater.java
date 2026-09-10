package com.gtv2stream;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-tap in-app update: downloads the stable release APK resolved by
 * {@link UpdateChecker} and hands it to the Android system installer.
 *
 * <p>There are deliberately no silent installs: the APK is committed to a
 * {@link PackageInstaller} full-install session for our own package, so the
 * system always shows its own user-confirmed install prompt. The update never
 * proceeds without the user tapping "Download &amp; install" in Settings and
 * then confirming in the system installer UI.
 */
final class ApkUpdater {
    private static final String TAG = "GTV2STREAM";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int MAX_REDIRECTS = 5;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    // Lazily created (never as a static initializer) so the dependency-free JVM
    // helper-test harness can load this class to exercise pure-Java helpers.
    private static volatile Handler mainHandler;
    private static Handler main() {
        Handler handler = mainHandler;
        if (handler == null) {
            synchronized (ApkUpdater.class) {
                handler = mainHandler;
                if (handler == null) {
                    handler = new Handler(Looper.getMainLooper());
                    mainHandler = handler;
                }
            }
        }
        return handler;
    }
    private static volatile Listener pendingListener;
    private static volatile DownloadHandle active;

    interface Listener {
        void onProgress(long downloadedBytes, long totalBytes);
        void onFailure(UpdateChecker.Failure failure);
        void onCancelled();
        /** The session was committed; the system install-confirmation UI is next. */
        void onInstallPrompt();
        void onInstalled();
    }

    /** Cancels an in-flight download; the partial file is deleted. */
    static final class DownloadHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        void cancel() { cancelled.set(true); }
        boolean isCancelled() { return cancelled.get(); }
    }

    private ApkUpdater() { }

    static boolean canInstallUnknownApps(Context context) {
        try {
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Conservative offline classification from an I/O failure, without any
     * extra permission: DNS failures mean no usable route; everything else is
     * reported as a generic network failure. There is no pre-flight
     * connectivity check (it would need ACCESS_NETWORK_STATE); the download
     * itself is the probe.
     */
    private static boolean looksOffline(IOException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.UnknownHostException) return true;
        }
        return false;
    }

    /**
     * Starts downloading {@code info}'s APK and, once verified, commits it to
     * the system installer. Any previous in-flight update is cancelled first.
     * Returns a handle the caller can use for cancellation.
     */
    static synchronized DownloadHandle startUpdate(Context context, UpdateChecker.UpdateInfo info,
            Listener listener) {
        cancelActive();
        final DownloadHandle handle = new DownloadHandle();
        active = handle;
        pendingListener = listener;
        final Context appContext = context.getApplicationContext();
        if (info == null || info.apkUrl == null) {
            postFailure(handle, listener, UpdateChecker.Failure.NO_APK);
            return handle;
        }
        // No offline pre-check on purpose: the download itself is the probe, so no
        // extra permission (ACCESS_NETWORK_STATE) is needed. DNS/connect failures
        // are classified as OFFLINE at catch time instead.
        EXECUTOR.execute(() -> runDownload(appContext, info, handle, listener));
        return handle;
    }

    static synchronized void cancelActive() {
        if (active != null) active.cancel();
        active = null;
    }

    /** Called by {@link UpdateInstallReceiver} with the system's install-session result. */
    static void onInstallResult(int status, String message) {
        final Listener listener = pendingListener;
        if (listener == null) return;
        pendingListener = null;
        active = null;
        switch (status) {
            case PackageInstaller.STATUS_SUCCESS:
                main().post(listener::onInstalled);
                break;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                main().post(() -> listener.onFailure(UpdateChecker.Failure.INSTALL_CANCELLED));
                break;
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                main().post(() -> listener.onFailure(UpdateChecker.Failure.UNKNOWN_SOURCES));
                break;
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // The receiver launches the system confirmation activity itself;
                // the final result arrives here afterwards.
                pendingListener = listener;
                break;
            default:
                Log.w(TAG, "Update install failed: " + message);
                main().post(() -> listener.onFailure(UpdateChecker.Failure.INSTALL_FAILED));
                break;
        }
    }

    private static void runDownload(Context context, UpdateChecker.UpdateInfo info,
            DownloadHandle handle, Listener listener) {
        File apk = cacheFile(context, info.version);
        deleteStale(context, apk);
        // Checked BEFORE the transfer, not after it. Downloading the whole APK
        // only to then say "go and allow installs" wastes the user's time and
        // strands them at a dead end, which is the worst version of this flow.
        // The same check after the download stays as the final guard.
        if (!canInstallUnknownApps(context)) {
            postFailure(handle, listener, UpdateChecker.Failure.UNKNOWN_SOURCES);
            return;
        }
        HttpURLConnection connection = null;
        try {
            connection = openAllowedConnection(info.apkUrl);
            if (connection == null) {
                postFailure(handle, listener, UpdateChecker.Failure.NETWORK);
                return;
            }
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                postFailure(handle, listener, UpdateChecker.Failure.NETWORK);
                return;
            }
            long total = info.apkSize >= 0L ? info.apkSize : connection.getContentLengthLong();
            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(apk)) {
                byte[] buffer = new byte[64 * 1024];
                long downloaded = 0L;
                int read;
                long lastPosted = 0L;
                while ((read = in.read(buffer)) != -1) {
                    if (handle.isCancelled()) {
                        deleteQuietly(apk);
                        postCancelled(handle, listener);
                        return;
                    }
                    out.write(buffer, 0, read);
                    downloaded += read;
                    if (downloaded - lastPosted >= 256 * 1024L) {
                        lastPosted = downloaded;
                        postProgress(handle, listener, downloaded, total);
                    }
                }
                out.flush();
                postProgress(handle, listener, downloaded, total);
            }
            if (handle.isCancelled()) {
                deleteQuietly(apk);
                postCancelled(handle, listener);
                return;
            }
            if (!UpdateChecker.isValidApkDownload(apk, info.apkSize)) {
                deleteQuietly(apk);
                postFailure(handle, listener, UpdateChecker.Failure.CORRUPT);
                return;
            }
            if (!canInstallUnknownApps(context)) {
                deleteQuietly(apk);
                postFailure(handle, listener, UpdateChecker.Failure.UNKNOWN_SOURCES);
                return;
            }
            commitInstall(context, apk, handle, listener);
        } catch (SocketTimeoutException timeout) {
            deleteQuietly(apk);
            postFailure(handle, listener, UpdateChecker.Failure.TIMEOUT);
        } catch (IOException network) {
            deleteQuietly(apk);
            postFailure(handle, listener, looksOffline(network)
                    ? UpdateChecker.Failure.OFFLINE : UpdateChecker.Failure.NETWORK);
        } catch (Exception error) {
            Log.w(TAG, "Update failed: " + error.getMessage());
            deleteQuietly(apk);
            postFailure(handle, listener, UpdateChecker.Failure.NETWORK);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Opens the download connection, following redirects only while every hop
     * stays on https GitHub release infrastructure. Anything else fails closed.
     */
    static HttpURLConnection openAllowedConnection(String url) throws IOException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!isAllowedDownloadUrl(current)) return null;
            HttpURLConnection connection =
                    (HttpURLConnection) new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307 || code == 308) {
                String next = connection.getHeaderField("Location");
                connection.disconnect();
                if (next == null) return null;
                current = next;
                continue;
            }
            return connection;
        }
        return null;
    }

    /** Seed URL plus redirect targets must be https GitHub release hosts. */
    static boolean isAllowedDownloadUrl(String url) {
        if (url == null || !url.startsWith("https://")) return false;
        String host = url.substring("https://".length());
        int slash = host.indexOf('/');
        host = (slash < 0 ? host : host.substring(0, slash)).toLowerCase(Locale.US);
        return host.equals("github.com")
                || host.endsWith(".githubusercontent.com")
                || host.equals("githubusercontent.com");
    }

    private static void commitInstall(Context context, File apk, DownloadHandle handle,
            Listener listener) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId);
             InputStream in = new FileInputStream(apk)) {
            byte[] buffer = new byte[64 * 1024];
            long written = 0L;
            int read;
            try (OutputStream out = session.openWrite("update", 0L, apk.length())) {
                while ((read = in.read(buffer)) != -1) {
                    if (handle.isCancelled()) {
                        session.abandon();
                        deleteQuietly(apk);
                        postCancelled(handle, listener);
                        return;
                    }
                    out.write(buffer, 0, read);
                    written += read;
                }
                out.flush();
                session.fsync(out);
            }
            if (written != apk.length()) {
                session.abandon();
                deleteQuietly(apk);
                postFailure(handle, listener, UpdateChecker.Failure.CORRUPT);
                return;
            }
            Intent result = new Intent(context, UpdateInstallReceiver.class)
                    .setAction(UpdateInstallReceiver.ACTION_STATUS);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, result, flags);
            // Committing hands control to the system installer UI; the user must
            // still confirm before anything is installed. Nothing is silent here.
            session.commit(pending.getIntentSender());
            deleteQuietly(apk);
            main().post(() -> { if (!handle.isCancelled()) listener.onInstallPrompt(); });
        } catch (IOException | RuntimeException error) {
            try { installer.abandonSession(sessionId); } catch (Exception ignored) { }
            throw error instanceof IOException ? (IOException) error : new IOException(error);
        }
    }

    private static File cacheFile(Context context, String version) {
        String safe = version == null ? "unknown" : version.replaceAll("[^A-Za-z0-9._-]", "_");
        File dir = new File(context.getCacheDir(), "updates");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "gtv2stream-" + safe + ".apk");
    }

    private static void deleteStale(Context context, File keep) {
        File dir = new File(context.getCacheDir(), "updates");
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.equals(keep)) deleteQuietly(file);
        }
    }

    private static void deleteQuietly(File file) {
        try { if (file != null) //noinspection ResultOfMethodCallIgnored
            file.delete(); } catch (Exception ignored) { }
    }

    private static void postProgress(DownloadHandle handle, Listener listener,
            long downloaded, long total) {
        main().post(() -> { if (!handle.isCancelled()) listener.onProgress(downloaded, total); });
    }

    private static void postFailure(DownloadHandle handle, Listener listener,
            UpdateChecker.Failure failure) {
        synchronized (ApkUpdater.class) {
            if (active == handle) active = null;
            if (pendingListener == listener) pendingListener = null;
        }
        main().post(() -> listener.onFailure(failure));
    }

    private static void postCancelled(DownloadHandle handle, Listener listener) {
        synchronized (ApkUpdater.class) {
            if (active == handle) active = null;
            if (pendingListener == listener) pendingListener = null;
        }
        main().post(listener::onCancelled);
    }
}

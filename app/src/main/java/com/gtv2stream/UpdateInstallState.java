package com.gtv2stream;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;

/** Small durable record for the system-installer handoff, including process replacement. */
final class UpdateInstallState {
    private static final String SESSION = "install_session";
    private static final String VERSION = "install_version";
    static final String STATUS = "install_status";
    static final int NONE = Integer.MIN_VALUE;
    private UpdateInstallState() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE);
    }

    /** Must be durable before commit can transfer execution to another process. */
    static boolean begin(Context context, int session, String version) {
        return prefs(context).edit().putInt(SESSION, session).putString(VERSION, version)
                .putInt(STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION).commit();
    }

    static int session(Context context) { return prefs(context).getInt(SESSION, -1); }

    static boolean record(Context context, int session, int status) {
        if (session < 0 || session != session(context)) return false;
        // Main-thread preference observers can run inside commit(), clear the
        // outcome, and re-enter Settings. Publish terminal state only after its
        // live handoff owner is gone, so that re-entry cannot restore Pending.
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) ApkUpdater.releaseHandoff(session);
        return prefs(context).edit().putInt(STATUS, status).commit();
    }

    static int status(Context context) {
        SharedPreferences prefs = prefs(context);
        int status = prefs.getInt(STATUS, NONE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            String expected = prefs.getString(VERSION, "");
            String current = UpdateChecker.currentVersion(context);
            // Self-update can replace our process before its success callback.
            if (UpdateChecker.isInstalledAtLeast(expected, current)) {
                record(context, session(context), PackageInstaller.STATUS_SUCCESS);
                return PackageInstaller.STATUS_SUCCESS;
            }
            int sessionId = session(context);
            // begin() precedes commit(). A live worker owns that short unsealed
            // interval; after process death no worker can ever finish it.
            if (ApkUpdater.isHandoffInProgress(sessionId)) return status;
            try {
                PackageInstaller installer = context.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionInfo info = installer.getSessionInfo(sessionId);
                if (info == null || !info.isSealed()) {
                    if (info != null) installer.abandonSession(sessionId);
                    record(context, sessionId, PackageInstaller.STATUS_FAILURE);
                    return PackageInstaller.STATUS_FAILURE;
                }
            } catch (RuntimeException unavailable) {
                // Keep pending; an unavailable session service is not proof of failure.
            }
        }
        return status;
    }

    static void clear(Context context) {
        prefs(context).edit().remove(SESSION).remove(VERSION).remove(STATUS).apply();
    }
}

package com.gtv2stream;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

/**
 * Receives the PackageInstaller session result for one-tap in-app updates and
 * forwards it to {@link ApkUpdater}. If the installer needs the user to
 * confirm (pending user action), the system-supplied confirmation activity is
 * launched; on TV this renders as the standard full-screen install prompt,
 * which is D-pad navigable.
 */
public final class UpdateInstallReceiver extends BroadcastReceiver {
    static final String ACTION_STATUS = "com.gtv2stream.UPDATE_INSTALL_STATUS";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_STATUS.equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                try {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                } catch (Exception error) {
                    Log.w("GTV2STREAM", "Update confirmation UI unavailable: " + error.getMessage());
                    ApkUpdater.onInstallResult(PackageInstaller.STATUS_FAILURE, "confirm unavailable");
                    return;
                }
            }
            ApkUpdater.onInstallResult(status, null);
            return;
        }
        ApkUpdater.onInstallResult(status,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
    }
}

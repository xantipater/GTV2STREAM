package com.gtv2stream;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Set;

public final class SettingsActivity extends Activity {
    private TextView status;
    private EditText keyField;
    private Button moviesTargetButton;
    private Button youtubeTargetButton;
    private Button movieTestButton;
    private Button youtubeTestButton;
    private Button badgeToggleButton;
    private Button appInfoButton;
    private TextView whitelistSummary;
    private LinearLayout whitelistRows;
    private TextView updateNotice;
    private Button updateButton;
    private Button downloadUpdateButton;
    private TextView updateStatus;
    private UpdateChecker.UpdateInfo pendingUpdate;
    private ApkUpdater.DownloadHandle updateDownload;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) updateStatus();
        if (moviesTargetButton != null) refreshTargetButtons();
        refreshWhitelistRows();
        refreshTestButtons();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.rgb(16, 19, 26));
        getWindow().setNavigationBarColor(Color.rgb(16, 19, 26));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(56), dp(30), dp(56), dp(30));
        root.setBackgroundColor(Color.rgb(16, 19, 26));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        ImageView wordmark = new ImageView(this);
        wordmark.setImageResource(R.drawable.gtv2stream_logo);
        wordmark.setContentDescription(getString(R.string.app_name));
        wordmark.setAdjustViewBounds(true);
        wordmark.setFocusable(false);
        wordmark.setFocusableInTouchMode(false);
        LinearLayout.LayoutParams wordmarkParams = lp(-1, dp(96), 0, 0, 0, 10);
        wordmarkParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(wordmark, wordmarkParams);
        TextView subtitle = text(getString(R.string.app_subtitle), 17, Color.rgb(183, 192, 208));
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, 22));

        status = text("", 18, Color.rgb(123, 228, 149));
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setPadding(dp(18), dp(14), dp(18), dp(14));
        status.setBackgroundColor(Color.rgb(28, 35, 48));
        root.addView(status, lp(-1, -2, 0, 0, 0, 22));

        updateNotice = text("", 18, Color.rgb(255, 209, 102));
        updateNotice.setVisibility(View.GONE);
        root.addView(updateNotice, lp(-1, -2, 0, 0, 0, 7));
        updateButton = button(getString(R.string.open_update));
        updateButton.setVisibility(View.GONE);
        root.addView(updateButton, lp(-1, dp(58), 0, 0, 0, 12));
        downloadUpdateButton = button("");
        downloadUpdateButton.setVisibility(View.GONE);
        root.addView(downloadUpdateButton, lp(-1, dp(58), 0, 0, 0, 12));
        updateStatus = text("", 16, Color.rgb(183, 192, 208));
        updateStatus.setVisibility(View.GONE);
        root.addView(updateStatus, lp(-1, -2, 0, 0, 0, 12));

        TextView keyLabel = text(getString(R.string.tmdb_key_label), 18, Color.WHITE);
        root.addView(keyLabel, lp(-1, -2, 0, 0, 0, 7));
        keyField = new EditText(this);
        keyField.setSingleLine(true);
        keyField.setTextSize(18);
        keyField.setTextColor(Color.WHITE);
        keyField.setHintTextColor(Color.rgb(150, 160, 175));
        keyField.setHint(getString(R.string.tmdb_key_hint));
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setSelectAllOnFocus(false);
        keyField.setText(getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
                .getString(AppPrefs.TMDB_KEY, ""));
        root.addView(keyField, lp(-1, dp(58), 0, 0, 0, 8));

        Button save = button(getString(R.string.save_key));
        save.setOnClickListener(v -> saveKey());
        root.addView(save, lp(-1, dp(58), 0, 0, 0, 18));

        moviesTargetButton = button(getString(R.string.target_movies_label, currentMoviesTargetName()));
        moviesTargetButton.setOnClickListener(v -> cycleMoviesTarget());
        root.addView(moviesTargetButton, lp(-1, dp(58), 0, 0, 0, 12));
        youtubeTargetButton = button(getString(R.string.target_youtube_label, currentYoutubeTargetName()));
        youtubeTargetButton.setOnClickListener(v -> cycleYoutubeTarget());
        root.addView(youtubeTargetButton, lp(-1, dp(58), 0, 0, 0, 12));

        Button accessibility = button(getString(R.string.open_accessibility));
        accessibility.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Exception error) { Toast.makeText(this, "Accessibility Settings unavailable", Toast.LENGTH_LONG).show(); }
        });
        root.addView(accessibility, lp(-1, dp(58), 0, 0, 0, 12));

        movieTestButton = button(testMovieLabel());
        movieTestButton.setOnClickListener(v -> runMovieTest());
        root.addView(movieTestButton, lp(-1, dp(58), 0, 0, 0, 12));

        youtubeTestButton = button(testYoutubeLabel());
        youtubeTestButton.setOnClickListener(v -> runYoutubeTest());
        root.addView(youtubeTestButton, lp(-1, dp(58), 0, 0, 0, 12));

        badgeToggleButton = button(getString(R.string.badge_toggle_label, badgeStateName()));
        badgeToggleButton.setOnClickListener(v -> cycleBadge());
        root.addView(badgeToggleButton, lp(-1, dp(58), 0, 0, 0, 12));

        whitelistSummary = text(getString(R.string.whitelist_label, whitelistSummaryText()), 18, Color.WHITE);
        root.addView(whitelistSummary, lp(-1, -2, 0, 0, 0, 7));
        whitelistRows = new LinearLayout(this);
        whitelistRows.setOrientation(LinearLayout.VERTICAL);
        root.addView(whitelistRows, lp(-1, -2, 0, 0, 0, 4));
        buildWhitelistRows();
        TextView whitelistNote = text(getString(R.string.whitelist_note), 15, Color.rgb(183, 192, 208));
        root.addView(whitelistNote, lp(-1, -2, 0, 0, 0, 12));

        appInfoButton = button(getString(R.string.open_app_info));
        appInfoButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception error) {
                Toast.makeText(this, "App info unavailable", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(appInfoButton, lp(-1, dp(58), 0, 0, 0, 12));
        appInfoButton.setVisibility(View.GONE);

        if (!Settings.canDrawOverlays(this)) {
            Button overlay = button(getString(R.string.enable_overlay));
            overlay.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception error) {
                    Toast.makeText(this, "Overlay permission screen unavailable",
                            Toast.LENGTH_LONG).show();
                }
            });
            root.addView(overlay, lp(-1, dp(58), 0, 0, 0, 12));
        }

        Button help = button(getString(R.string.open_setup_help));
        help.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));
        root.addView(help, lp(-1, dp(58), 0, 0, 0, 12));

        TextView note = text(getString(R.string.settings_note), 15, Color.rgb(183, 192, 208));
        note.setLineSpacing(0, 1.15f);
        root.addView(note, lp(-1, -2, 0, 0, 0, 12));
        TextView versionFooter = text(getString(R.string.version_footer, appVersionName()), 14,
                Color.rgb(130, 140, 155));
        versionFooter.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(versionFooter, lp(-1, -2, 0, 0, 0, 0));
        setContentView(scroll);
        refreshTargetButtons();
        refreshTestButtons();
        UpdateChecker.check(this, this::showUpdate);
    }

    private void showUpdate(UpdateChecker.UpdateInfo info) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())
                || updateNotice == null || updateButton == null) return;
        pendingUpdate = info;
        updateNotice.setText(getString(R.string.update_available, info.version));
        updateNotice.setVisibility(View.VISIBLE);
        updateButton.setVisibility(View.VISIBLE);
        updateButton.setOnClickListener(v -> openReleasePage(info));
        if (downloadUpdateButton != null) {
            if (info.apkUrl != null) {
                downloadUpdateButton.setText(getString(R.string.download_update, info.version));
                downloadUpdateButton.setOnClickListener(v -> startOneTapUpdate());
                downloadUpdateButton.setVisibility(View.VISIBLE);
            } else {
                downloadUpdateButton.setVisibility(View.GONE);
            }
        }
        maybePromptForUpdate(info);
    }

    /** Opens the release page in the system browser. */
    private void openReleasePage(UpdateChecker.UpdateInfo info) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl))); }
        catch (Exception ignored) { }
    }

    /**
     * Asks the user to update, once per release. Opening the app is the only
     * surface this app has, so this is the one moment a user can be asked; the
     * inline notice and its buttons stay visible for anyone who taps Later,
     * which keeps the prompt from becoming a nag.
     */
    private void maybePromptForUpdate(UpdateChecker.UpdateInfo info) {
        if (info == null || info.version == null || info.version.isEmpty()) return;
        android.content.SharedPreferences prefs = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE);
        if (info.version.equals(prefs.getString(AppPrefs.UPDATE_PROMPTED_VERSION, ""))) return;
        prefs.edit().putString(AppPrefs.UPDATE_PROMPTED_VERSION, info.version).apply();
        final boolean canOneTap = info.apkUrl != null;
        AlertDialog.Builder prompt = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_available, info.version))
                .setMessage(getString(canOneTap
                        ? R.string.update_prompt_message : R.string.update_prompt_page_message))
                .setNegativeButton(getString(R.string.update_later), null);
        if (canOneTap) {
            prompt.setPositiveButton(getString(R.string.download_update, info.version),
                    (dialog, which) -> startOneTapUpdate());
            prompt.setNeutralButton(getString(R.string.open_update),
                    (dialog, which) -> openReleasePage(info));
        } else {
            prompt.setPositiveButton(getString(R.string.open_update),
                    (dialog, which) -> openReleasePage(info));
        }
        prompt.show();
    }

    /**
     * Android needs "Install unknown apps" allowed before it will accept the
     * package. Say where to go and take them there: on a TV that settings tree is
     * buried deep enough that instructional text alone strands people.
     */
    private void showAllowInstallsAction() {
        if (updateButton == null) return;
        updateButton.setText(R.string.update_allow_installs);
        updateButton.setOnClickListener(v -> openUnknownSourcesSettings());
        updateButton.setVisibility(View.VISIBLE);
    }

    /** This app's "install unknown apps" page, with an app-details fallback. */
    private void openUnknownSourcesSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception unavailable) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
        }
    }

    private void startOneTapUpdate() {
        if (pendingUpdate == null || pendingUpdate.apkUrl == null) {
            setUpdateStatus(getString(R.string.update_no_apk));
            return;
        }
        downloadUpdateButton.setText(getString(R.string.cancel_download));
        downloadUpdateButton.setOnClickListener(v -> cancelOneTapUpdate());
        setUpdateStatus(getString(R.string.update_download_unknown));
        updateDownload = ApkUpdater.startUpdate(this, pendingUpdate, new ApkUpdater.Listener() {
            @Override public void onProgress(long downloaded, long total) {
                runOnUiThread(() -> {
                    if (total > 0L) {
                        int percent = (int) Math.min(100L, (downloaded * 100L) / total);
                        setUpdateStatus(getString(R.string.update_downloading, percent));
                    } else {
                        setUpdateStatus(getString(R.string.update_download_unknown));
                    }
                });
            }

            @Override public void onFailure(UpdateChecker.Failure failure) {
                runOnUiThread(() -> {
                    resetDownloadButton();
                    setUpdateStatus(updateFailureText(failure));
                    // The one failure the user can actually fix gets an action,
                    // not just an explanation.
                    if (failure == UpdateChecker.Failure.UNKNOWN_SOURCES) {
                        showAllowInstallsAction();
                    }
                });
            }

            @Override public void onCancelled() {
                runOnUiThread(() -> {
                    resetDownloadButton();
                    setUpdateStatus(getString(R.string.update_cancelled));
                });
            }

            @Override public void onInstallPrompt() {
                runOnUiThread(() -> {
                    resetDownloadButton();
                    setUpdateStatus(getString(R.string.update_confirm_install));
                });
            }

            @Override public void onInstalled() {
                runOnUiThread(() -> {
                    resetDownloadButton();
                    setUpdateStatus(getString(R.string.update_installed));
                });
            }
        });
    }

    private void cancelOneTapUpdate() {
        ApkUpdater.cancelActive();
        updateDownload = null;
        resetDownloadButton();
        setUpdateStatus(getString(R.string.update_cancelled));
    }

    private void resetDownloadButton() {
        updateDownload = null;
        if (downloadUpdateButton != null && pendingUpdate != null && pendingUpdate.apkUrl != null) {
            downloadUpdateButton.setText(getString(R.string.download_update, pendingUpdate.version));
            downloadUpdateButton.setOnClickListener(v -> startOneTapUpdate());
            downloadUpdateButton.setVisibility(View.VISIBLE);
        } else if (downloadUpdateButton != null) {
            downloadUpdateButton.setVisibility(View.GONE);
        }
    }

    private String updateFailureText(UpdateChecker.Failure failure) {
        if (failure == null) return getString(R.string.update_network_error);
        switch (failure) {
            case OFFLINE: return getString(R.string.update_offline);
            case TIMEOUT: return getString(R.string.update_timeout);
            case CORRUPT: return getString(R.string.update_corrupt);
            case NO_APK: return getString(R.string.update_no_apk);
            case UNKNOWN_SOURCES: return getString(R.string.update_unknown_sources);
            case INSTALL_CANCELLED: return getString(R.string.update_install_cancelled);
            case INSTALL_FAILED: return getString(R.string.update_install_failed);
            case NETWORK:
            default: return getString(R.string.update_network_error);
        }
    }

    private void setUpdateStatus(String message) {
        if (updateStatus == null) return;
        updateStatus.setText(message);
        updateStatus.setVisibility(View.VISIBLE);
    }

    @Override protected void onDestroy() {
        ApkUpdater.cancelActive();
        super.onDestroy();
    }

    private void cycleMoviesTarget() {
        String next = LaunchPolicy.nextMoviesTarget(AppPrefs.moviesTarget(this));
        getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit()
                .putString(AppPrefs.TARGET_MOVIES, next).apply();
        refreshTargetButtons();
        refreshTestButtons();
    }

    /** Runs the TV/movie test for the active target and reports the real outcome. */
    private void runMovieTest() {
        String target = AppPrefs.moviesTarget(this);
        String targetName = currentMoviesTargetName();
        if (AppPrefs.MOVIES_STREMIO.equals(target)) {
            StremioLauncher.openTest(this, new StremioLauncher.TestCallback() {
                @Override public void onMissingTarget() {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                            getString(R.string.status_stremio_missing), Toast.LENGTH_LONG).show());
                }
                @Override public void onResult(boolean opened) {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                            getString(opened ? R.string.test_link_opened : R.string.test_link_failed,
                                    targetName),
                            opened ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show());
                }
            });
        } else if (AppPrefs.MOVIES_WUPLAY.equals(target)) {
            WuPlayLauncher.openTest(this, new WuPlayLauncher.TestCallback() {
                @Override public void onMissingTarget() {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                            getString(R.string.status_wuplay_missing), Toast.LENGTH_LONG).show());
                }
                @Override public void onResult(boolean opened) {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                            getString(opened ? R.string.test_link_opened : R.string.test_link_failed,
                                    targetName),
                            opened ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show());
                }
            });
        } else {
            NuvioLauncher.openTest(this, new NuvioLauncher.TestCallback() {
                @Override public void onMissingTarget() {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                            getString(R.string.status_nuvio_missing), Toast.LENGTH_LONG).show());
                }
                @Override public void onResult(boolean opened) {
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                            getString(opened ? R.string.test_link_opened : R.string.test_link_failed,
                                    targetName),
                            opened ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    /** Runs the YouTube test for the active target and reports the real outcome. */
    private void runYoutubeTest() {
        String targetName = currentYoutubeTargetName();
        boolean tizentube = YouTubeTarget.isTizenTube(AppPrefs.youtubeTarget(this));
        YouTubeLauncher.openTest(this, new YouTubeLauncher.TestCallback() {
            @Override public void onMissingTarget() {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        getString(tizentube ? R.string.status_tizentube_missing
                                : R.string.status_smarttube_missing),
                        Toast.LENGTH_LONG).show());
            }
            @Override public void onResult(boolean opened) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this,
                        getString(opened ? R.string.test_link_opened : R.string.test_link_failed,
                                targetName),
                        opened ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show());
            }
        });
    }

    private String testMovieLabel() {
        return getString(R.string.test_movie, currentMoviesTargetName());
    }

    private String testYoutubeLabel() {
        return getString(R.string.test_youtube, currentYoutubeTargetName());
    }

    private void refreshTestButtons() {
        if (movieTestButton != null) movieTestButton.setText(testMovieLabel());
        if (youtubeTestButton != null) youtubeTestButton.setText(testYoutubeLabel());
    }

    private void cycleBadge() {
        getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit()
                .putBoolean(AppPrefs.SHOW_BADGE, !AppPrefs.badgeEnabled(this)).apply();
        refreshTargetButtons();
    }

    private String badgeStateName() {
        return getString(AppPrefs.badgeEnabled(this)
                ? R.string.badge_on : R.string.badge_off);
    }

    /** Installed version name for the footer; unknown only if package lookup fails. */
    private String appVersionName() {
        try {
            String name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return name == null ? "?" : name;
        } catch (Exception ignored) {
            return "?";
        }
    }

    private void refreshTargetButtons() {
        if (moviesTargetButton != null) {
            moviesTargetButton.setText(getString(R.string.target_movies_label, currentMoviesTargetName()));
        }
        if (youtubeTargetButton != null) youtubeTargetButton.setText(
                getString(R.string.target_youtube_label, currentYoutubeTargetName()));
        if (badgeToggleButton != null) {
            badgeToggleButton.setText(getString(R.string.badge_toggle_label, badgeStateName()));
        }
    }

    private void cycleYoutubeTarget() {
        String next = AppPrefs.YOUTUBE_TIZENTUBE.equals(AppPrefs.youtubeTarget(this))
                ? AppPrefs.YOUTUBE_SMARTTUBE : AppPrefs.YOUTUBE_TIZENTUBE;
        getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit()
                .putString(AppPrefs.TARGET_YOUTUBE, next).apply();
        refreshTargetButtons();
        refreshTestButtons();
    }

    /** One focusable toggle button per known provider; D-pad navigates the list. */
    private void buildWhitelistRows() {
        if (whitelistRows == null) return;
        whitelistRows.removeAllViews();
        for (String providerId : RecommendationTitleParser.PROVIDER_ID_ORDER) {
            Button row = button("");
            row.setTag(providerId);
            row.setOnClickListener(v -> {
                Object tag = v.getTag();
                if (tag instanceof String) toggleWhitelisted((String) tag);
            });
            whitelistRows.addView(row, lp(-1, dp(58), 0, 0, 0, 8));
        }
        refreshWhitelistRows();
    }

    private void toggleWhitelisted(String providerId) {
        Set<String> next = ProviderWhitelist.toggled(AppPrefs.whitelist(this), providerId);
        AppPrefs.setWhitelist(this, next);
        refreshWhitelistRows();
    }

    private void refreshWhitelistRows() {
        Set<String> whitelist = null;
        try {
            whitelist = AppPrefs.whitelist(this);
        } catch (RuntimeException prefsError) {
            whitelist = ProviderWhitelist.parse(null);
        }
        if (whitelistSummary != null) {
            whitelistSummary.setText(getString(R.string.whitelist_label,
                    ProviderWhitelist.summary(whitelist)));
        }
        if (whitelistRows == null) return;
        for (int index = 0; index < whitelistRows.getChildCount(); index++) {
            View child = whitelistRows.getChildAt(index);
            if (!(child instanceof Button) || !(child.getTag() instanceof String)) continue;
            String providerId = (String) child.getTag();
            boolean on = ProviderWhitelist.contains(whitelist, providerId);
            ((Button) child).setText(getString(R.string.whitelist_toggle_label,
                    RecommendationTitleParser.providerDisplayName(providerId),
                    getString(on ? R.string.whitelist_on : R.string.whitelist_off)));
        }
    }

    private String whitelistSummaryText() {
        try {
            return ProviderWhitelist.summary(AppPrefs.whitelist(this));
        } catch (RuntimeException prefsError) {
            return getString(R.string.whitelist_none);
        }
    }

    private String currentYoutubeTargetName() {
        return getString(AppPrefs.YOUTUBE_TIZENTUBE.equals(AppPrefs.youtubeTarget(this))
                ? R.string.target_tizentube : R.string.target_smarttube);
    }

    private String currentMoviesTargetName() {
        String target = AppPrefs.moviesTarget(this);
        if (AppPrefs.MOVIES_STREMIO.equals(target)) return getString(R.string.target_stremio);
        if (AppPrefs.MOVIES_WUPLAY.equals(target)) return getString(R.string.target_wuplay);
        return getString(R.string.target_nuvio);
    }

    private void saveKey() {
        String key = keyField.getText().toString().trim();
        if (!key.isEmpty() && key.length() < 10) {
            Toast.makeText(this, R.string.invalid_key, Toast.LENGTH_LONG).show();
            return;
        }
        getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit()
                .putString(AppPrefs.TMDB_KEY, key).apply();
        Toast.makeText(this, R.string.key_saved, Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void updateStatus() {
        boolean enabled = serviceEnabledInSettings();
        boolean bound = isServiceEnabled();
        String savedKey = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
                .getString(AppPrefs.TMDB_KEY, "").trim();
        boolean hasKey = !savedKey.isEmpty();
        boolean connected = serviceActuallyConnected();
        boolean showAppInfoFix = false;
        if (enabled && bound && connected) {
            status.setText(getString(R.string.status_line, getString(R.string.service_status_title),
                    getString(R.string.status_enabled), getString(R.string.status_ready)));
            status.setTextColor(Color.rgb(123, 228, 149));
        } else if (enabled && (!bound || !connected)) {
            // Enabled in the accessibility settings, but the system never bound it:
            // vendor auto-start protection (seen on TCL) vetoes the service bind.
            status.setText(getString(R.string.status_line, getString(R.string.service_status_title),
                    getString(R.string.status_enabled),
                    tvBlocksAutoStart()
                            ? getString(R.string.status_service_blocked)
                            : getString(R.string.status_service_not_connected)));
            status.setTextColor(Color.rgb(255, 209, 102));
            showAppInfoFix = true;
        } else if (!enabled && !hasKey) {
            status.setText(getString(R.string.status_line, getString(R.string.service_status_title),
                    getString(R.string.status_disabled),
                    getString(R.string.status_key_missing)));
            status.setTextColor(Color.rgb(255, 209, 102));
        } else if (!enabled) {
            status.setText(getString(R.string.status_line_plain, getString(R.string.service_status_title),
                    getString(R.string.status_disabled)));
            status.setTextColor(Color.rgb(255, 209, 102));
        } else {
            status.setText(getString(R.string.status_line, getString(R.string.service_status_title),
                    getString(R.string.status_enabled),
                    getString(R.string.status_key_missing)));
            status.setTextColor(Color.rgb(255, 209, 102));
        }
        if (appInfoButton != null) {
            appInfoButton.setVisibility(showAppInfoFix ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Reads the raw accessibility settings value instead of AccessibilityManager:
     * when a vendor auto-start firewall (TCL) vetoes the bind, the service does not
     * appear in the manager's enabled list, but the user did enable it.
     */
    private boolean serviceEnabledInSettings() {
        String value = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (value == null || value.isEmpty()) return false;
        ComponentName expected = new ComponentName(this, TvRecommendationService.class);
        for (String part : value.split(":")) {
            ComponentName actual = ComponentName.unflattenFromString(part.trim());
            if (expected.equals(actual)) return true;
        }
        return false;
    }

    private boolean serviceActuallyConnected() {
        long at = getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE)
                .getLong(AppPrefs.SERVICE_CONNECTED_AT, 0L);
        // Fresh within twice the heartbeat interval; the service rewrites it every 15 s.
        return at > 0L && System.currentTimeMillis() - at < 60000L;
    }

    /**
     * True when a vendor auto-start firewall (TCL TclAppBoot on this class of TV)
     * vetoes the service bind. Other builds without the AUTO_START appop fall
     * through to the generic message.
     */
    private boolean tvBlocksAutoStart() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow("AUTO_START", Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED;
        } catch (RuntimeException unknownOp) {
            return false;
        }
    }

    private boolean isServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        ComponentName expected = new ComponentName(this, TvRecommendationService.class);
        for (AccessibilityServiceInfo info : services) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                ComponentName actual = new ComponentName(info.getResolveInfo().serviceInfo.packageName, info.getResolveInfo().serviceInfo.name);
                if (expected.equals(actual)) return true;
            }
        }
        return false;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setMinHeight(dp(54));
        b.setBackgroundResource(R.drawable.tv_button_background);
        b.setTextColor(getResources().getColorStateList(R.color.tv_button_text, null));
        return b;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams lp(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}

package com.gtv2stream;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public final class SettingsActivity extends Activity {
    private TextView status;
    private EditText keyField;
    private Button moviesTargetButton;
    private Button badgeToggleButton;
    private Button appInfoButton;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) updateStatus();
        if (moviesTargetButton != null) refreshTargetButtons();
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

        TextView title = text(getString(R.string.app_name), 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, lp(-1, -2, 0, 0, 0, 10));
        TextView subtitle = text(getString(R.string.app_subtitle), 17, Color.rgb(183, 192, 208));
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, 22));

        status = text("", 18, Color.rgb(123, 228, 149));
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setPadding(dp(18), dp(14), dp(18), dp(14));
        status.setBackgroundColor(Color.rgb(28, 35, 48));
        root.addView(status, lp(-1, -2, 0, 0, 0, 22));

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

        TextView targetsLabel = text(getString(R.string.target_movies_label, currentMoviesTargetName()), 18, Color.WHITE);
        root.addView(targetsLabel, lp(-1, -2, 0, 0, 0, 7));
        moviesTargetButton = button(getString(R.string.target_movies_label, currentMoviesTargetName()));
        moviesTargetButton.setOnClickListener(v -> cycleMoviesTarget());
        root.addView(moviesTargetButton, lp(-1, dp(58), 0, 0, 0, 12));

        badgeToggleButton = button(getString(R.string.badge_toggle_label, badgeStateName()));
        badgeToggleButton.setOnClickListener(v -> cycleBadge());
        root.addView(badgeToggleButton, lp(-1, dp(58), 0, 0, 0, 12));

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

        Button accessibility = button(getString(R.string.open_accessibility));
        accessibility.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Exception error) { Toast.makeText(this, "Accessibility Settings unavailable", Toast.LENGTH_LONG).show(); }
        });
        root.addView(accessibility, lp(-1, dp(58), 0, 0, 0, 12));

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

        Button movieTest = button(getString(R.string.test_movie));
        movieTest.setOnClickListener(v -> {
            boolean stremio = AppPrefs.MOVIES_STREMIO.equals(AppPrefs.moviesTarget(this));
            boolean opened = stremio ? StremioLauncher.openTest(this) : NuvioLauncher.openTest(this);
            if (opened) Toast.makeText(this, R.string.test_link_opened, Toast.LENGTH_SHORT).show();
        });
        root.addView(movieTest, lp(-1, dp(58), 0, 0, 0, 12));

        Button youtubeTest = button(getString(R.string.test_youtube));
        youtubeTest.setOnClickListener(v -> {
            boolean opened = YouTubeLauncher.openTest(this);
            if (opened) Toast.makeText(this, R.string.test_youtube_opened, Toast.LENGTH_SHORT).show();
        });
        root.addView(youtubeTest, lp(-1, dp(58), 0, 0, 0, 18));

        TextView note = text(getString(R.string.settings_note), 15, Color.rgb(183, 192, 208));
        note.setLineSpacing(0, 1.15f);
        root.addView(note, lp(-1, -2, 0, 0, 0, 0));
        setContentView(scroll);
        refreshTargetButtons();
    }

    private void cycleMoviesTarget() {
        boolean stremio = AppPrefs.MOVIES_STREMIO.equals(AppPrefs.moviesTarget(this));
        String next = stremio ? AppPrefs.MOVIES_NUVIO : AppPrefs.MOVIES_STREMIO;
        getSharedPreferences(AppPrefs.PREFS, MODE_PRIVATE).edit()
                .putString(AppPrefs.TARGET_MOVIES, next).apply();
        refreshTargetButtons();
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

    private void refreshTargetButtons() {
        if (moviesTargetButton != null) {
            moviesTargetButton.setText(getString(R.string.target_movies_label, currentMoviesTargetName()));
        }
        if (badgeToggleButton != null) {
            badgeToggleButton.setText(getString(R.string.badge_toggle_label, badgeStateName()));
        }
    }

    private String currentMoviesTargetName() {
        return getString(AppPrefs.MOVIES_STREMIO.equals(AppPrefs.moviesTarget(this))
                ? R.string.target_stremio : R.string.target_nuvio);
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
            status.setText(getString(R.string.status_line, getString(R.string.service_status_title),
                    getString(R.string.status_disabled), ""));
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

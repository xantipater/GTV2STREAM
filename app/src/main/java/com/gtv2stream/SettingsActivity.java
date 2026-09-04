package com.gtv2stream;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
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
    private static final String PREFS = "gtv2stream";
    private static final String KEY = "tmdb_key";
    private TextView status;
    private EditText keyField;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) updateStatus();
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
        keyField.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY, ""));
        root.addView(keyField, lp(-1, dp(58), 0, 0, 0, 8));

        Button save = button(getString(R.string.save_key));
        save.setOnClickListener(v -> saveKey());
        root.addView(save, lp(-1, dp(58), 0, 0, 0, 18));

        Button accessibility = button(getString(R.string.open_accessibility));
        accessibility.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Exception error) { Toast.makeText(this, "Accessibility Settings unavailable", Toast.LENGTH_LONG).show(); }
        });
        root.addView(accessibility, lp(-1, dp(58), 0, 0, 0, 12));

        Button test = button(getString(R.string.test_nuvio));
        test.setOnClickListener(v -> {
            if (NuvioLauncher.openTest(this)) Toast.makeText(this, R.string.test_link_opened, Toast.LENGTH_SHORT).show();
        });
        root.addView(test, lp(-1, dp(58), 0, 0, 0, 18));

        TextView note = text("Only Nuvio links are opened. Your TMDB key is stored in this app's private local preferences and is sent only to TMDB for title lookup.", 15, Color.rgb(183, 192, 208));
        note.setLineSpacing(0, 1.15f);
        root.addView(note, lp(-1, -2, 0, 0, 0, 0));
        setContentView(scroll);
    }

    private void saveKey() {
        String key = keyField.getText().toString().trim();
        if (!key.isEmpty() && key.length() < 10) {
            Toast.makeText(this, R.string.invalid_key, Toast.LENGTH_LONG).show();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, key).apply();
        Toast.makeText(this, R.string.key_saved, Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void updateStatus() {
        boolean enabled = isServiceEnabled();
        String savedKey = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY, "").trim();
        boolean hasKey = !savedKey.isEmpty() || !BuildConfig.TMDB_API_KEY.trim().isEmpty();
        if (enabled && hasKey) {
            status.setText(getString(R.string.status_line, getString(R.string.service_status_title),
                    getString(R.string.status_enabled), getString(R.string.status_ready)));
            status.setTextColor(Color.rgb(123, 228, 149));
        } else if (!hasKey) {
            status.setText(getString(R.string.status_line, getString(R.string.service_status_title),
                    enabled ? getString(R.string.status_enabled) : getString(R.string.status_disabled),
                    getString(R.string.status_key_missing)));
            status.setTextColor(Color.rgb(255, 209, 102));
        } else {
            status.setText(getString(R.string.status_line, getString(R.string.service_status_title),
                    getString(R.string.status_disabled), ""));
            status.setTextColor(Color.rgb(255, 209, 102));
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

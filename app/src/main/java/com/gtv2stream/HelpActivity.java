package com.gtv2stream;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * On-TV condensed version of the Windows ADB installation guide, so the user can
 * read each step and command on the TV while working in the platform-tools
 * Command Prompt on the PC. The authoritative guide remains INSTALL_ADB.md.
 */
public final class HelpActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(16, 19, 26));
        getWindow().setNavigationBarColor(Color.rgb(16, 19, 26));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(40), dp(30), dp(40), dp(40));
        root.setBackgroundColor(Color.rgb(16, 19, 26));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView title = text(getString(R.string.help_title), 26, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, lp(-1, -2, 0, 0, 0, 16));

        root.addView(body(getString(R.string.help_intro), 0, 22));

        root.addView(body(getString(R.string.help_need), 0, 14));
        root.addView(body(getString(R.string.help_dev), 0, 14));
        root.addView(body(getString(R.string.help_connect), 0, 8));
        root.addView(command("adb connect TV_IP:5555", 0, 12));
        root.addView(body(getString(R.string.help_connect_pair), 0, 8));
        root.addView(command("adb pair TV_IP:PAIRING_PORT", 0, 12));
        root.addView(body(getString(R.string.help_connect_pair_2), 0, 8));
        root.addView(command("adb connect TV_IP:CONNECTION_PORT", 0, 12));
        root.addView(body(getString(R.string.help_install), 0, 8));
        root.addView(command("adb install -r GTV2STREAM-v1.0.0.apk", 0, 14));

        root.addView(body(getString(R.string.help_autostart), 0, 8));
        root.addView(command("appops set com.gtv2stream AUTO_START allow", 0, 14));

        root.addView(body(getString(R.string.help_configure), 0, 14));
        root.addView(body(getString(R.string.help_finish), 0, 18));
        root.addView(trouble(getString(R.string.help_trouble)));

        setContentView(scroll);
    }

    private TextView body(String value, int left, int bottom) {
        return lpView(text(value, 16, Color.rgb(210, 216, 226)), -1, -2, left, 0, left, bottom);
    }

    private TextView command(String value, int left, int bottom) {
        TextView view = text(value, 16, Color.rgb(123, 228, 149));
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setBackgroundColor(Color.rgb(28, 35, 48));
        return lpView(view, -1, -2, left, 0, left, bottom);
    }

    private TextView trouble(String value) {
        TextView view = text(value, 15, Color.rgb(183, 192, 208));
        view.setLineSpacing(0, 1.15f);
        return lpView(view, -1, -2, 0, 0, 0, 0);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private TextView lpView(TextView view, int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        view.setLayoutParams(p);
        return view;
    }

    private LinearLayout.LayoutParams lp(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

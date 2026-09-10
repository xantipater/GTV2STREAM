package com.gtv2stream;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Quiet, throttled stable-release check. It never downloads or installs anything. */
final class UpdateChecker {
    private static final String API = "https://api.github.com/repos/xantipater/GTV2STREAM/releases/latest";
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final Pattern TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern HTML_URL = Pattern.compile("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    interface Listener { void onResult(String version, String releaseUrl); }

    private UpdateChecker() { }

    static void check(Context context, Listener listener) {
        final Context appContext = context.getApplicationContext();
        final android.content.SharedPreferences prefs = appContext.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE);
        long last = prefs.getLong("update_check_at", 0L);
        if (System.currentTimeMillis() - last < DAY_MS) {
            String cached = prefs.getString("update_version", null);
            if (cached != null && compareVersions(cached, currentVersion(appContext)) > 0)
                listener.onResult(cached, prefs.getString("update_url", releaseUrl(cached)));
            return;
        }
        prefs.edit().putLong("update_check_at", System.currentTimeMillis()).apply();
        EXECUTOR.execute(() -> {
            String[] result = fetch();
            if (result == null) return;
            prefs.edit().putString("update_version", result[0]).putString("update_url", result[1]).apply();
            if (compareVersions(result[0], currentVersion(appContext)) > 0)
                new Handler(Looper.getMainLooper()).post(() -> listener.onResult(result[0], result[1]));
        });
    }

    static String currentVersion(Context context) {
        try { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "0.0.0"; }
    }

    static int compareVersions(String left, String right) {
        int[] a = numbers(left), b = numbers(right);
        if (a == null || b == null) return 0;
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return a[i] > b[i] ? 1 : -1;
        return 0;
    }

    static String parseStableReleaseVersion(String json) {
        if (json == null || Pattern.compile("\\\"draft\\\"\\s*:\\s*true").matcher(json).find()
                || Pattern.compile("\\\"prerelease\\\"\\s*:\\s*true").matcher(json).find()) return null;
        Matcher m = TAG.matcher(json); String tag = m.find() ? m.group(1) : null;
        return numbers(tag) == null ? null : tag;
    }

    static String parseReleaseUrl(String json, String version) {
        Matcher m = HTML_URL.matcher(json == null ? "" : json);
        return m.find() ? m.group(1) : releaseUrl(version);
    }

    private static int[] numbers(String value) {
        if (value == null) return null;
        Matcher m = Pattern.compile("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?$").matcher(value.trim());
        if (!m.matches()) return null;
        try { return new int[] { Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), m.group(3) == null ? 0 : Integer.parseInt(m.group(3)) }; }
        catch (NumberFormatException e) { return null; }
    }

    private static String[] fetch() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(API).openConnection(); c.setConnectTimeout(8000); c.setReadTimeout(8000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            if (c.getResponseCode() != 200) return null;
            StringBuilder body = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) { String line; while ((line = r.readLine()) != null) body.append(line); }
            String version = parseStableReleaseVersion(body.toString()); return version == null ? null : new String[] { version, parseReleaseUrl(body.toString(), version) };
        } catch (Exception ignored) { return null; } finally { if (c != null) c.disconnect(); }
    }

    private static String releaseUrl(String version) { return "https://github.com/xantipater/GTV2STREAM/releases/tag/" + version; }
}

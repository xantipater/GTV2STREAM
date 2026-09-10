package com.gtv2stream;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Quiet, throttled stable-release check. Checking never downloads or installs anything. */
final class UpdateChecker {
    private static final String API = "https://api.github.com/repos/xantipater/GTV2STREAM/releases/latest";
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final Pattern TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern HTML_URL = Pattern.compile("\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final String APK_SUFFIX = ".apk";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    interface Listener { void onResult(UpdateInfo info); }

    /** Stable-release details handed to Settings for the check-only notice and one-tap update. */
    static final class UpdateInfo {
        final String version;
        final String pageUrl;
        final String apkUrl;
        final long apkSize;

        UpdateInfo(String version, String pageUrl, String apkUrl, long apkSize) {
            this.version = version;
            this.pageUrl = pageUrl;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
        }
    }

    private UpdateChecker() { }

    static void check(Context context, Listener listener) {
        final Context appContext = context.getApplicationContext();
        final android.content.SharedPreferences prefs = appContext.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE);
        long last = prefs.getLong("update_check_at", 0L);
        if (System.currentTimeMillis() - last < DAY_MS) {
            String cached = prefs.getString("update_version", null);
            if (cached != null && compareVersions(cached, currentVersion(appContext)) > 0)
                listener.onResult(new UpdateInfo(cached, prefs.getString("update_url", releaseUrl(cached)),
                        prefs.getString("update_apk_url", null), prefs.getLong("update_apk_size", -1L)));
            return;
        }
        prefs.edit().putLong("update_check_at", System.currentTimeMillis()).apply();
        EXECUTOR.execute(() -> {
            String[] result = fetch();
            if (result == null) return;
            android.content.SharedPreferences.Editor editor = prefs.edit()
                    .putString("update_version", result[0]).putString("update_url", result[1]);
            if (result[2] != null) editor.putString("update_apk_url", result[2]);
            else editor.remove("update_apk_url");
            editor.putLong("update_apk_size", result[3] == null ? -1L : Long.parseLong(result[3]));
            editor.apply();
            if (compareVersions(result[0], currentVersion(appContext)) > 0) {
                UpdateInfo info = new UpdateInfo(result[0], result[1], result[2],
                        result[3] == null ? -1L : Long.parseLong(result[3]));
                new Handler(Looper.getMainLooper()).post(() -> listener.onResult(info));
            }
        });
    }

    static String currentVersion(Context context) {
        try { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "0.0.0"; }
    }

    /**
     * Compares a release version against the installed version.
     *
     * <p>The two sides are parsed differently on purpose. A release tag must be a
     * plain {@code X.Y.Z}: GitHub's prerelease flag is checked upstream, and a
     * suffixed tag ({@code v1.2.0-rc1}) is a candidate for a stable update that
     * must never be offered, so it stays unparseable here. The installed version,
     * by contrast, is read leniently through any build suffix, because a suffixed
     * build of our own ({@code 1.2.0-testbuild}) would otherwise compare as 0
     * against everything and silently disable update checks for that install.
     */
    static int compareVersions(String release, String installed) {
        int[] a = releaseNumbers(release), b = installedNumbers(installed);
        if (a == null || b == null) return 0;
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return a[i] > b[i] ? 1 : -1;
        return 0;
    }

    static String parseStableReleaseVersion(String json) {
        if (json == null || Pattern.compile("\\\"draft\\\"\\s*:\\s*true").matcher(json).find()
                || Pattern.compile("\\\"prerelease\\\"\\s*:\\s*true").matcher(json).find()) return null;
        Matcher m = TAG.matcher(json); String tag = m.find() ? m.group(1) : null;
        // A suffixed tag is a prerelease or a build name either way; only a plain
        // X.Y.Z tag is ever surfaced as a stable update.
        return releaseNumbers(tag) == null ? null : tag;
    }

    static String parseReleaseUrl(String json, String version) {
        Matcher m = HTML_URL.matcher(json == null ? "" : json);
        return m.find() ? m.group(1) : releaseUrl(version);
    }

    /**
     * Resolves the release APK download URL from the release JSON. Only same-origin
     * {@code https://github.com/...} browser download URLs whose asset name ends in
     * {@code .apk} (case-insensitive) are accepted; anything else returns null so the
     * one-tap update path stays hidden and Settings keeps the check-only page link.
     * Prefers an asset whose name looks like an app/release APK, then any APK asset.
     */
    static String parseApkDownloadUrl(String json) {
        List<String[]> assets = apkAssets(json);
        if (assets.isEmpty()) return null;
        for (String[] asset : assets) {
            String lower = asset[0].toLowerCase(Locale.US);
            if (lower.contains("app") || lower.contains("release") || lower.contains("gtv2stream")) return asset[1];
        }
        return assets.get(0)[1];
    }

    /** Published size in bytes of the resolved APK asset, or -1 when unknown. */
    static long parseApkDownloadSize(String json, String apkUrl) {
        if (json == null || apkUrl == null) return -1L;
        int end = 0;
        while (true) {
            int urlAt = json.indexOf("\"browser_download_url\"", end);
            if (urlAt < 0) return -1L;
            int urlStart = json.indexOf('"', json.indexOf(':', urlAt) + 1);
            int urlEnd = urlStart < 0 ? -1 : json.indexOf('"', urlStart + 1);
            if (urlStart < 0 || urlEnd < 0) return -1L;
            String url = json.substring(urlStart + 1, urlEnd);
            if (apkUrl.equals(url)) {
                // Read "size" from the enclosing asset object, wherever it sits
                // relative to the download URL inside that object.
                int blockStart = enclosingBraceStart(json, urlAt);
                int blockEnd = enclosingBraceEnd(json, urlEnd);
                if (blockStart < 0 || blockEnd < 0 || blockEnd <= blockStart) return -1L;
                Matcher s = Pattern.compile("\"size\"\\s*:\\s*(\\d+)")
                        .matcher(json.substring(blockStart, blockEnd));
                if (!s.find()) return -1L;
                try { return Long.parseLong(s.group(1)); }
                catch (NumberFormatException ignored) { return -1L; }
            }
            end = urlEnd + 1;
        }
    }

    /** Index of the '{' opening the object that contains position {@code at}. */
    private static int enclosingBraceStart(String json, int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            char c = json.charAt(i);
            if (c == '}') depth++;
            else if (c == '{') {
                if (depth == 0) return i;
                depth--;
            }
        }
        return -1;
    }

    /** Index just past the '}' closing the object that contains position {@code at}. */
    private static int enclosingBraceEnd(String json, int at) {
        int depth = 0;
        for (int i = at; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                if (depth == 0) return i + 1;
                depth--;
            }
        }
        return -1;
    }

    static boolean isFailureRetryable(Failure failure) {
        return failure == Failure.OFFLINE || failure == Failure.NETWORK || failure == Failure.TIMEOUT;
    }

    /** Minimum plausible APK size in bytes; smaller downloads are treated as corrupt. */
    static final long MIN_APK_BYTES = 100L * 1024L;

    /**
     * Verifies a downloaded file before install: it must exist, meet the minimum
     * size, match the published size when known, and start with the ZIP/APK
     * magic ({@code PK\003\004}).
     */
    static boolean isValidApkDownload(java.io.File file, long expectedSize) {
        if (file == null || !file.isFile()) return false;
        long length = file.length();
        if (length < MIN_APK_BYTES) return false;
        if (expectedSize >= 0L && length != expectedSize) return false;
        java.io.InputStream in = null;
        try {
            in = new java.io.FileInputStream(file);
            byte[] magic = new byte[4];
            int read = 0;
            while (read < 4) {
                int n = in.read(magic, read, 4 - read);
                if (n < 0) break;
                read += n;
            }
            return read == 4 && magic[0] == 0x50 && magic[1] == 0x4B && magic[2] == 0x03 && magic[3] == 0x04;
        } catch (java.io.IOException ignored) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (java.io.IOException ignored) { }
        }
    }

    enum Failure { OFFLINE, NETWORK, TIMEOUT, CORRUPT, NO_APK, UNKNOWN_SOURCES, INSTALL_CANCELLED, INSTALL_FAILED }

    /**
     * Collects [name, browser_download_url] pairs that pass the APK allow-list.
     * Each download URL is paired with the nearest preceding asset name, which is
     * robust against nested objects (e.g. the uploader block) in the release JSON.
     */
    private static List<String[]> apkAssets(String json) {
        List<String[]> out = new ArrayList<>();
        if (json == null) return out;
        int end = 0;
        while (true) {
            int urlAt = json.indexOf("\"browser_download_url\"", end);
            if (urlAt < 0) return out;
            int urlStart = json.indexOf('"', json.indexOf(':', urlAt) + 1);
            int urlEnd = urlStart < 0 ? -1 : json.indexOf('"', urlStart + 1);
            if (urlStart < 0 || urlEnd < 0) return out;
            String url = json.substring(urlStart + 1, urlEnd);
            int nameAt = json.lastIndexOf("\"name\"", urlAt);
            String name = null;
            if (nameAt >= end) {
                int nameStart = json.indexOf('"', json.indexOf(':', nameAt) + 1);
                int nameEnd = nameStart < 0 ? -1 : json.indexOf('"', nameStart + 1);
                if (nameStart >= 0 && nameEnd >= 0) name = json.substring(nameStart + 1, nameEnd);
            }
            if (isAllowedApkAsset(name, url)) out.add(new String[] { name, url });
            end = urlEnd + 1;
        }
    }

    private static boolean isAllowedApkAsset(String name, String url) {
        if (name == null || url == null) return false;
        if (!name.toLowerCase(Locale.US).endsWith(APK_SUFFIX)) return false;
        if (!url.startsWith("https://github.com/")) return false;
        String file = url.substring(url.lastIndexOf('/') + 1);
        return file.toLowerCase(Locale.US).endsWith(APK_SUFFIX);
    }

    /** Release-side parse: only a plain {@code X.Y.Z} tag counts as a stable update. */
    private static int[] releaseNumbers(String value) {
        return parse(value, "^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?$");
    }

    /** Installed-side parse: read through a build suffix such as {@code -testbuild}. */
    private static int[] installedNumbers(String value) {
        return parse(value, "^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$");
    }

    private static int[] parse(String value, String pattern) {
        if (value == null) return null;
        Matcher m = Pattern.compile(pattern).matcher(value.trim());
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
            String json = body.toString();
            String version = parseStableReleaseVersion(json);
            if (version == null) return null;
            String apkUrl = parseApkDownloadUrl(json);
            long apkSize = parseApkDownloadSize(json, apkUrl);
            return new String[] { version, parseReleaseUrl(json, version), apkUrl,
                    apkSize < 0L ? null : Long.toString(apkSize) };
        } catch (Exception ignored) { return null; } finally { if (c != null) c.disconnect(); }
    }

    private static String releaseUrl(String version) { return "https://github.com/xantipater/GTV2STREAM/releases/tag/" + version; }
}

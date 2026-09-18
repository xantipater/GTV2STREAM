package com.gtv2stream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Actual production helpers: no copied algorithms and no source-text assertions. */
public final class StabilisationTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.out.println("StabilisationTest: PASS (" + run() + " checks)");
        if (args.length > 0) {
            File apk = new File(args[0]);
            check(UpdateChecker.isValidApkDownload(apk, apk.length()), "actual release APK archive");
            System.out.println("Release archive: PASS (" + apk.length() + " bytes; " + apk.getName() + ")");
        }
    }

    static int run() throws Exception {
        checks = 0;
        TmdbClient.Candidate exact = candidate("Harry Potter and the Goblet of Fire", "2005", 1, 0);
        TmdbClient.Candidate sequel = candidate("Harry Potter and the Order of the Phoenix", "2007", 2, 1000);
        check(TitleResultHelper.chooseBest(exact.title, Arrays.asList(sequel, exact)) == exact,
                "exact title wins against repeated-word sequel");
        check(TitleResultHelper.chooseBest(exact.title, Arrays.asList(exact, sequel)) == exact,
                "result order cannot alter exact choice");
        check(TitleResultHelper.chooseBest("Dune", List.of(candidate("Alien", "1979", 3, 1000))) == null,
                "zero overlap rejected");
        check(TitleResultHelper.chooseBest("Harry Potter", List.of(sequel)) == null,
                "an incomplete franchise title is not permission to pick a sequel");
        TmdbClient.Candidate original = candidate("The Thing", "1982", 4, 1);
        TmdbClient.Candidate remake = candidate("The Thing", "2011", 5, 1000);
        List<TmdbClient.Candidate> remakes = Arrays.asList(remake, original);
        check(TitleResultHelper.chooseBest("The Thing", remakes) == null, "ambiguous remake is not popularity-ranked");
        check(TitleResultHelper.chooseBest("The Thing (1982)", remakes) == original, "explicit year selects original");
        check(TitleResultHelper.chooseBest("The Thing (2020)", remakes) == null, "wrong explicit year fails closed");
        check(TitleResultHelper.chooseBest("The Thing", Arrays.asList(original, original)) == original,
                "duplicate rows are one identity");
        check(TitleResultHelper.chooseBest("The Thing", Arrays.asList(original,
                new TmdbClient.Candidate("The Thing", "1982", "tv", 4, 100))) == null,
                "same numeric id in different media types is ambiguous");
        check(TitleResultHelper.chooseBest("Dune", Arrays.asList(null,
                new TmdbClient.Candidate("Dune", "2021", "person", 1, 100))) == null,
                "invalid/non-content candidates rejected");
        check(TitleResultHelper.chooseBest("S.W.A.T.", List.of(candidate("S W A T", "2017", 6, 0))) != null,
                "punctuation normalisation preserved");
        check(TitleResultHelper.extractYear("1917").isEmpty(), "numeric title is not year metadata");
        check("1968".equals(TitleResultHelper.extractYear("2001: A Space Odyssey (1968)")),
                "title numeral does not replace explicit year");

        for (String payload : Arrays.asList("The Thing (1982). Watch on Netflix",
                "Netflix. The Thing (1982).", "The Thing (1982), Netflix")) {
            RecommendationTitleParser.Source source = RecommendationTitleParser.fromDescriptionSource(payload);
            check("The Thing".equals(source.title) && "1982".equals(source.year), "description retains typed year");
            check(TitleResultHelper.chooseBest(source.lookupTitle(), remakes) == original,
                    "description-to-match pipeline retains year");
        }
        for (List<CharSequence> payload : Arrays.<List<CharSequence>>asList(
                Arrays.asList("Netflix", "The Thing (1982)"),
                Arrays.asList("The Thing (1982)", "Watch on Netflix"))) {
            RecommendationTitleParser.Source source = RecommendationTitleParser.fromEventTextSource(payload);
            check(TitleResultHelper.chooseBest(source.lookupTitle(), remakes) == original,
                    "event-to-match pipeline retains year");
        }
        check("The Thing (1982)".equals(RecommendationTitleParser.fromDetailTitleSource("The Thing (1982)").lookupTitle()),
                "detail-row metadata survives");
        check(RecommendationTitleParser.fromDescriptionSource(
                "Dune. A Review of Dune (1984). Watch on Netflix").year.isEmpty(), "synopsis year not borrowed");
        check(RecommendationTitleParser.fromEventTextSource(Arrays.asList("Dune", "(1984)", "Watch on Netflix")).year.isEmpty(),
                "unattributed metadata row is not a year hint");
        check(!DispatchPolicy.shouldSuppressProviderlessFallback("The Thing (1982)", false, 1000,
                "The Thing (2011)", false, "", 1100, 2000), "remakes do not suppress each other");
        check(DispatchPolicy.focusedProviderForDetail("The Thing (1982)", false, "netflix", 1000,
                "The Thing (2011)", 1100, 15000).isEmpty(), "provider bridge does not cross explicit years");

        MatchCache.clear();
        TitleMatch originalMatch = new TitleMatch("The Thing", "1982", "movie", 4, "tt0084787");
        MatchCache.put("The Thing (1982)", originalMatch);
        check(MatchCache.get("the thing (1982)") == originalMatch, "same identity cached");
        check(MatchCache.get("The Thing (2011)") == null && MatchCache.get("The Thing") == null,
                "another/unknown year has a separate cache identity");
        MatchCache.putMiss("The Thing (2011)");
        check(!MatchCache.isMiss("The Thing (1982)") && MatchCache.isMiss("The Thing (2011)"),
                "negative cache does not shadow another remake");
        MatchCache.clear();
        for (int i = 0; i < 33; i++) MatchCache.put("Title " + i, originalMatch);
        check(MatchCache.get("Title 0") == null && MatchCache.get("Title 32") == originalMatch, "positive cache bounded");
        MatchCache.clear();

        RecommendationTitleParser.Source netflix = RecommendationTitleParser.fromDescriptionSource(
                "The YouTube Effect. Watch on Netflix");
        check(!netflix.youtube && "netflix".equals(netflix.provider), "title mention is not YouTube evidence");
        check(!RecommendationTitleParser.fromEventTextSource(Arrays.asList("The YouTube Effect", "Watch on Netflix")).youtube,
                "event title mention is not YouTube evidence");
        check(!RecommendationTitleParser.fromDescriptionSource("The YouTube Effect").youtube,
                "bare title carries no invented provider");
        check(RecommendationTitleParser.fromEventTextSource(Arrays.asList("Big Buck Bunny", "YouTube • Blender")).youtube,
                "bounded YouTube metadata marker still routes");
        check(RecommendationTitleParser.fromDescriptionSource("YouTube. Big Buck Bunny.").youtube,
                "YouTube provider-first description still routes");
        check(RecommendationTitleParser.fromDescriptionSource("Big Buck Bunny, YouTube • Blender").youtube,
                "captured YouTube card shape still routes");
        String longTitle = "Shang-Chi and the Legend of the Ten Rings";
        check(longTitle.equals(RecommendationTitleParser.fromEventText(Arrays.asList("Disney+", longTitle))),
                "long provider-first event uses the same evidence bound as descriptions");

        var labels = AppLabelPolicy.normalizeAll(Arrays.asList("MiX", "YouTube", "Netflix"));
        for (List<CharSequence> payload : Arrays.<List<CharSequence>>asList(
                Arrays.asList("Dune", "Sponsored"), List.of("Advertisement. Dune. Watch on Netflix"), List.of("Move"))) {
            check(LauncherInteractionPolicy.assess(payload, "", "", "", labels).ignore,
                    "positive rejection is terminal");
        }
        var session = new LauncherInteractionPolicy.Session();
        var content = LauncherInteractionPolicy.assess(Arrays.asList("Dune", "Watch on Netflix"), "", "", "", labels);
        check(session.accept(content, true), "first card selected");
        long a = session.ticket();
        check(session.accept(content, false) && session.isCurrent(a), "duplicate focus alone does not cancel work");
        check(session.accept(content, true) && !session.isCurrent(a), "next click cancels previous content work");

        File valid = archive(true, true);
        check(valid.length() < 100 * 1024 && UpdateChecker.isValidApkDownload(valid, valid.length()),
                "small real ZIP passes structural validation (Android parsing is a separate check)");
        check(!UpdateChecker.isValidApkDownload(valid, valid.length() + 1), "published length mismatch rejected");
        check(!UpdateChecker.isValidApkDownload(archive(false, true), -1), "missing manifest rejected");
        check(!UpdateChecker.isValidApkDownload(archive(true, false), -1), "missing dex rejected");
        File fake = temporary();
        try (RandomAccessFile out = new RandomAccessFile(fake, "rw")) {
            out.write(new byte[]{80, 75, 3, 4}); out.setLength(200 * 1024);
        }
        check(!UpdateChecker.isValidApkDownload(fake, fake.length()), "ZIP header plus zeros is not an archive");
        File truncated = temporary();
        byte[] bytes = Files.readAllBytes(valid.toPath());
        Files.write(truncated.toPath(), Arrays.copyOf(bytes, bytes.length - 12));
        check(!UpdateChecker.isValidApkDownload(truncated, -1), "truncated central directory rejected");
        File corrupt = temporary();
        byte[] marker = "DEX-CONTENT".getBytes(StandardCharsets.UTF_8);
        int at = indexOf(bytes, marker);
        check(at >= 0, "fixture contains stored dex data");
        bytes[at] ^= 1;
        Files.write(corrupt.toPath(), bytes);
        check(!UpdateChecker.isValidApkDownload(corrupt, corrupt.length()), "entry CRC mismatch rejected");
        try (RandomAccessFile out = new RandomAccessFile(fake, "rw")) { out.setLength(ApkArchive.MAX_DOWNLOAD_BYTES + 1); }
        check(!UpdateChecker.isValidApkDownload(fake, -1), "oversized download rejected");
        check(!UpdateChecker.isValidApkDownload(null, -1), "missing download rejected");
        check(ApkUpdater.isAllowedDownloadUrl("https://release-assets.githubusercontent.com/a/file.apk"), "release CDN allowed");
        for (String url : Arrays.asList("http://github.com/a", "https://github.com@evil.example/a",
                "https://evil.example@github.com/a", "https://github.com:8443/a",
                "https://raw.githubusercontent.com/a", "https://github.com.evil.example/a")) {
            check(!ApkUpdater.isAllowedDownloadUrl(url), "foreign/misleading URL rejected");
        }
        check(!UpdateChecker.isInstalledAtLeast("v1.2.1", "garbage"), "invalid installed version cannot mean update success");
        check(UpdateChecker.isInstalledAtLeast("v1.2.1", "1.2.1"), "installed expected version recognised");
        check(!UpdateChecker.isInstalledAtLeast("v1.2.1", "1.2.0"), "old version is not success");
        return checks;
    }

    private static TmdbClient.Candidate candidate(String title, String year, long id, double popularity) {
        return new TmdbClient.Candidate(title, year, "movie", id, popularity);
    }
    private static File temporary() throws Exception {
        File file = File.createTempFile("gtv2stream-regression-", ".apk"); file.deleteOnExit(); return file;
    }
    private static File archive(boolean manifest, boolean dex) throws Exception {
        File file = temporary();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            if (manifest) entry(zip, "AndroidManifest.xml", new byte[]{3, 0, 8, 0});
            if (dex) entry(zip, "classes.dex", "DEX-CONTENT".getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
    private static void entry(ZipOutputStream zip, String name, byte[] data) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        CRC32 crc = new CRC32(); crc.update(data);
        entry.setMethod(ZipEntry.STORED); entry.setSize(data.length); entry.setCrc(crc.getValue());
        zip.putNextEntry(entry); zip.write(data); zip.closeEntry();
    }
    private static int indexOf(byte[] bytes, byte[] needle) {
        outer: for (int i = 0; i <= bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (bytes[i+j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}

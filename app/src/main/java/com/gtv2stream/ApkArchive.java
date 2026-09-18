package com.gtv2stream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Bounded structural validation, not a substitute for Android's package/signature checks. */
final class ApkArchive {
    static final long MAX_DOWNLOAD_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_EXPANDED_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 4096;
    private ApkArchive() { }

    static boolean isValid(File file, long expectedSize) {
        if (file == null || !file.isFile() || file.length() <= 0
                || file.length() > MAX_DOWNLOAD_BYTES
                || (expectedSize >= 0 && file.length() != expectedSize)) return false;
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry manifest = zip.getEntry("AndroidManifest.xml");
            ZipEntry dex = zip.getEntry("classes.dex");
            if (manifest == null || manifest.isDirectory() || manifest.getSize() <= 0
                    || dex == null || dex.isDirectory() || dex.getSize() <= 0) return false;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            Set<String> names = new HashSet<>();
            long expanded = 0;
            byte[] buffer = new byte[16 * 1024];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!names.add(entry.getName()) || names.size() > MAX_ENTRIES) return false;
                if (entry.isDirectory()) continue;
                CRC32 crc = new CRC32();
                long count = 0;
                try (InputStream in = zip.getInputStream(entry)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        expanded += read;
                        count += read;
                        if (expanded > MAX_EXPANDED_BYTES) return false;
                        crc.update(buffer, 0, read);
                    }
                }
                if (count != entry.getSize() || crc.getValue() != entry.getCrc()) return false;
            }
            return true;
        } catch (IOException | RuntimeException invalid) {
            return false;
        }
    }
}

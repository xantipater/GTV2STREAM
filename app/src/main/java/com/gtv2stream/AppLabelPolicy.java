package com.gtv2stream;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Pure installed-app-label policy for the fail-closed launcher-tile check.
 * Normalization lives here so the helper tests pin the exact matching
 * semantics; the service only supplies the label set.
 */
public final class AppLabelPolicy {
    private AppLabelPolicy() { }

    /** Normalizes one app label or candidate title for comparison. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.US);
    }

    /** Normalizes a label collection, dropping blanks. */
    public static Set<String> normalizeAll(Collection<String> labels) {
        Set<String> normalized = new HashSet<>();
        if (labels == null) return normalized;
        for (String label : labels) {
            String value = normalize(label);
            if (!value.isEmpty()) normalized.add(value);
        }
        return normalized;
    }

    /** True when the title exactly matches a known installed app label. */
    public static boolean matches(Set<String> normalizedLabels, String title) {
        if (normalizedLabels == null || normalizedLabels.isEmpty()) return false;
        String candidate = normalize(title);
        return !candidate.isEmpty() && normalizedLabels.contains(candidate);
    }
}

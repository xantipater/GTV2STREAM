package com.gtv2stream;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pure provider-whitelist logic shared by production code and helper tests.
 * Stored values are canonical provider identities (see
 * {@link RecommendationTitleParser#canonicalWhitelistId}); matching is exact and
 * case-sensitive on those identities, so a film title can never match.
 */
final class ProviderWhitelist {
    private ProviderWhitelist() { }

    /** Drops unknown, blank, and null entries; unknown ids fail closed (not whitelisted). */
    static Set<String> sanitize(Set<String> raw) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (raw == null) return cleaned;
        for (String value : raw) {
            String id = RecommendationTitleParser.canonicalWhitelistId(value);
            if (!id.isEmpty()) cleaned.add(id);
        }
        return cleaned;
    }

    /** True only for an exact canonical-identity membership hit. */
    static boolean contains(Set<String> whitelist, String providerId) {
        if (whitelist == null || whitelist.isEmpty()) return false;
        String id = RecommendationTitleParser.canonicalWhitelistId(providerId);
        return !id.isEmpty() && whitelist.contains(id);
    }

    /** Toggles one canonical identity; unknown ids are ignored. */
    static Set<String> toggled(Set<String> whitelist, String providerId) {
        Set<String> next = new LinkedHashSet<>();
        if (whitelist != null) next.addAll(sanitize(whitelist));
        String id = RecommendationTitleParser.canonicalWhitelistId(providerId);
        if (id.isEmpty()) return next;
        if (!next.remove(id)) next.add(id);
        return next;
    }

    /** Human-readable summary for Settings ("None" keeps the v1.1 default explicit). */
    static String summary(Set<String> whitelist) {
        Set<String> cleaned = sanitize(whitelist);
        if (cleaned.isEmpty()) return "None";
        StringBuilder names = new StringBuilder();
        for (String id : RecommendationTitleParser.PROVIDER_ID_ORDER) {
            if (!cleaned.contains(id)) continue;
            if (names.length() > 0) names.append(", ");
            names.append(RecommendationTitleParser.providerDisplayName(id));
        }
        return names.length() == 0 ? "None" : names.toString();
    }

    /** Serialized SharedPreferences form; empty set serializes to "". */
    static String serialize(Set<String> whitelist) {
        Set<String> cleaned = sanitize(whitelist);
        if (cleaned.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String id : cleaned) {
            if (out.length() > 0) out.append(',');
            out.append(id);
        }
        return out.toString();
    }

    /** Parses the serialized form; unknown tokens are dropped (safe migration). */
    static Set<String> parse(String raw) {
        Set<String> ids = new LinkedHashSet<>();
        if (raw == null || raw.trim().isEmpty()) return ids;
        for (String token : raw.split(",")) {
            String id = RecommendationTitleParser.canonicalWhitelistId(token.trim());
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }
}

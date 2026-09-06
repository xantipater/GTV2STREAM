package com.gtv2stream;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny LRU cache of recent TMDB matches, keyed by normalized title. Clicking the
 * same card again within the TTL (retrying a failed launch, coming back after
 * closing the target app) skips the network lookup entirely and launches from
 * the cached result. Accessed from the service worker thread; synchronized for
 * safety and for the settings test thread.
 */
public final class MatchCache {
    private static final long TTL_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_ENTRIES = 32;
    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>(32, 0.75f, true);

    private MatchCache() { }

    private static final class Entry {
        final TitleMatch match;
        final long storedAt;

        Entry(TitleMatch match, long storedAt) {
            this.match = match;
            this.storedAt = storedAt;
        }
    }

    /** Returns the cached match for the title, or null when absent or expired. */
    public static synchronized TitleMatch get(String title) {
        String key = TitleResultHelper.normalizedTitle(title);
        if (key.isEmpty()) return null;
        Entry entry = CACHE.get(key);
        if (entry == null) return null;
        if (System.currentTimeMillis() - entry.storedAt > TTL_MS) {
            CACHE.remove(key);
            return null;
        }
        return entry.match;
    }

    public static synchronized void put(String title, TitleMatch match) {
        if (title == null || match == null) return;
        String key = TitleResultHelper.normalizedTitle(title);
        if (key.isEmpty()) return;
        CACHE.put(key, new Entry(match, System.currentTimeMillis()));
        while (CACHE.size() > MAX_ENTRIES) {
            CACHE.remove(eldestKey());
        }
    }

    private static String eldestKey() {
        // accessOrder=true places the least recently used entry first.
        for (String key : CACHE.keySet()) return key;
        return null;
    }

    static synchronized void clear() {
        CACHE.clear();
    }
}

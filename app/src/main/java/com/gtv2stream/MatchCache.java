package com.gtv2stream;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny LRU cache of recent TMDB matches, keyed by normalized title. Clicking the
 * same card again within the TTL (retrying a failed launch, coming back after
 * closing the target app) skips the network lookup entirely and launches from
 * the cached result. Titles with no match are cached separately with a short
 * TTL so repeated clicks fail closed fast instead of re-querying TMDB.
 * Accessed from the service worker thread; synchronized for safety and for the
 * settings test thread.
 */
public final class MatchCache {
    private static final long TTL_MS = 24 * 60 * 60 * 1000L;
    private static final long MISS_TTL_MS = 60 * 60 * 1000L;
    private static final int MAX_ENTRIES = 32;
    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>(32, 0.75f, true);
    private static final LinkedHashMap<String, Long> MISSES = new LinkedHashMap<>(32, 0.75f, true);
    private static final int MAX_MISS_ENTRIES = 32;

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
        MISSES.remove(key);
        while (CACHE.size() > MAX_ENTRIES) {
            CACHE.remove(eldestKey());
        }
    }

    /** Returns true when the title recently resolved to no TMDB match. */
    public static synchronized boolean isMiss(String title) {
        String key = TitleResultHelper.normalizedTitle(title);
        if (key.isEmpty()) return false;
        Long storedAt = MISSES.get(key);
        if (storedAt == null) return false;
        if (System.currentTimeMillis() - storedAt > MISS_TTL_MS) {
            MISSES.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Records that the title resolved to no match. A later hit for the same
     * title clears the miss so fresh data always wins. A miss never shadows
     * a live hit: recording one over an existing entry is a no-op.
     */
    public static synchronized void putMiss(String title) {
        if (title == null) return;
        String key = TitleResultHelper.normalizedTitle(title);
        if (key.isEmpty()) return;
        if (CACHE.containsKey(key)) return;
        MISSES.put(key, System.currentTimeMillis());
        while (MISSES.size() > MAX_MISS_ENTRIES) {
            java.util.Iterator<String> oldest = MISSES.keySet().iterator();
            if (!oldest.hasNext()) break;
            oldest.next();
            oldest.remove();
        }
    }

    private static String eldestKey() {
        // accessOrder=true places the least recently used entry first.
        for (String key : CACHE.keySet()) return key;
        return null;
    }

    static synchronized void clear() {
        CACHE.clear();
        MISSES.clear();
    }
}

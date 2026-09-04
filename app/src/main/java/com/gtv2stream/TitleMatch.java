package com.gtv2stream;

/** A TMDB result with the external identifier required by Nuvio. */
public final class TitleMatch {
    public final String title;
    public final String year;
    public final String mediaType; // movie or tv
    public final long tmdbId;
    public final String imdbId;

    public TitleMatch(String title, String year, String mediaType, long tmdbId, String imdbId) {
        this.title = title;
        this.year = year == null ? "" : year;
        this.mediaType = mediaType == null ? "" : mediaType;
        this.tmdbId = tmdbId;
        this.imdbId = imdbId;
    }
}

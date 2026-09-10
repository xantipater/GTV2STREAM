package com.gtv2stream;

/** Pure target-selection seam shared by production routing and helper tests. */
final class YouTubeTarget {
    static final String SMARTTUBE = "smarttube";
    static final String TIZENTUBE = "tizentube";

    private YouTubeTarget() { }

    static boolean isTizenTube(String value) {
        return TIZENTUBE.equals(value);
    }
}

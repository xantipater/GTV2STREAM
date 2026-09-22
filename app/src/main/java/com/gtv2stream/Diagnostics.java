package com.gtv2stream;

import android.util.Log;

/** Raw payloads/queries are opt-in by installing a debug build, never release telemetry. */
final class Diagnostics {
    private Diagnostics() { }
    static void debug(String message) {
        if (BuildConfig.DEBUG) Log.d("GTV2STREAM", message);
    }
}

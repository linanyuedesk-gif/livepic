package com.cl.vtolive.utils;

/**
 * Live Photo flow constants (durations, limits).
 */
public final class LivePhotoConstants {

    private LivePhotoConstants() {}

    /** Recommended duration range for Live Photo (ms). Apple: 1.5–3 s. */
    public static final long DURATION_MIN_MS = 1500;
    public static final long DURATION_MAX_MS = 3500;

    /** Default interval when opening interval selection (start, end) in ms. */
    public static final long DEFAULT_INTERVAL_START_MS = 2000;
    public static final long DEFAULT_INTERVAL_END_MS = 5000;

    public static boolean isDurationInRange(long durationMs) {
        return durationMs >= DURATION_MIN_MS && durationMs <= DURATION_MAX_MS;
    }
}

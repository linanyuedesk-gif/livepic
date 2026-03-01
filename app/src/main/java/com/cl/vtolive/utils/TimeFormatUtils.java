package com.cl.vtolive.utils;

/**
 * Shared time formatting for the Live Photo flow (interval, duration, key frame).
 */
public final class TimeFormatUtils {

    private TimeFormatUtils() {}

    /**
     * Formats milliseconds as MM:SS (e.g. 01:30).
     */
    public static String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Formats duration for display: "Xs" or "M:SS".
     */
    public static String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}

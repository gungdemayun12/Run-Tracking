package com.example.runtrack.util;

import java.util.Locale;

public final class FormatUtils {

    private FormatUtils() {
    }

    public static String formatDistanceMeters(double meters) {
        return String.format(Locale.getDefault(), "%.2f km", meters / 1000.0);
    }

    public static String formatTimeSeconds(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
    }

    /** Compact clock for the live screen: MM:SS, or H:MM:SS after one hour. */
    public static String formatClock(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    /** Pace is expected in seconds per kilometre. */
    public static String formatPace(double secondsPerKm) {
        if (Double.isNaN(secondsPerKm) || Double.isInfinite(secondsPerKm) || secondsPerKm <= 0) {
            return "--:-- /km";
        }
        long total = Math.round(secondsPerKm);
        long m = total / 60;
        long s = total % 60;
        return String.format(Locale.getDefault(), "%02d:%02d /km", m, s);
    }

    public static String formatSpeedKmh(double metersPerSecond) {
        return String.format(Locale.getDefault(), "%.1f", Math.max(0, metersPerSecond) * 3.6);
    }
}

package com.example.runtrack.util;

import com.example.runtrack.model.RunActivity;

/**
 * Consistent formatting for stored activities.
 * All pace values are seconds per kilometre.
 */
public final class ActivityStatsHelper {

    private ActivityStatsHelper() {
    }

    public static long getDurationMillis(RunActivity activity) {
        if (activity == null) return 0;
        if (activity.durationMillis > 0) return activity.durationMillis;
        if (activity.endTime > activity.startTime) {
            return activity.endTime - activity.startTime;
        }
        return 0;
    }

    public static long getDurationSeconds(RunActivity activity) {
        return getDurationMillis(activity) / 1000;
    }

    public static double getAvgPaceSecondsPerKm(RunActivity activity) {
        if (activity == null) return 0;
        if (activity.avgPace > 0) return activity.avgPace;
        double km = activity.getDistanceKm();
        long sec = getDurationSeconds(activity);
        if (km > 0 && sec > 0) return sec / km;
        return 0;
    }

    public static String formatDuration(RunActivity activity) {
        return FormatUtils.formatTimeSeconds(getDurationSeconds(activity));
    }

    public static String formatDurationShort(RunActivity activity) {
        return FormatUtils.formatClock(getDurationSeconds(activity));
    }

    public static String formatPace(RunActivity activity) {
        return FormatUtils.formatPace(getAvgPaceSecondsPerKm(activity));
    }
}

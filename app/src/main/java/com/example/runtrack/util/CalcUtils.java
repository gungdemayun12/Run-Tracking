package com.example.runtrack.util;

import android.location.Location;

/**
 * Shared math used by the tracker and the stats screens.
 *
 * <p>Pace is always stored in <b>seconds per kilometre</b>.</p>
 */
public final class CalcUtils {

    private CalcUtils() {
    }

    /** MET value for running/walking speed (m/s). */
    public static double metForSpeed(double speedMps) {
        double kmh = Math.max(0, speedMps) * 3.6;
        if (kmh < 4) return 3.0;
        if (kmh < 5.5) return 4.3;
        if (kmh < 6.5) return 6.0;
        if (kmh < 8) return 8.3;
        if (kmh < 10) return 9.8;
        if (kmh < 12) return 11.0;
        if (kmh < 14) return 12.3;
        if (kmh < 16) return 13.5;
        if (kmh < 19) return 14.8;
        return 16.0;
    }

    /** Calories = MET * weight(kg) * time(hours). */
    public static double calculateCalories(double weightKg, double speedMps, long durationMillis) {
        if (durationMillis <= 0) return 0;
        double met = metForSpeed(speedMps);
        double durationHours = durationMillis / 3_600_000.0;
        return met * Math.max(weightKg, 30.0) * durationHours;
    }

    /**
     * Average pace in seconds per kilometre.
     */
    public static double calculatePace(double distanceMeters, long durationSeconds) {
        if (distanceMeters <= 1 || durationSeconds <= 0) return 0;
        double distanceKm = distanceMeters / 1000.0;
        return durationSeconds / distanceKm;
    }

    /** Exponential moving average used to avoid jittery speed readouts. */
    public static double smoothSpeed(double currentSpeed, double lastSpeed, float alpha) {
        if (currentSpeed < 0) currentSpeed = 0;
        if (lastSpeed < 0) lastSpeed = 0;
        return (alpha * currentSpeed) + (1.0f - alpha) * lastSpeed;
    }

    /** Precise geodesic distance in metres on WGS-84 ellipsoid between two coordinates. */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }

    /** Geodesic distance in metres between two coordinates. */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        return distanceMeters(lat1, lon1, lat2, lon2);
    }

    public static double distanceBetween(Location a, Location b) {
        if (a == null || b == null) return 0;
        return a.distanceTo(b);
    }
}

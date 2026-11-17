package com.example.runtrack.util;

import android.content.Context;

import com.example.runtrack.data.AppSettings;

import java.util.Locale;

/**
 * Converts and formats metric distances/pace/speed using the unit chosen in
 * Settings (kilometres or miles). All stored values remain metric.
 */
public final class UnitUtils {

    /** 1 km = 0.621371 miles */
    public static final double KM_TO_MI = 0.621371192;

    private UnitUtils() {
    }

    public static boolean isMiles(Context context) {
        return context != null && "mi".equalsIgnoreCase(AppSettings.getDistanceUnit(context));
    }

    public static String distanceUnit(Context context) {
        return isMiles(context) ? "mi" : "km";
    }

    public static String distanceLongUnit(Context context) {
        return isMiles(context) ? "MIL" : "KILOMETER";
    }

    public static String speedUnit(Context context) {
        return isMiles(context) ? "mph" : "km/h";
    }

    public static double toDisplayDistance(Context context, double km) {
        return isMiles(context) ? km * KM_TO_MI : km;
    }

    /** Formats a distance in km using the preferred unit, e.g. "2.00 km". */
    public static String distance(Context context, double km, int decimals) {
        double value = toDisplayDistance(context, km);
        return String.format(Locale.getDefault(), "%." + decimals + "f %s",
                value, distanceUnit(context));
    }

    public static String distance(Context context, double km) {
        return distance(context, km, 2);
    }

    /** Number only, without the unit suffix. */
    public static String distanceValue(Context context, double km, int decimals) {
        double value = toDisplayDistance(context, km);
        return String.format(Locale.getDefault(), "%." + decimals + "f", value);
    }

    /** Converts a pace in seconds per km to the preferred unit. */
    public static double toDisplayPace(Context context, double secondsPerKm) {
        return isMiles(context) ? secondsPerKm / KM_TO_MI : secondsPerKm;
    }

    /** Formats pace, e.g. "06:32 /km" or "10:31 /mi". */
    public static String pace(Context context, double secondsPerKm) {
        String unit = distanceUnit(context);
        if (Double.isNaN(secondsPerKm) || Double.isInfinite(secondsPerKm)
                || secondsPerKm <= 0) {
            return "--:-- /" + unit;
        }
        long total = Math.round(toDisplayPace(context, secondsPerKm));
        long minutes = total / 60;
        long seconds = total % 60;
        return String.format(Locale.getDefault(), "%02d:%02d /%s", minutes, seconds, unit);
    }

    /** Formats pace without the suffix, e.g. "06:32". */
    public static String paceValue(Context context, double secondsPerKm) {
        if (Double.isNaN(secondsPerKm) || Double.isInfinite(secondsPerKm)
                || secondsPerKm <= 0) {
            return "--:--";
        }
        long total = Math.round(toDisplayPace(context, secondsPerKm));
        return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60);
    }

    /** Formats speed from m/s using km/h or mph. */
    public static String speed(Context context, double metersPerSecond) {
        double kmh = Math.max(0, metersPerSecond) * 3.6;
        double value = isMiles(context) ? kmh * KM_TO_MI : kmh;
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    public static String speedWithUnit(Context context, double metersPerSecond) {
        return speed(context, metersPerSecond) + " " + speedUnit(context);
    }

    /** A goal such as "20 km" or "12 mi". */
    public static String goal(Context context, double km) {
        double value = toDisplayDistance(context, km);
        return String.format(Locale.getDefault(), "%.0f %s", value, distanceUnit(context));
    }

    /** Splits are displayed per km or per mile. */
    public static double splitLengthKm(Context context) {
        return isMiles(context) ? 1.609344 : 1.0;
    }

    public static String splitLabel(Context context, int index) {
        return isMiles(context) ? index + " mi" : index + " km";
    }
}

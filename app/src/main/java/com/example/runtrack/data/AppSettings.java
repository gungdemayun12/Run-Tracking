package com.example.runtrack.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Manages all persistent user preferences for the running engine and UI.
 */
public final class AppSettings {

    private static final String KEY_KEEP_SCREEN_ON = "pref_keep_screen_on";
    private static final String KEY_AUTO_PAUSE = "pref_auto_pause";
    private static final String KEY_COUNTDOWN_SECONDS = "pref_countdown_seconds";
    private static final String KEY_VOICE_COACH = "pref_voice_coach";
    private static final String KEY_VOICE_INTERVAL_KM = "pref_voice_interval_km";
    private static final String KEY_DISTANCE_UNIT = "pref_distance_unit";
    private static final String KEY_PRIVACY_ZONE = "pref_privacy_zone";
    private static final String KEY_WEEKLY_GOAL = "pref_weekly_goal_km";
    private static final String KEY_DEFAULT_MAP_TILE = "pref_default_map_tile";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";
    private static final String KEY_SOUND_EFFECTS = "pref_sound_effects";
    private static final String KEY_VIBRATION = "pref_vibration";
    private static final String KEY_GPS_MODE = "pref_gps_mode";
    private static final String KEY_AUTO_LAP_KM = "pref_auto_lap_km";
    private static final String KEY_PRIVACY_RADIUS_M = "pref_privacy_radius_m";
    private static final String KEY_WEEKLY_RUNS_GOAL = "pref_weekly_runs_goal";
    private static final String KEY_ANIMATIONS = "pref_animations";
    private static final String KEY_KEEP_SIGNED_IN = "pref_keep_signed_in";

    private AppSettings() {
    }

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static boolean isKeepScreenOn(Context context) {
        return getPrefs(context).getBoolean(KEY_KEEP_SCREEN_ON, true);
    }

    public static void setKeepScreenOn(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply();
    }

    public static boolean isAutoPauseEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_PAUSE, true);
    }

    public static void setAutoPauseEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_PAUSE, enabled).apply();
    }

    public static int getCountdownDuration(Context context) {
        return getPrefs(context).getInt(KEY_COUNTDOWN_SECONDS, 3);
    }

    public static void setCountdownDuration(Context context, int seconds) {
        getPrefs(context).edit().putInt(KEY_COUNTDOWN_SECONDS, seconds).apply();
    }

    public static boolean isVoiceCoachEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_VOICE_COACH, true);
    }

    public static void setVoiceCoachEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_VOICE_COACH, enabled).apply();
    }

    public static float getVoiceIntervalKm(Context context) {
        return getPrefs(context).getFloat(KEY_VOICE_INTERVAL_KM, 1.0f);
    }

    public static void setVoiceIntervalKm(Context context, float km) {
        getPrefs(context).edit().putFloat(KEY_VOICE_INTERVAL_KM, km).apply();
    }

    public static String getDistanceUnit(Context context) {
        return getPrefs(context).getString(KEY_DISTANCE_UNIT, "km");
    }

    public static void setDistanceUnit(Context context, String unit) {
        getPrefs(context).edit().putString(KEY_DISTANCE_UNIT, unit).apply();
    }

    public static boolean isPrivacyZoneEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_PRIVACY_ZONE, false);
    }

    public static void setPrivacyZoneEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_PRIVACY_ZONE, enabled).apply();
    }

    public static double getWeeklyGoalKm(Context context) {
        return getPrefs(context).getFloat(KEY_WEEKLY_GOAL, 20.0f);
    }

    public static void setWeeklyGoalKm(Context context, double km) {
        getPrefs(context).edit().putFloat(KEY_WEEKLY_GOAL, (float) km).apply();
    }

    public static int getDefaultMapTile(Context context) {
        return getPrefs(context).getInt(KEY_DEFAULT_MAP_TILE, 0);
    }

    public static void setDefaultMapTile(Context context, int tile) {
        getPrefs(context).edit().putInt(KEY_DEFAULT_MAP_TILE, tile).apply();
    }

    public static boolean isDarkMode(Context context) {
        return getPrefs(context).getBoolean(KEY_DARK_MODE, true);
    }

    public static void setDarkMode(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }

    public static boolean isNotificationsEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_NOTIFICATIONS, true);
    }

    public static void setNotificationsEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }

    /* ------------------------------------------------------------------ */
    /* Audio, haptics & motion                                            */
    /* ------------------------------------------------------------------ */

    public static boolean isSoundEffectsEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SOUND_EFFECTS, true);
    }

    public static void setSoundEffectsEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SOUND_EFFECTS, enabled).apply();
    }

    public static boolean isVibrationEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_VIBRATION, true);
    }

    public static void setVibrationEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_VIBRATION, enabled).apply();
    }

    public static boolean isAnimationsEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ANIMATIONS, true);
    }

    public static void setAnimationsEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ANIMATIONS, enabled).apply();
    }

    /** Apakah sesi login dipertahankan saat aplikasi dibuka ulang. */
    public static boolean isKeepSignedIn(Context context) {
        return getPrefs(context).getBoolean(KEY_KEEP_SIGNED_IN, true);
    }

    public static void setKeepSignedIn(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_KEEP_SIGNED_IN, enabled).apply();
    }

    /* ------------------------------------------------------------------ */
    /* Tracking engine                                                    */
    /* ------------------------------------------------------------------ */

    /** 0 = akurasi tinggi, 1 = seimbang, 2 = hemat baterai. */
    public static int getGpsMode(Context context) {
        return getPrefs(context).getInt(KEY_GPS_MODE, 0);
    }

    public static void setGpsMode(Context context, int mode) {
        getPrefs(context).edit().putInt(KEY_GPS_MODE, Math.max(0, Math.min(2, mode))).apply();
    }

    /** Jarak (km) untuk pengumuman lap otomatis. 0 berarti nonaktif. */
    public static float getAutoLapKm(Context context) {
        return getPrefs(context).getFloat(KEY_AUTO_LAP_KM, 1.0f);
    }

    public static void setAutoLapKm(Context context, float km) {
        getPrefs(context).edit().putFloat(KEY_AUTO_LAP_KM, Math.max(0f, km)).apply();
    }

    /* ------------------------------------------------------------------ */
    /* Privacy & goals                                                    */
    /* ------------------------------------------------------------------ */

    public static int getPrivacyRadiusMeters(Context context) {
        return getPrefs(context).getInt(KEY_PRIVACY_RADIUS_M, 200);
    }

    public static void setPrivacyRadiusMeters(Context context, int meters) {
        getPrefs(context).edit().putInt(KEY_PRIVACY_RADIUS_M, Math.max(50, meters)).apply();
    }

    public static int getWeeklyRunsGoal(Context context) {
        return getPrefs(context).getInt(KEY_WEEKLY_RUNS_GOAL, 3);
    }

    public static void setWeeklyRunsGoal(Context context, int runs) {
        getPrefs(context).edit().putInt(KEY_WEEKLY_RUNS_GOAL, Math.max(1, runs)).apply();
    }

    /** Kembalikan seluruh preferensi ke nilai pabrik. */
    public static void resetAll(Context context) {
        getPrefs(context).edit().clear().apply();
    }
}

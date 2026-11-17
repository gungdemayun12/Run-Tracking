package com.example.runtrack.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.TimeUnit;

/**
 * Keeps the Firebase session alive across app restarts.
 *
 * <p>Firebase already stores the auth token locally, so the user stays signed in.
 * On top of that we keep a rolling "last active" timestamp: the session only
 * expires after {@link #MAX_IDLE_MS} of inactivity, never while the user keeps
 * opening the app. Signing out manually always clears the session.</p>
 */
public final class SessionManager {

    private static final String PREFS = "runtrack_session";
    private static final String KEY_UID = "uid";
    private static final String KEY_LAST_ACTIVE = "last_active";

    /** Sesi dianggap habis setelah 30 hari tanpa membuka aplikasi. */
    public static final long MAX_IDLE_MS = TimeUnit.DAYS.toMillis(30);

    private SessionManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Call right after a successful sign-in / sign-up. */
    public static void onLogin(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        prefs(context).edit()
                .putString(KEY_UID, user.getUid())
                .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
                .apply();
    }

    /** Call whenever the app comes to the foreground to extend the session. */
    public static void touch(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        SharedPreferences p = prefs(context);
        long now = System.currentTimeMillis();
        if (!user.getUid().equals(p.getString(KEY_UID, null))) {
            p.edit().putString(KEY_UID, user.getUid()).putLong(KEY_LAST_ACTIVE, now).apply();
        } else {
            p.edit().putLong(KEY_LAST_ACTIVE, now).apply();
        }
    }

    /** True only when a remembered session has been idle for more than 30 days. */
    public static boolean isExpired(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return false;
        SharedPreferences p = prefs(context);
        long lastActive = p.getLong(KEY_LAST_ACTIVE, 0L);
        if (lastActive <= 0L) {
            // Old install without a timestamp: start counting from now.
            p.edit().putString(KEY_UID, user.getUid())
                    .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis()).apply();
            return false;
        }
        return System.currentTimeMillis() - lastActive > MAX_IDLE_MS;
    }

    /** Days left before an idle session expires (30 when freshly used). */
    public static int idleDaysLeft(Context context) {
        long lastActive = prefs(context).getLong(KEY_LAST_ACTIVE, 0L);
        if (lastActive <= 0L) return 30;
        long left = MAX_IDLE_MS - (System.currentTimeMillis() - lastActive);
        if (left <= 0L) return 0;
        return (int) Math.max(1, TimeUnit.MILLISECONDS.toDays(left));
    }

    /** Call on manual logout or when the session expired. */
    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }
}

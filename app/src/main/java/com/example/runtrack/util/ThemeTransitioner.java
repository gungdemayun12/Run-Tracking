package com.example.runtrack.util;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.runtrack.ui.theme.ThemeSwitchActivity;

/**
 * Makes dark/light theme switches smooth.
 *
 * <p>Changing the night mode recreates every started activity, which normally
 * flashes the bare window (black on dark mode, white on light mode) while the
 * new layout inflates. To avoid that, the current screen is snapshotted and a
 * translucent cover activity shows that snapshot. The theme is applied while
 * the cover is on top, the recreated activity is given time to draw its first
 * frame, and only then the snapshot fades away.</p>
 *
 * <p>The cover uses a translucent window on purpose: that keeps the activity
 * underneath in the STARTED state, so AppCompat still recreates it and
 * {@link #onActivityStarted(Activity)} can tell the cover when the fresh theme
 * is ready.</p>
 */
public final class ThemeTransitioner implements Application.ActivityLifecycleCallbacks {

    private static final long READY_DELAY_MS = 80L;

    private static final Object LOCK = new Object();
    private static Bitmap coverSnapshot;
    private static Runnable readyAction;
    private static boolean switchInFlight;

    /** Starts the smooth theme switch. */
    public static void switchTheme(Activity activity, boolean dark) {
        if (activity == null || activity.isFinishing()) {
            applyNightMode(dark);
            return;
        }

        Bitmap snapshot = capture(activity);
        synchronized (LOCK) {
            recycleLocked();
            coverSnapshot = snapshot;
            readyAction = null;
            switchInFlight = true;
        }

        Intent intent = new Intent(activity, ThemeSwitchActivity.class);
        intent.putExtra(ThemeSwitchActivity.EXTRA_DARK, dark);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
        } catch (Exception e) {
            synchronized (LOCK) {
                recycleLocked();
                switchInFlight = false;
            }
            applyNightMode(dark);
        }
    }

    private static void applyNightMode(boolean dark) {
        AppCompatDelegate.setDefaultNightMode(dark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /**
     * Hands the snapshot over to {@link ThemeSwitchActivity}. The cover owns the
     * bitmap afterwards and must recycle it once it is gone.
     */
    public static Bitmap takeCoverSnapshot() {
        synchronized (LOCK) {
            Bitmap snapshot = coverSnapshot;
            coverSnapshot = null;
            return snapshot;
        }
    }

    /** Called by the cover right before the night mode is changed. */
    public static void setReadyAction(Runnable action) {
        synchronized (LOCK) {
            readyAction = action;
        }
    }

    /** Called by the cover once it is done, to reset the shared state. */
    public static void finishSwitch() {
        synchronized (LOCK) {
            readyAction = null;
            switchInFlight = false;
            recycleLocked();
        }
    }

    private static Bitmap capture(Activity activity) {
        View decor = activity.getWindow() != null
                ? activity.getWindow().getDecorView() : null;
        if (decor == null) return null;
        int width = decor.getWidth();
        int height = decor.getHeight();
        if (width <= 0 || height <= 0) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            decor.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private static void recycleLocked() {
        if (coverSnapshot != null) {
            if (!coverSnapshot.isRecycled()) coverSnapshot.recycle();
            coverSnapshot = null;
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (activity instanceof ThemeSwitchActivity) return;

        Runnable action;
        synchronized (LOCK) {
            if (!switchInFlight) return;
            action = readyAction;
            if (action == null) return;
            readyAction = null;
        }

        // Give the recreated activity a couple of frames to draw the new theme
        // before the snapshot on top starts fading away.
        new Handler(Looper.getMainLooper()).postDelayed(action, READY_DELAY_MS);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}

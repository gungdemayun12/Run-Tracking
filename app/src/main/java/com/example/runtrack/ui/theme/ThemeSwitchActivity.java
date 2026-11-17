package com.example.runtrack.ui.theme;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.runtrack.util.ThemeTransitioner;

/**
 * Full-screen snapshot shown while the theme is being switched. The window is
 * translucent and declares {@code configChanges="uiMode"} in the manifest, so
 * this cover is never torn down during the switch and the user never sees the
 * bare window background that a plain {@code recreate()} produces.
 */
public class ThemeSwitchActivity extends Activity {

    public static final String EXTRA_DARK = "extra_dark";

    private static final long FADE_MS = 360L;
    private static final long SAFETY_MS = 1600L;

    private ImageView cover;
    private boolean applied;
    private boolean finished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bitmap snapshot = ThemeTransitioner.takeCoverSnapshot();

        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.FIT_XY);
        if (snapshot != null && !snapshot.isRecycled()) {
            cover.setImageBitmap(snapshot);
            applySystemBarColors(snapshot);
        }
        setContentView(cover, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Wait until the snapshot is actually on screen before touching the
        // theme, otherwise the very first frame could still flash the window.
        cover.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                cover.getViewTreeObserver().removeOnPreDrawListener(this);
                cover.postDelayed(ThemeSwitchActivity.this::applyTheme, 16L);
                return true;
            }
        });
    }

    private void applySystemBarColors(Bitmap snapshot) {
        try {
            Window window = getWindow();
            int top = snapshot.getPixel(snapshot.getWidth() / 2, 2);
            int bottom = snapshot.getPixel(snapshot.getWidth() / 2,
                    Math.max(0, snapshot.getHeight() - 3));
            window.setStatusBarColor(top);
            window.setNavigationBarColor(bottom);
        } catch (Exception ignored) {
        }
    }

    private void applyTheme() {
        if (applied || finished || isFinishing()) return;
        applied = true;
        boolean dark = getIntent().getBooleanExtra(EXTRA_DARK, true);
        ThemeTransitioner.setReadyAction(this::fadeOutAndFinish);
        AppCompatDelegate.setDefaultNightMode(dark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        // Safety net: never keep the cover longer than a moment, even if no
        // activity reports back (for example when switching from a dialog).
        new Handler(Looper.getMainLooper()).postDelayed(this::fadeOutAndFinish, SAFETY_MS);
    }

    private void fadeOutAndFinish() {
        if (finished) return;
        finished = true;
        cover.animate()
                .alpha(0f)
                .setDuration(FADE_MS)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .withEndAction(this::close)
                .start();
    }

    private void close() {
        ThemeTransitioner.finishSwitch();
        cover.setImageDrawable(null);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!finished) {
            ThemeTransitioner.finishSwitch();
        }
        if (cover != null) {
            cover.setImageDrawable(null);
        }
    }

    @Override
    public void onBackPressed() {
        // Swallow back presses while the theme is switching.
    }
}

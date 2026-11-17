package com.example.runtrack.ui.auth;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.runtrack.R;
import com.example.runtrack.data.AppSettings;
import com.example.runtrack.ui.main.MainNavigationActivity;
import com.example.runtrack.ui.onboard.OnboardingActivity;
import com.example.runtrack.ui.widget.RunnerLoaderView;
import com.example.runtrack.util.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final long MIN_DISPLAY_MS = 2400L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable navigationRunnable;
    private ObjectAnimator bobAnimator;
    private ObjectAnimator haloAnimator;
    private ObjectAnimator haloAnimatorY;
    private ValueAnimator progressAnimator;

    private View root;
    private View hero;
    private View wordmark;
    private View bottom;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        root = findViewById(R.id.splash_root);
        hero = findViewById(R.id.splash_hero);
        wordmark = findViewById(R.id.splash_wordmark);
        bottom = findViewById(R.id.splash_bottom);

        ImageView logo = findViewById(R.id.logo_splash);
        View pulseHalo = findViewById(R.id.logo_pulse_halo);
        TextView loadingText = findViewById(R.id.loading_text);
        ProgressBar progressBar = findViewById(R.id.loading_progress_bar);
        TextView progressText = findViewById(R.id.tv_progress_percent);
        RunnerLoaderView loader = findViewById(R.id.runner_loader);

        boolean animate = AppSettings.isAnimationsEnabled(this);
        if (animate) {
            playEntrance(logo, pulseHalo);
        } else {
            if (loader != null) {
                loader.setAutoStart(false);
                loader.stop();
            }
            hero.setAlpha(1f);
            wordmark.setAlpha(1f);
            bottom.setAlpha(1f);
        }

        // Determinate intro progress keeps the wait feeling deliberate.
        if (animate && progressBar != null && progressText != null) {
            progressAnimator = ValueAnimator.ofInt(0, 100);
            progressAnimator.setDuration(MIN_DISPLAY_MS - 150);
            progressAnimator.setInterpolator(new DecelerateInterpolator());
            progressAnimator.addUpdateListener(animation -> {
                int value = (int) animation.getAnimatedValue();
                progressBar.setProgress(value);
                progressText.setText(value + "%");
            });
            progressAnimator.start();
        } else {
            if (progressBar != null) progressBar.setProgress(100);
            if (progressText != null) progressText.setText("100%");
        }

        if (loadingText != null) {
            loadingText.setText("MEMPERSIAPKAN ENGINE...");
            handler.postDelayed(() -> {
                if (!isFinishing()) loadingText.setText("MEMUAT PETA & GPS...");
            }, 750);
            handler.postDelayed(() -> {
                if (!isFinishing()) loadingText.setText("MENYIAPKAN RUTE...");
            }, 1500);
            handler.postDelayed(() -> {
                if (!isFinishing()) loadingText.setText("SIAP BERLARI!");
            }, 2050);
        }

        navigationRunnable = this::leaveSplash;
        handler.postDelayed(navigationRunnable, MIN_DISPLAY_MS);
    }

    private void playEntrance(ImageView logo, View pulseHalo) {
        hero.setAlpha(0f);
        hero.setScaleX(0.86f);
        hero.setScaleY(0.86f);
        hero.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(750)
                .setInterpolator(new OvershootInterpolator(0.7f))
                .start();

        wordmark.setAlpha(0f);
        wordmark.setTranslationY(dp(18f));
        wordmark.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(260)
                .setDuration(520)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        bottom.setAlpha(0f);
        bottom.setTranslationY(dp(16f));
        bottom.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(420)
                .setDuration(520)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        if (logo != null) {
            bobAnimator = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, -dp(7f), dp(7f));
            bobAnimator.setDuration(1500);
            bobAnimator.setRepeatMode(ValueAnimator.REVERSE);
            bobAnimator.setRepeatCount(ValueAnimator.INFINITE);
            bobAnimator.start();
        }

        if (pulseHalo != null) {
            pulseHalo.setAlpha(0.2f);
            haloAnimator = ObjectAnimator.ofFloat(pulseHalo, View.SCALE_X, 1f, 1.22f);
            haloAnimator.setDuration(1500);
            haloAnimator.setRepeatMode(ValueAnimator.REVERSE);
            haloAnimator.setRepeatCount(ValueAnimator.INFINITE);
            haloAnimatorY = ObjectAnimator.ofFloat(pulseHalo, View.SCALE_Y, 1f, 1.22f);
            haloAnimatorY.setDuration(1500);
            haloAnimatorY.setRepeatMode(ValueAnimator.REVERSE);
            haloAnimatorY.setRepeatCount(ValueAnimator.INFINITE);
            haloAnimator.start();
            haloAnimatorY.start();
        }
    }

    private void leaveSplash() {
        if (isFinishing() || isDestroyed()) return;
        if (!AppSettings.isAnimationsEnabled(this)) {
            navigateNext();
            return;
        }
        root.animate()
                .alpha(0f)
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::navigateNext)
                .start();
    }

    private void navigateNext() {
        if (isFinishing() || isDestroyed()) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean seenOnboarding = prefs.getBoolean("onboard_seen", false);
        if (!seenOnboarding) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            return;
        }

        // Keep the Firebase session across restarts. It is only closed explicitly
        // from Profile, or when the remembered session has been idle for 30 days.
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && SessionManager.isExpired(this)) {
            FirebaseAuth.getInstance().signOut();
            SessionManager.clear(this);
            currentUser = null;
        }
        if (currentUser != null) {
            SessionManager.touch(this);
        }

        Intent next = currentUser != null
                ? new Intent(this, MainNavigationActivity.class)
                : new Intent(this, LoginActivity.class);
        startActivity(next);
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDestroy() {
        if (navigationRunnable != null) handler.removeCallbacks(navigationRunnable);
        handler.removeCallbacksAndMessages(null);
        if (bobAnimator != null) bobAnimator.cancel();
        if (haloAnimator != null) haloAnimator.cancel();
        if (haloAnimatorY != null) haloAnimatorY.cancel();
        if (progressAnimator != null) progressAnimator.cancel();
        super.onDestroy();
    }
}

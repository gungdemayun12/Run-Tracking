package com.example.runtrack.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.runtrack.R;

/**
 * Animated "run track" loader used on the splash screen.
 *
 * Draws a rotating glowing arc (the track), orbiting split markers, pulsing rings
 * and horizontal speed streaks. Everything is drawn with the platform Canvas API
 * so the intro stays smooth and needs no extra library or asset download.
 */
public class RunnerLoaderView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint streakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private ValueAnimator animator;
    private float phase;
    private boolean autoStart = true;
    private int orange;
    private int orangeLight;
    private int white;

    public RunnerLoaderView(Context context) {
        super(context);
        init();
    }

    public RunnerLoaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RunnerLoaderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        orange = ContextCompat.getColor(getContext(), R.color.run_orange);
        orangeLight = ContextCompat.getColor(getContext(), R.color.run_orange_light);
        white = ContextCompat.getColor(getContext(), R.color.white);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(1.5f));
        trackPaint.setColor(Color.argb(36, 255, 255, 255));

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(10f));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(Color.argb(38, 255, 107, 53));

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(3f));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);
        streakPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Allows the host screen to honour the global "animations" preference. */
    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
        if (!autoStart) stop();
    }

    public void start() {
        if (animator != null) return;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(3200);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        a.start();
        animator = a;
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (autoStart) start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (autoStart && visibility == VISIBLE) start();
        else if (!autoStart) stop();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float base = Math.min(getWidth(), getHeight()) / 2f - dp(14f);

        // Soft pulsing halo behind the logo.
        float pulse = (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2));
        glowPaint.setAlpha((int) (26 + pulse * 30));
        canvas.drawCircle(cx, cy, base - dp(6f), glowPaint);

        // Outer guide track.
        int rings = 3;
        for (int i = 0; i < rings; i++) {
            float r = base - i * dp(12f);
            trackPaint.setAlpha(i == 0 ? 46 : 24);
            canvas.drawCircle(cx, cy, r, trackPaint);
        }

        // Rotating gradient arc: the "track being painted".
        float radius = base - dp(2f);
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        int startColor = Color.argb(0, 255, 107, 53);
        SweepGradient sweep = new SweepGradient(cx, cy,
                new int[]{startColor, orange, orangeLight, orange, startColor},
                new float[]{0f, 0.12f, 0.22f, 0.34f, 0.5f});
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setRotate(phase * 360f, cx, cy);
        sweep.setLocalMatrix(matrix);
        arcPaint.setShader(sweep);
        canvas.drawArc(arcBounds, 0, 360, false, arcPaint);

        // Glow trail behind the arc head.
        arcPaint.setShader(null);
        arcPaint.setColor(Color.argb(70, 255, 154, 107));
        arcPaint.setStrokeWidth(dp(7f));
        canvas.drawArc(arcBounds, phase * 360f - 34f, 34f, false, arcPaint);
        arcPaint.setStrokeWidth(dp(3f));

        // Orbiting split markers.
        float[] orbit = {0f, 0.34f, 0.68f};
        for (int i = 0; i < orbit.length; i++) {
            double angle = (phase + orbit[i]) * Math.PI * 2;
            float r = base - dp(7f);
            float x = (float) (cx + Math.cos(angle) * r);
            float y = (float) (cy + Math.sin(angle) * r);
            dotPaint.setColor(i == 0 ? orangeLight : white);
            dotPaint.setAlpha(i == 0 ? 255 : 120);
            canvas.drawCircle(x, y, i == 0 ? dp(4f) : dp(2.4f), dotPaint);
        }

        // Speed streaks passing behind the runner.
        for (int i = 0; i < 4; i++) {
            float p = (phase * 1.6f + i * 0.27f) % 1f;
            float length = dp(30f + (i % 2) * 18f);
            float y = cy - base * 0.55f + i * (base * 1.1f / 3f);
            float left = -length + p * (getWidth() + length * 2f);
            int alpha = (int) (90 * Math.sin(Math.PI * p));
            int color = i % 2 == 0 ? orange : white;
            LinearGradient g = new LinearGradient(left, y, left + length, y,
                    new int[]{Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            streakPaint.setShader(g);
            streakPaint.setStrokeWidth(dp(2.2f));
            canvas.drawLine(left, y, left + length, y, streakPaint);
        }
        streakPaint.setShader(null);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

package com.example.runtrack.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.runtrack.R;

/**
 * Circular progress ring with tick marks, used for the weekly goal on the home hero.
 */
public class GoalRingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    private float progress;
    private float displayed;
    private ValueAnimator animator;

    public GoalRingView(Context context) {
        super(context);
        init();
    }

    public GoalRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GoalRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(13f));
        trackPaint.setColor(Color.argb(32, 255, 255, 255));

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(dp(1.4f));
        tickPaint.setColor(Color.argb(46, 255, 255, 255));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(dp(13f));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(dp(20f));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(Color.argb(54, 255, 107, 53));
    }

    public void setProgress(float value, boolean animate) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (!animate) {
            progress = clamped;
            displayed = clamped;
            invalidate();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(displayed, clamped);
        a.setDuration(1100);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(animation -> {
            displayed = (float) animation.getAnimatedValue();
            invalidate();
        });
        a.start();
        animator = a;
        progress = clamped;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - dp(16f);

        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // Tick ring for a premium stopwatch feel.
        for (int i = 0; i < 48; i++) {
            double angle = Math.toRadians(i * 360.0 / 48);
            float inner = radius + dp(9f);
            float outer = inner + dp(i % 4 == 0 ? 5f : 2.5f);
            canvas.drawLine(
                    (float) (cx + Math.cos(angle) * inner),
                    (float) (cy + Math.sin(angle) * inner),
                    (float) (cx + Math.cos(angle) * outer),
                    (float) (cy + Math.sin(angle) * outer),
                    tickPaint);
        }

        canvas.drawCircle(cx, cy, radius, trackPaint);

        if (displayed > 0.001f) {
            float sweep = 360f * displayed;

            SweepGradient gradient = new SweepGradient(cx, cy,
                    new int[]{
                            ContextCompat.getColor(getContext(), R.color.run_orange_light),
                            ContextCompat.getColor(getContext(), R.color.run_orange),
                            ContextCompat.getColor(getContext(), R.color.brand_dark),
                            ContextCompat.getColor(getContext(), R.color.run_orange_light)
                    },
                    new float[]{0f, 0.35f, 0.72f, 1f});
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.setRotate(-90f, cx, cy);
            gradient.setLocalMatrix(matrix);
            progressPaint.setShader(gradient);

            canvas.drawArc(bounds, -90f, sweep, false, glowPaint);
            canvas.drawArc(bounds, -90f, sweep, false, progressPaint);

            // Head marker.
            double endAngle = Math.toRadians(-90f + sweep);
            float hx = (float) (cx + Math.cos(endAngle) * radius);
            float hy = (float) (cy + Math.sin(endAngle) * radius);
            Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
            head.setColor(Color.WHITE);
            canvas.drawCircle(hx, hy, dp(4f), head);
        }
        progressPaint.setShader(null);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

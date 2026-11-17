package com.example.runtrack.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.runtrack.R;

import java.util.Locale;

/**
 * Lightweight animated bar chart for the last seven days of running distance.
 * Built directly on Canvas so it does not allocate a heavy chart library on the
 * home screen.
 */
public class WeeklyBarChartView extends View {

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mutedBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float[] values = new float[7];
    private String[] labels = {"Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min"};
    private float fraction;
    private ValueAnimator animator;

    public WeeklyBarChartView(Context context) {
        super(context);
        init();
    }

    public WeeklyBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WeeklyBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        barPaint.setColor(ContextCompat.getColor(getContext(), R.color.brand));
        mutedBarPaint.setColor(ContextCompat.getColor(getContext(), R.color.brand_soft_2));

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(sp(10f));
        labelPaint.setColor(ContextCompat.getColor(getContext(), R.color.text_tertiary));
        labelPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));

        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(sp(10f));
        valuePaint.setColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        valuePaint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));

        baselinePaint.setColor(ContextCompat.getColor(getContext(), R.color.border));
        baselinePaint.setStrokeWidth(dp(1f));
    }

    public void setData(float[] newValues, String[] newLabels) {
        if (newValues != null) {
            values = newValues;
        }
        if (newLabels != null && newLabels.length == 7) {
            labels = newLabels;
        }
        if (animator != null) animator.cancel();
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(800);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(animation -> {
            fraction = (float) animation.getAnimatedValue();
            invalidate();
        });
        a.start();
        animator = a;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float left = getPaddingLeft() + dp(4f);
        float right = getWidth() - getPaddingRight() - dp(4f);
        float top = getPaddingTop();
        float bottom = getHeight() - getPaddingBottom() - dp(20f);
        float chartHeight = Math.max(dp(20f), bottom - top - dp(18f));

        float max = 0.1f;
        for (float v : values) max = Math.max(max, v);

        canvas.drawLine(left, bottom, right, bottom, baselinePaint);

        int count = 7;
        float slot = (right - left) / count;
        float barWidth = Math.min(dp(22f), slot * 0.46f);
        int maxIndex = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[maxIndex]) maxIndex = i;
        }

        for (int i = 0; i < count; i++) {
            float cx = left + slot * i + slot / 2f;
            float ratio = values[i] / max;
            float height = chartHeight * ratio * fraction;
            boolean isBest = i == maxIndex && values[i] > 0.01f;

            if (isBest) {
                LinearGradient g = new LinearGradient(0, bottom - height, 0, bottom,
                        ContextCompat.getColor(getContext(), R.color.brand_light),
                        ContextCompat.getColor(getContext(), R.color.brand_dark),
                        Shader.TileMode.CLAMP);
                barPaint.setShader(g);
            }
            rect.set(cx - barWidth / 2f, bottom - height, cx + barWidth / 2f, bottom);
            float radius = barWidth / 2f;
            canvas.drawRoundRect(rect, radius, radius, isBest ? barPaint : mutedBarPaint);
            barPaint.setShader(null);

            labelPaint.setColor(isBest
                    ? ContextCompat.getColor(getContext(), R.color.brand)
                    : ContextCompat.getColor(getContext(), R.color.text_tertiary));
            canvas.drawText(labels[i], cx, bottom + dp(14f), labelPaint);

            if (isBest) {
                String text = String.format(Locale.getDefault(), "%.1f", values[i]);
                canvas.drawText(text, cx, bottom - height - dp(7f), valuePaint);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}

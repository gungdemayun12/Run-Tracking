package com.example.runtrack.ui.onboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.runtrack.R;
import com.example.runtrack.ui.auth.LoginActivity;
import com.google.android.material.button.MaterialButton;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 pager;
    private MaterialButton btnStart;
    private View[] dots;
    private int pageCount;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        pager = findViewById(R.id.viewPager);
        btnStart = findViewById(R.id.btn_start_onboard);
        dots = new View[]{
                findViewById(R.id.dot_1),
                findViewById(R.id.dot_2),
                findViewById(R.id.dot_3)
        };

        pager.setAdapter(new OnboardAdapter(this));
        pageCount = pager.getAdapter() != null ? pager.getAdapter().getItemCount() : 3;

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                boolean last = position == pageCount - 1;
                btnStart.setText(last ? "Mulai Sekarang" : "Lanjut");
            }
        });

        btnStart.setOnClickListener(v -> {
            int current = pager.getCurrentItem();
            if (current < pageCount - 1) {
                pager.setCurrentItem(current + 1, true);
            } else {
                finishOnboarding();
            }
        });

        View skip = findViewById(R.id.btn_skip_onboard);
        if (skip != null) skip.setOnClickListener(v -> finishOnboarding());

        updateDots(0);
    }

    private void updateDots(int position) {
        if (dots == null) return;
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] == null) continue;
            dots[i].setBackgroundResource(i == position
                    ? R.drawable.bg_dot_active
                    : R.drawable.bg_dot_inactive);
        }
    }

    private void finishOnboarding() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("onboard_seen", true).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (pager.getCurrentItem() > 0) {
            pager.setCurrentItem(pager.getCurrentItem() - 1, true);
        } else {
            super.onBackPressed();
        }
    }
}

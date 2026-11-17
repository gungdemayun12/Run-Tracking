package com.example.runtrack.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.runtrack.R;
import com.example.runtrack.data.AppSettings;
import com.example.runtrack.model.RunActivity;
import com.example.runtrack.ui.home.ActivityListAdapter;
import com.example.runtrack.ui.home.HomeViewModel;
import com.example.runtrack.ui.main.MainNavigationActivity;
import com.example.runtrack.ui.run.RunDetailActivity;
import com.example.runtrack.ui.run.RunningActivity;
import com.example.runtrack.ui.widget.GoalRingView;
import com.example.runtrack.ui.widget.WeeklyBarChartView;
import com.example.runtrack.util.ActivityStatsHelper;
import com.example.runtrack.util.FormatUtils;
import com.example.runtrack.util.UnitUtils;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeFragment extends Fragment {

    private HomeViewModel viewModel;
    private ActivityListAdapter adapter;

    private TextView tvDate;
    private TextView tvGreeting;
    private TextView tvWeeklyKm;
    private TextView tvWeeklyRuns;
    private TextView tvWeeklyTime;
    private TextView tvWeeklyCalories;
    private TextView tvWeeklyElevation;
    private TextView tvGoalLabel;
    private TextView tvGoalSubtitle;
    private TextView tvAvgPace;
    private TextView tvStreakDays;
    private TextView tvRunsGoal;
    private TextView tvWeekTotal;
    private TextView tvWeekHint;
    private TextView tvStreakTitle;
    private TextView tvStreakSubtitle;
    private TextView tvWeeklyKmUnit;
    private TextView tvPrLongest;
    private TextView tvPrFastest;
    private TextView tvPrRuns;
    private TextView tvPrHint;
    private List<RunActivity> lastActivities;
    private GoalRingView goalRing;
    private WeeklyBarChartView weeklyChart;
    private LinearProgressIndicator progressRunsGoal;
    private View emptyState;
    private RecyclerView recyclerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tvDate = view.findViewById(R.id.tv_date);
        tvGreeting = view.findViewById(R.id.tv_greeting);
        tvWeeklyKm = view.findViewById(R.id.tv_weekly_km);
        tvWeeklyRuns = view.findViewById(R.id.tv_weekly_runs);
        tvWeeklyTime = view.findViewById(R.id.tv_weekly_time);
        tvWeeklyCalories = view.findViewById(R.id.tv_weekly_calories);
        tvWeeklyElevation = view.findViewById(R.id.tv_weekly_elevation);
        tvGoalLabel = view.findViewById(R.id.tv_goal_label);
        tvGoalSubtitle = view.findViewById(R.id.tv_goal_subtitle);
        tvAvgPace = view.findViewById(R.id.tv_avg_pace);
        tvStreakDays = view.findViewById(R.id.tv_streak_days);
        tvRunsGoal = view.findViewById(R.id.tv_runs_goal);
        tvWeekTotal = view.findViewById(R.id.tv_week_total);
        tvWeekHint = view.findViewById(R.id.tv_week_hint);
        tvStreakTitle = view.findViewById(R.id.tv_streak_title);
        tvStreakSubtitle = view.findViewById(R.id.tv_streak_subtitle);
        tvWeeklyKmUnit = view.findViewById(R.id.tv_weekly_km_unit);
        tvPrLongest = view.findViewById(R.id.tv_pr_longest);
        tvPrFastest = view.findViewById(R.id.tv_pr_fastest);
        tvPrRuns = view.findViewById(R.id.tv_pr_runs);
        tvPrHint = view.findViewById(R.id.tv_pr_hint);
        if (tvWeeklyKmUnit != null) {
            tvWeeklyKmUnit.setText(UnitUtils.distanceUnit(requireContext())
                    .toUpperCase(Locale.getDefault()));
        }
        goalRing = view.findViewById(R.id.goal_ring);
        weeklyChart = view.findViewById(R.id.weekly_chart);
        progressRunsGoal = view.findViewById(R.id.progress_runs_goal);
        emptyState = view.findViewById(R.id.empty_state);
        recyclerView = view.findViewById(R.id.rv_recent_activities);

        View btnSettings = view.findViewById(R.id.btn_home_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> startActivity(new Intent(requireContext(),
                    com.example.runtrack.ui.settings.SettingsActivity.class)));
        }

        view.findViewById(R.id.btn_start_run).setOnClickListener(v -> openRunScreen());
        view.findViewById(R.id.btn_empty_start).setOnClickListener(v -> openRunScreen());
        view.findViewById(R.id.btn_see_all).setOnClickListener(v -> {
            if (requireActivity() instanceof MainNavigationActivity) {
                ((MainNavigationActivity) requireActivity())
                        .selectTab(MainNavigationActivity.TAB_HISTORY);
            }
        });

        adapter = new ActivityListAdapter(requireContext());
        adapter.setOnActivityClickListener(this::openDetail);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        tvDate.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
                .format(new Date()).toUpperCase(Locale.getDefault()));
        double goalKm = AppSettings.getWeeklyGoalKm(requireContext());
        tvGoalLabel.setText(UnitUtils.goal(requireContext(), goalKm));

        viewModel.getCurrentUserProfile().observe(getViewLifecycleOwner(), profile -> {
            String name = profile != null && profile.displayName != null
                    && !profile.displayName.isEmpty()
                    ? profile.displayName : viewModel.getUserDisplayName();
            tvGreeting.setText("Halo, " + name + "! 👋");
            adapter.setCurrentUserName(name);
        });

        viewModel.getRecentActivities(12).observe(getViewLifecycleOwner(), activities -> {
            adapter.updateItems(activities);
            boolean empty = activities == null || activities.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        viewModel.getAllActivities().observe(getViewLifecycleOwner(), this::calculateWeeklyStats);

        if (AppSettings.isAnimationsEnabled(requireContext())) {
            playEntrance(view);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded() || tvGoalLabel == null) return;
        if (adapter != null) {
            // Units may have changed in Settings while this screen was in the background.
            adapter.notifyDataSetChanged();
        }
        if (tvWeeklyKmUnit != null) {
            tvWeeklyKmUnit.setText(UnitUtils.distanceUnit(requireContext())
                    .toUpperCase(Locale.getDefault()));
        }
        tvGoalLabel.setText(UnitUtils.goal(requireContext(),
                AppSettings.getWeeklyGoalKm(requireContext())));
        if (lastActivities != null) {
            calculateWeeklyStats(lastActivities);
        }
    }

    private void playEntrance(View root) {
        int[] ids = {R.id.home_header, R.id.home_hero, R.id.chart_card, R.id.challenge_card};
        for (int i = 0; i < ids.length; i++) {
            View v = root.findViewById(ids[i]);
            if (v == null) continue;
            v.setAlpha(0f);
            v.setTranslationY(dp(24f));
            v.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(60L + i * 90L)
                    .setDuration(460)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void openRunScreen() {
        startActivity(new Intent(requireContext(), RunningActivity.class));
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
        }
    }

    private void openDetail(RunActivity activity) {
        Intent intent = new Intent(requireContext(), RunDetailActivity.class);
        intent.putExtra(RunDetailActivity.EXTRA_ACTIVITY_ID, activity.id);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
        }
    }

    private void calculateWeeklyStats(List<RunActivity> activities) {
        lastActivities = activities;
        if (!isAdded()) return;
        long weekStart = startOfWeekMillis();
        long todayStart = startOfTodayMillis();
        double totalKm = 0;
        int totalRuns = 0;
        double totalCalories = 0;
        double totalElevation = 0;
        long totalDurationMs = 0;
        float[] dailyKm = new float[7];
        int activeDays = 0;

        if (activities != null) {
            for (RunActivity activity : activities) {
                if (activity.startTime < weekStart) continue;
                totalKm += activity.getDistanceKm();
                totalCalories += activity.calories;
                totalElevation += activity.elevationGain;
                totalDurationMs += ActivityStatsHelper.getDurationMillis(activity);
                totalRuns++;

                int dayIndex = (int) ((activity.startTime - weekStart) / 86400000L);
                dayIndex = Math.max(0, Math.min(6, dayIndex));
                dailyKm[dayIndex] += (float) activity.getDistanceKm();
            }
            for (float v : dailyKm) if (v > 0.01f) activeDays++;
        }

        double goalKm = AppSettings.getWeeklyGoalKm(requireContext());
        int progress = goalKm > 0
                ? (int) Math.round(Math.min(100, totalKm / goalKm * 100)) : 0;

        tvWeeklyKm.setText(UnitUtils.distanceValue(requireContext(), totalKm, 1));
        tvWeeklyRuns.setText(String.valueOf(totalRuns));
        tvWeeklyCalories.setText(String.format(Locale.getDefault(), "%.0f", totalCalories));
        tvWeeklyTime.setText(FormatUtils.formatClock(totalDurationMs / 1000));
        tvWeeklyElevation.setText(String.format(Locale.getDefault(), "%.0f m", totalElevation));
        tvGoalSubtitle.setText(String.format(Locale.getDefault(),
                "%d%% tercapai • %s lagi", progress,
                UnitUtils.distance(requireContext(), Math.max(0, goalKm - totalKm), 1)));
        goalRing.setProgress(goalKm > 0 ? (float) Math.min(1.0, totalKm / goalKm) : 0f,
                AppSettings.isAnimationsEnabled(requireContext()));

        // Average pace for the week.
        String pace = "--:--";
        if (totalKm > 0.05 && totalDurationMs > 0) {
            double secondsPerKm = (totalDurationMs / 1000.0) / totalKm;
            pace = UnitUtils.paceValue(requireContext(), secondsPerKm);
        }
        tvAvgPace.setText(pace);

        // Weekly run target.
        int runsGoal = AppSettings.getWeeklyRunsGoal(requireContext());
        tvRunsGoal.setText(String.format(Locale.getDefault(), "%d / %d lari", totalRuns, runsGoal));
        progressRunsGoal.setMax(runsGoal);
        progressRunsGoal.setProgress(Math.min(totalRuns, runsGoal));

        // Streak across all activities (not only this week).
        int streak = calculateStreak(activities, todayStart);
        tvStreakDays.setText(String.format(Locale.getDefault(), "%d hari", streak));
        if (streak > 0) {
            tvStreakTitle.setText(String.format(Locale.getDefault(), "Streak %d hari beruntun 🔥", streak));
            boolean ranToday = hasActivityOnDay(activities, todayStart);
            tvStreakSubtitle.setText(ranToday
                    ? "Hebat! Kamu sudah lari hari ini."
                    : "Lari hari ini untuk menjaga streak-mu.");
        } else {
            tvStreakTitle.setText("Mulai streak pertamamu");
            tvStreakSubtitle.setText("Lari hari ini untuk membuka streak");
        }

        // Seven-day chart (Monday .. Sunday of the current week).
        tvWeekTotal.setText(UnitUtils.distance(requireContext(), totalKm, 1));
        tvWeekHint.setText(activeDays == 0
                ? "Belum ada aktivitas minggu ini"
                : String.format(Locale.getDefault(),
                "%d hari aktif • rata-rata %s/hari", activeDays,
                UnitUtils.distance(requireContext(), totalKm / 7.0, 1)));
        float[] displayDaily = new float[dailyKm.length];
        for (int i = 0; i < dailyKm.length; i++) {
            displayDaily[i] = (float) UnitUtils.toDisplayDistance(requireContext(), dailyKm[i]);
        }
        weeklyChart.setData(displayDaily, weekDayLabels());

        updatePersonalRecords(activities);
    }

    private void updatePersonalRecords(List<RunActivity> activities) {
        if (tvPrLongest == null || tvPrFastest == null || tvPrRuns == null) return;
        if (activities == null || activities.isEmpty()) {
            tvPrLongest.setText("--");
            tvPrFastest.setText("--");
            tvPrRuns.setText("0");
            if (tvPrHint != null) tvPrHint.setText("Belum ada data");
            return;
        }
        double longestKm = 0;
        double fastestPace = Double.MAX_VALUE;
        for (RunActivity activity : activities) {
            double km = activity.getDistanceKm();
            if (km > longestKm) longestKm = km;
            double pace = ActivityStatsHelper.getAvgPaceSecondsPerKm(activity);
            if (pace > 0 && km >= 0.2 && pace < fastestPace) fastestPace = pace;
        }
        tvPrLongest.setText(longestKm > 0
                ? UnitUtils.distance(requireContext(), longestKm, 2) : "--");
        tvPrFastest.setText(fastestPace < Double.MAX_VALUE
                ? UnitUtils.pace(requireContext(), fastestPace) : "--");
        tvPrRuns.setText(String.valueOf(activities.size()));
        if (tvPrHint != null) {
            tvPrHint.setText("Sepanjang waktu");
        }
    }

    private String[] weekDayLabels() {
        String[] labels = new String[7];
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(startOfWeekMillis());
        SimpleDateFormat format = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < 7; i++) {
            String value = format.format(calendar.getTime());
            labels[i] = value.length() > 3 ? value.substring(0, 3) : value;
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return labels;
    }

    private int calculateStreak(List<RunActivity> activities, long todayStart) {
        if (activities == null || activities.isEmpty()) return 0;
        Set<Long> days = new HashSet<>();
        for (RunActivity activity : activities) {
            days.add(dayKey(activity.startTime));
        }
        Calendar cursor = Calendar.getInstance();
        cursor.setTimeInMillis(todayStart);
        if (!days.contains(dayKey(cursor.getTimeInMillis()))) {
            cursor.add(Calendar.DAY_OF_YEAR, -1);
        }
        int streak = 0;
        while (days.contains(dayKey(cursor.getTimeInMillis())) && streak < 3650) {
            streak++;
            cursor.add(Calendar.DAY_OF_YEAR, -1);
        }
        return streak;
    }

    private boolean hasActivityOnDay(List<RunActivity> activities, long dayStart) {
        if (activities == null) return false;
        long key = dayKey(dayStart);
        for (RunActivity activity : activities) {
            if (dayKey(activity.startTime) == key) return true;
        }
        return false;
    }

    private long dayKey(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long startOfTodayMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long startOfWeekMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK); // Sunday = 1
        int daysSinceMonday = (dayOfWeek + 5) % 7;
        calendar.add(Calendar.DAY_OF_YEAR, -daysSinceMonday);
        return calendar.getTimeInMillis();
    }
}

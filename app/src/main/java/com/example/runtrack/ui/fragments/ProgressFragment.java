package com.example.runtrack.ui.fragments;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.runtrack.R;
import com.example.runtrack.data.AppSettings;
import com.example.runtrack.model.RunActivity;
import com.example.runtrack.model.UserProfile;
import com.example.runtrack.ui.home.HomeViewModel;
import com.example.runtrack.ui.widget.GoalRingView;
import com.example.runtrack.util.ActivityStatsHelper;
import com.example.runtrack.util.FormatUtils;
import com.example.runtrack.util.UnitUtils;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProgressFragment extends Fragment {

    private TextView tvTotalDistance;
    private TextView tvTotalRuns;
    private TextView tvTotalCalories;
    private TextView tvTotalTime;
    private TextView tvLongestRun;
    private TextView tvFastestPace;
    private TextView tvMostCalories;
    private TextView tvMonthlyKm;
    private TextView tvAvgPace;
    private TextView tvStreak;
    private TextView tvBmi;
    private TextView tvWeekProgress;
    private TextView tvWeekPercent;
    private TextView tvWeekGoal;
    private TextView tvChartUnit;
    private TextView tvBadgeSummary;
    private TextView tvBadgesEmpty;
    private ChipGroup chipGroupBadges;
    private GoalRingView goalRing;
    private BarChart barChart;
    private HomeViewModel viewModel;
    private UserProfile userProfile;
    private List<RunActivity> lastActivities;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_progress, container, false);

        tvTotalDistance = view.findViewById(R.id.tv_total_distance);
        tvTotalRuns = view.findViewById(R.id.tv_total_runs);
        tvTotalCalories = view.findViewById(R.id.tv_total_calories);
        tvTotalTime = view.findViewById(R.id.tv_total_time);
        tvLongestRun = view.findViewById(R.id.tv_longest_run);
        tvFastestPace = view.findViewById(R.id.tv_fastest_pace);
        tvMostCalories = view.findViewById(R.id.tv_most_calories);
        tvMonthlyKm = view.findViewById(R.id.tv_monthly_km);
        tvAvgPace = view.findViewById(R.id.tv_avg_pace);
        tvStreak = view.findViewById(R.id.tv_streak);
        tvBmi = view.findViewById(R.id.tv_bmi);
        tvWeekProgress = view.findViewById(R.id.tv_week_progress);
        tvWeekPercent = view.findViewById(R.id.tv_week_percent);
        tvWeekGoal = view.findViewById(R.id.tv_week_goal);
        goalRing = view.findViewById(R.id.goal_ring_week);
        barChart = view.findViewById(R.id.bar_chart_weekly);
        tvChartUnit = view.findViewById(R.id.tv_chart_unit);
        tvBadgeSummary = view.findViewById(R.id.tv_badge_summary);
        tvBadgesEmpty = view.findViewById(R.id.tv_badges_empty);
        chipGroupBadges = view.findViewById(R.id.chip_group_badges);
        if (tvChartUnit != null) {
            tvChartUnit.setText(UnitUtils.distanceUnit(requireContext()) + " / hari");
        }

        setupChart();

        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        viewModel.getAllActivities().observe(getViewLifecycleOwner(), this::populateStats);
        viewModel.getCurrentUserProfile().observe(getViewLifecycleOwner(), profile -> {
            userProfile = profile;
            updateBmi();
        });

        return view;
    }

    private void updateBmi() {
        if (userProfile == null || tvBmi == null
                || userProfile.heightCm <= 0 || userProfile.weightKg <= 0) {
            if (tvBmi != null) tvBmi.setText("--");
            return;
        }
        double heightM = userProfile.heightCm / 100.0;
        double bmi = userProfile.weightKg / (heightM * heightM);
        tvBmi.setText(String.format(Locale.getDefault(), "%.1f", bmi));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isAdded()) return;
        if (tvChartUnit != null) {
            tvChartUnit.setText(UnitUtils.distanceUnit(requireContext()) + " / hari");
        }
        if (lastActivities != null) {
            populateStats(lastActivities);
        }
    }

    private void populateStats(List<RunActivity> activities) {
        lastActivities = activities;
        if (!isAdded()) return;
        if (activities == null || activities.isEmpty()) {
            tvTotalDistance.setText(UnitUtils.distance(requireContext(), 0, 1));
            tvTotalRuns.setText("0");
            tvTotalCalories.setText("0 Cal");
            tvTotalTime.setText("00:00:00");
            tvLongestRun.setText("--");
            tvFastestPace.setText("--");
            tvMostCalories.setText("-- Cal");
            tvMonthlyKm.setText(UnitUtils.distance(requireContext(), 0, 1));
            tvAvgPace.setText("--");
            tvStreak.setText("0 hari");
            updateWeeklyGoal(0);
            setupEmptyChart();
            updateBmi();
            updateBadges(activities, 0, 0, 0, Double.MAX_VALUE);
            return;
        }

        double totalDistanceKm = 0;
        double totalCalories = 0;
        long totalDurationMs = 0;
        double monthlyKm = 0;
        double paceSum = 0;
        int paceCount = 0;

        double longestRunKm = 0;
        double fastestPace = Double.MAX_VALUE;
        double mostCalories = 0;

        long monthStartMs = startOfMonthMillis();
        long weekStartMs = startOfWeekMillis();
        double weekKm = 0;

        for (RunActivity activity : activities) {
            double km = activity.getDistanceKm();
            totalDistanceKm += km;
            totalCalories += activity.calories;
            totalDurationMs += ActivityStatsHelper.getDurationMillis(activity);

            if (activity.startTime >= monthStartMs) monthlyKm += km;
            if (activity.startTime >= weekStartMs) weekKm += km;

            double pace = ActivityStatsHelper.getAvgPaceSecondsPerKm(activity);
            if (pace > 0 && km > 0) {
                paceSum += pace;
                paceCount++;
            }

            if (km > longestRunKm) longestRunKm = km;
            if (pace > 0 && pace < fastestPace && km > 0.01) fastestPace = pace;
            if (activity.calories > mostCalories) mostCalories = activity.calories;
        }

        tvTotalDistance.setText(UnitUtils.distance(requireContext(), totalDistanceKm, 1));
        tvTotalRuns.setText(String.valueOf(activities.size()));
        tvTotalCalories.setText(String.format(Locale.getDefault(), "%.0f Cal", totalCalories));
        tvTotalTime.setText(FormatUtils.formatTimeSeconds(totalDurationMs / 1000));

        tvLongestRun.setText(longestRunKm > 0
                ? UnitUtils.distance(requireContext(), longestRunKm, 2) : "--");
        tvFastestPace.setText(fastestPace < Double.MAX_VALUE
                ? UnitUtils.pace(requireContext(), fastestPace) : "--");
        tvMostCalories.setText(String.format(Locale.getDefault(), "%.0f Cal", mostCalories));

        tvMonthlyKm.setText(UnitUtils.distance(requireContext(), monthlyKm, 1));
        tvAvgPace.setText(paceCount > 0
                ? UnitUtils.pace(requireContext(), paceSum / paceCount) : "--");
        tvStreak.setText(computeStreakDays(activities) + " hari");
        updateWeeklyGoal(weekKm);
        updateBmi();

        populateWeeklyChart(activities);
        updateBadges(activities, activities.size(), longestRunKm, totalDistanceKm,
                fastestPace);
    }

    private void updateBadges(List<RunActivity> activities, int totalRuns,
                              double longestKm, double totalKm, double fastestPace) {
        if (chipGroupBadges == null) return;
        chipGroupBadges.removeAllViews();
        int streak = activities == null || activities.isEmpty()
                ? 0 : computeStreakDays(activities);

        String[] labels = {
                "Lari pertama",
                "5K club",
                "10K club",
                "Half marathon",
                "10 lari",
                "50 lari",
                "100 km total",
                "500 km total",
                "Streak 3 hari",
                "Streak 7 hari",
                "Pace < 6:00"
        };
        boolean[] unlocked = {
                totalRuns >= 1,
                longestKm >= 5.0,
                longestKm >= 10.0,
                longestKm >= 21.0975,
                totalRuns >= 10,
                totalRuns >= 50,
                totalKm >= 100,
                totalKm >= 500,
                streak >= 3,
                streak >= 7,
                fastestPace < 360
        };

        int opened = 0;
        for (int i = 0; i < labels.length; i++) {
            if (!unlocked[i]) continue;
            opened++;
            Chip chip = new Chip(requireContext());
            chip.setText(labels[i]);
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setChipIconResource(R.drawable.ic_check_circle);
            chip.setChipIconVisible(true);
            chip.setChipIconTint(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.brand)));
            chip.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.brand_soft)));
            chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_dark));
            chip.setChipStrokeWidth(0f);
            chip.setEnsureMinTouchTargetSize(false);
            chipGroupBadges.addView(chip);
        }

        if (tvBadgeSummary != null) {
            tvBadgeSummary.setText(String.format(Locale.getDefault(),
                    "%d / %d", opened, labels.length));
        }
        if (tvBadgesEmpty != null) {
            tvBadgesEmpty.setVisibility(opened == 0 ? View.VISIBLE : View.GONE);
        }
        chipGroupBadges.setVisibility(opened == 0 ? View.GONE : View.VISIBLE);
    }

    private int computeStreakDays(List<RunActivity> activities) {
        SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        java.util.Set<String> days = new java.util.HashSet<>();
        for (RunActivity activity : activities) {
            days.add(keyFormat.format(new Date(activity.startTime)));
        }
        int streak = 0;
        Calendar calendar = Calendar.getInstance();
        for (int i = 0; i < 365; i++) {
            if (days.contains(keyFormat.format(calendar.getTime()))) {
                streak++;
                calendar.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                break;
            }
        }
        return streak;
    }

    private void setupChart() {
        if (barChart == null) return;
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setPinchZoom(false);
        barChart.setDoubleTapToZoomEnabled(false);
        barChart.setScaleEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.setExtraBottomOffset(8f);
        barChart.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        int textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);
        int gridColor = ContextCompat.getColor(requireContext(), R.color.border);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(11f);
        xAxis.setTextColor(textColor);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(gridColor);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextSize(10f);
        leftAxis.setTextColor(textColor);

        barChart.getAxisRight().setEnabled(false);
        barChart.animateY(700);
    }

    private void populateWeeklyChart(List<RunActivity> activities) {
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
        SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        Map<String, Float> dailyKm = new LinkedHashMap<>();
        List<String> dayLabels = new ArrayList<>();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            dailyKm.put(keyFormat.format(calendar.getTime()), 0f);
            dayLabels.add(dayFormat.format(calendar.getTime()));
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        for (RunActivity activity : activities) {
            String key = keyFormat.format(new Date(activity.startTime));
            if (dailyKm.containsKey(key)) {
                dailyKm.put(key, dailyKm.get(key) + (float) activity.getDistanceKm());
            }
        }

        ArrayList<BarEntry> entries = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, Float> entry : dailyKm.entrySet()) {
            entries.add(new BarEntry(index,
                    (float) UnitUtils.toDisplayDistance(requireContext(), entry.getValue())));
            index++;
        }
        renderBars(entries, dayLabels, true);
    }

    private void setupEmptyChart() {
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
        List<String> dayLabels = new ArrayList<>();
        ArrayList<BarEntry> entries = new ArrayList<>();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            dayLabels.add(dayFormat.format(calendar.getTime()));
            entries.add(new BarEntry(i, 0f));
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        renderBars(entries, dayLabels, false);
    }

    private void renderBars(ArrayList<BarEntry> entries, List<String> labels, boolean hasValues) {
        if (barChart == null) return;
        BarDataSet dataSet = new BarDataSet(entries,
                "Jarak (" + UnitUtils.distanceUnit(requireContext()) + ")");
        dataSet.setColor(ContextCompat.getColor(requireContext(), R.color.brand));
        dataSet.setValueTextSize(hasValues ? 10f : 0f);
        dataSet.setValueTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);

        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.setData(barData);
        barChart.invalidate();
    }

    private void updateWeeklyGoal(double weekKm) {
        if (tvWeekProgress == null) return;
        double goal = AppSettings.getWeeklyGoalKm(requireContext());
        if (goal <= 0) goal = 20;

        tvWeekGoal.setText(UnitUtils.goal(requireContext(), goal));
        tvWeekProgress.setText(String.format(Locale.getDefault(), "%s / %s",
                UnitUtils.distance(requireContext(), weekKm, 1),
                UnitUtils.goal(requireContext(), goal)));
        int percent = (int) Math.min(100, Math.round(weekKm / goal * 100));
        if (percent >= 100) {
            tvWeekPercent.setText("Target minggu ini tercapai! 🎉");
        } else if (weekKm <= 0) {
            tvWeekPercent.setText("Ayo mulai minggu ini");
        } else {
            tvWeekPercent.setText(String.format(Locale.getDefault(),
                    "%d%% dari target minggu ini", percent));
        }
        if (goalRing != null) {
            goalRing.setProgress((float) (weekKm / goal),
                    AppSettings.isAnimationsEnabled(requireContext()));
        }
    }

    private long startOfWeekMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int daysSinceMonday = (dayOfWeek + 5) % 7;
        calendar.add(Calendar.DAY_OF_YEAR, -daysSinceMonday);
        return calendar.getTimeInMillis();
    }

    private long startOfMonthMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}

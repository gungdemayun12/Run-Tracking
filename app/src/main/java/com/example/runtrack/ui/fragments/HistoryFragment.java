package com.example.runtrack.ui.fragments;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.runtrack.R;
import com.example.runtrack.model.RunActivity;
import com.example.runtrack.ui.home.ActivityListAdapter;
import com.example.runtrack.ui.home.HomeViewModel;
import com.example.runtrack.ui.run.RunDetailActivity;
import com.example.runtrack.util.ActivityStatsHelper;
import com.example.runtrack.util.FormatUtils;
import com.example.runtrack.util.UnitUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private static final int PERIOD_ALL = 0;
    private static final int PERIOD_WEEK = 1;
    private static final int PERIOD_MONTH = 2;
    private static final int PERIOD_QUARTER = 3;
    private static final int PERIOD_YEAR = 4;

    private static final String SORT_NEWEST = "newest";
    private static final String SORT_OLDEST = "oldest";
    private static final String SORT_DISTANCE = "distance";
    private static final String SORT_DURATION = "duration";

    private ActivityListAdapter adapter;
    private HistoryHeaderAdapter headerAdapter;
    private HistoryEmptyAdapter emptyAdapter;
    private HomeViewModel viewModel;

    private RecyclerView recyclerView;
    private EditText etSearch;
    private View filterBadge;

    private final List<RunActivity> allActivities = new ArrayList<>();
    private final List<RunActivity> deletedActivities = new ArrayList<>();
    private boolean trashMode;
    private int periodMode = PERIOD_ALL;
    private String sortMode = SORT_NEWEST;
    private Long filterDateFrom;
    private Long filterDateTo;

    private final SimpleDateFormat displayDateFormat =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
    private final SimpleDateFormat shortDateFormat =
            new SimpleDateFormat("dd MMM", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        recyclerView = view.findViewById(R.id.rv_history);
        etSearch = view.findViewById(R.id.et_search_history);
        filterBadge = view.findViewById(R.id.filter_badge);

        adapter = new ActivityListAdapter(requireContext());
        adapter.setOnActivityClickListener(this::openDetail);
        adapter.setShowShareButton(true);
        adapter.setOnShareClickListener(this::shareActivity);
        adapter.setOnDeleteClickListener(this::confirmSoftDelete);
        adapter.setOnRestoreClickListener(this::restoreActivity);
        adapter.setOnHardDeleteClickListener(this::confirmHardDelete);

        headerAdapter = new HistoryHeaderAdapter();
        emptyAdapter = new HistoryEmptyAdapter();

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(new ConcatAdapter(headerAdapter, adapter, emptyAdapter));
        recyclerView.setItemAnimator(null);

        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        viewModel.getCurrentUserProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null && profile.displayName != null) {
                adapter.setCurrentUserName(profile.displayName);
            } else {
                adapter.setCurrentUserName(viewModel.getUserDisplayName());
            }
        });

        viewModel.getAllActivities().observe(getViewLifecycleOwner(), activities -> {
            allActivities.clear();
            if (activities != null) allActivities.addAll(activities);
            if (!trashMode) applyFilters();
        });

        viewModel.getDeletedActivities().observe(getViewLifecycleOwner(), activities -> {
            deletedActivities.clear();
            if (activities != null) deletedActivities.addAll(activities);
            if (trashMode) applyFilters();
        });

        viewModel.getActivitiesFetchError().observe(getViewLifecycleOwner(), this::setFetchError);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        view.findViewById(R.id.btn_filter).setOnClickListener(v -> openFilterSheet());
        view.findViewById(R.id.btn_trash).setOnClickListener(v -> toggleTrashMode());
        return view;
    }

    /* ------------------------------------------------------------------ */
    /* Trash / soft delete                                                */
    /* ------------------------------------------------------------------ */

    private void toggleTrashMode() {
        trashMode = !trashMode;
        adapter.setTrashMode(trashMode);
        View trashBtn = getView() != null ? getView().findViewById(R.id.btn_trash) : null;
        if (trashBtn != null) {
            trashBtn.setBackgroundResource(trashMode
                    ? R.drawable.bg_icon_brand : R.drawable.bg_filter_button);
        }
        applyFilters();
    }

    private void confirmSoftDelete(RunActivity activity) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pindahkan ke tong sampah?")
                .setMessage("Aktivitas ini akan dipindahkan ke tong sampah. Kamu masih bisa memulihkannya nanti.")
                .setPositiveButton("Pindahkan", (d, w) -> {
                    viewModel.deleteActivity(activity.id);
                    android.widget.Toast.makeText(requireContext(),
                            "Dipindahkan ke tong sampah", android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void restoreActivity(RunActivity activity) {
        viewModel.restoreActivity(activity.id);
        android.widget.Toast.makeText(requireContext(),
                "Aktivitas dipulihkan", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void confirmHardDelete(RunActivity activity) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Hapus permanen?")
                .setMessage("Aktivitas ini akan dihapus selamanya dan tidak bisa dipulihkan lagi.")
                .setPositiveButton("Hapus", (d, w) -> {
                    viewModel.hardDeleteActivity(activity.id);
                    android.widget.Toast.makeText(requireContext(),
                            "Aktivitas dihapus permanen", android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    /* ------------------------------------------------------------------ */
    /* Filter sheet                                                       */
    /* ------------------------------------------------------------------ */

    private void openFilterSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(
                requireContext(), R.style.ThemeOverlay_RunTrack_BottomSheet);
        View sheet = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_history_filter, null);
        sheet.setBackgroundTintList(ColorStateList.valueOf(
                requireContext().getColor(R.color.surface)));
        dialog.setContentView(sheet);
        dialog.setOnShowListener(d -> {
            View bottom = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottom != null) {
                bottom.setBackgroundResource(android.R.color.transparent);
            }
        });

        final int[] tempPeriod = {periodMode};
        final String[] tempSort = {sortMode};
        final Long[] tempFrom = {filterDateFrom};
        final Long[] tempTo = {filterDateTo};

        Chip chipAll = sheet.findViewById(R.id.sheet_chip_all);
        Chip chipWeek = sheet.findViewById(R.id.sheet_chip_week);
        Chip chipMonth = sheet.findViewById(R.id.sheet_chip_month);
        Chip chipQuarter = sheet.findViewById(R.id.sheet_chip_quarter);
        Chip chipYear = sheet.findViewById(R.id.sheet_chip_year);
        chipAll.setChecked(tempPeriod[0] == PERIOD_ALL);
        chipWeek.setChecked(tempPeriod[0] == PERIOD_WEEK);
        chipMonth.setChecked(tempPeriod[0] == PERIOD_MONTH);
        chipQuarter.setChecked(tempPeriod[0] == PERIOD_QUARTER);
        chipYear.setChecked(tempPeriod[0] == PERIOD_YEAR);

        chipAll.setOnClickListener(v -> tempPeriod[0] = PERIOD_ALL);
        chipWeek.setOnClickListener(v -> tempPeriod[0] = PERIOD_WEEK);
        chipMonth.setOnClickListener(v -> tempPeriod[0] = PERIOD_MONTH);
        chipQuarter.setOnClickListener(v -> tempPeriod[0] = PERIOD_QUARTER);
        chipYear.setOnClickListener(v -> tempPeriod[0] = PERIOD_YEAR);

        TextInputEditText etFrom = sheet.findViewById(R.id.et_date_from_sheet);
        TextInputEditText etTo = sheet.findViewById(R.id.et_date_to_sheet);
        MaterialButton btnClearDates = sheet.findViewById(R.id.btn_clear_dates_sheet);

        if (tempFrom[0] != null) etFrom.setText(displayDateFormat.format(new Date(tempFrom[0])));
        if (tempTo[0] != null) etTo.setText(displayDateFormat.format(new Date(tempTo[0])));
        btnClearDates.setVisibility(tempFrom[0] != null || tempTo[0] != null
                ? View.VISIBLE : View.GONE);

        etFrom.setOnClickListener(v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Pilih tanggal mulai")
                    .build();
            picker.addOnPositiveButtonClickListener(selection -> {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(selection);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                tempFrom[0] = cal.getTimeInMillis();
                etFrom.setText(displayDateFormat.format(new Date(tempFrom[0])));
                btnClearDates.setVisibility(View.VISIBLE);
            });
            picker.show(getParentFragmentManager(), "date_from");
        });

        etTo.setOnClickListener(v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Pilih tanggal akhir")
                    .build();
            picker.addOnPositiveButtonClickListener(selection -> {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(selection);
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                tempTo[0] = cal.getTimeInMillis();
                etTo.setText(displayDateFormat.format(new Date(tempTo[0])));
                btnClearDates.setVisibility(View.VISIBLE);
            });
            picker.show(getParentFragmentManager(), "date_to");
        });

        btnClearDates.setOnClickListener(v -> {
            tempFrom[0] = null;
            tempTo[0] = null;
            etFrom.setText("");
            etTo.setText("");
            btnClearDates.setVisibility(View.GONE);
        });

        Chip sortNewest = sheet.findViewById(R.id.sheet_sort_newest);
        Chip sortOldest = sheet.findViewById(R.id.sheet_sort_oldest);
        Chip sortDistance = sheet.findViewById(R.id.sheet_sort_distance);
        Chip sortDuration = sheet.findViewById(R.id.sheet_sort_duration);
        sortNewest.setChecked(SORT_NEWEST.equals(tempSort[0]));
        sortOldest.setChecked(SORT_OLDEST.equals(tempSort[0]));
        sortDistance.setChecked(SORT_DISTANCE.equals(tempSort[0]));
        sortDuration.setChecked(SORT_DURATION.equals(tempSort[0]));

        sortNewest.setOnClickListener(v -> tempSort[0] = SORT_NEWEST);
        sortOldest.setOnClickListener(v -> tempSort[0] = SORT_OLDEST);
        sortDistance.setOnClickListener(v -> tempSort[0] = SORT_DISTANCE);
        sortDuration.setOnClickListener(v -> tempSort[0] = SORT_DURATION);

        sheet.findViewById(R.id.btn_reset_filter).setOnClickListener(v -> {
            tempPeriod[0] = PERIOD_ALL;
            tempSort[0] = SORT_NEWEST;
            tempFrom[0] = null;
            tempTo[0] = null;
            chipAll.setChecked(true);
            sortNewest.setChecked(true);
            etFrom.setText("");
            etTo.setText("");
            btnClearDates.setVisibility(View.GONE);
        });

        sheet.findViewById(R.id.btn_apply_filter).setOnClickListener(v -> {
            periodMode = tempPeriod[0];
            sortMode = tempSort[0];
            filterDateFrom = tempFrom[0];
            filterDateTo = tempTo[0];
            dialog.dismiss();
            applyFilters();
        });

        dialog.show();
    }

    /* ------------------------------------------------------------------ */
    /* Filtering                                                          */
    /* ------------------------------------------------------------------ */

    @Override
    public void onResume() {
        super.onResume();
        // Re-apply filters so distance units and summary follow Settings changes.
        applyFilters();
    }

    private void applyFilters() {
        if (adapter == null) return;
        List<RunActivity> source = trashMode ? deletedActivities : allActivities;
        List<RunActivity> filtered = new ArrayList<>();

        long periodStart = periodStartMillis();
        String query = etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase(Locale.getDefault()) : "";

        for (RunActivity activity : source) {
            if (periodStart > 0 && activity.startTime < periodStart) continue;
            if (filterDateFrom != null && activity.startTime < filterDateFrom) continue;
            if (filterDateTo != null && activity.startTime > filterDateTo) continue;
            if (!query.isEmpty() && !matchesQuery(activity, query)) continue;
            filtered.add(activity);
        }

        sortActivities(filtered);

        adapter.updateItems(filtered);
        filterBadge.setVisibility(hasActiveFilters() ? View.VISIBLE : View.GONE);

        if (filtered.isEmpty()) {
            emptyAdapter.setVisible(true);
            if (trashMode) {
                emptyAdapter.setTexts(
                        "Tong sampah kosong",
                        "Aktivitas yang kamu hapus akan muncul di sini.");
            } else {
                emptyAdapter.setTexts(
                        source.isEmpty() ? "Belum ada riwayat" : "Tidak ada hasil",
                        source.isEmpty()
                                ? "Setelah kamu selesai berlari, aktivitasmu akan muncul di sini."
                                : "Coba ubah kata kunci, periode, atau rentang tanggal.");
            }
        } else {
            emptyAdapter.setVisible(false);
        }

        double totalKm = 0;
        long totalMs = 0;
        for (RunActivity activity : filtered) {
            totalKm += activity.getDistanceKm();
            totalMs += ActivityStatsHelper.getDurationMillis(activity);
        }

        String subtitle;
        if (trashMode) {
            subtitle = source.isEmpty()
                    ? "Tong sampah kosong"
                    : String.format(Locale.getDefault(), "%d aktivitas di tong sampah", source.size());
        } else {
            subtitle = source.isEmpty()
                    ? "Belum ada aktivitas tersimpan"
                    : String.format(Locale.getDefault(), "%d dari %d aktivitas",
                            filtered.size(), source.size());
        }
        String summary = filtered.isEmpty()
                ? ""
                : String.format(Locale.getDefault(), "%s total • %s waktu aktif",
                        UnitUtils.distance(requireContext(), totalKm, 1),
                        FormatUtils.formatClock(totalMs / 1000));

        headerAdapter.setData(subtitle, summary, buildActiveFilterLabel());
    }

    private void sortActivities(List<RunActivity> activities) {
        Collections.sort(activities, (a, b) -> {
            switch (sortMode) {
                case SORT_OLDEST:
                    return Long.compare(a.startTime, b.startTime);
                case SORT_DISTANCE:
                    return Double.compare(b.getDistanceKm(), a.getDistanceKm());
                case SORT_DURATION:
                    return Long.compare(ActivityStatsHelper.getDurationMillis(b),
                            ActivityStatsHelper.getDurationMillis(a));
                case SORT_NEWEST:
                default:
                    return Long.compare(b.startTime, a.startTime);
            }
        });
    }

    private long periodStartMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        switch (periodMode) {
            case PERIOD_WEEK: {
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
                int daysSinceMonday = (dayOfWeek + 5) % 7;
                calendar.add(Calendar.DAY_OF_YEAR, -daysSinceMonday);
                return calendar.getTimeInMillis();
            }
            case PERIOD_MONTH: {
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                return calendar.getTimeInMillis();
            }
            case PERIOD_QUARTER: {
                calendar.add(Calendar.MONTH, -2);
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                return calendar.getTimeInMillis();
            }
            case PERIOD_YEAR: {
                calendar.set(Calendar.DAY_OF_YEAR, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                return calendar.getTimeInMillis();
            }
            case PERIOD_ALL:
            default:
                return 0L;
        }
    }

    private boolean hasActiveFilters() {
        return periodMode != PERIOD_ALL
                || filterDateFrom != null
                || filterDateTo != null
                || !SORT_NEWEST.equals(sortMode);
    }

    private String buildActiveFilterLabel() {
        List<String> parts = new ArrayList<>();
        switch (periodMode) {
            case PERIOD_WEEK:
                parts.add("Minggu ini");
                break;
            case PERIOD_MONTH:
                parts.add("Bulan ini");
                break;
            case PERIOD_QUARTER:
                parts.add("3 bulan terakhir");
                break;
            case PERIOD_YEAR:
                parts.add("Tahun ini");
                break;
            default:
                break;
        }
        if (filterDateFrom != null || filterDateTo != null) {
            String from = filterDateFrom != null
                    ? shortDateFormat.format(new Date(filterDateFrom)) : "Awal";
            String to = filterDateTo != null
                    ? shortDateFormat.format(new Date(filterDateTo)) : "Sekarang";
            parts.add(from + " – " + to);
        }
        switch (sortMode) {
            case SORT_OLDEST:
                parts.add("Terlama");
                break;
            case SORT_DISTANCE:
                parts.add("Jarak terjauh");
                break;
            case SORT_DURATION:
                parts.add("Durasi terlama");
                break;
            default:
                break;
        }
        return TextUtils.join(" · ", parts);
    }

    private void clearAllFilters() {
        periodMode = PERIOD_ALL;
        sortMode = SORT_NEWEST;
        filterDateFrom = null;
        filterDateTo = null;
        applyFilters();
    }

    private void setFetchError(String error) {
        if (headerAdapter == null) return;
        headerAdapter.setError(error == null || error.isEmpty()
                ? "" : "Sinkronisasi tertunda: " + error);
    }

    private boolean matchesQuery(RunActivity activity, String query) {
        String name = activity.activityName != null
                ? activity.activityName.toLowerCase(Locale.getDefault()) : "lari";
        if (name.contains(query)) return true;

        String km = String.format(Locale.getDefault(), "%.2f", activity.getDistanceKm());
        if (km.contains(query)) return true;

        String date = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(new Date(activity.startTime)).toLowerCase(Locale.getDefault());
        return date.contains(query);
    }

    /* ------------------------------------------------------------------ */
    /* Navigation & sharing                                               */
    /* ------------------------------------------------------------------ */

    private void openDetail(RunActivity activity) {
        Intent intent = new Intent(requireContext(), RunDetailActivity.class);
        intent.putExtra(RunDetailActivity.EXTRA_ACTIVITY_ID, activity.id);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
        }
    }

    private void shareActivity(RunActivity activity) {
        String text = "🏃 RunTrack • " + (activity.activityName != null ? activity.activityName : "Lari") + "\n"
                + "📍 Jarak: " + String.format(Locale.getDefault(), "%.2f km", activity.getDistanceKm()) + "\n"
                + "⏱ Waktu: " + ActivityStatsHelper.formatDuration(activity) + "\n"
                + "⚡ Pace: " + ActivityStatsHelper.formatPace(activity) + "\n"
                + "🔥 Kalori: " + String.format(Locale.getDefault(), "%.0f Cal", activity.calories) + "\n"
                + "⛰ Elevasi: " + String.format(Locale.getDefault(), "%.0f m", activity.elevationGain) + "\n\n"
                + "Dilacak dengan RunTrack";
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(share, "Bagikan"));
    }

    /* ------------------------------------------------------------------ */
    /* Empty state (kept inside the list so the header stays visible)     */
    /* ------------------------------------------------------------------ */

    private class HistoryEmptyAdapter extends RecyclerView.Adapter<HistoryEmptyAdapter.VH> {

        private boolean visible;
        private String title = "Belum ada riwayat";
        private String body = "Setelah kamu selesai berlari, aktivitasmu akan muncul di sini.";

        void setVisible(boolean visible) {
            if (this.visible == visible) return;
            this.visible = visible;
            notifyDataSetChanged();
        }

        void setTexts(String title, String body) {
            this.title = title;
            this.body = body;
            if (visible) notifyItemChanged(0);
        }

        @Override
        public int getItemCount() {
            return visible ? 1 : 0;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history_empty, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.tvTitle.setText(title);
            holder.tvBody.setText(body);
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView tvTitle;
            final TextView tvBody;

            VH(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tv_empty_title);
                tvBody = itemView.findViewById(R.id.tv_empty_body);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Scrolling header                                                   */
    /* ------------------------------------------------------------------ */

    private class HistoryHeaderAdapter extends RecyclerView.Adapter<HistoryHeaderAdapter.VH> {

        private String subtitle = "Semua aktivitas lari kamu";
        private String summary = "Ringkasan aktivitas";
        private String activeFilters = "";
        private String error = "";

        void setData(String subtitle, String summary, String activeFilters) {
            this.subtitle = subtitle;
            this.summary = summary;
            this.activeFilters = activeFilters;
            notifyItemChanged(0);
        }

        void setError(String error) {
            this.error = error;
            notifyItemChanged(0);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history_header, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.tvSubtitle.setText(subtitle);
            holder.tvSummary.setText(summary);

            boolean hasFilters = !TextUtils.isEmpty(activeFilters);
            boolean hasSummary = !TextUtils.isEmpty(summary);
            holder.tvSummary.setVisibility(hasSummary ? View.VISIBLE : View.GONE);
            holder.cardSummary.setVisibility(hasSummary || hasFilters ? View.VISIBLE : View.GONE);

            holder.rowActiveFilters.setVisibility(hasFilters ? View.VISIBLE : View.GONE);
            holder.tvActiveFilters.setText(hasFilters ? "Filter: " + activeFilters : "");
            holder.rowActiveFilters.setOnClickListener(v -> clearAllFilters());

            boolean hasError = !TextUtils.isEmpty(error);
            holder.tvError.setVisibility(hasError ? View.VISIBLE : View.GONE);
            holder.tvError.setText(error);
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView tvSubtitle;
            final TextView tvSummary;
            final TextView tvActiveFilters;
            final TextView tvError;
            final View rowActiveFilters;
            final View cardSummary;

            VH(@NonNull View itemView) {
                super(itemView);
                tvSubtitle = itemView.findViewById(R.id.tv_history_subtitle);
                tvSummary = itemView.findViewById(R.id.tv_history_summary);
                tvActiveFilters = itemView.findViewById(R.id.tv_active_filters);
                tvError = itemView.findViewById(R.id.tv_fetch_error);
                rowActiveFilters = itemView.findViewById(R.id.row_active_filter);
                cardSummary = itemView.findViewById(R.id.card_history_summary);
            }
        }
    }
}

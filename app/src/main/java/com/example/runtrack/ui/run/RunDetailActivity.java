package com.example.runtrack.ui.run;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.Base64;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.runtrack.R;
import com.example.runtrack.data.ActivityRepository;
import com.example.runtrack.data.UserRepository;
import com.example.runtrack.model.RunActivity;
import com.example.runtrack.model.UserProfile;
import com.example.runtrack.util.ActivityStatsHelper;
import com.example.runtrack.util.CalcUtils;
import com.example.runtrack.util.FormatUtils;
import com.example.runtrack.util.OsmdroidMapHelper;
import com.example.runtrack.util.UnitUtils;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RunDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ACTIVITY_ID = "extra_activity_id";
    /** Legacy extra kept so older callers still work. */
    public static final String EXTRA_ACTIVITY = "extra_activity";

    private RunActivity activity;
    private MapView mapView;
    private Polyline routePolyline;
    private int cardStyle = com.example.runtrack.util.ActivityCardRenderer.STYLE_DARK;
    private View loadingOverlay;
    private ActivityRepository repository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_run_details);
        repository = new ActivityRepository(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());
        View legacyEdit = findViewById(R.id.btn_edit);
        if (legacyEdit != null) legacyEdit.setVisibility(View.GONE);
        findViewById(R.id.btn_edit_title).setOnClickListener(v -> showRenameDialog());

        loadingOverlay = findViewById(R.id.detail_loading);
        mapView = findViewById(R.id.map_detail_view);
        OsmdroidMapHelper.setupInteractiveMap(mapView);

        MaterialButton btnShare = findViewById(R.id.btn_share);
        btnShare.setOnClickListener(v -> shareActivity());
        findViewById(R.id.btn_print).setOnClickListener(v -> showExportOptions());

        String id = getIntent().getStringExtra(EXTRA_ACTIVITY_ID);
        Object legacy = getIntent().getSerializableExtra(EXTRA_ACTIVITY);
        if (id == null && legacy instanceof RunActivity) {
            RunActivity serialized = (RunActivity) legacy;
            id = serialized.id;
            bindActivity(serialized);
        }

        if (id != null && !id.isEmpty()) {
            loadActivity(id);
        } else if (activity == null) {
            Toast.makeText(this, "Aktivitas tidak ditemukan", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadActivity(String id) {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Silakan login terlebih dahulu", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        repository.loadActivity(id, user.getUid(), new ActivityRepository.LoadCallback() {
            @Override
            public void onLoaded(RunActivity loaded) {
                runOnUiThread(() -> bindActivity(loaded));
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    if (activity == null) {
                        Toast.makeText(RunDetailActivity.this,
                                "Gagal memuat aktivitas: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        finish();
                    } else if (loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void bindActivity(RunActivity loaded) {
        if (loaded == null) return;
        activity = loaded;
        bindStats();
        populateSplits();
        bindMap();
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    private void bindStats() {
        TextView tvName = findViewById(R.id.tv_detail_activity_name);
        TextView tvUser = findViewById(R.id.tv_detail_user_name);
        TextView tvAvatar = findViewById(R.id.tv_detail_avatar_initial);
        TextView tvDate = findViewById(R.id.tv_detail_activity_date);
        TextView tvDistance = findViewById(R.id.tv_detail_distance);
        TextView tvDuration = findViewById(R.id.tv_detail_duration);
        TextView tvElevation = findViewById(R.id.tv_detail_elevation);
        TextView tvPace = findViewById(R.id.tv_detail_pace);
        TextView tvCalories = findViewById(R.id.tv_detail_calories);
        TextView tvMaxSpeed = findViewById(R.id.tv_detail_heart_rate);

        tvName.setText(activity.activityName != null ? activity.activityName : "Lari");

        String name = resolveUserName();
        tvUser.setText(name);
        tvAvatar.setText(initialOf(name));

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy • HH:mm", Locale.getDefault());
        tvDate.setText(sdf.format(new Date(activity.startTime)));

        tvDistance.setText(UnitUtils.distance(this, activity.getDistanceKm()));
        tvDuration.setText(ActivityStatsHelper.formatDuration(activity));
        tvElevation.setText(String.format(Locale.getDefault(), "%.0f m", activity.elevationGain));
        tvPace.setText(UnitUtils.pace(this,
                ActivityStatsHelper.getAvgPaceSecondsPerKm(activity)));
        tvCalories.setText(String.format(Locale.getDefault(), "%.0f Cal", activity.calories));
        tvMaxSpeed.setText(activity.maxSpeed > 0
                ? UnitUtils.speedWithUnit(this, activity.maxSpeed)
                : "-- " + UnitUtils.speedUnit(this));
    }

    private void populateSplits() {
        LinearLayout container = findViewById(R.id.ll_splits);
        TextView empty = findViewById(R.id.tv_splits_empty);
        TextView count = findViewById(R.id.tv_splits_count);
        TextView title = findViewById(R.id.tv_splits_title);
        if (container == null || activity == null) return;
        container.removeAllViews();
        if (title != null) {
            title.setText(UnitUtils.isMiles(this) ? "SPLIT PER MI" : "SPLIT PER KM");
        }

        List<com.example.runtrack.model.RoutePoint> points = activity.routePoints;
        long durationMs = ActivityStatsHelper.getDurationMillis(activity);
        // Prefer the distance recorded by the tracker: the raw polyline can contain
        // GPS spikes / re-anchor jumps that would otherwise invent dozens of splits.
        double totalMeters = activity.distanceMeters > 0 ? activity.distanceMeters : 0;
        if (totalMeters < 50) totalMeters = polylineMeters(points);

        if (totalMeters < 50 || durationMs <= 0) {
            if (empty != null) empty.setVisibility(View.VISIBLE);
            if (count != null) count.setText("--");
            return;
        }
        if (empty != null) empty.setVisibility(View.GONE);

        double splitMeters = UnitUtils.splitLengthKm(this) * 1000.0;
        int splitCount = (int) Math.ceil(totalMeters / splitMeters);
        // Keep long runs readable: fold everything beyond the 60th split into one row.
        int shownCount = Math.min(60, Math.max(1, splitCount));

        // Distance/time samples along a cleaned-up path (no spikes, monotonic time).
        SamplePath path = new SamplePath(points, totalMeters, durationMs);

        double[] segmentMeters = new double[shownCount];
        long[] segmentSeconds = new long[shownCount];
        double bestPace = Double.MAX_VALUE;
        int bestIndex = -1;
        for (int i = 0; i < shownCount; i++) {
            double start = i * splitMeters;
            double end = (i == shownCount - 1 && splitCount > shownCount)
                    ? totalMeters
                    : Math.min(totalMeters, start + splitMeters);
            double segment = end - start;
            if (segment < 5) {
                segmentMeters[i] = 0;
                continue;
            }
            segmentMeters[i] = segment;
            segmentSeconds[i] = Math.max(1L,
                    Math.round((path.timeAt(end) - path.timeAt(start))));
            double pace = segmentSeconds[i] / (segment / 1000.0);
            if (segment >= splitMeters * 0.9 && pace < bestPace) {
                bestPace = pace;
                bestIndex = i;
            }
        }

        if (count != null) {
            count.setText(splitCount + " split");
        }

        for (int i = 0; i < shownCount; i++) {
            if (segmentMeters[i] <= 0) continue;
            boolean isBest = i == bestIndex;
            boolean folded = i == shownCount - 1 && splitCount > shownCount;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));

            TextView label = new TextView(this);
            boolean fullSplit = segmentMeters[i] >= splitMeters * 0.999;
            String labelText;
            if (folded) {
                labelText = "Sisa " + String.format(Locale.getDefault(), "%.1f %s",
                        UnitUtils.toDisplayDistance(this, segmentMeters[i] / 1000.0),
                        UnitUtils.distanceUnit(this));
            } else if (fullSplit) {
                labelText = UnitUtils.splitLabel(this, i + 1);
            } else {
                labelText = String.format(Locale.getDefault(), "%.2f %s",
                        UnitUtils.toDisplayDistance(this, segmentMeters[i] / 1000.0),
                        UnitUtils.distanceUnit(this));
            }
            label.setText(isBest ? "⚡ " + labelText : labelText);
            label.setTextColor(isBest ? getColor(R.color.brand) : getColor(R.color.text_secondary));
            label.setTextSize(13f);
            if (isBest) label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);

            TextView duration = new TextView(this);
            duration.setText(FormatUtils.formatClock(segmentSeconds[i]));
            duration.setTextColor(getColor(R.color.text_primary));
            duration.setTextSize(13f);
            duration.setGravity(android.view.Gravity.END);
            duration.setPadding(0, 0, dp(12), 0);

            TextView pace = new TextView(this);
            double secPerKm = segmentSeconds[i] / (segmentMeters[i] / 1000.0);
            pace.setText(UnitUtils.paceValue(this, secPerKm));
            pace.setTextColor(isBest ? getColor(R.color.brand) : getColor(R.color.text_primary));
            pace.setTextSize(13f);
            pace.setTypeface(pace.getTypeface(), android.graphics.Typeface.BOLD);
            pace.setGravity(android.view.Gravity.END);
            pace.setMinWidth(dp(52));

            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(duration, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(pace, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            container.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (i < shownCount - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(getColor(R.color.border));
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
                dividerParams.topMargin = 0;
                container.addView(divider, dividerParams);
            }
        }
    }

    /** Rough route length ignoring GPS spikes. */
    private double polylineMeters(List<com.example.runtrack.model.RoutePoint> points) {
        if (points == null || points.size() < 2) return 0;
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            com.example.runtrack.model.RoutePoint a = points.get(i - 1);
            com.example.runtrack.model.RoutePoint b = points.get(i);
            double segment = CalcUtils.distanceMeters(a.lat, a.lng, b.lat, b.lng);
            if (segment <= 500.0) total += segment;
        }
        return total;
    }

    /**
     * Maps a distance along the run to elapsed seconds, using the recorded GPS
     * timestamps when they look sane and falling back to a proportional spread.
     */
    private static final class SamplePath {
        private final double[] cumMeters;
        private final long[] cumSeconds;

        SamplePath(List<com.example.runtrack.model.RoutePoint> points, double totalMeters,
                   long durationMs) {
            long durationSec = Math.max(1, durationMs / 1000);
            double[] fallbackCum = {0, totalMeters};
            long[] fallbackTime = {0, durationSec};

            if (points != null && points.size() > 1 && totalMeters > 0) {
                int n = points.size();
                double[] rawCum = new double[n];
                long[] rawTime = new long[n];
                int count = 1;
                long base = points.get(0).timestamp;
                double dist = 0;
                long lastTime = 0;
                for (int i = 1; i < n; i++) {
                    com.example.runtrack.model.RoutePoint a = points.get(i - 1);
                    com.example.runtrack.model.RoutePoint b = points.get(i);
                    double segment = CalcUtils.distanceMeters(a.lat, a.lng, b.lat, b.lng);
                    boolean spike = segment > 500.0;
                    if (!spike && a.timestamp > 0 && b.timestamp > 0) {
                        double dt = Math.max(0.2, (b.timestamp - a.timestamp) / 1000.0);
                        if (segment / dt > 12.0) spike = true;
                    }
                    if (!spike) dist += segment;
                    long t = (b.timestamp > 0 && base > 0) ? Math.max(0, b.timestamp - base) : 0;
                    lastTime = Math.max(lastTime, t);
                    rawCum[count] = dist;
                    rawTime[count] = lastTime;
                    count++;
                }

                double pathTotal = rawCum[count - 1];
                if (pathTotal > 1.0) {
                    double scale = totalMeters / pathTotal;
                    double pathSec = Math.max(0.5, rawTime[count - 1] / 1000.0);
                    boolean timeUsable = pathSec >= durationSec * 0.3 && pathSec <= durationSec * 3.0;
                    double[] cum = new double[count];
                    long[] seconds = new long[count];
                    for (int i = 0; i < count; i++) {
                        cum[i] = rawCum[i] * scale;
                        seconds[i] = timeUsable
                                ? Math.round((rawTime[i] / 1000.0) / pathSec * durationSec)
                                : Math.round(durationSec * (cum[i] / totalMeters));
                    }
                    cum[count - 1] = totalMeters;
                    seconds[count - 1] = durationSec;
                    this.cumMeters = cum;
                    this.cumSeconds = seconds;
                    return;
                }
            }
            this.cumMeters = fallbackCum;
            this.cumSeconds = fallbackTime;
        }

        long timeAt(double meters) {
            int last = cumMeters.length - 1;
            if (meters <= 0) return 0;
            if (meters >= cumMeters[last]) return cumSeconds[last];
            int lo = 0;
            int hi = last;
            while (lo + 1 < hi) {
                int mid = (lo + hi) / 2;
                if (cumMeters[mid] <= meters) lo = mid;
                else hi = mid;
            }
            double span = cumMeters[hi] - cumMeters[lo];
            long start = cumSeconds[lo];
            long end = cumSeconds[hi];
            if (span <= 0) return end;
            double ratio = (meters - cumMeters[lo]) / span;
            long value = Math.round(start + (end - start) * ratio);
            return Math.max(start, Math.min(end, value));
        }
    }

    private void bindMap() {
        if (mapView == null) return;
        try {
            mapView.onResume();
        } catch (Exception ignored) {
        }
        OsmdroidMapHelper.clearRouteOverlays(mapView);
        List<com.example.runtrack.model.RoutePoint> points =
                OsmdroidMapHelper.ensureRoutePoints(activity, -6.2088, 106.8456);
        if (points != null && !points.isEmpty()) {
            routePolyline = OsmdroidMapHelper.drawRoute(mapView, points, null);
            OsmdroidMapHelper.addStartEndMarkers(mapView, points);
            mapView.post(() -> OsmdroidMapHelper.fitRoute(
                    mapView, points, dp(48)));
        } else {
            OsmdroidMapHelper.centerOn(mapView, -6.2088, 106.8456, 15.0);
        }
    }

    private String resolveUserName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            UserProfile profile = UserRepository.getCached(user.getUid());
            if (profile != null && profile.displayName != null && !profile.displayName.isEmpty()) {
                return profile.displayName;
            }
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                return user.getDisplayName();
            }
        }
        return "Runner";
    }

    private String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) return "R";
        return name.trim().substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showRenameDialog() {
        if (activity == null || activity.id == null) return;

        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(activity.activityName != null ? activity.activityName : "Lari");
        input.setSelection(input.getText().length());
        input.setHint("Contoh: Lari Sore Santai");

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(dp(24), dp(8), dp(24), 0);
        container.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Edit judul lari")
                .setMessage("Beri nama aktivitas ini agar mudah kamu temukan di riwayat.")
                .setView(container)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String name = input.getText() != null
                            ? input.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Judul tidak boleh kosong", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    renameActivity(name);
                })
                .show();
    }

    private void renameActivity(String name) {
        repository.updateActivityName(activity.id, name, new ActivityRepository.SaveCallback() {
            @Override
            public void onSuccess(String documentId) {
                activity.activityName = name;
                bindStats();
                Toast.makeText(RunDetailActivity.this, "Judul diperbarui", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(RunDetailActivity.this,
                        "Gagal menyimpan judul: " + (e != null ? e.getMessage() : "unknown"),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showExportOptions() {
        if (activity == null) {
            Toast.makeText(this, "Aktivitas belum siap", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] styles = {
                "Gelap  •  seperti contoh (disarankan)",
                "Peta asli  •  rute di atas peta",
                "Transparan  •  PNG tanpa latar"
        };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Gaya kartu lari")
                .setItems(styles, (dialog, which) -> {
                    cardStyle = which;
                    showFormatOptions();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showFormatOptions() {
        String[] options = {
                "PNG  •  kualitas terbaik untuk dibagikan",
                "JPG  •  ukuran file lebih kecil",
                "PDF  •  siap dicetak (A4)"
        };
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Cetak kartu lari")
                .setItems(options, (dialog, which) -> exportCard(which))
                .setNegativeButton("Batal", null)
                .show();
    }

    private void exportCard(int option) {
        if (activity == null) return;
        View printButton = findViewById(R.id.btn_print);
        printButton.setEnabled(false);
        final RunActivity snapshot = activity;
        final String name = resolveUserName();
        final int requestedStyle = cardStyle;
        final int style = requestedStyle == com.example.runtrack.util.ActivityCardRenderer.STYLE_TRANSPARENT
                && option != 0
                ? com.example.runtrack.util.ActivityCardRenderer.STYLE_DARK
                : requestedStyle;

        final boolean needsMap = style
                == com.example.runtrack.util.ActivityCardRenderer.STYLE_MAP;

        final Runnable render = () -> {
            final Bitmap mapSnapshot = needsMap ? captureMapSnapshot() : null;
            Toast.makeText(this, "Menyiapkan kartu lari…", Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                Bitmap bitmap = null;
                try {
                    bitmap = com.example.runtrack.util.ActivityCardRenderer
                            .render(this, snapshot, name, style, mapSnapshot);
                    com.example.runtrack.util.ActivityCardRenderer.Result result;
                    if (option == 2) {
                        result = com.example.runtrack.util.ActivityCardRenderer
                                .exportPdf(this, bitmap, snapshot.activityName);
                    } else {
                        result = com.example.runtrack.util.ActivityCardRenderer
                                .exportImage(this, bitmap, option == 0, snapshot.activityName);
                    }
                    final com.example.runtrack.util.ActivityCardRenderer.Result finalResult = result;
                    runOnUiThread(() -> {
                        printButton.setEnabled(true);
                        if (isFinishing() || isDestroyed()) return;
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("Kartu berhasil dibuat")
                                .setMessage(finalResult.displayName
                                        + "\n\nTersimpan di Downloads/RunTrack (atau folder aplikasi) "
                                        + "dan siap dibagikan atau dicetak.")
                                .setPositiveButton("Bagikan", (dialog, which) ->
                                        com.example.runtrack.util.ActivityCardRenderer
                                                .share(this, finalResult, "Bagikan kartu lari"))
                                .setNeutralButton("Buka", (dialog, which) -> openExport(finalResult))
                                .setNegativeButton("Selesai", null)
                                .show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        printButton.setEnabled(true);
                        Toast.makeText(this, "Gagal membuat kartu: "
                                + (e.getMessage() != null ? e.getMessage() : "coba lagi"),
                                Toast.LENGTH_LONG).show();
                    });
                } finally {
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                    if (mapSnapshot != null && !mapSnapshot.isRecycled()) mapSnapshot.recycle();
                }
            }).start();
        };

        if (needsMap && mapView != null) {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
            mapView.invalidate();
            mapView.postDelayed(render, 900L);
        } else {
            render.run();
        }
    }

    /** Grabs what the detail map is currently showing, for the "Peta asli" card style. */
    private Bitmap captureMapSnapshot() {
        if (mapView == null) return null;
        int width = mapView.getWidth();
        int height = mapView.getHeight();
        if (width <= 0 || height <= 0) return null;
        try {
            mapView.invalidate();
            Bitmap shot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(shot);
            mapView.draw(canvas);
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
            return shot;
        } catch (Exception e) {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
            return null;
        }
    }

    private void openExport(com.example.runtrack.util.ActivityCardRenderer.Result result) {
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(result.uri, result.mimeType);
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(view, "Buka kartu lari"));
        } catch (Exception e) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void shareActivity() {
        if (activity == null) return;
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
        startActivity(Intent.createChooser(share, "Bagikan Lari"));
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mapView != null) mapView.onDetach();
        super.onDestroy();
    }
}

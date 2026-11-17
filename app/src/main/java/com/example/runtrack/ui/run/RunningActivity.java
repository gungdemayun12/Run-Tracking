package com.example.runtrack.ui.run;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import android.util.Log;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.runtrack.R;
import com.example.runtrack.data.ActivityRepository;
import com.example.runtrack.data.AppSettings;
import com.example.runtrack.data.UserRepository;
import com.example.runtrack.model.RoutePoint;
import com.example.runtrack.model.RunActivity;
import com.example.runtrack.model.UserProfile;
import com.example.runtrack.service.RunningService;
import com.example.runtrack.util.FormatUtils;
import com.example.runtrack.util.NotificationHelper;
import com.example.runtrack.util.OsmdroidMapHelper;
import com.example.runtrack.util.UnitUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RunningActivity extends AppCompatActivity {

    private static final int STATE_IDLE = 0;
    private static final int STATE_RUNNING = 1;
    private static final int STATE_PAUSED = 2;

    private int currentState = STATE_IDLE;

    private TextView tvTime;
    private TextView tvDistance;
    private TextView tvPace;
    private TextView tvSpeed;
    private TextView tvCalories;
    private TextView tvElevation;
    private TextView tvStatus;
    private TextView tvButtonLabel;
    private TextView tvGpsAccuracy;
    private TextView tvRunTitle;
    private TextView tvDistanceUnit;
    private TextView tvPaceUnit;
    private TextView tvSpeedUnit;

    private FloatingActionButton btnStart;
    private FloatingActionButton btnStop;
    private View layoutStop;
    private ImageButton btnBack;
    private ImageButton btnMapType;
    private ImageButton btnRecenter;
    private View mapTypeScroll;
    private View saveOverlay;

    private MapView mapView;
    private Polyline routePolyline;
    private MyLocationNewOverlay locationOverlay;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    private RunningService runningService;
    private boolean isBound;
    private boolean isSaving;

    private double userWeightKg = 70.0;
    private long lastMapUpdate;
    private int selectedTile = OsmdroidMapHelper.TILE_MAPNIK;
    private Location lastKnownLocation;
    private boolean isStartPending;
    private String runTitle;

    // Advanced live tracking features
    private View layoutCountdownOverlay;
    private TextView tvCountdownNumber;
    private TextView tvCountdownHint;
    private TextToSpeech tts;
    private boolean isAutoPaused;
    private long stationaryStartTime;
    private int lastAnnouncedKm;
    private int lastLapIndex;
    private ToneGenerator toneGenerator;
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;

    private ActivityRepository activityRepo;

    private ActivityResultLauncher<String[]> permissionLauncher;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!RunningService.ACTION_LOCATION_UPDATE.equals(intent.getAction())) return;
            bindState(intent);
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RunningService.LocalBinder binder = (RunningService.LocalBinder) service;
            runningService = binder.getService();
            isBound = true;
            syncFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            runningService = null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running);
        setupSystemBars();
        activityRepo = new ActivityRepository(this);

        bindViews();
        setupMap();
        setupActions();
        setupPermissionLauncher();
        loadUserWeight();
        initTts();

        runTitle = activityNameForNow();
        if (tvRunTitle != null) {
            tvRunTitle.setText(runTitle);
            tvRunTitle.setOnClickListener(v -> showRunTitleDialog(false));
        }

        if (AppSettings.isKeepScreenOn(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        updateUI();

        // Prompt immediately if location is not granted, or center on current user position
        if (hasLocationPermission()) {
            fetchInitialLocationAndCenter();
        } else {
            requestLocationPermissions();
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });

        if (RunningService.ACTION_STOP.equals(getIntent().getAction())) {
            new android.os.Handler(getMainLooper()).post(this::confirmStop);
        }
    }

    private void setupSystemBars() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.run_dark_bg));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.run_surface));
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(false);
    }

    private void bindViews() {
        tvTime = findViewById(R.id.tv_time);
        tvDistance = findViewById(R.id.tv_distance);
        tvPace = findViewById(R.id.tv_pace);
        tvSpeed = findViewById(R.id.tv_speed);
        tvCalories = findViewById(R.id.tv_calories);
        tvElevation = findViewById(R.id.tv_elevation);
        tvStatus = findViewById(R.id.tv_status);
        tvButtonLabel = findViewById(R.id.tv_button_label);
        tvGpsAccuracy = findViewById(R.id.tv_gps_accuracy);
        tvRunTitle = findViewById(R.id.tv_run_title);
        tvDistanceUnit = findViewById(R.id.tv_distance_unit);
        tvPaceUnit = findViewById(R.id.tv_pace_unit);
        tvSpeedUnit = findViewById(R.id.tv_speed_unit);
        if (tvDistanceUnit != null) {
            tvDistanceUnit.setText(UnitUtils.distanceLongUnit(this));
        }
        if (tvPaceUnit != null) {
            tvPaceUnit.setText("PACE /" + UnitUtils.distanceUnit(this).toUpperCase(Locale.getDefault()));
        }
        if (tvSpeedUnit != null) {
            tvSpeedUnit.setText(UnitUtils.speedUnit(this).toUpperCase(Locale.getDefault()));
        }

        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        layoutStop = findViewById(R.id.layout_stop);
        btnBack = findViewById(R.id.btn_back);
        btnMapType = findViewById(R.id.btn_map_type);
        btnRecenter = findViewById(R.id.btn_recenter);
        mapTypeScroll = findViewById(R.id.map_type_scroll);
        saveOverlay = findViewById(R.id.save_overlay);

        View panel = findViewById(R.id.bottom_panel);
        bottomSheetBehavior = BottomSheetBehavior.from(panel);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        layoutCountdownOverlay = findViewById(R.id.layout_countdown_overlay);
        tvCountdownNumber = findViewById(R.id.tv_countdown_number);
        tvCountdownHint = findViewById(R.id.tv_countdown_hint);
        View btnCancelCountdown = findViewById(R.id.btn_cancel_countdown);
        if (btnCancelCountdown != null) {
            btnCancelCountdown.setOnClickListener(v -> cancelCountdown());
        }
    }

    private void setupMap() {
        mapView = findViewById(R.id.map_view);
        OsmdroidMapHelper.setupInteractiveMap(mapView);
        selectedTile = AppSettings.getDefaultMapTile(this);
        OsmdroidMapHelper.setTileType(mapView, selectedTile);
        locationOverlay = OsmdroidMapHelper.addMyLocation(mapView, this);
        if (hasLocationPermission()) {
            fetchInitialLocationAndCenter();
        } else {
            OsmdroidMapHelper.centerOn(mapView, -6.200000, 106.816666, 15.0);
        }
    }

    private void fetchInitialLocationAndCenter() {
        if (!hasLocationPermission()) return;

        if (locationOverlay != null) {
            locationOverlay.enableMyLocation();
            locationOverlay.enableFollowLocation();
            locationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
                if (mapView != null && locationOverlay != null && locationOverlay.getMyLocation() != null) {
                    mapView.getController().setZoom(17.5);
                    mapView.getController().animateTo(locationOverlay.getMyLocation());
                }
            }));
        }

        try {
            FusedLocationProviderClient fusedClient = LocationServices.getFusedLocationProviderClient(this);
            fusedClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    lastKnownLocation = location;
                    if (mapView != null) {
                        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
                        mapView.getController().setZoom(17.5);
                        mapView.getController().animateTo(point);
                    }
                }
            });

            fusedClient.getCurrentLocation(gpsPriority(), null)
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            lastKnownLocation = location;
                            if (mapView != null) {
                                GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
                                mapView.getController().setZoom(17.5);
                                mapView.getController().animateTo(point);
                            }
                        }
                    });
        } catch (SecurityException e) {
            Log.e("RunningActivity", "Permission error fetching location", e);
        }
    }

    private void setupActions() {
        btnStart.setOnClickListener(v -> handleMainButton());
        btnStop.setOnClickListener(v -> confirmStop());
        btnBack.setOnClickListener(v -> handleBack());
        btnMapType.setOnClickListener(v -> toggleMapTypeMenu());
        btnRecenter.setOnClickListener(v -> recenterMap());

        Chip chipNormal = findViewById(R.id.chip_map_normal);
        Chip chipSatellite = findViewById(R.id.chip_map_satellite);
        Chip chipTopo = findViewById(R.id.chip_map_topo);
        if (chipNormal != null) {
            chipNormal.setOnClickListener(v -> {
                selectedTile = OsmdroidMapHelper.TILE_MAPNIK;
                OsmdroidMapHelper.setTileType(mapView, selectedTile);
                toggleMapTypeMenu();
            });
        }
        if (chipSatellite != null) {
            chipSatellite.setOnClickListener(v -> {
                selectedTile = OsmdroidMapHelper.TILE_SATELLITE;
                OsmdroidMapHelper.setTileType(mapView, selectedTile);
                toggleMapTypeMenu();
            });
        }
        if (chipTopo != null) {
            chipTopo.setOnClickListener(v -> {
                selectedTile = OsmdroidMapHelper.TILE_TOPO;
                OsmdroidMapHelper.setTileType(mapView, selectedTile);
                toggleMapTypeMenu();
            });
        }

        if (selectedTile == OsmdroidMapHelper.TILE_SATELLITE && chipSatellite != null) {
            chipSatellite.setChecked(true);
        } else if (selectedTile == OsmdroidMapHelper.TILE_TOPO && chipTopo != null) {
            chipTopo.setChecked(true);
        } else if (chipNormal != null) {
            chipNormal.setChecked(true);
        }
    }

    private void setupPermissionLauncher() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    if (fine != null && fine) {
                        fetchInitialLocationAndCenter();
                        if (isStartPending) {
                            isStartPending = false;
                            // Small delay to let location fix arrive
                            new android.os.Handler(getMainLooper()).postDelayed(
                                    this::startRunInternal, 500);
                        }
                    } else {
                        Toast.makeText(this,
                                "Izin lokasi diperlukan untuk melacak rute",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loadUserWeight() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        UserProfile profile = UserRepository.getCachedOrDefault(user.getUid());
        if (profile.weightKg > 0) userWeightKg = profile.weightKg;
    }

    /* ------------------------------------------------------------------ */
    /* Flow                                                                */
    /* ------------------------------------------------------------------ */

    private void handleMainButton() {
        switch (currentState) {
            case STATE_IDLE:
                showRunTitleDialog(true);
                break;
            case STATE_RUNNING:
                pauseRun();
                break;
            case STATE_PAUSED:
                resumeRun();
                break;
        }
    }

    private void showRunTitleDialog(boolean startAfter) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        String current = runTitle != null && !runTitle.trim().isEmpty()
                ? runTitle : activityNameForNow();
        input.setText(current);
        input.setSelection(input.getText().length());
        input.setHint("Contoh: Lari Sore Santai");

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        int pad = Math.round(22 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, dpToPx(6), pad, 0);
        container.addView(input, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(startAfter ? "Siap berlari?" : "Edit judul lari")
                .setMessage(startAfter
                        ? "Beri nama sesi ini supaya mudah ditemukan di riwayat."
                        : "Ubah nama sesi lari yang sedang berjalan.")
                .setView(container)
                .setNegativeButton(startAfter ? "Batal" : "Tutup", null)
                .setPositiveButton(startAfter ? "Mulai" : "Simpan", (dialog, which) -> {
                    String name = input.getText() != null
                            ? input.getText().toString().trim() : "";
                    runTitle = name.isEmpty() ? activityNameForNow() : name;
                    if (tvRunTitle != null) tvRunTitle.setText(runTitle);
                    if (startAfter) startRun();
                })
                .show();
    }

    private int dpToPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void startRun() {
        if (!hasLocationPermission()) {
            isStartPending = true;
            requestLocationPermissions();
            return;
        }
        int countdownSec = AppSettings.getCountdownDuration(this);
        if (countdownSec > 0) {
            startCountdown(countdownSec);
        } else {
            startRunInternal();
        }
    }

    private void startCountdown(int seconds) {
        if (layoutCountdownOverlay == null || tvCountdownNumber == null) {
            startRunInternal();
            return;
        }
        layoutCountdownOverlay.setVisibility(View.VISIBLE);
        runCountdownStep(seconds);
    }

    private void runCountdownStep(int remaining) {
        if (remaining <= 0) {
            if (layoutCountdownOverlay != null) {
                layoutCountdownOverlay.setVisibility(View.GONE);
            }
            beep(ToneGenerator.TONE_PROP_ACK);
            hapticTap();
            speakText("Mulai!");
            startRunInternal();
            return;
        }
        tvCountdownNumber.setText(String.valueOf(remaining));
        beep(ToneGenerator.TONE_PROP_BEEP);
        hapticTap();
        try {
            tvCountdownNumber.startAnimation(AnimationUtils.loadAnimation(this, R.anim.countdown_pop));
        } catch (Exception ignored) {
        }
        speakText(String.valueOf(remaining));

        countdownRunnable = () -> runCountdownStep(remaining - 1);
        countdownHandler.postDelayed(countdownRunnable, 1000);
    }

    private void cancelCountdown() {
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
        if (layoutCountdownOverlay != null) {
            layoutCountdownOverlay.setVisibility(View.GONE);
        }
        currentState = STATE_IDLE;
        updateUI();
    }

    private void initTts() {
        if (!AppSettings.isVoiceCoachEnabled(this)) return;
        try {
            tts = new TextToSpeech(getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    int res = tts.setLanguage(new Locale("id", "ID"));
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setLanguage(Locale.US);
                    }
                }
            });
        } catch (Exception e) {
            Log.w("RunningActivity", "TTS init error: " + e.getMessage());
        }
    }

    private void speakText(String text) {
        if (tts != null && AppSettings.isVoiceCoachEnabled(this)) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "run_tts");
        }
    }

    private void startRunInternal() {
        if (currentState != STATE_IDLE) return;
        Intent intent = new Intent(this, RunningService.class);
        intent.setAction(RunningService.ACTION_START);
        intent.putExtra(RunningService.EXTRA_WEIGHT_KG, userWeightKg);

        // Pass current location so the service knows the starting position immediately
        if (lastKnownLocation != null) {
            intent.putExtra(RunningService.EXTRA_INITIAL_LAT, lastKnownLocation.getLatitude());
            intent.putExtra(RunningService.EXTRA_INITIAL_LNG, lastKnownLocation.getLongitude());
            intent.putExtra(RunningService.EXTRA_INITIAL_ALT,
                    lastKnownLocation.hasAltitude() ? lastKnownLocation.getAltitude() : 0.0);
        }

        ContextCompat.startForegroundService(this, intent);
        currentState = STATE_RUNNING;
        if (locationOverlay != null) locationOverlay.enableFollowLocation();
        beep(ToneGenerator.TONE_PROP_ACK);
        hapticTap();
        updateUI();
    }

    private void pauseRun() {
        startService(new Intent(this, RunningService.class)
                .setAction(RunningService.ACTION_PAUSE));
        if (locationOverlay != null) locationOverlay.disableFollowLocation();
        beep(ToneGenerator.TONE_PROP_BEEP2);
        hapticTap();
        updateUI();
    }

    private void resumeRun() {
        startService(new Intent(this, RunningService.class)
                .setAction(RunningService.ACTION_RESUME));
        if (locationOverlay != null) locationOverlay.enableFollowLocation();
        beep(ToneGenerator.TONE_PROP_BEEP2);
        hapticTap();
        updateUI();
    }

    private int gpsPriority() {
        switch (AppSettings.getGpsMode(this)) {
            case 1:
                return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
            case 2:
                return Priority.PRIORITY_LOW_POWER;
            case 0:
            default:
                return Priority.PRIORITY_HIGH_ACCURACY;
        }
    }

    private void hapticTap() {
        if (!AppSettings.isVibrationEnabled(this)) return;
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        28, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) {
        }
    }

    private void beep(int tone) {
        if (!AppSettings.isSoundEffectsEnabled(this)) return;
        try {
            if (toneGenerator == null) {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 65);
            }
            toneGenerator.startTone(tone, 110);
        } catch (Exception ignored) {
        }
    }

    private void confirmStop() {
        if (currentState == STATE_IDLE) return;
        new AlertDialog.Builder(this)
                .setTitle("Sudahi lari?")
                .setMessage("Aktivitas akan disimpan ke riwayat.")
                .setPositiveButton("Simpan & Selesai", (dialog, which) -> saveAndFinish())
                .setNegativeButton("Lanjut lari", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void saveAndFinish() {
        if (isSaving) return;
        if (!isBound || runningService == null) {
            Toast.makeText(this, "Data lari belum siap, coba lagi", Toast.LENGTH_LONG).show();
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Silakan login terlebih dahulu", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        isSaving = true;
        if (saveOverlay != null) saveOverlay.setVisibility(View.VISIBLE);
        btnStart.setEnabled(false);
        btnStop.setEnabled(false);

        List<RoutePoint> points = applyPrivacyZone(
                new ArrayList<>(runningService.getRoutePoints()));

        RunActivity activity = new RunActivity();
        activity.userId = user.getUid();
        activity.activityName = runTitle != null && !runTitle.trim().isEmpty()
                ? runTitle.trim() : activityNameForNow();
        activity.startTime = runningService.getSessionStartTime();
        activity.endTime = System.currentTimeMillis();
        activity.distanceMeters = runningService.getTotalDistanceMeters();
        activity.calories = runningService.getCalories();
        activity.elevationGain = runningService.getElevationGain();
        activity.maxSpeed = runningService.getMaxSpeed();
        activity.durationMillis = runningService.getActiveElapsedMillis();
        activity.avgPace = runningService.getAvgPace();
        activity.routePoints = points;

        activityRepo.saveActivity(activity, new ActivityRepository.SaveCallback() {
            @Override
            public void onSuccess(String documentId) {
                runOnUiThread(() -> {
                    stopService();
                    hapticTap();
                    NotificationHelper.notifyRunSaved(RunningActivity.this,
                            activity.activityName, activity.distanceMeters,
                            activity.durationMillis / 1000);
                    Toast.makeText(RunningActivity.this,
                            "Aktivitas tersimpan di riwayat", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    isSaving = false;
                    if (saveOverlay != null) saveOverlay.setVisibility(View.GONE);
                    btnStart.setEnabled(true);
                    btnStop.setEnabled(true);
                    Toast.makeText(RunningActivity.this,
                            "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void stopService() {
        try {
            startService(new Intent(this, RunningService.class)
                    .setAction(RunningService.ACTION_STOP));
        } catch (Exception ignored) {
        }
    }

    private String activityNameForNow() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 11) return "Lari Pagi";
        if (hour < 15) return "Lari Siang";
        if (hour < 19) return "Lari Sore";
        return "Lari Malam";
    }

    /**
     * Trims the first and last points that fall inside the privacy radius so the
     * saved route does not expose the exact home / finish location.
     */
    private List<RoutePoint> applyPrivacyZone(List<RoutePoint> points) {
        if (!AppSettings.isPrivacyZoneEnabled(this) || points == null || points.size() < 4) {
            return points;
        }
        float radius = AppSettings.getPrivacyRadiusMeters(this);
        RoutePoint first = points.get(0);
        RoutePoint last = points.get(points.size() - 1);

        int start = 0;
        while (start < points.size() - 1
                && distanceBetween(first, points.get(start)) < radius) {
            start++;
        }
        int end = points.size() - 1;
        while (end > start && distanceBetween(last, points.get(end)) < radius) {
            end--;
        }
        if (end - start < 2) return points;
        return new ArrayList<>(points.subList(start, end + 1));
    }

    private float distanceBetween(RoutePoint a, RoutePoint b) {
        float[] result = new float[1];
        Location.distanceBetween(a.getLat(), a.getLng(), b.getLat(), b.getLng(), result);
        return result[0];
    }

    private void handleBack() {
        if (currentState == STATE_IDLE) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Keluar dari pelacakan?")
                .setMessage("Lari masih berlangsung. Simpan dulu agar tidak hilang.")
                .setPositiveButton("Simpan", (dialog, which) -> saveAndFinish())
                .setNegativeButton("Batal", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /* ------------------------------------------------------------------ */
    /* Service state                                                       */
    /* ------------------------------------------------------------------ */

    private void syncFromService() {
        if (runningService == null) return;
        if (!runningService.isCurrentlyTracking()) {
            currentState = STATE_IDLE;
        } else if (runningService.isCurrentlyPaused()) {
            currentState = STATE_PAUSED;
        } else {
            currentState = STATE_RUNNING;
        }
        updateUI();
        tvDistance.setText(UnitUtils.distanceValue(this,
                runningService.getTotalDistanceMeters() / 1000.0, 2));
        tvCalories.setText(String.format(Locale.getDefault(), "%.0f", runningService.getCalories()));
        tvElevation.setText(String.format(Locale.getDefault(), "%.0f m", runningService.getElevationGain()));
        tvPace.setText(UnitUtils.paceValue(this, runningService.getAvgPace()));
        tvSpeed.setText(UnitUtils.speed(this, runningService.getCurrentSpeedMps()));
        tvTime.setText(FormatUtils.formatClock(runningService.getActiveElapsedMillis() / 1000));
        updateMapFromService();
    }

    private void bindState(Intent intent) {
        double distance = intent.getDoubleExtra(RunningService.EXTRA_DISTANCE, 0);
        double calories = intent.getDoubleExtra(RunningService.EXTRA_CALORIES, 0);
        double avgPace = intent.getDoubleExtra(RunningService.EXTRA_AVG_PACE, 0);
        double elevation = intent.getDoubleExtra(RunningService.EXTRA_ELEVATION, 0);
        double currentSpeed = intent.getDoubleExtra(RunningService.EXTRA_CURRENT_SPEED, 0);
        long elapsedSeconds = intent.getLongExtra(RunningService.EXTRA_ELAPSED_SECONDS, 0);
        boolean paused = intent.getBooleanExtra(RunningService.EXTRA_PAUSED, false);
        boolean tracking = intent.getBooleanExtra(RunningService.EXTRA_TRACKING, false);
        float accuracy = intent.getFloatExtra(RunningService.EXTRA_GPS_ACCURACY, 0);

        tvDistance.setText(UnitUtils.distanceValue(this, distance / 1000.0, 2));
        tvCalories.setText(String.format(Locale.getDefault(), "%.0f", calories));
        tvElevation.setText(String.format(Locale.getDefault(), "%.0f m", elevation));
        tvPace.setText(UnitUtils.paceValue(this, avgPace));
        tvSpeed.setText(UnitUtils.speed(this, currentSpeed));
        tvTime.setText(FormatUtils.formatClock(elapsedSeconds));

        if (accuracy > 0) {
            tvGpsAccuracy.setText(String.format(Locale.getDefault(), "GPS ±%.0f m", accuracy));
        } else {
            tvGpsAccuracy.setText("GPS ±-- m");
        }

        if (!tracking) {
            currentState = STATE_IDLE;
        } else {
            currentState = paused ? STATE_PAUSED : STATE_RUNNING;
        }
        updateUI();
        updateMapFromService();

        // Auto-pause check
        if (tracking && AppSettings.isAutoPauseEnabled(this)) {
            if (!paused && currentSpeed < 0.25) {
                if (stationaryStartTime == 0) {
                    stationaryStartTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - stationaryStartTime > 4000) {
                    if (!isAutoPaused) {
                        isAutoPaused = true;
                        pauseRun();
                        speakText("Lari dijeda otomatis");
                    }
                }
            } else if (paused && isAutoPaused && currentSpeed > 0.8) {
                isAutoPaused = false;
                stationaryStartTime = 0;
                resumeRun();
                speakText("Lari dilanjutkan");
            } else if (currentSpeed >= 0.25) {
                stationaryStartTime = 0;
            }
        }

        // Voice Coach: user-configurable distance interval + auto lap.
        if (AppSettings.isVoiceCoachEnabled(this)) {
            boolean miles = UnitUtils.isMiles(this);
            float intervalKm = Math.max(0.25f, AppSettings.getVoiceIntervalKm(this));
            int milestone = (int) Math.floor((distance / 1000.0) / intervalKm);
            if (milestone > lastAnnouncedKm && milestone > 0) {
                lastAnnouncedKm = milestone;
                double kmDone = milestone * intervalKm;
                String msg = String.format(Locale.getDefault(),
                        "%s %.1f selesai. Pace rata-rata %s",
                        miles ? "Mil" : "Kilometer",
                        UnitUtils.toDisplayDistance(this, kmDone),
                        UnitUtils.pace(this, avgPace));
                speakText(msg);
                hapticTap();
            }

            float lapKm = AppSettings.getAutoLapKm(this);
            if (lapKm > 0) {
                int lap = (int) Math.floor((distance / 1000.0) / lapKm);
                if (lap > lastLapIndex && lap > 0) {
                    lastLapIndex = lap;
                    speakText(String.format(Locale.getDefault(),
                            "Lap %d selesai. Total %s.",
                            lap, UnitUtils.distance(this, distance / 1000.0)));
                    hapticTap();
                }
            }
        }
    }

    private void updateUI() {
        int colorOrange = ContextCompat.getColor(this, R.color.run_orange);
        int colorSurface2 = ContextCompat.getColor(this, R.color.run_surface_2);
        int colorWhite = ContextCompat.getColor(this, R.color.white);
        int colorTeal = ContextCompat.getColor(this, R.color.run_teal);

        switch (currentState) {
            case STATE_RUNNING:
                btnStart.setImageResource(R.drawable.ic_pause);
                btnStart.setBackgroundTintList(ColorStateList.valueOf(colorSurface2));
                btnStart.setImageTintList(ColorStateList.valueOf(colorOrange));
                layoutStop.setVisibility(View.VISIBLE);
                tvButtonLabel.setText("JEDA");
                tvButtonLabel.setTextColor(colorOrange);
                tvStatus.setText("BERLARI");
                tvStatus.setTextColor(colorWhite);
                break;
            case STATE_PAUSED:
                btnStart.setImageResource(R.drawable.ic_play);
                btnStart.setBackgroundTintList(ColorStateList.valueOf(colorOrange));
                btnStart.setImageTintList(ColorStateList.valueOf(colorWhite));
                layoutStop.setVisibility(View.VISIBLE);
                tvButtonLabel.setText("LANJUT");
                tvButtonLabel.setTextColor(colorOrange);
                tvStatus.setText("DIJEDA");
                tvStatus.setTextColor(colorTeal);
                break;
            case STATE_IDLE:
            default:
                btnStart.setImageResource(R.drawable.ic_play);
                btnStart.setBackgroundTintList(ColorStateList.valueOf(colorOrange));
                btnStart.setImageTintList(ColorStateList.valueOf(colorWhite));
                layoutStop.setVisibility(View.GONE);
                tvButtonLabel.setText("MULAI");
                tvButtonLabel.setTextColor(colorOrange);
                tvStatus.setText("SIAP BERLARI");
                tvStatus.setTextColor(colorWhite);
                break;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Map                                                                 */
    /* ------------------------------------------------------------------ */

    private void toggleMapTypeMenu() {
        if (mapTypeScroll == null) return;
        boolean visible = mapTypeScroll.getVisibility() == View.VISIBLE;
        mapTypeScroll.setVisibility(visible ? View.GONE : View.VISIBLE);
        if (!visible && bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    private void recenterMap() {
        if (locationOverlay != null) {
            locationOverlay.enableMyLocation();
            locationOverlay.enableFollowLocation();
        }
        if (mapView == null) return;
        if (locationOverlay != null && locationOverlay.getMyLocation() != null) {
            mapView.getController().setZoom(17.0);
            mapView.getController().animateTo(locationOverlay.getMyLocation());
            return;
        }
        if (isBound && runningService != null) {
            List<RoutePoint> points = runningService.getRoutePoints();
            if (!points.isEmpty()) {
                RoutePoint last = points.get(points.size() - 1);
                OsmdroidMapHelper.centerOn(mapView, last.getLat(), last.getLng(), 17.0);
                return;
            }
        }
        fetchInitialLocationAndCenter();
    }

    private void updateMapFromService() {
        if (mapView == null || !isBound || runningService == null) return;
        long now = System.currentTimeMillis();
        if (now - lastMapUpdate < 1000) return;
        lastMapUpdate = now;

        List<RoutePoint> points = runningService.getRoutePoints();
        if (points == null || points.isEmpty()) return;

        routePolyline = OsmdroidMapHelper.drawRoute(mapView, points, routePolyline);

        // Always keep the start marker pinned
        OsmdroidMapHelper.updateStartMarker(mapView, points.get(0));

        if (currentState == STATE_RUNNING) {
            RoutePoint last = points.get(points.size() - 1);
            OsmdroidMapHelper.centerOn(mapView, last.getLat(), last.getLng(), 17.5);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Lifecycle                                                           */
    /* ------------------------------------------------------------------ */

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && RunningService.ACTION_STOP.equals(intent.getAction())) {
            confirmStop();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, RunningService.class), serviceConnection, BIND_AUTO_CREATE);
        IntentFilter filter = new IntentFilter(RunningService.ACTION_LOCATION_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mapView != null) mapView.onPause();
        if (locationOverlay != null) locationOverlay.disableMyLocation();
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
            runningService = null;
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationOverlay != null) locationOverlay.disableMyLocation();
        if (mapView != null) mapView.onDetach();
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {
            }
            tts = null;
        }
        if (toneGenerator != null) {
            try {
                toneGenerator.release();
            } catch (Exception ignored) {
            }
            toneGenerator = null;
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            });
        } else {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }
}

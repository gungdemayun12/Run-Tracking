package com.example.runtrack.ui.settings;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.example.runtrack.R;
import com.example.runtrack.BuildConfig;
import com.example.runtrack.data.AppSettings;
import com.example.runtrack.util.OsmdroidMapHelper;
import com.example.runtrack.util.ThemeTransitioner;
import com.example.runtrack.util.UnitUtils;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;
import java.util.function.IntConsumer;

public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchAutoPause;
    private SwitchMaterial switchVoiceCoach;
    private SwitchMaterial switchScreenOn;
    private SwitchMaterial switchDarkMode;
    private SwitchMaterial switchPrivacyZone;
    private SwitchMaterial switchAnimations;
    private SwitchMaterial switchNotifications;
    private SwitchMaterial switchSoundEffects;
    private SwitchMaterial switchVibration;

    private TextView tvCountdownSummary;
    private TextView tvUnitSummary;
    private TextView tvMapTypeSummary;
    private TextView tvWeeklyGoalSummary;
    private TextView tvVoiceIntervalSummary;
    private TextView tvGpsAccuracySummary;
    private TextView tvAutoLapSummary;
    private TextView tvPrivacyRadiusSummary;
    private TextView tvWeeklyRunsSummary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ((TextView) findViewById(R.id.tv_developer_version))
                .setText("v" + BuildConfig.VERSION_NAME);

        ImageButton btnBack = findViewById(R.id.btn_back_settings);
        btnBack.setOnClickListener(v -> finish());

        switchAutoPause = findViewById(R.id.switch_auto_pause);
        switchVoiceCoach = findViewById(R.id.switch_voice_coach);
        switchScreenOn = findViewById(R.id.switch_screen_on);
        switchDarkMode = findViewById(R.id.switch_dark_mode_setting);
        switchPrivacyZone = findViewById(R.id.switch_privacy_zone);
        switchAnimations = findViewById(R.id.switch_animations);
        switchNotifications = findViewById(R.id.switch_notifications);
        switchSoundEffects = findViewById(R.id.switch_sound_effects);
        switchVibration = findViewById(R.id.switch_vibration);

        tvCountdownSummary = findViewById(R.id.tv_countdown_summary);
        tvUnitSummary = findViewById(R.id.tv_unit_summary);
        tvMapTypeSummary = findViewById(R.id.tv_map_type_summary);
        tvWeeklyGoalSummary = findViewById(R.id.tv_weekly_goal_summary);
        tvVoiceIntervalSummary = findViewById(R.id.tv_voice_interval_summary);
        tvGpsAccuracySummary = findViewById(R.id.tv_gps_accuracy_summary);
        tvAutoLapSummary = findViewById(R.id.tv_auto_lap_summary);
        tvPrivacyRadiusSummary = findViewById(R.id.tv_privacy_radius_summary);
        tvWeeklyRunsSummary = findViewById(R.id.tv_weekly_runs_summary);

        initValues();
        setupListeners();
    }

    private void initValues() {
        switchAutoPause.setChecked(AppSettings.isAutoPauseEnabled(this));
        switchVoiceCoach.setChecked(AppSettings.isVoiceCoachEnabled(this));
        switchScreenOn.setChecked(AppSettings.isKeepScreenOn(this));
        switchDarkMode.setChecked(AppSettings.isDarkMode(this));
        switchPrivacyZone.setChecked(AppSettings.isPrivacyZoneEnabled(this));
        switchAnimations.setChecked(AppSettings.isAnimationsEnabled(this));
        switchNotifications.setChecked(AppSettings.isNotificationsEnabled(this));
        switchSoundEffects.setChecked(AppSettings.isSoundEffectsEnabled(this));
        switchVibration.setChecked(AppSettings.isVibrationEnabled(this));

        updateCountdownSummary();
        updateUnitSummary();
        updateMapTypeSummary();
        updateWeeklyGoalSummary();
        updateVoiceIntervalSummary();
        updateGpsAccuracySummary();
        updateAutoLapSummary();
        updatePrivacyRadiusSummary();
        updateWeeklyRunsSummary();
    }

    private void setupListeners() {
        switchAutoPause.setOnCheckedChangeListener((btn, isChecked) -> {
            AppSettings.setAutoPauseEnabled(this, isChecked);
            showToast(isChecked ? "Auto-Pause diaktifkan" : "Auto-Pause dinonaktifkan");
        });

        switchVoiceCoach.setOnCheckedChangeListener((btn, isChecked) -> {
            AppSettings.setVoiceCoachEnabled(this, isChecked);
            showToast(isChecked ? "Panduan suara aktif" : "Panduan suara nonaktif");
        });

        switchScreenOn.setOnCheckedChangeListener((btn, isChecked) -> {
            AppSettings.setKeepScreenOn(this, isChecked);
            showToast(isChecked ? "Layar akan tetap menyala saat lari"
                    : "Layar mengikuti setelan sistem");
        });

        switchDarkMode.setOnCheckedChangeListener((btn, isChecked) -> {
            AppSettings.setDarkMode(this, isChecked);
            ThemeTransitioner.switchTheme(this, isChecked);
        });

        switchPrivacyZone.setOnCheckedChangeListener((btn, isChecked) -> {
            AppSettings.setPrivacyZoneEnabled(this, isChecked);
            showToast(isChecked
                    ? "Zona privasi aktif " + AppSettings.getPrivacyRadiusMeters(this) + " m"
                    : "Zona privasi dinonaktifkan");
        });

        switchAnimations.setOnCheckedChangeListener((btn, isChecked) -> {
            AppSettings.setAnimationsEnabled(this, isChecked);
            showToast(isChecked ? "Animasi & efek diaktifkan" : "Animasi & efek dimatikan");
        });

        switchNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
            AppSettings.setNotificationsEnabled(this, isChecked);
            if (isChecked) requestNotificationPermissionIfNeeded();
            showToast(isChecked ? "Notifikasi aktivitas aktif" : "Notifikasi aktivitas nonaktif");
        });

        switchSoundEffects.setOnCheckedChangeListener((btn, isChecked) -> {
            AppSettings.setSoundEffectsEnabled(this, isChecked);
            showToast(isChecked ? "Efek suara aktif" : "Efek suara nonaktif");
        });

        switchVibration.setOnCheckedChangeListener((btn, isChecked) -> {
            AppSettings.setVibrationEnabled(this, isChecked);
            showToast(isChecked ? "Getaran aktif" : "Getaran nonaktif");
        });

        findViewById(R.id.layout_setting_countdown).setOnClickListener(v -> showCountdownDialog());
        findViewById(R.id.layout_setting_unit).setOnClickListener(v -> showUnitDialog());
        findViewById(R.id.layout_setting_map_type).setOnClickListener(v -> showMapTypeDialog());
        findViewById(R.id.layout_setting_weekly_goal).setOnClickListener(v -> showWeeklyGoalDialog());
        findViewById(R.id.layout_setting_voice_interval).setOnClickListener(v -> showVoiceIntervalDialog());
        findViewById(R.id.layout_setting_gps_accuracy).setOnClickListener(v -> showGpsAccuracyDialog());
        findViewById(R.id.layout_setting_auto_lap).setOnClickListener(v -> showAutoLapDialog());
        findViewById(R.id.layout_setting_privacy_radius).setOnClickListener(v -> showPrivacyRadiusDialog());
        findViewById(R.id.layout_setting_weekly_runs).setOnClickListener(v -> showWeeklyRunsDialog());

        findViewById(R.id.layout_setting_clear_cache).setOnClickListener(v -> confirmClearCache());
        findViewById(R.id.layout_setting_reset).setOnClickListener(v -> confirmResetSettings());

        findViewById(R.id.btn_about_gungdev).setOnClickListener(v -> showAboutGungdevDialog());
        findViewById(R.id.card_developer_info).setOnClickListener(v -> showAboutGungdevDialog());
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 91);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Dialogs                                                            */
    /* ------------------------------------------------------------------ */

    private void showChoice(String title, String[] options, int selected, IntConsumer onPick) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    dialog.dismiss();
                    onPick.accept(which);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showCountdownDialog() {
        final String[] options = {"Tidak ada (0 detik)", "3 detik (rekomendasi)", "5 detik", "10 detik"};
        final int[] values = {0, 3, 5, 10};
        int current = AppSettings.getCountdownDuration(this);
        int selected = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                selected = i;
                break;
            }
        }
        showChoice("Hitung Mundur Mulai", options, selected, which -> {
            AppSettings.setCountdownDuration(this, values[which]);
            updateCountdownSummary();
            showToast("Hitung mundur: " + options[which]);
        });
    }

    private void updateCountdownSummary() {
        int sec = AppSettings.getCountdownDuration(this);
        tvCountdownSummary.setText(sec == 0
                ? "Mulai instan tanpa hitung mundur"
                : sec + " detik sebelum tracking aktif");
    }

    private void showUnitDialog() {
        final String[] options = {"Kilometer (km) • kecepatan km/h", "Mil (mi) • kecepatan mph"};
        final String[] values = {"km", "mi"};
        int selected = "mi".equalsIgnoreCase(AppSettings.getDistanceUnit(this)) ? 1 : 0;
        showChoice("Satuan Jarak", options, selected, which -> {
            AppSettings.setDistanceUnit(this, values[which]);
            updateUnitSummary();
            updateWeeklyGoalSummary();
            updateVoiceIntervalSummary();
            updateAutoLapSummary();
            showToast("Satuan: " + values[which].toUpperCase(Locale.getDefault()));
        });
    }

    private void updateUnitSummary() {
        tvUnitSummary.setText("mi".equalsIgnoreCase(AppSettings.getDistanceUnit(this))
                ? "Mil (mi) • kecepatan mph"
                : "Kilometer (km) • kecepatan km/h");
    }

    private void showMapTypeDialog() {
        final String[] options = {
                "Esri World Street (rekomendasi)",
                "Esri Satellite (citra satelit)",
                "Esri Topo (kontur & ketinggian)",
                "Peta Jalan Klasik (ringan)"
        };
        int selected = Math.min(Math.max(0, AppSettings.getDefaultMapTile(this)), options.length - 1);
        showChoice("Layer Peta Utama", options, selected, which -> {
            AppSettings.setDefaultMapTile(this, which);
            updateMapTypeSummary();
            showToast("Layer peta: " + options[which]);
        });
    }

    private void updateMapTypeSummary() {
        switch (AppSettings.getDefaultMapTile(this)) {
            case 1:
                tvMapTypeSummary.setText("Esri Satellite • citra satelit");
                break;
            case 2:
                tvMapTypeSummary.setText("Esri Topo • topografi & kontur");
                break;
            case 3:
                tvMapTypeSummary.setText("Peta jalan klasik");
                break;
            case 0:
            default:
                tvMapTypeSummary.setText("Esri World Street • jalan & landmark");
                break;
        }
    }

    private void showVoiceIntervalDialog() {
        final String[] options = {"Setiap 0,5 km", "Setiap 1 km (rekomendasi)", "Setiap 2 km", "Setiap 5 km"};
        final float[] values = {0.5f, 1f, 2f, 5f};
        float current = AppSettings.getVoiceIntervalKm(this);
        int selected = 1;
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 0.01f) {
                selected = i;
                break;
            }
        }
        showChoice("Interval Pengumuman Suara", options, selected, which -> {
            AppSettings.setVoiceIntervalKm(this, values[which]);
            updateVoiceIntervalSummary();
            showToast("Interval suara: " + options[which]);
        });
    }

    private void updateVoiceIntervalSummary() {
        float km = AppSettings.getVoiceIntervalKm(this);
        tvVoiceIntervalSummary.setText(String.format(Locale.getDefault(), "Setiap %s",
                UnitUtils.distance(this, km, 1)));
    }

    private void showGpsAccuracyDialog() {
        final String[] options = {
                "Akurasi tinggi • paling presisi (rekomendasi)",
                "Seimbang • hemat baterai",
                "Hemat baterai • interval lebih longgar"
        };
        showChoice("Mode Akurasi GPS", options, AppSettings.getGpsMode(this), which -> {
            AppSettings.setGpsMode(this, which);
            updateGpsAccuracySummary();
            showToast("Mode GPS: " + options[which]);
        });
    }

    private void updateGpsAccuracySummary() {
        switch (AppSettings.getGpsMode(this)) {
            case 1:
                tvGpsAccuracySummary.setText("Seimbang • hemat baterai");
                break;
            case 2:
                tvGpsAccuracySummary.setText("Hemat baterai • interval longgar");
                break;
            case 0:
            default:
                tvGpsAccuracySummary.setText("Akurasi tinggi • paling presisi");
                break;
        }
    }

    private void showAutoLapDialog() {
        final String[] options = {"Nonaktif", "Setiap 1 km", "Setiap 2 km", "Setiap 5 km"};
        final float[] values = {0f, 1f, 2f, 5f};
        float current = AppSettings.getAutoLapKm(this);
        int selected = 0;
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 0.01f) {
                selected = i;
                break;
            }
        }
        showChoice("Auto Lap", options, selected, which -> {
            AppSettings.setAutoLapKm(this, values[which]);
            updateAutoLapSummary();
            showToast("Auto lap: " + options[which]);
        });
    }

    private void updateAutoLapSummary() {
        float km = AppSettings.getAutoLapKm(this);
        tvAutoLapSummary.setText(km <= 0
                ? "Nonaktif"
                : "Bunyi tiap " + UnitUtils.distance(this, km, 1));
    }

    private void showPrivacyRadiusDialog() {
        final String[] options = {"100 meter", "200 meter (rekomendasi)", "300 meter", "500 meter"};
        final int[] values = {100, 200, 300, 500};
        int current = AppSettings.getPrivacyRadiusMeters(this);
        int selected = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                selected = i;
                break;
            }
        }
        showChoice("Radius Zona Privasi", options, selected, which -> {
            AppSettings.setPrivacyRadiusMeters(this, values[which]);
            updatePrivacyRadiusSummary();
            showToast("Radius privasi: " + options[which]);
        });
    }

    private void updatePrivacyRadiusSummary() {
        tvPrivacyRadiusSummary.setText(
                AppSettings.getPrivacyRadiusMeters(this) + " meter");
    }

    private void showWeeklyGoalDialog() {
        final String[] options = {"10 km / minggu", "15 km / minggu", "20 km / minggu (standar)",
                "30 km / minggu (marathon)", "50 km / minggu (elite)"};
        final double[] values = {10.0, 15.0, 20.0, 30.0, 50.0};
        double current = AppSettings.getWeeklyGoalKm(this);
        int selected = 2;
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 1.0) {
                selected = i;
                break;
            }
        }
        showChoice("Target Jarak Mingguan", options, selected, which -> {
            AppSettings.setWeeklyGoalKm(this, values[which]);
            updateWeeklyGoalSummary();
            showToast("Target mingguan: " + options[which]);
        });
    }

    private void updateWeeklyGoalSummary() {
        tvWeeklyGoalSummary.setText(UnitUtils.goal(this,
                AppSettings.getWeeklyGoalKm(this)) + " / minggu");
    }

    private void showWeeklyRunsDialog() {
        final String[] options = {"2 kali lari", "3 kali lari (rekomendasi)", "4 kali lari", "5 kali lari"};
        final int[] values = {2, 3, 4, 5};
        int current = AppSettings.getWeeklyRunsGoal(this);
        int selected = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                selected = i;
                break;
            }
        }
        showChoice("Target Lari per Minggu", options, selected, which -> {
            AppSettings.setWeeklyRunsGoal(this, values[which]);
            updateWeeklyRunsSummary();
            showToast("Target lari: " + options[which]);
        });
    }

    private void updateWeeklyRunsSummary() {
        tvWeeklyRunsSummary.setText(
                AppSettings.getWeeklyRunsGoal(this) + " kali lari / minggu");
    }

    /* ------------------------------------------------------------------ */
    /* Data actions                                                       */
    /* ------------------------------------------------------------------ */

    private void confirmClearCache() {
        new AlertDialog.Builder(this)
                .setTitle("Bersihkan Cache Peta?")
                .setMessage("Tile peta yang tersimpan akan dihapus. Peta akan diunduh ulang saat "
                        + "dibutuhkan. Riwayat lari tetap aman.")
                .setPositiveButton("Bersihkan", (dialog, which) -> clearCache())
                .setNegativeButton("Batal", null)
                .show();
    }

    private void clearCache() {
        new Thread(() -> {
            try {
                OsmdroidMapHelper.clearTileCache(getApplicationContext());
            } catch (Exception ignored) {
            }
            runOnUiThread(() -> {
                if (!isFinishing()) showToast("Cache peta berhasil dibersihkan");
            });
        }).start();
    }

    private void confirmResetSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Semua Pengaturan?")
                .setMessage("Seluruh preferensi akan kembali ke nilai default. Riwayat lari tidak "
                        + "terhapus.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    AppSettings.resetAll(this);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    showToast("Pengaturan telah direset");
                    recreate();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showAboutGungdevDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Tentang RunTrack")
                .setMessage("Dikembangkan oleh gungdev\n"
                        + "Perancang produk dan pengembang aplikasi\n\n"
                        + "RunTrack dirancang untuk membantu Anda merekam rute, memahami progres, "
                        + "dan menjaga riwayat latihan tetap mudah diakses.\n\n"
                        + "Versi " + BuildConfig.VERSION_NAME + "  ·  RunTrack Athletic")
                .setPositiveButton("Tutup", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

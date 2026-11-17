package com.example.runtrack.ui.permissions;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.runtrack.R;

public class PermissionActivity extends AppCompatActivity {

    private ActivityResultLauncher<String[]> requestLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        requestLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    if (fine != null && fine) {
                        finish();
                    } else {
                        showSettingsDialog();
                    }
                });

        findViewById(R.id.btn_allow).setOnClickListener(v -> requestPermissionsNow());
        View skip = findViewById(R.id.btn_skip);
        if (skip != null) skip.setOnClickListener(v -> finish());
    }

    private void requestPermissionsNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            });
        } else {
            requestLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void showSettingsDialog() {
        boolean canAskAgain = shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION);
        new AlertDialog.Builder(this)
                .setTitle("Izin lokasi diperlukan")
                .setMessage(canAskAgain
                        ? "RunTrack membutuhkan lokasi untuk menggambar rute lari kamu."
                        : "Izin lokasi ditolak permanen. Aktifkan lewat pengaturan aplikasi.")
                .setPositiveButton(canAskAgain ? "Coba lagi" : "Buka Pengaturan", (dialog, which) -> {
                    if (canAskAgain) {
                        requestPermissionsNow();
                    } else {
                        Intent intent = new Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Nanti", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            finish();
        }
    }
}

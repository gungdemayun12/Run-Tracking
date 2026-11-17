package com.example.runtrack.util;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.runtrack.R;
import com.example.runtrack.data.AppSettings;

import java.util.Locale;

/**
 * Small wrapper that posts the "activity saved" summary notification, honouring
 * the in-app notification switch and the runtime POST_NOTIFICATIONS permission.
 */
public final class NotificationHelper {

    private static final String CHANNEL_ID = "run_summary";
    private static final String CHANNEL_NAME = "Ringkasan Aktivitas";

    private NotificationHelper() {
    }

    public static void notifyRunSaved(Context context, String name,
                                      double distanceMeters, long durationSeconds) {
        if (context == null || !AppSettings.isNotificationsEnabled(context)) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Ringkasan setelah aktivitas lari tersimpan");
            manager.createNotificationChannel(channel);
        }

        String title = (name == null || name.trim().isEmpty())
                ? "Aktivitas tersimpan"
                : name + " tersimpan";

        String text = String.format(Locale.getDefault(), "%.2f km • durasi %s",
                Math.max(0, distanceMeters) / 1000.0,
                FormatUtils.formatClock(Math.max(0, durationSeconds)));

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_run)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build();

        try {
            NotificationManagerCompat.from(context)
                    .notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), notification);
        } catch (SecurityException ignored) {
        }
    }
}

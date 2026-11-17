package com.example.runtrack.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.example.runtrack.R;
import com.example.runtrack.data.AppSettings;
import com.example.runtrack.data.FirebaseRunRepository;
import com.example.runtrack.model.RoutePoint;
import com.example.runtrack.model.RunActivity;
import com.example.runtrack.ui.run.RunningActivity;
import com.example.runtrack.util.CalcUtils;
import com.example.runtrack.util.FormatUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Foreground service that records a run.
 *
 * <p>Design notes:</p>
 * <ul>
 *     <li>GPS points are filtered for accuracy, teleport spikes and jitter.</li>
 *     <li>A one-second ticker keeps the notification and UI in sync even when
 *     no new GPS fix arrives (for example while standing still).</li>
 *     <li>Active time excludes paused time.</li>
 * </ul>
 */
public class RunningService extends Service {

    private static final String TAG = "RunningService";
    public static final String CHANNEL_ID = "runtrack_tracking_channel";
    private static final int NOTIFICATION_ID = 101;

    public static final String ACTION_LOCATION_UPDATE = "com.example.runtrack.ACTION_STATE_UPDATE";
    public static final String ACTION_START = "com.example.runtrack.ACTION_START";
    public static final String ACTION_PAUSE = "com.example.runtrack.ACTION_PAUSE";
    public static final String ACTION_RESUME = "com.example.runtrack.ACTION_RESUME";
    public static final String ACTION_STOP = "com.example.runtrack.ACTION_STOP";
    public static final String EXTRA_WEIGHT_KG = "extra_weight_kg";

    public static final String EXTRA_DISTANCE = "distance";
    public static final String EXTRA_ELAPSED_SECONDS = "elapsedSeconds";
    public static final String EXTRA_ELAPSED_MILLIS = "elapsedMillis";
    public static final String EXTRA_CALORIES = "calories";
    public static final String EXTRA_ELEVATION = "elevationGain";
    public static final String EXTRA_MAX_SPEED = "maxSpeed";
    public static final String EXTRA_CURRENT_SPEED = "currentSpeed";
    public static final String EXTRA_AVG_PACE = "avgPace";
    public static final String EXTRA_PAUSED = "isPaused";
    public static final String EXTRA_TRACKING = "isTracking";
    public static final String EXTRA_GPS_ACCURACY = "gpsAccuracy";
    public static final String EXTRA_REQUEST_SAVE = "requestSave";
    public static final String EXTRA_INITIAL_LAT = "extra_initial_lat";
    public static final String EXTRA_INITIAL_LNG = "extra_initial_lng";
    public static final String EXTRA_INITIAL_ALT = "extra_initial_alt";

    private static final float MAX_ACCURACY_METERS = 50f;
    private static final float INITIAL_MAX_ACCURACY_METERS = 65f;
    private static final double MAX_RUNNING_SPEED_MPS = 12.5; // ~45 km/h
    private static final double MIN_SEGMENT_METERS = 1.2;
    private static final double MAX_GPS_GAP_SECONDS = 15.0;

    private final IBinder binder = new LocalBinder();
    private final FirebaseRunRepository firebaseRepo = new FirebaseRunRepository();
    private final Handler tickerHandler = new Handler(Looper.getMainLooper());

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private String currentSessionId;
    private String currentUserId;
    private double userWeightKg = 70.0;

    private final ArrayList<RoutePoint> routePoints = new ArrayList<>();

    private double totalDistanceMeters;
    private double calories;
    private double elevationGain;
    private double maxSpeed;
    private double smoothedSpeed;
    private double currentSpeedMps;

    private long startTimeElapsed;
    private long sessionStartTime;
    private long pausedAtElapsed;
    private long totalPausedDuration;

    private boolean isPaused;
    private boolean isTracking;

    private float lastAccuracy;
    private long lastPointWallClock;
    private long lastNotificationUpdate;
    private Location lastAcceptedLocation;
    private double lastAltitude = Double.NaN;
    private long lastFirebaseUpdateTs;
    private double lastFirebaseUpdateDistance;

    private Runnable ticker;

    public class LocalBinder extends Binder {
        public RunningService getService() {
            return RunningService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        setupLocationCallback();
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (!isTracking || isPaused) return;
                for (Location location : locationResult.getLocations()) {
                    processLocation(location);
                }
            }
        };
    }

    /* ------------------------------------------------------------------ */
    /* GPS processing                                                      */
    /* ------------------------------------------------------------------ */

    private void processLocation(Location location) {
        if (location == null) return;

        if (!location.hasAccuracy()) {
            return;
        }
        lastAccuracy = location.getAccuracy();
        float maxAllowed = routePoints.isEmpty() ? INITIAL_MAX_ACCURACY_METERS : MAX_ACCURACY_METERS;
        if (lastAccuracy > maxAllowed) {
            Log.d(TAG, "Rejected fix: accuracy " + lastAccuracy + "m (limit " + maxAllowed + "m)");
            broadcastState();
            return;
        }

        long wallClock = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();

        if (lastAcceptedLocation != null) {
            double segment = CalcUtils.distanceMeters(
                    lastAcceptedLocation.getLatitude(), lastAcceptedLocation.getLongitude(),
                    location.getLatitude(), location.getLongitude());
            // Use the monotonic clock for elapsed time. Location timestamps may be
            // stale or unavailable on emulators and can otherwise create false
            // teleport speeds or wildly incorrect calories/pace.
            double dtSeconds = Math.max(
                    (nowElapsed - lastPointWallClock) / 1000.0,
                    0.2);
            // A long gap is not enough evidence that the runner travelled in a
            // straight line. Do not charge the gap to distance or calories;
            // accept the new point as the next anchor instead.
            if (dtSeconds > MAX_GPS_GAP_SECONDS) {
                lastAcceptedLocation = location;
                lastPointWallClock = nowElapsed;
                currentSpeedMps = 0;
                smoothedSpeed = 0;
                if (location.hasAltitude()) lastAltitude = location.getAltitude();
                broadcastState();
                return;
            }

            double impliedSpeed = segment / dtSeconds;

            // Ignore GPS teleports caused by multipath in cities or driving.
            if (impliedSpeed > MAX_RUNNING_SPEED_MPS) {
                Log.d(TAG, "Rejected fix: implied " + impliedSpeed + " m/s");
                broadcastState();
                return;
            }

            // Stationary noise filter: if implied speed is < 0.35 m/s (~1.2 km/h) and movement is small (< 3m),
            // the user is standing still or GPS is drifting. Do not accumulate fake distance.
            if (impliedSpeed < 0.35 && segment < 3.0) {
                currentSpeedMps = 0;
                smoothedSpeed = CalcUtils.smoothSpeed(0, smoothedSpeed, 0.35f);
                broadcastState();
                return;
            }

            if (segment < MIN_SEGMENT_METERS) {
                // Keep the previous anchor so tiny GPS noise accumulates instead of
                // being thrown away on every tick.
                broadcastState();
                return;
            }

            double rawSpeed = location.hasSpeed()
                    ? location.getSpeed() : impliedSpeed;
            if (rawSpeed < 0 || rawSpeed > MAX_RUNNING_SPEED_MPS) {
                rawSpeed = impliedSpeed;
            }
            rawSpeed = Math.min(rawSpeed, MAX_RUNNING_SPEED_MPS);

            smoothedSpeed = CalcUtils.smoothSpeed(rawSpeed, smoothedSpeed, 0.35f);
            currentSpeedMps = smoothedSpeed;

            totalDistanceMeters += segment;
            calories += CalcUtils.calculateCalories(
                    userWeightKg, Math.max(smoothedSpeed, 0.6),
                    Math.max(500L, (long) (dtSeconds * 1000)));

            if (location.hasAltitude() && !Double.isNaN(lastAltitude)) {
                double diff = location.getAltitude() - lastAltitude;
                if (diff > 1.0) elevationGain += diff;
            }
            if (location.hasAltitude()) lastAltitude = location.getAltitude();

            if (smoothedSpeed > maxSpeed) maxSpeed = smoothedSpeed;
        } else {
            currentSpeedMps = 0;
            if (location.hasAltitude()) lastAltitude = location.getAltitude();
        }

        RoutePoint point = new RoutePoint(
                location.getLatitude(),
                location.getLongitude(),
                location.hasAltitude() ? location.getAltitude() : 0,
                wallClock,
                (float) smoothedSpeed);
        routePoints.add(point);

        lastAcceptedLocation = location;
        lastPointWallClock = nowElapsed;

        broadcastState();
        checkFirebaseSync(point);
    }

    private void checkFirebaseSync(RoutePoint point) {
        if (currentUserId == null || currentSessionId == null) return;
        long now = System.currentTimeMillis();
        double distanceDiff = totalDistanceMeters - lastFirebaseUpdateDistance;
        if (now - lastFirebaseUpdateTs < 5000 && distanceDiff < 50) return;

        try {
            firebaseRepo.updatePoint(currentUserId, currentSessionId, point);
            RunActivity snapshot = new RunActivity();
            snapshot.distanceMeters = totalDistanceMeters;
            snapshot.calories = calories;
            snapshot.durationMillis = getActiveElapsedMillis();
            snapshot.avgPace = getAvgPace();
            snapshot.maxSpeed = maxSpeed;
            snapshot.elevationGain = elevationGain;
            firebaseRepo.updateStats(currentUserId, currentSessionId, snapshot);
        } catch (Exception e) {
            Log.w(TAG, "Realtime DB sync skipped: " + e.getMessage());
        }
        lastFirebaseUpdateTs = now;
        lastFirebaseUpdateDistance = totalDistanceMeters;
    }

    /* ------------------------------------------------------------------ */
    /* Lifecycle / state                                                   */
    /* ------------------------------------------------------------------ */

    private void startTicker() {
        if (ticker != null) return;
        ticker = new Runnable() {
            @Override
            public void run() {
                if (!isTracking) return;
                broadcastState();
                long now = SystemClock.elapsedRealtime();
                if (now - lastNotificationUpdate >= 1000) {
                    updateNotification();
                    lastNotificationUpdate = now;
                }
                tickerHandler.postDelayed(this, 1000);
            }
        };
        tickerHandler.post(ticker);
    }

    private void stopTicker() {
        tickerHandler.removeCallbacksAndMessages(null);
        ticker = null;
    }

    public void startTracking() {
        startTracking(Double.NaN, Double.NaN, 0.0);
    }

    public void startTracking(double initLat, double initLng, double initAlt) {
        if (isTracking) {
            broadcastState();
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission missing");
            updateNotificationText("Izin lokasi diperlukan");
            stopForegroundCompat();
            stopSelf();
            return;
        }

        currentUserId = FirebaseAuth.getInstance().getUid();
        startTimeElapsed = SystemClock.elapsedRealtime();
        sessionStartTime = System.currentTimeMillis();
        totalDistanceMeters = 0;
        calories = 0;
        elevationGain = 0;
        maxSpeed = 0;
        smoothedSpeed = 0;
        currentSpeedMps = 0;
        totalPausedDuration = 0;
        isPaused = false;
        isTracking = true;
        lastAccuracy = 0;
        lastPointWallClock = 0;
        lastAltitude = Double.NaN;
        lastAcceptedLocation = null;
        routePoints.clear();

        // If an initial location was passed from RunningActivity, use it only as the
        // starting anchor. Keeping it as a route point would leave a bogus first point
        // on the map when the cached fix came from another city/country.
        if (!Double.isNaN(initLat) && !Double.isNaN(initLng)) {
            Location initialLocation = new Location("InitialFix");
            initialLocation.setLatitude(initLat);
            initialLocation.setLongitude(initLng);
            initialLocation.setAltitude(initAlt);
            initialLocation.setAccuracy(10.0f);
            initialLocation.setTime(sessionStartTime);
            lastAcceptedLocation = initialLocation;
            lastPointWallClock = SystemClock.elapsedRealtime();
            lastAccuracy = 10f;
            currentSpeedMps = 0;
            smoothedSpeed = 0;
            if (!Double.isNaN(initAlt)) lastAltitude = initAlt;
        }

        lastFirebaseUpdateTs = sessionStartTime;
        lastFirebaseUpdateDistance = 0;

        if (currentUserId != null) {
            try {
                currentSessionId = firebaseRepo.startSession(currentUserId, sessionStartTime);
            } catch (Exception e) {
                Log.w(TAG, "Realtime DB session skipped: " + e.getMessage());
                currentSessionId = null;
            }
        }

        startForegroundNotification();
        startTicker();
        requestLocationUpdates();
        broadcastState();
        updateNotification();
    }

    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // Respect the user's battery/accuracy preference from Settings.
        int gpsMode = AppSettings.getGpsMode(this);
        int priority = gpsMode == 2
                ? Priority.PRIORITY_LOW_POWER
                : gpsMode == 1
                ? Priority.PRIORITY_BALANCED_POWER_ACCURACY
                : Priority.PRIORITY_HIGH_ACCURACY;
        long intervalMs = gpsMode == 2 ? 4000L : gpsMode == 1 ? 2000L : 1000L;
        long minIntervalMs = gpsMode == 2 ? 2000L : gpsMode == 1 ? 1000L : 500L;
        long maxDelayMs = gpsMode == 2 ? 4000L : gpsMode == 1 ? 2000L : 1000L;

        LocationRequest request = new LocationRequest.Builder(priority, intervalMs)
                .setMinUpdateIntervalMillis(minIntervalMs)
                .setMaxUpdateDelayMillis(maxDelayMs)
                .setWaitForAccurateLocation(gpsMode == 0)
                .build();
        try {
            fusedLocationClient.requestLocationUpdates(
                    request, locationCallback, Looper.getMainLooper());

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location == null || isPaused || !isTracking) return;
                // A cached fix from another place/time is worse than no fix at all.
                long age = System.currentTimeMillis() - location.getTime();
                if (location.getTime() > 0 && age > 120_000L) return;
                if (routePoints.isEmpty()) {
                    processLocation(location);
                }
            });

            fusedLocationClient.getCurrentLocation(priority, null)
                    .addOnSuccessListener(location -> {
                        if (location == null || isPaused || !isTracking) return;
                        if (routePoints.isEmpty()) {
                            processLocation(location);
                        }
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Cannot request location updates", e);
        }
    }

    private void removeLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    public void pauseTracking() {
        if (!isTracking || isPaused) return;
        isPaused = true;
        pausedAtElapsed = SystemClock.elapsedRealtime();
        currentSpeedMps = 0;
        smoothedSpeed = 0;
        removeLocationUpdates();
        if (currentUserId != null && currentSessionId != null) {
            try {
                firebaseRepo.updateStatus(currentUserId, currentSessionId, "paused");
            } catch (Exception ignored) {
            }
        }
        broadcastState();
        updateNotification();
    }

    public void resumeTracking() {
        if (!isTracking || !isPaused) return;
        totalPausedDuration += SystemClock.elapsedRealtime() - pausedAtElapsed;
        isPaused = false;
        currentSpeedMps = 0;
        smoothedSpeed = 0;
        lastAcceptedLocation = null;
        lastAltitude = Double.NaN;
        if (currentUserId != null && currentSessionId != null) {
            try {
                firebaseRepo.updateStatus(currentUserId, currentSessionId, "active");
            } catch (Exception ignored) {
            }
        }
        requestLocationUpdates();
        broadcastState();
        updateNotification();
    }

    public void stopTracking() {
        if (!isTracking) {
            stopForegroundCompat();
            stopSelf();
            return;
        }
        if (currentUserId != null && currentSessionId != null) {
            try {
                firebaseRepo.finishSession(currentUserId, currentSessionId, System.currentTimeMillis());
            } catch (Exception ignored) {
            }
        }
        isTracking = false;
        isPaused = false;
        removeLocationUpdates();
        stopTicker();
        broadcastState();
        stopForegroundCompat();
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            if (intent.hasExtra(EXTRA_WEIGHT_KG)) {
                userWeightKg = intent.getDoubleExtra(EXTRA_WEIGHT_KG, 70.0);
            }
            double initLat = intent.getDoubleExtra(EXTRA_INITIAL_LAT, Double.NaN);
            double initLng = intent.getDoubleExtra(EXTRA_INITIAL_LNG, Double.NaN);
            double initAlt = intent.getDoubleExtra(EXTRA_INITIAL_ALT, 0.0);
            if (!isTracking) {
                // Post the foreground notification immediately; Android requires
                // startForeground() shortly after startForegroundService().
                startForegroundNotification();
            }
            startTracking(initLat, initLng, initAlt);
        } else if (ACTION_PAUSE.equals(action)) {
            pauseTracking();
        } else if (ACTION_RESUME.equals(action)) {
            resumeTracking();
        } else if (ACTION_STOP.equals(action)) {
            stopTracking();
        } else {
            broadcastState();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeLocationUpdates();
        stopTicker();
        isTracking = false;
        isPaused = false;
    }

    /* ------------------------------------------------------------------ */
    /* Broadcasts                                                          */
    /* ------------------------------------------------------------------ */

    private void broadcastState() {
        Intent intent = new Intent(ACTION_LOCATION_UPDATE);
        intent.setPackage(getPackageName());
        long elapsedMillis = getActiveElapsedMillis();
        intent.putExtra(EXTRA_DISTANCE, totalDistanceMeters);
        intent.putExtra(EXTRA_ELAPSED_SECONDS, elapsedMillis / 1000);
        intent.putExtra(EXTRA_ELAPSED_MILLIS, elapsedMillis);
        intent.putExtra(EXTRA_CALORIES, calories);
        intent.putExtra(EXTRA_ELEVATION, elevationGain);
        intent.putExtra(EXTRA_MAX_SPEED, maxSpeed);
        intent.putExtra(EXTRA_CURRENT_SPEED, currentSpeedMps);
        intent.putExtra(EXTRA_AVG_PACE, getAvgPace());
        intent.putExtra(EXTRA_PAUSED, isPaused);
        intent.putExtra(EXTRA_TRACKING, isTracking);
        intent.putExtra(EXTRA_GPS_ACCURACY, lastAccuracy);
        sendBroadcast(intent);
    }

    /* ------------------------------------------------------------------ */
    /* Notification                                                        */
    /* ------------------------------------------------------------------ */

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Pelacakan Lari",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Menampilkan jarak, waktu dan pace saat lari");
            channel.setShowBadge(false);
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String overrideText) {
        Intent openIntent = new Intent(this, RunningActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent toggleIntent = new Intent(this, RunningService.class)
                .setAction(isPaused ? ACTION_RESUME : ACTION_PAUSE);
        PendingIntent togglePending = PendingIntent.getService(
                this, 1, toggleIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Let the activity collect the route and save it locally before the
        // service is stopped. Stopping the service directly from the
        // notification would otherwise discard the in-memory route.
        Intent stopIntent = new Intent(this, RunningActivity.class)
                .setAction(ACTION_STOP)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent stopPending = PendingIntent.getActivity(
                this, 2, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String text = overrideText;
        if (text == null) {
            text = String.format(Locale.getDefault(), "%.2f km • %s • %s",
                    totalDistanceMeters / 1000.0,
                    FormatUtils.formatClock(getActiveElapsedMillis() / 1000),
                    FormatUtils.formatPace(getAvgPace()));
        }

        String title = !isTracking ? "Lari selesai"
                : (isPaused ? "Lari dijeda" : "Lari sedang berjalan");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_run)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(isTracking)
                .setAutoCancel(!isTracking)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if (isTracking) {
            builder.addAction(
                    isPaused ? R.drawable.ic_play : R.drawable.ic_pause,
                    isPaused ? "Lanjut" : "Jeda",
                    togglePending);
            builder.addAction(R.drawable.ic_stop, "Selesai", stopPending);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setColorized(false);
        }
        return builder.build();
    }

    private void updateNotificationText(String text) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private void updateNotification() {
        if (!isTracking) return;
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(null));
    }

    private void startForegroundNotification() {
        Notification notification = buildNotification(null);
        try {
            ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } catch (Exception e) {
            Log.w(TAG, "startForeground with type failed, retrying", e);
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void stopForegroundCompat() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
    }

    /* ------------------------------------------------------------------ */
    /* Public getters used by the live screen                              */
    /* ------------------------------------------------------------------ */

    private long getActiveElapsedMillisInternal() {
        if (startTimeElapsed == 0) return 0;
        long now = isPaused ? pausedAtElapsed : SystemClock.elapsedRealtime();
        return Math.max(0, now - startTimeElapsed - totalPausedDuration);
    }

    public long getActiveElapsedMillis() {
        return getActiveElapsedMillisInternal();
    }

    public List<RoutePoint> getRoutePoints() {
        return new ArrayList<>(routePoints);
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public double getCalories() {
        return calories;
    }

    public double getElevationGain() {
        return elevationGain;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public double getCurrentSpeedMps() {
        return currentSpeedMps;
    }

    public float getGpsAccuracy() {
        return lastAccuracy;
    }

    public long getSessionStartTime() {
        return sessionStartTime;
    }

    public boolean isCurrentlyTracking() {
        return isTracking;
    }

    public boolean isCurrentlyPaused() {
        return isPaused;
    }

    public double getAvgPace() {
        return CalcUtils.calculatePace(totalDistanceMeters, getActiveElapsedMillisInternal() / 1000);
    }
}

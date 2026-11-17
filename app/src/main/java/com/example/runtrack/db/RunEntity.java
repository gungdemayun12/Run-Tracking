package com.example.runtrack.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.example.runtrack.model.RoutePoint;
import com.example.runtrack.model.RunActivity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Local cache row for one running activity.
 *
 * <p>The route is stored as JSON so a single Room row holds the complete
 * activity, which keeps the app usable even when Firestore is unavailable.</p>
 */
@Entity(tableName = "runs")
public class RunEntity {

    @PrimaryKey
    @NonNull
    public String id = "";

    public String userId;
    public String activityName = "Lari";
    public long startTime;
    public long endTime;
    public double distanceMeters;
    public double calories;
    public double avgPace;
    public double maxSpeed;
    public double elevationGain;
    public double avgHeartRate;
    public long durationMillis;
    public String routeJson;
    public boolean synced;
    public long updatedAt;
    public long deletedAt; // 0 = active, >0 = timestamp when moved to trash

    public RunEntity() {
    }

    public static RunEntity fromActivity(RunActivity activity, Gson gson) {
        RunEntity entity = new RunEntity();
        entity.id = activity.id != null ? activity.id : "";
        entity.userId = activity.userId;
        entity.activityName = activity.activityName;
        entity.startTime = activity.startTime;
        entity.endTime = activity.endTime;
        entity.distanceMeters = activity.distanceMeters;
        entity.calories = activity.calories;
        entity.avgPace = activity.avgPace;
        entity.maxSpeed = activity.maxSpeed;
        entity.elevationGain = activity.elevationGain;
        entity.avgHeartRate = activity.avgHeartRate;
        entity.durationMillis = activity.durationMillis;
        entity.routeJson = gson.toJson(activity.routePoints != null
                ? activity.routePoints : new ArrayList<RoutePoint>());
        entity.updatedAt = System.currentTimeMillis();
        entity.deletedAt = activity.deletedAt;
        return entity;
    }

    public RunActivity toActivity(Gson gson) {
        RunActivity activity = new RunActivity();
        activity.id = id;
        activity.userId = userId;
        activity.activityName = activityName;
        activity.startTime = startTime;
        activity.endTime = endTime;
        activity.distanceMeters = distanceMeters;
        activity.calories = calories;
        activity.avgPace = avgPace;
        activity.maxSpeed = maxSpeed;
        activity.elevationGain = elevationGain;
        activity.avgHeartRate = avgHeartRate;
        activity.durationMillis = durationMillis;
        activity.routePoints = parseRoute(gson, routeJson);
        activity.deletedAt = deletedAt;
        return activity;
    }

    private static List<RoutePoint> parseRoute(Gson gson, String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<RoutePoint>>() { }.getType();
            List<RoutePoint> points = gson.fromJson(json, type);
            return points != null ? points : new ArrayList<>();
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }
}

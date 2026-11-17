package com.example.runtrack.model;

import java.io.Serializable;
import java.util.List;

public class RunActivity implements Serializable {
    public String id;
    public String userId;
    public String activityName = "Run";
    public long startTime;
    public long endTime;
    public double distanceMeters;
    public double calories;
    public double avgPace;
    public double maxSpeed;
    public double elevationGain;
    public double avgHeartRate;
    public String mapImageUrl; // For map preview
    public List<RoutePoint> routePoints;
    public long durationMillis; // Duration in milliseconds
    public long deletedAt; // 0 = active, >0 = timestamp when moved to trash

    public RunActivity() {}
    
    public String getDurationFormatted() {
        long ms = durationMillis > 0 ? durationMillis
                : (endTime > startTime ? endTime - startTime : 0);
        long seconds = ms / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d:%02d", hours, minutes, secs);
    }
    
    // Helper method to get distance in km
    public double getDistanceKm() {
        return distanceMeters / 1000.0;
    }
    
    // Helper method to get elevation gain in meters (can be converted to feet)
    public double getElevationGain() {
        return elevationGain;
    }
}

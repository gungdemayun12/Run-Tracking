package com.example.runtrack.data;

import com.example.runtrack.model.RoutePoint;
import com.example.runtrack.model.RunActivity;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles real-time updates to Firebase Realtime Database for tracking sessions.
 */
public class FirebaseRunRepository {
    private final DatabaseReference dbRef;

    public FirebaseRunRepository() {
        // Ensure persistence is handled if needed, but usually default is fine
        dbRef = FirebaseDatabase.getInstance().getReference("runs");
    }

    public String startSession(String userId, long startTime) {
        if (userId == null) return null;
        DatabaseReference userRef = dbRef.child(userId).push();
        String sessionId = userRef.getKey();
        
        Map<String, Object> data = new HashMap<>();
        data.put("startTime", startTime);
        data.put("status", "active");
        userRef.setValue(data);
        
        return sessionId;
    }

    public void updatePoint(String userId, String sessionId, RoutePoint point) {
        if (userId == null || sessionId == null) return;
        // Using timestamp as key to ensure ordering and uniqueness
        dbRef.child(userId).child(sessionId).child("points")
                .child(String.valueOf(point.timestamp)).setValue(point);
    }

    public void updateStats(String userId, String sessionId, RunActivity activity) {
        if (userId == null || sessionId == null) return;
        Map<String, Object> stats = new HashMap<>();
        stats.put("distance", activity.distanceMeters);
        stats.put("calories", activity.calories);
        stats.put("duration", activity.durationMillis / 1000); // duration in seconds
        stats.put("pace", activity.avgPace);
        stats.put("maxSpeed", activity.maxSpeed);
        stats.put("elevationGain", activity.elevationGain);
        
        dbRef.child(userId).child(sessionId).child("stats").setValue(stats);
    }

    public void updateStatus(String userId, String sessionId, String status) {
        if (userId == null || sessionId == null) return;
        dbRef.child(userId).child(sessionId).child("status").setValue(status);
    }

    public void finishSession(String userId, String sessionId, long endTime) {
        if (userId == null || sessionId == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("endTime", endTime);
        updates.put("status", "finished");
        dbRef.child(userId).child(sessionId).updateChildren(updates);
    }
}

package com.example.runtrack.model;

import java.io.Serializable;

/**
 * Represents a single GPS point along a running route.
 * Fields are public for Firestore deserialization compatibility.
 */
public class RoutePoint implements Serializable {
    public double lat;
    public double lng;
    public double altitude;
    public long timestamp;
    public double speed; // m/s — field Firestore: "speed"

    /** No-arg constructor required by Firestore */
    public RoutePoint() {}

    public RoutePoint(double lat, double lng, double altitude, long timestamp, float speed) {
        this.lat = lat;
        this.lng = lng;
        this.altitude = altitude;
        this.timestamp = timestamp;
        this.speed = speed;
    }

    // Getters kept for backward compat with existing code
    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public double getAltitude() { return altitude; }
    public long getTimestamp() { return timestamp; }
    public double getSpeed() { return speed; }
}

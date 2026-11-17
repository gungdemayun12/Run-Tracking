package com.example.runtrack.util;

import com.example.runtrack.model.RoutePoint;
import com.example.runtrack.model.RunActivity;
import org.osmdroid.views.MapView;
import java.util.List;

/** Preview peta OSM di kartu aktivitas. */
public final class ActivityCardMapBinder {
    private ActivityCardMapBinder() { }

    public static void bind(MapView mapView, RunActivity activity) {
        if (mapView == null || activity == null) return;
        OsmdroidMapHelper.setupPreviewMap(mapView);
        OsmdroidMapHelper.clearRouteOverlays(mapView);
        // Legacy/offline runs may not contain points; create a useful local preview.
        List<RoutePoint> points = OsmdroidMapHelper.ensureRoutePoints(activity, -6.200000, 106.816666);
        if (points != null && !points.isEmpty()) {
            OsmdroidMapHelper.drawRoute(mapView, points, null);
            OsmdroidMapHelper.fitRoute(mapView, points, 36);
        }
        mapView.onResume();
        mapView.invalidate();
    }

    public static void release(MapView mapView) {
        if (mapView == null) return;
        try { mapView.onPause(); } catch (Exception ignored) { }
    }
}

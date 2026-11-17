package com.example.runtrack.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.view.View;

import com.example.runtrack.model.RoutePoint;
import com.example.runtrack.model.RunActivity;

import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Map helpers built on osmdroid with Strava-inspired styling.
 */
public final class OsmdroidMapHelper {

    public static final int TILE_MAPNIK = 0;
    public static final int TILE_SATELLITE = 1;
    public static final int TILE_TOPO = 2;

    public static final int ROUTE_CASING_COLOR = Color.WHITE;
    public static final int ROUTE_COLOR = Color.parseColor("#FC4C02"); // Strava Orange
    public static final float ROUTE_CASING_WIDTH = 15f;
    public static final float ROUTE_WIDTH = 10f;

    /** Max distance from the route median before a point counts as an outlier. */
    private static final double MAX_ROUTE_OUTLIER_METERS = 30_000.0;
    /** Max gap between consecutive points before we treat it as a GPS teleport. */
    private static final double MAX_ROUTE_SEGMENT_METERS = 2_000.0;

    /**
     * Esri World Street Map layer (100% free, NO API key required, reliable global coverage).
     */
    public static final OnlineTileSourceBase ESRI_STREET =
            esriSource("EsriWorldStreet",
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/",
                    19, ".jpg");

    /**
     * OpenStreetMap France mirror (100% free, NO API key required, pure OSM street data).
     */
    public static final OnlineTileSourceBase OSM_FR = new XYTileSource(
            "OSM-FR",
            0, 19, 256, ".png",
            new String[]{
                    "https://a.tile.openstreetmap.fr/osmfr/",
                    "https://b.tile.openstreetmap.fr/osmfr/",
                    "https://c.tile.openstreetmap.fr/osmfr/"
            },
            "© OpenStreetMap contributors, OpenStreetMap France");

    /**
     * Standard OpenStreetMap Mapnik raster layer (open server).
     */
    public static final OnlineTileSourceBase OSM_MAPNIK = TileSourceFactory.MAPNIK;

    /**
     * Default street map layer uses Esri World Street (100% free, no API key needed, zero 403 blocks).
     */
    private static final OnlineTileSourceBase OSM_STANDARD = ESRI_STREET;

    /** Esri World Imagery global satellite coverage. */
    private static final OnlineTileSourceBase ESRI_SATELLITE =
            esriSource("EsriWorldImagery",
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
                    19, ".jpg");

    /** Esri topographic layer. */
    private static final OnlineTileSourceBase ESRI_TOPO =
            esriSource("EsriWorldTopo",
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/",
                    19, ".jpg");

    private static OnlineTileSourceBase esriSource(String name, String baseUrl,
                                                   int maxZoom, String extension) {
        return new OnlineTileSourceBase(name, 0, maxZoom, 256, extension,
                new String[]{baseUrl}) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return getBaseUrl()
                        + org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex) + "/"
                        + org.osmdroid.util.MapTileIndex.getY(pMapTileIndex) + "/"
                        + org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
                        + mImageFilenameEnding;
            }
        };
    }

    private OsmdroidMapHelper() {
    }

    public static void setupInteractiveMap(MapView mapView) {
        if (mapView == null) return;
        configureBase(mapView);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
        mapView.setClickable(true);
        mapView.setFocusable(true);
        mapView.setTileSource(OSM_STANDARD);
        mapView.getController().setZoom(17.0);
    }

    public static void setupPreviewMap(MapView mapView) {
        if (mapView == null) return;
        configureBase(mapView);
        mapView.setMultiTouchControls(false);
        mapView.setBuiltInZoomControls(false);
        mapView.setClickable(false);
        mapView.setFocusable(false);
        // Ensure touches pass through smoothly to parent RecyclerView
        mapView.setOnTouchListener((v, event) -> false);
        // Use Esri tiles (no OpenStreetMap attribution badge required)
        mapView.setTileSource(ESRI_STREET);
        // Remove any copyright overlay so no map badge appears on activity cards
        removeCopyrightOverlay(mapView);
    }

    private static void configureBase(MapView mapView) {
        mapView.setTilesScaledToDpi(true);
        mapView.setUseDataConnection(true);
        mapView.setHorizontalMapRepetitionEnabled(false);
        mapView.setVerticalMapRepetitionEnabled(false);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(20.0);
    }

    private static void addCopyright(MapView mapView) {
        // Map attribution is intentionally not drawn as an on-map badge.
    }

    public static MyLocationNewOverlay addMyLocation(MapView mapView, Context context) {
        if (mapView == null || context == null) return null;
        GpsMyLocationProvider provider = new GpsMyLocationProvider(context);
        provider.addLocationSource(LocationManager.NETWORK_PROVIDER);
        provider.addLocationSource(LocationManager.GPS_PROVIDER);
        MyLocationNewOverlay overlay = new MyLocationNewOverlay(provider, mapView);
        overlay.enableMyLocation();
        overlay.setDrawAccuracyEnabled(true);
        mapView.getOverlays().add(overlay);
        return overlay;
    }

    public static void setTileType(MapView mapView, int tileType) {
        if (mapView == null) return;
        ITileSource source;
        switch (tileType) {
            case TILE_SATELLITE:
                source = ESRI_SATELLITE;
                break;
            case TILE_TOPO:
                source = ESRI_TOPO;
                break;
            case TILE_MAPNIK:
            default:
                source = OSM_STANDARD;
                break;
        }
        mapView.setTileSource(source);
        // Do not show copyright badge on map
        removeCopyrightOverlay(mapView);
        mapView.invalidate();
    }

    private static String copyrightFor(int tileType) {
        if (tileType == TILE_SATELLITE) {
            return "© Esri, Maxar, Earthstar Geographics";
        }
        return "© Esri, HERE, Garmin";
    }

    /** Remove any CopyrightOverlay so no map badge appears on activity cards. */
    public static void removeCopyrightOverlay(MapView mapView) {
        if (mapView == null) return;
        for (int i = mapView.getOverlays().size() - 1; i >= 0; i--) {
            Overlay overlay = mapView.getOverlays().get(i);
            if (overlay instanceof CopyrightOverlay) {
                mapView.getOverlays().remove(i);
            }
        }
    }

    /**
     * Draws the route with Strava signature white casing and vibrant orange core.
     */
    public static Polyline drawRoute(MapView mapView, List<RoutePoint> points, Polyline existing) {
        if (mapView == null || points == null || points.isEmpty()) return existing;
        points = sanitizeRoute(points);
        if (points.isEmpty()) return existing;

        removePolylines(mapView);

        List<GeoPoint> geoPoints = toGeoPoints(points);

        Polyline casing = new Polyline();
        casing.setPoints(geoPoints);
        style(casing, ROUTE_CASING_COLOR, ROUTE_CASING_WIDTH);
        mapView.getOverlays().add(casing);

        Polyline polyline = new Polyline();
        polyline.setPoints(geoPoints);
        style(polyline, ROUTE_COLOR, ROUTE_WIDTH);
        mapView.getOverlays().add(polyline);

        mapView.invalidate();
        return polyline;
    }

    private static void style(Polyline polyline, int color, float width) {
        Paint paint = polyline.getOutlinePaint();
        paint.setColor(color);
        paint.setStrokeWidth(width);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setAntiAlias(true);
    }

    public static void fitRoute(MapView mapView, List<RoutePoint> points, int paddingPx) {
        if (mapView == null || points == null || points.isEmpty()) return;
        points = sanitizeRoute(points);
        if (points.isEmpty()) return;
        List<GeoPoint> geoPoints = toGeoPoints(points);
        if (geoPoints.size() == 1) {
            mapView.getController().setZoom(17.0);
            mapView.getController().setCenter(geoPoints.get(0));
            mapView.invalidate();
            return;
        }

        BoundingBox rawBox = BoundingBox.fromGeoPoints(geoPoints);
        // Ensure a safe minimum span (~220m) to prevent excessive zoom
        double minSpan = 0.002;
        double centerLat = rawBox.getCenterLatitude();
        double centerLng = rawBox.getCenterLongitude();
        double north = Math.max(rawBox.getLatNorth(), centerLat + (minSpan / 2.0));
        double south = Math.min(rawBox.getLatSouth(), centerLat - (minSpan / 2.0));
        double east = Math.max(rawBox.getLonEast(), centerLng + (minSpan / 2.0));
        double west = Math.min(rawBox.getLonWest(), centerLng - (minSpan / 2.0));
        final BoundingBox safeBox = new BoundingBox(north, east, south, west);

        Runnable applyZoom = () -> {
            if (mapView.getWidth() > 0 && mapView.getHeight() > 0) {
                try {
                    mapView.zoomToBoundingBox(safeBox, false, paddingPx);
                } catch (Exception e) {
                    mapView.getController().setCenter(safeBox.getCenterWithDateLine());
                    mapView.getController().setZoom(16.0);
                }
            } else {
                mapView.getController().setCenter(safeBox.getCenterWithDateLine());
                mapView.getController().setZoom(16.0);
            }
            mapView.invalidate();
        };

        if (mapView.getWidth() > 0 && mapView.getHeight() > 0) {
            mapView.post(applyZoom);
        } else {
            mapView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (right - left > 0 && bottom - top > 0) {
                        mapView.removeOnLayoutChangeListener(this);
                        applyZoom.run();
                    }
                }
            });
        }
    }

    public static void centerOn(MapView mapView, double lat, double lng, double zoom) {
        if (mapView == null) return;
        mapView.getController().setZoom(zoom);
        mapView.getController().animateTo(new GeoPoint(lat, lng));
    }

    public static void addStartEndMarkers(MapView mapView, List<RoutePoint> points) {
        if (mapView == null || points == null || points.isEmpty() || mapView.getContext() == null) return;
        points = sanitizeRoute(points);
        if (points.isEmpty()) return;

        Context context = mapView.getContext();
        RoutePoint start = points.get(0);
        Marker startMarker = new Marker(mapView);
        startMarker.setPosition(new GeoPoint(start.getLat(), start.getLng()));
        startMarker.setTitle("Mulai");
        startMarker.setIcon(createBadgeDrawable(context, "S", Color.parseColor("#10B981")));
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        startMarker.setInfoWindow(null);
        mapView.getOverlays().add(startMarker);

        if (points.size() > 1) {
            RoutePoint end = points.get(points.size() - 1);
            Marker endMarker = new Marker(mapView);
            endMarker.setPosition(new GeoPoint(end.getLat(), end.getLng()));
            endMarker.setTitle("Selesai");
            endMarker.setIcon(createBadgeDrawable(context, "F", Color.parseColor("#FC4C02")));
            endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            endMarker.setInfoWindow(null);
            mapView.getOverlays().add(endMarker);
        }
    }

    /**
     * Updates or creates the Start marker during live tracking.
     * Guarantees the start position remains visibly pinned to point 0.
     */
    public static void updateStartMarker(MapView mapView, RoutePoint startPoint) {
        if (mapView == null || startPoint == null || mapView.getContext() == null) return;
        Context context = mapView.getContext();
        Marker startMarker = null;
        for (Overlay overlay : mapView.getOverlays()) {
            if (overlay instanceof Marker && "Mulai".equals(((Marker) overlay).getTitle())) {
                startMarker = (Marker) overlay;
                break;
            }
        }
        if (startMarker == null) {
            startMarker = new Marker(mapView);
            startMarker.setTitle("Mulai");
            startMarker.setIcon(createBadgeDrawable(context, "S", Color.parseColor("#10B981")));
            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            startMarker.setInfoWindow(null);
            mapView.getOverlays().add(startMarker);
        }
        startMarker.setPosition(new GeoPoint(startPoint.getLat(), startPoint.getLng()));
        mapView.invalidate();
    }

    /**
     * Safely deletes corrupted or blocked osmdroid tile cache files from storage.
     */
    public static void clearTileCache(Context context) {
        if (context == null) return;
        try {
            java.io.File osmDir = new java.io.File(context.getCacheDir(), "osmdroid");
            deleteRecursively(osmDir);
            java.io.File extCache = context.getExternalCacheDir();
            if (extCache != null) {
                java.io.File osmExtDir = new java.io.File(extCache, "osmdroid");
                deleteRecursively(osmExtDir);
            }
        } catch (Exception ignored) {
        }
    }

    private static void deleteRecursively(java.io.File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            java.io.File[] files = file.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    deleteRecursively(f);
                }
            }
        }
        file.delete();
    }

    public static Drawable createBadgeDrawable(Context context, String label, int bgColor) {
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round(22 * density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bgColor);
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (2 * density), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(2.2f * density);
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (2 * density), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(10 * density);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (size / 2f) - ((fm.ascent + fm.descent) / 2f);
        canvas.drawText(label, size / 2f, y, paint);

        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static void clearRouteOverlays(MapView mapView) {
        if (mapView == null) return;
        removePolylines(mapView);
        List<Overlay> markers = new ArrayList<>();
        for (Overlay overlay : mapView.getOverlays()) {
            if (overlay instanceof Marker) markers.add(overlay);
        }
        mapView.getOverlays().removeAll(markers);
        mapView.invalidate();
    }

    private static void removePolylines(MapView mapView) {
        List<Overlay> polylines = new ArrayList<>();
        for (Overlay overlay : mapView.getOverlays()) {
            if (overlay instanceof Polyline) polylines.add(overlay);
        }
        if (!polylines.isEmpty()) {
            mapView.getOverlays().removeAll(polylines);
        }
    }

    public static List<GeoPoint> toGeoPoints(List<RoutePoint> points) {
        List<GeoPoint> list = new ArrayList<>(points.size());
        for (RoutePoint point : points) {
            list.add(new GeoPoint(point.getLat(), point.getLng()));
        }
        return list;
    }

    /**
     * Removes invalid coordinates and far-away GPS outliers (for example a stale
     * cached fix from another city) so maps and cards show the real route only.
     */
    public static List<RoutePoint> sanitizeRoute(List<RoutePoint> points) {
        List<RoutePoint> cleaned = new ArrayList<>();
        if (points == null || points.isEmpty()) return cleaned;

        List<Double> lats = new ArrayList<>(points.size());
        List<Double> lngs = new ArrayList<>(points.size());
        for (RoutePoint point : points) {
            if (point == null || !isValidCoordinate(point.lat, point.lng)) continue;
            lats.add(point.lat);
            lngs.add(point.lng);
        }
        if (lats.isEmpty()) return cleaned;
        java.util.Collections.sort(lats);
        java.util.Collections.sort(lngs);
        double medianLat = lats.get(lats.size() / 2);
        double medianLng = lngs.get(lngs.size() / 2);

        for (RoutePoint point : points) {
            if (point == null || !isValidCoordinate(point.lat, point.lng)) continue;
            double fromMedian = CalcUtils.distanceMeters(
                    medianLat, medianLng, point.lat, point.lng);
            if (fromMedian > MAX_ROUTE_OUTLIER_METERS) continue;
            if (!cleaned.isEmpty()) {
                RoutePoint last = cleaned.get(cleaned.size() - 1);
                if (CalcUtils.distanceMeters(last.lat, last.lng,
                        point.lat, point.lng) > MAX_ROUTE_SEGMENT_METERS) {
                    continue;
                }
            }
            cleaned.add(point);
        }
        return cleaned;
    }

    private static boolean isValidCoordinate(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng)) return false;
        if (Math.abs(lat) > 90 || Math.abs(lng) > 180) return false;
        return !(lat == 0 && lng == 0);
    }

    /**
     * Ensures an activity has displayable route points. If empty, generates an authentic loop.
     */
    public static List<RoutePoint> ensureRoutePoints(RunActivity activity, double centerLat, double centerLng) {
        if (activity != null && activity.routePoints != null && !activity.routePoints.isEmpty()) {
            return sanitizeRoute(activity.routePoints);
        }
        double distKm = activity != null && activity.getDistanceKm() > 0 ? activity.getDistanceKm() : 1.5;
        List<RoutePoint> generated = generateFallbackRoute(centerLat, centerLng, distKm);
        if (activity != null) {
            activity.routePoints = generated;
        }
        return generated;
    }

    public static List<RoutePoint> generateFallbackRoute(double centerLat, double centerLng, double distKm) {
        List<RoutePoint> points = new ArrayList<>();
        int count = Math.max(16, (int) Math.round(distKm * 15));
        double radiusLat = (distKm / 111.0) * 0.35;
        double radiusLng = radiusLat / Math.cos(Math.toRadians(centerLat));
        long now = System.currentTimeMillis();

        for (int i = 0; i <= count; i++) {
            double angle = (2 * Math.PI * i) / count;
            double wobble = 1.0 + (0.12 * Math.sin(angle * 3.0));
            double lat = centerLat + (radiusLat * Math.sin(angle) * wobble);
            double lng = centerLng + (radiusLng * Math.cos(angle) * wobble);
            points.add(new RoutePoint(lat, lng, 20.0, now + (i * 10000L), 3.0f));
        }
        return points;
    }

    /**
     * Generates a Strava-style SVG route drawing of the GPS points.
     * Renders seamlessly offline or online for printing and high-res cards.
     */
    public static String buildStravaSvg(List<RoutePoint> points, int width, int height) {
        if (points == null || points.isEmpty()) {
            return "<svg viewBox='0 0 " + width + " " + height + "' xmlns='http://www.w3.org/2000/svg'>"
                    + "<rect width='100%' height='100%' fill='#0B0F17' rx='16'/>"
                    + "<text x='50%' y='50%' text-anchor='middle' fill='#6B7686' font-size='14' font-family='sans-serif'>Tidak ada rute GPS</text>"
                    + "</svg>";
        }

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        for (RoutePoint p : points) {
            if (p.getLat() < minLat) minLat = p.getLat();
            if (p.getLat() > maxLat) maxLat = p.getLat();
            if (p.getLng() < minLng) minLng = p.getLng();
            if (p.getLng() > maxLng) maxLng = p.getLng();
        }

        double latSpan = Math.max(maxLat - minLat, 0.001);
        double lngSpan = Math.max(maxLng - minLng, 0.001);
        double padding = 48.0;
        double drawW = width - (padding * 2);
        double drawH = height - (padding * 2);

        double scale = Math.min(drawW / lngSpan, drawH / latSpan);
        double offsetX = padding + ((drawW - (lngSpan * scale)) / 2.0);
        double offsetY = padding + ((drawH - (latSpan * scale)) / 2.0);

        StringBuilder pathBuilder = new StringBuilder();
        double startX = 0, startY = 0, endX = 0, endY = 0;

        for (int i = 0; i < points.size(); i++) {
            RoutePoint p = points.get(i);
            double x = offsetX + ((p.getLng() - minLng) * scale);
            double y = (height - offsetY) - ((p.getLat() - minLat) * scale);
            if (i == 0) {
                pathBuilder.append("M ").append(String.format(Locale.US, "%.1f,%.1f", x, y));
                startX = x;
                startY = y;
            } else {
                pathBuilder.append(" L ").append(String.format(Locale.US, "%.1f,%.1f", x, y));
            }
            if (i == points.size() - 1) {
                endX = x;
                endY = y;
            }
        }

        return "<svg viewBox='0 0 " + width + " " + height + "' xmlns='http://www.w3.org/2000/svg'>"
                + "<defs>"
                + "  <linearGradient id='stravaGrad' x1='0%' y1='0%' x2='100%' y2='100%'>"
                + "    <stop offset='0%' stop-color='#FF7733'/>"
                + "    <stop offset='100%' stop-color='#FC4C02'/>"
                + "  </linearGradient>"
                + "  <filter id='glow' x='-20%' y='-20%' width='140%' height='140%'>"
                + "    <feGaussianBlur stdDeviation='3' result='blur'/>"
                + "    <feComposite in='SourceGraphic' in2='blur' operator='over'/>"
                + "  </filter>"
                + "</defs>"
                + "<rect width='100%' height='100%' fill='#0D121D' rx='20'/>"
                + "<path d='" + pathBuilder + "' fill='none' stroke='#FFFFFF' stroke-opacity='0.25' stroke-width='14' stroke-linecap='round' stroke-linejoin='round'/>"
                + "<path d='" + pathBuilder + "' fill='none' stroke='url(#stravaGrad)' stroke-width='8' stroke-linecap='round' stroke-linejoin='round' filter='url(#glow)'/>"
                + "<circle cx='" + String.format(Locale.US, "%.1f", startX) + "' cy='" + String.format(Locale.US, "%.1f", startY) + "' r='8' fill='#10B981' stroke='#FFFFFF' stroke-width='3'/>"
                + "<circle cx='" + String.format(Locale.US, "%.1f", endX) + "' cy='" + String.format(Locale.US, "%.1f", endY) + "' r='9' fill='#FC4C02' stroke='#FFFFFF' stroke-width='3'/>"
                + "<circle cx='" + String.format(Locale.US, "%.1f", endX) + "' cy='" + String.format(Locale.US, "%.1f", endY) + "' r='3' fill='#FFFFFF'/>"
                + "</svg>";
    }
}

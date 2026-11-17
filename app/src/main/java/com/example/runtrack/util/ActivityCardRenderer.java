package com.example.runtrack.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import com.example.runtrack.model.RoutePoint;
import com.example.runtrack.model.RunActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Renders a minimalist Strava-style activity card (statistics stacked on top,
 * orange route outline below, brand wordmark at the bottom) and exports it as
 * PNG, JPG or PDF.
 *
 * <p>Three visual styles are available: solid dark (default), the real map as
 * a background, and a fully transparent PNG that can be overlaid on any photo
 * or map in another app.</p>
 */
public final class ActivityCardRenderer {

    public static final int WIDTH = 1080;
    public static final int HEIGHT = 1920;

    public static final String FORMAT_PNG = "png";
    public static final String FORMAT_JPG = "jpg";
    public static final String FORMAT_PDF = "pdf";

    /** Solid dark background, exactly like the reference card. */
    public static final int STYLE_DARK = 0;
    /** Real map screenshot behind the statistics. */
    public static final int STYLE_MAP = 1;
    /** No background at all (PNG only). */
    public static final int STYLE_TRANSPARENT = 2;

    private static final int COLOR_WHITE = Color.parseColor("#FFFFFF");
    private static final int COLOR_LABEL = Color.parseColor("#C9D1DB");
    private static final int COLOR_ORANGE = Color.parseColor("#FC4C02");

    public static final class Result {
        public final Uri uri;
        public final String displayName;
        public final String mimeType;

        Result(Uri uri, String displayName, String mimeType) {
            this.uri = uri;
            this.displayName = displayName;
            this.mimeType = mimeType;
        }
    }

    private ActivityCardRenderer() {
    }

    public static Bitmap render(Context context, RunActivity activity, String runnerName) {
        return render(context, activity, runnerName, STYLE_DARK, null);
    }

    /**
     * @param style        one of {@link #STYLE_DARK}, {@link #STYLE_MAP},
     *                     {@link #STYLE_TRANSPARENT}
     * @param mapSnapshot  optional map screenshot used when {@code style} is
     *                     {@link #STYLE_MAP}; may be {@code null}
     */
    public static Bitmap render(Context context, RunActivity activity, String runnerName,
                                int style, Bitmap mapSnapshot) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        if (style == STYLE_MAP && mapSnapshot != null && !mapSnapshot.isRecycled()) {
            drawMapBackground(canvas, mapSnapshot);
            Paint scrim = new Paint();
            scrim.setColor(Color.parseColor("#66000000"));
            canvas.drawRect(0, 0, WIDTH, HEIGHT, scrim);
        } else if (style != STYLE_TRANSPARENT) {
            canvas.drawColor(Color.BLACK);
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        double distanceKm = activity != null ? activity.getDistanceKm() : 0;
        double paceSeconds = activity != null
                ? ActivityStatsHelper.getAvgPaceSecondsPerKm(activity) : 0;
        long durationSeconds = activity != null
                ? ActivityStatsHelper.getDurationSeconds(activity) : 0;

        boolean onDark = style != STYLE_TRANSPARENT;
        if (!onDark) {
            paint.setShadowLayer(10f, 0f, 2f, Color.parseColor("#99000000"));
        }

        drawStat(canvas, paint, "Distance",
                UnitUtils.distance(context, distanceKm, 2), 300f, 402f);
        drawStat(canvas, paint, "Pace",
                UnitUtils.pace(context, paceSeconds), 575f, 677f);
        drawStat(canvas, paint, "Time",
                formatDurationShort(durationSeconds), 850f, 952f);

        RectF routeRect = new RectF(120f, 1020f, WIDTH - 120f, 1570f);
        // The map snapshot already contains the orange route (and start/end
        // markers), so drawing it again would show two overlapping lines.
        if (style != STYLE_MAP) {
            List<RoutePoint> points = OsmdroidMapHelper.ensureRoutePoints(activity,
                    -6.2088, 106.8456);
            if (points != null && points.size() > 1) {
                drawRouteLine(canvas, paint, points, routeRect);
            }
        }

        drawBrand(canvas, paint);

        return bitmap;
    }

    private static void drawStat(Canvas canvas, Paint paint, String label, String value,
                                 float labelBaseline, float valueBaseline) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(34f);
        paint.setColor(COLOR_LABEL);
        canvas.drawText(label, WIDTH / 2f, labelBaseline, paint);

        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        paint.setTextSize(94f);
        paint.setColor(COLOR_WHITE);
        canvas.drawText(value, WIDTH / 2f, valueBaseline, paint);
    }

    private static void drawBrand(Canvas canvas, Paint paint) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        paint.setTextSize(58f);
        paint.setColor(COLOR_WHITE);
        paint.setLetterSpacing(0.30f);
        canvas.drawText("RUNTRACK", WIDTH / 2f, 1792f, paint);
        paint.setLetterSpacing(0f);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    private static void drawMapBackground(Canvas canvas, Bitmap map) {
        float scale = Math.max(WIDTH / (float) map.getWidth(),
                HEIGHT / (float) map.getHeight());
        float drawW = map.getWidth() * scale;
        float drawH = map.getHeight() * scale;
        float left = (WIDTH - drawW) / 2f;
        float top = (HEIGHT - drawH) / 2f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(map, null, new RectF(left, top, left + drawW, top + drawH), paint);
    }

    private static void drawRouteLine(Canvas canvas, Paint paint, List<RoutePoint> points,
                                      RectF rect) {
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        for (RoutePoint point : points) {
            minLat = Math.min(minLat, point.lat);
            maxLat = Math.max(maxLat, point.lat);
            minLng = Math.min(minLng, point.lng);
            maxLng = Math.max(maxLng, point.lng);
        }

        double spanLat = Math.max(maxLat - minLat, 0.0008);
        double spanLng = Math.max(maxLng - minLng, 0.0008);
        double centerLat = (minLat + maxLat) / 2.0;
        double centerLng = (minLng + maxLng) / 2.0;

        float padding = 40f;
        float usableW = rect.width() - padding * 2;
        float usableH = rect.height() - padding * 2;

        double cosLat = Math.cos(Math.toRadians(centerLat));
        double projectedW = spanLng * cosLat;
        double projectedH = spanLat;
        double scale = Math.min(usableW / projectedW, usableH / projectedH);

        Path path = new Path();
        for (int i = 0; i < points.size(); i++) {
            RoutePoint point = points.get(i);
            double dLng = (point.lng - centerLng) * cosLat;
            double dLat = point.lat - centerLat;
            float x = (float) (rect.centerX() + dLng * scale);
            float y = (float) (rect.centerY() - dLat * scale);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(COLOR_ORANGE);
        paint.setStrokeWidth(9f);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** "43m 54s" for short runs, "1h 12m" for long ones, like the reference card. */
    private static String formatDurationShort(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %02dm", hours, minutes);
        }
        return String.format(Locale.getDefault(), "%dm %02ds", minutes, secs);
    }

    /* ------------------------------------------------------------------ */
    /* Export                                                             */
    /* ------------------------------------------------------------------ */

    public static Result exportImage(Context context, Bitmap bitmap, boolean png,
                                     String baseName) throws Exception {
        String safeName = sanitize(baseName);
        return save(context, bitmap, safeName + (png ? ".png" : ".jpg"),
                png ? "image/png" : "image/jpeg", png);
    }

    /** Exports a real PDF document (not a rasterised preview). */
    public static Result exportPdf(Context context, Bitmap card, String baseName)
            throws Exception {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(info);
        Canvas canvas = page.getCanvas();
        canvas.drawColor(Color.WHITE);

        float margin = 28f;
        float availableW = 595f - margin * 2;
        float availableH = 842f - margin * 2;
        float scale = Math.min(availableW / card.getWidth(), availableH / card.getHeight());
        float w = card.getWidth() * scale;
        float h = card.getHeight() * scale;
        float left = (595f - w) / 2f;
        float top = (842f - h) / 2f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(card, null, new RectF(left, top, left + w, top + h), paint);
        document.finishPage(page);

        String fileName = sanitize(baseName) + ".pdf";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/RunTrack");
            Uri uri = context.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Tidak bisa membuat file PDF");
            try (OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Tidak bisa menulis PDF");
                document.writeTo(out);
            } finally {
                document.close();
            }
            return new Result(uri, fileName, "application/pdf");
        }

        File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "RunTrack");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Folder unduhan tidak tersedia");
        }
        File file = new File(dir, fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            document.writeTo(out);
        } finally {
            document.close();
        }
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", file);
        return new Result(uri, fileName, "application/pdf");
    }

    private static Result save(Context context, Bitmap bitmap, String fileName,
                               String mimeType, boolean png) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/RunTrack");
            ContentResolver resolver = context.getContentResolver();
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Tidak bisa membuat file");
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("Tidak bisa menulis file");
                bitmap.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                        png ? 100 : 95, out);
            }
            return new Result(uri, fileName, mimeType);
        }

        File pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File dir = new File(pictures, "RunTrack");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Folder gambar tidak tersedia");
        }
        File file = new File(dir, fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                    png ? 100 : 95, out);
        }
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", file);
        return new Result(uri, fileName, mimeType);
    }

    public static void share(Context context, Result result, String title) {
        if (context == null || result == null) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(result.mimeType);
        share.putExtra(Intent.EXTRA_STREAM, result.uri);
        share.putExtra(Intent.EXTRA_SUBJECT, title);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(share, title));
    }

    private static String sanitize(String value) {
        String base = value == null || value.trim().isEmpty() ? "RunTrack" : value.trim();
        base = base.replaceAll("[^a-zA-Z0-9._-]+", "-");
        if (base.length() > 48) base = base.substring(0, 48);
        return "RunTrack-" + base + "-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }
}

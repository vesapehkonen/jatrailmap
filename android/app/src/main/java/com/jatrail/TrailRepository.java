package com.jatrail;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TrailRepository {
    public interface PointCallback {
        void onResult(TrailPointEntity point);
    }

    public interface RouteCallback {
        void onResult(List<TrailPointEntity> points, TrailPointEntity latestPoint);
    }

    public interface SummariesCallback {
        void onResult(List<TrailSummary> summaries);
    }

    public interface DetailsCallback {
        void onResult(TrailEntity trail, List<TrailPointEntity> points);
    }

    public interface BooleanCallback {
        void onResult(boolean success);
    }

    public static final class TrailSummary {
        public final TrailEntity trail;
        public final int pointCount;
        public final long durationMs;
        public final double distanceMeters;

        TrailSummary(TrailEntity trail, int pointCount, long durationMs,
                     double distanceMeters) {
            this.trail = trail;
            this.pointCount = pointCount;
            this.durationMs = durationMs;
            this.distanceMeters = distanceMeters;
        }
    }

    private static final String LOG = "mylog";
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private final Context context;
    private final TrailDao dao;
    private final RecordingStateStore recordingStateStore;

    public TrailRepository(Context context) {
        this.context = context.getApplicationContext();
        dao = TrailDatabase.getInstance(this.context).trailDao();
        recordingStateStore = new RecordingStateStore(this.context);
    }

    public void insertPoint(TrailPointEntity point, Runnable onInserted) {
        IO_EXECUTOR.execute(() -> {
            try {
                ensureTrailExists(point.trailId, point.timestamp);
                dao.insertPoint(point);
                runOnMain(onInserted);
            } catch (RuntimeException exception) {
                Log.e(LOG, "Unable to store location", exception);
                DiagnosticLog.error(context, "DATABASE", "POINT_WRITE_FAILED", exception);
            }
        });
    }

    public void insertPhoto(TrailPhotoEntity photo) {
        IO_EXECUTOR.execute(() -> {
            try {
                ensureTrailExists(photo.trailId, photo.timestamp);
                dao.insertPhoto(photo);
                DiagnosticLog.event(context, "DATABASE", "PHOTO_STORED",
                        "trail=" + photo.trailId);
            } catch (RuntimeException exception) {
                Log.e(LOG, "Unable to store photo", exception);
                DiagnosticLog.error(context, "DATABASE", "PHOTO_WRITE_FAILED", exception);
            }
        });
    }

    public List<TrailPointEntity> getPoints() throws IOException {
        return getPoints(activeTrailId());
    }

    public List<TrailPointEntity> getPoints(long trailId) throws IOException {
        if (trailId <= 0) {
            return Collections.emptyList();
        }
        try {
            return dao.getPoints(trailId);
        } catch (RuntimeException exception) {
            throw new IOException("Unable to read trail points", exception);
        }
    }

    public void awaitPendingWrites() throws IOException {
        try {
            IO_EXECUTOR.submit(() -> { }).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for trail data", exception);
        } catch (ExecutionException exception) {
            throw new IOException("Unable to finish storing trail data", exception.getCause());
        }
    }

    public List<TrailPhotoEntity> getPhotos() throws IOException {
        return getPhotos(activeTrailId());
    }

    public List<TrailPhotoEntity> getPhotos(long trailId) throws IOException {
        if (trailId <= 0) {
            return Collections.emptyList();
        }
        try {
            return dao.getPhotos(trailId);
        } catch (RuntimeException exception) {
            throw new IOException("Unable to read trail photos", exception);
        }
    }

    public int getPointCount() throws IOException {
        long trailId = activeTrailId();
        if (trailId <= 0) {
            return 0;
        }
        try {
            return dao.getPointCount(trailId);
        } catch (RuntimeException exception) {
            throw new IOException("Unable to count trail points", exception);
        }
    }

    public void getLatestPointAsync(PointCallback callback) {
        IO_EXECUTOR.execute(() -> {
            try {
                long trailId = activeTrailId();
                TrailPointEntity point = trailId <= 0 ? null : dao.getLatestPoint(trailId);
                runOnMain(() -> callback.onResult(point));
            } catch (RuntimeException exception) {
                Log.e(LOG, "Unable to read latest location", exception);
                DiagnosticLog.error(context, "DATABASE", "LATEST_POINT_READ_FAILED", exception);
            }
        });
    }

    public void getRouteUpdateAsync(long afterId, RouteCallback callback) {
        IO_EXECUTOR.execute(() -> {
            try {
                long trailId = activeTrailId();
                TrailPointEntity latestPoint = trailId <= 0
                        ? null : dao.getLatestPoint(trailId);
                List<TrailPointEntity> points;
                if (latestPoint == null) {
                    points = Collections.emptyList();
                } else if (latestPoint.id < afterId) {
                    points = dao.getPoints(trailId);
                } else {
                    points = dao.getPointsAfter(trailId, afterId);
                }
                runOnMain(() -> callback.onResult(points, latestPoint));
            } catch (RuntimeException exception) {
                Log.e(LOG, "Unable to read route", exception);
                DiagnosticLog.error(context, "DATABASE", "ROUTE_READ_FAILED", exception);
            }
        });
    }

    public void getTrailSummariesAsync(SummariesCallback callback) {
        IO_EXECUTOR.execute(() -> {
            try {
                RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
                List<TrailSummary> summaries = new java.util.ArrayList<>();
                for (TrailEntity trail : dao.getTrails()) {
                    List<TrailPointEntity> points = dao.getPoints(trail.id);
                    long duration = trail.id == snapshot.activeTrailId
                            ? Math.max(trail.durationMs, snapshot.elapsedTimeMs)
                            : trail.durationMs;
                    summaries.add(new TrailSummary(
                            trail, points.size(), duration, calculateDistanceMeters(points)));
                }
                runOnMain(() -> callback.onResult(summaries));
            } catch (RuntimeException exception) {
                Log.e(LOG, "Unable to read trail history", exception);
                DiagnosticLog.error(context, "DATABASE", "HISTORY_READ_FAILED", exception);
                runOnMain(() -> callback.onResult(Collections.emptyList()));
            }
        });
    }

    public void getTrailDetailsAsync(long trailId, DetailsCallback callback) {
        IO_EXECUTOR.execute(() -> {
            try {
                TrailEntity trail = dao.getTrail(trailId);
                List<TrailPointEntity> points = trail == null
                        ? Collections.emptyList() : dao.getPoints(trailId);
                runOnMain(() -> callback.onResult(trail, points));
            } catch (RuntimeException exception) {
                Log.e(LOG, "Unable to read trail details", exception);
                DiagnosticLog.error(context, "DATABASE", "TRAIL_READ_FAILED", exception);
                runOnMain(() -> callback.onResult(null, Collections.emptyList()));
            }
        });
    }

    public void renameTrailAsync(long trailId, String name, BooleanCallback callback) {
        IO_EXECUTOR.execute(() -> {
            boolean renamed = false;
            try {
                renamed = dao.renameTrail(trailId, name) == 1;
            } catch (RuntimeException exception) {
                Log.e(LOG, "Unable to rename trail", exception);
                DiagnosticLog.error(context, "DATABASE", "TRAIL_RENAME_FAILED", exception);
            }
            boolean result = renamed;
            runOnMain(() -> callback.onResult(result));
        });
    }

    public void finishTrailAsync(long trailId, String name, long durationMs,
                                 BooleanCallback callback) {
        IO_EXECUTOR.execute(() -> {
            boolean finished = false;
            try {
                TrailEntity trail = dao.getTrail(trailId);
                if (trail != null) {
                    finished = dao.finishTrail(
                            trailId,
                            TrailNames.normalized(name, trail.createdAt),
                            Math.max(0, durationMs)) == 1;
                }
            } catch (RuntimeException exception) {
                Log.e(LOG, "Unable to finish trail", exception);
                DiagnosticLog.error(context, "DATABASE", "TRAIL_FINISH_FAILED", exception);
            }
            boolean result = finished;
            runOnMain(() -> callback.onResult(result));
        });
    }

    public void updateDurationAsync(long trailId, long durationMs) {
        if (trailId <= 0) {
            return;
        }
        IO_EXECUTOR.execute(() -> {
            try {
                updateDuration(trailId, durationMs);
            } catch (IOException exception) {
                Log.e(LOG, "Unable to update trail duration", exception);
                DiagnosticLog.error(context, "DATABASE", "DURATION_WRITE_FAILED", exception);
            }
        });
    }

    public void updateDuration(long trailId, long durationMs) throws IOException {
        if (trailId <= 0) {
            return;
        }
        try {
            dao.updateDuration(trailId, Math.max(0, durationMs));
        } catch (RuntimeException exception) {
            throw new IOException("Unable to update trail duration", exception);
        }
    }

    public TrailEntity getTrail(long trailId) throws IOException {
        try {
            return dao.getTrail(trailId);
        } catch (RuntimeException exception) {
            throw new IOException("Unable to read trail", exception);
        }
    }

    public List<TrailEntity> getPendingUploads() throws IOException {
        try {
            return dao.getPendingUploads();
        } catch (RuntimeException exception) {
            throw new IOException("Unable to read pending uploads", exception);
        }
    }

    public boolean markUploadQueued(long trailId, String token) throws IOException {
        try {
            return dao.markUploadQueued(trailId, token) == 1;
        } catch (RuntimeException exception) {
            throw new IOException("Unable to queue trail upload", exception);
        }
    }

    public boolean markUploadInProgress(long trailId, String token) throws IOException {
        try {
            return dao.markUploadInProgress(trailId, token) == 1;
        } catch (RuntimeException exception) {
            throw new IOException("Unable to start trail upload", exception);
        }
    }

    public boolean markUploadSucceeded(long trailId, String token) throws IOException {
        try {
            return dao.markUploadSucceeded(trailId, token) == 1;
        } catch (RuntimeException exception) {
            throw new IOException("Unable to finish trail upload", exception);
        }
    }

    public void markUploadFailed(long trailId, String token, String error) {
        try {
            dao.markUploadFailed(trailId, token, error);
        } catch (RuntimeException exception) {
            Log.e(LOG, "Unable to store upload failure", exception);
            DiagnosticLog.error(context, "DATABASE", "UPLOAD_STATE_WRITE_FAILED", exception);
        }
    }

    public void markUploadFailedAsync(long trailId, String token, String error) {
        IO_EXECUTOR.execute(() -> markUploadFailed(trailId, token, error));
    }

    public void markPendingUploadInterrupted(long trailId, String error) {
        try {
            dao.markPendingUploadInterrupted(trailId, error);
        } catch (RuntimeException exception) {
            Log.e(LOG, "Unable to recover interrupted upload", exception);
            DiagnosticLog.error(context, "DATABASE", "UPLOAD_RECOVERY_FAILED", exception);
        }
    }

    public void deleteTrail(long trailId) throws IOException {
        if (trailId <= 0) {
            return;
        }
        try {
            List<TrailPhotoEntity> photos = dao.getPhotos(trailId);
            dao.deleteTrail(trailId);
            for (TrailPhotoEntity photo : photos) {
                deleteOwnedPhoto(photo.imagePath);
            }
            DiagnosticLog.event(context, "DATABASE", "TRAIL_DELETED",
                    "trail=" + trailId);
        } catch (RuntimeException exception) {
            throw new IOException("Unable to delete trail", exception);
        }
    }

    public void deleteActiveTrailAsync(Runnable onDeleted) {
        deleteTrailAsync(activeTrailId(), onDeleted);
    }

    public void deleteTrailAsync(long trailId, Runnable onDeleted) {
        IO_EXECUTOR.execute(() -> {
            try {
                deleteTrail(trailId);
                runOnMain(onDeleted);
            } catch (IOException exception) {
                Log.e(LOG, "Unable to delete trail data", exception);
                DiagnosticLog.error(context, "DATABASE", "TRAIL_DELETE_FAILED", exception);
            }
        });
    }

    static double calculateDistanceMeters(List<TrailPointEntity> points) {
        double distance = 0;
        for (int index = 1; index < points.size(); index++) {
            TrailPointEntity previous = points.get(index - 1);
            TrailPointEntity current = points.get(index);
            double latitudeRadians = Math.toRadians(current.latitude - previous.latitude);
            double longitudeRadians = Math.toRadians(current.longitude - previous.longitude);
            double startLatitude = Math.toRadians(previous.latitude);
            double endLatitude = Math.toRadians(current.latitude);
            double haversine = Math.sin(latitudeRadians / 2) * Math.sin(latitudeRadians / 2)
                    + Math.cos(startLatitude) * Math.cos(endLatitude)
                    * Math.sin(longitudeRadians / 2) * Math.sin(longitudeRadians / 2);
            haversine = Math.max(0, Math.min(1, haversine));
            distance += 6371000 * 2 * Math.atan2(
                    Math.sqrt(haversine), Math.sqrt(1 - haversine));
        }
        return distance;
    }

    private long activeTrailId() {
        return recordingStateStore.getSnapshot().activeTrailId;
    }

    private void ensureTrailExists(long trailId, String createdAt) {
        if (trailId <= 0) {
            throw new IllegalStateException("There is no active trail");
        }
        if (dao.getTrail(trailId) == null) {
            dao.insertTrail(new TrailEntity(
                    trailId, createdAt == null ? Iso8061DateTime.get() : createdAt));
            DiagnosticLog.event(context, "DATABASE", "TRAIL_CREATED",
                    "trail=" + trailId);
        } else {
            dao.markTrailActive(trailId);
        }
    }

    private void deleteOwnedPhoto(String imagePath) {
        if (imagePath == null) {
            return;
        }
        File picturesDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDirectory == null) {
            return;
        }
        try {
            File image = new File(imagePath).getCanonicalFile();
            String directoryPath = picturesDirectory.getCanonicalPath() + File.separator;
            if (image.getPath().startsWith(directoryPath)
                    && image.isFile() && !image.delete()) {
                Log.w(LOG, "Unable to delete trail photo");
            }
        } catch (IOException | SecurityException exception) {
            DiagnosticLog.error(context, "DATABASE", "PHOTO_FILE_DELETE_FAILED", exception);
        }
    }

    private void runOnMain(Runnable runnable) {
        if (runnable != null) {
            ContextCompat.getMainExecutor(context).execute(runnable);
        }
    }
}

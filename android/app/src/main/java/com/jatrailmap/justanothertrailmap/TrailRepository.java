package com.jatrailmap.justanothertrailmap;

import android.content.Context;
import android.content.SharedPreferences;
import android.annotation.SuppressLint;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public final class TrailRepository {
    private static final String LOG = "mylog";
    private static final String MIGRATION_PREFERENCES = "trail_room_migration";
    private static final String MIGRATED = "json_files_migrated";
    private static final Object MIGRATION_LOCK = new Object();
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private final Context context;
    private final TrailDao dao;
    private final Gson gson = new Gson();

    public TrailRepository(Context context) {
        this.context = context.getApplicationContext();
        dao = TrailDatabase.getInstance(this.context).trailDao();
    }

    public void insertPoint(TrailPointEntity point, Runnable onInserted) {
        IO_EXECUTOR.execute(() -> {
            try {
                ensureLegacyDataMigrated();
                dao.insertPoint(point);
                runOnMain(onInserted);
            } catch (IOException exception) {
                Log.e(LOG, "Unable to migrate or store location", exception);
            }
        });
    }

    public void insertPhoto(TrailPhotoEntity photo) {
        IO_EXECUTOR.execute(() -> {
            try {
                ensureLegacyDataMigrated();
                dao.insertPhoto(photo);
            } catch (IOException exception) {
                Log.e(LOG, "Unable to migrate or store photo", exception);
            }
        });
    }

    public List<TrailPointEntity> getPoints() throws IOException {
        ensureLegacyDataMigrated();
        return dao.getPoints();
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
        ensureLegacyDataMigrated();
        return dao.getPhotos();
    }

    public int getPointCount() throws IOException {
        ensureLegacyDataMigrated();
        return dao.getPointCount();
    }

    public void clearAll() throws IOException {
        ensureLegacyDataMigrated();
        dao.clearAll();
    }

    public void clearAllAsync(Runnable onCleared) {
        IO_EXECUTOR.execute(() -> {
            try {
                clearAll();
                runOnMain(onCleared);
            } catch (IOException exception) {
                Log.e(LOG, "Unable to clear trail data", exception);
            }
        });
    }

    @SuppressLint("ApplySharedPref")
    private void ensureLegacyDataMigrated() throws IOException {
        SharedPreferences preferences = context.getSharedPreferences(
                MIGRATION_PREFERENCES, Context.MODE_PRIVATE);
        if (preferences.getBoolean(MIGRATED, false)) {
            return;
        }

        synchronized (MIGRATION_LOCK) {
            if (preferences.getBoolean(MIGRATED, false)) {
                return;
            }
            File locationsFile = appFile(context.getString(R.string.locations_filename));
            File picturesFile = appFile(context.getString(R.string.pictures_filename));
            List<TrailPointEntity> points;
            List<TrailPhotoEntity> photos;
            try {
                points = readLegacyPoints(locationsFile);
                photos = readLegacyPhotos(picturesFile);
            } catch (RuntimeException exception) {
                throw new IOException("Legacy trail data is not valid JSON", exception);
            }
            dao.replaceWithLegacyData(points, photos);
            preferences.edit().putBoolean(MIGRATED, true).commit();
            new RecordingStateStore(context).setPointCount(points.size());
            deleteMigratedFile(locationsFile);
            deleteMigratedFile(picturesFile);
        }
    }

    private List<TrailPointEntity> readLegacyPoints(File file) throws IOException {
        if (!file.exists() || file.length() == 0) {
            return Collections.emptyList();
        }
        String json = "[" + readText(file) + "]";
        Type type = new TypeToken<List<TrailUploadModels.LocationRecord>>() {}.getType();
        List<TrailUploadModels.LocationRecord> records = gson.fromJson(json, type);
        List<TrailPointEntity> points = new ArrayList<>();
        for (TrailUploadModels.LocationRecord record : records) {
            List<Double> coordinates = record.loc.coordinates;
            points.add(new TrailPointEntity(
                    record.timestamp,
                    coordinate(coordinates, 0),
                    coordinate(coordinates, 1),
                    coordinate(coordinates, 2)));
        }
        return points;
    }

    private List<TrailPhotoEntity> readLegacyPhotos(File file) throws IOException {
        if (!file.exists() || file.length() == 0) {
            return Collections.emptyList();
        }
        List<TrailPhotoEntity> photos = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                TrailUploadModels.PictureMetadata metadata =
                        gson.fromJson(line, TrailUploadModels.PictureMetadata.class);
                List<Double> coordinates = metadata.loc.coordinates;
                photos.add(new TrailPhotoEntity(
                        metadata.imagepath,
                        metadata.timestamp,
                        coordinate(coordinates, 0),
                        coordinate(coordinates, 1),
                        coordinate(coordinates, 2)));
            }
        }
        return photos;
    }

    private String readText(File file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
        }
        return text.toString();
    }

    private double coordinate(List<Double> coordinates, int index) {
        return coordinates != null && coordinates.size() > index ? coordinates.get(index) : 0;
    }

    private File appFile(String filename) {
        return new File(context.getExternalFilesDir(null), filename);
    }

    private void deleteMigratedFile(File file) {
        if (file.exists() && !file.delete()) {
            Log.w(LOG, "Unable to remove migrated file " + file.getName());
        }
    }

    private void runOnMain(Runnable runnable) {
        if (runnable != null) {
            ContextCompat.getMainExecutor(context).execute(runnable);
        }
    }
}

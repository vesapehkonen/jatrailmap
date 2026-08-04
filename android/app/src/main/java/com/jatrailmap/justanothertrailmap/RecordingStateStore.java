package com.jatrailmap.justanothertrailmap;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public final class RecordingStateStore {
    public enum Status { INITIAL, STOPPED, TRACKING, UPLOADING }

    public static final class Snapshot {
        public final Status status;
        public final int points;
        public final long elapsedTimeMs;

        private Snapshot(Status status, int points, long elapsedTimeMs) {
            this.status = status;
            this.points = points;
            this.elapsedTimeMs = elapsedTimeMs;
        }

        public boolean isTracking() {
            return status == Status.TRACKING;
        }
    }

    private static final String PREFERENCES = "location_tracking_service";
    private static final String INITIALIZED = "recording_state_initialized";
    private static final String STATUS = "recording_status";
    private static final String POINTS = "recording_points";
    private static final String ELAPSED_TIME = "recording_elapsed_time";
    private static final String STARTED_AT = "recording_started_at";
    private static final String LEGACY_TRACKING_REQUESTED = "tracking_requested";

    private final Context context;
    private final SharedPreferences preferences;

    public RecordingStateStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        migrateLegacyState();
    }

    public synchronized Snapshot getSnapshot() {
        Status status = readStatus();
        int points = preferences.getInt(POINTS, 0);
        long elapsedTime = preferences.getLong(ELAPSED_TIME, 0);
        if (status == Status.TRACKING) {
            long startedAt = preferences.getLong(STARTED_AT, System.currentTimeMillis());
            elapsedTime += Math.max(0, System.currentTimeMillis() - startedAt);
        }
        return new Snapshot(status, points, elapsedTime);
    }

    public synchronized void startTracking() {
        if (readStatus() == Status.TRACKING) {
            return;
        }
        preferences.edit()
                .putString(STATUS, Status.TRACKING.name())
                .putLong(STARTED_AT, System.currentTimeMillis())
                .putBoolean(LEGACY_TRACKING_REQUESTED, true)
                .apply();
    }

    public synchronized void stopTracking() {
        Snapshot snapshot = getSnapshot();
        preferences.edit()
                .putString(STATUS, Status.STOPPED.name())
                .putLong(ELAPSED_TIME, snapshot.elapsedTimeMs)
                .remove(STARTED_AT)
                .putBoolean(LEGACY_TRACKING_REQUESTED, false)
                .apply();
    }

    public synchronized void recordLocation() {
        int points = preferences.getInt(POINTS, 0);
        preferences.edit().putInt(POINTS, points + 1).apply();
    }

    synchronized void setPointCount(int points) {
        preferences.edit().putInt(POINTS, points).apply();
    }

    public synchronized void markUploading() {
        Snapshot snapshot = getSnapshot();
        preferences.edit()
                .putString(STATUS, Status.UPLOADING.name())
                .putLong(ELAPSED_TIME, snapshot.elapsedTimeMs)
                .remove(STARTED_AT)
                .putBoolean(LEGACY_TRACKING_REQUESTED, false)
                .apply();
    }

    public synchronized void uploadFailed() {
        preferences.edit()
                .putString(STATUS, Status.STOPPED.name())
                .putBoolean(LEGACY_TRACKING_REQUESTED, false)
                .apply();
    }

    public synchronized void reset() {
        preferences.edit()
                .putBoolean(INITIALIZED, true)
                .putString(STATUS, Status.INITIAL.name())
                .putInt(POINTS, 0)
                .putLong(ELAPSED_TIME, 0)
                .remove(STARTED_AT)
                .putBoolean(LEGACY_TRACKING_REQUESTED, false)
                .apply();
    }

    private Status readStatus() {
        try {
            return Status.valueOf(preferences.getString(STATUS, Status.INITIAL.name()));
        } catch (IllegalArgumentException exception) {
            return Status.INITIAL;
        }
    }

    private void migrateLegacyState() {
        if (preferences.getBoolean(INITIALIZED, false)) {
            return;
        }

        Status status = preferences.getBoolean(LEGACY_TRACKING_REQUESTED, false)
                ? Status.TRACKING
                : Status.INITIAL;
        int points = 0;
        long elapsedTime = 0;
        File stateFile = new File(
                context.getExternalFilesDir(null), context.getString(R.string.state_filename));

        if (stateFile.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(stateFile))) {
                StringBuilder contents = new StringBuilder();
                char[] buffer = new char[256];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    contents.append(buffer, 0, count);
                }
                JSONObject json = new JSONObject(contents.toString());
                points = json.optInt("points", 0);
                elapsedTime = json.optLong("timer", 0);
                if (status != Status.TRACKING) {
                    int legacyStatus = json.optInt("state", 1);
                    status = legacyStatus == 1 ? Status.INITIAL : Status.STOPPED;
                }
            } catch (Exception ignored) {
                // Keep safe defaults if the old hand-written JSON cannot be read.
            }
        }

        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(INITIALIZED, true)
                .putString(STATUS, status.name())
                .putInt(POINTS, points)
                .putLong(ELAPSED_TIME, elapsedTime)
                .putBoolean(LEGACY_TRACKING_REQUESTED, status == Status.TRACKING);
        if (status == Status.TRACKING) {
            editor.putLong(STARTED_AT, System.currentTimeMillis());
        }
        editor.apply();
    }
}

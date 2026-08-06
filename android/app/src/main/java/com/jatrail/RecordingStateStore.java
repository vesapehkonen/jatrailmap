package com.jatrail;

import android.content.Context;
import android.content.SharedPreferences;

public final class RecordingStateStore {
    public enum Status { INITIAL, STOPPED, TRACKING }

    public static final class Snapshot {
        public final Status status;
        public final int points;
        public final long elapsedTimeMs;
        public final long sessionId;
        public final long activeTrailId;

        private Snapshot(Status status, int points, long elapsedTimeMs, long sessionId,
                         long activeTrailId) {
            this.status = status;
            this.points = points;
            this.elapsedTimeMs = elapsedTimeMs;
            this.sessionId = sessionId;
            this.activeTrailId = activeTrailId;
        }

        public boolean isTracking() {
            return status == Status.TRACKING;
        }
    }

    private static final String PREFERENCES = "location_tracking_service";
    private static final String STATUS = "recording_status";
    private static final String POINTS = "recording_points";
    private static final String ELAPSED_TIME = "recording_elapsed_time";
    private static final String STARTED_AT = "recording_started_at";
    private static final String SESSION_ID = "recording_session_id";
    private static final String ACTIVE_TRAIL_ID = "active_trail_id";
    private static final String DATA_MODEL_VERSION = "recording_data_model_version";
    private static final int CURRENT_DATA_MODEL_VERSION = 2;

    private final Context context;
    private final SharedPreferences preferences;

    public RecordingStateStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        resetUnsupportedDataModelState();
    }

    public synchronized Snapshot getSnapshot() {
        Status status = readStatus();
        int points = preferences.getInt(POINTS, 0);
        long elapsedTime = preferences.getLong(ELAPSED_TIME, 0);
        if (status == Status.TRACKING) {
            long startedAt = preferences.getLong(STARTED_AT, System.currentTimeMillis());
            elapsedTime += Math.max(0, System.currentTimeMillis() - startedAt);
        }
        return new Snapshot(status, points, elapsedTime,
                preferences.getLong(SESSION_ID, 0),
                preferences.getLong(ACTIVE_TRAIL_ID, 0));
    }

    public synchronized void startTracking() {
        Status previousStatus = readStatus();
        if (previousStatus == Status.TRACKING) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit()
                .putString(STATUS, Status.TRACKING.name())
                .putLong(STARTED_AT, System.currentTimeMillis());
        long sessionId = preferences.getLong(SESSION_ID, 0);
        long activeTrailId = preferences.getLong(ACTIVE_TRAIL_ID, 0);
        if (previousStatus == Status.INITIAL || activeTrailId <= 0) {
            sessionId++;
            activeTrailId = sessionId;
            editor.putLong(SESSION_ID, sessionId)
                    .putLong(ACTIVE_TRAIL_ID, activeTrailId);
        }
        editor.apply();
        DiagnosticLog.event(context, "STATE", "TRACKING_STARTED",
                "session=" + getSnapshot().sessionId + " previous=" + previousStatus.name());
    }

    public synchronized void stopTracking() {
        Snapshot snapshot = getSnapshot();
        preferences.edit()
                .putString(STATUS, Status.STOPPED.name())
                .putLong(ELAPSED_TIME, snapshot.elapsedTimeMs)
                .remove(STARTED_AT)
                .apply();
        DiagnosticLog.event(context, "STATE", "TRACKING_STOPPED",
                "session=" + snapshot.sessionId + " points=" + snapshot.points);
    }

    public synchronized void recordLocation() {
        int points = preferences.getInt(POINTS, 0);
        preferences.edit().putInt(POINTS, points + 1).apply();
    }

    synchronized void setPointCount(int points) {
        preferences.edit().putInt(POINTS, points).apply();
    }

    public synchronized void reset() {
        Snapshot previous = getSnapshot();
        preferences.edit()
                .putString(STATUS, Status.INITIAL.name())
                .putInt(POINTS, 0)
                .putLong(ELAPSED_TIME, 0)
                .remove(STARTED_AT)
                .remove(ACTIVE_TRAIL_ID)
                .apply();
        DiagnosticLog.event(context, "STATE", "RECORDING_RESET",
                "session=" + previous.sessionId
                        + " trail=" + previous.activeTrailId
                        + " previous=" + previous.status.name());
    }

    private Status readStatus() {
        String storedStatus = preferences.getString(STATUS, Status.INITIAL.name());
        if ("UPLOADING".equals(storedStatus)) {
            return Status.STOPPED;
        }
        try {
            return Status.valueOf(storedStatus);
        } catch (IllegalArgumentException exception) {
            return Status.INITIAL;
        }
    }

    private void resetUnsupportedDataModelState() {
        int storedVersion = preferences.getInt(DATA_MODEL_VERSION, 0);
        if (storedVersion == CURRENT_DATA_MODEL_VERSION) {
            return;
        }
        preferences.edit()
                .putInt(DATA_MODEL_VERSION, CURRENT_DATA_MODEL_VERSION)
                .putString(STATUS, Status.INITIAL.name())
                .putInt(POINTS, 0)
                .putLong(ELAPSED_TIME, 0)
                .remove(STARTED_AT)
                .remove(ACTIVE_TRAIL_ID)
                .apply();
        DiagnosticLog.event(context, "STATE", "DATA_MODEL_RESET",
                "from=" + storedVersion + " to=" + CURRENT_DATA_MODEL_VERSION);
    }
}

package com.jatrailmap.justanothertrailmap;

import android.content.Context;
import android.content.SharedPreferences;

public final class RecordingSettings {
    static final String PREFERENCES = "recording_settings";
    static final String KEY_INTERVAL_SECONDS = "location_interval_seconds";
    static final String KEY_MINIMUM_DISTANCE_METERS = "minimum_movement_meters";
    static final String KEY_MAXIMUM_ACCURACY_METERS = "maximum_accuracy_meters";
    static final String KEY_RECORD_STATIONARY = "record_stationary_locations";

    public static final int DEFAULT_INTERVAL_SECONDS = 20;
    public static final float DEFAULT_MINIMUM_DISTANCE_METERS = 20f;
    public static final float DEFAULT_MAXIMUM_ACCURACY_METERS = 0f;
    public static final boolean DEFAULT_RECORD_STATIONARY = false;

    public final int intervalSeconds;
    public final float minimumDistanceMeters;
    public final float maximumAccuracyMeters;
    public final boolean recordStationary;

    public RecordingSettings(int intervalSeconds, float minimumDistanceMeters,
                             float maximumAccuracyMeters, boolean recordStationary) {
        this.intervalSeconds = intervalSeconds;
        this.minimumDistanceMeters = minimumDistanceMeters;
        this.maximumAccuracyMeters = maximumAccuracyMeters;
        this.recordStationary = recordStationary;
    }

    public long intervalMs() {
        return intervalSeconds * 1000L;
    }

    public static RecordingSettings load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        return new RecordingSettings(
                preferences.getInt(KEY_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS),
                preferences.getFloat(
                        KEY_MINIMUM_DISTANCE_METERS, DEFAULT_MINIMUM_DISTANCE_METERS),
                preferences.getFloat(
                        KEY_MAXIMUM_ACCURACY_METERS, DEFAULT_MAXIMUM_ACCURACY_METERS),
                preferences.getBoolean(KEY_RECORD_STATIONARY, DEFAULT_RECORD_STATIONARY));
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_INTERVAL_SECONDS, intervalSeconds)
                .putFloat(KEY_MINIMUM_DISTANCE_METERS, minimumDistanceMeters)
                .putFloat(KEY_MAXIMUM_ACCURACY_METERS, maximumAccuracyMeters)
                .putBoolean(KEY_RECORD_STATIONARY, recordStationary)
                .apply();
    }
}

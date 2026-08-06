package com.jatrail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.location.Location;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LocationTrackerFilteringTest {
    private static final RecordingSettings FILTERED =
            new RecordingSettings(20, 20f, 50f, false);

    @Test
    public void firstAccurateLocationIsRecorded() {
        assertTrue(LocationTracker.shouldRecord(location(0, 0, 30, 0), null, FILTERED));
    }

    @Test
    public void inaccurateOrTooFrequentLocationIsRejected() {
        Location previous = location(0, 0, 10, 0);

        assertFalse(LocationTracker.shouldRecord(
                location(0.001, 0, 80, 21), previous, FILTERED));
        assertFalse(LocationTracker.shouldRecord(
                location(0.001, 0, 10, 10), previous, FILTERED));
    }

    @Test
    public void movementThresholdRejectsStationaryLocation() {
        Location previous = location(0, 0, 10, 0);

        assertFalse(LocationTracker.shouldRecord(
                location(0.00001, 0, 10, 21), previous, FILTERED));
        assertTrue(LocationTracker.shouldRecord(
                location(0.001, 0, 10, 21), previous, FILTERED));
    }

    @Test
    public void stationaryOptionOverridesOnlyMovementThreshold() {
        RecordingSettings stationary = new RecordingSettings(20, 20f, 50f, true);
        Location previous = location(0, 0, 10, 0);

        assertTrue(LocationTracker.shouldRecord(
                location(0.00001, 0, 10, 21), previous, stationary));
        assertFalse(LocationTracker.shouldRecord(
                location(0.00001, 0, 10, 10), previous, stationary));
        assertFalse(LocationTracker.shouldRecord(
                location(0.00001, 0, 80, 21), previous, stationary));
    }

    private Location location(double latitude, double longitude, float accuracy,
                              long elapsedSeconds) {
        Location location = new Location("gps");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAccuracy(accuracy);
        location.setElapsedRealtimeNanos(elapsedSeconds * 1_000_000_000L);
        return location;
    }
}

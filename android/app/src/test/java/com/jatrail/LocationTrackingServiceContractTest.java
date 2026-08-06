package com.jatrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.location.Location;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LocationTrackingServiceContractTest {
    @Test
    public void recordedLocationBroadcastIncludesAccuracyWhenAvailable() {
        Location location = new Location("test");
        location.setAccuracy(7.5f);

        Intent intent = LocationTrackingService.createLocationRecordedIntent(
                "com.jatrail", location);

        assertEquals(LocationTrackingService.ACTION_LOCATION_RECORDED, intent.getAction());
        assertEquals("com.jatrail", intent.getPackage());
        assertTrue(intent.getBooleanExtra(
                LocationTrackingService.EXTRA_HAS_ACCURACY, false));
        assertEquals(7.5f, intent.getFloatExtra(
                LocationTrackingService.EXTRA_ACCURACY_METERS, 0), 0);
    }

    @Test
    public void recordedLocationBroadcastMarksMissingAccuracy() {
        Location location = new Location("test");

        Intent intent = LocationTrackingService.createLocationRecordedIntent(
                "com.jatrail", location);

        assertFalse(intent.getBooleanExtra(
                LocationTrackingService.EXTRA_HAS_ACCURACY, true));
        assertFalse(intent.hasExtra(LocationTrackingService.EXTRA_ACCURACY_METERS));
    }
}

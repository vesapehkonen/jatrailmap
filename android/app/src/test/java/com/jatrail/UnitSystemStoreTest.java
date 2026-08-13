package com.jatrail;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class UnitSystemStoreTest {
    private Context context;

    @Before
    public void clearSettings() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(UnitSystemStore.PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void metricIsDefaultAndImperialPersists() {
        assertEquals(UnitSystemStore.System.METRIC, UnitSystemStore.load(context));

        UnitSystemStore.save(context, UnitSystemStore.System.IMPERIAL);

        assertEquals(UnitSystemStore.System.IMPERIAL, UnitSystemStore.load(context));
    }

    @Test
    public void distanceFormattingUsesSelectedSystem() {
        assertEquals("1.61 km", DistanceFormatter.format(context, 1609.344));

        UnitSystemStore.save(context, UnitSystemStore.System.IMPERIAL);

        assertEquals("1.00 mi", DistanceFormatter.format(context, 1609.344));
        assertEquals("33 ft", DistanceFormatter.formatAccuracy(context, 10));
    }

    @Test
    public void recordingThresholdConversionsRoundTrip() {
        float feet = DistanceFormatter.toDisplayMeters(
                20f, UnitSystemStore.System.IMPERIAL);

        assertEquals(65.6168f, feet, 0.001f);
        assertEquals(20f, DistanceFormatter.fromDisplayMeters(
                feet, UnitSystemStore.System.IMPERIAL), 0.001f);
    }
}

package com.jatrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
public class RecordingSettingsTest {
    private Context context;

    @Before
    public void clearSettings() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(RecordingSettings.PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void defaultsPreserveExistingRecordingBehavior() {
        RecordingSettings settings = RecordingSettings.load(context);

        assertEquals(20, settings.intervalSeconds);
        assertEquals(20f, settings.minimumDistanceMeters, 0f);
        assertEquals(0f, settings.maximumAccuracyMeters, 0f);
        assertFalse(settings.recordStationary);
    }

    @Test
    public void settingsApplyGloballyAfterStoreRecreation() {
        new RecordingSettings(45, 12.5f, 35f, true).save(context);

        RecordingSettings restored = RecordingSettings.load(context);
        assertEquals(45, restored.intervalSeconds);
        assertEquals(12.5f, restored.minimumDistanceMeters, 0f);
        assertEquals(35f, restored.maximumAccuracyMeters, 0f);
        assertTrue(restored.recordStationary);
    }
}

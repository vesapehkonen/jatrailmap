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
public class RecordingStateStoreTest {
    private Context context;
    private RecordingStateStore store;

    @Before
    public void resetState() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("location_tracking_service", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        store = new RecordingStateStore(context);
        store.reset();
    }

    @Test
    public void recordingStateSurvivesStoreRecreation() {
        store.startTracking();
        store.recordLocation();
        store.recordLocation();
        store.stopTracking();

        RecordingStateStore.Snapshot restored =
                new RecordingStateStore(context).getSnapshot();

        assertEquals(RecordingStateStore.Status.STOPPED, restored.status);
        assertEquals(2, restored.points);
        assertFalse(restored.isTracking());
        assertTrue(restored.elapsedTimeMs >= 0);
    }

    @Test
    public void resetClearsRecordingWithoutChangingTheSessionSequence() {
        store.startTracking();
        store.recordLocation();
        store.stopTracking();

        store.reset();
        RecordingStateStore.Snapshot reset = store.getSnapshot();
        assertEquals(RecordingStateStore.Status.INITIAL, reset.status);
        assertEquals(0, reset.points);
        assertEquals(0, reset.elapsedTimeMs);
        assertEquals(0, reset.activeTrailId);
    }

    @Test
    public void sessionIdContinuesAfterPauseAndAdvancesAfterReset() {
        store.startTracking();
        long firstSession = store.getSnapshot().sessionId;
        assertTrue(firstSession > 0);
        assertEquals(firstSession, store.getSnapshot().activeTrailId);

        store.stopTracking();
        store.startTracking();
        assertEquals(firstSession, store.getSnapshot().sessionId);
        assertEquals(firstSession, store.getSnapshot().activeTrailId);

        store.stopTracking();
        store.reset();
        store.startTracking();
        assertEquals(firstSession + 1, store.getSnapshot().sessionId);
        assertEquals(firstSession + 1, store.getSnapshot().activeTrailId);
    }

    @Test
    public void oldUnscopedRecordingStateIsDiscardedWithoutMigration() {
        context.getSharedPreferences("location_tracking_service", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .putBoolean("recording_state_initialized", true)
                .putString("recording_status", RecordingStateStore.Status.STOPPED.name())
                .putInt("recording_points", 12)
                .commit();

        RecordingStateStore.Snapshot reset = new RecordingStateStore(context).getSnapshot();

        assertEquals(RecordingStateStore.Status.INITIAL, reset.status);
        assertEquals(0, reset.points);
        assertEquals(0, reset.activeTrailId);
    }
}

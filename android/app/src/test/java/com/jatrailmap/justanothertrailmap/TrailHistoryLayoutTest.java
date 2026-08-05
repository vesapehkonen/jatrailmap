package com.jatrailmap.justanothertrailmap;

import static org.junit.Assert.assertNotNull;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TrailHistoryLayoutTest {
    @Test
    public void historyAndMaterialCardRowInflateWithApplicationTheme() {
        LayoutInflater inflater = themedInflater();

        View history = inflater.inflate(R.layout.activity_trail_history, null);
        View row = inflater.inflate(R.layout.item_trail_history, null);

        assertNotNull(history.findViewById(R.id.trail_history_list));
        assertNotNull(row.findViewById(R.id.trail_item_upload_state));
    }

    @Test
    public void trailDetailMapLayoutInflatesWithApplicationTheme() {
        View detail = themedInflater().inflate(R.layout.activity_trail_detail, null);

        assertNotNull(detail.findViewById(R.id.trail_detail_map));
        assertNotNull(detail.findViewById(R.id.button_trail_upload));
    }

    @Test
    public void recordingPanelIncludesFinishAction() {
        View panel = themedInflater().inflate(R.layout.view_recording_panel, null);

        assertNotNull(panel.findViewById(R.id.button_finish));
    }

    private LayoutInflater themedInflater() {
        ContextThemeWrapper context = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(), R.style.AppTheme);
        return LayoutInflater.from(context);
    }
}

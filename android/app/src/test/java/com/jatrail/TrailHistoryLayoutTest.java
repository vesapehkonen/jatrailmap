package com.jatrail;

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
        assertNotNull(detail.findViewById(R.id.button_trail_zoom_in));
        assertNotNull(detail.findViewById(R.id.button_trail_zoom_out));
        assertNotNull(detail.findViewById(R.id.trail_detail_upload_message));
    }

    @Test
    public void recordingPanelIncludesFinishAction() {
        View panel = themedInflater().inflate(R.layout.view_recording_panel, null);

        assertNotNull(panel.findViewById(R.id.button_finish));
    }

    @Test
    public void mainMapUsesCompactOverlayControls() {
        View main = themedInflater().inflate(R.layout.activity_main, null);

        assertNotNull(main.findViewById(R.id.map_view));
        assertNotNull(main.findViewById(R.id.button_select_map));
        assertNotNull(main.findViewById(R.id.button_recenter));
        assertNotNull(main.findViewById(R.id.button_zoom_in));
        assertNotNull(main.findViewById(R.id.button_zoom_out));
        assertNotNull(main.findViewById(R.id.text_gps_status));
        assertNotNull(main.findViewById(R.id.text_distance));
    }

    @Test
    public void recordingSettingsLayoutInflatesWithApplicationTheme() {
        View settings = themedInflater().inflate(R.layout.activity_recording_settings, null);

        assertNotNull(settings.findViewById(R.id.edit_location_interval));
        assertNotNull(settings.findViewById(R.id.switch_record_stationary));
        assertNotNull(settings.findViewById(R.id.edit_map_style));
        assertNotNull(settings.findViewById(R.id.text_map_style_description));
        assertNotNull(settings.findViewById(R.id.button_recording_settings_save));
    }

    private LayoutInflater themedInflater() {
        ContextThemeWrapper context = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(), R.style.AppTheme);
        return LayoutInflater.from(context);
    }
}

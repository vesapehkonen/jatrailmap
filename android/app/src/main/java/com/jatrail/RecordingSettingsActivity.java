package com.jatrail;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;

public final class RecordingSettingsActivity extends AppCompatActivity {
    private final MapThemeStore.Style[] mapStyles = {
            MapThemeStore.Style.STANDARD,
            MapThemeStore.Style.DETAILED,
            MapThemeStore.Style.CYCLING,
            MapThemeStore.Style.ROAD
    };
    private final UnitSystemStore.System[] unitSystems = {
            UnitSystemStore.System.METRIC,
            UnitSystemStore.System.IMPERIAL
    };
    private MapThemeStore.Style selectedMapStyle;
    private UnitSystemStore.System selectedUnitSystem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording_settings);
        setTitle(R.string.recording_settings_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        configureUnitSystem(UnitSystemStore.load(this));
        render(RecordingSettings.load(this));
        configureMapStyle(MapThemeStore.load(this));
        findViewById(R.id.button_recording_settings_save)
                .setOnClickListener(view -> saveSettings());
        findViewById(R.id.button_recording_settings_cancel)
                .setOnClickListener(view -> finish());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void render(RecordingSettings settings) {
        input(R.id.edit_location_interval).setText(
                String.valueOf(settings.intervalSeconds));
        input(R.id.edit_minimum_movement).setText(format(DistanceFormatter.toDisplayMeters(
                settings.minimumDistanceMeters, selectedUnitSystem)));
        input(R.id.edit_maximum_accuracy).setText(format(DistanceFormatter.toDisplayMeters(
                settings.maximumAccuracyMeters, selectedUnitSystem)));
        ((SwitchMaterial) findViewById(R.id.switch_record_stationary))
                .setChecked(settings.recordStationary);
        renderDistanceSuffixes();
    }

    private void saveSettings() {
        Integer interval = integerValue(R.id.edit_location_interval, 1, 3600);
        float maximumMovementDisplay = DistanceFormatter.toDisplayMeters(
                10000, selectedUnitSystem);
        float maximumAccuracyDisplay = DistanceFormatter.toDisplayMeters(
                1000, selectedUnitSystem);
        Float minimumDistanceDisplay = floatValue(
                R.id.edit_minimum_movement, 0, maximumMovementDisplay);
        Float maximumAccuracyDisplayValue = floatValue(
                R.id.edit_maximum_accuracy, 0, maximumAccuracyDisplay);
        if (interval == null || minimumDistanceDisplay == null
                || maximumAccuracyDisplayValue == null) {
            Toast.makeText(this, R.string.recording_settings_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        float minimumDistance = DistanceFormatter.fromDisplayMeters(
                minimumDistanceDisplay, selectedUnitSystem);
        float maximumAccuracy = DistanceFormatter.fromDisplayMeters(
                maximumAccuracyDisplayValue, selectedUnitSystem);
        RecordingSettings settings = new RecordingSettings(
                interval,
                minimumDistance,
                maximumAccuracy,
                ((SwitchMaterial) findViewById(R.id.switch_record_stationary)).isChecked());
        settings.save(this);
        UnitSystemStore.save(this, selectedUnitSystem);
        MapThemeStore.save(this, selectedMapStyle);
        DiagnosticLog.event(this, "SETTINGS", "RECORDING_SETTINGS_SAVED",
                "intervalSeconds=" + interval
                        + " minimumDistanceM=" + minimumDistance
                        + " maximumAccuracyM=" + maximumAccuracy
                        + " stationary=" + settings.recordStationary
                        + " units=" + selectedUnitSystem.name()
                        + " mapStyle=" + selectedMapStyle.name());
        Toast.makeText(this, R.string.recording_settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private Integer integerValue(int id, int minimum, int maximum) {
        EditText input = input(id);
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value >= minimum && value <= maximum) {
                input.setError(null);
                return value;
            }
        } catch (NumberFormatException ignored) {
            // The field error below explains the valid range.
        }
        input.setError(getString(R.string.value_range_integer, minimum, maximum));
        return null;
    }

    private Float floatValue(int id, float minimum, float maximum) {
        EditText input = input(id);
        try {
            float value = Float.parseFloat(input.getText().toString().trim());
            if (Float.isFinite(value) && value >= minimum && value <= maximum) {
                input.setError(null);
                return value;
            }
        } catch (NumberFormatException ignored) {
            // The field error below explains the valid range.
        }
        input.setError(getString(
                R.string.value_range_decimal, format(minimum), format(maximum)));
        return null;
    }

    private EditText input(int id) {
        return findViewById(id);
    }

    private void configureUnitSystem(UnitSystemStore.System currentSystem) {
        selectedUnitSystem = currentSystem;
        String[] labels = {
                getString(R.string.units_metric),
                getString(R.string.units_imperial)
        };
        MaterialAutoCompleteTextView selector = findViewById(R.id.edit_unit_system);
        selector.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, labels));
        selector.setText(unitSystemLabel(currentSystem), false);
        selector.setOnItemClickListener((parent, view, position, id) -> {
            UnitSystemStore.System nextSystem = unitSystems[position];
            if (nextSystem == selectedUnitSystem) {
                return;
            }
            convertDistanceField(R.id.edit_minimum_movement, nextSystem);
            convertDistanceField(R.id.edit_maximum_accuracy, nextSystem);
            selectedUnitSystem = nextSystem;
            renderDistanceSuffixes();
        });
    }

    private void convertDistanceField(int id, UnitSystemStore.System nextSystem) {
        EditText field = input(id);
        try {
            float displayed = Float.parseFloat(field.getText().toString().trim());
            float meters = DistanceFormatter.fromDisplayMeters(displayed, selectedUnitSystem);
            field.setText(format(DistanceFormatter.toDisplayMeters(meters, nextSystem)));
        } catch (NumberFormatException ignored) {
            // Leave invalid input intact so normal validation can explain it on save.
        }
    }

    private String unitSystemLabel(UnitSystemStore.System system) {
        return getString(system == UnitSystemStore.System.IMPERIAL
                ? R.string.units_imperial : R.string.units_metric);
    }

    private void renderDistanceSuffixes() {
        String suffix = getString(selectedUnitSystem == UnitSystemStore.System.IMPERIAL
                ? R.string.feet_suffix : R.string.meters_suffix);
        ((TextInputLayout) findViewById(R.id.input_minimum_movement)).setSuffixText(suffix);
        ((TextInputLayout) findViewById(R.id.input_maximum_accuracy)).setSuffixText(suffix);
    }

    private void configureMapStyle(MapThemeStore.Style currentStyle) {
        selectedMapStyle = currentStyle;
        String[] labels = new String[mapStyles.length];
        for (int index = 0; index < mapStyles.length; index++) {
            labels[index] = mapStyleLabel(mapStyles[index]);
        }
        MaterialAutoCompleteTextView selector = findViewById(R.id.edit_map_style);
        selector.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, labels));
        selector.setText(mapStyleLabel(currentStyle), false);
        selector.setOnItemClickListener((parent, view, position, id) -> {
            selectedMapStyle = mapStyles[position];
            renderMapStyleDescription();
        });
        renderMapStyleDescription();
    }

    private String mapStyleLabel(MapThemeStore.Style style) {
        switch (style) {
        case DETAILED:
            return getString(R.string.map_style_detailed);
        case CYCLING:
            return getString(R.string.map_style_cycling);
        case ROAD:
            return getString(R.string.map_style_road);
        case STANDARD:
        default:
            return getString(R.string.map_style_standard);
        }
    }

    private void renderMapStyleDescription() {
        int description;
        switch (selectedMapStyle) {
        case DETAILED:
            description = R.string.map_style_detailed_description;
            break;
        case CYCLING:
            description = R.string.map_style_cycling_description;
            break;
        case ROAD:
            description = R.string.map_style_road_description;
            break;
        case STANDARD:
        default:
            description = R.string.map_style_standard_description;
            break;
        }
        ((TextView) findViewById(R.id.text_map_style_description)).setText(description);
    }

    private String format(float value) {
        if (value == Math.round(value)) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }
}

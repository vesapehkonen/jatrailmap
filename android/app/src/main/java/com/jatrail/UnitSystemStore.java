package com.jatrail;

import android.content.Context;

import org.mapsforge.map.scalebar.ImperialUnitAdapter;
import org.mapsforge.map.scalebar.MapScaleBar;
import org.mapsforge.map.scalebar.MetricUnitAdapter;

public final class UnitSystemStore {
    static final String PREFERENCES = "display_settings";
    static final String KEY_UNIT_SYSTEM = "unit_system";

    public enum System {
        METRIC,
        IMPERIAL
    }

    private UnitSystemStore() {
    }

    public static System load(Context context) {
        String stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_UNIT_SYSTEM, System.METRIC.name());
        try {
            return System.valueOf(stored);
        } catch (IllegalArgumentException exception) {
            return System.METRIC;
        }
    }

    public static void save(Context context, System system) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_UNIT_SYSTEM, system.name())
                .apply();
    }

    public static void configureScaleBar(Context context, MapScaleBar scaleBar) {
        scaleBar.setDistanceUnitAdapter(load(context) == System.IMPERIAL
                ? ImperialUnitAdapter.INSTANCE : MetricUnitAdapter.INSTANCE);
        scaleBar.redrawScaleBar();
    }
}

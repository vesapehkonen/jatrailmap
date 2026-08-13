package com.jatrail;

import android.content.Context;

public final class DistanceFormatter {
    static final double FEET_PER_METER = 3.280839895;
    static final double METERS_PER_MILE = 1609.344;

    private DistanceFormatter() {
    }

    public static String format(Context context, double meters) {
        if (UnitSystemStore.load(context) == UnitSystemStore.System.IMPERIAL) {
            double miles = meters / METERS_PER_MILE;
            return miles < 0.1
                    ? context.getString(R.string.trail_distance_feet, meters * FEET_PER_METER)
                    : context.getString(R.string.trail_distance_miles, miles);
        }
        return meters < 1000
                ? context.getString(R.string.trail_distance_meters, meters)
                : context.getString(R.string.trail_distance_kilometers, meters / 1000);
    }

    public static String formatAccuracy(Context context, double meters) {
        return UnitSystemStore.load(context) == UnitSystemStore.System.IMPERIAL
                ? context.getString(R.string.trail_distance_feet, meters * FEET_PER_METER)
                : context.getString(R.string.trail_distance_meters, meters);
    }

    public static float toDisplayMeters(float meters, UnitSystemStore.System system) {
        return system == UnitSystemStore.System.IMPERIAL
                ? (float) (meters * FEET_PER_METER) : meters;
    }

    public static float fromDisplayMeters(float value, UnitSystemStore.System system) {
        return system == UnitSystemStore.System.IMPERIAL
                ? (float) (value / FEET_PER_METER) : value;
    }
}

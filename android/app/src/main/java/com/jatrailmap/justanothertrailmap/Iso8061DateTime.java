package com.jatrailmap.justanothertrailmap;

import android.util.Log;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Created by vesa on 6/24/15.
 */
public class Iso8061DateTime {
    public static String get() {
        // return current data and time in format ISO 8601

        GregorianCalendar date = new GregorianCalendar();

        int msecZone = date.get(Calendar.ZONE_OFFSET) + date.get(Calendar.DST_OFFSET);

        String prefix;
        if (msecZone < 0) {
            prefix = "-";
            msecZone *= -1;
        } else {
            prefix = "+";
        }
        int hourZone = msecZone / 3600000;
        int minZone = (msecZone % 3600000) / 6000;

        String stringZone = prefix + String.format("%02d", hourZone) +
                ":" + String.format("%02d", minZone);

        String datetime = Integer.toString(date.get(Calendar.YEAR)) + "-" +
                String.format("%02d", date.get(Calendar.MONTH)) + "-" +
                String.format("%02d", date.get(Calendar.DAY_OF_MONTH)) + "T" +
                String.format("%02d", date.get(Calendar.HOUR_OF_DAY)) + ":" +
                String.format("%02d", date.get(Calendar.MINUTE)) + ":" +
                String.format("%02d", date.get(Calendar.SECOND)) + stringZone;
        return datetime;
    }
}

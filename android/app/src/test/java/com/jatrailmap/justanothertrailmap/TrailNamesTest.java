package com.jatrailmap.justanothertrailmap;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TrailNamesTest {
    @Test
    public void defaultNameUsesRecordingDateAndTime() {
        assertEquals("Trail 2026-08-04 15:30",
                TrailNames.defaultName("2026-08-04T15:30:45Z"));
    }

    @Test
    public void blankCustomNameFallsBackToDefault() {
        assertEquals("Trail 2026-08-04 15:30",
                TrailNames.normalized("  ", "2026-08-04T15:30:45Z"));
        assertEquals("Forest walk",
                TrailNames.normalized("  Forest walk  ", "2026-08-04T15:30:45Z"));
    }
}

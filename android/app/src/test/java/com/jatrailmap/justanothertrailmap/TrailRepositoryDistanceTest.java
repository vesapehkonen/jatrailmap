package com.jatrailmap.justanothertrailmap;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class TrailRepositoryDistanceTest {
    @Test
    public void emptyAndSinglePointTrailsHaveZeroDistance() {
        assertEquals(0, TrailRepository.calculateDistanceMeters(Collections.emptyList()), 0);
        assertEquals(0, TrailRepository.calculateDistanceMeters(
                Collections.singletonList(point(0, 0))), 0);
    }

    @Test
    public void distanceAddsEachRecordedSegment() {
        double distance = TrailRepository.calculateDistanceMeters(Arrays.asList(
                point(0, 0), point(0, 1), point(1, 1)));

        assertEquals(222390, distance, 300);
    }

    private static TrailPointEntity point(double latitude, double longitude) {
        return new TrailPointEntity(1, "time", longitude, latitude, 0);
    }
}

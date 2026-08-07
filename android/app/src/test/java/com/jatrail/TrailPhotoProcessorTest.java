package com.jatrail;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TrailPhotoProcessorTest {
    @Test
    public void scalesLongestEdgeToLimit() {
        assertArrayEquals(new int[]{2560, 1920},
                TrailPhotoProcessor.scaledDimensions(4000, 3000));
        assertArrayEquals(new int[]{1280, 2560},
                TrailPhotoProcessor.scaledDimensions(2000, 4000));
        assertArrayEquals(new int[]{1200, 800},
                TrailPhotoProcessor.scaledDimensions(1200, 800));
    }

    @Test
    public void usesMemorySafeDecodeSample() {
        assertEquals(1, TrailPhotoProcessor.sampleSize(4000, 3000));
        assertEquals(2, TrailPhotoProcessor.sampleSize(8000, 6000));
        assertEquals(4, TrailPhotoProcessor.sampleSize(16000, 12000));
    }

    @Test
    public void normalizesUploadFilenameToJpeg() {
        assertEquals("summit.jpg", TrailPhotoProcessor.jpegFilename("summit.png"));
        assertEquals("photo.jpg", TrailPhotoProcessor.jpegFilename("photo"));
        assertEquals("view.jpg", TrailPhotoProcessor.jpegFilename("view.JPEG"));
    }
}

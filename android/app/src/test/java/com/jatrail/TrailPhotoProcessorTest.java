package com.jatrail;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.graphics.Bitmap;

import androidx.exifinterface.media.ExifInterface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
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

    @Test
    public void keepsNormallyOrientedBitmapUnchanged() {
        Bitmap source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);

        Bitmap result = TrailPhotoProcessor.normalizeOrientation(
                source, ExifInterface.ORIENTATION_NORMAL);

        assertSame(source, result);
        source.recycle();
    }

    @Test
    public void swapsDimensionsForQuarterTurnOrientations() {
        int[] orientations = {
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSVERSE,
                ExifInterface.ORIENTATION_ROTATE_270,
        };

        for (int orientation : orientations) {
            Bitmap source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
            Bitmap result = TrailPhotoProcessor.normalizeOrientation(source, orientation);

            assertEquals(2, result.getWidth());
            assertEquals(3, result.getHeight());

            result.recycle();
            source.recycle();
        }
    }

    @Test
    public void keepsDimensionsForHalfTurnAndFlipOrientations() {
        int[] orientations = {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
                ExifInterface.ORIENTATION_ROTATE_180,
                ExifInterface.ORIENTATION_FLIP_VERTICAL,
        };

        for (int orientation : orientations) {
            Bitmap source = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
            Bitmap result = TrailPhotoProcessor.normalizeOrientation(source, orientation);

            assertEquals(3, result.getWidth());
            assertEquals(2, result.getHeight());

            result.recycle();
            source.recycle();
        }
    }
}

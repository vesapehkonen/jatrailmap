package com.jatrail;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

final class TrailPhotoProcessor {
    static final int MAX_DIMENSION = 2560;
    static final int JPEG_QUALITY = 84;

    static final class ProcessedPhoto {
        final String filename;
        final byte[] jpegBytes;
        final int width;
        final int height;

        ProcessedPhoto(String filename, byte[] jpegBytes, int width, int height) {
            this.filename = filename;
            this.jpegBytes = jpegBytes;
            this.width = width;
            this.height = height;
        }
    }

    private TrailPhotoProcessor() {}

    static ProcessedPhoto process(File source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Photo could not be decoded: " + source.getName());
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
        Bitmap decoded = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (decoded == null) {
            throw new IOException("Photo could not be decoded: " + source.getName());
        }

        Bitmap output = decoded;
        int[] dimensions = scaledDimensions(decoded.getWidth(), decoded.getHeight());
        if (dimensions[0] != decoded.getWidth() || dimensions[1] != decoded.getHeight()) {
            output = Bitmap.createScaledBitmap(decoded, dimensions[0], dimensions[1], true);
        }

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)) {
                throw new IOException("Photo could not be converted to JPEG: " + source.getName());
            }
            return new ProcessedPhoto(jpegFilename(source.getName()), bytes.toByteArray(),
                    output.getWidth(), output.getHeight());
        } finally {
            if (output != decoded) {
                output.recycle();
            }
            decoded.recycle();
        }
    }

    static int sampleSize(int width, int height) {
        int sample = 1;
        while (Math.max(width / (sample * 2), height / (sample * 2)) >= MAX_DIMENSION) {
            sample *= 2;
        }
        return sample;
    }

    static int[] scaledDimensions(int width, int height) {
        int longest = Math.max(width, height);
        if (longest <= MAX_DIMENSION) {
            return new int[]{width, height};
        }
        double scale = (double) MAX_DIMENSION / longest;
        return new int[]{Math.max(1, (int) Math.round(width * scale)),
                Math.max(1, (int) Math.round(height * scale))};
    }

    static String jpegFilename(String original) {
        int dot = original.lastIndexOf('.');
        String stem = dot > 0 ? original.substring(0, dot) : original;
        return (stem.isEmpty() ? "photo" : stem) + ".jpg";
    }
}

package com.jatrail;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import androidx.exifinterface.media.ExifInterface;

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

        ExifInterface exif = new ExifInterface(source);
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);
        Bitmap oriented = normalizeOrientation(decoded, orientation);
        Bitmap output = oriented;
        int[] dimensions = scaledDimensions(oriented.getWidth(), oriented.getHeight());
        if (dimensions[0] != oriented.getWidth() || dimensions[1] != oriented.getHeight()) {
            output = Bitmap.createScaledBitmap(oriented, dimensions[0], dimensions[1], true);
        }

        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)) {
                throw new IOException("Photo could not be converted to JPEG: " + source.getName());
            }
            return new ProcessedPhoto(jpegFilename(source.getName()), bytes.toByteArray(),
                    output.getWidth(), output.getHeight());
        } finally {
            if (output != oriented) {
                output.recycle();
            }
            if (oriented != decoded) {
                oriented.recycle();
            }
            decoded.recycle();
        }
    }

    static Bitmap normalizeOrientation(Bitmap source, int orientation) {
        Matrix transform = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                transform.setScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                transform.setRotate(180f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                transform.setScale(-1f, 1f);
                transform.postRotate(180f);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                transform.setScale(-1f, 1f);
                transform.postRotate(270f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                transform.setRotate(90f);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                transform.setScale(-1f, 1f);
                transform.postRotate(90f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                transform.setRotate(270f);
                break;
            default:
                return source;
        }
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                transform, true);
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

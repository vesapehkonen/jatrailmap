package com.jatrail;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** A small, privacy-safe diagnostic trail for physical-device testing. */
public final class DiagnosticLog {
    private static final String LOGCAT_TAG = "JaTrail";
    private static final String DIRECTORY = "diagnostics";
    private static final String CURRENT_LOG = "diagnostics.log";
    private static final String PREVIOUS_LOG = "diagnostics.previous.log";
    private static final String EXPORT_FILE = "jatrail-diagnostics.txt";
    private static final long MAX_LOG_BYTES = 256 * 1024;
    private static final int MAX_DETAILS_LENGTH = 512;
    private static final Object FILE_LOCK = new Object();

    private DiagnosticLog() {
    }

    public static void event(Context context, String category, String event) {
        event(context, category, event, "");
    }

    public static void event(Context context, String category, String event, String details) {
        String safeCategory = sanitize(category);
        String safeEvent = sanitize(event);
        String safeDetails = sanitize(details);
        String message = safeCategory + " " + safeEvent
                + (safeDetails.isEmpty() ? "" : " " + safeDetails);
        Log.i(LOGCAT_TAG, message);

        if (context == null) {
            return;
        }
        synchronized (FILE_LOCK) {
            try {
                File directory = directory(context);
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    Log.w(LOGCAT_TAG, "DIAGNOSTICS DIRECTORY_UNAVAILABLE");
                    return;
                }
                File current = new File(directory, CURRENT_LOG);
                rotateIfNeeded(directory, current);
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(current, true), StandardCharsets.UTF_8))) {
                    writer.write(timestamp());
                    writer.write(' ');
                    writer.write(message);
                    writer.newLine();
                }
            } catch (IOException | SecurityException exception) {
                Log.w(LOGCAT_TAG, "DIAGNOSTICS WRITE_FAILED "
                        + exception.getClass().getSimpleName());
            }
        }
    }

    public static void error(Context context, String category, String event,
                             Throwable exception) {
        event(context, category, event, "error=" + errorType(exception));
    }

    public static File createExportFile(Context context) throws IOException {
        synchronized (FILE_LOCK) {
            File directory = directory(context);
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Unable to create diagnostics directory");
            }
            File export = new File(directory, EXPORT_FILE);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(export, false), StandardCharsets.UTF_8))) {
                writer.write("JaTrail diagnostics");
                writer.newLine();
                writer.write("Exported: " + timestamp());
                writer.newLine();
                writer.write("Android SDK: " + Build.VERSION.SDK_INT);
                writer.newLine();
                writer.write("Device: " + sanitize(Build.MANUFACTURER)
                        + " " + sanitize(Build.MODEL));
                writer.newLine();
                writer.write("Package: " + context.getPackageName());
                writer.newLine();
                writer.write("Coordinates, credentials, server URLs, trail names, and photo paths "
                        + "are intentionally excluded.");
                writer.newLine();
                writer.newLine();
                appendFile(writer, new File(directory, PREVIOUS_LOG));
                appendFile(writer, new File(directory, CURRENT_LOG));
            }
            return export;
        }
    }

    static void clearForTests(Context context) {
        synchronized (FILE_LOCK) {
            File[] files = directory(context).listFiles();
            if (files == null) {
                return;
            }
            for (File file : files) {
                if (!file.delete()) {
                    file.deleteOnExit();
                }
            }
        }
    }

    private static void rotateIfNeeded(File directory, File current) throws IOException {
        if (!current.isFile() || current.length() < MAX_LOG_BYTES) {
            return;
        }
        File previous = new File(directory, PREVIOUS_LOG);
        if (previous.exists() && !previous.delete()) {
            throw new IOException("Unable to replace previous diagnostics");
        }
        if (!current.renameTo(previous)) {
            throw new IOException("Unable to rotate diagnostics");
        }
    }

    private static void appendFile(BufferedWriter writer, File source) throws IOException {
        if (!source.isFile()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(source), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private static File directory(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), DIRECTORY);
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static String errorType(Throwable exception) {
        return exception == null ? "Unknown" : sanitize(exception.getClass().getSimpleName());
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        return safe.length() <= MAX_DETAILS_LENGTH
                ? safe
                : safe.substring(0, MAX_DETAILS_LENGTH);
    }
}

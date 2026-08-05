package com.jatrailmap.justanothertrailmap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class DiagnosticLogTest {
    private Context context;

    @Before
    public void clearDiagnostics() {
        context = RuntimeEnvironment.getApplication();
        DiagnosticLog.clearForTests(context);
    }

    @After
    public void cleanDiagnostics() {
        DiagnosticLog.clearForTests(context);
    }

    @Test
    public void exportContainsDeviceHeaderAndPersistedEvents() throws Exception {
        DiagnosticLog.event(context, "SERVICE", "CREATED", "session=7 points=12");
        DiagnosticLog.event(context, "LOCATION", "FIX_RECEIVED",
                "provider=gps hasAccuracy=true accuracyM=8");

        File export = DiagnosticLog.createExportFile(context);
        String contents = readText(export);

        assertTrue(contents.contains("Just Another Trail Map diagnostics"));
        assertTrue(contents.contains("Package: " + context.getPackageName()));
        assertTrue(contents.contains("SERVICE CREATED session=7 points=12"));
        assertTrue(contents.contains(
                "LOCATION FIX_RECEIVED provider=gps hasAccuracy=true accuracyM=8"));
        assertTrue(contents.contains("Coordinates, credentials, server URLs"));
    }

    @Test
    public void eventDetailsCannotInjectAdditionalLogLines() throws Exception {
        DiagnosticLog.event(context, "TEST", "SANITIZE", "first\nsecond\rthird\tfourth");

        String contents = readText(DiagnosticLog.createExportFile(context));

        assertTrue(contents.contains("TEST SANITIZE first second third fourth"));
        assertFalse(contents.contains("\nsecond"));
    }

    private static String readText(File file) throws Exception {
        StringBuilder contents = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                contents.append(line).append('\n');
            }
        }
        return contents.toString();
    }
}

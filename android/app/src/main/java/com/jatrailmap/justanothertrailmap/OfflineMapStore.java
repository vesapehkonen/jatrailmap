package com.jatrailmap.justanothertrailmap;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.map.reader.MapFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OfflineMapStore {
    public static final String PREFERENCES = "offline_map";
    public static final String CENTER_LATITUDE = "center_latitude";
    public static final String CENTER_LONGITUDE = "center_longitude";
    public static final String ZOOM = "zoom";

    private static final String SELECTED_MAP = "selected_map";
    private static final String SELECTION_INITIALIZED = "selection_initialized";
    private static final String AUTOMATIC_SELECTION = "automatic_selection";
    private static final String LEGACY_MAP_NAME = "selected.map";
    private static final Map<String, CachedBounds> BOUNDS_CACHE = new HashMap<>();

    private static final class CachedBounds {
        final long size;
        final long modified;
        final BoundingBox bounds;

        CachedBounds(File file, BoundingBox bounds) {
            this.size = file.length();
            this.modified = file.lastModified();
            this.bounds = bounds;
        }

        boolean matches(File file) {
            return size == file.length() && modified == file.lastModified();
        }
    }

    public static final class MapInfo {
        public final String fileName;
        public final long sizeBytes;
        public final boolean selected;

        private MapInfo(String fileName, long sizeBytes, boolean selected) {
            this.fileName = fileName;
            this.sizeBytes = sizeBytes;
            this.selected = selected;
        }
    }

    private OfflineMapStore() {
    }

    public static List<MapInfo> listMaps(Context context) {
        File selectedMap = getSelectedMap(context);
        String selectedName = selectedMap == null ? null : selectedMap.getName();
        File[] files = mapsDirectory(context).listFiles(file ->
                file.isFile() && file.getName().toLowerCase().endsWith(".map"));
        if (files == null) {
            return new ArrayList<>();
        }
        Arrays.sort(files, (left, right) ->
                String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName()));
        List<MapInfo> maps = new ArrayList<>(files.length);
        for (File file : files) {
            maps.add(new MapInfo(file.getName(), file.length(),
                    file.getName().equals(selectedName)));
        }
        return maps;
    }

    public static File getSelectedMap(Context context) {
        File directory = mapsDirectory(context);
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        String selectedName = preferences.getString(SELECTED_MAP, null);
        if (selectedName != null) {
            File selected = fileInDirectory(directory, selectedName);
            return selected.isFile() ? selected : null;
        }

        if (preferences.getBoolean(SELECTION_INITIALIZED, false)) {
            return null;
        }

        File legacyMap = new File(directory, LEGACY_MAP_NAME);
        if (legacyMap.isFile()) {
            preferences.edit()
                    .putString(SELECTED_MAP, LEGACY_MAP_NAME)
                    .putBoolean(SELECTION_INITIALIZED, true)
                    .apply();
            return legacyMap;
        }
        preferences.edit().putBoolean(SELECTION_INITIALIZED, true).apply();
        return null;
    }

    public static boolean isAutomaticSelectionEnabled(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(AUTOMATIC_SELECTION, true);
    }

    public static void setAutomaticSelectionEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(AUTOMATIC_SELECTION, enabled)
                .apply();
    }

    public static String findBestMapForLocation(Context context, double latitude,
                                                double longitude) {
        File selected = getSelectedMap(context);
        Map<String, BoundingBox> mapBounds = new HashMap<>();
        File[] maps = mapFiles(context);
        for (File map : maps) {
            BoundingBox bounds = bounds(map);
            if (bounds != null) {
                mapBounds.put(map.getName(), bounds);
            }
        }
        return selectBestMap(
                selected == null ? null : selected.getName(),
                mapBounds,
                latitude,
                longitude);
    }

    static String selectBestMap(String selectedName, Map<String, BoundingBox> mapBounds,
                                double latitude, double longitude) {
        BoundingBox selectedBounds = mapBounds.get(selectedName);
        if (selectedBounds != null && selectedBounds.contains(latitude, longitude)) {
            return selectedName;
        }

        String bestName = null;
        double bestArea = Double.MAX_VALUE;
        for (Map.Entry<String, BoundingBox> entry : mapBounds.entrySet()) {
            BoundingBox bounds = entry.getValue();
            if (!bounds.contains(latitude, longitude)) {
                continue;
            }
            double area = bounds.getLatitudeSpan() * bounds.getLongitudeSpan();
            if (area < bestArea || (area == bestArea
                    && (bestName == null
                    || entry.getKey().compareToIgnoreCase(bestName) < 0))) {
                bestArea = area;
                bestName = entry.getKey();
            }
        }
        return bestName;
    }

    public static Set<String> findMapsCovering(Context context, double latitude,
                                               double longitude) {
        Set<String> coveringMaps = new HashSet<>();
        for (File map : mapFiles(context)) {
            if (covers(map, latitude, longitude)) {
                coveringMaps.add(map.getName());
            }
        }
        return coveringMaps;
    }

    public static String importMap(Context context, Uri uri) throws IOException {
        File directory = mapsDirectory(context);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create offline map directory");
        }

        String displayName = sanitizeFileName(queryDisplayName(context, uri));
        File destination = uniqueDestination(directory, displayName);
        File temporary = new File(directory, destination.getName() + ".importing");
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) {
                    throw new IOException("Unable to open selected map");
                }
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }

            MapFile validation = new MapFile(temporary);
            validation.close();
            if (!temporary.renameTo(destination)) {
                throw new IOException("Unable to install selected map");
            }
            try {
                context.getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // The private copy remains usable even if the provider does not persist access.
            }
            selectMap(context, destination.getName());
            return destination.getName();
        } catch (RuntimeException exception) {
            throw new IOException("The selected file is not a valid Mapsforge map", exception);
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
    }

    public static void selectMap(Context context, String fileName) throws IOException {
        File map = fileInDirectory(mapsDirectory(context), fileName);
        if (!map.isFile()) {
            throw new IOException("Offline map does not exist");
        }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(SELECTED_MAP, map.getName())
                .putBoolean(SELECTION_INITIALIZED, true)
                .remove(CENTER_LATITUDE)
                .remove(CENTER_LONGITUDE)
                .remove(ZOOM)
                .apply();
    }

    public static void deleteMap(Context context, String fileName) throws IOException {
        File map = fileInDirectory(mapsDirectory(context), fileName);
        if (!map.isFile()) {
            return;
        }
        File selected = getSelectedMap(context);
        boolean deletingSelected = selected != null && selected.getName().equals(map.getName());
        if (!map.delete()) {
            throw new IOException("Unable to delete offline map");
        }
        synchronized (BOUNDS_CACHE) {
            BOUNDS_CACHE.remove(map.getAbsolutePath());
        }
        if (deletingSelected) {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                    .remove(SELECTED_MAP)
                    .putBoolean(SELECTION_INITIALIZED, true)
                    .remove(CENTER_LATITUDE)
                    .remove(CENTER_LONGITUDE)
                    .remove(ZOOM)
                    .apply();
        }
    }

    private static File mapsDirectory(Context context) {
        return new File(context.getFilesDir(), "maps");
    }

    private static File[] mapFiles(Context context) {
        File[] files = mapsDirectory(context).listFiles(file ->
                file.isFile() && file.getName().toLowerCase().endsWith(".map"));
        return files == null ? new File[0] : files;
    }

    private static boolean covers(File file, double latitude, double longitude) {
        BoundingBox bounds = bounds(file);
        return bounds != null && bounds.contains(latitude, longitude);
    }

    private static BoundingBox bounds(File file) {
        synchronized (BOUNDS_CACHE) {
            CachedBounds cached = BOUNDS_CACHE.get(file.getAbsolutePath());
            if (cached != null && cached.matches(file)) {
                return cached.bounds;
            }
        }

        MapFile mapFile = null;
        try {
            mapFile = new MapFile(file);
            BoundingBox bounds = mapFile.boundingBox();
            synchronized (BOUNDS_CACHE) {
                BOUNDS_CACHE.put(file.getAbsolutePath(), new CachedBounds(file, bounds));
            }
            return bounds;
        } catch (RuntimeException exception) {
            return null;
        } finally {
            if (mapFile != null) {
                mapFile.close();
            }
        }
    }

    private static File fileInDirectory(File directory, String fileName) {
        return new File(directory, new File(fileName).getName());
    }

    private static String queryDisplayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    return cursor.getString(column);
                }
            }
        }
        return "offline-map.map";
    }

    private static String sanitizeFileName(String displayName) {
        String safeName = displayName == null
                ? "offline-map.map"
                : displayName.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (safeName.isEmpty()) {
            safeName = "offline-map.map";
        }
        if (!safeName.toLowerCase().endsWith(".map")) {
            safeName += ".map";
        }
        if (safeName.length() > 120) {
            safeName = safeName.substring(0, 116) + ".map";
        }
        return safeName;
    }

    private static File uniqueDestination(File directory, String fileName) {
        File candidate = new File(directory, fileName);
        if (!candidate.exists()) {
            return candidate;
        }
        String baseName = fileName.substring(0, fileName.length() - 4);
        for (int suffix = 2; ; suffix++) {
            candidate = new File(directory, baseName + " (" + suffix + ").map");
            if (!candidate.exists()) {
                return candidate;
            }
        }
    }
}

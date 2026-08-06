package com.jatrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import android.app.Application;
import android.content.Context;
import android.net.Uri;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mapsforge.core.model.BoundingBox;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class OfflineMapStoreTest {
    private Context context;
    private File mapsDirectory;

    @Before
    public void prepareStore() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(OfflineMapStore.PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        mapsDirectory = new File(context.getFilesDir(), "maps");
        if (!mapsDirectory.isDirectory() && !mapsDirectory.mkdirs()) {
            throw new IllegalStateException("Unable to create test map directory");
        }
        deleteContents();
    }

    @After
    public void cleanStore() {
        deleteContents();
    }

    @Test
    public void mapsCanBeListedSelectedAndDeleted() throws Exception {
        createMapPlaceholder("zulu.map", 2);
        createMapPlaceholder("Alpha.map", 1);

        List<OfflineMapStore.MapInfo> initial = OfflineMapStore.listMaps(context);
        assertEquals(2, initial.size());
        assertEquals("Alpha.map", initial.get(0).fileName);
        assertEquals("zulu.map", initial.get(1).fileName);

        OfflineMapStore.selectMap(context, "zulu.map");
        assertEquals("zulu.map", OfflineMapStore.getSelectedMap(context).getName());
        assertTrue(OfflineMapStore.listMaps(context).get(1).selected);

        OfflineMapStore.deleteMap(context, "zulu.map");
        assertNull(OfflineMapStore.getSelectedMap(context));
        assertFalse(new File(mapsDirectory, "zulu.map").exists());
    }

    @Test
    public void importRejectsInvalidMapAndRemovesTemporaryCopy() throws Exception {
        File invalidMap = new File(context.getCacheDir(), "invalid.map");
        try (FileOutputStream output = new FileOutputStream(invalidMap)) {
            output.write(1);
        }

        assertThrows(IOException.class,
                () -> OfflineMapStore.importMap(context, Uri.fromFile(invalidMap)));

        assertEquals(0, OfflineMapStore.listMaps(context).size());
        File[] leftovers = mapsDirectory.listFiles();
        assertTrue(leftovers == null || leftovers.length == 0);
        if (!invalidMap.delete()) {
            invalidMap.deleteOnExit();
        }
    }

    @Test
    public void coverageKeepsSelectedMapWhenItCoversLocation() {
        Map<String, BoundingBox> maps = new HashMap<>();
        maps.put("selected.map", new BoundingBox(0, 0, 20, 20));
        maps.put("smaller.map", new BoundingBox(9, 9, 11, 11));

        assertEquals("selected.map",
                OfflineMapStore.selectBestMap("selected.map", maps, 10, 10));
    }

    @Test
    public void coverageChoosesSmallestMapThenUsesStableNameTieBreak() {
        Map<String, BoundingBox> maps = new HashMap<>();
        maps.put("world.map", new BoundingBox(-80, -170, 80, 170));
        maps.put("Beta.map", new BoundingBox(5, 5, 15, 15));
        maps.put("alpha.map", new BoundingBox(5, 5, 15, 15));
        maps.put("elsewhere.map", new BoundingBox(-20, -20, -10, -10));

        assertEquals("alpha.map",
                OfflineMapStore.selectBestMap(null, maps, 10, 10));
        assertNull(OfflineMapStore.selectBestMap(null, maps, 89, 179));
    }

    private void createMapPlaceholder(String name, int bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(mapsDirectory, name))) {
            for (int index = 0; index < bytes; index++) {
                output.write(index);
            }
        }
    }

    private void deleteContents() {
        File[] files = mapsDirectory.listFiles();
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

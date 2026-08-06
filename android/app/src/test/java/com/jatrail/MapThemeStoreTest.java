package com.jatrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class MapThemeStoreTest {
    private Context context;

    @Before
    public void clearSettings() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(MapThemeStore.PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void standardThemeIsTheSafeDefault() {
        assertEquals(MapThemeStore.Style.STANDARD, MapThemeStore.load(context));
        assertSame(MapsforgeThemes.DEFAULT,
                MapThemeStore.Style.STANDARD.renderTheme());
    }

    @Test
    public void selectedThemeSurvivesStoreRecreation() {
        MapThemeStore.save(context, MapThemeStore.Style.CYCLING);

        assertEquals(MapThemeStore.Style.CYCLING, MapThemeStore.load(context));
        assertSame(MapsforgeThemes.BIKER,
                MapThemeStore.load(context).renderTheme());
        assertEquals("cycling", MapThemeStore.load(context).cacheSuffix());
    }

    @Test
    public void unknownStoredThemeFallsBackToStandard() {
        context.getSharedPreferences(MapThemeStore.PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(MapThemeStore.KEY_MAP_STYLE, "removed-theme")
                .commit();

        assertEquals(MapThemeStore.Style.STANDARD, MapThemeStore.load(context));
    }
}

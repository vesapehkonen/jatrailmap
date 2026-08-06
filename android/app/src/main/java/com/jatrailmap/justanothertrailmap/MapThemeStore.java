package com.jatrailmap.justanothertrailmap;

import android.content.Context;

import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

public final class MapThemeStore {
    static final String PREFERENCES = "map_display_settings";
    static final String KEY_MAP_STYLE = "map_style";

    public enum Style {
        STANDARD("standard", MapsforgeThemes.DEFAULT),
        DETAILED("detailed", MapsforgeThemes.OSMARENDER),
        CYCLING("cycling", MapsforgeThemes.BIKER),
        ROAD("road", MapsforgeThemes.MOTORIDER);

        private final String key;
        private final XmlRenderTheme renderTheme;

        Style(String key, XmlRenderTheme renderTheme) {
            this.key = key;
            this.renderTheme = renderTheme;
        }

        public String cacheSuffix() {
            return key;
        }

        public XmlRenderTheme renderTheme() {
            return renderTheme;
        }

        static Style fromKey(String key) {
            for (Style style : values()) {
                if (style.key.equals(key)) {
                    return style;
                }
            }
            return STANDARD;
        }
    }

    private MapThemeStore() {
    }

    public static Style load(Context context) {
        String key = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_MAP_STYLE, Style.STANDARD.key);
        return Style.fromKey(key);
    }

    public static void save(Context context, Style style) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MAP_STYLE, style == null ? Style.STANDARD.key : style.key)
                .apply();
    }
}

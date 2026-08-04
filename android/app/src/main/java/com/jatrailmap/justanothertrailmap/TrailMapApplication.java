package com.jatrailmap.justanothertrailmap;

import android.app.Application;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

public final class TrailMapApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AndroidGraphicFactory.createInstance(this);
    }
}

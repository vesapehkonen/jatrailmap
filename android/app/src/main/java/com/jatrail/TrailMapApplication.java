package com.jatrail;

import android.app.Application;

import org.mapsforge.map.android.graphics.AndroidGraphicFactory;

public final class TrailMapApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AndroidGraphicFactory.createInstance(this);
        DiagnosticLog.event(this, "APP", "PROCESS_CREATED",
                "sdk=" + android.os.Build.VERSION.SDK_INT);
        TrailUploadReconciler.reconcile(this);
    }
}

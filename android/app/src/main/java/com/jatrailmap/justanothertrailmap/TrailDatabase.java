package com.jatrailmap.justanothertrailmap;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {TrailPointEntity.class, TrailPhotoEntity.class},
        version = 1,
        exportSchema = true)
public abstract class TrailDatabase extends RoomDatabase {
    private static volatile TrailDatabase instance;

    public abstract TrailDao trailDao();

    public static TrailDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (TrailDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TrailDatabase.class,
                            "trails.db")
                            .build();
                }
            }
        }
        return instance;
    }
}

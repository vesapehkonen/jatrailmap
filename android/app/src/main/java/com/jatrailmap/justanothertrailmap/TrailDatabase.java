package com.jatrailmap.justanothertrailmap;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {TrailEntity.class, TrailPointEntity.class, TrailPhotoEntity.class},
        version = 4,
        exportSchema = true)
public abstract class TrailDatabase extends RoomDatabase {
    private static volatile TrailDatabase instance;
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE trails ADD COLUMN name TEXT NOT NULL DEFAULT ''");
            database.execSQL(
                    "ALTER TABLE trails ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE trails ADD COLUMN uploadState TEXT NOT NULL "
                    + "DEFAULT 'LOCAL'");
            database.execSQL("ALTER TABLE trails ADD COLUMN uploadToken TEXT");
            database.execSQL("ALTER TABLE trails ADD COLUMN uploadError TEXT");
        }
    };
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE trails ADD COLUMN recordingState TEXT NOT NULL "
                    + "DEFAULT 'FINISHED'");
            database.execSQL("UPDATE trails SET name = 'Trail ' || substr(createdAt, 1, 10) "
                    + "|| ' ' || substr(createdAt, 12, 5) WHERE trim(name) = ''");
        }
    };

    public abstract TrailDao trailDao();

    public static TrailDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (TrailDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TrailDatabase.class,
                            "trails.db")
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigrationFrom(true, 1)
                            .build();
                }
            }
        }
        return instance;
    }
}

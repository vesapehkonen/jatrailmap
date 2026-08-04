package com.jatrailmap.justanothertrailmap;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public interface TrailDao {
    @Insert
    long insertPoint(TrailPointEntity point);

    @Insert
    long insertPhoto(TrailPhotoEntity photo);

    @Insert
    void insertPoints(List<TrailPointEntity> points);

    @Insert
    void insertPhotos(List<TrailPhotoEntity> photos);

    @Query("SELECT * FROM trail_points ORDER BY id")
    List<TrailPointEntity> getPoints();

    @Query("SELECT * FROM trail_points ORDER BY id DESC LIMIT 1")
    TrailPointEntity getLatestPoint();

    @Query("SELECT * FROM trail_points WHERE id > :afterId ORDER BY id")
    List<TrailPointEntity> getPointsAfter(long afterId);

    @Query("SELECT * FROM trail_photos ORDER BY id")
    List<TrailPhotoEntity> getPhotos();

    @Query("SELECT COUNT(*) FROM trail_points")
    int getPointCount();

    @Query("DELETE FROM trail_points")
    void deletePoints();

    @Query("DELETE FROM trail_photos")
    void deletePhotos();

    @Transaction
    default void replaceWithLegacyData(List<TrailPointEntity> points,
                                       List<TrailPhotoEntity> photos) {
        deletePoints();
        deletePhotos();
        if (!points.isEmpty()) {
            insertPoints(points);
        }
        if (!photos.isEmpty()) {
            insertPhotos(photos);
        }
    }

    @Transaction
    default void clearAll() {
        deletePoints();
        deletePhotos();
    }
}

package com.jatrailmap.justanothertrailmap;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TrailDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertTrail(TrailEntity trail);

    @Insert
    long insertPoint(TrailPointEntity point);

    @Insert
    long insertPhoto(TrailPhotoEntity photo);

    @Query("SELECT * FROM trails WHERE id = :trailId LIMIT 1")
    TrailEntity getTrail(long trailId);

    @Query("SELECT * FROM trails ORDER BY id DESC")
    List<TrailEntity> getTrails();

    @Query("SELECT * FROM trails WHERE uploadState IN ('QUEUED', 'UPLOADING')")
    List<TrailEntity> getPendingUploads();

    @Query("UPDATE trails SET name = :name WHERE id = :trailId")
    int renameTrail(long trailId, String name);

    @Query("UPDATE trails SET durationMs = :durationMs WHERE id = :trailId")
    int updateDuration(long trailId, long durationMs);

    @Query("UPDATE trails SET name = :name, durationMs = :durationMs, "
            + "recordingState = 'FINISHED' WHERE id = :trailId")
    int finishTrail(long trailId, String name, long durationMs);

    @Query("UPDATE trails SET recordingState = 'ACTIVE' WHERE id = :trailId")
    int markTrailActive(long trailId);

    @Query("UPDATE trails SET uploadState = 'QUEUED', uploadToken = :token, uploadError = NULL "
            + "WHERE id = :trailId "
            + "AND recordingState = 'FINISHED' "
            + "AND uploadState NOT IN ('QUEUED', 'UPLOADING', 'UPLOADED')")
    int markUploadQueued(long trailId, String token);

    @Query("UPDATE trails SET uploadState = 'UPLOADING', uploadError = NULL "
            + "WHERE id = :trailId AND uploadToken = :token "
            + "AND uploadState IN ('QUEUED', 'UPLOADING')")
    int markUploadInProgress(long trailId, String token);

    @Query("UPDATE trails SET uploadState = 'UPLOADED', uploadToken = NULL, uploadError = NULL "
            + "WHERE id = :trailId AND uploadToken = :token")
    int markUploadSucceeded(long trailId, String token);

    @Query("UPDATE trails SET uploadState = 'FAILED', uploadToken = NULL, "
            + "uploadError = :error WHERE id = :trailId AND uploadToken = :token")
    int markUploadFailed(long trailId, String token, String error);

    @Query("UPDATE trails SET uploadState = 'FAILED', uploadToken = NULL, "
            + "uploadError = :error WHERE id = :trailId "
            + "AND uploadState IN ('QUEUED', 'UPLOADING')")
    int markPendingUploadInterrupted(long trailId, String error);

    @Query("SELECT * FROM trail_points WHERE trailId = :trailId ORDER BY id")
    List<TrailPointEntity> getPoints(long trailId);

    @Query("SELECT * FROM trail_points WHERE trailId = :trailId ORDER BY id DESC LIMIT 1")
    TrailPointEntity getLatestPoint(long trailId);

    @Query("SELECT * FROM trail_points WHERE trailId = :trailId AND id > :afterId ORDER BY id")
    List<TrailPointEntity> getPointsAfter(long trailId, long afterId);

    @Query("SELECT * FROM trail_photos WHERE trailId = :trailId ORDER BY id")
    List<TrailPhotoEntity> getPhotos(long trailId);

    @Query("SELECT COUNT(*) FROM trail_points WHERE trailId = :trailId")
    int getPointCount(long trailId);

    @Query("DELETE FROM trails WHERE id = :trailId")
    void deleteTrail(long trailId);
}

package com.jatrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;

import androidx.room.Room;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = Application.class)
public class TrailDaoIntegrationTest {
    private TrailDatabase database;
    private TrailDao dao;

    @Before
    public void createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
                        RuntimeEnvironment.getApplication(), TrailDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.trailDao();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void pointsRestoreInRecordingOrderAndCanResumeAfterLatestId() {
        dao.insertTrail(new TrailEntity(10, "created"));
        long firstId = dao.insertPoint(point(10, "first", 1));
        long secondId = dao.insertPoint(point(10, "second", 2));
        long thirdId = dao.insertPoint(point(10, "third", 3));

        List<TrailPointEntity> restored = dao.getPoints(10);
        assertEquals(3, restored.size());
        assertEquals(firstId, restored.get(0).id);
        assertEquals(secondId, restored.get(1).id);
        assertEquals(thirdId, restored.get(2).id);
        assertEquals(thirdId, dao.getLatestPoint(10).id);

        List<TrailPointEntity> resumed = dao.getPointsAfter(10, firstId);
        assertEquals(2, resumed.size());
        assertEquals(secondId, resumed.get(0).id);
        assertEquals(thirdId, resumed.get(1).id);
    }

    @Test
    public void deletingOneTrailCascadesOnlyItsPointsAndPhotos() {
        dao.insertTrail(new TrailEntity(10, "first trail"));
        dao.insertTrail(new TrailEntity(20, "second trail"));
        dao.insertPoint(point(10, "first point", 1));
        dao.insertPhoto(new TrailPhotoEntity(
                10, "/photo.jpg", "photo", 1, 2, 3));
        dao.insertPoint(point(20, "second point", 2));

        dao.deleteTrail(10);

        assertNull(dao.getTrail(10));
        assertEquals(0, dao.getPointCount(10));
        assertEquals(0, dao.getPhotos(10).size());
        assertEquals(1, dao.getPointCount(20));
        assertEquals(1, dao.getTrails().size());
        assertEquals(20, dao.getTrails().get(0).id);
    }

    @Test
    public void uploadStateMachineRejectsDuplicateAndStaleWorkers() {
        dao.insertTrail(new TrailEntity(10, "created"));
        dao.finishTrail(10, "Trail created", 0);

        assertEquals(1, dao.markUploadQueued(10, "token-one"));
        assertEquals(0, dao.markUploadQueued(10, "token-two"));
        TrailEntity queued = dao.getTrail(10);
        assertEquals(TrailEntity.UPLOAD_QUEUED, queued.uploadState);
        assertEquals("token-one", queued.uploadToken);

        assertEquals(0, dao.markUploadInProgress(10, "token-two"));
        assertEquals(1, dao.markUploadInProgress(10, "token-one"));
        assertEquals(TrailEntity.UPLOAD_UPLOADING, dao.getTrail(10).uploadState);

        assertEquals(0, dao.markUploadSucceeded(10, "token-two"));
        assertEquals(1, dao.markUploadSucceeded(10, "token-one"));
        TrailEntity uploaded = dao.getTrail(10);
        assertEquals(TrailEntity.UPLOAD_UPLOADED, uploaded.uploadState);
        assertNull(uploaded.uploadToken);

        assertEquals(0, dao.markUploadQueued(10, "token-three"));

        dao.insertTrail(new TrailEntity(20, "created"));
        dao.finishTrail(20, "Trail created", 0);
        assertEquals(1, dao.markUploadQueued(20, "token-three"));
        assertEquals(1, dao.markUploadFailed(20, "token-three", "Network error"));
        TrailEntity failed = dao.getTrail(20);
        assertEquals(TrailEntity.UPLOAD_FAILED, failed.uploadState);
        assertEquals("Network error", failed.uploadError);

        dao.insertTrail(new TrailEntity(30, "created"));
        dao.finishTrail(30, "Trail created", 0);
        assertEquals(1, dao.markUploadQueued(30, "orphaned-token"));
        assertEquals(1, dao.getPendingUploads().size());
        assertEquals(1, dao.markPendingUploadInterrupted(30, "Interrupted"));
        assertEquals(TrailEntity.UPLOAD_FAILED, dao.getTrail(30).uploadState);
        assertNull(dao.getTrail(30).uploadToken);
    }

    @Test
    public void trailMetadataCanBeRenamedAndDurationUpdatedIndependently() {
        dao.insertTrail(new TrailEntity(10, "first"));
        dao.insertTrail(new TrailEntity(20, "second"));

        assertEquals(1, dao.renameTrail(10, "Morning hike"));
        assertEquals(1, dao.updateDuration(10, 123456));

        assertEquals("Morning hike", dao.getTrail(10).name);
        assertEquals(123456, dao.getTrail(10).durationMs);
        assertEquals("Trail second", dao.getTrail(20).name);
        assertEquals(0, dao.getTrail(20).durationMs);
    }

    @Test
    public void trailMustBeFinishedBeforeUploadAndFinishStoresNameAndDuration() {
        dao.insertTrail(new TrailEntity(10, "2026-08-04T15:30:00Z"));

        TrailEntity active = dao.getTrail(10);
        assertEquals("Trail 2026-08-04 15:30", active.name);
        assertEquals(TrailEntity.RECORDING_ACTIVE, active.recordingState);
        assertEquals(0, dao.markUploadQueued(10, "too-early"));

        assertEquals(1, dao.finishTrail(10, "Afternoon hike", 4567));
        TrailEntity finished = dao.getTrail(10);
        assertEquals("Afternoon hike", finished.name);
        assertEquals(4567, finished.durationMs);
        assertEquals(TrailEntity.RECORDING_FINISHED, finished.recordingState);
        assertEquals(1, dao.markUploadQueued(10, "ready"));
    }

    private static TrailPointEntity point(long trailId, String timestamp, double coordinate) {
        return new TrailPointEntity(
                trailId, timestamp, coordinate, coordinate, coordinate);
    }
}

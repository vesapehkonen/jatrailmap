package com.jatrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Collections;

public class TrailUploadJsonCompatibilityTest {
    @Test
    public void newApiResponseFieldsDeserialize() {
        TrailUploadModels.UploadResponse success = new Gson().fromJson(
                "{\"status\":\"ok\",\"message\":\"Uploaded\",\"trailid\":\"abc123\"}",
                TrailUploadModels.UploadResponse.class);
        TrailUploadModels.UploadResponse error = new Gson().fromJson(
                "{\"status\":\"error\",\"error_code\":\"photo_too_large\"," +
                        "\"message\":\"Too large\",\"details\":{\"limit_bytes\":2097152}}",
                TrailUploadModels.UploadResponse.class);

        assertTrue(success.isSuccessful());
        assertEquals("abc123", success.trailid);
        assertFalse(error.isSuccessful());
        assertEquals("photo_too_large", error.errorCode);
        assertEquals(2097152, error.details.get("limit_bytes").getAsInt());
    }

    @Test
    public void retryPolicyUsesNewApiStatusAndErrorCode() {
        assertTrue(TrailUploadWorker.shouldRetry(503, "storage_failure"));
        assertTrue(TrailUploadWorker.shouldRetry(429, null));
        assertFalse(TrailUploadWorker.shouldRetry(503, "account_storage_exceeded"));
        assertFalse(TrailUploadWorker.shouldRetry(422, "invalid_payload"));
    }

    @Test
    public void uploadCallHasTenMinuteOverallTimeout() {
        assertEquals(10, TrailUploadWorker.CALL_TIMEOUT_MINUTES);
    }

    @Test
    public void roomRecordsSerializeToLegacyServerStructure() {
        TrailPointEntity point = new TrailPointEntity(
                42, "2026-08-03T12:01:00-07:00", -122.1, 37.4, 150.0);
        TrailPhotoEntity photo = new TrailPhotoEntity(
                42,
                "/photos/img.jpg",
                "2026-08-03T12:02:00-07:00",
                -122.2,
                37.5,
                151.0);

        TrailUploadModels.UploadRequest request = TrailUploadRequestFactory.create(
                "2026-08-03T12:00:00-07:00",
                "Example trail",
                "California",
                "Hike",
                "user",
                "password",
                Collections.singletonList(point),
                Collections.singletonList(
                        new TrailUploadModels.PictureUpload(photo, "img.jpg")),
                true);

        String actual = new Gson().toJson(request);
        String expected = "{\"newtrail\":[" +
                "{\"type\":\"TrailInfo\",\"access\":\"public\"," +
                "\"date\":\"2026-08-03T12:00:00-07:00\"," +
                "\"trailname\":\"Example trail\",\"locationname\":\"California\"," +
                "\"description\":\"Hike\"}," +
                "{\"type\":\"UserInfo\",\"username\":\"user\"," +
                "\"password\":\"password\"}," +
                "{\"type\":\"LocationCollection\",\"locations\":[{" +
                "\"timestamp\":\"2026-08-03T12:01:00-07:00\"," +
                "\"loc\":{\"type\":\"Point\",\"coordinates\":[-122.1,37.4,150.0]}}]}," +
                "{\"type\":\"PictureCollection\",\"pictures\":[{" +
                "\"timestamp\":\"2026-08-03T12:02:00-07:00\"," +
                "\"filename\":\"img.jpg\",\"picturename\":\"\"," +
                "\"description\":\"\",\"loc\":{\"type\":\"Point\"," +
                "\"coordinates\":[-122.2,37.5,151.0]}}]}]}";

        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual));
    }
}

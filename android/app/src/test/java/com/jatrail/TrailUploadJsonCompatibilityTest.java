package com.jatrail;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.Collections;

public class TrailUploadJsonCompatibilityTest {
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
                        new TrailUploadModels.PictureUpload(photo, "aW1hZ2U=")),
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
                "\"filename\":\"/photos/img.jpg\",\"picturename\":\"\"," +
                "\"description\":\"\",\"loc\":{\"type\":\"Point\"," +
                "\"coordinates\":[-122.2,37.5,151.0]},\"file\":\"aW1hZ2U=\"}]}]}";

        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual));
    }
}

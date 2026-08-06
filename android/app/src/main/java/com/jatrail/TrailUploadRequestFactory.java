package com.jatrail;

import java.util.ArrayList;
import java.util.List;

public final class TrailUploadRequestFactory {
    private TrailUploadRequestFactory() {}

    public static TrailUploadModels.UploadRequest create(
            String date,
            String trailName,
            String locationName,
            String description,
            String username,
            String password,
            List<TrailPointEntity> points,
            List<TrailUploadModels.PictureUpload> pictures,
            boolean includePictureCollection) {
        List<Object> entries = new ArrayList<>();
        entries.add(new TrailUploadModels.TrailInfo(
                date, trailName, locationName, description));
        entries.add(new TrailUploadModels.UserInfo(username, password));

        List<TrailUploadModels.LocationRecord> locations = new ArrayList<>();
        for (TrailPointEntity point : points) {
            locations.add(new TrailUploadModels.LocationRecord(
                    point.timestamp,
                    new TrailUploadModels.GeoPoint(
                            point.longitude, point.latitude, point.altitude)));
        }
        entries.add(new TrailUploadModels.LocationCollection(locations));
        if (includePictureCollection) {
            entries.add(new TrailUploadModels.PictureCollection(pictures));
        }
        return new TrailUploadModels.UploadRequest(entries);
    }
}

package com.jatrail;

import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Arrays;

public final class TrailUploadModels {
    private TrailUploadModels() {}

    public static final class UploadRequest {
        @SerializedName("newtrail")
        final List<Object> entries;

        UploadRequest(List<Object> entries) {
            this.entries = entries;
        }
    }

    public static final class TrailInfo {
        final String type = "TrailInfo";
        final String access = "public";
        final String date;
        final String trailname;
        final String locationname;
        final String description;

        TrailInfo(String date, String trailname, String locationname, String description) {
            this.date = date;
            this.trailname = trailname;
            this.locationname = locationname;
            this.description = description;
        }
    }

    public static final class UserInfo {
        final String type = "UserInfo";
        final String username;
        final String password;

        UserInfo(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    public static final class LocationCollection {
        final String type = "LocationCollection";
        final List<LocationRecord> locations;

        LocationCollection(List<LocationRecord> locations) {
            this.locations = locations;
        }
    }

    public static final class LocationRecord {
        final String timestamp;
        final GeoPoint loc;

        LocationRecord(String timestamp, GeoPoint loc) {
            this.timestamp = timestamp;
            this.loc = loc;
        }
    }

    public static final class GeoPoint {
        String type;
        List<Double> coordinates;

        GeoPoint(double longitude, double latitude, double altitude) {
            type = "Point";
            coordinates = Arrays.asList(longitude, latitude, altitude);
        }
    }

    public static final class PictureMetadata {
        String imagepath;
        String timestamp;
        GeoPoint loc;
    }

    public static final class PictureCollection {
        final String type = "PictureCollection";
        final List<PictureUpload> pictures;

        PictureCollection(List<PictureUpload> pictures) {
            this.pictures = pictures;
        }
    }

    public static final class PictureUpload {
        final String timestamp;
        final String filename;
        final String picturename = "";
        final String description = "";
        final GeoPoint loc;

        PictureUpload(PictureMetadata metadata) {
            timestamp = metadata.timestamp;
            filename = metadata.imagepath;
            loc = metadata.loc;
        }

        PictureUpload(TrailPhotoEntity photo, String uploadFilename) {
            timestamp = photo.timestamp;
            filename = uploadFilename;
            loc = new GeoPoint(photo.longitude, photo.latitude, photo.altitude);
        }
    }

    public static final class UploadResponse {
        public String status;
        public String message;
        public String trailid;

        @SerializedName("error_code")
        public String errorCode;

        public JsonObject details;

        public boolean isSuccessful() {
            return "ok".equals(status);
        }
    }
}

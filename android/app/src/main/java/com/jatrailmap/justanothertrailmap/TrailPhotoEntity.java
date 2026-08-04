package com.jatrailmap.justanothertrailmap;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trail_photos")
public class TrailPhotoEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String imagePath;
    public String timestamp;
    public double longitude;
    public double latitude;
    public double altitude;

    public TrailPhotoEntity(String imagePath, String timestamp, double longitude,
                            double latitude, double altitude) {
        this.imagePath = imagePath;
        this.timestamp = timestamp;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
    }
}

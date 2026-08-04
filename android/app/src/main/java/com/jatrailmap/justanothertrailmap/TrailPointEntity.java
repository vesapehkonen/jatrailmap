package com.jatrailmap.justanothertrailmap;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trail_points")
public class TrailPointEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String timestamp;
    public double longitude;
    public double latitude;
    public double altitude;

    public TrailPointEntity(String timestamp, double longitude, double latitude, double altitude) {
        this.timestamp = timestamp;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
    }
}

package com.jatrail;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "trail_points",
        foreignKeys = @ForeignKey(
                entity = TrailEntity.class,
                parentColumns = "id",
                childColumns = "trailId",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("trailId"))
public class TrailPointEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long trailId;
    public String timestamp;
    public double longitude;
    public double latitude;
    public double altitude;

    public TrailPointEntity(long trailId, String timestamp, double longitude, double latitude,
                            double altitude) {
        this.trailId = trailId;
        this.timestamp = timestamp;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
    }
}

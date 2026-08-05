package com.jatrailmap.justanothertrailmap;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trails")
public class TrailEntity {
    public static final String RECORDING_ACTIVE = "ACTIVE";
    public static final String RECORDING_FINISHED = "FINISHED";
    public static final String UPLOAD_LOCAL = "LOCAL";
    public static final String UPLOAD_QUEUED = "QUEUED";
    public static final String UPLOAD_UPLOADING = "UPLOADING";
    public static final String UPLOAD_FAILED = "FAILED";
    public static final String UPLOAD_UPLOADED = "UPLOADED";

    @PrimaryKey
    public long id;
    @NonNull
    public String createdAt;
    @NonNull
    public String name;
    public long durationMs;
    @NonNull
    public String recordingState;
    @NonNull
    public String uploadState;
    public String uploadToken;
    public String uploadError;

    public TrailEntity(long id, @NonNull String createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.name = TrailNames.defaultName(createdAt);
        this.durationMs = 0;
        this.recordingState = RECORDING_ACTIVE;
        this.uploadState = UPLOAD_LOCAL;
    }
}

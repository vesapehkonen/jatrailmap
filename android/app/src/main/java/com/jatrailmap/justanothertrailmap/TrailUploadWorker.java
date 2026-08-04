package com.jatrailmap.justanothertrailmap;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class TrailUploadWorker extends Worker {
    public static final String UNIQUE_WORK_NAME = "trail-upload";
    public static final String KEY_URL = "url";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_TRAIL_NAME = "trailName";
    public static final String KEY_LOCATION_NAME = "locationName";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_LOCATIONS_FILENAME = "locationsFilename";
    public static final String KEY_PICTURES_FILENAME = "picturesFilename";
    public static final String KEY_ERROR = "error";

    private static final int MAX_ATTEMPTS = 8;

    private final Gson gson = new Gson();

    public TrailUploadWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        RecordingStateStore recordingStateStore = new RecordingStateStore(getApplicationContext());
        recordingStateStore.markUploading();
        try {
            String url = requireInput(KEY_URL);
            validateHttpsUrl(url);
            TrailUploadModels.UploadRequest request = buildRequest();
            TrailApi api = new Retrofit.Builder()
                    .baseUrl("https://localhost/")
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
                    .create(TrailApi.class);

            Response<TrailUploadModels.UploadResponse> response =
                    api.uploadTrail(url, request).execute();
            if (!response.isSuccessful()) {
                if (response.code() == 408 || response.code() == 429 || response.code() >= 500) {
                    return retryOrFail("Server returned HTTP " + response.code());
                }
                return failure("Server returned HTTP " + response.code());
            }

            TrailUploadModels.UploadResponse body = response.body();
            if (body == null) {
                return retryOrFail("Server returned an empty response");
            }
            if (!body.isSuccessful()) {
                return failure(body.msg == null ? "Server rejected the upload" : body.msg);
            }

            deleteUploadedMetadata();
            recordingStateStore.reset();
            return Result.success();
        } catch (IllegalArgumentException exception) {
            return failure(exception.getMessage());
        } catch (IOException exception) {
            return retryOrFail(exception.getMessage() == null
                    ? "Network or file error" : exception.getMessage());
        } catch (RuntimeException exception) {
            return failure(exception.getMessage() == null
                    ? "Unable to prepare upload" : exception.getMessage());
        }
    }

    private TrailUploadModels.UploadRequest buildRequest() throws IOException {
        List<Object> entries = new ArrayList<>();
        entries.add(new TrailUploadModels.TrailInfo(
                Iso8061DateTime.get(),
                requireInput(KEY_TRAIL_NAME),
                value(KEY_LOCATION_NAME),
                value(KEY_DESCRIPTION)));
        entries.add(new TrailUploadModels.UserInfo(
                requireInput(KEY_USERNAME), requireInput(KEY_PASSWORD)));

        File locationsFile = appFile(requireInput(KEY_LOCATIONS_FILENAME));
        if (!locationsFile.exists() || locationsFile.length() == 0) {
            throw new IllegalArgumentException("There is no location data to upload");
        }
        String locationsJson = "[" + readText(locationsFile) + "]";
        Type locationListType = new TypeToken<List<TrailUploadModels.LocationRecord>>() {}.getType();
        List<TrailUploadModels.LocationRecord> locations =
                gson.fromJson(locationsJson, locationListType);
        entries.add(new TrailUploadModels.LocationCollection(locations));

        String picturesFilename = value(KEY_PICTURES_FILENAME);
        File picturesFile = appFile(picturesFilename);
        if (picturesFile.exists()) {
            List<TrailUploadModels.PictureUpload> pictures = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(picturesFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    TrailUploadModels.PictureMetadata metadata =
                            gson.fromJson(line, TrailUploadModels.PictureMetadata.class);
                    File image = new File(metadata.imagepath);
                    if (image.exists()) {
                        pictures.add(new TrailUploadModels.PictureUpload(
                                metadata, encodeImage(image)));
                    }
                }
            }
            entries.add(new TrailUploadModels.PictureCollection(pictures));
        }
        return new TrailUploadModels.UploadRequest(entries);
    }

    private String encodeImage(File image) throws IOException {
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(image);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
        }
    }

    private String readText(File file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
        }
        return text.toString();
    }

    private void validateHttpsUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Server URL is invalid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Server URL must be a valid HTTPS URL");
        }
    }

    private String requireInput(String key) {
        String result = value(key);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Required upload information is missing");
        }
        return result;
    }

    private String value(String key) {
        String result = getInputData().getString(key);
        return result == null ? "" : result;
    }

    private File appFile(String filename) {
        return new File(getApplicationContext().getExternalFilesDir(null), filename);
    }

    private void deleteUploadedMetadata() {
        appFile(value(KEY_LOCATIONS_FILENAME)).delete();
        File pictures = appFile(value(KEY_PICTURES_FILENAME));
        if (pictures.exists()) {
            pictures.delete();
        }
    }

    private Result retryOrFail(String message) {
        return getRunAttemptCount() + 1 >= MAX_ATTEMPTS ? failure(message) : Result.retry();
    }

    private Result failure(String message) {
        new RecordingStateStore(getApplicationContext()).uploadFailed();
        return Result.failure(new Data.Builder().putString(KEY_ERROR, message).build());
    }
}

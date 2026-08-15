package com.jatrail;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class TrailUploadWorker extends Worker {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType JPEG = MediaType.get("image/jpeg");
    private static final String UNIQUE_WORK_PREFIX = "trail-upload-";
    public static final String KEY_URL = "url";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_TRAIL_NAME = "trailName";
    public static final String KEY_LOCATION_NAME = "locationName";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_ERROR = "error";
    public static final String KEY_TRAIL_ID = "trailId";
    public static final String KEY_UPLOAD_TOKEN = "uploadToken";

    private static final int MAX_ATTEMPTS = 8;
    static final int CALL_TIMEOUT_MINUTES = 10;

    private final Gson gson = new Gson();
    private final TrailRepository trailRepository;

    public TrailUploadWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
        trailRepository = new TrailRepository(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        long trailId = getInputData().getLong(KEY_TRAIL_ID, 0);
        String uploadToken = value(KEY_UPLOAD_TOKEN);
        if (trailId <= 0 || uploadToken.isEmpty()) {
            return Result.failure(new Data.Builder()
                    .putString(KEY_ERROR, "Upload target is missing")
                    .build());
        }
        try {
            if (!trailRepository.markUploadInProgress(trailId, uploadToken)) {
                return Result.failure(new Data.Builder()
                        .putString(KEY_ERROR, "This upload is no longer active")
                        .build());
            }
        } catch (IOException exception) {
            return Result.retry();
        }
        DiagnosticLog.event(getApplicationContext(), "UPLOAD", "WORK_STARTED",
                "attempt=" + (getRunAttemptCount() + 1)
                        + " trail=" + trailId);
        try {
            String url = requireInput(KEY_URL);
            validateServerUrl(url);
            PreparedUpload upload = buildRequest(trailId);
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .callTimeout(CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build();
            TrailApi api = new Retrofit.Builder()
                    .baseUrl("https://localhost/")
                    .client(httpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
                    .create(TrailApi.class);

            Response<TrailUploadModels.UploadResponse> response =
                    api.uploadTrail(
                            url,
                            RequestBody.create(gson.toJson(upload.manifest), JSON),
                            upload.photos).execute();
            DiagnosticLog.event(getApplicationContext(), "UPLOAD", "HTTP_RESPONSE",
                    "code=" + response.code());
            if (!response.isSuccessful()) {
                TrailUploadModels.UploadResponse error = parseError(response.errorBody());
                String errorCode = error == null ? null : error.errorCode;
                String message = error == null || error.message == null
                        ? "Server returned HTTP " + response.code()
                        : error.message;
                DiagnosticLog.event(getApplicationContext(), "UPLOAD", "API_ERROR",
                        "code=" + response.code()
                                + " errorCode=" + (errorCode == null ? "unknown" : errorCode));
                if (shouldRetry(response.code(), errorCode)) {
                    return retryOrFail(
                            message, trailId, uploadToken);
                }
                return failure(message, trailId, uploadToken);
            }

            TrailUploadModels.UploadResponse body = response.body();
            if (body == null) {
                return retryOrFail("Server returned an empty response", trailId, uploadToken);
            }
            if (!body.isSuccessful()) {
                return failure(body.message == null
                                ? "Server rejected the upload" : body.message,
                        trailId, uploadToken);
            }

            boolean uploadMarkedSuccessful;
            try {
                uploadMarkedSuccessful =
                        trailRepository.markUploadSucceeded(trailId, uploadToken);
            } catch (IOException exception) {
                DiagnosticLog.error(
                        getApplicationContext(), "UPLOAD", "SUCCESS_STATE_WRITE_FAILED", exception);
                return Result.failure(new Data.Builder()
                        .putString(KEY_ERROR, "Upload succeeded but local status could not be saved")
                        .build());
            }
            if (!uploadMarkedSuccessful) {
                return Result.failure(new Data.Builder()
                        .putString(KEY_ERROR, "This upload is no longer active")
                        .build());
            }
            DiagnosticLog.event(getApplicationContext(), "UPLOAD", "SUCCEEDED");
            return Result.success();
        } catch (IllegalArgumentException exception) {
            DiagnosticLog.error(getApplicationContext(), "UPLOAD", "INPUT_FAILED", exception);
            return failure(exception.getMessage(), trailId, uploadToken);
        } catch (IOException exception) {
            DiagnosticLog.error(getApplicationContext(), "UPLOAD", "IO_FAILED", exception);
            return retryOrFail(exception.getMessage() == null
                    ? "Network or file error" : exception.getMessage(), trailId, uploadToken);
        } catch (RuntimeException exception) {
            DiagnosticLog.error(getApplicationContext(), "UPLOAD", "PREPARATION_FAILED", exception);
            return failure(exception.getMessage() == null
                    ? "Unable to prepare upload" : exception.getMessage(), trailId, uploadToken);
        }
    }

    private PreparedUpload buildRequest(long trailId) throws IOException {
        trailRepository.awaitPendingWrites();
        List<TrailPointEntity> pointEntities = trailRepository.getPoints(trailId);
        if (pointEntities.isEmpty()) {
            throw new IllegalArgumentException("There is no location data to upload");
        }
        List<TrailPhotoEntity> photoEntities = trailRepository.getPhotos(trailId);
        List<TrailUploadModels.PictureUpload> pictures = new ArrayList<>();
        List<MultipartBody.Part> photoParts = new ArrayList<>();
        for (TrailPhotoEntity photo : photoEntities) {
            File image = new File(photo.imagePath);
            if (image.exists()) {
                TrailPhotoProcessor.ProcessedPhoto processed =
                        TrailPhotoProcessor.process(image);
                pictures.add(new TrailUploadModels.PictureUpload(photo, processed.filename));
                photoParts.add(MultipartBody.Part.createFormData(
                        "photos",
                        processed.filename,
                        RequestBody.create(processed.jpegBytes, JPEG)));
                DiagnosticLog.event(getApplicationContext(), "UPLOAD", "PHOTO_PREPARED",
                        "width=" + processed.width + " height=" + processed.height
                                + " bytes=" + processed.jpegBytes.length);
            }
        }
        DiagnosticLog.event(getApplicationContext(), "UPLOAD", "REQUEST_PREPARED",
                "points=" + pointEntities.size()
                        + " photosStored=" + photoEntities.size()
                        + " photosAttached=" + pictures.size());
        TrailUploadModels.UploadRequest manifest = TrailUploadRequestFactory.create(
                Iso8061DateTime.get(),
                requireInput(KEY_TRAIL_NAME),
                value(KEY_LOCATION_NAME),
                value(KEY_DESCRIPTION),
                requireInput(KEY_USERNAME),
                requireInput(KEY_PASSWORD),
                pointEntities,
                pictures,
                !pictures.isEmpty());
        return new PreparedUpload(manifest, photoParts);
    }

    private static final class PreparedUpload {
        final TrailUploadModels.UploadRequest manifest;
        final List<MultipartBody.Part> photos;

        PreparedUpload(TrailUploadModels.UploadRequest manifest,
                       List<MultipartBody.Part> photos) {
            this.manifest = manifest;
            this.photos = photos;
        }
    }

    private void validateServerUrl(String url) {
        if (!TrailServerUrl.isValid(url)) {
            throw new IllegalArgumentException("Server URL must use HTTP or HTTPS");
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

    private TrailUploadModels.UploadResponse parseError(ResponseBody errorBody) {
        if (errorBody == null) {
            return null;
        }
        try {
            return gson.fromJson(errorBody.charStream(), TrailUploadModels.UploadResponse.class);
        } catch (RuntimeException exception) {
            DiagnosticLog.error(getApplicationContext(), "UPLOAD", "ERROR_RESPONSE_INVALID",
                    exception);
            return null;
        }
    }

    static boolean shouldRetry(int httpStatus, String errorCode) {
        return httpStatus == 408
                || httpStatus == 429
                || (httpStatus == 503 && "storage_failure".equals(errorCode));
    }

    private Result retryOrFail(String message, long trailId, String uploadToken) {
        int attempt = getRunAttemptCount() + 1;
        if (attempt >= MAX_ATTEMPTS) {
            DiagnosticLog.event(getApplicationContext(), "UPLOAD", "RETRIES_EXHAUSTED",
                    "attempt=" + attempt);
            return failure(message, trailId, uploadToken);
        }
        DiagnosticLog.event(getApplicationContext(), "UPLOAD", "RETRY_SCHEDULED",
                "attempt=" + attempt + " nextAttempt=" + (attempt + 1));
        return Result.retry();
    }

    private Result failure(String message, long trailId, String uploadToken) {
        trailRepository.markUploadFailed(trailId, uploadToken, safeError(message));
        DiagnosticLog.event(getApplicationContext(), "UPLOAD", "FAILED",
                "attempt=" + (getRunAttemptCount() + 1));
        return Result.failure(new Data.Builder().putString(KEY_ERROR, message).build());
    }

    public static String uniqueWorkName(long trailId) {
        return UNIQUE_WORK_PREFIX + trailId;
    }

    private String safeError(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Upload failed";
        }
        String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= 200 ? singleLine : singleLine.substring(0, 200);
    }
}

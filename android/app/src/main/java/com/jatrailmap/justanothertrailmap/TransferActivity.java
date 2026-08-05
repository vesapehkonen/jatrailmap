package com.jatrailmap.justanothertrailmap;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TransferActivity extends AppCompatActivity {
    public static final String EXTRA_TRAIL_ID = "trailId";
    public static final String EXTRA_POINT_COUNT = "pointCount";
    public static final String EXTRA_TRAIL_NAME = "trailName";
    private static final ExecutorService UPLOAD_QUEUE_EXECUTOR =
            Executors.newSingleThreadExecutor();

    private long trailId;
    private int pointCount;
    private TrailRepository trailRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);
        trailId = getIntent().getLongExtra(EXTRA_TRAIL_ID, 0);
        pointCount = getIntent().getIntExtra(EXTRA_POINT_COUNT, 0);
        trailRepository = new TrailRepository(this);
        fillForm();
        String selectedTrailName = getIntent().getStringExtra(EXTRA_TRAIL_NAME);
        if (selectedTrailName != null && !selectedTrailName.trim().isEmpty()) {
            setText(R.id.edit_trailname, selectedTrailName);
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
    }

    public void onClick(View view) {
        if (view.getId() == R.id.button_send) {
            enqueueUpload();
        } else if (view.getId() == R.id.button_cancel) {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void enqueueUpload() {
        String url = text(R.id.edit_server_url);
        String username = text(R.id.edit_username);
        String password = text(R.id.edit_password);
        String trailName = text(R.id.edit_trailname);
        String locationName = text(R.id.edit_locationname);
        String description = text(R.id.edit_description);
        saveForm(url, username, password, trailName, locationName, description);

        if (username.isEmpty() || password.isEmpty() || trailName.isEmpty()) {
            DiagnosticLog.event(this, "UPLOAD", "QUEUE_REJECTED",
                    "reason=required_fields");
            Toast.makeText(this, R.string.upload_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidHttpsUrl(url)) {
            DiagnosticLog.event(this, "UPLOAD", "QUEUE_REJECTED",
                    "reason=invalid_https_url");
            Toast.makeText(this, R.string.upload_https_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (trailId <= 0 || pointCount == 0) {
            DiagnosticLog.event(this, "UPLOAD", "QUEUE_REJECTED",
                    "reason=no_points");
            Toast.makeText(this, R.string.upload_no_locations, Toast.LENGTH_LONG).show();
            return;
        }

        Data input = new Data.Builder()
                .putString(TrailUploadWorker.KEY_URL, url)
                .putString(TrailUploadWorker.KEY_USERNAME, username)
                .putString(TrailUploadWorker.KEY_PASSWORD, password)
                .putString(TrailUploadWorker.KEY_TRAIL_NAME, trailName)
                .putString(TrailUploadWorker.KEY_LOCATION_NAME, locationName)
                .putString(TrailUploadWorker.KEY_DESCRIPTION, description)
                .putLong(TrailUploadWorker.KEY_TRAIL_ID, trailId)
                .build();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        UUID workId = UUID.randomUUID();
        String uploadToken = workId.toString();
        input = new Data.Builder()
                .putAll(input)
                .putString(TrailUploadWorker.KEY_UPLOAD_TOKEN, uploadToken)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TrailUploadWorker.class)
                .setId(workId)
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        ((Button) findViewById(R.id.button_send)).setEnabled(false);
        WorkManager workManager = WorkManager.getInstance(this);
        UPLOAD_QUEUE_EXECUTOR.execute(() -> {
            try {
                if (!trailRepository.markUploadQueued(trailId, uploadToken)) {
                    runOnUiThread(() -> {
                        ((Button) findViewById(R.id.button_send)).setEnabled(true);
                        Toast.makeText(this, R.string.upload_already_pending,
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                workManager.enqueueUniqueWork(
                                TrailUploadWorker.uniqueWorkName(trailId),
                                ExistingWorkPolicy.KEEP,
                                request)
                        .getResult()
                        .get();
                runOnUiThread(() -> {
                    observeUpload(workManager, request, uploadToken);
                    DiagnosticLog.event(this, "UPLOAD", "QUEUED",
                            "trail=" + trailId);
                    Toast.makeText(this, R.string.upload_queued, Toast.LENGTH_LONG).show();
                });
            } catch (Exception exception) {
                trailRepository.markUploadFailed(
                        trailId, uploadToken, getString(R.string.upload_unknown_error));
                DiagnosticLog.error(this, "UPLOAD", "ENQUEUE_FAILED", exception);
                runOnUiThread(() -> {
                    ((Button) findViewById(R.id.button_send)).setEnabled(true);
                    showDialog(getString(R.string.upload_failed),
                            getString(R.string.upload_unknown_error));
                });
            }
        });
    }

    private void observeUpload(WorkManager workManager, OneTimeWorkRequest request,
                               String uploadToken) {
        workManager.getWorkInfoByIdLiveData(request.getId()).observe(this, workInfo -> {
            if (workInfo == null || !workInfo.getState().isFinished()) {
                return;
            }
            if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                DiagnosticLog.event(this, "UPLOAD", "UI_OBSERVED_SUCCESS");
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_TRAIL_ID, trailId));
                finish();
            } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                DiagnosticLog.event(this, "UPLOAD", "UI_OBSERVED_FAILURE");
                String error = workInfo.getOutputData().getString(TrailUploadWorker.KEY_ERROR);
                showDialog(getString(R.string.upload_failed),
                        error == null ? getString(R.string.upload_unknown_error) : error);
                ((Button) findViewById(R.id.button_send)).setEnabled(true);
            } else {
                DiagnosticLog.event(this, "UPLOAD", "UI_OBSERVED_CANCELLED",
                        "state=" + workInfo.getState().name());
                trailRepository.markUploadFailedAsync(
                        trailId, uploadToken, getString(R.string.upload_unknown_error));
                ((Button) findViewById(R.id.button_send)).setEnabled(true);
            }
        });
    }

    private String text(int viewId) {
        return ((EditText) findViewById(viewId)).getText().toString().trim();
    }

    private boolean isValidHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void showDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNeutralButton(android.R.string.ok, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void saveForm(String url, String username, String password, String trailName,
                          String locationName, String description) {
        JSONObject json = new JSONObject();
        try {
            json.put("url", url);
            json.put("username", username);
            json.put("password", password);
            json.put("trailname", trailName);
            json.put("locationname", locationName);
            json.put("description", description);
            java.io.File file = new java.io.File(
                    getExternalFilesDir(null), getString(R.string.form_state_filename));
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
        } catch (Exception exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void fillForm() {
        java.io.File file = new java.io.File(
                getExternalFilesDir(null), getString(R.string.form_state_filename));
        if (!file.exists()) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[256];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                text.append(buffer, 0, count);
            }
            JSONObject json = new JSONObject(text.toString());
            setText(R.id.edit_server_url, json.optString("url"));
            setText(R.id.edit_username, json.optString("username"));
            setText(R.id.edit_password, json.optString("password"));
            setText(R.id.edit_trailname, json.optString("trailname"));
            setText(R.id.edit_locationname, json.optString("locationname"));
            setText(R.id.edit_description, json.optString("description"));
        } catch (Exception exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setText(int viewId, String value) {
        ((EditText) findViewById(viewId)).setText(value);
    }
}

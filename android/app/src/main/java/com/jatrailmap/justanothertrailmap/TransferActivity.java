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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class TransferActivity extends AppCompatActivity {
    private String locationsFilename;
    private String picturesFilename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);
        locationsFilename = getIntent().getStringExtra("locsFilename");
        picturesFilename = getIntent().getStringExtra("picsFilename");
        fillForm();
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
            Toast.makeText(this, R.string.upload_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidHttpsUrl(url)) {
            Toast.makeText(this, R.string.upload_https_required, Toast.LENGTH_LONG).show();
            return;
        }
        File locations = new File(getExternalFilesDir(null), locationsFilename);
        if (!locations.exists() || locations.length() == 0) {
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
                .putString(TrailUploadWorker.KEY_LOCATIONS_FILENAME, locationsFilename)
                .putString(TrailUploadWorker.KEY_PICTURES_FILENAME, picturesFilename)
                .build();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TrailUploadWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        WorkManager workManager = WorkManager.getInstance(this);
        RecordingStateStore recordingStateStore = new RecordingStateStore(this);
        recordingStateStore.markUploading();
        try {
            workManager.enqueueUniqueWork(
                    TrailUploadWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
        } catch (RuntimeException exception) {
            recordingStateStore.uploadFailed();
            showDialog(getString(R.string.upload_failed), exception.getMessage());
            return;
        }
        observeUpload(workManager, request);
        ((Button) findViewById(R.id.button_send)).setEnabled(false);
        Toast.makeText(this, R.string.upload_queued, Toast.LENGTH_LONG).show();
    }

    private void observeUpload(WorkManager workManager, OneTimeWorkRequest request) {
        workManager.getWorkInfoByIdLiveData(request.getId()).observe(this, workInfo -> {
            if (workInfo == null || !workInfo.getState().isFinished()) {
                return;
            }
            if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                setResult(RESULT_OK);
                finish();
            } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                String error = workInfo.getOutputData().getString(TrailUploadWorker.KEY_ERROR);
                showDialog(getString(R.string.upload_failed),
                        error == null ? getString(R.string.upload_unknown_error) : error);
                ((Button) findViewById(R.id.button_send)).setEnabled(true);
            } else {
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
            File file = new File(getExternalFilesDir(null), getString(R.string.form_state_filename));
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
        } catch (Exception exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void fillForm() {
        File file = new File(getExternalFilesDir(null), getString(R.string.form_state_filename));
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

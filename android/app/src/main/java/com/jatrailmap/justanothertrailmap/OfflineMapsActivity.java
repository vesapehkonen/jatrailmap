package com.jatrailmap.justanothertrailmap;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.io.IOException;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OfflineMapsActivity extends AppCompatActivity {
    private static final ExecutorService MAP_IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private LinearLayout mapList;
    private TextView emptyMessage;
    private Button importButton;
    private boolean importing;
    private int coverageGeneration;
    private Set<String> coveringMaps = Collections.emptySet();
    private final ActivityResultLauncher<String[]> mapFileLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importMap);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_maps);
        setTitle(R.string.offline_maps_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        mapList = findViewById(R.id.offline_map_list);
        emptyMessage = findViewById(R.id.offline_maps_empty);
        importButton = findViewById(R.id.button_import_map);
        importButton.setOnClickListener(view ->
                mapFileLauncher.launch(new String[]{"application/octet-stream", "*/*"}));
        SwitchCompat automaticSelection = findViewById(R.id.switch_automatic_map);
        automaticSelection.setChecked(OfflineMapStore.isAutomaticSelectionEnabled(this));
        automaticSelection.setOnCheckedChangeListener((button, checked) ->
                OfflineMapStore.setAutomaticSelectionEnabled(this, checked));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithResult();
            }
        });
        renderMaps();
        loadCoverage();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finishWithResult();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void finishWithResult() {
        if (importing) {
            Toast.makeText(this, R.string.map_import_wait, Toast.LENGTH_SHORT).show();
            return;
        }
        setResult(RESULT_OK);
        finish();
    }

    private void importMap(Uri uri) {
        if (uri == null) {
            return;
        }
        importing = true;
        DiagnosticLog.event(this, "MAP", "IMPORT_STARTED");
        importButton.setEnabled(false);
        Toast.makeText(this, R.string.map_importing, Toast.LENGTH_SHORT).show();
        MAP_IO_EXECUTOR.execute(() -> {
            try {
                OfflineMapStore.importMap(this, uri);
                DiagnosticLog.event(this, "MAP", "IMPORT_SUCCEEDED",
                        "installedMaps=" + OfflineMapStore.listMaps(this).size());
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    importing = false;
                    importButton.setEnabled(true);
                    renderMaps();
                    loadCoverage();
                });
            } catch (IOException exception) {
                DiagnosticLog.error(this, "MAP", "IMPORT_FAILED", exception);
                runOnUiThread(() -> {
                    if (isDestroyed()) {
                        return;
                    }
                    importing = false;
                    importButton.setEnabled(true);
                    Toast.makeText(this, R.string.map_import_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void renderMaps() {
        List<OfflineMapStore.MapInfo> maps = OfflineMapStore.listMaps(this);
        mapList.removeAllViews();
        emptyMessage.setVisibility(maps.isEmpty() ? View.VISIBLE : View.GONE);
        for (OfflineMapStore.MapInfo map : maps) {
            mapList.addView(createMapRow(map));
        }
    }

    private View createMapRow(OfflineMapStore.MapInfo map) {
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(padding, padding, padding, padding);

        TextView details = new TextView(this);
        details.setText(getString(map.selected
                        ? R.string.offline_map_selected_details
                        : R.string.offline_map_details,
                map.fileName, Formatter.formatShortFileSize(this, map.sizeBytes))
                + (coveringMaps.contains(map.fileName)
                ? "\n" + getString(R.string.map_covers_current_location) : ""));
        details.setTextAppearance(android.R.style.TextAppearance_Material_Medium);
        row.addView(details, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button select = new Button(this);
        select.setText(map.selected ? R.string.map_selected : R.string.select_map_action);
        select.setEnabled(!map.selected);
        select.setOnClickListener(view -> selectMap(map.fileName));
        actions.addView(select, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button delete = new Button(this);
        delete.setText(R.string.delete_map_action);
        delete.setOnClickListener(view -> confirmDelete(map.fileName));
        actions.addView(delete, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(actions);
        return row;
    }

    private void selectMap(String fileName) {
        try {
            OfflineMapStore.selectMap(this, fileName);
            DiagnosticLog.event(this, "MAP", "SELECTED");
            renderMaps();
        } catch (IOException exception) {
            DiagnosticLog.error(this, "MAP", "SELECT_FAILED", exception);
            Toast.makeText(this, R.string.map_select_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete(String fileName) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_map_title)
                .setMessage(getString(R.string.delete_map_message, fileName))
                .setPositiveButton(R.string.delete_map_action, (dialog, which) -> deleteMap(fileName))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteMap(String fileName) {
        try {
            OfflineMapStore.deleteMap(this, fileName);
            DiagnosticLog.event(this, "MAP", "DELETED",
                    "installedMaps=" + OfflineMapStore.listMaps(this).size());
            renderMaps();
        } catch (IOException exception) {
            DiagnosticLog.error(this, "MAP", "DELETE_FAILED", exception);
            Toast.makeText(this, R.string.map_delete_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void loadCoverage() {
        int generation = ++coverageGeneration;
        new TrailRepository(this).getLatestPointAsync(point -> {
            if (point == null || isDestroyed()) {
                return;
            }
            MAP_IO_EXECUTOR.execute(() -> {
                Set<String> result = OfflineMapStore.findMapsCovering(
                        getApplicationContext(), point.latitude, point.longitude);
                runOnUiThread(() -> {
                    if (!isDestroyed() && generation == coverageGeneration) {
                        coveringMaps = result;
                        renderMaps();
                    }
                });
            });
        });
    }
}

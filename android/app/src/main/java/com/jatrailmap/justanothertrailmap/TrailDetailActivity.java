package com.jatrailmap.justanothertrailmap;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.mapsforge.core.graphics.Cap;
import org.mapsforge.core.graphics.Join;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class TrailDetailActivity extends AppCompatActivity {
    public static final String EXTRA_TRAIL_ID = "trailId";

    private long trailId;
    private TrailRepository trailRepository;
    private RecordingStateStore recordingStateStore;
    private TrailEntity trail;
    private List<TrailPointEntity> points = new ArrayList<>();
    private MapView mapView;
    private MapDataStore mapDataStore;
    private Polyline routePolyline;
    private final ActivityResultLauncher<Intent> transferLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> loadTrail());
    private final ActivityResultLauncher<Intent> mapsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> recreate());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        trailId = getIntent().getLongExtra(EXTRA_TRAIL_ID, 0);
        if (trailId <= 0) {
            finish();
            return;
        }
        setContentView(R.layout.activity_trail_detail);
        setTitle(R.string.trail_details_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        trailRepository = new TrailRepository(this);
        recordingStateStore = new RecordingStateStore(this);
        mapView = findViewById(R.id.trail_detail_map);
        mapView.getMapScaleBar().setVisible(true);
        mapView.setBuiltInZoomControls(true);
        findViewById(R.id.button_trail_rename).setOnClickListener(view -> renameTrail());
        findViewById(R.id.button_trail_delete).setOnClickListener(view -> confirmDelete());
        findViewById(R.id.button_trail_upload).setOnClickListener(view -> uploadTrail());
        findViewById(R.id.button_trail_maps).setOnClickListener(view -> mapsLauncher.launch(
                new Intent(this, OfflineMapsActivity.class)));
        restoreOfflineMap();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTrail();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadTrail() {
        trailRepository.getTrailDetailsAsync(trailId, (loadedTrail, loadedPoints) -> {
            if (loadedTrail == null) {
                Toast.makeText(this, R.string.trail_not_found, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            trail = loadedTrail;
            points = loadedPoints;
            renderDetails();
            renderRoute();
        });
    }

    private void renderDetails() {
        String name = trail.name.trim().isEmpty()
                ? getString(R.string.trail_default_name,
                TrailHistoryActivity.displayDate(trail.createdAt))
                : trail.name;
        ((TextView) findViewById(R.id.trail_detail_name)).setText(name);
        ((TextView) findViewById(R.id.trail_detail_date))
                .setText(TrailHistoryActivity.displayDate(trail.createdAt));
        long duration = trail.durationMs;
        RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
        if (snapshot.activeTrailId == trailId) {
            duration = Math.max(duration, snapshot.elapsedTimeMs);
        }
        ((TextView) findViewById(R.id.trail_detail_stats)).setText(getString(
                R.string.trail_summary_stats,
                points.size(),
                TrailHistoryActivity.displayDuration(duration),
                points.isEmpty() ? getString(R.string.trail_distance_meters, 0d)
                        : displayDistance(TrailRepository.calculateDistanceMeters(points))));
        TextView state = findViewById(R.id.trail_detail_upload_state);
        state.setText(uploadStateLabel(trail.uploadState));
        if (TrailEntity.UPLOAD_FAILED.equals(trail.uploadState)
                && trail.uploadError != null && !trail.uploadError.isEmpty()) {
            state.append(" · " + trail.uploadError);
        }
        boolean pending = TrailEntity.UPLOAD_QUEUED.equals(trail.uploadState)
                || TrailEntity.UPLOAD_UPLOADING.equals(trail.uploadState);
        boolean uploaded = TrailEntity.UPLOAD_UPLOADED.equals(trail.uploadState);
        boolean activeTrail = snapshot.activeTrailId == trailId;
        boolean activeTracking = activeTrail && snapshot.isTracking();
        ((Button) findViewById(R.id.button_trail_upload))
                .setEnabled(!pending && !uploaded && !activeTrail && !points.isEmpty()
                        && TrailEntity.RECORDING_FINISHED.equals(trail.recordingState));
        ((Button) findViewById(R.id.button_trail_delete)).setEnabled(!activeTracking && !pending);
    }

    private void renameTrail() {
        if (trail == null) {
            return;
        }
        EditText input = new EditText(this);
        input.setHint(R.string.trail_name_hint);
        input.setText(trail.name);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.rename_trail_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        trailRepository.renameTrailAsync(
                                trailId, input.getText().toString().trim(), success -> {
                                    if (success) {
                                        loadTrail();
                                    } else {
                                        Toast.makeText(this, R.string.trail_operation_failed,
                                                Toast.LENGTH_LONG).show();
                                    }
                                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete() {
        RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
        if (snapshot.activeTrailId == trailId && snapshot.isTracking()) {
            Toast.makeText(this, R.string.active_trail_delete_blocked, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_saved_trail_title)
                .setMessage(R.string.delete_saved_trail_message)
                .setPositiveButton(R.string.delete_this_trail, (dialog, which) ->
                        trailRepository.deleteTrailAsync(trailId, () -> {
                            if (recordingStateStore.getSnapshot().activeTrailId == trailId) {
                                recordingStateStore.reset();
                            }
                            setResult(RESULT_OK);
                            finish();
                        }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void uploadTrail() {
        RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
        if (snapshot.activeTrailId == trailId && snapshot.isTracking()) {
            Toast.makeText(this, R.string.active_trail_upload_blocked, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, TransferActivity.class)
                .putExtra(TransferActivity.EXTRA_TRAIL_ID, trailId)
                .putExtra(TransferActivity.EXTRA_POINT_COUNT, points.size())
                .putExtra(TransferActivity.EXTRA_TRAIL_NAME, trail.name);
        transferLauncher.launch(intent);
    }

    private void restoreOfflineMap() {
        File mapFile = OfflineMapStore.getSelectedMap(this);
        if (mapFile == null) {
            mapView.setCenter(new LatLong(0, 0));
            mapView.setZoomLevel((byte) 2);
            return;
        }
        try {
            TileCache tileCache = AndroidUtil.createTileCache(this, "trail-detail-mapcache",
                    mapView.getModel().displayModel.getTileSize(), 1f,
                    mapView.getModel().frameBufferModel.getOverdrawFactor());
            mapDataStore = new MapFile(mapFile);
            TileRendererLayer layer = new TileRendererLayer(
                    tileCache, mapDataStore, mapView.getModel().mapViewPosition,
                    AndroidGraphicFactory.INSTANCE);
            layer.setXmlRenderTheme(MapsforgeThemes.DEFAULT);
            mapView.getLayerManager().getLayers().add(layer);
            findViewById(R.id.trail_detail_map_message).setVisibility(View.GONE);
        } catch (RuntimeException exception) {
            DiagnosticLog.error(this, "MAP", "TRAIL_DETAIL_OPEN_FAILED", exception);
        }
    }

    private void renderRoute() {
        if (routePolyline != null && mapView != null) {
            mapView.getLayerManager().getLayers().remove(routePolyline);
            routePolyline = null;
        }
        if (points.isEmpty() || mapView == null) {
            return;
        }
        List<LatLong> route = new ArrayList<>(points.size());
        for (TrailPointEntity point : points) {
            route.add(new LatLong(point.latitude, point.longitude));
        }
        Paint paint = AndroidGraphicFactory.INSTANCE.createPaint();
        paint.setColor(AndroidGraphicFactory.INSTANCE.createColor(255, 27, 94, 32));
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(5 * getResources().getDisplayMetrics().density);
        paint.setStrokeCap(Cap.ROUND);
        paint.setStrokeJoin(Join.ROUND);
        routePolyline = new Polyline(paint, AndroidGraphicFactory.INSTANCE);
        routePolyline.addPoints(route);
        mapView.getLayerManager().getLayers().add(routePolyline);
        mapView.setCenter(route.get(route.size() - 1));
        mapView.setZoomLevel((byte) 15);
        mapView.getLayerManager().redrawLayers();
    }

    private String displayDistance(double meters) {
        return meters < 1000
                ? getString(R.string.trail_distance_meters, meters)
                : getString(R.string.trail_distance_kilometers, meters / 1000);
    }

    private int uploadStateLabel(String state) {
        if (TrailEntity.UPLOAD_QUEUED.equals(state)) {
            return R.string.trail_upload_queued;
        }
        if (TrailEntity.UPLOAD_UPLOADING.equals(state)) {
            return R.string.trail_upload_uploading;
        }
        if (TrailEntity.UPLOAD_FAILED.equals(state)) {
            return R.string.trail_upload_failed_state;
        }
        if (TrailEntity.UPLOAD_UPLOADED.equals(state)) {
            return R.string.trail_upload_uploaded;
        }
        return R.string.trail_upload_local;
    }

    @Override
    protected void onDestroy() {
        if (mapView != null) {
            mapView.destroyAll();
            mapView = null;
        }
        mapDataStore = null;
        super.onDestroy();
    }
}

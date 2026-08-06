package com.jatrail;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.content.Context;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Cap;
import org.mapsforge.core.graphics.Join;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.MapPosition;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.overlay.Circle;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EmptyStackException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.lang.Thread;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.core.content.FileProvider;

public class MainActivity extends AppCompatActivity {
    private static final String FILE_PROVIDER_AUTHORITY =
            "com.jatrail.fileprovider";
    private static final ExecutorService MAP_COVERAGE_EXECUTOR =
            Executors.newSingleThreadExecutor();

    public class Timer {
        private final Chronometer chronometer;

        public Timer(Chronometer chronometer) {
            this.chronometer = chronometer;
        }

        public void render(long elapsedTimeMs, boolean running) {
            chronometer.setBase(SystemClock.elapsedRealtime() - elapsedTimeMs);
            if (running) {
                chronometer.start();
            } else {
                chronometer.stop();
            }
        }
    }

    private int delete_this = 0;
    private Timer timer;
    private RecordingStateStore recordingStateStore;
    private TrailRepository trailRepository;
    private final String LOG = "mylog";
    private Context context;
    private final int TAKE_PICTURE = 1, TRANSFER_DATA = 2;
    private boolean trackingReceiverRegistered;
    private MapView mapView;
    private TileCache tileCache;
    private MapDataStore mapDataStore;
    private MapThemeStore.Style displayedMapStyle;
    private Marker locationMarker;
    private Circle accuracyCircle;
    private Polyline routePolyline;
    private long lastRenderedPointId;
    private LatLong latestLocation;
    private LatLong lastDistanceLocation;
    private double routeDistanceMeters;
    private long lastGpsPointTimeMs;
    private boolean hasStoredMapPosition;
    private String displayedMapFileName;
    private boolean discardMapPositionOnPause;
    private BoundingBox selectedMapBounds;
    private boolean automaticMapSelectionInProgress;
    private long lastCoverageCheckPointId;
    private boolean mainActivityResumed;
    private boolean hasLiveGpsFix;
    private Float liveGpsAccuracyMeters;
    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateGpsState(intent);
            refreshUi();
            refreshMapRoute();
        }
    };
    private final ActivityResultLauncher<Intent> offlineMapsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    lastCoverageCheckPointId = 0;
                    File selectedMap = OfflineMapStore.getSelectedMap(this);
                    String selectedName = selectedMap == null ? null : selectedMap.getName();
                    if (!Objects.equals(displayedMapFileName, selectedName)) {
                        discardMapPositionOnPause = true;
                        recreate();
                    } else {
                        refreshMapRoute();
                    }
                }
            });
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
                boolean granted = Boolean.TRUE.equals(
                        grants.get(Manifest.permission.ACCESS_FINE_LOCATION));
                DiagnosticLog.event(this, "PERMISSION", "LOCATION_RESULT",
                        "fine=" + granted);
                if (granted) {
                    startTracking();
                } else {
                    Toast.makeText(this, R.string.location_permission_denied,
                            Toast.LENGTH_LONG).show();
                }
            });
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                DiagnosticLog.event(this, "PERMISSION", "CAMERA_RESULT",
                        "granted=" + granted);
                if (granted) {
                    dispatchTakePictureIntent();
                } else {
                    Toast.makeText(this, R.string.camera_permission_denied,
                            Toast.LENGTH_LONG).show();
                }
            });
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                DiagnosticLog.event(this, "PERMISSION", "NOTIFICATION_RESULT",
                        "granted=" + granted);
                startTrackingService();
            });

    private void requestLocationPermissionAndStartTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            DiagnosticLog.event(this, "PERMISSION", "LOCATION_ALREADY_GRANTED");
            startTracking();
        } else {
            DiagnosticLog.event(this, "PERMISSION", "LOCATION_REQUESTED");
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            });
        }
    }

    private void startTracking() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            DiagnosticLog.event(this, "PERMISSION", "NOTIFICATION_REQUESTED");
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            startTrackingService();
        }
    }

    private void startTrackingService() {
        DiagnosticLog.event(this, "UI", "START_TRACKING_REQUESTED");
        Intent intent = new Intent(this, LocationTrackingService.class);
        intent.setAction(LocationTrackingService.ACTION_START);
        ContextCompat.startForegroundService(this, intent);
    }

    private void stopTrackingService() {
        DiagnosticLog.event(this, "UI", "STOP_TRACKING_REQUESTED");
        Intent intent = new Intent(this, LocationTrackingService.class);
        intent.setAction(LocationTrackingService.ACTION_STOP);
        startService(intent);
    }

    private void refreshUi() {
        RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
        Button startButton = findViewById(R.id.button_start);
        Button stopButton = findViewById(R.id.button_stop);
        Button pictureButton = findViewById(R.id.button_picture);
        Button sendButton = findViewById(R.id.button_send);
        Button deleteButton = findViewById(R.id.button_delete);
        Button finishButton = findViewById(R.id.button_finish);
        TextView status = findViewById(R.id.text_recording_status);
        TextView summary = findViewById(R.id.text_recording_summary);
        sendButton.setVisibility(View.GONE);
        switch (snapshot.status) {
        case INITIAL:
            status.setText(R.string.recording_status_ready);
            summary.setText(R.string.recording_summary_ready);
            startButton.setText(R.string.start_recording);
            startButton.setVisibility(View.VISIBLE);
            startButton.setEnabled(true);
            stopButton.setVisibility(View.GONE);
            pictureButton.setVisibility(View.GONE);
            pictureButton.setEnabled(false);
            sendButton.setEnabled(false);
            deleteButton.setVisibility(View.GONE);
            deleteButton.setEnabled(false);
            finishButton.setVisibility(View.GONE);
            break;
        case STOPPED:
            status.setText(R.string.recording_status_paused);
            summary.setText(R.string.recording_summary_paused);
            startButton.setText(R.string.continue_recording);
            startButton.setVisibility(View.VISIBLE);
            startButton.setEnabled(true);
            stopButton.setVisibility(View.GONE);
            pictureButton.setVisibility(View.GONE);
            pictureButton.setEnabled(false);
            sendButton.setEnabled(false);
            deleteButton.setVisibility(View.VISIBLE);
            deleteButton.setEnabled(true);
            finishButton.setVisibility(View.VISIBLE);
            finishButton.setEnabled(snapshot.activeTrailId > 0);
            break;
        case TRACKING:
            status.setText(R.string.recording_status_active);
            summary.setText(R.string.recording_summary_active);
            startButton.setVisibility(View.GONE);
            stopButton.setVisibility(View.VISIBLE);
            stopButton.setEnabled(true);
            pictureButton.setVisibility(View.VISIBLE);
            pictureButton.setEnabled(true);
            sendButton.setEnabled(false);
            deleteButton.setVisibility(View.GONE);
            deleteButton.setEnabled(false);
            finishButton.setVisibility(View.GONE);
            break;
        }
        ((TextView) findViewById(R.id.text_locs)).setText(String.valueOf(snapshot.points));
        renderDistance();
        renderRecordingSummary(snapshot);
        timer.render(snapshot.elapsedTimeMs, snapshot.isTracking());
        renderGpsStatus(snapshot);
    }

    // Starts the timer and sets states of buttons depending on the existence of the location file
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(LOG, "MainActivity: onCreate()");
        Log.i(LOG, "onCreate() delete_this=" + delete_this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Context context = getApplicationContext();
        timer = new Timer((Chronometer) findViewById(R.id.chronometer));
        recordingStateStore = new RecordingStateStore(context);
        trailRepository = new TrailRepository(context);
        ((Chronometer) findViewById(R.id.chronometer)).setOnChronometerTickListener(
                chronometer -> renderRecordingSummary(recordingStateStore.getSnapshot()));
        RecordingStateStore.Snapshot restoredState = recordingStateStore.getSnapshot();
        DiagnosticLog.event(this, "UI", "MAIN_CREATED",
                "status=" + restoredState.status.name()
                        + " session=" + restoredState.sessionId
                        + " points=" + restoredState.points
                        + " savedInstance=" + (savedInstanceState != null));
        mapView = findViewById(R.id.map_view);
        mapView.getMapScaleBar().setVisible(true);
        mapView.setBuiltInZoomControls(false);
        findViewById(R.id.button_select_map).setOnClickListener(view -> offlineMapsLauncher.launch(
                new Intent(this, OfflineMapsActivity.class)));
        findViewById(R.id.button_recenter).setOnClickListener(view -> centerOnLatestLocation());
        findViewById(R.id.button_zoom_in).setOnClickListener(view ->
                mapView.getModel().mapViewPosition.zoomIn());
        findViewById(R.id.button_zoom_out).setOnClickListener(view ->
                mapView.getModel().mapViewPosition.zoomOut());
        restoreOfflineMap();
        registerTrackingReceiver();
        if (savedInstanceState != null) {
            currentImagePath = savedInstanceState.getString("currentImagePath", "");
            hasLiveGpsFix = savedInstanceState.getBoolean("hasLiveGpsFix", false);
            if (savedInstanceState.containsKey("liveGpsAccuracyMeters")) {
                liveGpsAccuracyMeters = savedInstanceState.getFloat("liveGpsAccuracyMeters");
            }
        }
        refreshUi();
        refreshMapRoute();
        if (recordingStateStore.getSnapshot().isTracking()) {
            DiagnosticLog.event(this, "UI", "RESTORE_TRACKING_REQUESTED",
                    "session=" + recordingStateStore.getSnapshot().sessionId);
            requestLocationPermissionAndStartTracking();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString("currentImagePath", currentImagePath);
        savedInstanceState.putBoolean("hasLiveGpsFix", hasLiveGpsFix);
        if (liveGpsAccuracyMeters != null) {
            savedInstanceState.putFloat("liveGpsAccuracyMeters", liveGpsAccuracyMeters);
        }
        super.onSaveInstanceState(savedInstanceState);
        Log.i(LOG, "MainActivity: onSaveInstanceState()");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i(LOG, "MainActivity: onConfigurationChanged()");
        super.onConfigurationChanged(newConfig);
    }

    public void onClick(View view) {
        final int id = view.getId();
        //switch (id) {
	if (id ==  R.id.button_start) {
		requestLocationPermissionAndStartTracking();
	}

	if (id == R.id.button_stop) {
		    stopTrackingService();
	}

	if (id == R.id.button_picture) {
		requestCameraPermissionAndTakePicture();
	}

        if (id == R.id.button_finish) {
                finishActiveTrail();
        }

	if (id == R.id.button_send ) {
                RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
                Intent intent = new Intent(this, TransferActivity.class);
                intent.putExtra(TransferActivity.EXTRA_TRAIL_ID, snapshot.activeTrailId);
                intent.putExtra(TransferActivity.EXTRA_POINT_COUNT, snapshot.points);
                startActivityForResult(intent, TRANSFER_DATA);
	}

	if (id == R.id.button_delete) {
                new AlertDialog.Builder(this)
                        .setTitle("Delete entry")
                        .setMessage("Are you sure you want to delete this entry?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                                trailRepository.deleteActiveTrailAsync(() -> {
                                    recordingStateStore.reset();
                                    refreshUi();
                                    clearMapRoute();
                                });
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                //Log.i(LOG, "CANCEL clicked");
                                // do nothing
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
	}
    }

    private void finishActiveTrail() {
        RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
        if (snapshot.status != RecordingStateStore.Status.STOPPED
                || snapshot.activeTrailId <= 0) {
            return;
        }
        trailRepository.getTrailDetailsAsync(snapshot.activeTrailId, (trail, points) -> {
            if (trail == null && points.isEmpty()) {
                recordingStateStore.reset();
                refreshUi();
                clearMapRoute();
                Toast.makeText(this, R.string.empty_trail_finished, Toast.LENGTH_LONG).show();
                return;
            }
            EditText nameInput = new EditText(this);
            nameInput.setHint(R.string.trail_name_hint);
            nameInput.setText(TrailNames.normalized(trail.name, trail.createdAt));
            nameInput.setSelectAllOnFocus(true);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.finish_trail_title)
                    .setMessage(R.string.finish_trail_message)
                    .setView(nameInput)
                    .setPositiveButton(R.string.finish_trail, (dialog, which) -> {
                        RecordingStateStore.Snapshot current =
                                recordingStateStore.getSnapshot();
                        if (current.status != RecordingStateStore.Status.STOPPED
                                || current.activeTrailId != snapshot.activeTrailId) {
                            return;
                        }
                            trailRepository.finishTrailAsync(
                                    snapshot.activeTrailId,
                                    nameInput.getText().toString(),
                                    current.elapsedTimeMs,
                                    success -> {
                                        if (!success) {
                                            Toast.makeText(this, R.string.finish_trail_failed,
                                                    Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        recordingStateStore.reset();
                                        refreshUi();
                                        clearMapRoute();
                                        Toast.makeText(this, R.string.trail_finished,
                                                Toast.LENGTH_LONG).show();
                                    });
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        //Log.e(LOG, "onOptionsItemSelected func");
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_trail_history) {
            startActivity(new Intent(this, TrailHistoryActivity.class));
            return true;
        } else if (id == R.id.action_recording_settings) {
            startActivity(new Intent(this, RecordingSettingsActivity.class));
            return true;
        } else if (id == R.id.action_export_diagnostics) {
            shareDiagnostics();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void onStart() {
        Log.i(LOG, "onStart()");
        super.onStart();
    }

    private void registerTrackingReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationTrackingService.ACTION_LOCATION_RECORDED);
        filter.addAction(LocationTrackingService.ACTION_TRACKING_STOPPED);
        filter.addAction(LocationTrackingService.ACTION_GPS_SEARCHING);
        ContextCompat.registerReceiver(
                this, trackingReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        trackingReceiverRegistered = true;
    }

    protected void onRestart() {
        Log.i(LOG, "onRestart()");
        super.onRestart();
    }

    protected void onResume() {
        Log.i(LOG, "onResume()");
        super.onResume();
        if (displayedMapStyle != null
                && displayedMapStyle != MapThemeStore.load(this)) {
            DiagnosticLog.event(this, "MAP", "STYLE_CHANGED",
                    "from=" + displayedMapStyle.name()
                            + " to=" + MapThemeStore.load(this).name());
            recreate();
            return;
        }
        DiagnosticLog.event(this, "UI", "MAIN_RESUMED",
                "status=" + recordingStateStore.getSnapshot().status.name());
        mainActivityResumed = true;
        lastCoverageCheckPointId = 0;
        refreshUi();
        refreshMapRoute();
    }

    protected void onPause() {
        Log.i(LOG, "onPause()");
        DiagnosticLog.event(this, "UI", "MAIN_PAUSED",
                "status=" + recordingStateStore.getSnapshot().status.name());
        mainActivityResumed = false;
        if (!discardMapPositionOnPause) {
            saveMapPosition();
        }
        super.onPause();
    }

    public void onStop() {
        Log.i(LOG, "onStop()");
        super.onStop();
    }

    protected void onDestroy() {
        Log.i(LOG, "onDestroy()");
        if (trackingReceiverRegistered) {
            unregisterReceiver(trackingReceiver);
            trackingReceiverRegistered = false;
        }
        if (mapView != null) {
            mapView.destroyAll();
            mapView = null;
            mapDataStore = null;
            selectedMapBounds = null;
        }
        AndroidGraphicFactory.clearResourceMemoryCache();
        super.onDestroy();
    }

    private void restoreOfflineMap() {
        displayedMapStyle = MapThemeStore.load(this);
        File mapFile = OfflineMapStore.getSelectedMap(this);
        if (mapFile == null) {
            DiagnosticLog.event(this, "MAP", "NO_MAP_SELECTED");
            displayedMapFileName = null;
            mapView.setCenter(new LatLong(0, 0));
            mapView.setZoomLevel((byte) 2);
            return;
        }
        try {
            displayedMapFileName = mapFile.getName();
            tileCache = AndroidUtil.createTileCache(
                    this, "mapcache-" + displayedMapStyle.cacheSuffix(),
                    mapView.getModel().displayModel.getTileSize(), 1f,
                    mapView.getModel().frameBufferModel.getOverdrawFactor());
            mapDataStore = new MapFile(mapFile);
            selectedMapBounds = mapDataStore.boundingBox();
            TileRendererLayer mapLayer = new TileRendererLayer(tileCache, mapDataStore,
                    mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
            mapLayer.setXmlRenderTheme(displayedMapStyle.renderTheme());
            mapView.getLayerManager().getLayers().add(mapLayer);
            findViewById(R.id.map_empty_message).setVisibility(View.GONE);
            DiagnosticLog.event(this, "MAP", "OPENED",
                    "sizeBytes=" + mapFile.length());

            SharedPreferences preferences = getSharedPreferences(
                    OfflineMapStore.PREFERENCES, MODE_PRIVATE);
            hasStoredMapPosition = preferences.contains(OfflineMapStore.CENTER_LATITUDE)
                    && preferences.contains(OfflineMapStore.CENTER_LONGITUDE);
            if (hasStoredMapPosition) {
                LatLong center = new LatLong(
                        Double.longBitsToDouble(preferences.getLong(
                                OfflineMapStore.CENTER_LATITUDE, 0)),
                        Double.longBitsToDouble(preferences.getLong(
                                OfflineMapStore.CENTER_LONGITUDE, 0)));
                byte zoom = (byte) preferences.getInt(OfflineMapStore.ZOOM, 12);
                mapView.getModel().mapViewPosition.setMapPosition(new MapPosition(center, zoom));
            } else {
                mapView.setCenter(new LatLong(0, 0));
                mapView.setZoomLevel((byte) 2);
            }
        } catch (Exception exception) {
            Log.e(LOG, "Unable to open stored offline map", exception);
            DiagnosticLog.error(this, "MAP", "OPEN_FAILED", exception);
            findViewById(R.id.map_empty_message).setVisibility(View.VISIBLE);
        }
    }

    private void refreshMapRoute() {
        long afterId = lastRenderedPointId;
        trailRepository.getRouteUpdateAsync(afterId, (points, latestPoint) -> {
            if (mapView == null) {
                return;
            }
            if (latestPoint == null) {
                clearMapRoute();
                return;
            }

            if (latestPoint.id < lastRenderedPointId) {
                clearMapRoute();
            }
            List<LatLong> newLocations = new ArrayList<>();
            for (TrailPointEntity point : points) {
                if (point.id > lastRenderedPointId) {
                    LatLong location = new LatLong(point.latitude, point.longitude);
                    if (lastDistanceLocation != null) {
                        routeDistanceMeters += distanceMeters(lastDistanceLocation, location);
                    }
                    lastDistanceLocation = location;
                    newLocations.add(location);
                    lastRenderedPointId = point.id;
                }
            }
            if (!newLocations.isEmpty()) {
                ensureRoutePolyline();
                routePolyline.addPoints(newLocations);
            }

            latestLocation = new LatLong(latestPoint.latitude, latestPoint.longitude);
            lastGpsPointTimeMs = parseTimestamp(latestPoint.timestamp);
            renderDistance();
            renderRecordingSummary(recordingStateStore.getSnapshot());
            maybeSelectMapForLocation(latestPoint.id, latestLocation);
            showLocationMarker();
            findViewById(R.id.button_recenter).setEnabled(true);
            if (!hasStoredMapPosition) {
                centerOnLatestLocation();
                hasStoredMapPosition = true;
            }
        });
    }

    private void maybeSelectMapForLocation(long pointId, LatLong location) {
        if (location == null
                || !mainActivityResumed
                || !OfflineMapStore.isAutomaticSelectionEnabled(this)
                || automaticMapSelectionInProgress
                || pointId == lastCoverageCheckPointId) {
            return;
        }
        lastCoverageCheckPointId = pointId;
        if (selectedMapBounds != null && selectedMapBounds.contains(location)) {
            return;
        }

        automaticMapSelectionInProgress = true;
        Context applicationContext = getApplicationContext();
        MAP_COVERAGE_EXECUTOR.execute(() -> {
            String bestMap = OfflineMapStore.findBestMapForLocation(
                    applicationContext, location.latitude, location.longitude);
            runOnUiThread(() -> {
                automaticMapSelectionInProgress = false;
                if (mapView == null || !mainActivityResumed) {
                    return;
                }
                if (!location.equals(latestLocation)) {
                    maybeSelectMapForLocation(lastRenderedPointId, latestLocation);
                    return;
                }
                if (bestMap == null || bestMap.equals(displayedMapFileName)
                        || !OfflineMapStore.isAutomaticSelectionEnabled(this)) {
                    return;
                }
                try {
                    OfflineMapStore.selectMap(this, bestMap);
                    DiagnosticLog.event(this, "MAP", "AUTOMATICALLY_SELECTED");
                    discardMapPositionOnPause = true;
                    Toast.makeText(this, getString(R.string.map_automatically_selected, bestMap),
                            Toast.LENGTH_SHORT).show();
                    recreate();
                } catch (IOException exception) {
                    Log.e(LOG, "Unable to automatically select offline map", exception);
                }
            });
        });
    }

    private void ensureRoutePolyline() {
        if (routePolyline != null) {
            return;
        }
        Paint routePaint = AndroidGraphicFactory.INSTANCE.createPaint();
        routePaint.setColor(AndroidGraphicFactory.INSTANCE.createColor(255, 27, 94, 32));
        routePaint.setStyle(Style.STROKE);
        routePaint.setStrokeWidth(5 * getResources().getDisplayMetrics().density);
        routePaint.setStrokeCap(Cap.ROUND);
        routePaint.setStrokeJoin(Join.ROUND);
        routePolyline = new Polyline(routePaint, AndroidGraphicFactory.INSTANCE);
        mapView.getLayerManager().getLayers().add(routePolyline);
    }

    private void clearMapRoute() {
        lastRenderedPointId = 0;
        latestLocation = null;
        lastDistanceLocation = null;
        routeDistanceMeters = 0;
        lastGpsPointTimeMs = 0;
        renderDistance();
        if (mapView == null) {
            return;
        }
        findViewById(R.id.button_recenter).setEnabled(false);
        if (routePolyline != null) {
            routePolyline.clear();
        }
        if (locationMarker != null) {
            mapView.getLayerManager().getLayers().remove(locationMarker);
            locationMarker = null;
        }
        mapView.getLayerManager().redrawLayers();
    }

    private void showLocationMarker() {
        updateAccuracyCircle();
        if (locationMarker == null) {
            Drawable drawable = ResourcesCompat.getDrawable(
                    getResources(), R.drawable.map_location_marker, getTheme());
            if (drawable == null) {
                return;
            }
            Bitmap markerBitmap = AndroidGraphicFactory.convertToBitmap(drawable);
            locationMarker = new Marker(latestLocation, markerBitmap, 0, 0);
            mapView.getLayerManager().getLayers().add(locationMarker);
        } else {
            locationMarker.setLatLong(latestLocation);
        }
        mapView.getLayerManager().redrawLayers();
    }

    private void updateGpsState(Intent intent) {
        String action = intent.getAction();
        if (LocationTrackingService.ACTION_LOCATION_RECORDED.equals(action)) {
            hasLiveGpsFix = true;
            lastGpsPointTimeMs = System.currentTimeMillis();
            liveGpsAccuracyMeters = intent.getBooleanExtra(
                    LocationTrackingService.EXTRA_HAS_ACCURACY, false)
                    ? intent.getFloatExtra(LocationTrackingService.EXTRA_ACCURACY_METERS, 0)
                    : null;
        } else if (LocationTrackingService.ACTION_GPS_SEARCHING.equals(action)
                || LocationTrackingService.ACTION_TRACKING_STOPPED.equals(action)) {
            hasLiveGpsFix = false;
            liveGpsAccuracyMeters = null;
        }
    }

    private void renderGpsStatus(RecordingStateStore.Snapshot snapshot) {
        TextView gpsStatus = findViewById(R.id.text_gps_status);
        if (!snapshot.isTracking()) {
            gpsStatus.setText(R.string.gps_inactive);
            hideAccuracyCircle();
        } else if (!hasLiveGpsFix) {
            gpsStatus.setText(R.string.gps_searching);
            hideAccuracyCircle();
        } else if (liveGpsAccuracyMeters == null) {
            gpsStatus.setText(R.string.gps_fix);
        } else if (liveGpsAccuracyMeters <= 10) {
            gpsStatus.setText(getString(
                    R.string.gps_accuracy_strong, liveGpsAccuracyMeters));
        } else if (liveGpsAccuracyMeters <= 30) {
            gpsStatus.setText(getString(
                    R.string.gps_accuracy_good, liveGpsAccuracyMeters));
        } else {
            gpsStatus.setText(getString(
                    R.string.gps_accuracy_weak, liveGpsAccuracyMeters));
        }
    }

    private void renderDistance() {
        TextView distance = findViewById(R.id.text_distance);
        if (distance == null) {
            return;
        }
        if (routeDistanceMeters < 1000) {
            distance.setText(getString(R.string.trail_distance_meters, routeDistanceMeters));
        } else {
            distance.setText(getString(
                    R.string.trail_distance_kilometers, routeDistanceMeters / 1000));
        }
    }

    private void renderRecordingSummary(RecordingStateStore.Snapshot snapshot) {
        if (snapshot == null || snapshot.status == RecordingStateStore.Status.INITIAL) {
            return;
        }
        TextView summary = findViewById(R.id.text_recording_summary);
        if (snapshot.points == 0 || lastGpsPointTimeMs <= 0) {
            if (snapshot.isTracking()) {
                summary.setText(R.string.waiting_for_first_gps_point);
            }
            return;
        }
        long ageSeconds = Math.max(
                0, (System.currentTimeMillis() - lastGpsPointTimeMs) / 1000);
        if (ageSeconds < 10) {
            summary.setText(R.string.last_gps_point_now);
        } else if (ageSeconds < 60) {
            summary.setText(getString(R.string.last_gps_point_seconds, ageSeconds));
        } else if (ageSeconds < 3600) {
            summary.setText(getString(R.string.last_gps_point_minutes, ageSeconds / 60));
        } else {
            summary.setText(getString(R.string.last_gps_point_hours, ageSeconds / 3600));
        }
    }

    static double distanceMeters(LatLong start, LatLong end) {
        double latitudeRadians = Math.toRadians(end.latitude - start.latitude);
        double longitudeRadians = Math.toRadians(end.longitude - start.longitude);
        double startLatitude = Math.toRadians(start.latitude);
        double endLatitude = Math.toRadians(end.latitude);
        double haversine = Math.sin(latitudeRadians / 2) * Math.sin(latitudeRadians / 2)
                + Math.cos(startLatitude) * Math.cos(endLatitude)
                * Math.sin(longitudeRadians / 2) * Math.sin(longitudeRadians / 2);
        haversine = Math.max(0, Math.min(1, haversine));
        return 6371000 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }

    private long parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return 0;
        }
        try {
            String normalized = timestamp;
            if (normalized.endsWith("Z")) {
                normalized = normalized.substring(0, normalized.length() - 1) + "+0000";
            } else if (normalized.length() >= 6
                    && normalized.charAt(normalized.length() - 3) == ':') {
                normalized = normalized.substring(0, normalized.length() - 3)
                        + normalized.substring(normalized.length() - 2);
            }
            SimpleDateFormat format = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            format.setLenient(false);
            Date date = format.parse(normalized);
            return date == null ? 0 : date.getTime();
        } catch (java.text.ParseException exception) {
            return 0;
        }
    }

    private void updateAccuracyCircle() {
        if (!hasLiveGpsFix || liveGpsAccuracyMeters == null || latestLocation == null) {
            hideAccuracyCircle();
            return;
        }
        if (accuracyCircle == null) {
            Paint fill = AndroidGraphicFactory.INSTANCE.createPaint();
            fill.setColor(AndroidGraphicFactory.INSTANCE.createColor(45, 21, 101, 192));
            fill.setStyle(Style.FILL);
            Paint stroke = AndroidGraphicFactory.INSTANCE.createPaint();
            stroke.setColor(AndroidGraphicFactory.INSTANCE.createColor(170, 21, 101, 192));
            stroke.setStyle(Style.STROKE);
            stroke.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
            accuracyCircle = new Circle(
                    latestLocation, Math.max(1, liveGpsAccuracyMeters), fill, stroke);
            int routeIndex = routePolyline == null
                    ? mapView.getLayerManager().getLayers().size()
                    : mapView.getLayerManager().getLayers().indexOf(routePolyline);
            mapView.getLayerManager().getLayers().add(routeIndex, accuracyCircle);
        } else {
            accuracyCircle.setLatLong(latestLocation);
            accuracyCircle.setRadius(Math.max(1, liveGpsAccuracyMeters));
            accuracyCircle.setVisible(true, false);
        }
    }

    private void hideAccuracyCircle() {
        if (accuracyCircle != null) {
            accuracyCircle.setVisible(false, true);
        }
    }

    private void centerOnLatestLocation() {
        if (latestLocation == null) {
            return;
        }
        mapView.setCenter(latestLocation);
        if (mapView.getModel().mapViewPosition.getZoomLevel() < 15) {
            mapView.setZoomLevel((byte) 15);
        }
    }

    private void saveMapPosition() {
        if (mapView == null || mapDataStore == null) {
            return;
        }
        LatLong center = mapView.getModel().mapViewPosition.getCenter();
        getSharedPreferences(OfflineMapStore.PREFERENCES, MODE_PRIVATE).edit()
                .putLong(OfflineMapStore.CENTER_LATITUDE,
                        Double.doubleToRawLongBits(center.latitude))
                .putLong(OfflineMapStore.CENTER_LONGITUDE,
                        Double.doubleToRawLongBits(center.longitude))
                .putInt(OfflineMapStore.ZOOM,
                        mapView.getModel().mapViewPosition.getZoomLevel())
                .apply();
    }

    private void showDialog(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    private String currentImagePath = "";

    // Create a image file to the public picture directory
    private File createImageFile()  {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "img_" + timeStamp + ".jpg";
        //File storageDir = Environment.getExternalStoragePublicDirectory(
	//       Environment.DIRECTORY_PICTURES);
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = new File(storageDir + "/" + imageFileName);

        // Save a file path for use with later
	try {
	    currentImagePath = image.getAbsolutePath();
	}
	catch (SecurityException e) {
	    Log.e(LOG, "exception", e);
	    return null;
	}
        return image;
    }

    private void requestCameraPermissionAndTakePicture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            DiagnosticLog.event(this, "PERMISSION", "CAMERA_ALREADY_GRANTED");
            dispatchTakePictureIntent();
        } else {
            DiagnosticLog.event(this, "PERMISSION", "CAMERA_REQUESTED");
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(getBaseContext(), "There isn't a camera!",
                    Toast.LENGTH_SHORT).show();
            Log.w(LOG, "There isn't a camera activity to handle the intent");
	        return;
        }
	// Create the File where the photo should go
	File photoFile = null;
	if ((photoFile = createImageFile()) == null) {
            Toast.makeText(getBaseContext(), "Couldn\'t create photo file!",
			   Toast.LENGTH_SHORT).show();
	    return;
	}
        //takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
	Uri photoURI = FileProvider.getUriForFile(this,
                                                  FILE_PROVIDER_AUTHORITY,
                                                  photoFile);
	takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
	try {
            startActivityForResult(takePictureIntent, TAKE_PICTURE);
        } catch (Exception e) {
            Toast.makeText(getBaseContext(), "Exception: " + e.getMessage(),
                           Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case TAKE_PICTURE:
                switch (resultCode) {
                    case RESULT_OK:
                        Intent savePictureIntent = new Intent(this, LocationTrackingService.class);
                        savePictureIntent.setAction(LocationTrackingService.ACTION_SAVE_PICTURE);
                        savePictureIntent.putExtra(
                                LocationTrackingService.EXTRA_IMAGE_PATH, currentImagePath);
                        startService(savePictureIntent);
                        break;
                    case RESULT_CANCELED:
                        (new File(currentImagePath)).delete();
                        break;
                }
                break; // TAKE_PICTURE

            case TRANSFER_DATA:
                switch (resultCode) {
                    case RESULT_OK:
                        Log.i(LOG, "onActivityResult: RESULT_OK");
                        refreshUi();
                        clearMapRoute();
                        showDialog("Information", "Trail data was sent successfully");
                        break;
                    case RESULT_CANCELED:
                        //Log.i(LOG, "onActivityResult: RESULT_CANCELED");
                        break;
                }
                break;
        }
    }

    private void shareDiagnostics() {
        try {
            DiagnosticLog.event(this, "UI", "DIAGNOSTICS_EXPORT_REQUESTED");
            File diagnostics = DiagnosticLog.createExportFile(this);
            Uri uri = FileProvider.getUriForFile(
                    this, FILE_PROVIDER_AUTHORITY, diagnostics);
            Intent share = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newRawUri("diagnostics", uri));
            startActivity(Intent.createChooser(
                    share, getString(R.string.diagnostics_share_title)));
        } catch (IOException | RuntimeException exception) {
            DiagnosticLog.error(this, "UI", "DIAGNOSTICS_EXPORT_FAILED", exception);
            Toast.makeText(this, R.string.diagnostics_export_failed, Toast.LENGTH_LONG).show();
        }
    }
}

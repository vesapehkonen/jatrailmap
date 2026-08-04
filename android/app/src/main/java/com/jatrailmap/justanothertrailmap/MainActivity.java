package com.jatrailmap.justanothertrailmap;

import android.app.AlertDialog;
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
import org.mapsforge.core.graphics.Color;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Style;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.MapPosition;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.overlay.Polyline;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EmptyStackException;
import java.util.ArrayList;
import java.util.List;
import java.lang.Thread;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.core.content.FileProvider;

public class MainActivity extends AppCompatActivity {
    private static final String MAP_PREFERENCES = "offline_map";
    private static final String MAP_URI = "source_uri";
    private static final String MAP_FILE_NAME = "selected.map";
    private static final String MAP_LATITUDE = "center_latitude";
    private static final String MAP_LONGITUDE = "center_longitude";
    private static final String MAP_ZOOM = "zoom";
    private static final ExecutorService MAP_IO_EXECUTOR = Executors.newSingleThreadExecutor();

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
    private Marker locationMarker;
    private Polyline routePolyline;
    private long lastRenderedPointId;
    private LatLong latestLocation;
    private boolean hasStoredMapPosition;
    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
            refreshMapRoute();
        }
    };
    private final ActivityResultLauncher<String[]> mapFileLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importMap);
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
                if (Boolean.TRUE.equals(grants.get(Manifest.permission.ACCESS_FINE_LOCATION))) {
                    startTracking();
                } else {
                    Toast.makeText(this, R.string.location_permission_denied,
                            Toast.LENGTH_LONG).show();
                }
            });
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    dispatchTakePictureIntent();
                } else {
                    Toast.makeText(this, R.string.camera_permission_denied,
                            Toast.LENGTH_LONG).show();
                }
            });
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
                    startTrackingService());

    private void requestLocationPermissionAndStartTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startTracking();
        } else {
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
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            startTrackingService();
        }
    }

    private void startTrackingService() {
        Intent intent = new Intent(this, LocationTrackingService.class);
        intent.setAction(LocationTrackingService.ACTION_START);
        ContextCompat.startForegroundService(this, intent);
    }

    private void stopTrackingService() {
        Intent intent = new Intent(this, LocationTrackingService.class);
        intent.setAction(LocationTrackingService.ACTION_STOP);
        startService(intent);
    }

    private void refreshUi() {
        RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
        switch (snapshot.status) {
        case INITIAL:
            ((Button) findViewById(R.id.button_start)).setText("Start tracking");
            ((Button) findViewById(R.id.button_start)).setEnabled(true);
            ((Button) findViewById(R.id.button_stop)).setEnabled(false);
            ((Button) findViewById(R.id.button_picture)).setEnabled(false);
            ((Button) findViewById(R.id.button_send)).setEnabled(false);
            ((Button) findViewById(R.id.button_delete)).setEnabled(false);
            break;
        case STOPPED:
            ((Button) findViewById(R.id.button_start)).setText("Continue tracking");
            ((Button) findViewById(R.id.button_start)).setEnabled(true);
            ((Button) findViewById(R.id.button_stop)).setEnabled(false);
            ((Button) findViewById(R.id.button_picture)).setEnabled(false);
            ((Button) findViewById(R.id.button_send)).setEnabled(true);
            ((Button) findViewById(R.id.button_delete)).setEnabled(true);
            break;
        case TRACKING:
            ((Button) findViewById(R.id.button_start)).setEnabled(false);
            ((Button) findViewById(R.id.button_stop)).setEnabled(true);
            ((Button) findViewById(R.id.button_picture)).setEnabled(true);
            ((Button) findViewById(R.id.button_send)).setEnabled(false);
            ((Button) findViewById(R.id.button_delete)).setEnabled(false);
            break;
        case UPLOADING:
            ((Button) findViewById(R.id.button_start)).setText(R.string.upload_in_progress);
            ((Button) findViewById(R.id.button_start)).setEnabled(false);
            ((Button) findViewById(R.id.button_stop)).setEnabled(false);
            ((Button) findViewById(R.id.button_picture)).setEnabled(false);
            ((Button) findViewById(R.id.button_send)).setEnabled(false);
            ((Button) findViewById(R.id.button_delete)).setEnabled(false);
            break;
        }
        ((TextView) findViewById(R.id.text_locs))
                .setText(getString(R.string.points) + snapshot.points);
        timer.render(snapshot.elapsedTimeMs, snapshot.isTracking());
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
        mapView = findViewById(R.id.map_view);
        mapView.getMapScaleBar().setVisible(true);
        mapView.setBuiltInZoomControls(true);
        findViewById(R.id.button_select_map).setOnClickListener(view ->
                mapFileLauncher.launch(new String[]{"application/octet-stream", "*/*"}));
        findViewById(R.id.button_recenter).setOnClickListener(view -> centerOnLatestLocation());
        restoreOfflineMap();
        registerTrackingReceiver();
        if (savedInstanceState != null) {
            currentImagePath = savedInstanceState.getString("currentImagePath", "");
        }
        refreshUi();
        refreshMapRoute();
        if (recordingStateStore.getSnapshot().isTracking()) {
            requestLocationPermissionAndStartTracking();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString("currentImagePath", currentImagePath);
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

	if (id == R.id.button_send ) {
                Intent intent = new Intent(this, TransferActivity.class);
                startActivityForResult(intent, TRANSFER_DATA);
	}

	if (id == R.id.button_delete) {
                new AlertDialog.Builder(this)
                        .setTitle("Delete entry")
                        .setMessage("Are you sure you want to delete this entry?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                                trailRepository.clearAllAsync(() -> {
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
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
        if (id == R.id.action_settings) {
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
        refreshUi();
        refreshMapRoute();
    }

    protected void onPause() {
        Log.i(LOG, "onPause()");
        saveMapPosition();
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
        }
        AndroidGraphicFactory.clearResourceMemoryCache();
        super.onDestroy();
    }

    private void importMap(Uri uri) {
        if (uri == null) {
            return;
        }
        Toast.makeText(this, R.string.map_importing, Toast.LENGTH_SHORT).show();
        MAP_IO_EXECUTOR.execute(() -> {
            File mapDirectory = new File(getFilesDir(), "maps");
            File destination = new File(mapDirectory, MAP_FILE_NAME);
            File temporary = new File(mapDirectory, MAP_FILE_NAME + ".importing");
            File backup = new File(mapDirectory, MAP_FILE_NAME + ".backup");
            boolean imported = false;
            try {
                if (!mapDirectory.exists() && !mapDirectory.mkdirs()) {
                    throw new IOException("Unable to create map directory");
                }
                try (InputStream input = getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    if (input == null) {
                        throw new IOException("Unable to open selected map");
                    }
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                }
                MapFile validation = new MapFile(temporary);
                try {
                    // Opening the copied file validates the Mapsforge header.
                } finally {
                    validation.close();
                }
                if (backup.exists() && !backup.delete()) {
                    throw new IOException("Unable to remove previous map backup");
                }
                if (destination.exists() && !destination.renameTo(backup)) {
                    throw new IOException("Unable to back up current map");
                }
                if (!temporary.renameTo(destination)) {
                    if (backup.exists()) {
                        backup.renameTo(destination);
                    }
                    throw new IOException("Unable to install selected map");
                }
                if (backup.exists() && !backup.delete()) {
                    Log.w(LOG, "Unable to remove previous map backup");
                }
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException exception) {
                    Log.w(LOG, "Provider did not grant persistent map access", exception);
                }
                getSharedPreferences(MAP_PREFERENCES, MODE_PRIVATE).edit()
                        .putString(MAP_URI, uri.toString())
                        .apply();
                imported = true;
            } catch (Exception exception) {
                Log.e(LOG, "Unable to import offline map", exception);
                if (temporary.exists() && !temporary.delete()) {
                    Log.w(LOG, "Unable to remove incomplete map import");
                }
            }
            boolean result = imported;
            runOnUiThread(() -> {
                if (result) {
                    recreate();
                } else {
                    Toast.makeText(this, R.string.map_import_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void restoreOfflineMap() {
        File mapFile = new File(new File(getFilesDir(), "maps"), MAP_FILE_NAME);
        if (!mapFile.isFile()) {
            mapView.setCenter(new LatLong(0, 0));
            mapView.setZoomLevel((byte) 2);
            return;
        }
        try {
            tileCache = AndroidUtil.createTileCache(this, "mapcache",
                    mapView.getModel().displayModel.getTileSize(), 1f,
                    mapView.getModel().frameBufferModel.getOverdrawFactor());
            mapDataStore = new MapFile(mapFile);
            TileRendererLayer mapLayer = new TileRendererLayer(tileCache, mapDataStore,
                    mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
            mapLayer.setXmlRenderTheme(MapsforgeThemes.DEFAULT);
            mapView.getLayerManager().getLayers().add(mapLayer);
            findViewById(R.id.map_empty_message).setVisibility(View.GONE);

            SharedPreferences preferences = getSharedPreferences(MAP_PREFERENCES, MODE_PRIVATE);
            hasStoredMapPosition = preferences.contains(MAP_LATITUDE)
                    && preferences.contains(MAP_LONGITUDE);
            if (hasStoredMapPosition) {
                LatLong center = new LatLong(
                        Double.longBitsToDouble(preferences.getLong(MAP_LATITUDE, 0)),
                        Double.longBitsToDouble(preferences.getLong(MAP_LONGITUDE, 0)));
                byte zoom = (byte) preferences.getInt(MAP_ZOOM, 12);
                mapView.getModel().mapViewPosition.setMapPosition(new MapPosition(center, zoom));
            } else {
                mapView.setCenter(new LatLong(0, 0));
                mapView.setZoomLevel((byte) 2);
            }
        } catch (Exception exception) {
            Log.e(LOG, "Unable to open stored offline map", exception);
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
                    newLocations.add(new LatLong(point.latitude, point.longitude));
                    lastRenderedPointId = point.id;
                }
            }
            if (!newLocations.isEmpty()) {
                ensureRoutePolyline();
                routePolyline.addPoints(newLocations);
            }

            latestLocation = new LatLong(latestPoint.latitude, latestPoint.longitude);
            showLocationMarker();
            findViewById(R.id.button_recenter).setEnabled(true);
            if (!hasStoredMapPosition) {
                centerOnLatestLocation();
                hasStoredMapPosition = true;
            }
        });
    }

    private void ensureRoutePolyline() {
        if (routePolyline != null) {
            return;
        }
        Paint routePaint = AndroidGraphicFactory.INSTANCE.createPaint();
        routePaint.setColor(Color.BLUE);
        routePaint.setStyle(Style.STROKE);
        routePaint.setStrokeWidth(5 * getResources().getDisplayMetrics().density);
        routePolyline = new Polyline(routePaint, AndroidGraphicFactory.INSTANCE);
        mapView.getLayerManager().getLayers().add(routePolyline);
    }

    private void clearMapRoute() {
        lastRenderedPointId = 0;
        latestLocation = null;
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
        getSharedPreferences(MAP_PREFERENCES, MODE_PRIVATE).edit()
                .putLong(MAP_LATITUDE, Double.doubleToRawLongBits(center.latitude))
                .putLong(MAP_LONGITUDE, Double.doubleToRawLongBits(center.longitude))
                .putInt(MAP_ZOOM, mapView.getModel().mapViewPosition.getZoomLevel())
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
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
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
	Log.i(LOG, "createImagefile: " + currentImagePath);
        return image;
    }

    private void requestCameraPermissionAndTakePicture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            dispatchTakePictureIntent();
        } else {
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
                                                  "com.jatrailmap.android.fileprovider",
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
                        recordingStateStore.reset();
                        refreshUi();
                        showDialog("Information", "Trail data was sent successfully");
                        break;
                    case RESULT_CANCELED:
                        //Log.i(LOG, "onActivityResult: RESULT_CANCELED");
                        break;
                }
                break;
        }
    }
}

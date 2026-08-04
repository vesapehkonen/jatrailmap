package com.jatrailmap.justanothertrailmap;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
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
import java.lang.Thread;
import androidx.core.content.FileProvider;

public class MainActivity extends AppCompatActivity {
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
    private final String LOG = "mylog";
    private Context context;
    private final int TAKE_PICTURE = 1, TRANSFER_DATA = 2;
    private boolean trackingReceiverRegistered;
    private final BroadcastReceiver trackingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUi();
        }
    };
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
        registerTrackingReceiver();
        if (savedInstanceState != null) {
            currentImagePath = savedInstanceState.getString("currentImagePath", "");
        }
        refreshUi();
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
                intent.putExtra("locsFilename", getString(R.string.locations_filename));
                intent.putExtra("picsFilename", getString(R.string.pictures_filename));
                startActivityForResult(intent, TRANSFER_DATA);
	}

	if (id == R.id.button_delete) {
                new AlertDialog.Builder(this)
                        .setTitle("Delete entry")
                        .setMessage("Are you sure you want to delete this entry?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {

                                // Delete location and picture files
                                File file = new File(getExternalFilesDir(null),
                                        getString(R.string.locations_filename));
                                if (file.exists()) {
                                    file.delete();
                                    Log.i(LOG, getString(R.string.locations_filename) + " deleted");
                                }
                                file = new File(getExternalFilesDir(null),
                                        getString(R.string.pictures_filename));
                                if (file.exists()) {
                                    file.delete();
                                    Log.i(LOG, getString(R.string.pictures_filename) + " deleted");
                                }
                                recordingStateStore.reset();
                                refreshUi();
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
    }

    protected void onPause() {
        Log.i(LOG, "onPause()");
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
        super.onDestroy();
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

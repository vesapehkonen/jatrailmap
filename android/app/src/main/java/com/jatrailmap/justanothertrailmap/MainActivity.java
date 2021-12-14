package com.jatrailmap.justanothertrailmap;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.os.Build;
import android.Manifest;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;

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
	    private Chronometer chronometer;
	    private long timeWhenStopped;
        private boolean running;

	    public Timer(Chronometer c) {
	        chronometer = c;
            running = false;
	    }
	    public void start() {
            running = true;
	        chronometer.setBase(SystemClock.elapsedRealtime() - timeWhenStopped);
	        chronometer.start();
	    }
	    public void stop() {
            if (!running) {
                return;
            }
            running = false;
	        timeWhenStopped = SystemClock.elapsedRealtime() - chronometer.getBase();
	        chronometer.stop();
	    }
        public void init(long time) {
            running = false;
            timeWhenStopped = time;
            chronometer.setBase(SystemClock.elapsedRealtime() - timeWhenStopped);
        }
        public long get() {
            if (running) {
                return SystemClock.elapsedRealtime() - chronometer.getBase();
            } else {
                return timeWhenStopped;
            }
        }
    }

    private int delete_this = 0;
    private Timer timer;
    private final String LOG = "mylog";
    private Context context;
    private LocationTracker tracker = null;
    private int points = 0;
    private final int TAKE_PICTURE = 1, TRANSFER_DATA = 2;
    private final int STATE_INIT = 1, STATE_STOPPED = 2, STATE_TRACKING = 3;
    private int state;

    private void resumeState() {
        File file = new File(getApplicationContext().getExternalFilesDir(null),
                getString(R.string.state_filename));
        if (!file.exists()) {
            state = STATE_INIT;
            points = 0;
            timer.init(0);
            return;
        }
		try {
			InputStreamReader reader= new InputStreamReader(new FileInputStream(file));

			char[] buf= new char[100];
			String line = "";
			int bytesRead;

			while ((bytesRead = reader.read(buf, 0, 100)) > 0) {
				String readstring=String.copyValueOf(buf, 0, bytesRead);
				line += readstring;
			}
			reader.close();
            JSONObject json = new JSONObject(line);
            state = json.getInt("state");
            points = json.getInt("points");
            timer.init(json.getInt("timer"));
	    currentImagePath = json.getString("currentImagePath");
		}
        catch (Exception e) {
            Log.e(LOG, "exception", e);
   			Toast.makeText(getBaseContext(), "Exception: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
		}
        if (state == STATE_TRACKING) {
            tracker.start();
            timer.start();
        }
	}

    private void saveState() {
        try {
            File file = new File(getApplicationContext().getExternalFilesDir(null),
                    getString(R.string.state_filename));
            OutputStreamWriter outputWriter = new OutputStreamWriter(new FileOutputStream(file, false));
            outputWriter.write("{ state: \"" + state +
			       "\", points: \"" + points +
			       "\" , timer: \"" + timer.get() +
			       "\", currentImagePath: \"" + currentImagePath + "\"}");
            outputWriter.close();
        }
        catch (Exception e) {
            Log.e(LOG, "exception", e);
            Toast.makeText(getBaseContext(), "Exception: " + e.getMessage(),
                   Toast.LENGTH_LONG).show();
		}
	}

    private void updateButtons() {
	switch (state) {
        case STATE_INIT:
            ((Button) findViewById(R.id.button_start)).setText("Start tracking");
            ((Button) findViewById(R.id.button_start)).setEnabled(true);
            ((Button) findViewById(R.id.button_stop)).setEnabled(false);
            ((Button) findViewById(R.id.button_picture)).setEnabled(false);
            ((Button) findViewById(R.id.button_send)).setEnabled(false);
            ((Button) findViewById(R.id.button_delete)).setEnabled(false);
	    break;
        case STATE_STOPPED:
            ((Button) findViewById(R.id.button_start)).setText("Continue tracking");
            ((Button) findViewById(R.id.button_start)).setEnabled(true);
            ((Button) findViewById(R.id.button_stop)).setEnabled(false);
            ((Button) findViewById(R.id.button_picture)).setEnabled(false);
            ((Button) findViewById(R.id.button_send)).setEnabled(true);
            ((Button) findViewById(R.id.button_delete)).setEnabled(true);
	    break;
        case STATE_TRACKING:
            ((Button) findViewById(R.id.button_start)).setEnabled(false);
            ((Button) findViewById(R.id.button_stop)).setEnabled(true);
            ((Button) findViewById(R.id.button_picture)).setEnabled(true);
            ((Button) findViewById(R.id.button_send)).setEnabled(false);
            ((Button) findViewById(R.id.button_delete)).setEnabled(false);
	        break;
        }
	    ((TextView) findViewById(R.id.text_locs)).
	        setText(getString(R.string.points) + Integer.toString(points));
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
        File file = new File(context.getExternalFilesDir(null),
                getString(R.string.locations_filename));

        tracker = new LocationTracker(getString(R.string.locations_filename),
                getString(R.string.pictures_filename),
                super.getApplicationContext(), this);
        resumeState();
        updateButtons();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        //savedInstanceState.putLong("param", 100);
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
        switch (id) {
            case R.id.button_start:
                if (tracker.start()) {
                    state = STATE_TRACKING;
                    updateButtons();
		    timer.start();
                }
                break;

            case R.id.button_stop:
                tracker.stop();
    		    state = STATE_STOPPED;
	    	    updateButtons();
		        timer.stop();
                break;

            case R.id.button_picture:
                dispatchTakePictureIntent();
                break;

            case R.id.button_send:
                Intent intent = new Intent(this, TransferActivity.class);
                intent.putExtra("locsFilename", getString(R.string.locations_filename));
                intent.putExtra("picsFilename", getString(R.string.pictures_filename));
                startActivityForResult(intent, TRANSFER_DATA);
                break;

            case R.id.button_delete:
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
				points = 0;
				state = STATE_INIT;
				updateButtons();
				timer.init(0);
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
                break;
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

    public void onBackPressed() {
        Log.i(LOG, "onBackPressed()");
        if (state != STATE_STOPPED) {
            // because we don't want to continue tracking, when we start next time
            state = STATE_STOPPED;
            timer.stop();
        }
        super.onBackPressed();
    }

    protected void onStart() {
        Log.i(LOG, "onStart()");
        super.onStart();
    }

    protected void onRestart() {
        Log.i(LOG, "onRestart()");
        super.onRestart();
    }

    protected void onResume() {
        Log.i(LOG, "onResume()");
        super.onResume();
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
        tracker.stop();
        // don't set the state_stopped here, because if orientation caused onDestroy(),
        // we want to continue immediately when app is started again
        //state = STATE_STOPPED;
        // timer.stop();
        saveState();
        super.onDestroy();
    }

    public void locationChanged(boolean status) {
        if (status) {
            points++;
            ((TextView) findViewById(R.id.text_locs)).
                    setText(getString(R.string.points) + Integer.toString(points));
        }
        else {
	        // error, the location service doesn't work
            tracker.stop();
	        state = STATE_STOPPED;
	        updateButtons();
	        timer.stop();
        }
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

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String permission = Manifest.permission.CAMERA;
            if (ContextCompat.checkSelfPermission(super.getApplicationContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{permission}, 1);
                android.os.SystemClock.sleep(4000);
            }
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
	checkPermissions();
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
                        tracker.savePicture(currentImagePath);
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
			            timer.init(0);
                        points = 0;
			            state = STATE_INIT;
			            updateButtons();
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

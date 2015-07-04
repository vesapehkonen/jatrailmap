package com.jatrailmap.justanothertrailmap;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.content.Context;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EmptyStackException;

public class MainActivity extends AppCompatActivity {
    private final String LOG = "mylog";
    private Context context;
    private LocationTracker tracker = null;
    private Chronometer chronometer;
    private long timeWhenStopped;
    private int points = 0;
    private final int TAKE_PICTURE = 1, TRANSFER_DATA = 2;

    private void setButtons(String state) {
        if (state.equals("orig")) {
            ((Button) findViewById(R.id.button_start)).setText("Start tracking");
            ((Button) findViewById(R.id.button_start)).setEnabled(true);
            ((Button) findViewById(R.id.button_stop)).setEnabled(false);
            ((Button) findViewById(R.id.button_picture)).setEnabled(false);
            ((Button) findViewById(R.id.button_send)).setEnabled(false);
            ((Button) findViewById(R.id.button_delete)).setEnabled(false);
        } else if (state.equals("continue")) {
            ((Button) findViewById(R.id.button_start)).setText("Continue tracking");
            ((Button) findViewById(R.id.button_start)).setEnabled(true);
            ((Button) findViewById(R.id.button_stop)).setEnabled(false);
            ((Button) findViewById(R.id.button_picture)).setEnabled(false);
            ((Button) findViewById(R.id.button_send)).setEnabled(true);
            ((Button) findViewById(R.id.button_delete)).setEnabled(true);
        } else if (state.equals("tracking")) {
            ((Button) findViewById(R.id.button_start)).setEnabled(false);
            ((Button) findViewById(R.id.button_stop)).setEnabled(true);
            ((Button) findViewById(R.id.button_picture)).setEnabled(true);
            ((Button) findViewById(R.id.button_send)).setEnabled(false);
            ((Button) findViewById(R.id.button_delete)).setEnabled(false);
        }
    }

    // Starts the timer and sets states of buttons depending on the existence of the location file
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(LOG, "MainActivity: onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Context context = getApplicationContext();
        chronometer = (Chronometer) findViewById(R.id.chronometer);
        int timeWhenStopped;

        File file = new File(context.getExternalFilesDir(null),
                getString(R.string.locations_filename));
        if (file.exists() && file.length() != 0) {
            setButtons("continue");
        } else {
            setButtons("orig");
        }
        chronometer.setBase(SystemClock.elapsedRealtime());
        timeWhenStopped = 0;
        tracker = new LocationTracker(getString(R.string.locations_filename),
                getString(R.string.pictures_filename),
                super.getApplicationContext(), this);
    }

    public void onClick(View view) {
        final int id = view.getId();
        switch (id) {
            case R.id.button_start:
                if (tracker.start()) {
                    setButtons("tracking");
                    chronometer.setBase(SystemClock.elapsedRealtime() + timeWhenStopped);
                    chronometer.start();
                }
                break;

            case R.id.button_stop:
                tracker.stop();
                setButtons("continue");
                timeWhenStopped = chronometer.getBase() - SystemClock.elapsedRealtime();
                chronometer.stop();
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
                                setButtons("orig");
                                chronometer.setBase(SystemClock.elapsedRealtime());
                                timeWhenStopped = 0;

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
        tracker.stop();
        super.onBackPressed();
    }

    public void OnStop() {
        Log.i(LOG, "onStop()");
        tracker.stop();
    }

    public void locationChanged(boolean status) {
        if (status) {
            points++;
            ((TextView) findViewById(R.id.text_locs)).
                    setText(getString(R.string.points) + Integer.toString(points));
        }
        else {
            tracker.stop();
            setButtons("continue");
            timeWhenStopped = chronometer.getBase() - SystemClock.elapsedRealtime();
            chronometer.stop();
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

    private String currentImagePath;

    // Create a image file to the public picture directory
    private File createImageFile()  {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "img_" + timeStamp + ".jpg";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File image = new File(storageDir + "/" + imageFileName);

        // Save a file path for use with later
        currentImagePath = image.getAbsolutePath();
        Log.i(LOG, "createImagefile: " + currentImagePath);
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            photoFile = createImageFile();
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                    Uri.fromFile(photoFile));
            startActivityForResult(takePictureIntent, TAKE_PICTURE);
        }
        else {
            Toast.makeText(getBaseContext(), "There isn't a camera!",
                    Toast.LENGTH_SHORT).show();
            Log.w(LOG, "There isn't a camera activity to handle the intent");
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
                        setButtons("orig");
                        chronometer.setBase(SystemClock.elapsedRealtime());
                        timeWhenStopped = 0;
                        points = 0;
                        ((TextView) findViewById(R.id.text_locs)).
                                setText(getString(R.string.points) + Integer.toString(points));
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
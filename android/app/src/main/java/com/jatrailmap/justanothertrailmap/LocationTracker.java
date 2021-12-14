package com.jatrailmap.justanothertrailmap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import android.os.Build;
import android.Manifest;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.lang.Thread;

/**
 * Created by vesa on 6/25/15.
 */
public class LocationTracker implements LocationListener {
    private final String LOG = "mylog";
    private String locsFilename;
    private String picsFilename;
    private LocationManager locationManager;
    private Context context;
    private BufferedWriter writer = null;
    private MainActivity mainActivity;

    private enum State {idle, active}

    ;
    private State state = State.idle;

    public LocationTracker(String locs, String pics, Context ctx, MainActivity act) {
        locsFilename = locs;
        picsFilename = pics;
        context = ctx;
        mainActivity = act;
    }

    // Checks if external storage is available for read and write
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String permission = Manifest.permission.ACCESS_FINE_LOCATION;
	    if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(mainActivity, new String[]{permission}, 1);
                android.os.SystemClock.sleep(4000);
            }
        }
    }

    // Start to get GPS coordinates
    public boolean start() {
        Log.i(LOG, "LocationTracker: start");
        if (!isExternalStorageWritable()) {
            Toast.makeText(context, "Unable to write external storage",
                    Toast.LENGTH_LONG).show();
            Log.w(LOG, "Unable to write external storage");
            return false;
        }
	checkPermissions();
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        // Request coordinates on every 20 seconds and if location changes least 20 meters
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 20000, 20, this);
        } catch (SecurityException ex) {
            Log.e(LOG, "Fail to request location update: " + ex.getMessage());
            Toast.makeText(context, new String("Fail to request location update: " + ex.getMessage()),
                           Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException ex) {
            Log.e(LOG, "Location provider does not exist: " + ex.getMessage());
            Toast.makeText(context, new String("Location provider does not exist: " + ex.getMessage()),
                           Toast.LENGTH_LONG).show();
        }
        state = State.active;
        return true;
    }

    // Cancel gps information and close the locations file
    public void stop() {
        if (state == State.active) {
            Log.i(LOG, "LocationTracker: stop");
            state = State.idle;
            locationManager.removeUpdates(this);
            if (writer != null) {
                try {
                    Log.d(LOG, "close file " + locsFilename );
                    writer.close();
                } catch (IOException e) {
                    Log.e(LOG, "exception", e);
                    Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                }
                writer = null;
            }
        }
    }

    // Gets gps coordinates of the photo and writes photo information to the file
    public void savePicture(String imagePath) {
        String line;
        if (!isExternalStorageWritable()) {
            Toast.makeText(context, "Unable to write external storage",
                    Toast.LENGTH_LONG).show();
            Log.w(LOG, "Unable to write external storage");
            return;
        }
	Location loc = null;
	try {
	    loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (SecurityException ex) {
            Log.e(LOG, "Fail to request location update: " + ex.getMessage());
            Toast.makeText(context, new String("Fail to request location update: " + ex.getMessage()),
                           Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException ex) {
            Log.e(LOG, "Location provider does not exist: " + ex.getMessage());
            Toast.makeText(context, new String("Location provider does not exist: " + ex.getMessage()),
                           Toast.LENGTH_LONG).show();
        }
        if (loc == null) {
            loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) {
                Toast.makeText(context, "Location data is not available.",
                        Toast.LENGTH_LONG).show();
                Log.w(LOG, "Location data is not available.");
                return;
            }
        }
        File file = new File(context.getExternalFilesDir(null), picsFilename);

        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter
                    (new FileOutputStream(file, true), "UTF-8"));

            line = "{\"imagepath\":\"" + imagePath +
                    "\",\"timestamp\":\"" + Iso8061DateTime.get() +
                    "\",\"loc\":{\"type\":\"Point\",\"coordinates\":[" + loc.getLongitude() +
                    "," + loc.getLatitude() + "," + loc.getAltitude() + "]}}\n";
            writer.write(line);
            writer.close();
            Log.d(LOG, "write to " + picsFilename + ": " + line);
        } catch (IOException e) {
            Log.e(LOG, "exception", e);
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // Get gps coordinates and writes them to the file
    @Override
    public void onLocationChanged(Location loc) {
        String line;
        line = "Latitude: " + loc.getLatitude()
                + " Longitude: " + loc.getLongitude()
                + " Altitude: " + loc.getAltitude();
        Log.i(LOG, line);
        //Toast.makeText(context, line, Toast.LENGTH_SHORT).show();

        mainActivity.locationChanged(true);
        boolean addComma = true;
        // write location point in one line and add comma, if it isn't first line
        try {
            if (writer == null) {
                File file = new File(context.getExternalFilesDir(null), locsFilename);
                Log.d(LOG, "open file " + locsFilename );
                writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
                if (file.length() == 0) {
                    addComma = false;
                }
            }
            if (addComma) {
                writer.write(",\n");
                Log.d(LOG, "write to " + locsFilename + ": " + ",\n");
            }
            line = "{\"timestamp\":\"" + Iso8061DateTime.get() +
                    "\",\"loc\":{\"type\":\"Point\",\"coordinates\":[" +
                    loc.getLongitude() + "," + loc.getLatitude() + "," +
                    loc.getAltitude() + "]}}";
            writer.write(line);
            Log.d(LOG, "write to " + locsFilename + ": " + line);
        } catch (IOException e) {
            Log.e(LOG, "exeption", e);
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.i(LOG, "onProviderEnabled");
        Toast.makeText(context, "Gps is turned on!",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderDisabled(String provider) {
        // Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        // context.startActivity(intent);
        stop();
        Log.w(LOG, "onProviderDisabled");
        Toast.makeText(context, "Gps is turned off!",
                Toast.LENGTH_LONG).show();
        mainActivity.locationChanged(false);
    }
}

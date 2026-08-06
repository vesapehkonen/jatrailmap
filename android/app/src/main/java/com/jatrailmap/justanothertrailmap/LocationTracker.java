package com.jatrailmap.justanothertrailmap;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

/**
 * Created by vesa on 6/25/15.
 */
public class LocationTracker implements LocationListener {
    public interface Listener {
        void onLocationRecorded(Location location);
        void onPictureRecorded(String imagePath, Location location);
        void onTrackingStopped();
    }

    private final String LOG = "mylog";
    private LocationManager locationManager;
    private Context context;
    private Listener listener;
    private RecordingSettings settings;
    private Location lastRecordedLocation;

    private enum State {idle, active}

    ;
    private State state = State.idle;

    public LocationTracker(Context ctx, Listener listener) {
        context = ctx;
        this.listener = listener;
    }

    // Start to get GPS coordinates
    public boolean start() {
        Log.i(LOG, "LocationTracker: start");
        settings = RecordingSettings.load(context);
        DiagnosticLog.event(context, "LOCATION", "START_REQUESTED",
                "intervalMs=" + settings.intervalMs()
                        + " minDistanceM=" + settings.minimumDistanceMeters
                        + " maxAccuracyM=" + settings.maximumAccuracyMeters
                        + " stationary=" + settings.recordStationary);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(LOG, "Location permission is not granted");
            DiagnosticLog.event(context, "LOCATION", "START_REJECTED",
                    "reason=permission_missing");
            return false;
        }
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, settings.intervalMs(), 0, this);
        } catch (SecurityException ex) {
            Log.e(LOG, "Fail to request location update: " + ex.getMessage());
            DiagnosticLog.error(context, "LOCATION", "UPDATE_REQUEST_FAILED", ex);
            Toast.makeText(context, "Fail to request location update: " + ex.getMessage(),
                           Toast.LENGTH_LONG).show();
            return false;
        } catch (IllegalArgumentException ex) {
            Log.e(LOG, "Location provider does not exist: " + ex.getMessage());
            DiagnosticLog.error(context, "LOCATION", "GPS_PROVIDER_UNAVAILABLE", ex);
            Toast.makeText(context, "Location provider does not exist: " + ex.getMessage(),
                           Toast.LENGTH_LONG).show();
            return false;
        }
        state = State.active;
        lastRecordedLocation = null;
        DiagnosticLog.event(context, "LOCATION", "UPDATES_REGISTERED",
                "provider=gps");
        return true;
    }

    // Cancel gps information and close the locations file
    public void stop() {
        if (state == State.active) {
            Log.i(LOG, "LocationTracker: stop");
            DiagnosticLog.event(context, "LOCATION", "UPDATES_REMOVED");
            state = State.idle;
            locationManager.removeUpdates(this);
        }
    }

    // Gets gps coordinates of the photo and writes photo information to the file
    public void savePicture(String imagePath) {
	Location loc = null;
	try {
	    loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) {
                loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException ex) {
            Log.e(LOG, "Fail to request location update: " + ex.getMessage());
            DiagnosticLog.error(context, "PHOTO", "LAST_LOCATION_DENIED", ex);
            Toast.makeText(context, new String("Fail to request location update: " + ex.getMessage()),
                           Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException ex) {
            Log.e(LOG, "Location provider does not exist: " + ex.getMessage());
            DiagnosticLog.error(context, "PHOTO", "LAST_LOCATION_UNAVAILABLE", ex);
            Toast.makeText(context, new String("Location provider does not exist: " + ex.getMessage()),
                           Toast.LENGTH_LONG).show();
        }
        if (loc == null) {
            Toast.makeText(context, "Location data is not available.",
                    Toast.LENGTH_LONG).show();
            Log.w(LOG, "Location data is not available.");
            DiagnosticLog.event(context, "PHOTO", "NOT_RECORDED",
                    "reason=location_unavailable");
            return;
        }
        DiagnosticLog.event(context, "PHOTO", "LOCATION_AVAILABLE",
                locationDetails(loc));
        listener.onPictureRecorded(imagePath, loc);
    }

    // Get gps coordinates and writes them to the file
    @Override
    public void onLocationChanged(Location loc) {
        DiagnosticLog.event(context, "LOCATION", "FIX_RECEIVED", locationDetails(loc));
        String rejectionReason = rejectionReason(loc, lastRecordedLocation, settings);
        if (rejectionReason != null) {
            DiagnosticLog.event(context, "LOCATION", "FIX_REJECTED",
                    "reason=" + rejectionReason + " " + locationDetails(loc));
            return;
        }
        lastRecordedLocation = new Location(loc);
        listener.onLocationRecorded(loc);
    }

    static boolean shouldRecord(Location location, Location previous,
                                RecordingSettings settings) {
        return rejectionReason(location, previous, settings) == null;
    }

    private static String rejectionReason(Location location, Location previous,
                                          RecordingSettings settings) {
        if (settings == null) {
            return null;
        }
        if (settings.maximumAccuracyMeters > 0) {
            if (!location.hasAccuracy()) {
                return "accuracy_unavailable";
            }
            if (location.getAccuracy() > settings.maximumAccuracyMeters) {
                return "accuracy_too_low";
            }
        }
        if (previous == null) {
            return null;
        }
        long elapsedMs = elapsedMs(previous, location);
        if (elapsedMs >= 0 && elapsedMs < settings.intervalMs()) {
            return "interval_not_reached";
        }
        if (!settings.recordStationary
                && settings.minimumDistanceMeters > 0
                && previous.distanceTo(location) < settings.minimumDistanceMeters) {
            return "movement_too_small";
        }
        return null;
    }

    private static long elapsedMs(Location previous, Location current) {
        long previousNanos = previous.getElapsedRealtimeNanos();
        long currentNanos = current.getElapsedRealtimeNanos();
        if (currentNanos > 0 && currentNanos >= previousNanos) {
            return (currentNanos - previousNanos) / 1_000_000L;
        }
        if (previous.getTime() > 0 && current.getTime() >= previous.getTime()) {
            return current.getTime() - previous.getTime();
        }
        return -1;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.i(LOG, "onProviderEnabled");
        DiagnosticLog.event(context, "LOCATION", "PROVIDER_ENABLED",
                "provider=" + provider);
        Toast.makeText(context, "Gps is turned on!",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderDisabled(String provider) {
        // Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        // context.startActivity(intent);
        stop();
        Log.w(LOG, "onProviderDisabled");
        DiagnosticLog.event(context, "LOCATION", "PROVIDER_DISABLED",
                "provider=" + provider);
        Toast.makeText(context, "Gps is turned off!",
                Toast.LENGTH_LONG).show();
        listener.onTrackingStopped();
    }

    private String locationDetails(Location location) {
        String provider = location.getProvider() == null ? "unknown" : location.getProvider();
        return "provider=" + provider
                + " hasAccuracy=" + location.hasAccuracy()
                + (location.hasAccuracy() ? " accuracyM=" + Math.round(location.getAccuracy()) : "")
                + " hasAltitude=" + location.hasAltitude();
    }
}

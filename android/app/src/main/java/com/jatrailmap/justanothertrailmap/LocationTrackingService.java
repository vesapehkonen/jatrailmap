package com.jatrailmap.justanothertrailmap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

public class LocationTrackingService extends Service implements LocationTracker.Listener {
    public static final String ACTION_START =
            "com.jatrailmap.justanothertrailmap.action.START_TRACKING";
    public static final String ACTION_STOP =
            "com.jatrailmap.justanothertrailmap.action.STOP_TRACKING";
    public static final String ACTION_SAVE_PICTURE =
            "com.jatrailmap.justanothertrailmap.action.SAVE_PICTURE";
    public static final String ACTION_LOCATION_RECORDED =
            "com.jatrailmap.justanothertrailmap.action.LOCATION_RECORDED";
    public static final String ACTION_TRACKING_STOPPED =
            "com.jatrailmap.justanothertrailmap.action.TRACKING_STOPPED";
    public static final String EXTRA_IMAGE_PATH = "imagePath";

    private static final String LOG = "mylog";
    private static final String CHANNEL_ID = "trail_tracking";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFERENCES = "location_tracking_service";
    private static final String TRACKING_REQUESTED = "tracking_requested";

    private LocationTracker tracker;
    private boolean tracking;

    public static boolean isTrackingRequested(Context context) {
        return context.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean(TRACKING_REQUESTED, false);
    }

    private void setTrackingRequested(boolean requested) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(TRACKING_REQUESTED, requested)
                .apply();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        tracker = new LocationTracker(
                getString(R.string.locations_filename),
                getString(R.string.pictures_filename),
                getApplicationContext(),
                this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_START.equals(intent.getAction())) {
            startTracking();
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopTracking();
        } else if (ACTION_SAVE_PICTURE.equals(intent.getAction())) {
            String imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH);
            if (tracking && imagePath != null) {
                tracker.savePicture(imagePath);
            } else {
                stopSelf(startId);
            }
        }
        return tracking ? START_STICKY : START_NOT_STICKY;
    }

    private void startTracking() {
        if (tracking) {
            return;
        }

        int foregroundServiceType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                : 0;
        ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                foregroundServiceType);

        if (tracker.start()) {
            tracking = true;
            setTrackingRequested(true);
            Log.i(LOG, "Location tracking service started");
        } else {
            setTrackingRequested(false);
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            stopSelf();
            broadcast(ACTION_TRACKING_STOPPED);
        }
    }

    private void stopTracking() {
        tracker.stop();
        tracking = false;
        setTrackingRequested(false);
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
        broadcast(ACTION_TRACKING_STOPPED);
        Log.i(LOG, "Location tracking service stopped");
    }

    private Notification buildNotification() {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openApp = PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, LocationTrackingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopTracking = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.tracking_notification_title))
                .setContentText(getString(R.string.tracking_notification_text))
                .setContentIntent(openApp)
                .addAction(0, getString(R.string.stop_tracking), stopTracking)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.tracking_notification_channel),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.tracking_notification_channel_description));
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void broadcast(String action) {
        Intent intent = new Intent(action);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public void onLocationRecorded() {
        broadcast(ACTION_LOCATION_RECORDED);
    }

    @Override
    public void onTrackingStopped() {
        tracking = false;
        setTrackingRequested(false);
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
        broadcast(ACTION_TRACKING_STOPPED);
    }

    @Override
    public void onDestroy() {
        tracker.stop();
        tracking = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

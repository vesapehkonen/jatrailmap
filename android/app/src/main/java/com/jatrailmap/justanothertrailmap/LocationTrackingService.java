package com.jatrailmap.justanothertrailmap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
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
    public static final String ACTION_GPS_SEARCHING =
            "com.jatrailmap.justanothertrailmap.action.GPS_SEARCHING";
    public static final String EXTRA_IMAGE_PATH = "imagePath";
    public static final String EXTRA_HAS_ACCURACY = "hasAccuracy";
    public static final String EXTRA_ACCURACY_METERS = "accuracyMeters";

    private static final String LOG = "mylog";
    private static final String CHANNEL_ID = "trail_tracking";
    private static final int NOTIFICATION_ID = 1;

    private LocationTracker tracker;
    private RecordingStateStore recordingStateStore;
    private TrailRepository trailRepository;
    private boolean tracking;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        recordingStateStore = new RecordingStateStore(this);
        trailRepository = new TrailRepository(this);
        tracker = new LocationTracker(getApplicationContext(), this);
        RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
        DiagnosticLog.event(this, "SERVICE", "CREATED",
                "status=" + snapshot.status.name()
                        + " session=" + snapshot.sessionId
                        + " points=" + snapshot.points);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        DiagnosticLog.event(this, "SERVICE", "COMMAND_RECEIVED",
                "action=" + safeAction(intent)
                        + " startId=" + startId
                        + " tracking=" + tracking);
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
            DiagnosticLog.event(this, "SERVICE", "START_IGNORED",
                    "reason=already_tracking");
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
            recordingStateStore.startTracking();
            broadcast(ACTION_GPS_SEARCHING);
            Log.i(LOG, "Location tracking service started");
            RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
            DiagnosticLog.event(this, "SERVICE", "FOREGROUND_TRACKING_STARTED",
                    "session=" + snapshot.sessionId + " points=" + snapshot.points);
        } else {
            recordingStateStore.stopTracking();
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            stopSelf();
            broadcast(ACTION_TRACKING_STOPPED);
            DiagnosticLog.event(this, "SERVICE", "TRACKING_START_FAILED");
        }
    }

    private void stopTracking() {
        RecordingStateStore.Snapshot beforeStop = recordingStateStore.getSnapshot();
        tracker.stop();
        tracking = false;
        trailRepository.updateDurationAsync(
                beforeStop.activeTrailId, beforeStop.elapsedTimeMs);
        recordingStateStore.stopTracking();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
        broadcast(ACTION_TRACKING_STOPPED);
        Log.i(LOG, "Location tracking service stopped");
        DiagnosticLog.event(this, "SERVICE", "TRACKING_STOPPED",
                "session=" + beforeStop.sessionId + " points=" + beforeStop.points);
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
    public void onLocationRecorded(Location location) {
        RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
        DiagnosticLog.event(this, "SERVICE", "POINT_WRITE_REQUESTED",
                "session=" + snapshot.sessionId + " trail=" + snapshot.activeTrailId);
        TrailPointEntity point = new TrailPointEntity(
                snapshot.activeTrailId,
                Iso8061DateTime.get(),
                location.getLongitude(),
                location.getLatitude(),
                location.getAltitude());
        trailRepository.insertPoint(point, () -> {
            recordingStateStore.recordLocation();
            RecordingStateStore.Snapshot stored = recordingStateStore.getSnapshot();
            DiagnosticLog.event(this, "DATABASE", "POINT_STORED",
                    "session=" + stored.sessionId + " points=" + stored.points);
            broadcastLocation(location);
        });
    }

    @Override
    public void onPictureRecorded(String imagePath, Location location) {
        RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
        DiagnosticLog.event(this, "SERVICE", "PHOTO_WRITE_REQUESTED",
                "session=" + snapshot.sessionId + " trail=" + snapshot.activeTrailId);
        trailRepository.insertPhoto(new TrailPhotoEntity(
                snapshot.activeTrailId,
                imagePath,
                Iso8061DateTime.get(),
                location.getLongitude(),
                location.getLatitude(),
                location.getAltitude()));
    }

    @Override
    public void onTrackingStopped() {
        DiagnosticLog.event(this, "SERVICE", "TRACKER_STOP_CALLBACK");
        RecordingStateStore.Snapshot snapshot = recordingStateStore.getSnapshot();
        tracking = false;
        trailRepository.updateDurationAsync(snapshot.activeTrailId, snapshot.elapsedTimeMs);
        recordingStateStore.stopTracking();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
        broadcast(ACTION_TRACKING_STOPPED);
    }

    @Override
    public void onDestroy() {
        RecordingStateStore.Snapshot snapshot = recordingStateStore == null
                ? null : recordingStateStore.getSnapshot();
        DiagnosticLog.event(this, "SERVICE", "DESTROYED",
                snapshot == null ? "state=unavailable"
                        : "status=" + snapshot.status.name()
                        + " session=" + snapshot.sessionId
                        + " points=" + snapshot.points);
        tracker.stop();
        tracking = false;
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        DiagnosticLog.event(this, "SERVICE", "TASK_REMOVED",
                "tracking=" + tracking);
        super.onTaskRemoved(rootIntent);
    }

    private void broadcastLocation(Location location) {
        sendBroadcast(createLocationRecordedIntent(getPackageName(), location));
    }

    static Intent createLocationRecordedIntent(String packageName, Location location) {
        Intent intent = new Intent(ACTION_LOCATION_RECORDED);
        intent.setPackage(packageName);
        intent.putExtra(EXTRA_HAS_ACCURACY, location.hasAccuracy());
        if (location.hasAccuracy()) {
            intent.putExtra(EXTRA_ACCURACY_METERS, location.getAccuracy());
        }
        return intent;
    }

    private static String safeAction(Intent intent) {
        if (intent == null) {
            return "system_restart";
        }
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            return "start";
        }
        if (ACTION_STOP.equals(action)) {
            return "stop";
        }
        if (ACTION_SAVE_PICTURE.equals(action)) {
            return "save_picture";
        }
        return action == null ? "none" : "other";
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

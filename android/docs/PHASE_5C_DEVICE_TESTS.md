# Phase 5C physical-device validation

Run these cases on a real phone with a debug or release build. Before each case, note the phone
model, Android version, application version, and whether battery optimization is enabled for the
application.

The action-bar overflow menu contains **Export diagnostics**. Export immediately after a failure
and keep the generated text file with the test result. The file contains lifecycle, permission,
GPS accuracy, database, map, and upload events. It intentionally excludes coordinates,
credentials, server URLs, trail names, and photo paths.

Live diagnostics can also be viewed from a connected computer:

```bash
adb logcat -v time JaTrail:I '*:S'
```

## Tracking and lifecycle

### 5C-T01 — First location permission request

1. Clear the application's storage or install it for the first time.
2. Tap **Start recording**.
3. Deny precise location permission.
4. Tap **Start recording** again and grant precise location permission.

Expected: denial does not start recording; granting permission starts the foreground service and
shows the tracking notification. Diagnostics contain `LOCATION_RESULT fine=false`, followed by
`LOCATION_RESULT fine=true` and `FOREGROUND_TRACKING_STARTED`.

### 5C-T02 — Notification permission denial

1. On Android 13 or newer, clear application storage.
2. Grant location but deny notification permission when recording starts.
3. Confirm Android still reports the active foreground service in its system UI.
4. Stop recording from the application.

Expected: recording remains controllable. Diagnostics contain `NOTIFICATION_RESULT granted=false`,
`FOREGROUND_TRACKING_STARTED`, and `TRACKING_STOPPED`.

### 5C-T03 — Screen-off background recording

1. Start outdoors and wait for **GPS fix**.
2. Note the point count, turn the screen off, and walk for at least ten minutes and 300 metres.
3. Unlock the phone and stop recording.

Expected: the foreground notification remains present, the point count increases, and the route
contains the screen-off section. The exported log contains repeated `FIX_RECEIVED`,
`POINT_WRITE_REQUESTED`, and `POINT_STORED` events during the screen-off interval.

### 5C-T04 — Remove application from Recents

1. Start recording and wait for at least three stored points.
2. Remove the application task from the Recents screen without using **Force stop**.
3. Continue walking for five minutes, then reopen the application.

Expected: the foreground service continues, the notification stays visible, and the restored UI
shows the same session with additional points. `TASK_REMOVED` should be followed by later
`POINT_STORED` events.

### 5C-T05 — Process recreation

1. Start recording and put the application in the background.
2. With ADB, run `adb shell am kill com.jatrail`.
3. Wait up to one minute, walk, and reopen the application.

Expected: Android recreates the sticky foreground service when permitted, or reopening the app
requests restoration. The session identifier remains unchanged. Look for `PROCESS_CREATED`,
`COMMAND_RECEIVED action=system_restart` or `RESTORE_TRACKING_REQUESTED`, and subsequent stored
points.

`adb shell am force-stop com.jatrail` is intentionally not this test: Android force-stop blocks
the application and its service until the user launches it again.

### 5C-T06 — GPS disabled and recovered

1. Start recording and obtain a fix.
2. Disable device location services.
3. Confirm the application changes to paused/stopped.
4. Re-enable location services and tap **Continue recording**.

Expected: disabling GPS stops cleanly instead of leaving a false active state. Continuing uses the
same session identifier and stores new points after GPS returns.

### 5C-T07 — Stationary behavior

1. Start recording with a GPS fix and leave the phone stationary outdoors for five minutes.
2. Compare point counts before and after.

Expected: the app requests updates with a 20-second and 20-metre threshold, so stationary points
are not deliberately added. GPS drift may occasionally cross the distance threshold. The log
shows every fix delivered by Android and every resulting stored point.

### 5C-T08 — Weak GPS accuracy

1. Continue recording while moving from open sky into an area with weak reception.
2. Observe the accuracy label and route.

Expected: the UI changes between strong, good, and weak accuracy states. The current recording
policy stores every GPS update delivered by Android; the log's `accuracyM` values allow any route
spikes to be evaluated before introducing a filtering policy.

## Upload durability

### 5C-U01 — Offline queue and recovery

1. Stop a trail with several points.
2. Enable airplane mode and submit the upload.
3. Close the application, wait one minute, disable airplane mode, and reopen it.

Expected: upload remains pending while offline and WorkManager later retries. On success, the
active recording state resets, while the trail and its route remain in **Trail history** with the
**Uploaded** state. Look for `QUEUED`, `WORK_STARTED`, `RETRY_SCHEDULED`, and eventually
`SUCCEEDED`.

### 5C-U02 — Server rejection

1. Submit using valid HTTPS transport but credentials the server will reject.

Expected: the upload fails without deleting the trail, the UI returns to the stopped state, and a
later corrected upload can be queued. Diagnostics must not contain the username, password, or URL.

### 5C-U03 — Process death during upload

1. Queue an upload on a slow or temporarily unavailable connection.
2. Remove the task from Recents or run `adb shell am kill com.jatrail`.
3. Restore connectivity.

Expected: WorkManager finishes or retries independently of the activity. Reopening the app reflects
the persisted upload/recording state.

## Maps, upgrade, and release

### 5C-M01 — Large offline map

1. Import the largest map expected in normal use.
2. Pan and zoom repeatedly, begin recording, and return to the map after screen-off tracking.

Expected: import completes without a partial `.importing` file becoming selectable, map rendering
remains responsive, and recording continues while tiles render.

### 5C-R01 — Upgrade with an unfinished trail

1. Install the previous build, record and stop a trail, then install the new APK over it without
   clearing data.
2. Open the new build.

Expected: recording state, point count, route, photos, selected map, and map position are retained.

### 5C-R02 — Release smoke test

1. Install the signed release APK on a clean device.
2. Run 5C-T03, take a photo, reopen the app, and run 5C-U01.

Expected: release behavior matches debug behavior and diagnostics can be exported through the
system share sheet.

## Result template

```text
Test: 5C-T03
Result: PASS / FAIL
Phone and Android:
Battery optimization:
Steps that differed:
Expected:
Observed:
Point count before/after:
Attachments: screenshot, diagnostics export, optional logcat
```

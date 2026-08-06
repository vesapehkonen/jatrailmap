package com.jatrail;

import android.content.Context;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TrailUploadReconciler {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private TrailUploadReconciler() {
    }

    public static void reconcile(Context context) {
        Context applicationContext = context.getApplicationContext();
        EXECUTOR.execute(() -> reconcileOnBackgroundThread(applicationContext));
    }

    private static void reconcileOnBackgroundThread(Context context) {
        TrailRepository repository = new TrailRepository(context);
        try {
            WorkManager workManager = WorkManager.getInstance(context);
            for (TrailEntity trail : repository.getPendingUploads()) {
                WorkInfo workInfo = workInfo(workManager, trail.uploadToken);
                if (workInfo == null
                        || workInfo.getState() == WorkInfo.State.FAILED
                        || workInfo.getState() == WorkInfo.State.CANCELLED) {
                    repository.markPendingUploadInterrupted(
                            trail.id, "Upload was interrupted");
                    DiagnosticLog.event(context, "UPLOAD", "INTERRUPTED_WORK_RECOVERED",
                            "trail=" + trail.id);
                } else if (workInfo.getState() == WorkInfo.State.SUCCEEDED
                        && trail.uploadToken != null) {
                    repository.markUploadSucceeded(trail.id, trail.uploadToken);
                }
            }
        } catch (Exception exception) {
            DiagnosticLog.error(context, "UPLOAD", "RECONCILIATION_FAILED", exception);
        }
    }

    private static WorkInfo workInfo(WorkManager workManager, String token) throws Exception {
        if (token == null) {
            return null;
        }
        try {
            return workManager.getWorkInfoById(UUID.fromString(token)).get();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

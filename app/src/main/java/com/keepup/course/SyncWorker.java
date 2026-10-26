package com.keepup.course;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class SyncWorker extends androidx.work.Worker {
    public SyncWorker(Context context, androidx.work.WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        try {
            SyncEngine.sync(getApplicationContext(), null);
            return Result.success();
        } catch (Http.AuthException e) {
            Notifications.authExpired(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            Prefs.setLastError(getApplicationContext(), e.getMessage());
            return Result.retry();
        }
    }

    public static void schedulePeriodic(Context c) {
        int minutes = Math.max(15, Prefs.interval(c));
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(SyncWorker.class, minutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    public static void scheduleOnce(Context c) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(c).enqueueUniqueWork("sync_once", ExistingWorkPolicy.REPLACE, req);
    }
}

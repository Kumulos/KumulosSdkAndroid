package com.kumulos.android;

import android.app.Application;
import android.content.Context;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class InAppSyncWorker extends Worker {

    private static final String TAG = InAppSyncWorker.class.getName();

    static void startPeriodicFetches(@NonNull final Context context) {
        Constraints taskConstraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        final PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(InAppSyncWorker.class,
                1, TimeUnit.HOURS,
                1, TimeUnit.HOURS)
                .setConstraints(taskConstraints)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build();

        Kumulos.executorService.submit(new Runnable() {
            @Override
            public void run() {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, workRequest);
            }
        });
    }

    static void cancelPeriodicFetches(Application application) {
        Kumulos.executorService.submit(new Runnable() {
            @Override
            public void run() {
                WorkManager.getInstance(application).cancelUniqueWork(TAG);
            }
        });
    }

    public InAppSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean success = InAppMessageService.fetch(getApplicationContext(), false);

        if (!success) {
            return Result.retry();
        }

        return Result.success();
    }
}

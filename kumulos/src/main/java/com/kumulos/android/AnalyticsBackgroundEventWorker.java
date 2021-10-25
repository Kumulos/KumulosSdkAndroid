package com.kumulos.android;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class AnalyticsBackgroundEventWorker extends Worker {
    static final String TAG = AnalyticsBackgroundEventWorker.class.getName();
    static final String EXTRAS_KEY_TIMESTAMP = "ts";

    public AnalyticsBackgroundEventWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data extras = getInputData();

        long ts = extras.getLong(EXTRAS_KEY_TIMESTAMP, System.currentTimeMillis());

        Runnable trackingTask = new AnalyticsContract.TrackEventRunnable(getApplicationContext(), AnalyticsContract.EVENT_TYPE_BACKGROUND, ts, null, false);
        Kumulos.executorService.submit(trackingTask);
        SessionHelper.startNewSession.set(true);

        return Result.success();
    }
}

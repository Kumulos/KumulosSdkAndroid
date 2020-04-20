package com.kumulos.android;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class AnalyticsUploadWorker extends Worker {
    static final String TAG = AnalyticsUploadWorker.class.getName();

    public AnalyticsUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AnalyticsUploadHelper helper = new AnalyticsUploadHelper();
        AnalyticsUploadHelper.Result result = helper.flushEvents(getApplicationContext());

        if (result == AnalyticsUploadHelper.Result.FAILED_RETRY_LATER) {
            return Result.retry();
        }

        return Result.success();
    }
}

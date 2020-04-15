package com.kumulos.android;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import org.acra.ReportField;
import org.acra.config.CoreConfigurationBuilder;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class AnalyticsBackgroundEventWorker extends Worker {
    static final String TAG = AnalyticsBackgroundEventWorker.class.getName();
    static final String EXTRAS_KEY_TIMESTAMP = "ts";
    static final String EXTRAS_KEY_CONFIG = "config";

    public AnalyticsBackgroundEventWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data extras = getInputData();
//        Bundle extras = taskParams.getExtras();

        if (!Kumulos.isInitialized()) {
            // TODO - necessary?
//            Bundle configBundle = extras.getBundle(EXTRAS_KEY_CONFIG);
//
//            if (null == configBundle) {
//                return GcmNetworkManager.RESULT_FAILURE;
//            }
//
//            KumulosConfig config = KumulosConfig.fromBundle(configBundle);
//
//            Application application = getApplication();
//
//            if (config.crashReportingEnabled()) {
//                CoreConfigurationBuilder acraBuilder = config.getAcraConfigBuilder(application);
//                acraBuilder.setReportField(ReportField.USER_EMAIL, false);
//                acraBuilder.setReportField(ReportField.LOGCAT, false);
//            }
//
//            Kumulos.initialize(application, config);
        }

        long ts = extras.getLong(EXTRAS_KEY_TIMESTAMP, System.currentTimeMillis());

        Runnable trackingTask = new AnalyticsContract.TrackEventRunnable(getApplicationContext(), AnalyticsContract.EVENT_TYPE_BACKGROUND, ts, null, false);
        Kumulos.executorService.submit(trackingTask);
        AnalyticsContract.ForegroundStateWatcher.startNewSession.set(true);

        return Result.success();
    }
}

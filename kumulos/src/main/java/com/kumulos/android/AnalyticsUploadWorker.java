package com.kumulos.android;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class AnalyticsUploadWorker extends Worker {
    static final String TAG = AnalyticsUploadWorker.class.getName();
    /** package */ static final String KEY_CONFIG = "config";

    public AnalyticsUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // TODO - fix if still relevant for init
//        if (!Kumulos.isInitialized()) {
//            Data input = getInputData();
//
//
////            Bundle bundle = taskParams.getExtras();
////            Bundle configBundle = bundle.getBundle(KEY_CONFIG);
//            Bundle configBundle = null;
//
//            if (null == configBundle) {
//                return Result.failure();
//            }
//
//            KumulosConfig config = KumulosConfig.fromBundle(configBundle);
//            Application application = (Application) this.getApplicationContext();
//            Kumulos.initialize(application, config);
//        }

        AnalyticsUploadHelper helper = new AnalyticsUploadHelper();
        AnalyticsUploadHelper.Result result = helper.flushEvents(getApplicationContext());

        if (result == AnalyticsUploadHelper.Result.FAILED_RETRY_LATER) {
            return Result.retry();
        }

        return Result.success();
    }
}

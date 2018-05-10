package com.kumulos.android;

import android.os.Bundle;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

public class AnalyticsUploadService extends GcmTaskService {

    static final String TAG = AnalyticsUploadService.class.getName();
    /** package */ static final String KEY_CONFIG = "config";

    @Override
    public int onRunTask(TaskParams taskParams) {
        if (!Kumulos.isInitialized()) {
            Bundle bundle = taskParams.getExtras();
            Bundle configBundle = bundle.getBundle(KEY_CONFIG);

            KumulosConfig config = KumulosConfig.fromBundle(configBundle);
            Kumulos.initialize(this.getApplication(), config);
        }

        AnalyticsUploadHelper helper = new AnalyticsUploadHelper();
        AnalyticsUploadHelper.Result result = helper.flushEvents(this);

        if (result == AnalyticsUploadHelper.Result.FAILED_RETRY_LATER) {
            return GcmNetworkManager.RESULT_RESCHEDULE;
        }

        return GcmNetworkManager.RESULT_SUCCESS;
    }

}

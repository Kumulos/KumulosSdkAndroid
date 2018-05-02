package com.kumulos.android;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

public class AnalyticsUploadService extends GcmTaskService {

    static final String TAG = AnalyticsUploadService.class.getName();

    @Override
    public int onRunTask(TaskParams taskParams) {

        AnalyticsUploadHelper helper = new AnalyticsUploadHelper();
        AnalyticsUploadHelper.Result result = helper.flushEvents(this);

        if (result == AnalyticsUploadHelper.Result.FAILED_RETRY_LATER) {
            return GcmNetworkManager.RESULT_RESCHEDULE;
        }

        return GcmNetworkManager.RESULT_SUCCESS;
    }

}

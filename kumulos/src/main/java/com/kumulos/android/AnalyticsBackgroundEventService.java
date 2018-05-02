package com.kumulos.android;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

public class AnalyticsBackgroundEventService extends GcmTaskService {

    static final String TAG = AnalyticsBackgroundEventService.class.getName();
    static final String EXTRAS_KEY_TIMESTAMP = "ts";

    @Override
    public int onRunTask(TaskParams taskParams) {
        long ts = taskParams.getExtras().getLong(EXTRAS_KEY_TIMESTAMP);

        Runnable trackingTask = new AnalyticsContract.TrackEventRunnable(this, AnalyticsContract.EVENT_TYPE_BACKGROUND, ts, null, false);
        Kumulos.executorService.submit(trackingTask);
        AnalyticsContract.ForegroundStateWatcher.startNewSession.set(true);

        return GcmNetworkManager.RESULT_SUCCESS;
    }
}

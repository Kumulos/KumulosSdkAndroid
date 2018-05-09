package com.kumulos.android;

import android.os.Bundle;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

public class AnalyticsBackgroundEventService extends GcmTaskService {

    static final String TAG = AnalyticsBackgroundEventService.class.getName();
    static final String EXTRAS_KEY_TIMESTAMP = "ts";
    static final String EXTRAS_KEY_CONFIG = "config";

    @Override
    public int onRunTask(TaskParams taskParams) {
        Bundle extras = taskParams.getExtras();
        extras.setClassLoader(getClassLoader());

        if (!Kumulos.isInitialized()) {
            KumulosConfig config = extras.getParcelable(EXTRAS_KEY_CONFIG);
            Kumulos.initialize(this.getApplication(), config);
        }

        long ts = extras.getLong(EXTRAS_KEY_TIMESTAMP);

        Runnable trackingTask = new AnalyticsContract.TrackEventRunnable(this, AnalyticsContract.EVENT_TYPE_BACKGROUND, ts, null, false);
        Kumulos.executorService.submit(trackingTask);
        AnalyticsContract.ForegroundStateWatcher.startNewSession.set(true);

        return GcmNetworkManager.RESULT_SUCCESS;
    }
}

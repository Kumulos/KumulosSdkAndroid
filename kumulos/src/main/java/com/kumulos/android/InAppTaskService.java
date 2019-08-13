package com.kumulos.android;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

public class InAppTaskService extends GcmTaskService {

    private static final String TAG = "inapp-fetch";
    private static final long DESIRED_TASK_FREQUENCY = 15 * 60L;//15 minutes
    private static final long ACCEPTED_FREQUENCY_DEVIATION = 5 * 60L; //can run 5 min earlier

    void startPeriodicFetches(Context context){
        long periodSecs = DESIRED_TASK_FREQUENCY;
        long flexSecs = ACCEPTED_FREQUENCY_DEVIATION;
        if (BuildConfig.DEBUG){
            periodSecs = 30L;
            flexSecs = 15L;
        }

        PeriodicTask periodic = new PeriodicTask.Builder()
                .setService(InAppTaskService.class)
                .setPeriod(periodSecs)
                .setFlex(flexSecs)
                .setTag(TAG)
                .setUpdateCurrent(true)
                .build();

        new Thread(new Runnable() {
            public void run() {
                GcmNetworkManager.getInstance(context).schedule(periodic);
            }
        }).start();
    }

    void cancelPeriodicFetches(Application application){
        new Thread(new Runnable() {
            public void run() {
                GcmNetworkManager.getInstance(application).cancelTask(TAG, InAppTaskService.class);
            }
        }).start();;
    }

    @Override
    public int onRunTask(TaskParams params) {//background thread
        Log.d("vlad", "TASK RUN");
        boolean success = InAppMessageService.fetch(this, null);
Log.d("vlad", "success task: "+success);
        if (!success){
            return GcmNetworkManager.RESULT_RESCHEDULE;
        }

        return GcmNetworkManager.RESULT_SUCCESS;
    }
}
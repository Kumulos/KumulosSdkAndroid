package com.kumulos.android;

import android.app.Application;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

public class InAppTaskService extends GcmTaskService {

    static final String TAG = "inapp-fetch";

    //https://stackoverflow.com/questions/31396499/gcm-network-manager-periodic-task-not-firing (check options)
    void startPeriodicFetches(Application application){
        long periodSecs = 30L; // the task should be executed every 30 seconds
        long flexSecs = 15L; // the task can run as early as -15 seconds from the scheduled time


        //TODO: sensible values
        PeriodicTask periodic = new PeriodicTask.Builder()
                .setService(InAppTaskService.class)
                .setPeriod(periodSecs)
                .setFlex(flexSecs)
                .setTag(TAG)
                .setPersisted(false)
                .setUpdateCurrent(true)
                .build();

        new Thread(new Runnable() {
            public void run() {
                GcmNetworkManager.getInstance(application).schedule(periodic);
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
        InAppMessageService.fetch(this, null);
        return GcmNetworkManager.RESULT_SUCCESS;
    }
}
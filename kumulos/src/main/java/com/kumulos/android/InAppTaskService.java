package com.kumulos.android;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

import java.util.Date;

public class InAppTaskService extends GcmTaskService {

    static final String TAG = "inapp-fetch";

    //https://stackoverflow.com/questions/31396499/gcm-network-manager-periodic-task-not-firing (check options)
    void startPeriodicFetches(Application application){
        long periodSecs = 30L; // the task should be executed every 30 seconds
        long flexSecs = 15L; // the task can run as early as -15 seconds from the scheduled time



        PeriodicTask periodic = new PeriodicTask.Builder()
                .setService(InAppTaskService.class)
                .setPeriod(periodSecs)
                .setFlex(flexSecs)
                .setTag(TAG)
                .setPersisted(false)
                .setRequiredNetwork(com.google.android.gms.gcm.Task.NETWORK_STATE_ANY)
                .setRequiresCharging(false)
                .setUpdateCurrent(true)//new task with the same tag replaces
                .build();

        GcmNetworkManager.getInstance(application).schedule(periodic);//Since this involves system IPC calls that can ocassionally be slow, it should be called on a background thread to avoid blocking the main (UI) thread.
    }

    void cancelPeriodicFetches(Application application){
        GcmNetworkManager.getInstance(application).cancelTask(TAG, InAppTaskService.class);
    }

    @Override
    public int onRunTask(TaskParams params) {//background thread
        Log.d("vlad", "TASK RUN");
        InAppMessageService.fetch(this, null);
        return GcmNetworkManager.RESULT_SUCCESS;
    }
}
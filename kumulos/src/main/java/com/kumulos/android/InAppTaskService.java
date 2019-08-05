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

class InAppTaskService extends GcmTaskService {

    //https://stackoverflow.com/questions/31396499/gcm-network-manager-periodic-task-not-firing (check options)
    void startPeriodicFetches(final Application application){
        long periodSecs = 30L; // the task should be executed every 30 seconds
        long flexSecs = 15L; // the task can run as early as -15 seconds from the scheduled time

        String tag = "inapp-fetch";

        PeriodicTask periodic = new PeriodicTask.Builder()
                .setService(InAppTaskService.class)
                .setPeriod(periodSecs)
                .setFlex(flexSecs)
                .setTag(tag)
                .setPersisted(false)
                .setRequiredNetwork(com.google.android.gms.gcm.Task.NETWORK_STATE_ANY)
                .setRequiresCharging(false)
                .setUpdateCurrent(true)
                .build();

        GcmNetworkManager.getInstance(application).schedule(periodic);//Since this involves system IPC calls that can ocassionally be slow, it should be called on a background thread to avoid blocking the main (UI) thread.
    }

    @Override
    public int onRunTask(TaskParams params) {//background thread

        InAppMessageService s = new InAppMessageService(this);

        s.fetch();

        return GcmNetworkManager.RESULT_SUCCESS;
    }
}

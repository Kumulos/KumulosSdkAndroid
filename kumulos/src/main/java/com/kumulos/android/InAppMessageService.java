package com.kumulos.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

class InAppMessageService extends GcmTaskService {

    private InAppRequestService reqServ = new InAppRequestService();

    //https://stackoverflow.com/questions/31396499/gcm-network-manager-periodic-task-not-firing (check options)
    void startPeriodicFetches(){
        long periodSecs = 30L; // the task should be executed every 30 seconds
        long flexSecs = 15L; // the task can run as early as -15 seconds from the scheduled time

        String tag = "inapp-fetch";

        PeriodicTask periodic = new PeriodicTask.Builder()
                .setService(InAppMessageService.class)
                .setPeriod(periodSecs)
                .setFlex(flexSecs)
                .setTag(tag)
                .setPersisted(false)
                .setRequiredNetwork(com.google.android.gms.gcm.Task.NETWORK_STATE_ANY)
                .setRequiresCharging(false)
                .setUpdateCurrent(true)
                .build();

        GcmNetworkManager.getInstance(this).schedule(periodic);//Since this involves system IPC calls that can ocassionally be slow, it should be called on a background thread to avoid blocking the main (UI) thread.
    }

    @Override
    public int onRunTask(TaskParams params) {//background thread
        Log.d("vlad", "thread: "+Thread.currentThread().getName());

        SharedPreferences preferences = this.getSharedPreferences("kumulos_prefs", Context.MODE_PRIVATE);
        long millis = preferences.getLong("last_sync_time", 0L);
        Date lastSyncTime = millis == 0 ? null : new Date(millis);

        lastSyncTime = null;//to remove time filtering
        reqServ.readInAppMessages(mReadCallback, lastSyncTime);

        return GcmNetworkManager.RESULT_SUCCESS;
    }

    private void storeLastSyncTime(List<InAppMessage> inAppMessages){

        Date maxUpdatedAt = inAppMessages.get(0).getUpdatedAt();

        for (int i=1; i<inAppMessages.size();i++){
            Date messageUpdatedAt = inAppMessages.get(i).getUpdatedAt();
            if (messageUpdatedAt.after(maxUpdatedAt)){
                maxUpdatedAt = messageUpdatedAt;
            }
        }

        SharedPreferences prefs = this.getSharedPreferences("kumulos_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("last_sync_time", maxUpdatedAt.getTime());
        editor.apply();
    }

    private Kumulos.ResultCallback<List<InAppMessage>> mReadCallback = new Kumulos.ResultCallback<List<InAppMessage>>() {
        @Override
        public void onSuccess(List<InAppMessage> inAppMessages) {

            if (inAppMessages.isEmpty()){
                return;
            }

            InAppMessageService.this.storeLastSyncTime(inAppMessages);

            Callable<List<InAppMessage>> task = new InAppContract.SaveInAppMessagesCallable(InAppMessageService.this, inAppMessages);
            final Future<List<InAppMessage>> future = Kumulos.executorService.submit(task);

            Log.d("vlad", "thread: "+Thread.currentThread().getName());
            InAppMessagePresenter.getInstance().presentMessages(future);//TODO: can multiple threads call this simultaneously?

        }

        @Override
        public void onFailure(Exception e) {
            Log.d("vlad", e.getMessage());
        }
    };
}





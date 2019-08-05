package com.kumulos.android;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.TaskParams;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

public class InAppMessageService {

    private InAppRequestService reqServ = new InAppRequestService();
    static final String EVENT_TYPE_MESSAGE_OPENED = "k.message.opened";
    private static final String EVENT_TYPE_MESSAGE_DELIVERED = "k.message.delivered";
    static final int MESSAGE_TYPE_IN_APP = 2;
    private Context mContext;

    InAppMessageService(Context context){
        mContext = context;
    }

    void fetch(){
        Log.d("vlad", "thread: "+Thread.currentThread().getName());

        SharedPreferences preferences = mContext.getSharedPreferences("kumulos_prefs", Context.MODE_PRIVATE);
        long millis = preferences.getLong("last_sync_time", 0L);
        Date lastSyncTime = millis == 0 ? null : new Date(millis);

        //lastSyncTime = null;//to remove time filtering

        //
        reqServ.readInAppMessages(mContext, mReadCallback, lastSyncTime);
    }

    void handlePushOpen(int inAppId){


        List<InAppMessage> messages = this.readMessages();

        //if in , present
        //if not,

    }

    List<InAppMessage> readMessages(){

        Callable<List<InAppMessage>> task = new InAppContract.ReadInAppMessagesCallable(mContext, );
        final Future<List<InAppMessage>> future = Kumulos.executorService.submit(task);

        List<InAppMessage> itemsToPresent;
        try {
            itemsToPresent = future.get();
        } catch (InterruptedException | ExecutionException ex) {
            return null;
        }

        return itemsToPresent;


    }



    private void storeLastSyncTime(List<InAppMessage> inAppMessages){

        Date maxUpdatedAt = inAppMessages.get(0).getUpdatedAt();

        for (int i=1; i<inAppMessages.size();i++){
            Date messageUpdatedAt = inAppMessages.get(i).getUpdatedAt();
            if (messageUpdatedAt.after(maxUpdatedAt)){
                maxUpdatedAt = messageUpdatedAt;
            }
        }

        SharedPreferences prefs = mContext.getSharedPreferences("kumulos_prefs", Context.MODE_PRIVATE);
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


            Callable<Pair<List<InAppMessage>, List<Integer>>> task = new InAppContract.SaveInAppMessagesCallable(mContext, inAppMessages);
            final Future<Pair<List<InAppMessage>, List<Integer>>> future = Kumulos.executorService.submit(task);

            List<InAppMessage> itemsToPresent;
            List<Integer> deliveredIds;
            try {
                Pair<List<InAppMessage>, List<Integer>> p = future.get();
                itemsToPresent = p.first;
                deliveredIds = p.second;
                Log.d("vlad", ""+itemsToPresent.size());
            } catch (InterruptedException | ExecutionException ex) {
                return;
            }

            this.trackDeliveredEvents(deliveredIds);

            Log.d("vlad", "thread: "+Thread.currentThread().getName());

            //filter
            InAppMessagePresenter.getInstance().presentMessages(itemsToPresent);//TODO: can multiple threads call this simultaneously?

        }

        private void trackDeliveredEvents( List<Integer> deliveredIds ){

            JSONObject params = new JSONObject();

            for (Integer deliveredId: deliveredIds){
                try {
                    params.put("type", InAppMessageService.MESSAGE_TYPE_IN_APP);
                    params.put("id", deliveredId);

                    Kumulos.trackEvent(mContext, InAppMessageService.EVENT_TYPE_MESSAGE_DELIVERED, params);//TODO: does it matter which context passed. consistency?
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onFailure(Exception e) {
            Log.d("vlad", e.getMessage());
        }
    };
}





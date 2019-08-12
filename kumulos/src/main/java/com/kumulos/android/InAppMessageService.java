package com.kumulos.android;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

class InAppMessageService {
    private static final String EVENT_TYPE_MESSAGE_OPENED = "k.message.opened";
    private static final String EVENT_TYPE_MESSAGE_DELIVERED = "k.message.delivered";
    private static final int MESSAGE_TYPE_IN_APP = 2;
    private static final String TAG = InAppMessageService.class.getName();
    private static final String PRESENTED_WHEN_IMMEDIATELY = "immediately";
    private static final String PRESENTED_WHEN_NEXT_OPEN = "next-open";
    private static final String PRESENTED_WHEN_NEVER = "never";

    static void clearAllMessages(Context context){
        Runnable task = new InAppContract.ClearDbRunnable(context);
        Kumulos.executorService.submit(task);
    }

    static void fetch(Context context, Integer tickleId){
        Log.d("vlad", "thread: "+Thread.currentThread().getName());

        SharedPreferences preferences = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
        long millis = preferences.getLong(SharedPrefs.IN_APP_LAST_SYNC_TIME, 0L);
        Date lastSyncTime = millis == 0 ? null : new Date(millis);

        FetchCallback callback = new FetchCallback(context, tickleId);
        InAppRequestService.readInAppMessages(context, callback, lastSyncTime);
    }

    static void readMessages(Context context, boolean fromBackground, Integer tickleId){

        Callable<List<InAppMessage>> task = new InAppContract.ReadInAppMessagesCallable(context);
        final Future<List<InAppMessage>> future = Kumulos.executorService.submit(task);

        List<InAppMessage> unreadMessages;
        try {
            unreadMessages = future.get();
        } catch (InterruptedException | ExecutionException ex) {
            return;
        }

        if (tickleId != null) {
            Log.d("vlad", "readMessages with tickle id : " + tickleId);
        }


        List<InAppMessage> itemsToPresent = new ArrayList<>();
        for(InAppMessage message: unreadMessages){
            if (message.getPresentedWhen().equals(PRESENTED_WHEN_IMMEDIATELY)
                    || (fromBackground && message.getPresentedWhen().equals(PRESENTED_WHEN_NEXT_OPEN))
                    || Integer.valueOf(message.getInAppId()).equals(tickleId)){
                itemsToPresent.add(message);
            }
        }

        //TODO: if tickleId != null, and message with it not present, extra fetch

        InAppMessagePresenter.presentMessages(itemsToPresent, tickleId);
    }

    static void handleMessageClosed(InAppMessage message){
        updateOpenedAt(message);
        trackOpenedEvent(message.getInAppId());
        clearNotification(message.getInAppId());
    }

    private static void updateOpenedAt(InAppMessage message){
        message.setOpenedAt(new Date());
        Runnable task = new InAppContract.TrackMessageOpenedRunnable(Kumulos.application, message);
        Kumulos.executorService.submit(task);
    }

    private static void trackOpenedEvent(int id){
        JSONObject params = new JSONObject();
        try {
            params.put("type", InAppMessageService.MESSAGE_TYPE_IN_APP);
            params.put("id", id);

            Kumulos.trackEvent(Kumulos.application, InAppMessageService.EVENT_TYPE_MESSAGE_OPENED, params);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static void clearNotification(int inAppId){
        NotificationManager notificationManager = (NotificationManager) Kumulos.application.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(PushBroadcastReceiver.KUMULOS_NOTIFICATION_TAG, inAppId);
    }

    private static void storeLastSyncTime(Context context, List<InAppMessage> inAppMessages){
        Date maxUpdatedAt = inAppMessages.get(0).getUpdatedAt();

        for (int i=1; i<inAppMessages.size();i++){
            Date messageUpdatedAt = inAppMessages.get(i).getUpdatedAt();
            if (messageUpdatedAt.after(maxUpdatedAt)){
                maxUpdatedAt = messageUpdatedAt;
            }
        }

        SharedPreferences prefs = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(SharedPrefs.IN_APP_LAST_SYNC_TIME, maxUpdatedAt.getTime());
        editor.apply();
    }

    private static class FetchCallback extends Kumulos.ResultCallback<List<InAppMessage>> {
        Integer mTickleId;
        Context mContext;

        FetchCallback(Context context, Integer tickleId){
            this.mTickleId = tickleId;
            this.mContext = context;
        }

        @Override
        public void onSuccess(List<InAppMessage> inAppMessages) {
            Log.d("vlad", "FETCH ON SUCCESS");
            if (inAppMessages.isEmpty()){
                Log.d("vlad", "empty");
                return;
            }

            InAppMessageService.storeLastSyncTime(this.mContext, inAppMessages);

            Callable<Pair<List<InAppMessage>, List<Integer>>> task = new InAppContract.SaveInAppMessagesCallable(mContext, inAppMessages);
            final Future<Pair<List<InAppMessage>, List<Integer>>> future = Kumulos.executorService.submit(task);

            List<InAppMessage> unreadMessages;
            List<Integer> deliveredIds;
            try {
                Pair<List<InAppMessage>, List<Integer>> p = future.get();
                unreadMessages = p.first;
                deliveredIds = p.second;
                Log.d("vlad", ""+unreadMessages.size());
            } catch (InterruptedException | ExecutionException ex) {
                return;
            }

            this.trackDeliveredEvents(deliveredIds);

            Log.d("vlad", "thread: "+Thread.currentThread().getName());

            if (InAppActivityLifecycleWatcher.isBackground()){
                Log.d("vlad", "present, but bg");
                return;
            }

            List<InAppMessage> itemsToPresent = new ArrayList<>();
            for(InAppMessage message: unreadMessages){
                if (message.getPresentedWhen().equals(PRESENTED_WHEN_IMMEDIATELY)
                        || Integer.valueOf(message.getInAppId()).equals(mTickleId)){
                    itemsToPresent.add(message);
                }
            }
            Log.d("vlad", "size to present: "+itemsToPresent.size());

            InAppMessagePresenter.presentMessages(itemsToPresent, mTickleId);
        }

        private void trackDeliveredEvents( List<Integer> deliveredIds ){

            JSONObject params = new JSONObject();

            for (Integer deliveredId: deliveredIds){
                try {
                    params.put("type", InAppMessageService.MESSAGE_TYPE_IN_APP);
                    params.put("id", deliveredId);

                    Kumulos.trackEvent(Kumulos.application, InAppMessageService.EVENT_TYPE_MESSAGE_DELIVERED, params);
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onFailure(Exception e) {
            Kumulos.log(TAG, e.getMessage());
        }
    }
}
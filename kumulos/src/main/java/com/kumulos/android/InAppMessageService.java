package com.kumulos.android;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Calendar;

class InAppMessageService {
    private static final String TAG = InAppMessageService.class.getName();
    private static final String PRESENTED_WHEN_IMMEDIATELY = "immediately";
    private static final String PRESENTED_WHEN_NEXT_OPEN = "next-open";
    private static final String PRESENTED_WHEN_NEVER = "never";
    private static List<Integer> pendingTickleIds = new ArrayList<>();

    static void clearAllMessages(Context context){
        Runnable task = new InAppContract.ClearDbRunnable(context);
        Kumulos.executorService.submit(task);
    }

    static boolean fetch(Context context, boolean includeNextOpen){
        SharedPreferences preferences = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
        long millis = preferences.getLong(SharedPrefs.IN_APP_LAST_SYNC_TIME, 0L);
        Date lastSyncTime = millis == 0 ? null : new Date(millis);

        List<InAppMessage> inAppMessages = InAppRequestService.readInAppMessages(context, lastSyncTime);
        if (inAppMessages == null){
            return false;
        }

        showFetchedMessages(context, inAppMessages, includeNextOpen);
        return true;
    }

    private static void showFetchedMessages(Context context, List<InAppMessage> inAppMessages, boolean includeNextOpen){
        if (inAppMessages.isEmpty()){
            return;
        }

        Callable<Pair<List<InAppMessage>, List<Integer>>> task = new InAppContract.SaveInAppMessagesCallable(context, inAppMessages);
        final Future<Pair<List<InAppMessage>, List<Integer>>> future = Kumulos.executorService.submit(task);

        List<InAppMessage> unreadMessages;
        List<Integer> deliveredIds;
        try {
            Pair<List<InAppMessage>, List<Integer>> p = future.get();
            unreadMessages = p.first;
            deliveredIds = p.second;
        } catch (InterruptedException | ExecutionException ex) {
            return;
        }

        InAppMessageService.storeLastSyncTime(context, inAppMessages);

        trackDeliveredEvents(context, deliveredIds);

        if (AnalyticsContract.ForegroundStateWatcher.isBackground()){
            return;
        }

        List<InAppMessage> itemsToPresent = new ArrayList<>();
        for(InAppMessage message: unreadMessages){
            boolean hasPendingTickleId = false;
            for(Integer pendingTickleId : pendingTickleIds){
                if (message.getInAppId() == pendingTickleId){
                    hasPendingTickleId = true;
                    break;
                }
            }
            if (message.getPresentedWhen().equals(PRESENTED_WHEN_IMMEDIATELY)
                    || (includeNextOpen && message.getPresentedWhen().equals(PRESENTED_WHEN_NEXT_OPEN))
                    || hasPendingTickleId){
                itemsToPresent.add(message);
            }
        }

        InAppMessagePresenter.presentMessages(itemsToPresent, pendingTickleIds);
        pendingTickleIds.clear();
    }

    private static void trackDeliveredEvents(Context context, List<Integer> deliveredIds ){

        JSONObject params = new JSONObject();

        for (Integer deliveredId: deliveredIds){
            try {
                params.put("type", AnalyticsContract.MESSAGE_TYPE_IN_APP);
                params.put("id", deliveredId);

                Kumulos.trackEvent(context, AnalyticsContract.EVENT_TYPE_MESSAGE_DELIVERED, params);
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
        }
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

        List<InAppMessage> itemsToPresent = new ArrayList<>();
        for(InAppMessage message: unreadMessages){
            if (message.getPresentedWhen().equals(PRESENTED_WHEN_IMMEDIATELY)
                    || (fromBackground && message.getPresentedWhen().equals(PRESENTED_WHEN_NEXT_OPEN))
                    || Integer.valueOf(message.getInAppId()).equals(tickleId)){
                itemsToPresent.add(message);
            }
        }

        List<Integer> tickleIds = new ArrayList<>();
        if (tickleId != null){
            boolean tickleMessageFound = false;
            for (InAppMessage message : itemsToPresent){
                if (message.getInAppId() == tickleId){
                    tickleMessageFound = true;
                    break;
                }
            }

            if (!tickleMessageFound){
                pendingTickleIds.add(tickleId);
            }
            else{
                tickleIds.add(tickleId);
            }
        }

        InAppMessagePresenter.presentMessages(itemsToPresent, tickleIds);

        maybeDoExtraFetch(context, fromBackground);
    }

    private static void maybeDoExtraFetch(Context context, boolean fromBackground){
        boolean shouldFetch = false;
        if (BuildConfig.DEBUG){
            shouldFetch = true;
        }
        else{
            SharedPreferences preferences = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
            long lastSyncMillis = preferences.getLong(SharedPrefs.IN_APP_LAST_SYNC_TIME, 0L);

            Calendar now = Calendar.getInstance();
            now.setTime(new Date());
            if (lastSyncMillis != 0L && lastSyncMillis +  3600 * 1000 < now.getTimeInMillis()){
                shouldFetch = true;
            }
        }

        if (shouldFetch){
            new Thread(new Runnable() {
                public void run() {
                    InAppMessageService.fetch(context, fromBackground);
                }
            }).start();
        }
    }

    static void handleMessageClosed(Context context, InAppMessage message){
        updateDismissedAt(context, message);
        trackDismissedEvent(context, message.getInAppId());
        clearNotification(context, message.getInAppId());
    }

    private static void updateDismissedAt(Context context, InAppMessage message){
        message.setDismissedAt(new Date());
        Runnable task = new InAppContract.TrackMessageDismissedRunnable(context, message);
        Kumulos.executorService.submit(task);
    }

    private static void trackDismissedEvent(Context context, int id){
        JSONObject params = new JSONObject();
        try {
            params.put("type", AnalyticsContract.MESSAGE_TYPE_IN_APP);
            params.put("id", id);

            Kumulos.trackEvent(context, AnalyticsContract.EVENT_TYPE_MESSAGE_DISMISSED, params);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }

    static void trackOpenedEvent(Context context, int id){
        JSONObject params = new JSONObject();
        try {
            params.put("type", AnalyticsContract.MESSAGE_TYPE_IN_APP);
            params.put("id", id);

            Kumulos.trackEvent(context, AnalyticsContract.EVENT_TYPE_MESSAGE_OPENED, params);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static void clearNotification(Context context, int inAppId){
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
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

    static List<InAppInboxItem> readInboxItems(Context context){
        Callable<List<InAppInboxItem>> task = new InAppContract.ReadInAppInboxCallable(context);
        final Future<List<InAppInboxItem>> future = Kumulos.executorService.submit(task);

        List<InAppInboxItem> inboxItems;
        try {
            inboxItems = future.get();
        } catch (InterruptedException | ExecutionException ex) {

            return new ArrayList<>();
        }

        return inboxItems;
    }

    static KumulosInApp.InboxMessagePresentationResult presentMessage(Context context, InAppInboxItem item){
        Callable<InAppMessage> task = new InAppContract.ReadInAppInboxMessageCallable(context, item.getId());
        final Future<InAppMessage> future = Kumulos.executorService.submit(task);

        InAppMessage inboxMessage;
        try {
            inboxMessage = future.get();
        } catch (InterruptedException | ExecutionException ex) {
            return KumulosInApp.InboxMessagePresentationResult.FAILED;
        }

        if (inboxMessage == null){
           return KumulosInApp.InboxMessagePresentationResult.FAILED;
        }

        if (item.getAvailableTo() != null && item.getAvailableTo().getTime() < new Date().getTime()){
            return KumulosInApp.InboxMessagePresentationResult.FAILED_EXPIRED;
        }

        List<InAppMessage> itemsToPresent = new ArrayList<>();
        itemsToPresent.add(inboxMessage);

        InAppMessagePresenter.presentMessages(itemsToPresent, null);

        return KumulosInApp.InboxMessagePresentationResult.PRESENTED;
    }

    static boolean deleteMessageFromInbox(Context context, int id){
        JSONObject params = new JSONObject();
        try {
            params.put("type", AnalyticsContract.MESSAGE_TYPE_IN_APP);
            params.put("id", id);

            Kumulos.trackEvent(context, AnalyticsContract.MESSAGE_DELETED_FROM_INBOX, params);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }

        clearNotification(context, id);

        Callable<Boolean> task = new InAppContract.DeleteInAppInboxMessageCallable(context, id);
        final Future<Boolean> future = Kumulos.executorService.submit(task);

        Boolean result = false;
        try {
            result = future.get();
        } catch (InterruptedException | ExecutionException ex) {
            Kumulos.log(TAG, ex.getMessage());
        }

        return result;
    }
}
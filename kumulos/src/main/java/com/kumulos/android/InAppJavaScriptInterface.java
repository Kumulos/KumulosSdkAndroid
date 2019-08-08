package com.kumulos.android;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

class InAppJavaScriptInterface {

    private static final String TAG = InAppJavaScriptInterface.class.getName();

    @JavascriptInterface
    public void postClientMessage(String msg) {

        String messageType = null;
        JSONObject data = null;
        try{
            JSONObject message = new JSONObject(msg);
            messageType = message.getString("type");
            data = message.optJSONObject("data");
        }
        catch(JSONException e){
            Log.d(TAG, "Incorrect message format: "+msg);
            return;
        }

        switch(messageType){
            case "READY":
                InAppMessagePresenter.clientReady();
                return;
            case "MESSAGE_OPENED":
                InAppMessagePresenter.messageOpened();
                return;
            case "MESSAGE_CLOSED":
                InAppMessagePresenter.messageClosed();
                return;
            case "EXECUTE_ACTIONS":
                List<ExecutableAction> actions = this.parseButtonActionData(data);
                this.executeActions(actions);
                return;
            default:
                Log.d(TAG, "Unknown message type: "+messageType);
        }
    }

    private void executeActions(List<ExecutableAction> actions){
        InAppMessagePresenter.closeCurrentMessage();

        WeakReference<Activity> currentActivityRef = InAppActivityLifecycleWatcher.getCurrentActivity();

        for(ExecutableAction action : actions){
            switch(action.getType()){
                case "subscribeToChannel":
                    PushSubscriptionManager psm = new PushSubscriptionManager();
                    psm.subscribe(Kumulos.applicationContext, new String[]{action.getChannelUuid()});
                    break;
                case "trackConversionEvent":
                    Kumulos.trackEvent(Kumulos.applicationContext, action.getEventType(), new JSONObject());
                    break;
                case "openUrl":
                    if (currentActivityRef == null){
                        return;
                    }
                    this.openUrl(currentActivityRef.get(), action.getUrl());
                    return;
                case "deepLink":
                    Kumulos.inAppDeepLinkHandler.handle(action.getDeepLink());
                    return;
                case "requestAppStoreRating":
                    if (currentActivityRef == null){
                        return;
                    }
                    this.openUrl(currentActivityRef.get(), "market://details?id=" + currentActivityRef.get().getPackageName());
                    break;
            }
        }
    }

    private void openUrl(Activity currentActivity, String uri){
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (browserIntent.resolveActivity(currentActivity.getPackageManager()) != null) {
            currentActivity.startActivity(browserIntent);
        }
    }

    private List<ExecutableAction> parseButtonActionData(JSONObject data){
        List<ExecutableAction> actions = new ArrayList<>();
        JSONArray rawActions = data.optJSONArray("actions");

        for (int i=0; i< rawActions.length(); i++){
            JSONObject rawAction = rawActions.optJSONObject(i);

            String actionType = rawAction.optString("type", null);
            JSONObject rawActionData = rawAction.optJSONObject("data");

            ExecutableAction action = new ExecutableAction();
            action.setType(actionType);

            switch(actionType){
                case "subscribeToChannel":
                    String channelUuid = rawActionData.optString("channelUuid");
                    action.setChannelUuid(channelUuid);
                    break;
                case "trackConversionEvent":
                    String eventType = rawActionData.optString("eventType");
                    action.setEventType(eventType);
                    break;
                case "openUrl":
                    String url = rawActionData.optString("url");
                    action.setUrl(url);
                    break;
                case "deepLink":
                    String deepLink = rawActionData.optString("deepLink");
                    try{
                        JSONObject jsonDeepLink = new JSONObject(deepLink);
                        action.setDeepLink(jsonDeepLink);
                    }
                    catch(JSONException e){
                        continue;
                    }

                    break;
                case "closeMessage":
                case "requestAppStoreRating":
                default:
            }

            actions.add(action);
        }

        return actions;
    }

    private static class ExecutableAction{
        String type;

        String url;
        String channelUuid;
        String eventType;
        JSONObject deepLink;

        void setType(String type){
            this.type = type;
        }

        void setChannelUuid(String channelUuid){
            this.channelUuid = channelUuid;
        }

        void setEventType(String eventType){
            this.eventType = eventType;
        }

        void setUrl(String url){
            this.url = url;
        }

        void setDeepLink(JSONObject deepLink){
            this.deepLink = deepLink;
        }

        String getType() {
            return type;
        }

        String getUrl() {
            return url;
        }

        String getChannelUuid() {
            return channelUuid;
        }

        String getEventType() {
            return eventType;
        }

        JSONObject getDeepLink() {
            return deepLink;
        }
    }
}
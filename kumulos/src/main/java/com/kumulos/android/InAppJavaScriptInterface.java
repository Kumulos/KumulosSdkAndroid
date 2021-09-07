package com.kumulos.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.annotation.UiThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class InAppJavaScriptInterface {

    private static final String TAG = InAppJavaScriptInterface.class.getName();
    private static final String BUTTON_ACTION_CLOSE_MESSAGE = "closeMessage";
    private static final String BUTTON_ACTION_SUBSCRIBE_TO_CHANNEL = "subscribeToChannel";
    private static final String BUTTON_ACTION_TRACK_CONVERSION_EVENT = "trackConversionEvent";
    private static final String BUTTON_ACTION_OPEN_URL = "openUrl";
    private static final String BUTTON_ACTION_DEEP_LINK = "deepLink";
    private static final String BUTTON_ACTION_REQUEST_APP_STORE_RATING = "requestAppStoreRating";
    private static final String BUTTON_ACTION_PUSH_REGISTER = "promptPushPermission";

    static final String NAME = "Android";

    @JavascriptInterface
    public void postClientMessage(String msg) {
        String messageType = null;
        JSONObject data = null;
        try {
            JSONObject message = new JSONObject(msg);
            messageType = message.getString("type");
            data = message.optJSONObject("data");
        } catch (JSONException e) {
            Log.d(TAG, "Incorrect message format: " + msg);
            return;
        }

        final Activity currentActivity = AnalyticsContract.ForegroundStateWatcher.getCurrentActivity();
        if (null == currentActivity) {
            return;
        }

        switch (messageType) {
            case "READY":
                InAppMessagePresenter.clientReady(currentActivity);
                return;
            case "MESSAGE_OPENED":
                InAppMessagePresenter.messageOpened(currentActivity);
                return;
            case "MESSAGE_CLOSED":
                InAppMessagePresenter.messageClosed();
                return;
            case "EXECUTE_ACTIONS":
                List<ExecutableAction> actions = this.parseButtonActionData(data);
                currentActivity.runOnUiThread(() -> this.executeActions(currentActivity, actions));
                return;
            default:
                Log.d(TAG, "Unknown message type: " + messageType);
        }
    }

    @UiThread
    private void executeActions(Activity currentActivity, List<ExecutableAction> actions) {
        InAppMessage currentMessage = InAppMessagePresenter.getCurrentMessage();

        if (null == currentMessage) {
            Log.e(TAG, "Expected a currently-presented message");
            return;
        }

        // Handle 'secondary' actions
        for (ExecutableAction action : actions) {
            switch (action.getType()) {
                case BUTTON_ACTION_CLOSE_MESSAGE:
                    InAppMessagePresenter.closeCurrentMessage(currentActivity);
                    break;
                case BUTTON_ACTION_SUBSCRIBE_TO_CHANNEL:
                    PushSubscriptionManager psm = new PushSubscriptionManager();
                    psm.subscribe(currentActivity, new String[]{action.getChannelUuid()});
                    break;
                case BUTTON_ACTION_TRACK_CONVERSION_EVENT:
                    Kumulos.trackEventImmediately(currentActivity, action.getEventType(), null);
                    break;
            }
        }

        // Handle 'terminating' actions
        for (ExecutableAction action : actions) {
            switch (action.getType()) {
                case BUTTON_ACTION_OPEN_URL:
                    this.openUrl(currentActivity, action.getUrl());
                    return;
                case BUTTON_ACTION_DEEP_LINK:
                    if (null != KumulosInApp.inAppDeepLinkHandler) {
                        KumulosInApp.inAppDeepLinkHandler.handle(KumulosInApp.application,
                                new InAppDeepLinkHandlerInterface.InAppButtonPress(
                                        action.getDeepLink(),
                                        currentMessage.getInAppId(),
                                        currentMessage.getData()
                                )
                        );
                    }
                    return;
                case BUTTON_ACTION_REQUEST_APP_STORE_RATING:
                    this.openPlayStore(currentActivity);
                    return;
                case BUTTON_ACTION_PUSH_REGISTER:
                    Kumulos.pushRegister(currentActivity);
                    return;
            }
        }
    }

    private void openPlayStore(Activity currentActivity) {
        String packageName = currentActivity.getPackageName();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
        if (intent.resolveActivity(currentActivity.getPackageManager()) != null) {
            currentActivity.startActivity(intent);
            return;
        }

        intent.setData(Uri.parse("https://play.google.com/store/apps/details?" + packageName));
        currentActivity.startActivity(intent);
    }

    private void openUrl(Activity currentActivity, String uri) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (browserIntent.resolveActivity(currentActivity.getPackageManager()) != null) {
            currentActivity.startActivity(browserIntent);
        }
    }

    private List<ExecutableAction> parseButtonActionData(JSONObject data) {
        List<ExecutableAction> actions = new ArrayList<>();
        JSONArray rawActions = data.optJSONArray("actions");

        for (int i = 0; i < rawActions.length(); i++) {
            JSONObject rawAction = rawActions.optJSONObject(i);

            String actionType = rawAction.optString("type");
            JSONObject rawActionData = rawAction.optJSONObject("data");

            ExecutableAction action = new ExecutableAction();
            action.setType(actionType);

            switch (actionType) {
                case BUTTON_ACTION_SUBSCRIBE_TO_CHANNEL:
                    String channelUuid = rawActionData.optString("channelUuid");
                    action.setChannelUuid(channelUuid);
                    break;
                case BUTTON_ACTION_TRACK_CONVERSION_EVENT:
                    String eventType = rawActionData.optString("eventType");
                    action.setEventType(eventType);
                    break;
                case BUTTON_ACTION_OPEN_URL:
                    String url = rawActionData.optString("url");
                    action.setUrl(url);
                    break;
                case BUTTON_ACTION_DEEP_LINK:
                    JSONObject deepLink = rawActionData.optJSONObject("deepLink");
                    action.setDeepLink(deepLink);

                default:
            }
            actions.add(action);
        }
        return actions;
    }

    private static class ExecutableAction {
        String type;

        String url;
        String channelUuid;
        String eventType;
        JSONObject deepLink;

        void setType(String type) {
            this.type = type;
        }

        void setChannelUuid(String channelUuid) {
            this.channelUuid = channelUuid;
        }

        void setEventType(String eventType) {
            this.eventType = eventType;
        }

        void setUrl(String url) {
            this.url = url;
        }

        void setDeepLink(JSONObject deepLink) {
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
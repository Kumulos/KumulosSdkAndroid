package com.kumulos.android;

import android.content.Intent;
import android.net.Uri;

import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class FirebaseMessagingService extends com.google.firebase.messaging.FirebaseMessagingService {


    private static final String TAG = FirebaseMessagingService.class.getName();

    @Override
    public void onNewToken(String token) {
        Kumulos.log(TAG, "Got a push token: " + token);
        Kumulos.pushTokenStore(this, token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Kumulos.log(TAG, "Received a push message");

        Map<String, String> bundle = remoteMessage.getData();

        if (!bundle.containsKey("custom")) {
            return;
        }

        String customStr = bundle.get("custom");

        // Extract bundle
        String id;
        JSONObject data;
        JSONObject custom;
        Uri uri;

        try {
            custom = new JSONObject(customStr);
            id = custom.getString("i");
            uri = (!custom.isNull("u")) ? Uri.parse(custom.getString("u")) : null;
            data = custom.optJSONObject("a");
        } catch (JSONException e) {
            Kumulos.log(TAG, "Push received had no ID/data/uri or was incorrectly formatted, ignoring...");
            return;
        }

        String bgn = bundle.get("bgn");
        boolean isBackground = (null != bgn && bgn.equals("1"));

        PushMessage pushMessage = new PushMessage(
                id,
                bundle.get("title"),
                bundle.get("alert"),
                data,
                remoteMessage.getSentTime(),
                uri,
                isBackground
        );

        Intent intent = new Intent(PushBroadcastReceiver.ACTION_PUSH_RECEIVED);
        intent.setPackage(getPackageName());
        intent.putExtra(PushMessage.EXTRAS_KEY, pushMessage);

        this.sendBroadcast(intent);
    }
}

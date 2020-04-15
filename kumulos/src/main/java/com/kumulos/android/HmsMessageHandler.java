package com.kumulos.android;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.huawei.hms.push.RemoteMessage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * HmsMessageHandler provides helpers for handling HmsMessageService events
 *
 * This can allow interoperating Kumulos push with your own HCM service
 */
public class HmsMessageHandler {

    private static final String TAG = HmsMessageHandler.class.getName();

    /**
     * Handles the received notification from HCM, creating a PushMessage model and broadcasting
     * the appropriate com.kumulos.push Intent
     * @param context
     * @param remoteMessage
     */
    public static void onMessageReceived(@NonNull Context context, @Nullable RemoteMessage remoteMessage) {
        if (null == remoteMessage) {
            return;
        }

        Kumulos.log(TAG, "Received a push message");

        JSONObject bundle;

        try {
            bundle = new JSONObject(remoteMessage.getData());
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        if (!bundle.has("custom") || bundle.isNull("custom")) {
            return;
        }

        int id;
        JSONObject data;
        JSONObject custom;
        Uri uri;
        String pictureUrl = bundle.optString("bicon", null);
        JSONArray buttons;
        String sound = bundle.optString("sound", null);

        try {
            custom = new JSONObject(bundle.getString("custom"));
            uri = (!custom.isNull("u")) ? Uri.parse(custom.getString("u")) : null;
            data = custom.optJSONObject("a");
            id = data.getJSONObject("k.message").getJSONObject("data").getInt("id");
            buttons = data.optJSONArray("k.buttons");
        } catch (JSONException e) {
            Kumulos.log(TAG, "Push received had no ID/data/uri or was incorrectly formatted, ignoring...");
            return;
        }

        String bgn = bundle.optString("bgn", null);
        boolean runBackgroundHandler = (null != bgn && bgn.equals("1"));

        PushMessage pushMessage = new PushMessage(
                id,
                bundle.optString("title", null),
                bundle.optString("alert", null),
                data,
                remoteMessage.getSentTime(),
                uri,
                runBackgroundHandler,
                pictureUrl,
                buttons,
                sound
        );

        Intent intent = new Intent(PushBroadcastReceiver.ACTION_PUSH_RECEIVED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(PushMessage.EXTRAS_KEY, pushMessage);

        context.sendBroadcast(intent);
    }

}

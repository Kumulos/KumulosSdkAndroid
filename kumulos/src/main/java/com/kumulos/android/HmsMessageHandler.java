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
        String pictureUrl = optNullableString(bundle, "bicon");
        JSONArray buttons;
        String sound = optNullableString(bundle, "sound");

        try {
            custom = new JSONObject(bundle.getString("custom"));
            uri = (!custom.isNull("u")) ? Uri.parse(custom.getString("u")) : null;
            data = custom.getJSONObject("a");
            id = data.getJSONObject("k.message").getJSONObject("data").getInt("id");
            buttons = data.optJSONArray("k.buttons");
        } catch (JSONException e) {
            Kumulos.log(TAG, "Push received had no ID/data/uri or was incorrectly formatted, ignoring...");
            return;
        }

        String bgn = optNullableString(bundle, "bgn");
        boolean runBackgroundHandler = (null != bgn && bgn.equals("1"));

        PushMessage pushMessage = new PushMessage(
                id,
                optNullableString(bundle, "title"),
                optNullableString(bundle, "alert"),
                data,
                remoteMessage.getSentTime(),
                uri,
                runBackgroundHandler,
                pictureUrl,
                buttons,
                sound,
                remoteMessage.getCollapseKey()
        );

        Intent intent = new Intent(PushBroadcastReceiver.ACTION_PUSH_RECEIVED);
        intent.setPackage(context.getPackageName());
        intent.putExtra(PushMessage.EXTRAS_KEY, pushMessage);

        context.sendBroadcast(intent);
    }

    private static String optNullableString(@NonNull JSONObject object, @NonNull String name) {
        if (!object.has(name) || object.isNull(name)) {
            return null;
        }

        return object.optString(name);
    }

}

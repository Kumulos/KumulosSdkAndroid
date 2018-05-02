package com.kumulos.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

public class GcmListenerService extends com.google.android.gms.gcm.GcmListenerService {

    private static final String TAG = GcmListenerService.class.getName();

    @Override
    public void onMessageReceived(String s, Bundle bundle) {
        super.onMessageReceived(s, bundle);

        Kumulos.log(TAG, "Received a push message");

        String customStr = bundle.getString("custom");

        if (null == customStr) {
            return;
        }

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

        String bgn = bundle.getString("bgn");
        boolean isBackground = (null != bgn && bgn.equals("1"));

        PushMessage pushMessage = new PushMessage(
                id,
                bundle.getString("title"),
                bundle.getString("alert"),
                data,
                bundle.getLong("google.sent_time", 0L),
                uri,
                isBackground
        );

        Intent intent = new Intent(PushBroadcastReceiver.ACTION_PUSH_RECEIVED);
        intent.setPackage(getPackageName());
        intent.putExtra(PushMessage.EXTRAS_KEY, pushMessage);

        this.sendBroadcast(intent);
    }

}

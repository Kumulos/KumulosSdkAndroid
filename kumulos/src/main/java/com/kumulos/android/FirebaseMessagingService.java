package com.kumulos.android;

import android.text.TextUtils;

import com.google.firebase.messaging.RemoteMessage;

import androidx.annotation.NonNull;

public class FirebaseMessagingService extends com.google.firebase.messaging.FirebaseMessagingService {

    private static final String TAG = FirebaseMessagingService.class.getName();

    @Override
    public void onNewToken(@NonNull String token) {
        if (TextUtils.isEmpty(token)) {
            return;
        }

        Kumulos.log(TAG, "Got a push token: " + token);
        Kumulos.pushTokenStore(this, PushTokenType.FCM, token, this.getPackageName());
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        FirebaseMessageHandler.onMessageReceived(this, remoteMessage);
    }
}

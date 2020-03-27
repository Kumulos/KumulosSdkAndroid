package com.kumulos.android;

import com.google.firebase.messaging.RemoteMessage;

public class FirebaseMessagingService extends com.google.firebase.messaging.FirebaseMessagingService {

    private static final String TAG = FirebaseMessagingService.class.getName();

    @Override
    public void onNewToken(String token) {
        Kumulos.log(TAG, "Got a push token: " + token);
        Kumulos.pushTokenStore(this, token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        FirebaseMessageHandler.onMessageReceived(this, remoteMessage);
    }
}

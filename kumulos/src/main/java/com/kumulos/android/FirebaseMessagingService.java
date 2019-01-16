package com.kumulos.android;

public class FirebaseMessagingService extends com.google.firebase.messaging.FirebaseMessagingService {


    private static final String TAG = FirebaseMessagingService.class.getName();

    @Override
    public void onNewToken(String token) {
        Kumulos.log(TAG, "Got a push token: " + token);
        Kumulos.pushTokenStore(this, token);
    }
}

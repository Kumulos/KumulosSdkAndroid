package com.kumulos.android;

import com.google.firebase.iid.FirebaseInstanceId;

public class FirebaseInstanceIdService extends com.google.firebase.iid.FirebaseInstanceIdService {

    private static final String TAG = FirebaseInstanceIdService.class.getName();

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();

        String token = FirebaseInstanceId.getInstance().getToken();

        if (null == token) {
            return;
        }

        Kumulos.log(TAG, "Got a push token: " + token);
        Kumulos.pushTokenStore(this, token);
    }
}

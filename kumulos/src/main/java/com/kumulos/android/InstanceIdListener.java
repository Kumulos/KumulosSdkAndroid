package com.kumulos.android;

import android.content.Intent;

import com.google.android.gms.iid.InstanceIDListenerService;

public class InstanceIdListener extends InstanceIDListenerService {

    private static final String TAG = InstanceIdListener.class.getName();

    @Override
    public void onTokenRefresh() {
        Kumulos.log(TAG, "Push token has been refreshed, reset flag and reregister");

        Intent intent = new Intent(InstanceIdListener.this, GcmRegistrationIntentService.class);
        intent.setAction(GcmRegistrationIntentService.ACTION_REGISTER);
        try {
            startService(intent);
        }
        catch (IllegalStateException e) {
            // Noop for Android 8+ if app in background
        }
    }
}

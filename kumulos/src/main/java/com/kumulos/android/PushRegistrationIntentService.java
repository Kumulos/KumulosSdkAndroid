package com.kumulos.android;

import android.app.IntentService;
import android.content.Intent;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;

public class PushRegistrationIntentService extends IntentService {

    private static final String TAG = PushRegistrationIntentService.class.getName();

    protected static final String ACTION_REGISTER = "com.kumulos.push.ACTION_REGISTER";
    protected static final String ACTION_UNREGISTER = "com.kumulos.push.ACTION_UNREGISTER";

    public PushRegistrationIntentService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (null == intent) {
            return;
        }

        String action = intent.getAction();

        if (null == action) {
            return;
        }

        String token = FirebaseInstanceId.getInstance().getToken();
        if (token == null) {
            return;
        }

        switch (action) {
            case ACTION_REGISTER: {
                Kumulos.log(TAG, "Got a push token: " + token);
                Kumulos.pushTokenStore(this, token);
            }
            break;
            case ACTION_UNREGISTER: {
                try {
                    FirebaseInstanceId.getInstance().deleteToken(token, FirebaseMessaging.INSTANCE_ID_SCOPE);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Kumulos.pushTokenDelete(new Kumulos.Callback() {
                            @Override
                            public void onSuccess() {
                                // noop
                            }
                        });
            }
            break;
        }

    }

}

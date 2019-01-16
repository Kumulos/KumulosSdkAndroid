package com.kumulos.android;

import android.app.IntentService;
import android.content.Intent;

import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
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

        switch (action) {
            case ACTION_REGISTER: {
                Task<InstanceIdResult> result = FirebaseInstanceId.getInstance().getInstanceId();

                result.addOnSuccessListener(Kumulos.executorService, instanceIdResult ->
                        Kumulos.pushTokenStore(PushRegistrationIntentService.this, instanceIdResult.getToken()));
            }
            break;
            case ACTION_UNREGISTER: {
                // TODO what is the authorized entity
                try {
                    FirebaseInstanceId.getInstance().deleteToken("...??", FirebaseMessaging.INSTANCE_ID_SCOPE);
                } catch (IOException e) {
                    e.printStackTrace();
                }
//                String defaultSenderId = getDefaultSenderId();
//                InstanceID instanceID = InstanceID.getInstance(this);
//
//                try {
//                    instanceID.deleteToken(defaultSenderId, GoogleCloudMessaging.INSTANCE_ID_SCOPE);
//                    Kumulos.pushTokenDelete(new Kumulos.Callback() {
//                        @Override
//                        public void onSuccess() {
//                            // Noop
//                        }
//                    });
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }
            break;
        }

    }

}

package com.kumulos.android;

import android.app.IntentService;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import java.io.IOException;

public class GcmRegistrationIntentService extends IntentService {

    private static final String TAG = GcmRegistrationIntentService.class.getName();

    protected static final String ACTION_REGISTER = "com.kumulos.push.ACTION_REGISTER";
    protected static final String ACTION_UNREGISTER = "com.kumulos.push.ACTION_UNREGISTER";

    public GcmRegistrationIntentService() {
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
                String defaultSenderId = getDefaultSenderId();
                InstanceID instanceID = InstanceID.getInstance(this);

                try {
                    String token = instanceID.getToken(defaultSenderId,
                            GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);

                    Kumulos.log(TAG, "Got a push token: " + token);
                    Kumulos.pushTokenStore(this, token);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            break;
            case ACTION_UNREGISTER: {
                String defaultSenderId = getDefaultSenderId();
                InstanceID instanceID = InstanceID.getInstance(this);

                try {
                    instanceID.deleteToken(defaultSenderId, GoogleCloudMessaging.INSTANCE_ID_SCOPE);
                    Kumulos.pushTokenDelete(new Kumulos.Callback() {
                        @Override
                        public void onSuccess() {
                            // Noop
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            break;
        }

    }

    private String getDefaultSenderId() {
        String pkg = getPackageName();
        ApplicationInfo info = null;
        try {
            info = getPackageManager().getApplicationInfo(pkg, PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to read Google project number for GCM registration, aborting!");
        }

        Bundle meta = info.metaData;
        String senderId = meta.getString("kumulos_gcm_sender_id");
        if (TextUtils.isEmpty(senderId) || senderId.length() < 5) {
            throw new RuntimeException("Unable to read Google project number for GCM registration, aborting!");
        }

        // Prefixed in manifest to read correctly as a string: str:123
        return senderId.substring(4);
    }

}

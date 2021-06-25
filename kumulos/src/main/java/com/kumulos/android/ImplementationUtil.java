package com.kumulos.android;

import android.content.Context;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailabilityLight;
import com.google.firebase.messaging.FirebaseMessaging;
import com.huawei.hms.api.HuaweiApiAvailability;

import androidx.annotation.NonNull;

final class ImplementationUtil {

    private MessagingApi availableMessagingApi;
    private FirebaseMessagingApi firebaseMessagingApi = FirebaseMessagingApi.UNKNOWN;
    enum MessagingApi {
        NONE,
        FCM,
        HMS
    }

    enum FirebaseMessagingApi {
        UNKNOWN,
        DEPRECATED_1,  //FirebaseMessaging [19.0.0, 22.0.0)
        LATEST   //FirebaseMessaging [21.0.0, 22.99.99]
    }

    private static ImplementationUtil instance;

    private ImplementationUtil() {}

    private ImplementationUtil(@NonNull Context context) {
        if (canLoadClass("com.google.android.gms.common.GoogleApiAvailabilityLight")) {
            int result = GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context);

            if (ConnectionResult.SUCCESS == result) {
                availableMessagingApi = MessagingApi.FCM;

                if (canLoadClass("com.google.firebase.iid.FirebaseInstanceId")){
                    firebaseMessagingApi = FirebaseMessagingApi.DEPRECATED_1;
                }
                else if (this.hasLatestFirebaseMessaging()){
                    firebaseMessagingApi = FirebaseMessagingApi.LATEST;
                }

                return;
            }
        }

        if (canLoadClass("com.huawei.hms.api.HuaweiApiAvailability")) {
            int result = HuaweiApiAvailability.getInstance().isHuaweiMobileServicesAvailable(context);

            if (ConnectionResult.SUCCESS == result) {
                availableMessagingApi = MessagingApi.HMS;
                return;
            }
        }

        availableMessagingApi = MessagingApi.NONE;
    }

    static ImplementationUtil getInstance(@NonNull Context context) {
        if (null != instance) {
            return instance;
        }

        synchronized (ImplementationUtil.class) {
            instance = new ImplementationUtil(context);
            return instance;
        }
    }

    MessagingApi getAvailableMessagingApi() {
        return availableMessagingApi;
    }

    FirebaseMessagingApi getAvailableFirebaseMessagingApi() {
        return firebaseMessagingApi;
    }

    private boolean canLoadClass(@NonNull String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean hasLatestFirebaseMessaging() {
        FirebaseMessaging instance = FirebaseMessaging.getInstance();
        try {
            instance.getClass().getMethod("getToken");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
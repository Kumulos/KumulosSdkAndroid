package com.kumulos.android;

import android.content.Context;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.huawei.hms.api.HuaweiApiAvailability;

import androidx.annotation.NonNull;

final class ImplementationUtil {

    private MessagingApi availableMessagingApi;

    enum MessagingApi {
        NONE,
        FCM,
        HMS
    }

    private static ImplementationUtil instance;

    private ImplementationUtil() {}

    private ImplementationUtil(@NonNull Context context) {
        if (canLoadClass("com.google.android.gms.common.GoogleApiAvailability")) {
            int result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);

            if (ConnectionResult.SUCCESS == result) {
                availableMessagingApi = MessagingApi.FCM;
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

    private boolean canLoadClass(@NonNull String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
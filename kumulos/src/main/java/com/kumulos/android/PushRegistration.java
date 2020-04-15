package com.kumulos.android;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessaging;
import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.common.ApiException;

import java.io.IOException;
import java.lang.ref.WeakReference;

import androidx.annotation.Nullable;

final class PushRegistration {

    private static final String HCM_SCOPE = "HCM";

    static class RegisterTask implements Runnable {

        private static final String TAG = RegisterTask.class.getName();

        private final WeakReference<Context> mContextRef;

        RegisterTask(Context context) {
            mContextRef = new WeakReference<>(context);
        }

        @Override
        public void run() {
            final Context context = mContextRef.get();

            if (null == context) {
                Kumulos.log(TAG, "Context null in registration task, aborting");
                return;
            }

            ImplementationUtil.MessagingApi api = ImplementationUtil.getInstance(context).getAvailableMessagingApi();

            switch (api) {
                case FCM:
                    this.registerFcm(context);
                    break;
                case HMS:
                    this.registerHms(context);
                    break;
                default:
                    Log.e(TAG, "No messaging implementation found, please ensure FCM or HMS libraries are loaded and available");
                    break;
            }
        }

        private void registerFcm(Context context) {
            Task<InstanceIdResult> result = FirebaseInstanceId.getInstance().getInstanceId();

            result.addOnSuccessListener(Kumulos.executorService, instanceIdResult ->
                    Kumulos.pushTokenStore(context, PushTokenType.FCM, instanceIdResult.getToken()));
        }

        private void registerHms(Context context) {
            try {
                String appId = getHmsAppId(context);

                if (TextUtils.isEmpty(appId)) {
                    Log.e(TAG, "HMS app ID not found, aborting");
                    return;
                }

                String token = HmsInstanceId.getInstance(context).getToken(appId, HCM_SCOPE);

                if (!TextUtils.isEmpty(token)) {
                    Kumulos.pushTokenStore(context, PushTokenType.HCM, token);
                }
            } catch (ApiException e) {
                Log.e(TAG, "get token failed, " + e);
            }
        }
    }

    static class UnregisterTask implements Runnable {

        private static final String TAG = UnregisterTask.class.getName();

        private final WeakReference<Context> mContextRef;

        UnregisterTask(Context context) {
            mContextRef = new WeakReference<>(context);
        }

        @Override
        public void run() {
            final Context context = mContextRef.get();

            if (null == context) {
                Kumulos.log(TAG, "Context null in unregistration task, aborting");
                return;
            }

            ImplementationUtil.MessagingApi api = ImplementationUtil.getInstance(context).getAvailableMessagingApi();

            switch (api) {
                case FCM:
                    this.unregisterFcm(context);
                    break;
                case HMS:
                    this.unregisterHms(context);
                    break;
                default:
                    Log.e(TAG, "No messaging implementation found, please ensure FCM or HMS libraries are loaded and available");
                    break;
            }
        }

        private void unregisterFcm(Context context) {
            Task<InstanceIdResult> result = FirebaseInstanceId.getInstance().getInstanceId();

            result.addOnSuccessListener(Kumulos.executorService, instanceIdResult -> {
                try {
                    FirebaseInstanceId.getInstance().deleteToken(instanceIdResult.getToken(),
                            FirebaseMessaging.INSTANCE_ID_SCOPE);
                    Kumulos.trackEventImmediately(context, AnalyticsContract.EVENT_TYPE_PUSH_DEVICE_UNSUBSCRIBED, null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        private void unregisterHms(Context context) {
            try {
                String appId = PushRegistration.getHmsAppId(context);

                if (TextUtils.isEmpty(appId)) {
                    Log.e(TAG, "HMS app ID not found, aborting");
                    return;
                }

                HmsInstanceId.getInstance(context).deleteToken(appId, HCM_SCOPE);
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
    }

    private static @Nullable String getHmsAppId(Context context) {
        return AGConnectServicesConfig.fromContext(context).getString("client/app_id");
    }

}

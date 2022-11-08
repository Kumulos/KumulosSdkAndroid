package com.kumulos.android;

import static com.kumulos.android.PushBroadcastReceiver.DEFAULT_CHANNEL_ID;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import com.huawei.agconnect.config.AGConnectServicesConfig;
import com.huawei.hms.aaid.HmsInstanceId;
import com.huawei.hms.common.ApiException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

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

            ImplementationUtil instance = ImplementationUtil.getInstance(context);
            ImplementationUtil.MessagingApi api = instance.getAvailableMessagingApi();

            switch (api) {
                case FCM:
                    this.requestPermissionIfNeeded(context);
                    this.registerFcm(context, instance);
                    break;
                case HMS:
                    this.requestPermissionIfNeeded(context);
                    this.registerHms(context);
                    break;
                default:
                    Log.e(TAG, "No messaging implementation found, please ensure FCM or HMS libraries are loaded and available");
                    break;
            }
        }
        private void requestPermissionIfNeeded(Context context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.getApplicationContext()
                    .getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.TIRAMISU) {
                requestPermission();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                createChannelToRequestPermission(context);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        private void createChannelToRequestPermission(Context context) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID);

            if (null == channel) {
                channel =
                        new NotificationChannel(DEFAULT_CHANNEL_ID, "General", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setSound(null, null);
                channel.setVibrationPattern(new long[]{0, 250, 250, 250});
                notificationManager.createNotificationChannel(channel);
            }
        }

        private void requestPermission() {
            Kumulos.handler.post(() -> KumulosInitProvider.getAppStateWatcher()
                    .registerListener(new AppStateWatcher.AppStateChangedListener() {
                        @Override
                        public void appEnteredForeground() {

                        }

                        @Override
                        public void activityAvailable(@NonNull Activity activity) {
                            Intent intent = new Intent(activity, RequestNotificationPermissionActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            activity.startActivity(intent);
                            KumulosInitProvider.getAppStateWatcher()
                                    .unregisterListener(this);
                        }

                        @Override
                        public void activityUnavailable(@NonNull Activity activity) {

                        }

                        @Override
                        public void appEnteredBackground() {

                        }
                    }));
        }

        private void registerFcm(Context context, ImplementationUtil instance) {
            ImplementationUtil.FirebaseMessagingApi api = instance.getAvailableFirebaseMessagingApi();

            switch (api) {
                case LATEST:
                    this.registerFcmNew(context);
                    break;
                case DEPRECATED_1:
                    this.registerFcmOld(context);
                    break;
                case UNKNOWN:
                    Log.e(TAG, "FirebaseMessaging version not supported");
                    break;
            }
        }

        @SuppressWarnings("unchecked")
        private void registerFcmNew(Context context) {
            FirebaseMessaging instance = FirebaseMessaging.getInstance();

            Exception exception = null;
            try {
                Method getToken = instance.getClass().getMethod("getToken");
                Task<String> result = (Task<String>) getToken.invoke(instance);

                result.addOnCompleteListener(Kumulos.executorService, task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed for FirebaseMessaging >=21.0.0 ", task.getException());
                        return;
                    }

                    String token = task.getResult();

                    Kumulos.pushTokenStore(context, PushTokenType.FCM, token);
                });
            } catch (NoSuchMethodException e) {
                exception = e;
            } catch (IllegalAccessException e) {
                exception = e;
            } catch (InvocationTargetException e) {
                exception = e;
            }

            if (exception != null) {
                Log.e(TAG, "Failed to get FCM token with FirebaseMessaging >=21.0.0 : " + exception.getMessage());
            }
        }


        private void registerFcmOld(Context context) {
            // Equivalent of:
            // Task<InstanceIdResult> result = com.google.firebase.iid.FirebaseInstanceId.getInstance().getInstanceId();
            // result.addOnSuccessListener(Kumulos.executorService, instanceIdResult ->
            //        Kumulos.pushTokenStore(context, PushTokenType.FCM, instanceIdResult.getToken()));

            Exception exception = null;
            try {
                Class<?> FirebaseInstanceIdClass = Class.forName("com.google.firebase.iid.FirebaseInstanceId");

                Method getInstanceMethod = FirebaseInstanceIdClass.getMethod("getInstance");
                Object instance = getInstanceMethod.invoke(null);

                Method getInstanceIdMethod = instance.getClass().getMethod("getInstanceId");
                Object task = getInstanceIdMethod.invoke(instance);

                OnSuccessListener<?> callback = instanceIdResult -> {
                    try {
                        Method getTokenMethod = instanceIdResult.getClass().getMethod("getToken");
                        String token = (String) getTokenMethod.invoke(instanceIdResult);

                        Kumulos.pushTokenStore(context, PushTokenType.FCM, token);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to get FCM token with FirebaseMessaging <21.0.0, in callback : " + e.getMessage());
                    }
                };

                Method addOnSuccessListenerMethod = task.getClass().getMethod("addOnSuccessListener", Executor.class, OnSuccessListener.class);
                addOnSuccessListenerMethod.invoke(task, Kumulos.executorService, callback);
            } catch (ClassNotFoundException e) {
                exception = e;
            } catch (NoSuchMethodException e) {
                exception = e;
            } catch (IllegalAccessException e) {
                exception = e;
            } catch (InvocationTargetException e) {
                exception = e;
            }

            if (exception != null) {
                Log.e(TAG, "Failed to get FCM token with FirebaseMessaging <21.0.0 : " + exception.getMessage());
            }
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

            ImplementationUtil instance = ImplementationUtil.getInstance(context);
            ImplementationUtil.MessagingApi api = instance.getAvailableMessagingApi();

            switch (api) {
                case FCM:
                    this.unregisterFcm(context, instance);
                    break;
                case HMS:
                    this.unregisterHms(context);
                    break;
                default:
                    Log.e(TAG, "No messaging implementation found, please ensure FCM or HMS libraries are loaded and available");
                    break;
            }
        }

        private void unregisterFcm(Context context, ImplementationUtil instance) {
            ImplementationUtil.FirebaseMessagingApi api = instance.getAvailableFirebaseMessagingApi();

            switch (api) {
                case LATEST:
                    this.unregisterFcmNew(context);
                    break;
                case DEPRECATED_1:
                    this.unregisterFcmOld(context);
                    break;
                case UNKNOWN:
                    Log.e(TAG, "FirebaseMessaging version not supported");
                    break;
            }
        }

        @SuppressWarnings("unchecked")
        private void unregisterFcmNew(Context context) {
            FirebaseMessaging instance = FirebaseMessaging.getInstance();

            Exception exception = null;
            try {
                Method deleteToken = instance.getClass().getMethod("deleteToken");
                Task<Void> result = (Task<Void>) deleteToken.invoke(instance);

                result.addOnCompleteListener(Kumulos.executorService, task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Deleting FCM registration token failed for FirebaseMessaging >=21.0.0 ", task.getException());
                        return;
                    }

                    Kumulos.trackEventImmediately(context, AnalyticsContract.EVENT_TYPE_PUSH_DEVICE_UNSUBSCRIBED, null);
                });
            } catch (NoSuchMethodException e) {
                exception = e;
            } catch (IllegalAccessException e) {
                exception = e;
            } catch (InvocationTargetException e) {
                exception = e;
            }

            if (exception != null) {
                Log.e(TAG, "Failed to get FCM token with FirebaseMessaging >=21.0.0 : " + exception.getMessage());
            }
        }

        private void unregisterFcmOld(Context context) {
            Exception exception = null;
            try {
                Class<?> FirebaseInstanceIdClass = Class.forName("com.google.firebase.iid.FirebaseInstanceId");

                Method getInstanceMethod = FirebaseInstanceIdClass.getMethod("getInstance");
                Object instance = getInstanceMethod.invoke(null);

                Method getInstanceIdMethod = instance.getClass().getMethod("getInstanceId");
                Object task = getInstanceIdMethod.invoke(instance);

                OnSuccessListener<?> callback = instanceIdResult -> {
                    try {
                        Method getTokenMethod = instanceIdResult.getClass().getMethod("getToken");
                        String token = (String) getTokenMethod.invoke(instanceIdResult);

                        Method getDeleteTokenMethod = instance.getClass().getMethod("deleteToken", String.class, String.class);
                        getDeleteTokenMethod.invoke(instance, token, FirebaseMessaging.INSTANCE_ID_SCOPE);
                        Kumulos.trackEventImmediately(context, AnalyticsContract.EVENT_TYPE_PUSH_DEVICE_UNSUBSCRIBED, null);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to delete FCM token with FirebaseMessaging <21.0.0, in callback : " + e.getMessage());
                    }
                };

                Method addOnSuccessListenerMethod = task.getClass().getMethod("addOnSuccessListener", Executor.class, OnSuccessListener.class);
                addOnSuccessListenerMethod.invoke(task, Kumulos.executorService, callback);
            } catch (ClassNotFoundException e) {
                exception = e;
            } catch (NoSuchMethodException e) {
                exception = e;
            } catch (IllegalAccessException e) {
                exception = e;
            } catch (InvocationTargetException e) {
                exception = e;
            }

            if (exception != null) {
                Log.e(TAG, "Failed to delete FCM token with FirebaseMessaging <21.0.0 : " + exception.getMessage());
            }
        }

        private void unregisterHms(Context context) {
            try {
                String appId = PushRegistration.getHmsAppId(context);

                if (TextUtils.isEmpty(appId)) {
                    Log.e(TAG, "HMS app ID not found, aborting");
                    return;
                }

                HmsInstanceId.getInstance(context).deleteToken(appId, HCM_SCOPE);
                Kumulos.trackEventImmediately(context, AnalyticsContract.EVENT_TYPE_PUSH_DEVICE_UNSUBSCRIBED, null);
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
    }

    private static @Nullable
    String getHmsAppId(Context context) {
        return AGConnectServicesConfig.fromContext(context).getString("client/app_id");
    }
}
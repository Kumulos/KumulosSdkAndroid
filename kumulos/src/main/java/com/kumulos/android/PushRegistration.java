package com.kumulos.android;

import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;
import java.lang.ref.WeakReference;

final class PushRegistration {

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

            Task<InstanceIdResult> result = FirebaseInstanceId.getInstance().getInstanceId();

            result.addOnSuccessListener(Kumulos.executorService, instanceIdResult ->
                    Kumulos.pushTokenStore(context, instanceIdResult.getToken()));
        }
    }

    static class UnregisterTask implements Runnable {

        @Override
        public void run() {
            Task<InstanceIdResult> result = FirebaseInstanceId.getInstance().getInstanceId();

            result.addOnSuccessListener(Kumulos.executorService, instanceIdResult -> {
                try {
                    FirebaseInstanceId.getInstance().deleteToken(instanceIdResult.getToken(),
                            FirebaseMessaging.INSTANCE_ID_SCOPE);
                    Kumulos.pushTokenDelete(new Kumulos.Callback() {
                        @Override
                        public void onSuccess() {
                            // noop
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

}

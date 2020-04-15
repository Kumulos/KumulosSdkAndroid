//package com.kumulos.android;
//
//import android.app.Application;
//import android.content.Context;
//import android.os.Bundle;
//
//import com.google.android.gms.gcm.GcmNetworkManager;
//import com.google.android.gms.gcm.GcmTaskService;
//import com.google.android.gms.gcm.PeriodicTask;
//import com.google.android.gms.gcm.TaskParams;
//
//public class InAppTaskService extends GcmTaskService {
//
//    private static final String TAG = "inapp-fetch";
//    private static final long DESIRED_TASK_FREQUENCY = 15 * 60L;//15 minutes
//    private static final long ACCEPTED_FREQUENCY_DEVIATION = 5 * 60L; //can run 5 min earlier
//    static final String KEY_CONFIG = "config";
//
//    void startPeriodicFetches(Context context){
//        long periodSecs = DESIRED_TASK_FREQUENCY;
//        long flexSecs = ACCEPTED_FREQUENCY_DEVIATION;
//        if (BuildConfig.DEBUG){
//            periodSecs = 30L;
//            flexSecs = 15L;
//        }
//
//        KumulosConfig config = Kumulos.getConfig();
//        Bundle bundle = new Bundle();
//        bundle.putBundle(KEY_CONFIG, config.toBundle());
//
//        PeriodicTask periodic = new PeriodicTask.Builder()
//                .setService(InAppTaskService.class)
//                .setPeriod(periodSecs)
//                .setFlex(flexSecs)
//                .setTag(TAG)
//                .setUpdateCurrent(true)
//                .setExtras(bundle)
//                .build();
//
//        new Thread(new Runnable() {
//            public void run() {
//                GcmNetworkManager.getInstance(context).schedule(periodic);
//            }
//        }).start();
//    }
//
//    void cancelPeriodicFetches(Application application){
//        new Thread(new Runnable() {
//            public void run() {
//                GcmNetworkManager.getInstance(application).cancelTask(TAG, InAppTaskService.class);
//            }
//        }).start();
//    }
//
//    @Override
//    public int onRunTask(TaskParams params) {
//        if (!Kumulos.isInitialized()) {
//            Bundle bundle = params.getExtras();
//            Bundle configBundle = bundle.getBundle(KEY_CONFIG);
//
//            if (null == configBundle) {
//                return GcmNetworkManager.RESULT_FAILURE;
//            }
//
//            KumulosConfig config = KumulosConfig.fromBundle(configBundle);
//            Kumulos.initialize(this.getApplication(), config);
//        }
//
//        boolean success = InAppMessageService.fetch(this, false);
//        if (!success){
//            return GcmNetworkManager.RESULT_RESCHEDULE;
//        }
//
//        return GcmNetworkManager.RESULT_SUCCESS;
//    }
//}

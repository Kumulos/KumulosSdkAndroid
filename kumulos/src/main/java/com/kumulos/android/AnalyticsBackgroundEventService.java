//package com.kumulos.android;
//
//import android.app.Application;
//import android.os.Bundle;
//
//import com.google.android.gms.gcm.GcmNetworkManager;
//import com.google.android.gms.gcm.GcmTaskService;
//import com.google.android.gms.gcm.TaskParams;
//
//import org.acra.ReportField;
//import org.acra.config.CoreConfigurationBuilder;
//
//public class AnalyticsBackgroundEventService extends GcmTaskService {
//
//    static final String TAG = AnalyticsBackgroundEventService.class.getName();
//    static final String EXTRAS_KEY_TIMESTAMP = "ts";
//    static final String EXTRAS_KEY_CONFIG = "config";
//
//    @Override
//    public int onRunTask(TaskParams taskParams) {
//        Bundle extras = taskParams.getExtras();
//
//        if (!Kumulos.isInitialized()) {
//            Bundle configBundle = extras.getBundle(EXTRAS_KEY_CONFIG);
//
//            if (null == configBundle) {
//                return GcmNetworkManager.RESULT_FAILURE;
//            }
//
//            KumulosConfig config = KumulosConfig.fromBundle(configBundle);
//
//            Application application = getApplication();
//
//            if (config.crashReportingEnabled()) {
//                CoreConfigurationBuilder acraBuilder = config.getAcraConfigBuilder(application);
//                acraBuilder.setReportField(ReportField.USER_EMAIL, false);
//                acraBuilder.setReportField(ReportField.LOGCAT, false);
//            }
//
//            Kumulos.initialize(application, config);
//        }
//
//        long ts = extras.getLong(EXTRAS_KEY_TIMESTAMP);
//
//        Runnable trackingTask = new AnalyticsContract.TrackEventRunnable(this, AnalyticsContract.EVENT_TYPE_BACKGROUND, ts, null, false);
//        Kumulos.executorService.submit(trackingTask);
//        AnalyticsContract.ForegroundStateWatcher.startNewSession.set(true);
//
//        return GcmNetworkManager.RESULT_SUCCESS;
//    }
//}

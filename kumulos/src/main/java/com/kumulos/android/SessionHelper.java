package com.kumulos.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SessionHelper implements AppStateWatcher.AppStateChangedListener {

    private static final String EVENT_TYPE_FOREGROUND = "k.fg";

    private final WeakReference<Context> mContextRef;
    static AtomicBoolean startNewSession;

    SessionHelper(Context context) {
        startNewSession = new AtomicBoolean(true);
        this.mContextRef = new WeakReference<>(context.getApplicationContext());

        KumulosInitProvider.getAppStateWatcher().registerListener(this);
    }

    @Override
    public void appEnteredForeground() {
        checkNotificationEnablementForStatusChange();
    }

    @Override
    public void activityAvailable(@NonNull Activity activity) {
        //Not in appEnteredForeground because need activity.
        final Context context = mContextRef.get();
        if (null == context) {
            return;
        }

        if (startNewSession.getAndSet(false)) {
            if (this.isLaunchActivity(context, activity)) {
                DeferredDeepLinkHelper.nonContinuationLinkCheckedForSession.set(false);
            }

            Kumulos.trackEvent(context, SessionHelper.EVENT_TYPE_FOREGROUND, null);

            Kumulos.executorService.submit(() -> {
                WorkManager.getInstance(context).cancelUniqueWork(AnalyticsBackgroundEventWorker.TAG);
            });
        }
    }
    private void checkNotificationEnablementForStatusChange(){
        final Context context = mContextRef.get();
        if (null == context) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);

        boolean persistedNotificationsEnabled = prefs.getBoolean(SharedPrefs.KEY_NOTIFICATIONS_ENABLEMENT_STATUS,
                false);
        boolean currentNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled();

        if (persistedNotificationsEnabled == currentNotificationsEnabled) {
            return;
        }

        Kumulos.notificationEnablementStatusChanged(context, currentNotificationsEnabled);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(SharedPrefs.KEY_NOTIFICATIONS_ENABLEMENT_STATUS, currentNotificationsEnabled);
        editor.apply();
    }

    private boolean isLaunchActivity(Context context, Activity activity) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            return false;
        }
        ComponentName component = launchIntent.getComponent();
        if (component == null) {
            return false;
        }

        return component.getClassName().equals(activity.getComponentName().getClassName());
    }


    @Override
    public void activityUnavailable(@NonNull Activity activity) {
        //noop
    }

    @Override
    public void appEnteredBackground() {
        final Context context = mContextRef.get();
        if (null == context) {
            return;
        }

        final Data input = new Data.Builder()
                .putLong(AnalyticsBackgroundEventWorker.EXTRAS_KEY_TIMESTAMP, System.currentTimeMillis())
                .build();

        Kumulos.executorService.submit(() -> {
            KumulosConfig config = Kumulos.getConfig();

            OneTimeWorkRequest.Builder taskBuilder = new OneTimeWorkRequest.Builder(AnalyticsBackgroundEventWorker.class)
                    .setInitialDelay(config.getSessionIdleTimeoutSeconds(), TimeUnit.SECONDS)
                    .setInputData(input);

            WorkManager.getInstance(context).enqueueUniqueWork(AnalyticsBackgroundEventWorker.TAG,
                    ExistingWorkPolicy.REPLACE, taskBuilder.build());
        });
    }
}

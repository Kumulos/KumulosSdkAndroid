package com.kumulos.android;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;

class InAppActivityLifecycleWatcher implements Application.ActivityLifecycleCallbacks{

    private static WeakReference<Activity> currentActivityRef = new WeakReference<>(null);

    @Nullable
    static Activity getCurrentActivity() {
        return currentActivityRef.get();
    }
    private static int numStarted = 0;
    static boolean isBackground(){
        return numStarted == 0;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        currentActivityRef = new WeakReference<Activity>(activity);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        InAppMessagePresenter.maybeCloseDialog(activity);

        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null){
            return;
        }

        if (currentActivity.hashCode() == activity.hashCode()) {
            currentActivityRef = new WeakReference<>(null);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        Integer tickleId = this.getTickleId(activity);
        if (isBackground() || tickleId != null) {
            InAppMessageService.readMessages(activity, isBackground(), tickleId);
        }

        numStarted++;
    }

    private Integer getTickleId(Activity activity){
        Intent i = activity.getIntent();
        int tickleIdExtra = i.getIntExtra(PushBroadcastReceiver.EXTRAS_KEY_TICKLE_ID, -1);
        return tickleIdExtra == -1 ? null : tickleIdExtra;
    }

    @Override
    public void onActivityStopped(Activity activity) {
        numStarted--;
    }

    @Override
    public void onActivityResumed(Activity activity) {}

    @Override
    public void onActivityPaused(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
}
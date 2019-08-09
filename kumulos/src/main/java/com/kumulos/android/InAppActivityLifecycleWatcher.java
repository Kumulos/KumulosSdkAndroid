package com.kumulos.android;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.lang.ref.WeakReference;

class InAppActivityLifecycleWatcher implements Application.ActivityLifecycleCallbacks{

    private static WeakReference<Activity> currentActivity = null;
    static WeakReference<Activity> getCurrentActivity() {
        return currentActivity;
    }
    private static int numStarted = 0;
    static boolean isBackground(){
        return numStarted == 0;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        currentActivity = new WeakReference<Activity>(activity);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        Log.d("vlad", "activity destroyed");
        InAppMessagePresenter.maybeCloseDialog(activity);

        if (currentActivity != null && currentActivity.get().hashCode() == activity.hashCode()) {Log.d("vlad", "current activity is null!!!");
            currentActivity = null;
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        Log.d("vlad", "activity started");

        Integer tickleId = this.getTickleId(activity);
        if (isBackground() || tickleId != null) {
            Log.d("vlad", isBackground() ? "onActivityStarted: app goes fg!!!" : "onActivityStarted: tickleId "+ tickleId);

            InAppMessageService.readMessages(activity, isBackground(), tickleId);

            Log.d("vlad", "read messages thread: "+Thread.currentThread().getName());

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


        Log.d("vlad", "activity stopped");
        numStarted--;
        if (numStarted == 0) {
            Log.d("vlad", "app goes bg!!!");
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {}

    @Override
    public void onActivityPaused(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
}
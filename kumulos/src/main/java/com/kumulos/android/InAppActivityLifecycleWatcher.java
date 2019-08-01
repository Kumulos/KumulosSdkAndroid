package com.kumulos.android;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class InAppActivityLifecycleWatcher implements Application.ActivityLifecycleCallbacks{

    private static WeakReference<Activity> currentActivity = null;
    static WeakReference<Activity> getCurrentActivity() {
        return currentActivity;
    }
    private int numStarted = 0;

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        currentActivity = new WeakReference<Activity>(activity);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (currentActivity != null && currentActivity.get().hashCode() == activity.hashCode()) {
            currentActivity = null;
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (numStarted == 0) {
            Log.d("vlad", "app goes fg!!!");

            Callable<List<InAppMessage>> task = new InAppContract.ReadInAppMessagesCallable(activity);
            final Future<List<InAppMessage>> future = Kumulos.executorService.submit(task);

            List<InAppMessage> itemsToPresent;
            try {
                itemsToPresent = future.get();
            } catch (InterruptedException | ExecutionException ex) {
                return;
            }

            Log.d("vlad", "read messages thread: "+Thread.currentThread().getName());
            InAppMessagePresenter.getInstance().presentMessages(itemsToPresent);//TODO: can multiple threads call this simultaneously?
        }
        numStarted++;
    }

    @Override
    public void onActivityStopped(Activity activity) {
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

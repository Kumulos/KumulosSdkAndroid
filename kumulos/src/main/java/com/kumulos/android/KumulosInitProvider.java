package com.kumulos.android;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class KumulosInitProvider extends ContentProvider {
    private static final AppStateWatcher appStateWatcher = new AppStateWatcher();

    @NonNull
    static AppStateWatcher getAppStateWatcher() {
        return appStateWatcher;
    }

    @Override
    public boolean onCreate() {
        Application application = (Application) getContext().getApplicationContext();
        application.registerActivityLifecycleCallbacks(appStateWatcher);

        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    interface AppStateChangedListener {
        @UiThread
        void appEnteredForeground();

        @UiThread
        void activityAvailable(@NonNull Activity activity);

        @UiThread
        void activityUnavailable(@NonNull Activity activity);

        @UiThread
        void appEnteredBackground();
    }

    static class AppStateWatcher implements Application.ActivityLifecycleCallbacks {

        private final Handler handler;
        private int runningActivities;
        private final List<AppStateChangedListener> listeners;
        private boolean appInForeground;
        private WeakReference<Activity> currentActivityRef;

        AppStateWatcher() {
            handler = new Handler(Looper.getMainLooper());
            listeners = new ArrayList<>(2);
            currentActivityRef = new WeakReference<>(null);
            appInForeground = false;
        }

        @UiThread
        void registerListener(AppStateChangedListener listener) {
            listeners.add(listener);

            if (appInForeground) {
                listener.appEnteredForeground();
            }

            Activity current = currentActivityRef.get();
            if (null != current) {
                listener.activityAvailable(current);
            }
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {

        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {

        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            ++runningActivities;

            if (1 == runningActivities && !appInForeground) {
                Kumulos.log("APPSTATE", "appEnteredForeground");
                appInForeground = true;

                for (AppStateChangedListener listener : listeners) {
                    listener.appEnteredForeground();
                }
            }

            Activity current = currentActivityRef.get();
            if (current != activity) {
                Kumulos.log("APPSTATE", "activityAvailable");
                currentActivityRef = new WeakReference<>(activity);
                for (AppStateChangedListener listener : listeners) {
                    listener.activityAvailable(activity);
                }
            }
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
            --runningActivities;

            handler.postDelayed(() -> {
                if (0 == runningActivities) {
                    appInForeground = false;
                    for (AppStateChangedListener listener : listeners) {
                        Kumulos.log("APPSTATE", "appEnteredBackground");
                        listener.appEnteredBackground();
                    }
                }
            }, 700);
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            for (AppStateChangedListener listener : listeners) {
                listener.activityUnavailable(activity);
            }
        }
    }
}

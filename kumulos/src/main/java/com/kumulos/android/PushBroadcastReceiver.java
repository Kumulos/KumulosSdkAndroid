package com.kumulos.android;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class PushBroadcastReceiver extends BroadcastReceiver {

    public static final String TAG = PushBroadcastReceiver.class.getName();

    public static final String ACTION_PUSH_RECEIVED = "com.kumulos.push.RECEIVED";
    public static final String ACTION_PUSH_OPENED = "com.kumulos.push.OPENED";

    private static final String DEFAULT_CHANNEL_ID = "general";

    @Override
    final public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        PushMessage pushMessage = intent.getParcelableExtra(PushMessage.EXTRAS_KEY);

        if (null == action) {
            return;
        }

        switch (action) {
            case ACTION_PUSH_RECEIVED:
                this.onPushReceived(context, pushMessage);
                break;
            case ACTION_PUSH_OPENED:
                this.onPushOpened(context, pushMessage);
                break;
        }
    }

    /**
     * Handles showing a notification in the notification drawer when a content push is received.
     *
     * @param context
     * @param pushMessage
     * @see PushBroadcastReceiver#buildNotification(Context, PushMessage) for cusomization
     */
    protected void onPushReceived(Context context, PushMessage pushMessage) {
        Kumulos.log(TAG, "Push received");

        if (pushMessage.isBackgroundPush()) {
            Intent serviceIntent = getBackgroundPushServiceIntent(context, pushMessage);

            if (null == serviceIntent) {
                return;
            }

            ComponentName component = serviceIntent.getComponent();
            if (null == component) {
                Kumulos.log(TAG, "Service intent did not specify a component, ignoring.");
                return;
            }

            Class<? extends Service> cls = null;
            try {
                cls = (Class<? extends Service>) Class.forName(component.getClassName());
            } catch (ClassNotFoundException e) {
                Kumulos.log(TAG, "Service intent to handle a data push was provided, but it is not for a Service, check: " + component.getClassName());
            }

            if (null != cls) {
                context.startService(serviceIntent);
            }

            return;
        }
        else if (!pushMessage.hasTitleAndMessage()) {
            // Non-background pushes should always have a title & message otherwise we can't show a notification
            return;
        }

        Notification notification = buildNotification(context, pushMessage);

        if (null == notification) {
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (null == notificationManager) {
            return;
        }

        // TODO fix this in 2038 when we run out of time
        notificationManager.notify((int) pushMessage.getTimeSent(), notification);
    }

    /**
     * Handles launching the Activity specified by the {#getPushOpenActivityIntent} method when a push
     * notification is opened from the notifications drawer.
     *
     * @param context
     * @param pushMessage
     * @see PushBroadcastReceiver#getPushOpenActivityIntent(Context, PushMessage) for customization
     */
    protected void onPushOpened(Context context, PushMessage pushMessage) {
        Kumulos.log(TAG, "Push opened");

        try {
            Kumulos.pushTrackOpen(context, pushMessage.getId());
        } catch (Kumulos.UninitializedException e) {
            Kumulos.log(TAG, "Failed to track the push opening -- Kumulos is not initialised.");
        }

        Intent launchIntent = getPushOpenActivityIntent(context, pushMessage);

        if (null == launchIntent) {
            return;
        }

        ComponentName component = launchIntent.getComponent();
        if (null == component) {
            Kumulos.log(TAG, "Intent to handle push notification open does not specify a component, ignoring. Override PushBroadcastReceiver#onPushOpened to change this behaviour.");
            return;
        }

        Class<? extends Activity> cls = null;
        try {
            cls = (Class<? extends Activity>) Class.forName(component.getClassName());
        } catch (ClassNotFoundException e) {
            Kumulos.log(TAG, "Activity intent to handle a content push open was provided, but it is not for an Activity, check: " + component.getClassName());
        }

        // Ensure we're trying to launch an Activity
        if (null == cls) {
            return;
        }

        if (null != pushMessage.getUrl()) {
            launchIntent = new Intent(Intent.ACTION_VIEW, pushMessage.getUrl());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);
            taskStackBuilder.addParentStack(component);
            taskStackBuilder.addNextIntent(launchIntent);

            taskStackBuilder.startActivities();
            return;
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launchIntent);
    }

    /**
     * Builds the notification shown in the notification drawer when a content push is received.
     * <p/>
     * Defaults to using the application's icon.
     * <p/>
     * Override to customize the notification shown.
     *
     * @param context
     * @param pushMessage
     * @return
     * @see Kumulos#pushTrackOpen(Context,String) for correctly tracking conversions if you customize the content intent
     */
    protected Notification buildNotification(Context context, PushMessage pushMessage) {
        Intent openIntent = new Intent(ACTION_PUSH_OPENED);
        openIntent.putExtra(PushMessage.EXTRAS_KEY, pushMessage);
        openIntent.setPackage(context.getPackageName());

        PendingIntent pendingOpenIntent = PendingIntent.getBroadcast(
                context,
                (int) pushMessage.getTimeSent(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

        Notification.Builder notificationBuilder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (null == notificationManager) {
                return null;
            }

            NotificationChannel channel = notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID);
            if (null == channel) {
                channel = new NotificationChannel(DEFAULT_CHANNEL_ID, "General", NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
            }

            notificationBuilder = new Notification.Builder(context, "general");
        }
        else {
            notificationBuilder = new Notification.Builder(context);
        }

        KumulosConfig config = Kumulos.getConfig();
        int icon = config != null ? config.getNotificationSmallIconId() : KumulosConfig.DEFAULT_NOTIFICATION_ICON_ID;

        notificationBuilder
                .setSmallIcon(icon)
                .setContentTitle(pushMessage.getTitle())
                .setContentText(pushMessage.getMessage())
                .setAutoCancel(true)
                .setContentIntent(pendingOpenIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return notificationBuilder.build();
        }

        return notificationBuilder.getNotification();
    }

    /**
     * Returns the Intent to launch when a push notification is opened from the notification drawer.
     * <p/>
     * The Intent must specify an Activity component or it will be ignored.
     * <p/>
     * Override to change the launched Activity when a push notification is opened.
     *
     * @param context
     * @param pushMessage
     * @return
     */
    protected Intent getPushOpenActivityIntent(Context context, PushMessage pushMessage) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());

        if (null == launchIntent) {
            return null;
        }

        launchIntent.putExtra(PushMessage.EXTRAS_KEY, pushMessage);
        return launchIntent;
    }

    /**
     * If you want a service started when a background data push is received, override this method.
     * <p/>
     * The intent must specify a Service component or it will be ignored.
     * <p/>
     * Return null to silently ignore the data push. This is the default behaviour.
     *
     * @param context
     * @param pushMessage
     * @return
     */
    protected Intent getBackgroundPushServiceIntent(Context context, PushMessage pushMessage) {
        return null;
    }
}

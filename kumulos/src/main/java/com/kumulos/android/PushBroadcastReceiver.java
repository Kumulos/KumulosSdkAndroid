package com.kumulos.android;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.DisplayMetrics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

public class PushBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = PushBroadcastReceiver.class.getName();

    public static String ACTION_PUSH_RECEIVED = "com.kumulos.push.RECEIVED";
    public static String ACTION_PUSH_OPENED = "com.kumulos.push.OPENED";
    public static String ACTION_PUSH_DISMISSED = "com.kumulos.push.DISMISSED";
    public static String ACTION_BUTTON_CLICKED = "com.kumulos.push.BUTTON_CLICKED";

    static final String EXTRAS_KEY_TICKLE_ID = "com.kumulos.inapp.tickle.id";
    static final String EXTRAS_KEY_BUTTON_ID = "com.kumulos.push.message.button.id";

    static final String DEFAULT_CHANNEL_ID = "kumulos_general_v3";
    static final String IMPORTANT_CHANNEL_ID = "kumulos_important_v1";
    protected static final String KUMULOS_NOTIFICATION_TAG = "kumulos";

    @Override
    final public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        PushMessage pushMessage = intent.getParcelableExtra(PushMessage.EXTRAS_KEY);

        if (null == action || pushMessage == null) {
            return;
        }

        if (action.equals(ACTION_PUSH_RECEIVED)) {
            this.onPushReceived(context, pushMessage);
        } else if (action.equals(ACTION_PUSH_OPENED)) {
            this.onPushOpened(context, pushMessage);
        } else if (action.equals(ACTION_PUSH_DISMISSED)) {
            this.onPushDismissed(context, pushMessage);
        } else if (action.equals(ACTION_BUTTON_CLICKED)) {
            String buttonIdentifier = intent.getStringExtra(PushBroadcastReceiver.EXTRAS_KEY_BUTTON_ID);
            this.handleButtonClick(context, pushMessage, buttonIdentifier);
        }
    }

    /**
     * Handles showing a notification in the notification drawer when a content push is received.
     *
     * @param context
     * @param pushMessage
     * Override and use custom notification builder for complete control.
     */
    protected void onPushReceived(Context context, PushMessage pushMessage) {
        Kumulos.log(TAG, "Push received");

        this.pushTrackDelivered(context, pushMessage);

        this.maybeTriggerInAppSync(context, pushMessage);

        if (pushMessage.runBackgroundHandler()) {
            this.onBackgroundPush(context, pushMessage);
        }

        if (!pushMessage.hasTitleAndMessage()) {
            // Always show Notification if has title + message
            return;
        }

        Notification.Builder builder = getNotificationBuilder(context, pushMessage);
        if (null == builder) {
            return;
        }

        String pictureUrl = pushMessage.getPictureUrl();
        if (pictureUrl != null) {
            final PendingResult pendingResult = goAsync();
            new LoadNotificationPicture(context, pendingResult, builder, pushMessage).execute();

            return;
        }

        this.showNotification(context, pushMessage, builder.build());
    }

    private void showNotification(Context context, PushMessage pushMessage, Notification notification) {

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (null == notificationManager) {
            return;
        }

        notificationManager.notify(KUMULOS_NOTIFICATION_TAG, this.getNotificationId(pushMessage), notification);
    }

    protected void pushTrackDelivered(Context context, PushMessage pushMessage) {
        try {
            JSONObject params = new JSONObject();
            params.put("type", AnalyticsContract.MESSAGE_TYPE_PUSH);
            params.put("id", pushMessage.getId());

            Kumulos.trackEvent(context, AnalyticsContract.EVENT_TYPE_MESSAGE_DELIVERED, params);
        } catch (JSONException e) {
            Kumulos.log(TAG, e.toString());
        }
    }

    protected void maybeTriggerInAppSync(Context context, PushMessage pushMessage) {
        if (!KumulosInApp.isInAppEnabled()) {
            return;
        }

        int tickleId = pushMessage.getTickleId();
        if (tickleId == -1) {
            return;
        }

        Kumulos.executorService.submit(new Runnable() {
            @Override
            public void run() {
                InAppMessageService.fetch(context, false);
            }
        });
    }

    private int getNotificationId(PushMessage pushMessage) {
        int tickleId = pushMessage.getTickleId();
        if (tickleId == -1) {
            // TODO fix this in 2038 when we run out of time
            return (int) pushMessage.getTimeSent();
        }
        return tickleId;
    }

    /**
     * Builder for the notification shown in the notification drawer when a content push is received.
     * <p/>
     * Defaults to using the application's icon.
     * <p/>
     * To customize the notification shown you can override builder settings.
     * <p>
     * Also sets the intent specified by the {#getPushOpenActivityIntent} method when a push notification is opened
     * from the notifications drawer.
     *
     * @param context
     * @param pushMessage
     * @return
     * @see PushBroadcastReceiver#getPushOpenActivityIntent(Context, PushMessage) for customization
     */
    protected @Nullable Notification.Builder getNotificationBuilder(Context context, PushMessage pushMessage) {
        PendingIntent pendingOpenIntent = this.getPendingOpenIntent(context, pushMessage);
        PendingIntent pendingDismissedIntent = this.getPendingDismissedIntent(context, pushMessage);

        Notification.Builder notificationBuilder;

        NotificationManager notificationManager = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (null == notificationManager) {
                return null;
            }

            this.channelSetup(notificationManager);

            if (notificationManager.getNotificationChannel(pushMessage.getChannel()) == null) {
                notificationBuilder = new Notification.Builder(context, DEFAULT_CHANNEL_ID);
            } else {
                notificationBuilder = new Notification.Builder(context, pushMessage.getChannel());
            }
        } else {
            notificationBuilder = new Notification.Builder(context);
        }

        KumulosConfig config = Kumulos.getConfig();
        int icon = config != null ? config.getNotificationSmallIconId() : KumulosConfig.DEFAULT_NOTIFICATION_ICON_ID;

        notificationBuilder
                .setSmallIcon(icon)
                .setContentTitle(pushMessage.getTitle())
                .setContentText(pushMessage.getMessage())
                .setAutoCancel(true)
                .setContentIntent(pendingOpenIntent)
                .setDeleteIntent(pendingDismissedIntent);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            int priority = pushMessage.getChannel().equals(IMPORTANT_CHANNEL_ID) ? Notification.PRIORITY_MAX : Notification.PRIORITY_DEFAULT;
            notificationBuilder.setPriority(priority);
        }

        this.maybeAddSound(context, notificationBuilder, notificationManager, pushMessage);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationBuilder.setShowWhen(true);
        }

        notificationBuilder.setStyle(new Notification.BigTextStyle().bigText(pushMessage.getMessage()));

        JSONArray buttons = pushMessage.getButtons();
        if (buttons != null) {
            this.attachButtons(context, pushMessage, notificationBuilder, buttons);
        }

        return notificationBuilder;
    }

    private PendingIntent getPendingOpenIntent(Context context, PushMessage pushMessage) {
        List<Intent> intentList = new ArrayList<>();
        //launch intent must come 1st, FLAG_ACTIVITY_NEW_TASK
        Intent launchIntent = getLaunchIntent(context, pushMessage);
        if (launchIntent != null) {
            intentList.add(launchIntent);
        }

        //open tracking intent starts invisible activity on top of stack or in a new task if no launch intent
        Intent kumulosPushOpenIntent = new Intent(context, PushOpenInvisibleActivity.class);
        kumulosPushOpenIntent.putExtra(PushMessage.EXTRAS_KEY, pushMessage);
        kumulosPushOpenIntent.setPackage(context.getPackageName());
        if (launchIntent == null || null != pushMessage.getUrl()) {
            kumulosPushOpenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        }

        intentList.add(kumulosPushOpenIntent);

        Intent[] intents = new Intent[intentList.size()];
        intentList.toArray(intents);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getActivities(context, (int) pushMessage.getTimeSent(), intents, flags);
    }

    private @Nullable
    Intent getLaunchIntent(Context context, PushMessage pushMessage) {
        Intent launchIntent = getPushOpenActivityIntent(context, pushMessage);

        if (null == launchIntent) {
            return null;
        }

        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        ComponentName component = launchIntent.getComponent();
        if (null == component) {
            Kumulos.log(TAG, "Intent to handle push notification open does not specify a component, ignoring. Override PushBroadcastReceiver#getPushOpenActivityIntent to change this behaviour.");
            return null;
        }

        Class<? extends Activity> cls = null;
        try {
            cls = Class.forName(component.getClassName()).asSubclass(Activity.class);
        } catch (ClassNotFoundException e) {
            Kumulos.log(TAG, "Activity intent to handle a content push open was provided, but it is not for an Activity, check: " + component.getClassName());
        }

        // Ensure we're trying to launch an Activity
        if (null == cls) {
            return null;
        }

        if (null != pushMessage.getUrl()) {
            launchIntent = new Intent(Intent.ACTION_VIEW, pushMessage.getUrl());
        }

        addDeepLinkExtras(pushMessage, launchIntent);

        return launchIntent;
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

    private PendingIntent getPendingDismissedIntent(Context context, PushMessage pushMessage) {
        Intent intent = new Intent(ACTION_PUSH_DISMISSED);

        intent.putExtra(PushMessage.EXTRAS_KEY, pushMessage);
        intent.setPackage(context.getPackageName());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getBroadcast(
                context,
                (int) pushMessage.getTimeSent() - 1,
                intent,
                flags);
    }

    private void channelSetup(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID);
        NotificationChannel importantChannel = notificationManager.getNotificationChannel(IMPORTANT_CHANNEL_ID);

        //- Signalling a change / update to SDK
        if (null == channel || null == importantChannel) {
            this.clearOldChannels(notificationManager);
        }

        if (null == channel) {
            channel = new NotificationChannel(DEFAULT_CHANNEL_ID, "General", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setSound(null, null);
            channel.setVibrationPattern(new long[]{0, 250, 250, 250});
            notificationManager.createNotificationChannel(channel);
        }

        if (null == importantChannel) {
            channel = new NotificationChannel(IMPORTANT_CHANNEL_ID, "Important", NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null, null);
            channel.setVibrationPattern(new long[]{0, 250, 250, 250});
            notificationManager.createNotificationChannel(channel);
        }
    }

    @TargetApi(android.os.Build.VERSION_CODES.O)
    private void clearOldChannels(NotificationManager notificationManager) {
        //Initial setup of channels changed multiple times. Remove old channels
        String[] oldChannelIds = {"general", "kumulos_general"};

        for (String channelId : oldChannelIds) {
            NotificationChannel oldChannel = notificationManager.getNotificationChannel(channelId);
            if (oldChannel != null) {
                notificationManager.deleteNotificationChannel(channelId);
            }
        }
    }

    private void maybeAddSound(Context context, Notification.Builder notificationBuilder, @Nullable NotificationManager notificationManager, PushMessage pushMessage) {
        String soundFileName = pushMessage.getSound();

        Uri ringtoneSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (soundFileName != null) {
            ringtoneSound = Uri.parse("android.resource://" + context.getPackageName() + "/raw/" + soundFileName);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationBuilder.setSound(ringtoneSound);
            return;
        }

        if (notificationManager == null) {
            return;
        }

        NotificationChannel channel = notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID);
        if (channel.getSound() != null) {
            return;
        }

        if (channel.getImportance() <= NotificationManager.IMPORTANCE_LOW) {
            return;
        }

        int filter = notificationManager.getCurrentInterruptionFilter();
        boolean inDnD = false;
        switch (filter) {
            case NotificationManager.INTERRUPTION_FILTER_ALL:
                inDnD = false;
                break;
            case NotificationManager.INTERRUPTION_FILTER_PRIORITY:
                inDnD = !channel.canBypassDnd();
                break;
            case NotificationManager.INTERRUPTION_FILTER_UNKNOWN:
            case NotificationManager.INTERRUPTION_FILTER_ALARMS:
            case NotificationManager.INTERRUPTION_FILTER_NONE:
                inDnD = true;
        }

        if (inDnD) {
            return;
        }

        try {
            Ringtone r = RingtoneManager.getRingtone(context, ringtoneSound);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void attachButtons(Context context, PushMessage pushMessage, Notification.Builder notificationBuilder, JSONArray buttons) {
        for (int i = 0; i < buttons.length(); i++) {
            try {
                JSONObject button = buttons.getJSONObject(i);
                String label = button.getString("text");
                String buttonId = button.getString("id");

                Intent clickIntent = new Intent(context, PushOpenInvisibleActivity.class);
                clickIntent.putExtra(PushMessage.EXTRAS_KEY, pushMessage);
                clickIntent.putExtra(PushBroadcastReceiver.EXTRAS_KEY_BUTTON_ID, buttonId);
                clickIntent.setPackage(context.getPackageName());
                clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

                int flags = PendingIntent.FLAG_ONE_SHOT;
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }

                PendingIntent pendingClickIntent = PendingIntent.getActivity(
                        context,
                        ((int) pushMessage.getTimeSent()) + (i + 1),
                        clickIntent,
                        flags);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    notificationBuilder.addAction(0, label, pendingClickIntent);
                } else {
                    Notification.Action action = new Notification.Action.Builder(null, label, pendingClickIntent).build();

                    notificationBuilder.addAction(action);
                }
            } catch (JSONException e) {
                Kumulos.log(e.toString());
            }

        }
    }

    private class LoadNotificationPicture extends AsyncTask<Void, Void, Bitmap> {
        private final Notification.Builder builder;
        private final Context context;
        private final PushMessage pushMessage;
        private final PendingResult pendingResult;

        //Theoretical time limit for BroadcastReceiver's bg execution is 30s. Leave 6s for connection.
        //Practically ANR doesnt happen with even bigger 40+s timeouts.
        private final int READ_TIMEOUT = 24000;
        private final int CONNECTION_TIMEOUT = 6000;

        LoadNotificationPicture(Context context, PendingResult pendingResult, Notification.Builder builder, PushMessage pushMessage) {
            super();

            this.builder = builder;
            this.pushMessage = pushMessage;
            this.context = context;
            this.pendingResult = pendingResult;
        }

        private URL getPictureUrl() throws MalformedURLException {
            String pictureUrl = this.pushMessage.getPictureUrl();
            if (pictureUrl == null) {
                throw new RuntimeException("Kumulos: pictureUrl cannot be null at this point");
            }

            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
            return MediaHelper.getCompletePictureUrl(pictureUrl, metrics.widthPixels);
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            InputStream in;
            try {
                URL url = this.getPictureUrl();

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(CONNECTION_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);

                connection.connect();
                in = connection.getInputStream();
                return BitmapFactory.decodeStream(in);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (SocketTimeoutException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);

            if (result == null) {
                Notification notification = this.builder.build();
                PushBroadcastReceiver.this.showNotification(this.context, this.pushMessage, notification);

                pendingResult.finish();
                return;
            }

            Notification notification = this.builder
                    .setLargeIcon(result)
                    .setStyle(new Notification.BigPictureStyle()
                            .bigPicture(result)
                            .bigLargeIcon((Bitmap) null))
                    .build();

            PushBroadcastReceiver.this.showNotification(this.context, this.pushMessage, notification);
            pendingResult.finish();
        }
    }

    /**
     * Used to add Kumulos extras when using custom notification builder
     *
     * @param pushMessage
     * @param launchIntent
     */
    protected static void addDeepLinkExtras(PushMessage pushMessage, Intent launchIntent) {
        if (!KumulosInApp.isInAppEnabled()) {
            return;
        }

        int tickleId = pushMessage.getTickleId();
        if (tickleId == -1) {
            return;
        }

        launchIntent.putExtra(PushBroadcastReceiver.EXTRAS_KEY_TICKLE_ID, tickleId);
    }

    /**
     * If you want to enqueue work when a background data push is received, override this method.
     *
     * @param context
     * @param pushMessage
     * @return
     */
    protected void onBackgroundPush(Context context, PushMessage pushMessage) {
//        WorkManager workManager = WorkManager.getInstance(context);
//        workManager.enqueue(new OneTimeWorkRequest.Builder(MyWorker.class).build());
    }

    /**
     * Handles Kumulos push open tracking. Call parent if override.
     *
     * @param context
     * @param pushMessage
     */
    protected void onPushOpened(Context context, PushMessage pushMessage) {
        Kumulos.log(TAG, "Push opened");

        try {
            Kumulos.pushTrackOpen(context, pushMessage.getId());
        } catch (Kumulos.UninitializedException e) {
            Kumulos.log(TAG, "Failed to track the push opening -- Kumulos is not initialised.");
        }
    }

    /**
     * Handles Kumulos push dismissed tracking. Call parent if override.
     *
     * @param context
     * @param pushMessage
     */
    protected void onPushDismissed(Context context, PushMessage pushMessage) {
        Kumulos.log(TAG, "Push dismissed");

        try {
            Kumulos.pushTrackDismissed(context, pushMessage.getId());
        } catch (Kumulos.UninitializedException e) {
            Kumulos.log(TAG, "Failed to track the push dismissal -- Kumulos is not initialised.");
        }
    }

    /**
     * Handles action button clicks
     *
     * @param context
     * @param buttonIdentifier
     */
    private void handleButtonClick(Context context, PushMessage pushMessage, String buttonIdentifier) {
        try {
            Kumulos.pushTrackOpen(context, pushMessage.getId());
        } catch (Kumulos.UninitializedException e) {
            Kumulos.log(TAG, "Failed to track the push opening won button click -- Kumulos is not initialised.");
        }

        if (Kumulos.pushActionHandler != null) {
            Kumulos.pushActionHandler.handle(context, pushMessage, buttonIdentifier);
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (null == notificationManager) {
            return;
        }

        notificationManager.cancel(PushBroadcastReceiver.KUMULOS_NOTIFICATION_TAG, this.getNotificationId(pushMessage));
    }
}

package com.kumulos.android;

import android.annotation.TargetApi;
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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import androidx.annotation.Nullable;

public class PushBroadcastReceiver extends BroadcastReceiver {
    public static final String TAG = PushBroadcastReceiver.class.getName();

    public static final String ACTION_PUSH_RECEIVED = "com.kumulos.push.RECEIVED";
    public static final String ACTION_PUSH_OPENED = "com.kumulos.push.OPENED";
    public static final String ACTION_BUTTON_CLICKED = "com.kumulos.push.BUTTON_CLICKED";

    static final String EXTRAS_KEY_TICKLE_ID = "com.kumulos.inapp.tickle.id";
    static final String EXTRAS_KEY_BUTTON_ID = "com.kumulos.push.message.button.id";

    private static final String DEFAULT_CHANNEL_ID = "kumulos_general";
    private static final String DEFAULT_CHANNEL_ID_v3 = "kumulos_general_v3";
    protected static final String KUMULOS_NOTIFICATION_TAG = "kumulos";

    private static final String MEDIA_RESIZER_BASE_URL = "https://i.app.delivery";

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
            case ACTION_BUTTON_CLICKED:
                String buttonIdentifier = intent.getStringExtra(PushBroadcastReceiver.EXTRAS_KEY_BUTTON_ID);
                this.handleButtonClick(context, pushMessage, buttonIdentifier);
                break;

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

        if (Kumulos.pushActionHandler != null){
            Kumulos.pushActionHandler.handle(context, pushMessage, buttonIdentifier);
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (null == notificationManager) {
            return;
        }

        notificationManager.cancel(PushBroadcastReceiver.KUMULOS_NOTIFICATION_TAG, this.getNotificationId(pushMessage));
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

        this.pushTrackDelivered(context, pushMessage);

        this.maybeTriggerInAppSync(context, pushMessage);

        if (pushMessage.runBackgroundHandler()) {
            this.runBackgroundHandler(context, pushMessage);
        }

        if (!pushMessage.hasTitleAndMessage()) {
            // Always show Notification if has title + message
            return;
        }

        Notification notification = buildNotification(context, pushMessage);

        if (null == notification) {
            return;
        }

        this.showNotification(context, pushMessage, notification);
    }

    private void showNotification(Context context, PushMessage pushMessage, Notification notification){

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (null == notificationManager) {
            return;
        }

        notificationManager.notify(KUMULOS_NOTIFICATION_TAG, this.getNotificationId(pushMessage), notification);
    }

    protected void pushTrackDelivered(Context context, PushMessage pushMessage){
        try {
            JSONObject params = new JSONObject();
            params.put("type", AnalyticsContract.MESSAGE_TYPE_PUSH);
            params.put("id", pushMessage.getId());

            Kumulos.trackEvent(context, AnalyticsContract.EVENT_TYPE_MESSAGE_DELIVERED, params);
        }
        catch(JSONException e){
            Kumulos.log(TAG, e.toString());
        }
    }

    protected void maybeTriggerInAppSync(Context context, PushMessage pushMessage){
        if (!KumulosInApp.isInAppEnabled()){
            return;
        }

        int tickleId = pushMessage.getTickleId();
        if (tickleId == -1){
            return;
        }

        Kumulos.executorService.submit(new Runnable() {
            @Override
            public void run() {
                InAppMessageService.fetch(context, false);
            }
        });
    }

    private int getNotificationId(PushMessage pushMessage){
        int tickleId = pushMessage.getTickleId();
        if (tickleId == -1){
            // TODO fix this in 2038 when we run out of time
            return (int) pushMessage.getTimeSent();
        }
        return tickleId;
    }

    private void runBackgroundHandler(Context context, PushMessage pushMessage){
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
            cls = Class.forName(component.getClassName()).asSubclass(Service.class);
        } catch (ClassNotFoundException e) {
            Kumulos.log(TAG, "Service intent to handle a data push was provided, but it is not for a Service, check: " + component.getClassName());
        }

        if (null != cls) {
            context.startService(serviceIntent);
        }
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
            cls = Class.forName(component.getClassName()).asSubclass(Activity.class);
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

        addDeepLinkExtras(pushMessage, launchIntent);

        TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);
        taskStackBuilder.addParentStack(component);
        taskStackBuilder.addNextIntent(launchIntent);

        taskStackBuilder.startActivities();
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
     * @see Kumulos#pushTrackOpen(Context,int) for correctly tracking conversions if you customize the content intent
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

        NotificationManager notificationManager = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (null == notificationManager) {
                return null;
            }

            NotificationChannel channel = notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID_v3);
            if (null == channel) {
                this.clearOldChannels(notificationManager);

                channel = new NotificationChannel(DEFAULT_CHANNEL_ID_v3, "General", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setSound(null, null);
                channel.setVibrationPattern(new long[]{0, 250, 250, 250});
                notificationManager.createNotificationChannel(channel);
            }

            notificationBuilder = new Notification.Builder(context, DEFAULT_CHANNEL_ID_v3);
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

        this.maybeAddSound(context, notificationBuilder, notificationManager, pushMessage);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
             notificationBuilder.setShowWhen(true);
        }

        notificationBuilder.setStyle(new Notification.BigTextStyle().bigText(pushMessage.getMessage()));

        JSONArray buttons = pushMessage.getButtons();
        if (buttons != null){
            this.attachButtons(context, pushMessage, notificationBuilder, buttons);
        }

        String pictureUrl = pushMessage.getPictureUrl();
        if (pictureUrl != null){
            final PendingResult pendingResult = goAsync();
            new LoadNotificationPicture(context, pendingResult, notificationBuilder, pushMessage).execute();

            return null;
        }
        return notificationBuilder.build();
    }

    @TargetApi(android.os.Build.VERSION_CODES.O)
    private void clearOldChannels(NotificationManager notificationManager){
        //Initial setup of channels changed multiple times. Remove old channels
        NotificationChannel oldChannel = notificationManager.getNotificationChannel("general");
        if (oldChannel != null){
            notificationManager.deleteNotificationChannel("general");
        }

        NotificationChannel oldChannel2 = notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID);
        if (oldChannel2 != null){
            notificationManager.deleteNotificationChannel(DEFAULT_CHANNEL_ID);
        }
    }

    private void maybeAddSound(Context context, Notification.Builder notificationBuilder, @Nullable NotificationManager notificationManager, PushMessage pushMessage){
        String soundFileName = pushMessage.getSound();

        Uri ringtoneSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (soundFileName != null){
            ringtoneSound =  Uri.parse("android.resource://"+context.getPackageName()+"/raw/"+soundFileName);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O){
            notificationBuilder.setSound(ringtoneSound);
            return;
        }

        if (notificationManager == null){
            return;
        }

        NotificationChannel channel = notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID_v3);
        if (channel.getSound() != null){
            return;
        }

        if (channel.getImportance() <= NotificationManager.IMPORTANCE_LOW) {
            return;
        }

        int filter = notificationManager.getCurrentInterruptionFilter();
        boolean inDnD = false;
        switch(filter){
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

        if (inDnD){
            return;
        }

        try {
            Ringtone r = RingtoneManager.getRingtone(context, ringtoneSound);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void attachButtons(Context context, PushMessage pushMessage, Notification.Builder notificationBuilder, JSONArray buttons){
        for (int i = 0; i < buttons.length(); i++) {
            try{
                JSONObject button = buttons.getJSONObject(i);
                String label = button.getString("text");
                String buttonId = button.getString("id");

                Intent clickIntent = new Intent(ACTION_BUTTON_CLICKED);
                clickIntent.putExtra(PushMessage.EXTRAS_KEY, pushMessage);
                clickIntent.putExtra(PushBroadcastReceiver.EXTRAS_KEY_BUTTON_ID, buttonId);
                clickIntent.setPackage(context.getPackageName());

                PendingIntent pendingClickIntent = PendingIntent.getBroadcast(
                        context,
                        ((int) pushMessage.getTimeSent()) + (i+1),
                        clickIntent,
                        PendingIntent.FLAG_ONE_SHOT);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M){
                    notificationBuilder.addAction(0, label, pendingClickIntent);
                }
                else {
                    Notification.Action action = new Notification.Action.Builder(null, label, pendingClickIntent).build();

                    notificationBuilder.addAction(action);
                }
            }
            catch(JSONException e){
                Kumulos.log(e.toString());
            }

        }
    }

    private class LoadNotificationPicture extends AsyncTask<Void, Void, Bitmap> {
        private Notification.Builder builder;
        private Context context;
        private PushMessage pushMessage;
        private PendingResult pendingResult;

        LoadNotificationPicture(Context context, PendingResult pendingResult, Notification.Builder builder, PushMessage pushMessage) {
            super();

            this.builder = builder;
            this.pushMessage = pushMessage;
            this.context = context;
            this.pendingResult = pendingResult;
        }

        private URL getPictureUrl() throws MalformedURLException {
            String pictureUrl = this.pushMessage.getPictureUrl();
            if (pictureUrl == null){
                throw new RuntimeException("Kumulos: pictureUrl cannot be null at this point");
            }

            if (pictureUrl.substring(0, 8).equals("https://") || pictureUrl.substring(0, 7).equals("http://")){
                return new URL(pictureUrl);
            }

            DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();

            return new URL(MEDIA_RESIZER_BASE_URL + "/" + metrics.widthPixels + "x/" + pictureUrl);
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            InputStream in;
            try {
                URL url = this.getPictureUrl();

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                in = connection.getInputStream();
                return BitmapFactory.decodeStream(in);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);

            if (result == null){
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
     * Used to add Kumulos extras when overriding buildNotification and providing own launch intent
     *
     * @param pushMessage
     * @param launchIntent
     */
    protected static void addDeepLinkExtras(PushMessage pushMessage, Intent launchIntent){
        if (!KumulosInApp.isInAppEnabled()){
            return;
        }

        int tickleId = pushMessage.getTickleId();
        if (tickleId == -1){
            return;
        }

        launchIntent.putExtra(PushBroadcastReceiver.EXTRAS_KEY_TICKLE_ID, tickleId);
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

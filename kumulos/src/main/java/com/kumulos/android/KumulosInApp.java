package com.kumulos.android;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class KumulosInApp {
    static InAppDeepLinkHandlerInterface inAppDeepLinkHandler = null;
    static Application application;

    public enum InboxMessagePresentationResult {
        FAILED,
        FAILED_EXPIRED,
        PRESENTED
    }

    public interface InAppInboxUpdatedHandler extends Runnable {
        void run();
    }

    private static InAppInboxUpdatedHandler inboxUpdatedHandler;

    public interface InAppInboxSummaryHandler {
        void run(@Nullable InAppInboxSummary summary);
    }

    //==============================================================================================
    //-- Public APIs

    /**
     * Returns up to 50 non-expired in-app messages stored in inbox
     *
     * @param context
     */
    public static List<InAppInboxItem> getInboxItems(Context context) {
        boolean inAppEnabled = isInAppEnabled();
        if (!inAppEnabled) {
            throw new RuntimeException("Kumulos: It is only possible to read In App inbox if In App messaging is enabled");
        }

        return InAppMessageService.readInboxItems(context);
    }

    /**
     * Presents selected inbox item
     *
     * @param context
     * @param item inbox item to present
     */
    public static InboxMessagePresentationResult presentInboxMessage(Context context, InAppInboxItem item) {
        boolean inAppEnabled = isInAppEnabled();
        if (!inAppEnabled) {
            throw new RuntimeException("Kumulos: It is only possible to present In App inbox if In App messaging is enabled");
        }

        return InAppMessageService.presentMessage(context, item);
    }

    /**
     * Deletes selected inbox item
     *
     * @param context
     * @param item inbox item to delete
     */
    public static boolean deleteMessageFromInbox(Context context, InAppInboxItem item) {
        return InAppMessageService.deleteMessageFromInbox(context, item.getId());
    }

    /**
     * Marks selected inbox item as read
     *
     * @param context
     * @param item inbox item to mark as read
     */
    public static boolean markAsRead(Context context, InAppInboxItem item) {
        if (item.isRead()) {
            return false;
        }

        boolean res = InAppMessageService.markInboxItemRead(context, item.getId(), true);
        maybeRunInboxUpdatedHandler(res);

        return res;
    }

    /**
     * Marks all inbox items as read.
     *
     * @param context
     */
    public static boolean markAllInboxItemsAsRead(Context context) {
        return InAppMessageService.markAllInboxItemsAsRead(context);
    }

    /**
     * Set a handler to run when inbox has changes which might be relevant for presentation.
     * These concern messages with inbox set: fetched new message, message evicted, message opened, message deleted, message marked as read.
     * Handler runs on UI thread.
     *
     * @param inboxUpdatedHandler handler
     */
    public static void setOnInboxUpdated(@Nullable InAppInboxUpdatedHandler inboxUpdatedHandler) {
        KumulosInApp.inboxUpdatedHandler = inboxUpdatedHandler;
    }

    /**
     * Asynchronously runs inbox summary handler on UI thread. Handler receives a single argument InAppInboxSummary
     *
     * @param context
     * @param inboxSummaryHandler handler
     */
    public static void getInboxSummaryAsync(Context context, InAppInboxSummaryHandler inboxSummaryHandler) {
        Runnable task = new InAppContract.ReadInboxSummaryRunnable(context, inboxSummaryHandler);
        Kumulos.executorService.submit(task);
    }


    /**
     * Used to update in-app consent when enablement strategy is EXPLICIT_BY_USER
     *
     * @param consentGiven
     */

    public static void updateConsentForUser(boolean consentGiven) {
        if (Kumulos.getConfig().getInAppConsentStrategy() != KumulosConfig.InAppConsentStrategy.EXPLICIT_BY_USER) {
            throw new RuntimeException("Kumulos: It is only possible to update In App consent for user if consent strategy is set to EXPLICIT_BY_USER");
        }

        boolean inAppWasEnabled = isInAppEnabled();
        if (consentGiven != inAppWasEnabled) {
            updateInAppEnablementFlags(consentGiven);
            toggleInAppMessageMonitoring(consentGiven);
        }
    }

    /**
     * Allows setting the handler you want to use for in-app deep-link buttons
     *
     * @param handler
     */
    public static void setDeepLinkHandler(InAppDeepLinkHandlerInterface handler) {
        inAppDeepLinkHandler = handler;
    }


    //==============================================================================================
    //-- Internal Helpers

    static void initialize(Application application, KumulosConfig currentConfig) {
        KumulosInApp.application = application;

        KumulosConfig.InAppConsentStrategy strategy = currentConfig.getInAppConsentStrategy();
        boolean inAppEnabled = isInAppEnabled();

        if (strategy == KumulosConfig.InAppConsentStrategy.AUTO_ENROLL && !inAppEnabled) {
            inAppEnabled = true;
            updateInAppEnablementFlags(true);
        } else if (strategy == null && inAppEnabled) {
            inAppEnabled = false;
            updateInAppEnablementFlags(false);
            InAppMessageService.clearAllMessages(application);
            clearLastSyncTime(application);
        }

        toggleInAppMessageMonitoring(inAppEnabled);
    }

    private static void updateInAppEnablementFlags(boolean enabled) {
        updateRemoteInAppEnablementFlag(enabled);
        updateLocalInAppEnablementFlag(enabled);
    }

    static boolean isInAppEnabled() {
        SharedPreferences prefs = application.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
        return prefs.getBoolean(SharedPrefs.IN_APP_ENABLED, false);
    }

    private static void updateRemoteInAppEnablementFlag(boolean enabled) {
        try {
            JSONObject params = new JSONObject().put("consented", enabled);

            Kumulos.trackEvent(application, "k.inApp.statusUpdated", params);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static void updateLocalInAppEnablementFlag(boolean enabled) {
        SharedPreferences prefs = application.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(SharedPrefs.IN_APP_ENABLED, enabled);
        editor.apply();
    }

    private static void clearLastSyncTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(SharedPrefs.IN_APP_LAST_SYNC_TIME);
        editor.apply();
    }

    static void handleInAppUserChange(Context context, KumulosConfig currentConfig) {
        InAppMessageService.clearAllMessages(context);
        clearLastSyncTime(context);

        KumulosConfig.InAppConsentStrategy strategy = currentConfig.getInAppConsentStrategy();
        if (strategy == KumulosConfig.InAppConsentStrategy.EXPLICIT_BY_USER) {
            updateLocalInAppEnablementFlag(false);
            toggleInAppMessageMonitoring(false);
        } else if (strategy == KumulosConfig.InAppConsentStrategy.AUTO_ENROLL) {
            updateRemoteInAppEnablementFlag(true);

            fetchMessages();
        } else if (strategy == null) {
            updateRemoteInAppEnablementFlag(false);
        }
    }

    private static void toggleInAppMessageMonitoring(boolean enabled) {
        if (enabled) {
            InAppSyncWorker.startPeriodicFetches(application);

            fetchMessages();
        } else {
            InAppSyncWorker.cancelPeriodicFetches(application);
        }
    }

    private static void fetchMessages() {
        Kumulos.executorService.submit(new Runnable() {
            @Override
            public void run() {
                InAppMessageService.fetch(KumulosInApp.application, true);
            }
        });
    }

    static void maybeRunInboxUpdatedHandler(boolean inboxNeedsUpdate) {
        if (!inboxNeedsUpdate || inboxUpdatedHandler == null) {
            return;
        }

        Activity currentActivity = AnalyticsContract.ForegroundStateWatcher.getCurrentActivity();
        if (currentActivity == null) {
            return;
        }

        currentActivity.runOnUiThread(inboxUpdatedHandler);
    }
}

package com.kumulos.android;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class KumulosInApp {
    private static InAppActivityLifecycleWatcher inAppActivityWatcher;
    static InAppDeepLinkHandlerInterface inAppDeepLinkHandler = null;

    public enum InboxMessagePresentationResult{
        FAILED,
        FAILED_EXPIRED,
        PRESENTED
    }

    //==============================================================================================
    //-- Public APIs

    public static List<InAppInboxItem> getInboxItems(Context context){
        boolean inAppEnabled = isInAppEnabled();
        if (!inAppEnabled){
            throw new RuntimeException("Kumulos: It is only possible to read In App inbox if In App messaging is enabled");
        }

        return InAppMessageService.readInboxItems(context);
    }

    public static InboxMessagePresentationResult presentInboxMessage(Context context, InAppInboxItem item)  {
        boolean inAppEnabled = isInAppEnabled();
        if (!inAppEnabled){
            throw new RuntimeException("Kumulos: It is only possible to present In App inbox if In App messaging is enabled");
        }

        return InAppMessageService.presentMessage(context, item);
    }

    /**
     * Used to update in-app consent when enablement strategy is EXPLICIT_BY_USER
     *
     *   @param consentGiven
     */
    public static void updateInAppConsentForUser(boolean consentGiven){
        if (Kumulos.getConfig().getInAppConsentStrategy() != KumulosConfig.InAppConsentStrategy.EXPLICIT_BY_USER){
            throw new RuntimeException("Kumulos: It is only possible to update In App consent for user if consent strategy is set to EXPLICIT_BY_USER");
        }

        boolean inAppWasEnabled = isInAppEnabled();
        if (consentGiven != inAppWasEnabled){
            updateInAppEnablementFlags(consentGiven);
            toggleInAppMessageMonitoring(consentGiven);
        }
    }


    //==============================================================================================
    //-- Internal Helpers

    static void initializeInApp(KumulosConfig currentConfig){

        KumulosConfig.InAppConsentStrategy strategy = currentConfig.getInAppConsentStrategy();
        boolean inAppEnabled = isInAppEnabled();

        if (strategy == KumulosConfig.InAppConsentStrategy.AUTO_ENROLL && !inAppEnabled){
            inAppEnabled = true;
            updateInAppEnablementFlags(inAppEnabled);
        }
        else if (strategy == null && inAppEnabled){
            inAppEnabled = false;
            updateInAppEnablementFlags(inAppEnabled);
        }

        toggleInAppMessageMonitoring(inAppEnabled);

        inAppDeepLinkHandler = currentConfig.getInAppDeepLinkHandler();
    }

    private static void updateInAppEnablementFlags(boolean enabled){
        updateRemoteInAppEnablementFlag(enabled);
        updateLocalInAppEnablementFlag(enabled);
    }

    static boolean isInAppEnabled(){
        SharedPreferences prefs = Kumulos.application.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
        return prefs.getBoolean(SharedPrefs.IN_APP_ENABLED, false);
    }

    private static void updateRemoteInAppEnablementFlag(boolean enabled){
        try {
            JSONObject params = new JSONObject().put("consented", enabled);
            Kumulos.trackEvent(Kumulos.application, "k.inApp.statusUpdated", params);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static void updateLocalInAppEnablementFlag(boolean enabled){
        SharedPreferences prefs = Kumulos.application.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(SharedPrefs.IN_APP_ENABLED, enabled);
        editor.apply();
    }

    static void handleInAppUserChange(Context context, KumulosConfig currentConfig){
        InAppMessageService.clearAllMessages(context);

        SharedPreferences prefs = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(SharedPrefs.IN_APP_LAST_SYNC_TIME);
        editor.apply();

        KumulosConfig.InAppConsentStrategy strategy = currentConfig.getInAppConsentStrategy();
        if (strategy == KumulosConfig.InAppConsentStrategy.EXPLICIT_BY_USER){
            updateLocalInAppEnablementFlag(false);
            updateRemoteInAppEnablementFlag(false);
            toggleInAppMessageMonitoring(false);
        }
        else if (strategy == KumulosConfig.InAppConsentStrategy.AUTO_ENROLL){
            updateRemoteInAppEnablementFlag(true);
        }
        else if (strategy == null){
            updateRemoteInAppEnablementFlag(false);
        }
    }

    private static void toggleInAppMessageMonitoring(boolean enabled){
        InAppTaskService its = new InAppTaskService();
        if (enabled){
            inAppActivityWatcher = new InAppActivityLifecycleWatcher();
            Kumulos.application.registerActivityLifecycleCallbacks(inAppActivityWatcher);
            its.startPeriodicFetches(Kumulos.application);
        }
        else {
            if (inAppActivityWatcher != null){
                Kumulos.application.unregisterActivityLifecycleCallbacks(inAppActivityWatcher);
                inAppActivityWatcher = null;
            }

            its.cancelPeriodicFetches(Kumulos.application);
        }
    }
}

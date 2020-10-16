package com.kumulos.android;

import android.app.Application;
import android.text.TextUtils;

import org.acra.config.CoreConfigurationBuilder;
import org.json.JSONObject;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Represents the configuration for the Kumulos client
 */
public final class KumulosConfig {

    @DrawableRes
    static final int DEFAULT_NOTIFICATION_ICON_ID = R.drawable.kumulos_ic_stat_notifications;
    static final int DEFAULT_SESSION_IDLE_TIMEOUT_SECONDS = 40;
    private static final String KEY_API_KEY = "apiKey";
    private static final String KEY_SECRET_KEY = "secretKey";
    private static final String KEY_NOTIFICATION_SMALL_ICON_ID = "smallNotificationIconId";
    private static final String KEY_CRASH_REPORTING_ENABLED = "crashEnabled";
    private static final String KEY_SESSION_IDLE_TIMEOUT = "sessionTimeout";
    private static final String KEY_RUNTIME_INFO = "runtimeInfo";
    private static final String KEY_SDK_INFO = "sdkInfo";
    private static final String KEY_IN_APP_CONSENT_STRATEGY = "inAppConsentStrategy";

    private String apiKey;
    private String secretKey;
    @DrawableRes
    private int notificationSmallIconId;
    private boolean crashReportingEnabled;
    private InAppConsentStrategy inAppConsentStrategy;
    private int sessionIdleTimeoutSeconds;

    private JSONObject runtimeInfo;
    private JSONObject sdkInfo;

    private CoreConfigurationBuilder acraConfigBuilder;

    private URL deepLinkCname;
    private DeferredDeepLinkHandlerInterface deferredDeepLinkHandler;

    public enum InAppConsentStrategy{
        AUTO_ENROLL,
        EXPLICIT_BY_USER
    }

    // Private constructor to discourage not using the Builder.
    private KumulosConfig() {}

    private void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    private void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    private void setNotificationSmallIconId(@DrawableRes int notificationSmallIconId) {
        this.notificationSmallIconId = notificationSmallIconId;
    }

    private void setCrashReportingEnabled(boolean enabled) {
        this.crashReportingEnabled = enabled;
    }

    private void setSessionIdleTimeoutSeconds(int timeoutSeconds) {
        this.sessionIdleTimeoutSeconds = timeoutSeconds;
    }

    private void setRuntimeInfo(JSONObject info) {
        this.runtimeInfo = info;
    }

    private void setSdkInfo(JSONObject info) {
        this.sdkInfo = info;
    }


    private void setInAppConsentStrategy(InAppConsentStrategy strategy) {
        this.inAppConsentStrategy = strategy;
    }

    private void setCname(@Nullable URL deepLinkCname) {
        this.deepLinkCname = deepLinkCname;
    }
    private void setDeferredDeepLinkHandler(DeferredDeepLinkHandlerInterface deferredHandler) {
        this.deferredDeepLinkHandler = deferredHandler;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public @DrawableRes int getNotificationSmallIconId() {
        return notificationSmallIconId;
    }

    public boolean crashReportingEnabled() {
        return crashReportingEnabled;
    }

    public int getSessionIdleTimeoutSeconds() {
        return sessionIdleTimeoutSeconds;
    }

    public JSONObject getRuntimeInfo() {
        return this.runtimeInfo;
    }

    public JSONObject getSdkInfo() {
        return this.sdkInfo;
    }

    public CoreConfigurationBuilder getAcraConfigBuilder(Application application) {
        if (null == this.acraConfigBuilder) {
            this.acraConfigBuilder = new CoreConfigurationBuilder(application);
        }

        return this.acraConfigBuilder;
    }

    InAppConsentStrategy getInAppConsentStrategy() {
        return inAppConsentStrategy;
    }

    public @Nullable URL getDeepLinkCname() {
        return this.deepLinkCname;
    }

    public DeferredDeepLinkHandlerInterface getDeferredDeepLinkHandler() {
        return this.deferredDeepLinkHandler;
    }

    /**
     * Config builder for the Kumulos client
     */
    public static class Builder {
        private String apiKey;
        private String secretKey;

        @DrawableRes
        private int notificationSmallIconDrawableId = KumulosConfig.DEFAULT_NOTIFICATION_ICON_ID;
        private boolean enableCrashReporting = false;
        private InAppConsentStrategy consentStrategy = null;
        private int sessionIdleTimeoutSeconds = KumulosConfig.DEFAULT_SESSION_IDLE_TIMEOUT_SECONDS;

        private JSONObject runtimeInfo;
        private JSONObject sdkInfo;

        private @Nullable URL deepLinkCname;
        private DeferredDeepLinkHandlerInterface deferredDeepLinkHandler;

        public Builder(@NonNull String apiKey, @NonNull String secretKey) {
            this.apiKey = apiKey;
            this.secretKey = secretKey;
        }

        /**
         * Set up the drawable to use for the small push notification icon
         *
         * @param drawableIconId
         * @return
         */
        public Builder setPushSmallIconId(@DrawableRes int drawableIconId) {
            this.notificationSmallIconDrawableId = drawableIconId;
            return this;
        }

        public Builder enableCrashReporting() {
            this.enableCrashReporting = true;
            return this;
        }

        public Builder enableInAppMessaging(InAppConsentStrategy strategy) {
            this.consentStrategy = strategy;
            return this;
        }

        public Builder enableDeepLinking(@NonNull String cname, DeferredDeepLinkHandlerInterface handler) {
            this.deferredDeepLinkHandler = handler;
            try{
                this.deepLinkCname = new URL(cname);
            }
            catch(MalformedURLException e){
                e.printStackTrace();
                this.deepLinkCname = null;
            }

            return this;
        }

        public Builder enableDeepLinking(DeferredDeepLinkHandlerInterface handler) {
            this.deferredDeepLinkHandler = handler;
            this.deepLinkCname = null;
            return this;
        }

        /**
         * The minimum amount of time the user has to have left the app for a session end event to be
         * recorded.
         *
         * The idle period starts when a pause lifecycle event is observed, and is reset when any resume
         * event is seen. If no resume event is observed and the idle period elapses, the app is considered
         * to be in the background and the session ends.
         *
         * This defaults to KumulosConfig.DEFAULT_SESSION_IDLE_TIMEOUT_SECONDS if unspecified.
         *
         * @param idleTimeSeconds
         * @return
         */
        public Builder setSessionIdleTimeoutSeconds(int idleTimeSeconds) {
            this.sessionIdleTimeoutSeconds = Math.abs(idleTimeSeconds);
            return this;
        }

        public Builder setRuntimeInfo(JSONObject info) {
            this.runtimeInfo = info;
            return this;
        }

        public Builder setSdkInfo(JSONObject info) {
            this.sdkInfo = info;
            return this;
        }

        public KumulosConfig build() {
            if (TextUtils.isEmpty(apiKey) || TextUtils.isEmpty(secretKey)) {
                throw new IllegalStateException("You need to provide apiKey and secretKey before you can build KumulosConfig.");
            }

            KumulosConfig newConfig = new KumulosConfig();
            newConfig.setApiKey(apiKey);
            newConfig.setSecretKey(secretKey);
            newConfig.setNotificationSmallIconId(notificationSmallIconDrawableId);
            newConfig.setCrashReportingEnabled(enableCrashReporting);
            newConfig.setSessionIdleTimeoutSeconds(sessionIdleTimeoutSeconds);
            newConfig.setRuntimeInfo(this.runtimeInfo);
            newConfig.setSdkInfo(this.sdkInfo);

            newConfig.setInAppConsentStrategy(consentStrategy);

            newConfig.setCname(this.deepLinkCname);
            newConfig.setDeferredDeepLinkHandler(this.deferredDeepLinkHandler);

            return newConfig;
        }
    }
}

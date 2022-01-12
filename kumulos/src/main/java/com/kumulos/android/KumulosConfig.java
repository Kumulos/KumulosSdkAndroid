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
import java.util.Map;

/**
 * Represents the configuration for the Kumulos client
 */
public final class KumulosConfig {

    @DrawableRes
    static final int DEFAULT_NOTIFICATION_ICON_ID = R.drawable.kumulos_ic_stat_notifications;
    static final int DEFAULT_SESSION_IDLE_TIMEOUT_SECONDS = 23;

    private String apiKey;
    private String secretKey;

    @DrawableRes
    private int notificationSmallIconId;
    private boolean crashReportingEnabled;
    private InAppConsentStrategy inAppConsentStrategy;
    private int sessionIdleTimeoutSeconds;

    private JSONObject runtimeInfo;
    private JSONObject sdkInfo;
    private Map<UrlBuilder.Service, String> baseUrlMap;

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

    private void setBaseUrlMap(Map<UrlBuilder.Service, String> baseUrlMap) {
        this.baseUrlMap = baseUrlMap;
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

    JSONObject getRuntimeInfo() {
        return this.runtimeInfo;
    }

    JSONObject getSdkInfo() {
        return this.sdkInfo;
    }

    Map<UrlBuilder.Service, String> getBaseUrlMap() {
        return baseUrlMap;
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
        private final String apiKey;
        private final String secretKey;

        @DrawableRes
        private int notificationSmallIconDrawableId = KumulosConfig.DEFAULT_NOTIFICATION_ICON_ID;
        private boolean enableCrashReporting = false;
        private InAppConsentStrategy consentStrategy = null;
        private int sessionIdleTimeoutSeconds = KumulosConfig.DEFAULT_SESSION_IDLE_TIMEOUT_SECONDS;

        private JSONObject runtimeInfo;
        private JSONObject sdkInfo;
        private Map<UrlBuilder.Service, String> baseUrlMap;

        private @Nullable URL deepLinkCname;
        private DeferredDeepLinkHandlerInterface deferredDeepLinkHandler;

        public Builder(@NonNull String region, @NonNull String apiKey, @NonNull String secretKey) {
            this.apiKey = apiKey;
            this.secretKey = secretKey;

            this.baseUrlMap = UrlBuilder.defaultMapping(region);
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

        /** Private API */
        @InternalSdkEmbeddingApi(purpose = "Allow override of stats data in x-plat SDKs")
        public Builder setRuntimeInfo(JSONObject info) {
            this.runtimeInfo = info;
            return this;
        }

        /** Private API */
        @InternalSdkEmbeddingApi(purpose = "Allow override of stats data in x-plat SDKs")
        public Builder setSdkInfo(JSONObject info) {
            this.sdkInfo = info;
            return this;
        }

        /** Private API */
        @InternalSdkEmbeddingApi(purpose = "Allow sending traffic to different domains")
        public Builder setBaseUrlMapping(Map<UrlBuilder.Service, String> baseUrlMap) {
            this.baseUrlMap = baseUrlMap;
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
            newConfig.setBaseUrlMap(this.baseUrlMap);

            newConfig.setInAppConsentStrategy(consentStrategy);

            newConfig.setCname(this.deepLinkCname);
            newConfig.setDeferredDeepLinkHandler(this.deferredDeepLinkHandler);

            return newConfig;
        }
    }
}

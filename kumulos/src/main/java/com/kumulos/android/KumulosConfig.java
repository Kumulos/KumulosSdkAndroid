package com.kumulos.android;

import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.text.TextUtils;

/**
 * Represents the configuration for the Kumulos client
 */
public final class KumulosConfig {

    @DrawableRes
    static final int DEFAULT_NOTIFICATION_ICON_ID = R.drawable.kumulos_ic_stat_notifications;
    static final int DEFAULT_SESSION_IDLE_TIMEOUT_SECONDS = 40;

    private String apiKey;
    private String secretKey;
    @DrawableRes
    private int notificationSmallIconId;
    private boolean crashReportingEnabled;
    private int sessionIdleTimeoutSeconds;

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

    /**
     * Config builder for the Kumulos client
     */
    public static class Builder {
        private String apiKey;
        private String secretKey;

        @DrawableRes
        private int notificationSmallIconDrawableId = KumulosConfig.DEFAULT_NOTIFICATION_ICON_ID;
        private boolean enableCrashReporting = false;
        private int sessionIdleTimeoutSeconds = KumulosConfig.DEFAULT_SESSION_IDLE_TIMEOUT_SECONDS;

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

            return newConfig;
        }
    }
}

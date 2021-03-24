package com.kumulos.android;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import androidx.annotation.Nullable;

class InAppMessage {
    private String presentedWhen;
    private int inAppId;
    @Nullable
    private JSONObject badgeConfig;
    @Nullable
    private JSONObject data;
    private JSONObject content;
    @Nullable
    private JSONObject inbox;
    @Nullable
    private Date dismissedAt = null;
    @Nullable
    private Date updatedAt;
    @Nullable
    private Date expiresAt = null;
    @Nullable
    private Date inboxDeletedAt;
    @Nullable
    private Date readAt;
    @Nullable
    private Date sentAt;

    InAppMessage(){}

    InAppMessage(JSONObject obj) throws JSONException, ParseException {
        this.inAppId = obj.getInt("id");
        this.presentedWhen = obj.getString("presentedWhen");
        this.data = obj.optJSONObject("data");
        this.badgeConfig = obj.optJSONObject("badge");
        this.content = obj.getJSONObject("content");
        this.inbox = obj.optJSONObject("inbox");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.updatedAt =  sdf.parse(obj.getString("updatedAt"));

        if (!obj.isNull("openedAt")) {
            this.dismissedAt = sdf.parse(obj.getString("openedAt"));
        }

        if (!obj.isNull("expiresAt")) {
            this.expiresAt =  sdf.parse(obj.getString("expiresAt"));
        }

        if (!obj.isNull("inboxDeletedAt")) {
            this.inboxDeletedAt = sdf.parse(obj.getString("inboxDeletedAt"));
        }

        if (!obj.isNull("readAt")) {
            this.readAt = sdf.parse(obj.getString("readAt"));
        }

        if (!obj.isNull("sentAt")) {
            this.sentAt = sdf.parse(obj.getString("sentAt"));
        }
    }

    int getInAppId() {
        return inAppId;
    }

    String getPresentedWhen() {
        return presentedWhen;
    }

    @Nullable
    JSONObject getBadgeConfig() {
        return badgeConfig;
    }

    @Nullable
    JSONObject getData() {
        return data;
    }

    JSONObject getContent() {
        return content;
    }

    @Nullable
    Date getDismissedAt() {
        return dismissedAt;
    }
    @Nullable
    Date getUpdatedAt() {
        return updatedAt;
    }
    @Nullable
    Date getExpiresAt() {
        return expiresAt;
    }
    @Nullable
    JSONObject getInbox() {
        return inbox;
    }

    void setInAppId(int id){
        this.inAppId = id;
    }

    void setContent(JSONObject content){
        this.content = content;
    }

    void setDismissedAt(@Nullable Date dismissedAt){
        this.dismissedAt = dismissedAt;
    }

    void setPresentedWhen(String presentedWhen){
        this.presentedWhen = presentedWhen;
    }

    @Nullable
    Date getInboxDeletedAt() {
        return inboxDeletedAt;
    }

    @Nullable
    Date getReadAt() {
        return readAt;
    }

    @Nullable
    Date getSentAt() {
        return sentAt;
    }
}
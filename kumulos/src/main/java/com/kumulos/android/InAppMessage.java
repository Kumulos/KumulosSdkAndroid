package com.kumulos.android;

import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

class InAppMessage {
    private String presentedWhen;
    private int inAppId;
    @Nullable
    private JSONObject badgeConfig;
    @Nullable
    private JSONObject data;
    private JSONObject content;
    private JSONObject inbox;

    private Date openedAt = null;
    private Date updatedAt;

    InAppMessage(){}

    InAppMessage(JSONObject obj) throws JSONException, ParseException {
        this.inAppId = obj.getInt("id");
        this.presentedWhen = obj.getString("presentedWhen");
        this.data = obj.optJSONObject("data");
        this.badgeConfig = obj.optJSONObject("badge");
        this.content = obj.getJSONObject("content");
        this.inbox = obj.optJSONObject("inbox");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        this.updatedAt =  sdf.parse(obj.getString("updatedAt"));

        if (!obj.isNull("openedAt")) {
            this.openedAt = sdf.parse(obj.getString("openedAt"));
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

    Date getOpenedAt() {
        return openedAt;
    }

    Date getUpdatedAt() {
        return updatedAt;
    }

    JSONObject getInbox() {
        return inbox;
    }

    void setInAppId(int id){
        this.inAppId = id;
    }

    void setContent(JSONObject content){
        this.content = content;
    }

    void setOpenedAt(Date openedAt){
        this.openedAt = openedAt;
    }

    void setPresentedWhen(String presentedWhen){
        this.presentedWhen = presentedWhen;
    }
}
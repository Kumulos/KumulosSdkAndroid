package com.kumulos.android.inapp;

import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** package */ public class InAppMessage {



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



    public InAppMessage(){

    }

    public InAppMessage(JSONObject obj) throws JSONException, ParseException {
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


    public int getInAppId() {
        return inAppId;
    }

    public String getPresentedWhen() {
        return presentedWhen;
    }

    @Nullable
    public JSONObject getBadgeConfig() {
        return badgeConfig;
    }

    @Nullable
    public JSONObject getData() {
        return data;
    }

    public JSONObject getContent() {
        return content;
    }

    public Date getOpenedAt() {
        return openedAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public JSONObject getInbox() {
        return inbox;
    }

    public void setInAppId(int id){
       this.inAppId = id;
    }

    public void setContent(JSONObject content){
       this.content = content;
    }

    public void setOpenedAt(Date openedAt){
       this.openedAt = openedAt;
    }
}

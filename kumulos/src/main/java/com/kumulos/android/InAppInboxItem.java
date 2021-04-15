package com.kumulos.android;

import java.util.Date;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

public class InAppInboxItem {

    InAppInboxItem() {
    }

    private int id;
    private String title;
    private String subtitle;
    @Nullable
    private Date availableFrom;
    @Nullable
    private Date availableTo;
    @Nullable
    private Date dismissedAt;
    @Nullable
    private Date readAt;
    private Date sentAt;
    @Nullable
    private JSONObject data;

    public int getId() {
        return id;
    }

    void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    @Nullable
    public Date getAvailableFrom() {
        return availableFrom;
    }

    void setAvailableFrom(@Nullable Date availableFrom) {
        this.availableFrom = availableFrom;
    }

    @Nullable
    public Date getAvailableTo() {
        return availableTo;
    }

    void setAvailableTo(@Nullable Date availableTo) {
        this.availableTo = availableTo;
    }

    @Nullable
    public Date getDismissedAt() {
        return dismissedAt;
    }

    void setDismissedAt(@Nullable Date dismissedAt) {
        this.dismissedAt = dismissedAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    void setReadAt(@Nullable Date readAt) {
        this.readAt = readAt;
    }

    public Date getSentAt() {
        return sentAt;
    }

    void setSentAt(Date sentAt) {
        this.sentAt = sentAt;
    }

    public @Nullable
    JSONObject getData() {
        return data;
    }

    void setData(@Nullable JSONObject data) {
        this.data = data;
    }
}

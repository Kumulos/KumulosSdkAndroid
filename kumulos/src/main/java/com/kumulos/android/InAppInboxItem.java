package com.kumulos.android;

import android.support.annotation.Nullable;

import java.util.Date;

public class InAppInboxItem {

    InAppInboxItem(){ }

    private int id;
    private String title;
    private String subtitle;
    @Nullable
    private Date availableFrom;
    @Nullable
    private Date availableTo;
    @Nullable
    private Date dismissedAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    @Nullable
    public Date getAvailableFrom() {
        return availableFrom;
    }

    public void setAvailableFrom(@Nullable Date availableFrom) {
        this.availableFrom = availableFrom;
    }

    @Nullable
    public Date getAvailableTo() {
        return availableTo;
    }

    public void setAvailableTo(@Nullable Date availableTo) {
        this.availableTo = availableTo;
    }

    @Nullable
    public Date getDismissedAt() {
        return dismissedAt;
    }

    public void setDismissedAt(@Nullable Date dismissedAt) {
        this.dismissedAt = dismissedAt;
    }
}

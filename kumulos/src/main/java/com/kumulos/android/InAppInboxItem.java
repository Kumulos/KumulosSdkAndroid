package com.kumulos.android;

import androidx.annotation.Nullable;

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
}

package com.kumulos.android;

public class InAppInboxSummaryInfo {
    private final int totalCount;
    private final int unreadCount;

    InAppInboxSummaryInfo(int totalCount, int unreadCount) {
        this.totalCount = totalCount;
        this.unreadCount = unreadCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getUnreadCount() {
        return unreadCount;
    }
}

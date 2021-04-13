package com.kumulos.android;

import java.util.List;

class InAppSaveResult {
    List<InAppMessage> itemsToPresent;
    List<Integer> deliveredIds;
    List<Integer> deletedIds;
    boolean inboxUpdated;

    InAppSaveResult(List<InAppMessage> itemsToPresent, List<Integer> deliveredIds, List<Integer> deletedIds, boolean inboxUpdated) {
        this.itemsToPresent = itemsToPresent;
        this.deliveredIds = deliveredIds;
        this.deletedIds = deletedIds;
        this.inboxUpdated = inboxUpdated;
    }

    List<InAppMessage> getItemsToPresent() {
        return itemsToPresent;
    }

    List<Integer> getDeliveredIds() {
        return deliveredIds;
    }

    List<Integer> getDeletedIds() {
        return deletedIds;
    }

    boolean wasInboxUpdated() { return inboxUpdated; }
}

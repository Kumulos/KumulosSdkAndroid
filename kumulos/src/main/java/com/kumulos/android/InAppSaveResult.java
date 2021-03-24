package com.kumulos.android;

import java.util.List;

class InAppSaveResult {
    List<InAppMessage> itemsToPresent;
    List<Integer> deliveredIds;
    List<Integer> deletedIds;

    InAppSaveResult(List<InAppMessage> itemsToPresent, List<Integer> deliveredIds, List<Integer> deletedIds) {
        this.itemsToPresent = itemsToPresent;
        this.deliveredIds = deliveredIds;
        this.deletedIds = deletedIds;
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
}

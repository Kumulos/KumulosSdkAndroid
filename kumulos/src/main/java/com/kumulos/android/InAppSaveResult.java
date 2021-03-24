package com.kumulos.android;

import java.util.List;

public class InAppSaveResult {
    List<InAppMessage> itemsToPresent;
    List<Integer> deliveredIds;
    List<Integer> deletedIds;

    public InAppSaveResult(List<InAppMessage> itemsToPresent, List<Integer> deliveredIds, List<Integer> deletedIds) {
        this.itemsToPresent = itemsToPresent;
        this.deliveredIds = deliveredIds;
        this.deletedIds = deletedIds;
    }

    public List<InAppMessage> getItemsToPresent() {
        return itemsToPresent;
    }

    public List<Integer> getDeliveredIds() {
        return deliveredIds;
    }

    public List<Integer> getDeletedIds() {
        return deletedIds;
    }
}

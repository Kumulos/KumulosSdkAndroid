package com.kumulos.android;

import android.content.Context;

import java.util.List;

public class KumulosInApp {
    public enum InboxMessagePresentationResult{
        FAILED,
        FAILED_EXPIRED,
        PRESENTED
    }

    public static List<InAppInboxItem> getInboxItems(Context context){
        boolean inAppEnabled = Kumulos.isInAppEnabled();
        if (!inAppEnabled){
            throw new RuntimeException("Kumulos: It is only possible to read In App inbox if In App messaging is enabled");
        }

        return InAppMessageService.readInboxItems(context);
    }

    public static InboxMessagePresentationResult presentInboxMessage(Context context, InAppInboxItem item)  {
        boolean inAppEnabled = Kumulos.isInAppEnabled();
        if (!inAppEnabled){
            throw new RuntimeException("Kumulos: It is only possible to present In App inbox if In App messaging is enabled");
        }

        return InAppMessageService.presentMessage(context, item);
    }
}

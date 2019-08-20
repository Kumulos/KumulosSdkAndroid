package com.kumulos.android;

import android.content.Context;

import java.util.List;

public class KumulosInApp {

    public static List<InAppInboxItem> getInboxItems(Context context){
        boolean inAppEnabled = Kumulos.isInAppEnabled();
        if (!inAppEnabled){
            throw new RuntimeException("Kumulos: It is only possible to read In App inbox if In App messaging is enabled");
        }

        return InAppMessageService.readInboxItems(context);
    }
}

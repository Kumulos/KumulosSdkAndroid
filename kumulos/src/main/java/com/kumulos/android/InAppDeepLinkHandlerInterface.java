package com.kumulos.android;

import android.content.Context;

import org.json.JSONObject;

public interface InAppDeepLinkHandlerInterface {
    /**
     * Override to change the behaviour of button deep link. Default none
     *
     * @param data deep link
     * @return
     */
    void handle(Context context, JSONObject data);
}

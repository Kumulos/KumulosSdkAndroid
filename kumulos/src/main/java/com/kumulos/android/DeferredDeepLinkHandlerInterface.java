package com.kumulos.android;

import android.content.Context;

import org.json.JSONObject;

public interface DeferredDeepLinkHandlerInterface {
    /**
     * Override to change the behaviour of deep link. Default none
     *
     * @param data deep link
     * @return
     */
    void handle(Context context, JSONObject data);//TODO: params
}

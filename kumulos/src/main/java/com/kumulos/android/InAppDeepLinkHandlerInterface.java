package com.kumulos.android;

import org.json.JSONObject;

public interface InAppDeepLinkHandlerInterface {
    /**
     * Override to change the behaviour of button deep link. Default none
     *
     * @param data deep link
     * @return
     */
    void handle(JSONObject data);
}

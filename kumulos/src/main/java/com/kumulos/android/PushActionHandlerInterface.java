package com.kumulos.android;

import android.content.Context;

public interface PushActionHandlerInterface {
    /**
     * Override to change the behaviour of push action button deep link. Default none
     *
     * @param actionId identifier of the button clicked
     * @return
     */
    void handle(Context context, PushMessage pushMessage, String actionId);
}

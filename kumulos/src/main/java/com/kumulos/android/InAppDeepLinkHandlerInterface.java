package com.kumulos.android;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

public interface InAppDeepLinkHandlerInterface {
    /**
     * Override to change the behaviour of button deep link. Default none
     *
     * @param buttonPress Data from the button's deep link, along with any in-app message data payload
     */
    void handle(Context context, InAppButtonPress buttonPress);

    class InAppButtonPress {
        @NonNull
        private final JSONObject deepLinkData;
        private final int messageId;
        @Nullable
        private final JSONObject messageData;

        InAppButtonPress(@NonNull JSONObject deepLinkData, int messageId, @Nullable JSONObject messageData) {
            this.deepLinkData = deepLinkData;
            this.messageId = messageId;
            this.messageData = messageData;
        }

        @NonNull
        public JSONObject getDeepLinkData() {
            return deepLinkData;
        }

        public int getMessageId() {
            return messageId;
        }

        @Nullable
        public JSONObject getMessageData() {
            return messageData;
        }
    }
}

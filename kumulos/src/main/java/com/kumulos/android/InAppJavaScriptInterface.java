package com.kumulos.android;
import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONException;
import org.json.JSONObject;

class InAppJavaScriptInterface {

    private static final String TAG = InAppJavaScriptInterface.class.getName();

    InAppJavaScriptInterface(Context c) {
    }

    @JavascriptInterface
    public void postClientMessage(String msg) {

        String messageType = null;

        try{
            JSONObject message = new JSONObject(msg);
            messageType = message.getString("type");

        }
        catch(JSONException e){
            Log.d(TAG, "Incorrect message format: "+msg);
            return;
        }

        switch(messageType){
            case "READY":
                InAppMessagePresenter.clientReady();
                return;
            case "MESSAGE_OPENED":
                InAppMessagePresenter.messageOpened();
                return;
            case "MESSAGE_CLOSED":
                InAppMessagePresenter.messageClosed();
                return;
            default:
                Log.d(TAG, "Unknown message type: "+messageType);
        }
    }


}
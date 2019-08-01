package com.kumulos.android;
import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONException;
import org.json.JSONObject;

class InAppJavaScriptInterface {

    private InAppMessagePresenter mMessagePresenter;

    private static final String TAG = InAppJavaScriptInterface.class.getName();

    InAppJavaScriptInterface(Context c, InAppMessagePresenter messagePresenter) {
        mMessagePresenter = messagePresenter;
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
                mMessagePresenter.clientReady();
                return;
            case "MESSAGE_OPENED":
                mMessagePresenter.messageOpened();
                return;
            case "MESSAGE_CLOSED":
                mMessagePresenter.messageClosed();
                return;
            default:
                Log.d(TAG, "Unknown message type: "+messageType);
        }
    }


}
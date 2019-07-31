package com.kumulos.android.inapp;
import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONException;
import org.json.JSONObject;

public class JavaScriptInterface {

    private MessagePresenter mMessagePresenter;

    private static final String TAG = JavaScriptInterface.class.getName();

    JavaScriptInterface(Context c, MessagePresenter messagePresenter) {
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
                mMessagePresenter.clientPresentMessage();
                return;
            case "MESSAGE_OPENED":
                mMessagePresenter.trackMessageOpened();
                return;
            case "MESSAGE_CLOSED":
                mMessagePresenter.messageClosed();
                return;
            default:
                Log.d(TAG, "Unknown message type: "+messageType);
        }
    }


}
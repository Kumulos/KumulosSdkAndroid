package com.example.sdktestapp;

import android.util.Log;

import com.kumulos.android.InAppDeepLinkHandler;


import org.json.JSONObject;

public class MyDeepLinkHandler extends InAppDeepLinkHandler {

    @Override
    protected void handle(JSONObject data){
        Log.d("vlad", "total win "+data.toString());
    }
}

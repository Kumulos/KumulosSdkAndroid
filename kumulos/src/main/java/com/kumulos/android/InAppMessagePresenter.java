package com.kumulos.android;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class InAppMessagePresenter {

    private static final String TAG = InAppMessagePresenter.class.getName();

    //TODO: these should be set to null when closeDialog. Fine?
    private static List<InAppMessage> messageQueue = new ArrayList<>();
    private static WebView wv = null;
    private static Dialog dialog = null;
    private static ProgressBar spinner = null;

    static void presentMessages(List<InAppMessage> itemsToPresent, Integer tickleId){

        if (itemsToPresent.isEmpty()){
            return;
        }

        WeakReference<Activity> currentActivityRef = InAppActivityLifecycleWatcher.getCurrentActivity();

        if (currentActivityRef == null) {
            return;
        }

        List<InAppMessage> oldQueue = new ArrayList<InAppMessage>(messageQueue);

        addMessagesToQueue(itemsToPresent);
        moveTickleToFront(tickleId);

        if (dialog == null){
            showWebView(currentActivityRef.get());
            return;
        }

        maybeRefreshFirstMessageInQueue(oldQueue);
    }

    private static void maybeRefreshFirstMessageInQueue(List<InAppMessage> oldQueue){
        if (oldQueue.isEmpty()){
            return;
        }

        InAppMessage oldFront = oldQueue.get(0);

        if (oldFront.getInAppId() != messageQueue.get(0).getInAppId()){
            presentMessageToClient();
        }
    }

    private static void moveTickleToFront(Integer tickleId){
        if (tickleId == null){
            return;
        }

        for(int i=0; i<messageQueue.size(); i++){
            InAppMessage next = messageQueue.get(i);
            if (tickleId == next.getInAppId()){
                messageQueue.remove(i);
                messageQueue.add(0, next);

                return;
            }
        }
    }

    private static void addMessagesToQueue(List<InAppMessage> itemsToPresent){
        for(InAppMessage messageToAppend: itemsToPresent){
            boolean exists = false;
            for (InAppMessage messageFromQueue: messageQueue){
                if (messageToAppend.getInAppId() == messageFromQueue.getInAppId()){
                    exists = true;
                    break;
                }
            }

            if (!exists){
                messageQueue.add(messageToAppend);
            }
        }
    }


    static void clientReady(){//java bridge thread
        presentMessageToClient();
    }

    private static void presentMessageToClient(){
        Log.d("vlad","presentMessageToClient");
        if (messageQueue.isEmpty()){
            closeDialog();
            return;
        }

        setSpinnerVisibility(View.VISIBLE);

        InAppMessage message = messageQueue.get(0);
        sendToClient("PRESENT_MESSAGE", message.getContent());
    }

    static void messageOpened(){//java bridge thread
        Log.d("vlad","messageOpened");

        setSpinnerVisibility(View.GONE);
    }

    static void messageClosed(){//java bridge thread
        if (dialog == null || wv == null){
            return;
        }

        InAppMessage message = messageQueue.get(0);
        messageQueue.remove(0);

        InAppMessageService.handleMessageClosed(message);

        presentMessageToClient();
    }



    private static void setSpinnerVisibility(int visibility){
        wv.post(new Runnable() {
            @Override
            public void run() {
                spinner.setVisibility(visibility);
            }
        });
    }

    private static void sendToClient(String type, JSONObject data){
        if (wv == null){
            return;
        }

        wv.post(new Runnable() {
            @Override
            public void run() {
                JSONObject j = new JSONObject();
                try{
                    j.put("data", data);
                    j.put("type", type);
                }
                catch(JSONException e){
                    Log.d(TAG, "Could not create client message");
                    return;
                }

                String script = "window.postHostMessage("+j.toString()+")";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    wv.evaluateJavascript(script, null);
                } else {
                    wv.loadUrl("javascript:" + script);
                }
            }
        });
    }



    private static void closeDialog(){
        dialog.dismiss();
        dialog = null;
        wv = null;
        spinner = null;
    }

    private static void showWebView(Activity currentActivity){
        Handler mHandler = new Handler(Looper.getMainLooper());
        mHandler.post(new Runnable() {
            @Override
            public void run() {

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        WebView.setWebContentsDebuggingEnabled(true);//chrome://inspect/#devices
                    }

                    RelativeLayout.LayoutParams paramsWebView = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
                    dialog = new Dialog(currentActivity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
                    LayoutInflater inflater = (LayoutInflater) currentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    dialog.addContentView(inflater.inflate(R.layout.dialog_view, null), paramsWebView);
                    dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                        @Override
                        public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {

                            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() != KeyEvent.ACTION_DOWN) {
                                Log.d("vlad", "back button pressed");

                                InAppMessagePresenter.sendToClient("CLOSE_MESSAGE", null);
                            }
                            return true;
                        }
                    });
                    dialog.show();

                    //has to be called after dialog.show() !??
                    wv = (WebView) dialog.findViewById(R.id.webview);
                    spinner = dialog.findViewById(R.id.progressBar);

                    wv.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK);
                    wv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    wv.getSettings().setJavaScriptEnabled(true);
                    wv.addJavascriptInterface(new InAppJavaScriptInterface(currentActivity), "Android");

                    wv.setWebViewClient(new WebViewClient() {

                        @Override
                        public void onPageCommitVisible(WebView view, String url) {
                            super.onPageCommitVisible(view, url);
                        }

                        @Override
                        public void onLoadResource(WebView view, String url) {
                            super.onLoadResource(view, url);
                        }

                        @Override
                        public void onPageStarted(WebView view, String url, Bitmap favicon) {
                            super.onPageStarted(view, url, favicon);
                            spinner.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onPageFinished(WebView view, String url) {
                            view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                            super.onPageFinished(view, url);
                        }
                    });

                    wv.loadUrl("http://192.168.1.24:8080");
                }
                catch(Exception e){
                    Log.d("vlad", e.getMessage());
                }
            }
        });
    }
}
package com.kumulos.android;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
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
    private static final String HOST_MESSAGE_TYPE_PRESENT_MESSAGE = "PRESENT_MESSAGE";
    private static final String HOST_MESSAGE_TYPE_CLOSE_MESSAGE = "CLOSE_MESSAGE";

    //TODO: these should be set to null when closeDialog. When activity is destroyed, they are. Cannot use DialogFragment as v4 required. Fine?
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
        else if( getDialogActivity(dialog.getContext()).hashCode() != currentActivityRef.get().hashCode()){
            closeDialog();
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

    private static void presentMessageToClient(){
        Log.d("vlad","presentMessageToClient");
        if (messageQueue.isEmpty()){
            closeDialog();
            return;
        }

        if (dialog == null){
            return;
        }

        setSpinnerVisibility(View.VISIBLE);

        InAppMessage message = messageQueue.get(0);
        sendToClient(HOST_MESSAGE_TYPE_PRESENT_MESSAGE, message.getContent());
    }

    static void clientReady(){//java bridge thread
        presentMessageToClient();
    }

    static void messageOpened(){//java bridge thread
        setSpinnerVisibility(View.GONE);
    }

    static void messageClosed(){//java bridge thread

        InAppMessage message = messageQueue.get(0);
        messageQueue.remove(0);

        InAppMessageService.handleMessageClosed(message);

        presentMessageToClient();
    }

    static void closeCurrentMessage(){
        InAppMessagePresenter.sendToClient(HOST_MESSAGE_TYPE_CLOSE_MESSAGE, null);
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

    static void maybeCloseDialog(Activity stoppedActivity){
        if (dialog == null){
            return;
        }
        Activity dialogActivity = getDialogActivity(dialog.getContext());
        if (stoppedActivity.hashCode() == dialogActivity.hashCode()){
            closeDialog();
        }
    }

    private static Activity getDialogActivity(Context cont) {
        if (cont == null)
            return null;
        else if (cont instanceof Activity)
            return (Activity)cont;
        else if (cont instanceof ContextWrapper)
            return getDialogActivity(((ContextWrapper)cont).getBaseContext());

        return null;
    }

    static void closeDialog(){
        if (dialog != null){
            dialog.dismiss();
        }
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

                    //TODO: notch stuff https://developer.android.com/guide/topics/display-cutout
                    //1) calculate notch height
                    //2) determine where is the notch. Cross/close may be under it. Can apply padding to it as well / can move to the other corner
                    //3) having padding for content may disalign  bg image with contents ==> is it needed really?


                    RelativeLayout.LayoutParams paramsWebView = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
                    dialog = new Dialog(currentActivity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
                    LayoutInflater inflater = (LayoutInflater) currentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    dialog.addContentView(inflater.inflate(R.layout.dialog_view, null), paramsWebView);
                    dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                        @Override
                        public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {
                            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() != KeyEvent.ACTION_DOWN) {
                                InAppMessagePresenter.closeCurrentMessage();
                            }
                            return true;
                        }
                    });
                    dialog.show();

                    wv = (WebView) dialog.findViewById(R.id.webview);
                    spinner = dialog.findViewById(R.id.progressBar);

                    wv.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);//TODO: set when not developing renderer :) LOAD_CACHE_ELSE_NETWORK
                    wv.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                    wv.getSettings().setJavaScriptEnabled(true);
                    wv.addJavascriptInterface(new InAppJavaScriptInterface(), InAppJavaScriptInterface.NAME);

                    wv.setWebViewClient(new WebViewClient() {
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


                    //wv.loadUrl("https://iar.app.delivery");
                    wv.loadUrl("http://192.168.1.24:8080");
                }
                catch(Exception e){
                    Kumulos.log(TAG, e.getMessage());
                }
            }
        });
    }
}
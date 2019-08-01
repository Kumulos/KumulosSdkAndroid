package com.kumulos.android;

import android.app.Activity;
import android.app.Dialog;
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
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private List<InAppMessage> messageQueue = new ArrayList<>();
    private WebView wv = null;
    private Dialog dialog = null;
    private ProgressBar spinner = null;
    private WeakReference<Activity> currentActivityRef;

    private static InAppMessagePresenter messagePresenter;

    //TODO: thread safety
    static InAppMessagePresenter getInstance() {
        if (messagePresenter == null) {
            messagePresenter = new InAppMessagePresenter();
        }
        return messagePresenter;
    }

    void presentMessages(Future<List<InAppMessage>> future){



        List<InAppMessage> itemsToPresent = new ArrayList<>();
        try {
            itemsToPresent = future.get();
            Log.d("vlad", ""+itemsToPresent.size());
        } catch (InterruptedException | ExecutionException ex) {
            return;
        }

        if (itemsToPresent.isEmpty()){
            return;
        }

        currentActivityRef = InAppActivityLifecycleWatcher.getCurrentActivity();

        if (currentActivityRef != null){
            messageQueue.addAll(itemsToPresent);
            if (dialog == null){
                this.showWebView(currentActivityRef.get());
            }
        }

    }

    void clientPresentMessage(){//java bridge thread
        Log.d("vlad","clientPresentMessage");
        if (messageQueue.isEmpty()){
            this.closeDialog();
            return;
        }

        this.setSpinnerVisibility(View.VISIBLE);

        InAppMessage message = messageQueue.get(0);
        this.sendToClient("PRESENT_MESSAGE", message.getContent());
    }



    void trackMessageOpened(){//java bridge thread
        Log.d("vlad","trackMessageOpened");

        this.setSpinnerVisibility(View.GONE);

        InAppMessage message = messageQueue.get(0);

        messageQueue.remove(0);

        Date now = new Date();
        message.setOpenedAt(now);

        Runnable task = new InAppContract.TrackMessageOpenedRunnable(currentActivityRef.get(), message);
        Kumulos.executorService.submit(task);

        //TODO: track opened event
//        JSONObject params = new JSONObject();
//
//        try {
//            params.put("localNumber", AuthContext.getInstance().getLocalNumber());
//
//            String eventType = EventType.EVENT_ADDED.getRawValue();
//
//            com.kumulos.android.Kumulos.trackEvent(context, eventType, params);
//        }
//        catch (JSONException e) {
//            e.printStackTrace();
//        }
    }

    void messageClosed(){//java bridge thread
        if (dialog == null || wv == null){
            return;
        }

        if (messageQueue.isEmpty()){
            this.closeDialog();
            return;
        }

        this.clientPresentMessage();
    }

    private void setSpinnerVisibility(int visibility){
        wv.post(new Runnable() {
            @Override
            public void run() {
                spinner.setVisibility(visibility);
            }
        });
    }

    private void sendToClient(String type, JSONObject data){
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



    private void closeDialog(){
        dialog.dismiss();
        dialog = null;
        wv = null;
        spinner = null;
    }

    private void showWebView(Activity currentActivity){
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

                                InAppMessagePresenter.this.sendToClient("CLOSE_MESSAGE", null);
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
                    wv.addJavascriptInterface(new InAppJavaScriptInterface(currentActivity, InAppMessagePresenter.this), "Android");

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

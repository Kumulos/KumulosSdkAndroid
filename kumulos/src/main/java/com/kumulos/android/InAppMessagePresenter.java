package com.kumulos.android;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class InAppMessagePresenter {

    private static final String TAG = InAppMessagePresenter.class.getName();
    private static final String HOST_MESSAGE_TYPE_PRESENT_MESSAGE = "PRESENT_MESSAGE";
    private static final String HOST_MESSAGE_TYPE_CLOSE_MESSAGE = "CLOSE_MESSAGE";
    private static final String HOST_MESSAGE_TYPE_SET_NOTCH_INSETS = "SET_NOTCH_INSETS";
    private static final String IN_APP_RENDERER_URL = "https://iar.app.delivery";

    private static List<InAppMessage> messageQueue = new ArrayList<>();
    private static WebView wv = null;
    private static Dialog dialog = null;
    private static ProgressBar spinner = null;
    private static int prevStatusBarColor;
    private static boolean prevFlagTranslucentStatus;
    private static boolean prevFlagDrawsSystemBarBackgrounds;
    private static boolean presentationPendingOnResume = false;

    static synchronized void presentMessages(List<InAppMessage> itemsToPresent, List<Integer> tickleIds){
        Activity currentActivity = AnalyticsContract.ForegroundStateWatcher.getCurrentActivity();

        if (currentActivity == null) {
            return;
        }

        if (itemsToPresent.isEmpty()) {
            if (messageQueue.isEmpty()) {
                maybeCloseDialog(currentActivity);
            }

            return;
        }

        List<InAppMessage> oldQueue = new ArrayList<>(messageQueue);

        addMessagesToQueue(itemsToPresent);
        moveTicklesToFront(tickleIds);

        if (dialog == null){
            showWebView(currentActivity);
            return;
        }

        Activity dialogActivity = getDialogActivity(dialog.getContext());
        if(dialogActivity.hashCode() != currentActivity.hashCode()){
            closeDialog(dialogActivity);
            showWebView(currentActivity);
            return;
        }

        maybeRefreshFirstMessageInQueue(oldQueue);

        if (presentationPendingOnResume) {
            presentMessageToClient();
        }
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

    private static void moveTicklesToFront(List<Integer> tickleIds){
        if (tickleIds == null || tickleIds.isEmpty()){
            return;
        }

        for (Integer tickleId : tickleIds){
            for(int i=0; i<messageQueue.size(); i++){
                InAppMessage next = messageQueue.get(i);
                if (tickleId == next.getInAppId()){
                    messageQueue.remove(i);
                    messageQueue.add(0, next);

                    break;
                }
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
        Activity currentActivity = AnalyticsContract.ForegroundStateWatcher.getCurrentActivity();
        if (currentActivity == null){
            presentationPendingOnResume = true;

            return;
        }

        presentationPendingOnResume = false;

        if (messageQueue.isEmpty()){
            closeDialog(currentActivity);

            return;
        }

        if (dialog == null){
            return;
        }

        setSpinnerVisibility(View.VISIBLE);

        InAppMessage message = messageQueue.get(0);
        sendToClient(HOST_MESSAGE_TYPE_PRESENT_MESSAGE, message.getContent());
    }

    static void clientReady(Context context){
        if (wv == null){
            return;
        }

        wv.post(new Runnable() {
            @Override
            public void run() {
                maybeSetNotchInsets(context);
                presentMessageToClient();
            }
        });
    }

    private static void maybeSetNotchInsets(Context context){
        if (dialog == null){
            return;
        }

        Window window = dialog.getWindow();
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P){
            return;
        }

        DisplayCutout displayCutout = window.getDecorView().getRootWindowInsets().getDisplayCutout();
        if (displayCutout == null){
            return;
        }

        List<Rect> cutoutBoundingRectangles = displayCutout.getBoundingRects();
        if (cutoutBoundingRectangles.size() == 0) {
            return;
        }

        Pair notchPositions = determineNotchPositions(window, cutoutBoundingRectangles);
        float density = context.getResources().getDisplayMetrics().density;

        JSONObject notchData = new JSONObject();
        try{
            notchData.put("hasNotchOnTheLeft", notchPositions.first);
            notchData.put("hasNotchOnTheRight", notchPositions.second);
            notchData.put("insetTop", displayCutout.getSafeInsetTop() / density);
            notchData.put("insetRight", displayCutout.getSafeInsetRight() / density);
            notchData.put("insetBottom", displayCutout.getSafeInsetBottom() / density);
            notchData.put("insetLeft", displayCutout.getSafeInsetLeft() / density);

            sendToClient(HOST_MESSAGE_TYPE_SET_NOTCH_INSETS, notchData);
        }
        catch(JSONException e){
            Kumulos.log(TAG, e.getMessage());
        }
    }

    private static Pair<Boolean, Boolean> determineNotchPositions(Window window, List<Rect> cutoutBoundingRectangles){
        Display display = window.getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics ();
        display.getMetrics(outMetrics);

        boolean hasNotchOnTheRight = false;
        boolean hasNotchOnTheLeft = false;
        for (Rect rect: cutoutBoundingRectangles){
            if (rect.top == 0){
                if (rect.left > outMetrics.widthPixels - rect.right){
                    hasNotchOnTheRight = true;
                }
                else if (rect.left < outMetrics.widthPixels - rect.right){
                    hasNotchOnTheLeft = true;
                }
            }
            else if (rect.right >= outMetrics.widthPixels){
                hasNotchOnTheRight = true;
            }
            else if (rect.left == 0){
                hasNotchOnTheLeft = true;
            }
        }

        return new Pair<Boolean, Boolean>(hasNotchOnTheLeft, hasNotchOnTheRight);
    }

    static void messageOpened(Context context){
        InAppMessageService.trackOpenedEvent(context, messageQueue.get(0).getInAppId());
        setSpinnerVisibility(View.GONE);
    }

    static void messageClosed(){
        if (wv == null){
            return;
        }

        wv.post(new Runnable() {
            @Override
            public void run() {
                InAppMessage message = messageQueue.get(0);
                messageQueue.remove(0);

                presentMessageToClient();
            }
        });
    }

    static void closeCurrentMessage(Context context){
        if (dialog == null || messageQueue.isEmpty()){
            return;
        }

        InAppMessage message = messageQueue.get(0);

        InAppMessagePresenter.sendToClient(HOST_MESSAGE_TYPE_CLOSE_MESSAGE, null);

        InAppMessageService.handleMessageClosed(context, message);
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
            closeDialog(stoppedActivity);
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

    private static void closeDialog(Activity dialogActivity){
        if (dialog != null){
            unsetStatusBarColorForDialog(dialogActivity);
            dialog.setOnKeyListener(null);
            dialog.dismiss();
        }
        dialog = null;
        wv = null;
        spinner = null;
    }

    private static void setStatusBarColorForDialog(Activity currentActivity){
        if (currentActivity == null){
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP){
            return;
        }

        Window window = currentActivity.getWindow();

        prevStatusBarColor = window.getStatusBarColor();

        int flags = window.getAttributes().flags;
        prevFlagTranslucentStatus = (flags & WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS) != 0;
        prevFlagDrawsSystemBarBackgrounds = (flags & WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0;

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        int statusBarColor;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            statusBarColor = currentActivity.getResources().getColor(R.color.statusBarColorForNotch, null);
        }
        else{
            statusBarColor = currentActivity.getResources().getColor(R.color.statusBarColorForNotch);
        }

        window.setStatusBarColor(statusBarColor);
    }

    private static void unsetStatusBarColorForDialog(Activity dialogActivity){
        if (dialogActivity == null){
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP){
            return;
        }

        Window window = dialogActivity.getWindow();
        window.setStatusBarColor(prevStatusBarColor);

        if (prevFlagTranslucentStatus){
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        if (!prevFlagDrawsSystemBarBackgrounds){
            window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
    }


    private static void showWebView(Activity currentActivity){
        Handler mHandler = new Handler(Looper.getMainLooper());
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (dialog != null){
                    return;
                }

                try {
                    if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        WebView.setWebContentsDebuggingEnabled(true);
                    }

                    RelativeLayout.LayoutParams paramsWebView = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
                    dialog = new Dialog(currentActivity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);

                    Window window = dialog.getWindow();
                    if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
                        WindowManager.LayoutParams windowAttributes = dialog.getWindow().getAttributes();
                        windowAttributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

                        View view = window.getDecorView();
                        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                    }

                    LayoutInflater inflater = (LayoutInflater) currentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    dialog.setContentView(inflater.inflate(R.layout.dialog_view, null), paramsWebView);
                    dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                        @Override
                        public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {
                            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() != KeyEvent.ACTION_DOWN) {
                                InAppMessagePresenter.closeCurrentMessage(currentActivity);
                            }
                            return true;
                        }
                    });
                    dialog.show();

                    wv = (WebView) dialog.findViewById(R.id.webview);
                    spinner = dialog.findViewById(R.id.progressBar);

                    int cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK;
                    if (BuildConfig.DEBUG) {
                        cacheMode = WebSettings.LOAD_NO_CACHE;
                    }
                    wv.getSettings().setCacheMode(cacheMode);

                    wv.setBackgroundColor(android.graphics.Color.TRANSPARENT);

                    WebSettings settings = wv.getSettings();
                    settings.setJavaScriptEnabled(true);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){
                        settings.setMediaPlaybackRequiresUserGesture(false);
                    }

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
                            setStatusBarColorForDialog(currentActivity);
                            super.onPageFinished(view, url);
                        }

                        @SuppressWarnings("deprecation")
                        @Override
                        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                            if (BuildConfig.DEBUG){
                                Kumulos.log(TAG, "Error code: "+errorCode+". "+description);
                            }

                            closeDialog(currentActivity);
                        }

                        @TargetApi(android.os.Build.VERSION_CODES.M)
                        @Override
                        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError rerr) {
                            onReceivedError(view, rerr.getErrorCode(), rerr.getDescription().toString(), req.getUrl().toString());
                        }

                    });

                    wv.loadUrl(IN_APP_RENDERER_URL);
                }
                catch(Exception e){
                    Kumulos.log(TAG, e.getMessage());
                }
            }
        });
    }
}
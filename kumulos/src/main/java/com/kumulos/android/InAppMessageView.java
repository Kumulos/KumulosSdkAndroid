package com.kumulos.android;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
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
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class InAppMessageView extends WebViewClient {

    private enum State {
        INITIAL,
        LOADING,
        READY,
        DISPOSED
    }

    private static final String TAG = InAppMessageView.class.getName();
    private static final String IN_APP_RENDERER_URL = "https://iar.app.delivery";
    // Use for simulating a renderer process crash (triggers onRenderProcessGone())
    // private static final String IN_APP_RENDERER_URL = "chrome://crash";

    private static final String BUTTON_ACTION_CLOSE_MESSAGE = "closeMessage";
    private static final String BUTTON_ACTION_SUBSCRIBE_TO_CHANNEL = "subscribeToChannel";
    private static final String BUTTON_ACTION_TRACK_CONVERSION_EVENT = "trackConversionEvent";
    private static final String BUTTON_ACTION_OPEN_URL = "openUrl";
    private static final String BUTTON_ACTION_DEEP_LINK = "deepLink";
    private static final String BUTTON_ACTION_REQUEST_APP_STORE_RATING = "requestAppStoreRating";
    private static final String BUTTON_ACTION_PUSH_REGISTER = "promptPushPermission";

    private static final String HOST_MESSAGE_TYPE_PRESENT_MESSAGE = "PRESENT_MESSAGE";
    private static final String HOST_MESSAGE_TYPE_CLOSE_MESSAGE = "CLOSE_MESSAGE";
    private static final String HOST_MESSAGE_TYPE_SET_NOTCH_INSETS = "SET_NOTCH_INSETS";

    private static final String JS_NAME = "Android";

    private State state;
    private boolean pageFinished;

    @NonNull
    private final Activity currentActivity;

    @Nullable
    private WebView wv;
    @Nullable
    private Dialog dialog;
    @Nullable
    private ProgressBar spinner;

    private int prevStatusBarColor;
    private boolean prevFlagTranslucentStatus;
    private boolean prevFlagDrawsSystemBarBackgrounds;

    @NonNull
    private final InAppMessagePresenter2 presenter;
    @NonNull
    private InAppMessage currentMessage;

    @UiThread
    InAppMessageView(@NonNull InAppMessagePresenter2 presenter, @NonNull InAppMessage message, @NonNull Activity currentActivity) {
        this.state = State.INITIAL;
        pageFinished = false;
        this.presenter = presenter;
        this.currentActivity = currentActivity;
        this.currentMessage = message;

        showWebView(currentActivity);
    }

    @UiThread
    void showMessage(@NonNull InAppMessage currentMessage) {
        this.currentMessage = currentMessage;

        sendCurrentMessageToClient();
    }

    @UiThread
    void dispose() {
        closeDialog(currentActivity);
        state = State.DISPOSED;
    }

    @UiThread
    private void sendCurrentMessageToClient() {
        if (state == State.READY && pageFinished) {
            sendToClient(HOST_MESSAGE_TYPE_PRESENT_MESSAGE, currentMessage.getContent());
        }
    }

    @UiThread
    private void setSpinnerVisibility(int visibility) {
        if (spinner != null) {
            spinner.setVisibility(visibility);
        }
    }

    @UiThread
    private void sendToClient(String type, JSONObject data) {
        if (wv == null) {
            return;
        }

        JSONObject j = new JSONObject();
        try {
            j.put("data", data);
            j.put("type", type);
        } catch (JSONException e) {
            Log.d(TAG, "Could not create client message");
            return;
        }

        String script = "window.postHostMessage(" + j.toString() + ")";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            wv.evaluateJavascript(script, null);
        } else {
            wv.loadUrl("javascript:" + script);
        }
    }

    @UiThread
    private void setStatusBarColorForDialog(Activity currentActivity) {
        if (currentActivity == null) {
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        Window window = currentActivity.getWindow();

        prevStatusBarColor = window.getStatusBarColor();

        int flags = window.getAttributes().flags;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            prevFlagTranslucentStatus = (flags & WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS) != 0;
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        prevFlagDrawsSystemBarBackgrounds = (flags & WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0;
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        int statusBarColor;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            statusBarColor = currentActivity.getResources().getColor(R.color.statusBarColorForNotch, null);
        } else {
            statusBarColor = currentActivity.getResources().getColor(R.color.statusBarColorForNotch);
        }

        window.setStatusBarColor(statusBarColor);
    }

    @UiThread
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void unsetStatusBarColorForDialog(Activity dialogActivity) {
        if (dialogActivity == null) {
            return;
        }

        Window window = dialogActivity.getWindow();
        window.setStatusBarColor(prevStatusBarColor);

        if (prevFlagTranslucentStatus) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        if (!prevFlagDrawsSystemBarBackgrounds) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
    }

    @UiThread
    private void closeDialog(Activity dialogActivity) {
        if (dialog != null) {
            dialog.setOnKeyListener(null);
            dialog.dismiss();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                unsetStatusBarColorForDialog(dialogActivity);
            }
        }

        if (null != wv) {
            wv.destroy();
        }

        dialog = null;
        wv = null;
        spinner = null;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface", "InflateParams"})
    @UiThread
    private void showWebView(@NonNull final Activity currentActivity) {
        try {
            if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(true);
            }

            RelativeLayout.LayoutParams paramsWebView = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            dialog = new Dialog(currentActivity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);

            Window window = dialog.getWindow();
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams windowAttributes = dialog.getWindow().getAttributes();
                windowAttributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

                View view = window.getDecorView();
                view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            }

            LayoutInflater inflater = (LayoutInflater) currentActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            dialog.setContentView(inflater.inflate(R.layout.kumulos_dialog_view, null), paramsWebView);
            dialog.setOnKeyListener((dialog, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() != KeyEvent.ACTION_DOWN) {
                    closeCurrentMessage();
                }
                return true;
            });

            wv = dialog.findViewById(R.id.kumulos_webview);
            spinner = dialog.findViewById(R.id.kumulos_progressBar);

            if (null == wv || null == spinner) {
                dispose();
                return;
            }

            int cacheMode = WebSettings.LOAD_DEFAULT;
            if (BuildConfig.DEBUG) {
                cacheMode = WebSettings.LOAD_NO_CACHE;
            }
            wv.getSettings().setCacheMode(cacheMode);

            wv.setBackgroundColor(android.graphics.Color.TRANSPARENT);

            WebSettings settings = wv.getSettings();
            settings.setJavaScriptEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                settings.setMediaPlaybackRequiresUserGesture(false);
            }

            wv.addJavascriptInterface(this, JS_NAME);
            wv.setWebViewClient(this);

            dialog.show();

            setSpinnerVisibility(View.VISIBLE);
            wv.loadUrl(IN_APP_RENDERER_URL);
            state = State.LOADING;
        } catch (Exception e) {
            Kumulos.log(TAG, e.getMessage());
        }
    }

    private void closeCurrentMessage() {
        sendToClient(HOST_MESSAGE_TYPE_CLOSE_MESSAGE, null);
        InAppMessageService.handleMessageClosed(currentActivity, currentMessage);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setStatusBarColorForDialog(currentActivity);
        pageFinished = true;

        sendCurrentMessageToClient();

        super.onPageFinished(view, url);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        super.onReceivedHttpError(view, request, errorResponse);

        String url = request.getUrl().toString();
        // Only consider handling for failures of our renderer assets
        // 3rd-party fonts/images etc. shouldn't trigger this
        if (!url.startsWith(IN_APP_RENDERER_URL)) {
            return;
        }

        // Cached index page may refer to stale JS/CSS file hashes
        // Evict the cache to allow next presentation to re-fetch
        if (404 == errorResponse.getStatusCode()) {
            view.clearCache(true);
        }

        closeDialog(currentActivity);
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        super.onReceivedSslError(view, handler, error);

        closeDialog(currentActivity);
    }

    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        closeDialog(currentActivity);

        // Allow app to keep running, don't terminate
        return true;
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        Kumulos.log(TAG, "Error code: " + errorCode + ". " + description + " " + failingUrl);

        String extension = failingUrl.substring(failingUrl.length() - 4);
        boolean isVideo = extension.matches(".mp4|.m4a|.m4p|.m4b|.m4r|.m4v");
        if (errorCode == -1 && "net::ERR_FAILED".equals(description) && isVideo) {
            // This is a workaround for a bug in the WebView.
            // See these chromium issues for more context:
            // https://bugs.chromium.org/p/chromium/issues/detail?id=1023678
            // https://bugs.chromium.org/p/chromium/issues/detail?id=1050635

            //We encountered the issue only with some (and not other) videos, but possibly not limited to other file types
            return;
        }

        closeDialog(currentActivity);
    }

    @Override
    @TargetApi(android.os.Build.VERSION_CODES.M)
    public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError rerr) {
        onReceivedError(view, rerr.getErrorCode(), rerr.getDescription().toString(), req.getUrl().toString());
    }

    @JavascriptInterface
    @AnyThread
    public void postClientMessage(String msg) {
        String messageType;
        JSONObject data;
        try {
            JSONObject message = new JSONObject(msg);
            messageType = message.getString("type");
            data = message.optJSONObject("data");
        } catch (JSONException e) {
            Log.d(TAG, "Incorrect message format: " + msg);
            return;
        }

        switch (messageType) {
            case "READY":
                currentActivity.runOnUiThread(() -> {
                    if (state == State.DISPOSED) {
                        return;
                    }

                    state = State.READY;

                    maybeSetNotchInsets(currentActivity);
                    sendCurrentMessageToClient();
                });
                return;
            case "MESSAGE_OPENED":
                currentActivity.runOnUiThread(() -> {
                    if (state == State.DISPOSED) {
                        return;
                    }

                    setSpinnerVisibility(View.GONE);
                    InAppMessageService.handleMessageOpened(currentActivity, currentMessage);
                });
                return;
            case "MESSAGE_CLOSED":
                currentActivity.runOnUiThread(presenter::messageClosed);
                return;
            case "EXECUTE_ACTIONS":
                if (null == data) {
                    return;
                }
                List<ExecutableAction> actions = this.parseButtonActionData(data);
                currentActivity.runOnUiThread(() -> this.executeActions(currentActivity, actions));
                return;
            default:
                Log.d(TAG, "Unknown message type: " + messageType);
        }
    }

    @UiThread
    private void maybeSetNotchInsets(Context context) {
        if (dialog == null) {
            return;
        }

        Window window = dialog.getWindow();
        if (window == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }

        DisplayCutout displayCutout = window.getDecorView().getRootWindowInsets().getDisplayCutout();
        if (displayCutout == null) {
            return;
        }

        List<Rect> cutoutBoundingRectangles = displayCutout.getBoundingRects();
        if (cutoutBoundingRectangles.size() == 0) {
            return;
        }

        Pair<Boolean, Boolean> notchPositions = determineNotchPositions(window, cutoutBoundingRectangles);
        float density = context.getResources().getDisplayMetrics().density;

        JSONObject notchData = new JSONObject();
        try {
            notchData.put("hasNotchOnTheLeft", notchPositions.first);
            notchData.put("hasNotchOnTheRight", notchPositions.second);
            notchData.put("insetTop", displayCutout.getSafeInsetTop() / density);
            notchData.put("insetRight", displayCutout.getSafeInsetRight() / density);
            notchData.put("insetBottom", displayCutout.getSafeInsetBottom() / density);
            notchData.put("insetLeft", displayCutout.getSafeInsetLeft() / density);

            sendToClient(HOST_MESSAGE_TYPE_SET_NOTCH_INSETS, notchData);
        } catch (JSONException e) {
            Kumulos.log(TAG, e.getMessage());
        }
    }

    @UiThread
    private Pair<Boolean, Boolean> determineNotchPositions(Window window, List<Rect> cutoutBoundingRectangles) {
        Display display = window.getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        boolean hasNotchOnTheRight = false;
        boolean hasNotchOnTheLeft = false;
        for (Rect rect : cutoutBoundingRectangles) {
            if (rect.top == 0) {
                if (rect.left > outMetrics.widthPixels - rect.right) {
                    hasNotchOnTheRight = true;
                } else if (rect.left < outMetrics.widthPixels - rect.right) {
                    hasNotchOnTheLeft = true;
                }
            } else if (rect.right >= outMetrics.widthPixels) {
                hasNotchOnTheRight = true;
            } else if (rect.left == 0) {
                hasNotchOnTheLeft = true;
            }
        }

        return new Pair<>(hasNotchOnTheLeft, hasNotchOnTheRight);
    }

    @UiThread
    private void executeActions(Activity currentActivity, List<ExecutableAction> actions) {
        // Handle 'secondary' actions
        for (ExecutableAction action : actions) {
            switch (action.getType()) {
                case BUTTON_ACTION_CLOSE_MESSAGE:
                    closeCurrentMessage();
                    break;
                case BUTTON_ACTION_SUBSCRIBE_TO_CHANNEL:
                    PushSubscriptionManager psm = new PushSubscriptionManager();
                    psm.subscribe(currentActivity, new String[]{action.getChannelUuid()});
                    break;
                case BUTTON_ACTION_TRACK_CONVERSION_EVENT:
                    Kumulos.trackEventImmediately(currentActivity, action.getEventType(), action.getConversionEventData());
                    break;
            }
        }

        // Handle 'terminating' actions
        for (ExecutableAction action : actions) {
            switch (action.getType()) {
                case BUTTON_ACTION_OPEN_URL:
                    presenter.cancelCurrentPresentationQueue();

                    this.openUrl(currentActivity, action.getUrl());
                    return;
                case BUTTON_ACTION_DEEP_LINK:
                    if (null != KumulosInApp.inAppDeepLinkHandler) {
                        presenter.cancelCurrentPresentationQueue();

                        KumulosInApp.inAppDeepLinkHandler.handle(currentActivity.getApplicationContext(),
                                new InAppDeepLinkHandlerInterface.InAppButtonPress(
                                        action.getDeepLink(),
                                        currentMessage.getInAppId(),
                                        currentMessage.getData()
                                )
                        );
                    }
                    return;
                case BUTTON_ACTION_REQUEST_APP_STORE_RATING:
                    presenter.cancelCurrentPresentationQueue();

                    this.openPlayStore(currentActivity);
                    return;
                case BUTTON_ACTION_PUSH_REGISTER:
                    presenter.cancelCurrentPresentationQueue();

                    Kumulos.pushRegister(currentActivity);
                    return;
            }
        }
    }

    private void openPlayStore(Activity currentActivity) {
        String packageName = currentActivity.getPackageName();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName));
        if (intent.resolveActivity(currentActivity.getPackageManager()) != null) {
            currentActivity.startActivity(intent);
            return;
        }

        intent.setData(Uri.parse("https://play.google.com/store/apps/details?" + packageName));
        currentActivity.startActivity(intent);
    }

    private void openUrl(Activity currentActivity, String uri) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        if (browserIntent.resolveActivity(currentActivity.getPackageManager()) != null) {
            currentActivity.startActivity(browserIntent);
        }
    }

    private List<ExecutableAction> parseButtonActionData(@NonNull JSONObject data) {
        List<ExecutableAction> actions = new ArrayList<>();
        JSONArray rawActions = data.optJSONArray("actions");

        if (null == rawActions) {
            return actions;
        }

        for (int i = 0; i < rawActions.length(); i++) {
            JSONObject rawAction = rawActions.optJSONObject(i);

            String actionType = rawAction.optString("type");
            JSONObject rawActionData = rawAction.optJSONObject("data");

            if (null == rawActionData) {
                continue;
            }

            ExecutableAction action = new ExecutableAction();
            action.setType(actionType);

            switch (actionType) {
                case BUTTON_ACTION_SUBSCRIBE_TO_CHANNEL:
                    String channelUuid = rawActionData.optString("channelUuid");
                    action.setChannelUuid(channelUuid);
                    break;
                case BUTTON_ACTION_TRACK_CONVERSION_EVENT:
                    String eventType = rawActionData.optString("eventType");
                    JSONObject eventData = rawActionData.optJSONObject("data");
                    action.setEventType(eventType);
                    action.setConversionEventData(eventData);
                    break;
                case BUTTON_ACTION_OPEN_URL:
                    String url = rawActionData.optString("url");
                    action.setUrl(url);
                    break;
                case BUTTON_ACTION_DEEP_LINK:
                    JSONObject deepLink = rawActionData.optJSONObject("deepLink");
                    action.setDeepLink(deepLink);

                default:
            }
            actions.add(action);
        }

        return actions;
    }

    private static class ExecutableAction {
        String type;

        String url;
        String channelUuid;
        String eventType;
        JSONObject deepLink;
        JSONObject conversionEventData;

        void setType(String type) {
            this.type = type;
        }

        void setChannelUuid(String channelUuid) {
            this.channelUuid = channelUuid;
        }

        void setEventType(String eventType) {
            this.eventType = eventType;
        }

        void setConversionEventData(JSONObject data) {
            conversionEventData = data;
        }

        void setUrl(String url) {
            this.url = url;
        }

        void setDeepLink(JSONObject deepLink) {
            this.deepLink = deepLink;
        }

        String getType() {
            return type;
        }

        String getUrl() {
            return url;
        }

        String getChannelUuid() {
            return channelUuid;
        }

        String getEventType() {
            return eventType;
        }

        JSONObject getConversionEventData() {
            return conversionEventData;
        }

        JSONObject getDeepLink() {
            return deepLink;
        }
    }

}

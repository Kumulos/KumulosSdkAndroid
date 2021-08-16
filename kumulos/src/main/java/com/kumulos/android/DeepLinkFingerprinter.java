package com.kumulos.android;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class Deferred<R> {
    enum State {
        PENDING,
        RESOLVED
    }

    private State state = State.PENDING;
    private final List<Kumulos.ResultCallback<R>> pendingWatchers = new ArrayList<>();
    private R components;

    void resolve(R result) {
        if (state == State.RESOLVED) {
            return;
        }

        state = State.RESOLVED;
        components = result;

        new Handler(Looper.getMainLooper()).post(() -> {
            for (Kumulos.ResultCallback<R> watcher : pendingWatchers) {
                watcher.onSuccess(result);
            }

            pendingWatchers.clear();
        });
    }

    void then(Kumulos.ResultCallback<R> onResult) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (state == State.PENDING) {
                pendingWatchers.add(onResult);
                return;
            }
            if (state == State.RESOLVED) {
                onResult.onSuccess(components);
            }
        });
    }
}

class DeepLinkFingerprinter {
    private static final String TAG = DeepLinkFingerprinter.class.getName();
    private WebView wv;
    private final String PRINT_DUST_RUNTIME_URL = "https://pd.app.delivery";
    static final String NAME = "Android";
    static final int PAGE_LOAD_TIMEOUT = 10000;

    private final String CLIENT_READY = "READY";
    private final String CLIENT_FINGERPRINT_GENERATED = "FINGERPRINT_GENERATED";
    private final String REQUEST_FINGERPRINT = "REQUEST_FINGERPRINT";

    private final Deferred<JSONObject> fingerprint;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @AnyThread
    public DeepLinkFingerprinter(Context context) {
        fingerprint = new Deferred<>();
        wv = new WebView(context);

        int cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK;
        if (BuildConfig.DEBUG) {
            cacheMode = WebSettings.LOAD_NO_CACHE;
        }
        wv.getSettings().setCacheMode(cacheMode);

        WebSettings settings = wv.getSettings();
        settings.setJavaScriptEnabled(true);

        wv.addJavascriptInterface(this, DeepLinkFingerprinter.NAME);

        wv.setWebViewClient(new WebViewClient() {
            boolean pageFinished;

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                pageFinished = false;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!pageFinished) {
                        cleanUpWebView();
                    }
                }, PAGE_LOAD_TIMEOUT);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageFinished = true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Kumulos.log(TAG, "Error code: " + errorCode + ". " + description + " " + failingUrl);

                cleanUpWebView();
            }

            @TargetApi(android.os.Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError rerr) {
                onReceivedError(view, rerr.getErrorCode(), rerr.getDescription().toString(), req.getUrl().toString());
            }
        });

        wv.loadUrl(PRINT_DUST_RUNTIME_URL);
    }

    public void getFingerprintComponents(Kumulos.ResultCallback<JSONObject> onGenerated) {
        fingerprint.then(onGenerated);
    }

    @UiThread
    private void cleanUpWebView() {
        if (this.wv == null) {
            return;
        }

        wv.stopLoading();
        wv.destroy();
        wv = null;
    }

    @JavascriptInterface
    @AnyThread
    public void postClientMessage(String msg) {
        String messageType = null;
        JSONObject data = null;
        try {
            JSONObject message = new JSONObject(msg);
            messageType = message.getString("type");
            data = message.optJSONObject("data");
        } catch (JSONException e) {
            Log.d(TAG, "Incorrect message format: " + msg);
            return;
        }

        switch (messageType) {
            case CLIENT_READY:
                new Handler(Looper.getMainLooper()).post(() -> {
                    this.sendToClient(REQUEST_FINGERPRINT, null);
                });
                return;
            case CLIENT_FINGERPRINT_GENERATED:
                if (data == null) {
                    return;
                }

                JSONObject components = data.optJSONObject("components");
                if (components == null) {
                    return;
                }

                fingerprint.resolve(components);

                new Handler(Looper.getMainLooper()).post(this::cleanUpWebView);

                return;
            default:
                Log.d(TAG, "Unknown message type: " + messageType);

        }
    }

    @UiThread
    private void sendToClient(String type, @Nullable JSONObject data) {
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
}

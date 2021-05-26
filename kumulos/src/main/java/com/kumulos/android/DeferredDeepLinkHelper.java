package com.kumulos.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static android.content.Context.CLIPBOARD_SERVICE;

public class DeferredDeepLinkHelper {
    private static final String BASE_URL = "https://links.kumulos.com";
    private static final String TAG = DeferredDeepLinkHelper.class.getName();

    /* package */ DeferredDeepLinkHelper() {
    }

    public class DeepLinkContent {
        DeepLinkContent(@Nullable String title, @Nullable String description) {
            this.title = title;
            this.description = description;
        }

        public @Nullable
        String title;
        public @Nullable
        String description;
    }

    public class DeepLink {
        public String url;
        public DeepLinkContent content;
        public JSONObject data;

        DeepLink(URL url, JSONObject obj) throws JSONException {
            this.url = url.toString();
            this.data = obj.getJSONObject("linkData");

            JSONObject content = obj.getJSONObject("content");
            String title = null;
            String description = null;
            if (content.has("title")) {
                title = content.getString("title");
            }
            if (content.has("description")) {
                description = content.getString("description");
            }
            this.content = new DeepLinkContent(title, description);
        }
    }

    public enum DeepLinkResolution {
        LOOKUP_FAILED,
        LINK_NOT_FOUND,
        LINK_EXPIRED,
        LINK_LIMIT_EXCEEDED,
        LINK_MATCHED
    }

    /* package */ void checkForDeferredLink(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
        boolean checked = preferences.getBoolean(SharedPrefs.DEFERRED_LINK_CHECKED_KEY, false);
        if (checked) {
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(SharedPrefs.DEFERRED_LINK_CHECKED_KEY, true);
        editor.apply();

        String text = this.getClipText(context);
        if (text == null) {
            return;
        }

        this.maybeProcessUrl(context, text, true);
    }


    /* package */ void maybeProcessUrl(Context context, String urlStr, boolean wasDeferred) {
        URL url = this.getURL(urlStr);
        if (url == null) {
            return;
        }

        if (!this.urlShouldBeHandled(url)) {
            return;
        }

        this.handleDeepLink(context, url, wasDeferred);
    }

    private @Nullable
    String getClipText(Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return null;
        }

        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null) {
            return null;
        }

        if (clip.getItemCount() != 1) {
            return null;
        }

        CharSequence text = clip.getItemAt(0).getText();
        if (TextUtils.isEmpty(text)) {
            return null;
        }

        return text.toString();
    }

    private @Nullable
    URL getURL(String text) {
        boolean isUrl = URLUtil.isValidUrl(text);
        if (!isUrl) {
            return null;
        }

        try {
            return new URL(text);

        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean urlShouldBeHandled(URL url) {
        String host = url.getHost();
        KumulosConfig config = Kumulos.getConfig();
        URL cname = config.getDeepLinkCname();

        return host.endsWith("lnk.click") || (cname != null && host.equals(cname.getHost()));
    }

    private void handleDeepLink(Context context, URL url, boolean wasDeferred) {
        OkHttpClient httpClient = Kumulos.getHttpClient();

        String slug = Uri.encode(url.getPath().replaceAll("/$|^/", ""));
        String params = "?wasDeferred=" + (wasDeferred ? 1 : 0);
        try {
            Map<String, String> map = HttpUtils.splitQuery(url);
            String webInstallId = map.get("webInstallId");
            if (webInstallId != null) {
                params = params + "&webInstallId=" + webInstallId;
            }
        } catch (UnsupportedEncodingException e) {
            Log.d(TAG, "Could not decode query parameters: " + e.getMessage());
        }

        String requestUrl = DeferredDeepLinkHelper.BASE_URL + "/v1/deeplinks/" + slug + params;

        final Request request = new Request.Builder()
                .url(requestUrl)
                .addHeader(Kumulos.KEY_AUTH_HEADER, Kumulos.authHeader)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .get()
                .build();

        this.makeNetworkRequest(context, httpClient, request, url, wasDeferred);

    }

    private void makeNetworkRequest(Context context, OkHttpClient httpClient, Request request, URL url, boolean wasDeferred) {
        Kumulos.executorService.submit(new Runnable() {
            @Override
            public void run() {
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        DeferredDeepLinkHelper.this.handledSuccessResponse(context, url, wasDeferred, response);
                    } else {
                        DeferredDeepLinkHelper.this.handleFailedResponse(context, url, response);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    DeferredDeepLinkHelper.this.invokeDeepLinkHandler(context, DeepLinkResolution.LOOKUP_FAILED, url, null);
                }
            }
        });
    }

    private void handledSuccessResponse(Context context, URL url, boolean wasDeferred, Response response) throws IOException {
        if (response.code() != 200) {
            this.invokeDeepLinkHandler(context, DeepLinkResolution.LOOKUP_FAILED, url, null);
            return;
        }

        try {
            JSONObject data = new JSONObject(response.body().string());
            DeepLink deepLink = new DeepLink(url, data);

            this.invokeDeepLinkHandler(context, DeepLinkResolution.LINK_MATCHED, url, deepLink);

            this.trackLinkMatched(context, url, wasDeferred);

        } catch (NullPointerException | JSONException e) {
            this.invokeDeepLinkHandler(context, DeepLinkResolution.LOOKUP_FAILED, url, null);
        }
    }

    private void trackLinkMatched(Context context, URL url, boolean wasDeferred) {
        JSONObject params = new JSONObject();
        try {
            params.put("url", url.toString());
            params.put("wasDeferred", wasDeferred);

            Kumulos.trackEvent(context, AnalyticsContract.EVENT_TYPE_DEEP_LINK_MATCHED, params);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleFailedResponse(Context context, URL url, Response response) {
        switch (response.code()) {
            case 404:
                this.invokeDeepLinkHandler(context, DeepLinkResolution.LINK_NOT_FOUND, url, null);
                break;
            case 410:
                this.invokeDeepLinkHandler(context, DeepLinkResolution.LINK_EXPIRED, url, null);
                break;
            case 429:
                this.invokeDeepLinkHandler(context, DeepLinkResolution.LINK_LIMIT_EXCEEDED, url, null);
                break;
            default:
                this.invokeDeepLinkHandler(context, DeepLinkResolution.LOOKUP_FAILED, url, null);
                break;
        }
    }

    private void invokeDeepLinkHandler(Context context, DeepLinkResolution resolution, URL url, @Nullable DeepLink data) {
        KumulosConfig config = Kumulos.getConfig();
        DeferredDeepLinkHandlerInterface handler = config.getDeferredDeepLinkHandler();
        if (handler == null) {
            return;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                handler.handle(context, resolution, url.toString(), data);
            }
        });
    }
}



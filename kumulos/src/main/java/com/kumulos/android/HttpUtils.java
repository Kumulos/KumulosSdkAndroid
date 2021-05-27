package com.kumulos.android;

import org.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

class HttpUtils {

    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    static RequestBody jsonBody(JSONObject object) {
        return RequestBody.create(MEDIA_TYPE_JSON, object.toString());
    }

    static Request.Builder authedJsonRequest(String url) {
        return new Request.Builder()
                .url(url)
                .addHeader(Kumulos.KEY_AUTH_HEADER, Kumulos.authHeader)
                .addHeader("Accept", "application/json");
    }
}

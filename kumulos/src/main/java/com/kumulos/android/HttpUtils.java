package com.kumulos.android;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

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

    static Map<String, String> splitQuery(URL url) throws UnsupportedEncodingException {
        Map<String, String> map = new LinkedHashMap<String, String>();
        String query = url.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            map.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return map;
    }
}

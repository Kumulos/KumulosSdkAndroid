package com.kumulos.android;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

class InAppRequestService {

    private static final String TAG = InAppRequestService.class.getName();

    static List<InAppMessage> readInAppMessages(Context c, Date lastSyncTime) {
        OkHttpClient httpClient;
        String userIdentifier = Kumulos.getCurrentUserIdentifier(c);

        try {
            httpClient = Kumulos.getHttpClient();
        } catch (Kumulos.UninitializedException e) {
            Kumulos.log(TAG, e.getMessage());
            return null;
        }

        String params = "";
        if (lastSyncTime != null){
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            params= "?after="+ sdf.format(lastSyncTime);
        }
        String encodedIdentifier = Uri.encode(userIdentifier);
        String url = Kumulos.PUSH_BASE_URL + "/v1/users/"+encodedIdentifier+"/messages"+params;

        final Request request = new Request.Builder()
                .url(url)
                .addHeader(Kumulos.KEY_AUTH_HEADER, Kumulos.authHeader)
                .addHeader("Accept", "application/json")
                .get()
                .build();

        List<InAppMessage> messages = null;

        try(Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logFailedResponse(response);
            }
            else{
                messages = getMessages(response);
            }
        }
        catch(IOException e){
            e.printStackTrace();
        }

        return messages;
    }

    private static void logFailedResponse(Response response){
        switch (response.code()) {
            case 404:
                Kumulos.log(TAG, "User not found");
                break;
            case 422:
                try {
                    Kumulos.log(TAG, response.body().string());
                } catch (NullPointerException|IOException e) {
                    Kumulos.log(TAG, e.getMessage());
                }
                break;
            default:
                Kumulos.log(TAG, response.message());
                break;
        }
    }

    private static List<InAppMessage> getMessages(Response response){
        try {
            JSONArray result = new JSONArray(response.body().string());
            List<InAppMessage> inAppMessages = new ArrayList<>();
            int len = result.length();

            for (int i = 0; i < len; i++) {
                InAppMessage message = new InAppMessage(result.getJSONObject(i));
                inAppMessages.add(message);
            }

            return inAppMessages;
        }
        catch (NullPointerException| JSONException | ParseException | IOException e) {
            Kumulos.log(TAG, e.getMessage());
            return null;
        }
    }
}
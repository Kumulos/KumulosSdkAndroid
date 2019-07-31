package com.kumulos.android.inapp;

import android.util.Log;

import com.kumulos.android.Kumulos;

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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;



public class InAppRequestService {

    private static class UserNotFoundException extends Exception {
        UserNotFoundException() {
            super("User not found");
        }
    }


    private static class ValidationException extends Exception {
        ValidationException(String s) {
            super(s);
        }
    }

    public void readInAppMessages(final com.kumulos.android.Kumulos.ResultCallback<List<InAppMessage>> callback, Date lastSyncTime) {
        OkHttpClient httpClient;
        String userIdentifier = "A67487";//app_10307 on staging. message_id 17. ????

        try {
            httpClient = Kumulos.getHttpClient();

        } catch (Kumulos.UninitializedException e) {
            callback.onFailure(e);
            return;
        }

        String params = "";
        if (lastSyncTime != null){
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            params= "?after="+ sdf.format(lastSyncTime);
        }
        String url = Kumulos.PUSH_BASE_URL + "/v1/users/"+userIdentifier+"/messages"+params;

        Log.d("vlad", url);


        final Request request = new Request.Builder()
                .url(url)
                .addHeader(Kumulos.KEY_AUTH_HEADER, Kumulos.authHeader)
                .addHeader("Accept", "application/json")
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    try {
                        JSONArray result = new JSONArray(response.body().string());
                        List<InAppMessage> inAppMessages = new ArrayList<>();
                        int len = result.length();

                        for (int i = 0; i < len; i++) {
                            InAppMessage message = new InAppMessage(result.getJSONObject(i));
                            inAppMessages.add(message);
                        }

                        callback.onSuccess(inAppMessages);
                    }
                    catch (NullPointerException| JSONException | ParseException | IOException e) {
                        callback.onFailure(e);
                    }

                    response.close();
                    return;
                }

                Log.d("vlad", ""+response.code());
                switch (response.code()) {
                    case 404:
                        response.close();
                        callback.onFailure(new UserNotFoundException());
                        break;
                    case 422:
                        try {
                            callback.onFailure(new ValidationException(response.body().string()));
                        } catch (NullPointerException|IOException e) {
                            callback.onFailure(e);
                        }
                        break;
                    default:
                        callback.onFailure(new Exception(response.message()));
                        response.close();
                        break;
                }
            }
        });
    }
}

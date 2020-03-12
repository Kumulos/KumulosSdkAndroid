
package com.kumulos.android;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ResponseHandler implements Callback {

    protected String url;
    protected int tag;
    @Nullable
    protected Map<String, String> params;
    @Nullable
    protected Map<String, Object> response;

    /**
     * Called when we receive a network response. Parses a response object
     * and calls the appropriate handler.
     *
     * Override if you want to handle the raw JSON API response yourself.
     *
     * @param body
     */
    public void onSuccess(String body) {
        Kumulos.log("Successful request, response:");
        response = Kumulos.getResponseObjectFromJson(body);

        if (null == response) {
            didFailWithError("Failed to parse response object from body: " + body);
            return;
        }

        Object sessionTokenValue = response.get("sessionToken");
        if (null != sessionTokenValue) {
            String sessionToken = String.valueOf(sessionTokenValue);
            Kumulos.setSessionToken(sessionToken);
        }

        Object responseCodeField = response.get("responseCode");
        if (null == responseCodeField) {
            didFailWithError("Failed to parse response code");
            return;
        }

        Integer responseCode;
        try {
            responseCode = Integer.parseInt(responseCodeField.toString());
        } catch (NumberFormatException e) {
            didFailWithError("Failed to parse response code");
            return;
        }

        if (responseCode != 1) {
            didFailWithError(String.valueOf(response.get("responseMessage")));
            return;
        }

        didCompleteWithResult(response.get("payload"));
    }

    /**
     * Called when a request fails at the network level. Can be overridden to handle connectivity problems etc.
     *
     * @param error
     */
    public void onFailure(@Nullable Throwable error) {
        Kumulos.log("Request has failed to complete");

        if (null != error) {
            Kumulos.log(error.toString());
            error.printStackTrace();
        }
    }

    /**
     * Called when a request from Kumulos completes. Should be overridden to handle the response.
     *
     * @param result
     */
    public void didCompleteWithResult(@Nullable Object result) {
        Kumulos.log("Currently in com.kumulos.android.ResponseHandler#didCompleteWithResult. Override this method to handle the response yourself.");
        if (null != result) {
            Kumulos.log(result.toString());
        }
    }

    /**
     * Called when a request from Kumulos fails for a logic reason (such as server error, unauthorized, account suspended)
     *
     * @param message
     */
    public void didFailWithError(String message) {
        Kumulos.log("Currently in com.kumulos.android.ResponseHandler#didFailWithError. Override this method to handle failures yourself.");
        if (null != message) {
            Kumulos.log("Kumulos returned an error: " + message);
        }
    }

    @Override
    final public void onFailure(Call call, IOException e) {
        onFailure(e);
    }

    @Override
    final public void onResponse(Call call, Response response) {
        String body;
        try {
            body = response.body().string();
        } catch (NullPointerException|IOException e) {
            onFailure(e);
            return;
        }

        if (response.isSuccessful()) {
            onSuccess(body);
        }
        else {
            onFailure(new Exception(response.message()));
        }
    }
}

package com.kumulos.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A client for interacting with the Kumulos contact tracing API
 */
public class KumulosCheckinClient {

    /**
     * Registers a checkin with Kumulos
     * <p>
     * Checkins contain one or more contacts associated with a location.
     * <p>
     * After creation, the checkin can be managed using the other client methods.
     *
     * @param context
     * @param checkin
     * @param resultCallback
     * @throws KumulosCheckin.ValidationException
     */
    public static void checkIn(Context context, KumulosCheckin checkin, Kumulos.ResultCallback<KumulosCheckin> resultCallback) throws KumulosCheckin.ValidationException {
        if (checkin.getContacts().size() < 1) {
            throw new KumulosCheckin.ValidationException("Must have at least one contact record added prior to check in");
        }

        JSONObject params;
        try {
            params = checkin.toJSONObject();
            params.put("tz", TimeZone.getDefault().getID());
            params.put("deviceKey", deviceKey(context));
        } catch (JSONException e) {
            resultCallback.onFailure(e);
            return;
        }

        String url = getUserCheckinsUrlBuilder(context, "checkins").toString();

        Request request = HttpUtils.authedJsonRequest(url)
                .post(HttpUtils.jsonBody(params))
                .build();

        Kumulos.getHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                resultCallback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (201 != response.code()) {
                        resultCallback.onFailure(new Exception("Http error: " + response.code()));
                        return;
                    }

                    assert body != null;
                    JSONObject data = new JSONObject(body.string());

                    KumulosCheckin checkin = KumulosCheckin.fromJSONObject(data);
                    resultCallback.onSuccess(checkin);
                } catch (JSONException e) {
                    resultCallback.onFailure(e);
                }
            }
        });
    }

    /**
     * Lists all open checkin models registered with Kumulos for this user on this device
     * <p>
     * Once checked out, a checkin will no longer be returned in this listing.
     *
     * @param context
     * @param resultCallback
     */
    public static void getOpenCheckins(Context context, Kumulos.ResultCallback<List<KumulosCheckin>> resultCallback) {
        String openCheckinsUrl = getUserCheckinsUrlBuilder(context, "open-checkins").toString();
        String url = getDeviceKeyedUrl(context, openCheckinsUrl);

        Request request = HttpUtils.authedJsonRequest(url).build();

        Kumulos.getHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                resultCallback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (200 != response.code()) {
                        resultCallback.onFailure(new Exception("Http error " + response.code()));
                        return;
                    }

                    assert body != null;
                    JSONArray data = new JSONArray(body.string());
                    List<KumulosCheckin> results = new ArrayList<>(data.length());

                    for (int i = 0; i < data.length(); ++i) {
                        JSONObject obj = data.getJSONObject(i);
                        KumulosCheckin checkin = KumulosCheckin.fromJSONObject(obj);
                        results.add(checkin);
                    }

                    resultCallback.onSuccess(results);
                } catch (JSONException e) {
                    resultCallback.onFailure(e);
                }
            }
        });
    }

    /**
     * Checks out all contacts associated with the group identified in the checkin model
     *
     * @param context
     * @param checkin
     * @param callback
     */
    public static void checkOut(@NonNull Context context, @NonNull KumulosCheckin checkin, @NonNull Kumulos.ResultCallback<KumulosCheckin> callback) {
        checkOut(context, checkin, null, callback);
    }

    /**
     * Checks out an individual contact from a checkin group
     * <p>
     * Once all contacts are checked out, the group checkin is considered closed.
     *
     * @param context
     * @param contact
     * @param callback
     */
    public static void checkOutContact(@NonNull Context context, @NonNull KumulosCheckin.Contact contact, @NonNull Kumulos.ResultCallback<KumulosCheckin> callback) {
        checkOut(context, null, contact, callback);
    }

    private static void checkOut(@NonNull Context context, @Nullable KumulosCheckin checkin, @Nullable KumulosCheckin.Contact contact, @NonNull Kumulos.ResultCallback<KumulosCheckin> callback) {
        StringBuilder urlBuilder = getUserCheckinsUrlBuilder(context, "checkins");

        if (null != checkin) {
            urlBuilder.append("/").append(checkin.getId());
        } else if (null != contact) {
            urlBuilder.append("/")
                    .append(contact.getCheckinId())
                    .append("/contacts/")
                    .append(contact.getId());
        }

        String url = getDeviceKeyedUrl(context, urlBuilder.toString());

        Request request = HttpUtils.authedJsonRequest(url)
                .delete()
                .build();

        Kumulos.getHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (200 != response.code()) {
                        callback.onFailure(new Exception("Http error " + response.code()));
                        return;
                    }

                    assert body != null;
                    JSONObject data = new JSONObject(body.string());
                    KumulosCheckin updatedCheckin = KumulosCheckin.fromJSONObject(data);

                    callback.onSuccess(updatedCheckin);
                } catch (JSONException e) {
                    callback.onFailure(e);
                }
            }
        });
    }

    private static synchronized String deviceKey(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);

        if (sp.contains(SharedPrefs.CHECKINS_DEVICE_KEY)) {
            return sp.getString(SharedPrefs.CHECKINS_DEVICE_KEY, "");
        }

        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);

        String deviceKey = Base64.encodeToString(bytes, Base64.NO_PADDING | Base64.NO_WRAP);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(SharedPrefs.CHECKINS_DEVICE_KEY, deviceKey);
        editor.apply();

        return deviceKey;
    }

    private static StringBuilder getUserCheckinsUrlBuilder(@NonNull Context context, String collection) {
        StringBuilder urlBuilder = new StringBuilder(Kumulos.CRM_BASE_URL)
                .append("/v1/users/");

        String encodedIdentifier = Uri.encode(Kumulos.getCurrentUserIdentifier(context));
        urlBuilder.append(encodedIdentifier);

        urlBuilder.append("/").append(collection);
        return urlBuilder;
    }

    private static String getDeviceKeyedUrl(Context context, String url) {
        return HttpUrl.parse(url)
                .newBuilder()
                .addQueryParameter("deviceKey", deviceKey(context))
                .toString();
    }

}

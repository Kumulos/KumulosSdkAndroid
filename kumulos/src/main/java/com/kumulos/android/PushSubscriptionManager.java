package com.kumulos.android;

import android.content.Context;
import androidx.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Allows managing the push subscriptions for this installation ID & creating channels from the SDK
 */
public class PushSubscriptionManager {

    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    private static class ChannelNotFoundException extends Exception {
        ChannelNotFoundException() {
            super("One or more of the specified channels were not found");
        }
    }

    private static class ValidationException extends Exception {
        ValidationException(String s) {
            super(s);
        }
    }

    /**
     * Subscribes the current installation to the push channels specified by their unique identifiers.
     *
     * Channels that don't exist will be created.
     *
     * @param c
     * @param uuids The unique push channel identifiers to subscribe to
     */
    public void subscribe(Context c, String[] uuids) {
        subscribe(c, uuids, new Kumulos.Callback() {
            @Override
            public void onSuccess() {
                /* noop */
            }
        });
    }

    /**
     * Subscribes the current installation to the push channels specified by their unique identifiers.
     *
     * Channels that don't exist will be created.
     *
     * @param c
     * @param uuids The unique push channel identifiers to subscribe to
     * @param callback
     */
    public void subscribe(Context c, String[] uuids, final Kumulos.Callback callback) {
        if (0 == uuids.length) {
            callback.onFailure(new ValidationException("Subscription request must specify at least one channel to subscribe to"));
            return;
        }

        OkHttpClient httpClient;
        String installId;

        try {
            httpClient = Kumulos.getHttpClient();
            installId = Kumulos.getInstallId();
        } catch (Kumulos.UninitializedException e) {
            callback.onFailure(e);
            return;
        }

        String url = Kumulos.PUSH_BASE_URL + "/v1/app-installs/" + installId + "/channels/subscriptions";

        JSONObject params = new JSONObject();
        try {
            params.put("uuids", new JSONArray(Arrays.asList(uuids)));
        } catch (JSONException e) {
            callback.onFailure(e);
            return;
        }

        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, params.toString());

        final Request request = new Request.Builder()
                .url(url)
                .addHeader(Kumulos.KEY_AUTH_HEADER, Kumulos.authHeader)
                .addHeader("Accept", "application/json")
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                    response.close();
                    return;
                }

                switch (response.code()) {
                    case 404:
                        response.close();
                        callback.onFailure(new ChannelNotFoundException());
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

    /**
     * Unsubscribes the current installation from the push channels specified by their unique identifiers.
     *
     * @param c
     * @param uuids The unique push channel identifiers to unsubscribe from
     */
    public void unsubscribe(Context c, String[] uuids) {
        unsubscribe(c, uuids, new Kumulos.Callback() {
            @Override
            public void onSuccess() {
                /* noop */
            }
        });
    }

    /**
     * Unsubscribes the current installation from the push channels specified by their unique identifiers.
     *
     * @param c
     * @param uuids The unique push channel identifiers to unsubscribe from
     * @param callback
     */
    public void unsubscribe(Context c, String[] uuids, final Kumulos.Callback callback) {
        if (0 == uuids.length) {
            callback.onFailure(new ValidationException("Unsubscribe request must specify at least one channel to unsubscribe from"));
            return;
        }

        OkHttpClient httpClient;
        String installId;

        try {
            httpClient = Kumulos.getHttpClient();
            installId = Kumulos.getInstallId();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        JSONObject params;
        try {
            params = new JSONObject();
            params.put("uuids", new JSONArray(Arrays.asList(uuids)));
        } catch (JSONException e) {
            callback.onFailure(e);
            return;
        }

        String url = Kumulos.PUSH_BASE_URL + "/v1/app-installs/" + installId + "/channels/subscriptions";

        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, params.toString());

        Request request = new Request.Builder()
                .url(url)
                .addHeader(Kumulos.KEY_AUTH_HEADER, Kumulos.authHeader)
                .addHeader("Accept", "application/json")
                .delete(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                    response.close();
                    return;
                }

                switch (response.code()) {
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

    /**
     * Subscribe the current installation to the given push channels.
     *
     * Any other existing channel subscriptions will be removed.
     *
     * @param c
     * @param uuids The unique push channel identifiers to subscribe to
     */
    public void setSubscriptions(Context c, String[] uuids) {
        setSubscriptions(c, uuids, new Kumulos.Callback() {
            @Override
            public void onSuccess() {
                /* noop */
            }
        });
    }

    /**
     * Subscribe the current installation to the given push channels.
     *
     * Any other existing channel subscriptions will be removed.
     *
     * @param c
     * @param uuids The unique push channel identifiers to subscribe to
     * @param callback
     */
    public void setSubscriptions(Context c, String[] uuids, final Kumulos.Callback callback) {
        OkHttpClient httpClient;
        String installId;

        try {
            httpClient = Kumulos.getHttpClient();
            installId = Kumulos.getInstallId();
        } catch (Kumulos.UninitializedException e) {
            callback.onFailure(e);
            return;
        }

        JSONObject params;
        try {
            params = new JSONObject();
            params.put("uuids", new JSONArray(Arrays.asList(uuids)));
        } catch (JSONException e) {
            callback.onFailure(e);
            return;
        }

        String url = Kumulos.PUSH_BASE_URL + "/v1/app-installs/" + installId + "/channels/subscriptions";

        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, params.toString());

        Request request = new Request.Builder()
                .url(url)
                .addHeader(Kumulos.KEY_AUTH_HEADER, Kumulos.authHeader)
                .addHeader("Accept", "application/json")
                .put(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                    response.close();
                    return;
                }

                switch (response.code()) {
                    case 404:
                        response.close();
                        callback.onFailure(new ChannelNotFoundException());
                        break;
                    case 422:
                        try {
                            try {
                                callback.onFailure(new ValidationException(response.body().string()));
                            } catch (NullPointerException|IOException e) {
                                callback.onFailure(e);
                            }
                        }
                        catch (NullPointerException e) {
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

    /**
     * Unsubscribe the existing installation from all push channel subscriptions.
     *
     * @param c
     */
    public void clearSubscriptions(Context c) {
        clearSubscriptions(c, new Kumulos.Callback() {
            @Override
            public void onSuccess() {
                /* noop */
            }
        });
    }

    /**
     * Unsubscribe the existing installation from all push channel subscriptions.
     *
     * @param c
     * @param callback
     */
    public void clearSubscriptions(Context c, final Kumulos.Callback callback) {
        this.setSubscriptions(c, new String[] {}, callback);
    }

    /**
     * Get a list of all push channels that are available for subscription or already subscribed to
     * by this installation.
     *
     * @param c
     * @param callback
     */
    public void listChannels(Context c, final Kumulos.ResultCallback<List<PushChannel>> callback) {
        OkHttpClient httpClient;
        String installId;

        try {
            httpClient = Kumulos.getHttpClient();
            installId = Kumulos.getInstallId();
        } catch (Kumulos.UninitializedException e) {
            callback.onFailure(e);
            return;
        }

        String url = Kumulos.PUSH_BASE_URL + "/v1/app-installs/" + installId + "/channels";

        Request request = new Request.Builder()
                .url(url)
                .addHeader(Kumulos.KEY_AUTH_HEADER, Kumulos.authHeader)
                .addHeader("Accept", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    callback.onFailure(new Exception(response.message()));
                    response.close();
                    return;
                }

                try {
                    JSONArray result = new JSONArray(response.body().string());
                    List<PushChannel> channels = new ArrayList<>();
                    int len = result.length();

                    for (int i = 0; i < len; i++) {
                        PushChannel channel = PushChannel.fromJsonObject(result.getJSONObject(i));
                        channels.add(channel);
                    }

                    callback.onSuccess(channels);
                }
                catch (NullPointerException|JSONException|IOException e) {
                    callback.onFailure(e);
                }
            }
        });
    }

    /**
     * Create a push channel for subscribing to.
     *
     * The channel will only be listed for subscribers.
     *
     * @param c
     * @param uuid Unique identifier for the channel
     * @param subscribe Subscribe the current installation as part of creation
     * @param callback
     */
    public void createChannel(Context c, String uuid, boolean subscribe, final Kumulos.ResultCallback<PushChannel> callback) {
        createChannel(c, uuid, subscribe, null, false, null, callback);
    }

    /**
     * Create a push channel for subscribing to.
     *
     * The channel will only be listed for subscribers.
     *
     * @param c
     * @param uuid Unique identifier for the channel
     * @param subscribe Subscribe the current installation as part of creation
     * @param meta Optional custom meta-data to associate with this push channel
     * @param callback
     */
    public void createChannel(Context c, String uuid, boolean subscribe, @Nullable JSONObject meta, final Kumulos.ResultCallback<PushChannel> callback) {
        createChannel(c, uuid, subscribe, null, false, meta, callback);
    }

    /**
     * Create a push channel for subscribing to.
     *
     * If no name is given, the channel will be listed to subscribers who subscribed directly by using
     * the uuid.
     *
     * If a name is given, the channel will show up in listings to all installations, even if not
     * subscribed.
     *
     * If the showInPortal flag is set, users of the Push Dashboard will be able to target this channel
     * directly from the UI.
     *
     * @param c
     * @param uuid Unique identifier for the channel
     * @param subscribe Subscribe the current installation as part of creation
     * @param name Optional descriptive name for the channel (required if showing in the portal)
     * @param showInPortal Should the channel show up in the portal for targeting?
     * @param meta Optional custom meta-data to associate with this push channel
     * @param callback
     */
    public void createChannel(Context c, String uuid, boolean subscribe, @Nullable String name, boolean showInPortal, @Nullable JSONObject meta, final Kumulos.ResultCallback<PushChannel> callback) {
        if (TextUtils.isEmpty(uuid)) {
            callback.onFailure(new ValidationException("Channel uuid must be specified for channel creation"));
            return;
        }

        if (showInPortal && TextUtils.isEmpty(name)) {
            callback.onFailure(new ValidationException("Channel name must be specified for channel creation if the channel should be displayed in the portal"));
            return;
        }

        OkHttpClient httpClient;
        String installId;

        try {
            httpClient = Kumulos.getHttpClient();
            installId = Kumulos.getInstallId();
        }
        catch (Kumulos.UninitializedException e) {
            callback.onFailure(e);
            return;
        }

        String url = Kumulos.PUSH_BASE_URL + "/v1/channels";

        JSONObject params = new JSONObject();
        try {
            params.put("uuid", uuid);
            params.put("name", TextUtils.isEmpty(name) ? null : name);
            params.put("showInPortal", showInPortal);
            params.put("meta", meta);

            if (subscribe) {
                params.put("installId", installId);
            }
        } catch (JSONException e) {
            callback.onFailure(e);
            return;
        }

        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, params.toString());

        Request request = new Request.Builder()
                .url(url)
                .addHeader(Kumulos.KEY_AUTH_HEADER, Kumulos.authHeader)
                .addHeader("Accept", "application/json")
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    handleResponse(response);
                    return;
                }

                switch (response.code()) {
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

            private void handleResponse(Response response) {
                try {
                    JSONObject obj = new JSONObject(response.body().string());
                    PushChannel channel = PushChannel.fromJsonObject(obj);

                    callback.onSuccess(channel);
                } catch (NullPointerException|IOException|JSONException e) {
                    callback.onFailure(e);
                }
            }
        });
    }

}

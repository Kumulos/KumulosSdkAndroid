
package com.kumulos.android;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.Debug;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.HttpSenderConfigurationBuilder;
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * The Kumulos class is the main public API for calling Kumulos RPC methods and handling push registration
 */
public final class Kumulos {

    public static final String VERSION = BuildConfig.VERSION_NAME;

    private static final String TAG = Kumulos.class.getName();
    private static final String API_BASE_URL = "https://api.kumulos.com/b2.2/";
    private static final String CRASH_BASE_URL = "https://crash.kumulos.com";
    /** package */ static final String PUSH_BASE_URL = "https://push.kumulos.com";
    /** package */ static final String EVENTS_BASE_URL = "https://events.kumulos.com";
    /** package */ static final String KEY_AUTH_HEADER = "Authorization";
    private static final String K_PREFS_FILE = "kumulos_prefs";
    private static boolean initialized;

    private static String installId;

    private static KumulosConfig currentConfig;

    private static transient String sessionToken;

    private static OkHttpClient httpClient;
    /** package */ static String authHeader;
    /** package */ static ExecutorService executorService;
    private static final Object userIdLocker = new Object();
    static Context applicationContext;


    /** package */ static class BaseCallback {
        public void onFailure(Exception e) {
            e.printStackTrace();
        }
    }

    public static abstract class Callback extends BaseCallback {
        public abstract void onSuccess();
    }

    public static abstract class ResultCallback<S> extends BaseCallback {
        public abstract void onSuccess(S result);
    }

    public static class UninitializedException extends Exception {
        UninitializedException() {
            super("The Kumulos has not been correctly initialized. Please ensure you have followed the integration guide before invoking SDK methods");
        }
    }

    static class ObjectMapperHolder {
        static final ObjectMapper jsonMapper = new ObjectMapper();
    }

    /**
     * Used to configure the Kumulos class. Only needs to be called once per process
     * @param application
     * @param config
     */
    public static synchronized void initialize(final Application application, KumulosConfig config) {
        if (initialized) {
            log("Kumulos is already initialized, aborting...");
            return;
        }

        applicationContext = application.getApplicationContext();

        currentConfig = config;

        installId = Installation.id(application);
        sessionToken = UUID.randomUUID().toString();

        authHeader = buildBasicAuthHeader(config.getApiKey(), config.getSecretKey());

        httpClient = new OkHttpClient();
        executorService = Executors.newSingleThreadExecutor();

        initialized = true;

        application.registerActivityLifecycleCallbacks(new AnalyticsContract.ForegroundStateWatcher(application));

        //IN APP
        application.registerActivityLifecycleCallbacks(new InAppActivityLifecycleWatcher());
        InAppTaskService its = new InAppTaskService();
        its.startPeriodicFetches(application);

        // Stats ping
        AnalyticsContract.StatsCallHomeRunnable statsTask = new AnalyticsContract.StatsCallHomeRunnable(application);
        executorService.submit(statsTask);

        if (config.crashReportingEnabled()) {
            // Crash reporting
            CoreConfigurationBuilder acraConfig = config.getAcraConfigBuilder(application);
            acraConfig
                    .setReportFormat(StringFormat.JSON)
                    .getPluginConfigurationBuilder(HttpSenderConfigurationBuilder.class)
                    .setEnabled(true)
                    .setUri(CRASH_BASE_URL + "/v1/track/" + config.getApiKey() + "/acra/" + installId)
                    .setBasicAuthLogin(config.getApiKey())
                    .setBasicAuthPassword(config.getSecretKey())
                    .setHttpMethod(HttpSender.Method.POST);

            boolean debugging = Debug.waitingForDebugger() || Debug.isDebuggerConnected();

            if (!debugging) {
                ACRA.init(application, acraConfig);
            } else {
                log(TAG, "Not attaching crash reporting whilst on the debugger");
            }
        }
    }

    //==============================================================================================
    //-- Getters/setters

    /**
     * Sets the current session token
     *
     * @param token
     */
    public static synchronized void setSessionToken(String token) {
        Kumulos.sessionToken = token;
    }

    /**
     * Gets the current session token
     *
     * @return
     */
    public static synchronized String getSessionToken() {
        return sessionToken;
    }

    /**
     * Gets the current config
     *
     * @return
     */
    public static KumulosConfig getConfig() {
        return currentConfig;
    }

    //==============================================================================================
    //-- RPC APIs

    /**
     * Used to make API calls to Kumulos. Pass in an anonymous handler to handle the response
     *
     * @param methodAlias
     * @param params
     * @param handler
     * @param tag
     */
    public static void call(String methodAlias, @Nullable Map<String, String> params, ResponseHandler handler, int tag) {
        Looper looper = Looper.myLooper();

        // If there's no Looper, we're being called from some arbitrary Thread so should use the sync HTTP client
        if (null == looper) {
            callSync(methodAlias, params, handler, tag);
            return;
        }

        String url = getMethodUrl(methodAlias);
        handler.params = params;
        handler.url = url;
        handler.tag = tag;

        Request request = new Request.Builder()
                .url(url)
                .addHeader(KEY_AUTH_HEADER, authHeader)
                .post(getRpcBody(params))
                .build();

        httpClient.newCall(request).enqueue(handler);
    }

    /**
     * Used to make API calls to Kumulos.
     *
     * @param methodAlias
     * @param params
     * @param handler
     */
    public static void call(String methodAlias, @Nullable Map<String, String> params, ResponseHandler handler) {
        call(methodAlias, params, handler, -1);
    }

    /**
     * Used to make API calls to Kumulos.
     *
     * @param methodAlias
     * @param handler
     */
    public static void call(String methodAlias, ResponseHandler handler) {
        call(methodAlias, null, handler, -1);
    }

    /**
     * Used to make blocking API calls to Kumulos from background threads.
     *
     * @param methodAlias
     * @param params
     * @param handler
     * @param tag
     */
    public static void callSync(String methodAlias, @Nullable Map<String, String> params, ResponseHandler handler, int tag) {
        String url = getMethodUrl(methodAlias);
        handler.params = params;
        handler.url = url;
        handler.tag = tag;

        Request request = new Request.Builder()
                .url(url)
                .addHeader(KEY_AUTH_HEADER, authHeader)
                .post(getRpcBody(params))
                .build();

        Call call = httpClient.newCall(request);
        Response response;

        try {
            response = call.execute();
        } catch (IOException e) {
            handler.onFailure(call, e);
            return;
        }

        handler.onResponse(call, response);
    }

    /**
     * Used to make blocking API calls to Kumulos from background threads.
     *
     * @param methodAlias
     * @param params
     * @param handler
     */
    public static void callSync(String methodAlias, @Nullable Map<String, String> params, ResponseHandler handler) {
        callSync(methodAlias, params, handler, -1);
    }

    /**
     * Used to make blocking API calls to Kumulos from background threads.
     *
     * @param methodAlias
     * @param handler
     */
    public static void callSync(String methodAlias, ResponseHandler handler) {
        callSync(methodAlias, null, handler, -1);
    }

    //==============================================================================================
    //-- Location APIs

    /**
     * Updates the location of the current installation in Kumulos
     * Accurate locaiton information is used for geofencing
     * @param context
     * @param location
     */
    public static void sendLocationUpdate(Context context, @Nullable Location location) {
        if (null == location) {
            return;
        }

        JSONObject props = new JSONObject();
        try {
            props.put("lat", location.getLatitude());
            props.put("lng", location.getLongitude());
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        trackEvent(context, AnalyticsContract.EVENT_TYPE_LOCATION_UPDATED, props, location.getTime(), true);
    }

    /**
     * Records a proximity event for an Eddystone beacon. Proximity events can be used in automation rules.
     * @param context
     * @param hexNamespace
     * @param hexInstance
     * @param distanceMetres - Optional distance to beacon in metres. If null, will not be recorded
     */
    public static void trackEddystoneBeaconProximity(@NonNull Context context, @NonNull String hexNamespace, @NonNull String hexInstance, @Nullable Double distanceMetres) {
        JSONObject properties = new JSONObject();
        try {
            properties.put("type", 2);
            properties.put("namespace", hexNamespace);
            properties.put("instance", hexInstance);

            if (null != distanceMetres) {
                properties.put("distance", distanceMetres);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        trackEvent(context, AnalyticsContract.EVENT_TYPE_ENTERED_BEACON_PROXIMITY, properties, System.currentTimeMillis(), true);
    }

    //==============================================================================================
    //-- Crash APIs

    /**
     * Logs a caught exception as a non-fatal issue with Kumulos Crash reporting
     * @param throwable
     */
    public static void logException(@Nullable Throwable throwable) {
        if (!ACRA.isInitialised() || !currentConfig.crashReportingEnabled()) {
            log(TAG, "Crash reporting is not enabled or initialized (are you on the debugger?)");
            return;
        }

        ACRA.getErrorReporter().handleSilentException(throwable);
    }

    //==============================================================================================
    //-- Analytics APIs

    /** package */ static void trackEvent(@NonNull final Context context, @NonNull final String eventType, @Nullable final JSONObject properties, final long timestamp, boolean immediateFlush) {
        if (TextUtils.isEmpty(eventType)) {
            throw new IllegalArgumentException("Kumulos.trackEvent expects a non-empty event type");
        }

        Runnable trackingTask = new AnalyticsContract.TrackEventRunnable(context, eventType, timestamp, properties, immediateFlush);
        executorService.submit(trackingTask);
    }

    /**
     * Tracks a custom analytics event with Kumulos.
     *
     * Events are persisted locally and synced to the server in the background in batches.
     *
     * @param context
     * @param eventType Identifier for the event category
     * @param properties Additional information about the event
     */
    public static void trackEvent(@NonNull final Context context, @NonNull final String eventType, @Nullable final JSONObject properties) {
        trackEvent(context, eventType, properties, System.currentTimeMillis(), false);
    }

    /**
     * Tracks a custom analytics event with Kumulos.
     *
     * After being recorded locally, all stored events will be flushed to the server.
     *
     * @param context
     * @param eventType Identifier for the event category
     * @param properties Additional information about the event
     */
    public static void trackEventImmediately(@NonNull final Context context, @NonNull final String eventType, @Nullable final JSONObject properties) {
        trackEvent(context, eventType, properties, System.currentTimeMillis(), true);
    }

    /**
     * Associates a user identifier with the current Kumulos installation record.
     * @param context
     * @param userIdentifier
     */
    public static void associateUserWithInstall(Context context, @NonNull final String userIdentifier) {
        associateUserWithInstallImpl(context, userIdentifier, null);
    }

    /**
     * Associates a user identifier with the current Kumulos installation record, additionally setting the attributes for the user.
     * @param context
     * @param userIdentifier
     * @param attributes
     */
    public static void associateUserWithInstall(Context context, @NonNull final String userIdentifier, @NonNull final JSONObject attributes) {
        associateUserWithInstallImpl(context, userIdentifier, attributes);
    }

    /**
     * Clears any existing association between this install record and a user identifier
     * @see Kumulos#associateUserWithInstall(Context, String)
     * @see Kumulos#getCurrentUserIdentifier(Context)
     * @param context
     */
    public static void clearUserAssociation(@NonNull Context context) {
        String currentUserId = null;
        SharedPreferences prefs = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);

        synchronized (userIdLocker) {
            currentUserId = prefs.getString(SharedPrefs.KEY_USER_IDENTIFIER, null);
        }

        JSONObject props = new JSONObject();
        try {
            props.put("oldUserIdentifier", currentUserId);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        trackEvent(context, AnalyticsContract.EVENT_TYPE_CLEAR_USER_ASSOCIATION, props);

        synchronized (userIdLocker) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(SharedPrefs.KEY_USER_IDENTIFIER);
            editor.apply();
        }
    }

    /**
     * Returns the identifier for the user currently associated with the Kumulos installation record
     *
     * @see Kumulos#associateUserWithInstall(Context, String)
     * @see Installation#id(Context)
     *
     * @param context
     * @return The current user identifier (if available), otherwise the Kumulos installation ID
     */
    public static String getCurrentUserIdentifier(@NonNull Context context) {
        synchronized (userIdLocker) {
            SharedPreferences preferences = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
            return preferences.getString(SharedPrefs.KEY_USER_IDENTIFIER, Installation.id(context));
        }
    }

    private static void associateUserWithInstallImpl(Context context, @NonNull final String userIdentifier, @Nullable final JSONObject attributes) {
        if (TextUtils.isEmpty(userIdentifier)) {
            throw new IllegalArgumentException("Kumulos.associateUserWithInstall requires a non-empty user identifier");
        }

        JSONObject props = new JSONObject();

        try {
            props.put("id", userIdentifier);
            if (null != attributes) {
                props.put("attributes", attributes);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);

        synchronized (userIdLocker) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(SharedPrefs.KEY_USER_IDENTIFIER, userIdentifier);
            editor.apply();
        }

        trackEvent(context, AnalyticsContract.EVENT_TYPE_ASSOCIATE_USER, props);
    }

    //==============================================================================================
    //-- Push APIs

    /**
     * Used to register the device installation with FCM to receive push notifications
     *
     * @param context
     */
    public static void pushRegister(Context context) {
        PushRegistration.RegisterTask task = new PushRegistration.RegisterTask(context);
        executorService.submit(task);
    }

    /**
     * Used to unregister the current installation from receiving push notifications
     *
     * @param context
     */
    public static void pushUnregister(Context context) {
        PushRegistration.UnregisterTask task = new PushRegistration.UnregisterTask(context);
        executorService.submit(task);
    }

    /**
     * Used to track a conversion from a push notification
     *
     * @param context
     * @param id
     */
    public static void pushTrackOpen(Context context, final String id) throws UninitializedException {
        log("PUSH: Tracking open for " + id);

        JSONObject props = new JSONObject();
        try {
            props.put("id", id);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        trackEvent(context, AnalyticsContract.EVENT_TYPE_PUSH_OPENED, props);
    }

    /**
     * Registers the push token with Kumulos to allow sending push notifications to this install
     * @param context
     * @param token
     */
    public static void pushTokenStore(Context context, final String token) {

        JSONObject props = new JSONObject();

        try {
            props.put("token", token);
            props.put("type", PushTokenType.ANDROID.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        trackEvent(context, AnalyticsContract.EVENT_TYPE_PUSH_DEVICE_REGISTERED, props, System.currentTimeMillis(), true);
    }

    //==============================================================================================
    //-- Internal Helpers

    /**
     * Generates the correct Authorization header value for HTTP Basic auth with the API key & secret
     * @return Authorization header value
     */
    private static String buildBasicAuthHeader(String apiKey, String secretKey) {
        return "Basic "
                + Base64.encodeToString((apiKey + ":" + secretKey).getBytes(), Base64.NO_WRAP);
    }

    /**
     * Returns the method URLs
     *
     * @param methodAlias
     * @return
     */
    private static String getMethodUrl(String methodAlias) {
        return Kumulos.API_BASE_URL + currentConfig.getApiKey() + "/" + methodAlias + ".json";
    }

    /**
     * Prepares the parameter map so that the method params become a nested params array.
     *
     * @param params
     * @return
     */
    private static RequestBody getRpcBody(@Nullable Map<String, String> params) {
        FormBody.Builder body = new FormBody.Builder()
                .add("deviceType", "6")
                .add("installId", installId)
                .add("sessionToken", getSessionToken());

        if (null == params) {
            return body.build();
        }

        for (String key : params.keySet()) {
            body.add("params[" + key + "]", params.get(key));
        }

        return body.build();
    }

    /**
     * Logging
     *
     * @param message
     */
    protected static void log(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message);
        }
    }

    protected static void log(String message) {
        log(TAG, message);
    }

    /**
     * Uses the JSON parser library to get the response Map
     *
     * @param json
     * @return
     */
    @Nullable
    @SuppressWarnings("unchecked")
    /** package */ static Map<String, Object> getResponseObjectFromJson(String json) {
        Map<String, Object> object = null;
        try {
            object = ObjectMapperHolder.jsonMapper.readValue(json, Map.class);
        } catch (JsonParseException e) {
            log("Error parsing JSON");
            e.printStackTrace();
        } catch (JsonMappingException e) {
            log("Error mapping JSON to Java types");
            e.printStackTrace();
        } catch (IOException e) {
            log("IO Exception");
            e.printStackTrace();
        }

        return object;
    }

    /** package */ static OkHttpClient getHttpClient() throws UninitializedException {
        if (!initialized) {
            throw new UninitializedException();
        }

        return httpClient;
    }

    /** package */ static String getInstallId() throws UninitializedException {
        if (!initialized) {
            throw new UninitializedException();
        }
        return installId;
    }

    /** package */ static boolean isInitialized() {
        return initialized;
    }

    static InAppDeepLinkHandler inAppDeepLinkHandler = new InAppDeepLinkHandler();
    public static void setInAppDeepLinkHandler(InAppDeepLinkHandler handler){
        inAppDeepLinkHandler = handler;
    }
}
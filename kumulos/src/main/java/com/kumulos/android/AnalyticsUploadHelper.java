package com.kumulos.android;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class AnalyticsUploadHelper {

    enum Result {
        SUCCESS,
        FAILED_RETRY_LATER
    };

    /** package */ Result flushEvents(Context context) {
        try (SQLiteOpenHelper dbHelper = new AnalyticsDbHelper(context)) {
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            Pair<ArrayList<JSONObject>, Long> eventsResult = this.getBatchOfEvents(db, 0L);
            ArrayList<JSONObject> events = eventsResult.first;
            long maxEventId = eventsResult.second;

            while (!events.isEmpty()) {
                if (!this.flushBatchToNetwork(context, events, maxEventId)) {
                    return Result.FAILED_RETRY_LATER;
                }

                eventsResult = this.getBatchOfEvents(db, maxEventId);
                events = eventsResult.first;
                maxEventId = eventsResult.second;
            }
        }
        catch (SQLiteException e) {
            e.printStackTrace();
            return Result.FAILED_RETRY_LATER;
        }

        return Result.SUCCESS;
    }

    private boolean flushBatchToNetwork(Context context, ArrayList<JSONObject> events, long maxEventId) {
        // Pack into JSON
        JSONArray data = new JSONArray(events);
        String dataStr = data.toString();

        // Post to server
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), dataStr);

        final OkHttpClient httpClient = new OkHttpClient();
        final String url = Kumulos.EVENTS_BASE_URL + "/v1/app-installs/" + Installation.id(context) + "/events";

        Request request = new Request.Builder()
                .url(url)
                .addHeader(Kumulos.KEY_AUTH_HEADER, Kumulos.authHeader)
                .post(body)
                .build();

        boolean result = false;
        try {
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                result = true;
            }

            response.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Clean up batch from DB
        if (result) {
            Runnable trimTask = new AnalyticsContract.TrimEventsRunnable(context, maxEventId);
            Kumulos.executorService.submit(trimTask);
        }

        return result;
    }

    private Pair<ArrayList<JSONObject>, Long> getBatchOfEvents(SQLiteDatabase db, long minEventId) {
        String[] projection = {
                AnalyticsContract.AnalyticsEvent.COL_ID,
                AnalyticsContract.AnalyticsEvent.COL_HAPPENED_AT_MILLIS,
                AnalyticsContract.AnalyticsEvent.COL_UUID,
                AnalyticsContract.AnalyticsEvent.COL_EVENT_TYPE,
                AnalyticsContract.AnalyticsEvent.COL_PROPERTIES,
                AnalyticsContract.AnalyticsEvent.COL_USER_IDENTIFIER
        };

        String sortBy = AnalyticsContract.AnalyticsEvent.COL_ID + " ASC";

        String selection = AnalyticsContract.AnalyticsEvent.COL_ID + " > ?";
        String[] params = new String[] {String.valueOf(minEventId)};

        Cursor cursor = db.query(
                AnalyticsContract.AnalyticsEvent.TABLE_NAME,
                projection,
                selection,
                params,
                null,
                null,
                sortBy,
                String.valueOf(100)
        );

        ArrayList<JSONObject> events = new ArrayList<>();
        long maxEventId = -1L;

        while (cursor.moveToNext()) {
            JSONObject event = new JSONObject();

            try {
                event.put("type", cursor.getString(cursor.getColumnIndex(AnalyticsContract.AnalyticsEvent.COL_EVENT_TYPE)));
                event.put("uuid", cursor.getString(cursor.getColumnIndex(AnalyticsContract.AnalyticsEvent.COL_UUID)));
                event.put("timestamp", cursor.getLong(cursor.getColumnIndex(AnalyticsContract.AnalyticsEvent.COL_HAPPENED_AT_MILLIS)));

                int propsIdx = cursor.getColumnIndex(AnalyticsContract.AnalyticsEvent.COL_PROPERTIES);
                if (!cursor.isNull(propsIdx)) {
                    String eventPropsStr = cursor.getString(propsIdx);
                    event.put("data", new JSONObject(eventPropsStr));
                }

                String userId = null;
                int userIdIdx = cursor.getColumnIndex(
                        AnalyticsContract.AnalyticsEvent.COL_USER_IDENTIFIER);

                if (!cursor.isNull(userIdIdx)) {
                    userId = cursor.getString(userIdIdx);
                }

                event.put("userId", userId);

                events.add(event);

                maxEventId = cursor.getLong(cursor.getColumnIndex(AnalyticsContract.AnalyticsEvent.COL_ID));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        cursor.close();

        return new Pair<>(events, maxEventId);
    }

}

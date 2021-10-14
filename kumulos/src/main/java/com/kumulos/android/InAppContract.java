package com.kumulos.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;

import androidx.annotation.Nullable;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE;

class InAppContract {

    private InAppContract() {
    }

    static class InAppMessageTable {
        static final String TABLE_NAME = "in_app_messages";
        static final String COL_ID = "inAppId";
        static final String COL_DISMISSED_AT = "dismissedAt";
        static final String COL_UPDATED_AT = "updatedAt";
        static final String COL_PRESENTED_WHEN = "presentedWhen";
        static final String COL_INBOX_FROM = "inboxFrom";
        static final String COL_INBOX_TO = "inboxTo";
        static final String COL_INBOX_CONFIG_JSON = "inboxConfigJson";
        static final String COL_BADGE_CONFIG_JSON = "badgeConfigJson";
        static final String COL_DATA_JSON = "dataJson";
        static final String COL_CONTENT_JSON = "contentJson";
        static final String COL_EXPIRES_AT = "expiresAt";
        static final String COL_READ_AT = "readAt";
        static final String COL_SENT_AT = "sentAt";
    }

    private static final SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private static final SimpleDateFormat incomingDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
    private static final String DESC_SORT_ORDER = InAppMessageTable.COL_SENT_AT + " DESC, " + InAppMessageTable.COL_UPDATED_AT + " DESC, " + InAppMessageTable.COL_ID + " DESC";
    private static final String ASC_SORT_ORDER = InAppMessageTable.COL_SENT_AT + " ASC, " + InAppMessageTable.COL_UPDATED_AT + " ASC, " + InAppMessageTable.COL_ID + " ASC";
    private static final Integer STORED_IN_APP_LIMIT = 50;

    static {
        dbDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    static @Nullable
    Date getNullableDate(Cursor cursor, String column) throws ParseException {
        String date = cursor.getString(cursor.getColumnIndexOrThrow(column));

        return date == null ? null : dbDateFormat.parse(date);
    }

    static @Nullable
    JSONObject getNullableJsonObject(Cursor cursor, String column) throws JSONException {
        String rawJson = cursor.getString(cursor.getColumnIndexOrThrow(column));

        return rawJson == null ? null : new JSONObject(rawJson);
    }

    static class ClearDbRunnable implements Runnable {
        private static final String TAG = InAppContract.ClearDbRunnable.class.getName();
        private Context mContext;

        ClearDbRunnable(Context context) {
            mContext = context.getApplicationContext();
        }

        @Override
        public void run() {
            try (SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext)) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                db.execSQL("delete from " + InAppMessageTable.TABLE_NAME);
            } catch (SQLiteException e) {
                Kumulos.log(TAG, "Failed clearing in-app db ");
                e.printStackTrace();
            }
        }
    }

    static class TrackMessageDismissedRunnable implements Runnable {
        private static final String TAG = TrackMessageDismissedRunnable.class.getName();

        private final Context mContext;
        private final InAppMessage mInAppMessage;

        TrackMessageDismissedRunnable(Context context, InAppMessage message) {
            mContext = context.getApplicationContext();
            mInAppMessage = message;
        }

        @Override
        public void run() {
            try (SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext)) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                String datetime = dbDateFormat.format(mInAppMessage.getDismissedAt());
                String sql = "UPDATE " + InAppMessageTable.TABLE_NAME
                        + " SET " + InAppMessageTable.COL_DISMISSED_AT + " = ?, " + InAppMessageTable.COL_READ_AT + " = IFNULL(readAt, ?)"
                        + " WHERE " + InAppMessageTable.COL_ID + " = ?;";

                Cursor c = db.rawQuery(sql, new String[]{datetime, datetime, mInAppMessage.getInAppId() + ""});
                c.moveToFirst();
                c.close();
            } catch (SQLiteException e) {
                Kumulos.log(TAG, "Failed to track open for inAppID: " + mInAppMessage.getInAppId());
                e.printStackTrace();
            }
        }
    }

    static class SaveInAppMessagesCallable implements Callable<InAppSaveResult> {

        private static final String TAG = SaveInAppMessagesCallable.class.getName();

        private Context mContext;
        private List<InAppMessage> mInAppMessages;

        SaveInAppMessagesCallable(Context context, List<InAppMessage> inAppMessages) {
            mContext = context.getApplicationContext();
            mInAppMessages = inAppMessages;
        }

        @Override
        public InAppSaveResult call() {
            List<InAppMessage> itemsToPresent = new ArrayList<>();
            List<Integer> deliveredIds = new ArrayList<>();
            List<Integer> deletedIds = new ArrayList<>();
            boolean inboxUpdated = false;

            try (SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext)) {
                List<ContentValues> rows = this.assembleRows();

                SQLiteDatabase db = dbHelper.getWritableDatabase();

                deliveredIds = this.insertRows(db, rows);
                Pair<Boolean, List<Integer>> deleteResult = this.deleteRows(db);
                deletedIds = deleteResult.second;
                itemsToPresent = this.readRows(db);
                inboxUpdated = this.isInboxUpdated(mInAppMessages, deleteResult.first);

                Kumulos.log(TAG, "Saved messages: " + mInAppMessages.size());
            } catch (SQLiteException e) {
                Kumulos.log(TAG, "Failed to save messages: " + mInAppMessages.size());
                e.printStackTrace();
            } catch (Exception e) {
                Kumulos.log(TAG, e.getMessage());
            }

            return new InAppSaveResult(itemsToPresent, deliveredIds, deletedIds, inboxUpdated);
        }

        private boolean isInboxUpdated(List<InAppMessage> mInAppMessages, boolean evictedInbox) {
            boolean syncUpdatedInbox = false;
            for (InAppMessage message : mInAppMessages) {
                //crude way to refresh when new inbox, updated readAt, updated inbox title/subtite
                //may cause redundant refreshes.
                if (message.getInbox() != null) {
                    syncUpdatedInbox = true;
                    break;
                }
            }

            return syncUpdatedInbox || evictedInbox;
        }

        private List<Integer> insertRows(SQLiteDatabase db, List<ContentValues> rows) {
            List<Integer> deliveredIds = new ArrayList<>();

            for (ContentValues row : rows) {
                int id = (int) db.insertWithOnConflict(InAppMessageTable.TABLE_NAME, null, row, CONFLICT_IGNORE);
                if (id == -1) {
                    db.update(InAppMessageTable.TABLE_NAME, row, InAppMessageTable.COL_ID + "=?", new String[]{"" + row.getAsInteger(InAppMessageTable.COL_ID)});
                }
                //tracks all messages, which were received and saved/updated
                deliveredIds.add(row.getAsInteger(InAppMessageTable.COL_ID));
            }

            return deliveredIds;
        }

        private Pair<Boolean, List<Integer>> deleteRows(SQLiteDatabase db) {
            String messageExpiredCondition = String.format("(%s IS NOT NULL AND (DATETIME(%s) <= DATETIME('now'))",
                    InAppMessageTable.COL_EXPIRES_AT,
                    InAppMessageTable.COL_EXPIRES_AT);

            String noInboxAndMessageDismissed = String.format("(%s IS NULL AND %s IS NOT NULL)", InAppMessageTable.COL_INBOX_CONFIG_JSON, InAppMessageTable.COL_DISMISSED_AT);
            String noInboxAndMessageExpired = String.format("(%s IS NULL AND %s))", InAppMessageTable.COL_INBOX_CONFIG_JSON, messageExpiredCondition);
            String inboxExpiredAndMessageDismissedOrExpired = String.format("(%s IS NOT NULL AND (DATETIME('now') > IFNULL(%s, '3970-01-01')) AND (%s IS NOT NULL OR %s)))",
                    InAppMessageTable.COL_INBOX_CONFIG_JSON,
                    InAppMessageTable.COL_INBOX_TO,
                    InAppMessageTable.COL_DISMISSED_AT,
                    messageExpiredCondition);

            String notConditions = "(NOT " + noInboxAndMessageDismissed + " AND NOT " + noInboxAndMessageExpired + " AND NOT " + inboxExpiredAndMessageDismissedOrExpired + ")";

            String columnList = InAppMessageTable.COL_ID + ", " + InAppMessageTable.COL_INBOX_CONFIG_JSON;
            String inAppsExceedingLimitSql = "select * from (SELECT " + columnList +
                    " FROM " + InAppMessageTable.TABLE_NAME +
                    " WHERE " + notConditions +
                    " ORDER BY " + DESC_SORT_ORDER +
                    " LIMIT -1 OFFSET " + STORED_IN_APP_LIMIT + ")";

            String readSql = "SELECT " + columnList + " FROM " + InAppMessageTable.TABLE_NAME +
                    " WHERE " +
                    noInboxAndMessageDismissed +
                    " OR " +
                    noInboxAndMessageExpired +
                    " OR " +
                    inboxExpiredAndMessageDismissedOrExpired +
                    " UNION " +
                    inAppsExceedingLimitSql;

            Cursor c = db.rawQuery(readSql, new String[]{});
            List<Integer> deletedIds = new ArrayList<>();
            boolean evictedInbox = false;
            while (c.moveToNext()) {
                deletedIds.add(c.getInt(c.getColumnIndexOrThrow(InAppMessageTable.COL_ID)));

                if (!evictedInbox) {
                    evictedInbox = !c.isNull(c.getColumnIndexOrThrow(InAppMessageTable.COL_INBOX_CONFIG_JSON));
                }
            }
            c.close();

            if (deletedIds.size() > 0) {
                String placeholders = new String(new char[deletedIds.size() - 1]).replace("\0", "?,") + "?";
                String deleteSql = "DELETE FROM " + InAppMessageTable.TABLE_NAME + " WHERE " + InAppMessageTable.COL_ID + " IN (" + placeholders + ")";
                db.execSQL(deleteSql, deletedIds.toArray(new Integer[0]));
            }

            return new Pair<>(evictedInbox, deletedIds);
        }

        private List<InAppMessage> readRows(SQLiteDatabase db) throws ParseException {

            List<InAppMessage> itemsToPresent = new ArrayList<>();

            String[] projection = {
                    InAppMessageTable.COL_ID,
                    InAppMessageTable.COL_PRESENTED_WHEN,
                    InAppMessageTable.COL_CONTENT_JSON,
                    InAppMessageTable.COL_DATA_JSON,
                    InAppMessageTable.COL_READ_AT,
                    InAppMessageTable.COL_INBOX_CONFIG_JSON
            };
            String selection = InAppMessageTable.COL_DISMISSED_AT + " IS NULL";

            Cursor cursor = db.query(InAppMessageTable.TABLE_NAME, projection, selection, null, null, null, ASC_SORT_ORDER);
            while (cursor.moveToNext()) {
                int inAppId = cursor.getInt(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_ID));
                String presentedWhen = cursor.getString(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_PRESENTED_WHEN));
                Date readAt = getNullableDate(cursor, InAppMessageTable.COL_READ_AT);
                JSONObject content = null;
                JSONObject inbox = null;
                JSONObject data = null;

                try {
                    content = getNullableJsonObject(cursor, InAppMessageTable.COL_CONTENT_JSON);
                    data = getNullableJsonObject(cursor, InAppMessageTable.COL_DATA_JSON);
                    inbox = getNullableJsonObject(cursor, InAppMessageTable.COL_INBOX_CONFIG_JSON);
                } catch (JSONException e) {
                    Kumulos.log(TAG, e.getMessage());
                    continue;
                }

                InAppMessage m = new InAppMessage(inAppId, presentedWhen, content, data, inbox, readAt);
                itemsToPresent.add(m);
            }

            cursor.close();

            return itemsToPresent;
        }

        private List<ContentValues> assembleRows() throws ParseException {
            List<ContentValues> rows = new ArrayList<>();

            for (InAppMessage message : mInAppMessages) {
                ContentValues values = new ContentValues();

                values.put(InAppMessageTable.COL_ID, message.getInAppId());

                Date inboxDeletedAt = message.getInboxDeletedAt();
                Date messageDismissedAt = message.getDismissedAt();
                if (messageDismissedAt != null || inboxDeletedAt != null) {
                    Date dismissedTime = messageDismissedAt != null ? messageDismissedAt : inboxDeletedAt;
                    values.put(InAppMessageTable.COL_DISMISSED_AT, dbDateFormat.format(dismissedTime));
                }

                Date expiresAt = message.getExpiresAt();
                if (expiresAt != null) {
                    values.put(InAppMessageTable.COL_EXPIRES_AT, dbDateFormat.format(expiresAt));
                }
                values.put(InAppMessageTable.COL_UPDATED_AT, dbDateFormat.format(message.getUpdatedAt()));
                values.put(InAppMessageTable.COL_PRESENTED_WHEN, message.getPresentedWhen());

                Date readAt = message.getReadAt();
                if (readAt != null) {
                    values.put(InAppMessageTable.COL_READ_AT, dbDateFormat.format(readAt));
                }

                Date sentAt = message.getSentAt();
                if (sentAt != null) {
                    values.put(InAppMessageTable.COL_SENT_AT, dbDateFormat.format(sentAt));
                }

                String inboxFrom = null;
                String inboxTo = null;
                JSONObject inbox = null;
                if (inboxDeletedAt == null) {
                    inbox = message.getInbox();
                }

                if (inbox != null) {
                    inboxFrom = this.getNullableString(inbox, "from");
                    if (inboxFrom != null) {
                        inboxFrom = this.formatDateForDb(inboxFrom);
                    }
                    inboxTo = this.getNullableString(inbox, "to");
                    if (inboxTo != null) {
                        inboxTo = this.formatDateForDb(inboxTo);
                    }
                }

                values.put(InAppMessageTable.COL_INBOX_CONFIG_JSON, inbox != null ? inbox.toString() : null);
                values.put(InAppMessageTable.COL_INBOX_FROM, inboxFrom);
                values.put(InAppMessageTable.COL_INBOX_TO, inboxTo);

                JSONObject badge = message.getBadgeConfig();
                JSONObject data = message.getData();

                values.put(InAppMessageTable.COL_BADGE_CONFIG_JSON, badge != null ? badge.toString() : null);
                values.put(InAppMessageTable.COL_DATA_JSON, data != null ? data.toString() : null);
                values.put(InAppMessageTable.COL_CONTENT_JSON, message.getContent().toString());
                rows.add(values);
            }

            return rows;
        }

        private String formatDateForDb(String date) throws ParseException {
            return dbDateFormat.format(incomingDateFormat.parse(date));
        }

        private String getNullableString(JSONObject json, String key) {
            if (!json.has(key) || json.isNull(key)) {
                return null;
            }

            return json.optString(key);
        }
    }

    static class ReadInAppInboxCallable implements Callable<List<InAppInboxItem>> {

        private static final String TAG = ReadInAppInboxCallable.class.getName();

        private final Context mContext;

        ReadInAppInboxCallable(Context context) {
            mContext = context.getApplicationContext();
        }

        @Override
        public List<InAppInboxItem> call() {
            List<InAppInboxItem> inboxItems = new ArrayList<>();
            try (SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext)) {
                SQLiteDatabase db = dbHelper.getReadableDatabase();

                String columnList = InAppMessageTable.COL_ID + ", "
                        + InAppMessageTable.COL_DISMISSED_AT + ", "
                        + InAppMessageTable.COL_READ_AT + ", "
                        + InAppMessageTable.COL_SENT_AT + ", "
                        + InAppMessageTable.COL_DATA_JSON + ", "
                        + InAppMessageTable.COL_INBOX_FROM + ", "
                        + InAppMessageTable.COL_INBOX_TO + ", "
                        + InAppMessageTable.COL_INBOX_CONFIG_JSON;

                String selectSql = "SELECT " + columnList + " FROM " + InAppMessageTable.TABLE_NAME +
                        " WHERE " + InAppMessageTable.COL_INBOX_CONFIG_JSON + " IS NOT NULL " +
                        " AND (datetime('now') BETWEEN IFNULL(" + InAppMessageTable.COL_INBOX_FROM + ", '1970-01-01') AND IFNULL(" + InAppMessageTable.COL_INBOX_TO + ", '3970-01-01'))" +
                        " ORDER BY " + DESC_SORT_ORDER;

                Cursor cursor = db.rawQuery(selectSql, new String[]{});
                while (cursor.moveToNext()) {
                    int inAppId = cursor.getInt(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_ID));
                    JSONObject inboxConfig = getNullableJsonObject(cursor, InAppMessageTable.COL_INBOX_CONFIG_JSON);
                    JSONObject data = getNullableJsonObject(cursor, InAppMessageTable.COL_DATA_JSON);
                    Date availableFrom = getNullableDate(cursor, InAppMessageTable.COL_INBOX_FROM);
                    Date availableTo = getNullableDate(cursor, InAppMessageTable.COL_INBOX_TO);
                    Date dismissedAt = getNullableDate(cursor, InAppMessageTable.COL_DISMISSED_AT);
                    Date readAt = getNullableDate(cursor, InAppMessageTable.COL_READ_AT);
                    Date sentAt = getNullableDate(cursor, InAppMessageTable.COL_SENT_AT);

                    InAppInboxItem i = new InAppInboxItem();
                    i.setId(inAppId);
                    i.setDismissedAt(dismissedAt);
                    i.setReadAt(readAt);
                    i.setAvailableTo(availableTo);
                    i.setAvailableFrom(availableFrom);
                    i.setSentAt(sentAt);
                    i.setData(data);

                    if (inboxConfig != null) {
                        i.setTitle(inboxConfig.getString("title"));
                        i.setSubtitle(inboxConfig.getString("subtitle"));
                        if (!inboxConfig.isNull("imagePath")) {
                            i.setImagePath(inboxConfig.getString("imagePath"));
                        }
                    }

                    inboxItems.add(i);
                }
                cursor.close();
            } catch (SQLiteException e) {
                e.printStackTrace();
            } catch (Exception e) {
                Kumulos.log(TAG, e.getMessage());
            }

            return inboxItems;
        }
    }

    static class ReadInAppInboxMessageCallable implements Callable<InAppMessage> {
        private static final String TAG = ReadInAppInboxMessageCallable.class.getName();

        private final Context mContext;
        private final int mId;

        ReadInAppInboxMessageCallable(Context context, int id) {
            mContext = context.getApplicationContext();
            mId = id;
        }

        @Override
        public InAppMessage call() {
            InAppMessage inboxMessage = null;
            try (SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext)) {
                SQLiteDatabase db = dbHelper.getReadableDatabase();

                String[] projection = {
                        InAppMessageTable.COL_ID,
                        InAppMessageTable.COL_CONTENT_JSON,
                        InAppMessageTable.COL_DATA_JSON,
                        InAppMessageTable.COL_READ_AT,
                        InAppMessageTable.COL_INBOX_CONFIG_JSON
                };
                String selection = InAppMessageTable.COL_INBOX_CONFIG_JSON + " IS NOT NULL AND " + InAppMessageTable.COL_ID + " = ?";
                String[] selectionArgs = {mId + ""};

                Cursor cursor = db.query(InAppMessageTable.TABLE_NAME, projection, selection, selectionArgs, null, null, null);

                if (cursor.moveToFirst()) {
                    inboxMessage = new InAppMessage(mId,
                            getNullableJsonObject(cursor, InAppMessageTable.COL_CONTENT_JSON),
                            getNullableJsonObject(cursor, InAppMessageTable.COL_DATA_JSON),
                            getNullableJsonObject(cursor, InAppMessageTable.COL_INBOX_CONFIG_JSON),
                            getNullableDate(cursor, InAppMessageTable.COL_READ_AT));
                }

                cursor.close();
            } catch (SQLiteException e) {
                e.printStackTrace();
            } catch (Exception e) {
                Kumulos.log(TAG, e.getMessage());
            }

            return inboxMessage;
        }
    }

    static class DeleteInAppInboxMessageCallable implements Callable<Boolean> {

        private static final String TAG = DeleteInAppInboxMessageCallable.class.getName();

        private final Context mContext;
        private final int mId;

        DeleteInAppInboxMessageCallable(Context context, int id) {
            mContext = context.getApplicationContext();
            mId = id;
        }

        @Override
        public Boolean call() {
            try (SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext)) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                ContentValues values = new ContentValues();
                values.putNull(InAppMessageTable.COL_INBOX_FROM);
                values.putNull(InAppMessageTable.COL_INBOX_TO);
                values.putNull(InAppMessageTable.COL_INBOX_CONFIG_JSON);
                values.put(InAppMessageTable.COL_DISMISSED_AT, dbDateFormat.format(new Date()));
                values.put(InAppMessageTable.COL_READ_AT, dbDateFormat.format(new Date()));

                String selection = InAppMessageTable.COL_ID + " = ?";
                String[] selectionArgs = {mId + ""};

                int count = db.update(InAppMessageTable.TABLE_NAME, values, selection, selectionArgs);

                if (count == 0) {
                    return false;
                }
            } catch (SQLiteException e) {
                Kumulos.log(TAG, "Failed to delete inbox message with inAppID: " + mId);
                e.printStackTrace();
                return false;
            }

            return true;
        }
    }

    static class MarkInAppInboxMessageAsReadCallable implements Callable<Boolean> {

        private static final String TAG = MarkInAppInboxMessageAsReadCallable.class.getName();

        private final Context mContext;
        private final int mId;

        MarkInAppInboxMessageAsReadCallable(Context context, int id) {
            mContext = context.getApplicationContext();
            mId = id;
        }

        @Override
        public Boolean call() {
            try (SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext)) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                ContentValues values = new ContentValues();
                values.put(InAppMessageTable.COL_READ_AT, dbDateFormat.format(new Date()));

                String selection = InAppMessageTable.COL_ID + " = ? AND " + InAppMessageTable.COL_READ_AT + " IS NULL";
                String[] selectionArgs = {mId + ""};

                int count = db.update(InAppMessageTable.TABLE_NAME, values, selection, selectionArgs);

                if (count == 0) {
                    return false;
                }
            } catch (SQLiteException e) {
                Kumulos.log(TAG, "Failed to set readAt of inbox message with inAppID: " + mId);
                e.printStackTrace();
                return false;
            }

            return true;
        }
    }

    static class ReadInboxSummaryRunnable implements Runnable {

        private static final String TAG = ReadInboxSummaryRunnable.class.getName();
        private final Context mContext;
        private final KumulosInApp.InAppInboxSummaryHandler callback;

        ReadInboxSummaryRunnable(Context context, KumulosInApp.InAppInboxSummaryHandler callback) {
            mContext = context.getApplicationContext();
            this.callback = callback;
        }

        @Override
        public void run() {
            InAppInboxSummary summary = null;
            try (SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext)) {
                SQLiteDatabase db = dbHelper.getReadableDatabase();

                String selectSql = "SELECT COUNT(*) as totalCount, SUM(isUnread) as unreadCount FROM " +
                        "(SELECT CASE WHEN " + InAppMessageTable.COL_READ_AT + " IS NULL THEN 1 ELSE 0 END AS isUnread " +
                        " FROM " + InAppMessageTable.TABLE_NAME +
                        " WHERE " + InAppMessageTable.COL_INBOX_CONFIG_JSON + " IS NOT NULL " +
                        " AND (datetime('now') BETWEEN IFNULL(" + InAppMessageTable.COL_INBOX_FROM + ", '1970-01-01') AND IFNULL(" + InAppMessageTable.COL_INBOX_TO + ", '3970-01-01'))" +
                        ") as sub";

                Cursor cursor = db.rawQuery(selectSql, new String[]{});
                cursor.moveToNext();
                int totalCount = cursor.getInt(cursor.getColumnIndexOrThrow("totalCount"));
                int unreadCount = cursor.getInt(cursor.getColumnIndexOrThrow("unreadCount"));
                cursor.close();

                summary = new InAppInboxSummary(totalCount, unreadCount);
            } catch (SQLiteException e) {
                Kumulos.log(TAG, "Failed to read inbox summary");
                e.printStackTrace();
            }

            this.fireCallback(summary);
        }

        private void fireCallback(InAppInboxSummary summary) {
            Kumulos.handler.post(() -> ReadInboxSummaryRunnable.this.callback.run(summary));
        }
    }
}
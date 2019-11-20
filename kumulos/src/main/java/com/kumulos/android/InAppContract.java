package com.kumulos.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.text.ParseException;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE;

import android.util.Pair;

class InAppContract {

    private InAppContract() {}

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
        static final String COL_EXPIRES_AT = "expires";
    }

    private static SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static SimpleDateFormat incomingDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault());

    static{
        dbDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    static class ClearDbRunnable implements Runnable {
        private static final String TAG = InAppContract.ClearDbRunnable.class.getName();
        private Context mContext;

        ClearDbRunnable(Context context) {
            mContext = context.getApplicationContext();
        }

        @Override
        public void run() {
            SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext);
            try {
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                db.execSQL("delete from "+ InAppMessageTable.TABLE_NAME);

                dbHelper.close();
            }
            catch (SQLiteException e) {
                Kumulos.log(TAG, "Failed clearing in-app db ");
                e.printStackTrace();
            }
        }
    }

    static class TrackMessageDismissedRunnable implements Runnable {
        private static final String TAG = TrackMessageDismissedRunnable.class.getName();

        private Context mContext;
        private InAppMessage mInAppMessage;

        TrackMessageDismissedRunnable(Context context, InAppMessage message) {
            mContext = context.getApplicationContext();
            mInAppMessage = message;
        }

        @Override
        public void run() {
            SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext);

            try {
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                ContentValues values = new ContentValues();
                values.put(InAppMessageTable.COL_DISMISSED_AT, dbDateFormat.format(mInAppMessage.getDismissedAt()));

                String selection = InAppMessageTable.COL_ID + " = ?";
                String[] selectionArgs = { mInAppMessage.getInAppId()+"" };

                int count = db.update(InAppMessageTable.TABLE_NAME, values, selection, selectionArgs);

                dbHelper.close();
            }
            catch (SQLiteException e) {
                Kumulos.log(TAG, "Failed to track open for inAppID: "+ mInAppMessage.getInAppId());
                e.printStackTrace();
            }
        }
    }

    static class ReadInAppMessagesCallable implements Callable<List<InAppMessage>> {

        private static final String TAG = ReadInAppMessagesCallable.class.getName();

        private Context mContext;

        ReadInAppMessagesCallable(Context context) {
            mContext = context.getApplicationContext();
        }

        @Override
        public List<InAppMessage> call() {
            SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext);

            List<InAppMessage> itemsToPresent = new ArrayList<>();
            try {
                SQLiteDatabase db = dbHelper.getReadableDatabase();

                String[] projection = {InAppMessageTable.COL_ID, InAppMessageTable.COL_PRESENTED_WHEN, InAppMessageTable.COL_CONTENT_JSON};
                String selection = String.format("%s IS NULL AND (%s IS NULL OR (DATETIME(%s) > DATETIME('now')))",
                        InAppMessageTable.COL_DISMISSED_AT,
                        InAppMessageTable.COL_EXPIRES_AT,
                        InAppMessageTable.COL_EXPIRES_AT);

                String sortOrder = InAppMessageTable.COL_UPDATED_AT + " ASC";

                Cursor cursor = db.query(InAppMessageTable.TABLE_NAME, projection, selection, null,null,null, sortOrder);

                while(cursor.moveToNext()) {
                    int inAppId = cursor.getInt(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_ID));
                    String content = cursor.getString(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_CONTENT_JSON));
                    String presentedWhen = cursor.getString(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_PRESENTED_WHEN));
                    InAppMessage m = new InAppMessage();
                    m.setInAppId(inAppId);
                    m.setContent(new JSONObject(content));
                    m.setPresentedWhen(presentedWhen);
                    itemsToPresent.add(m);
                }
                cursor.close();

                dbHelper.close();
            }
            catch (SQLiteException e) {
                e.printStackTrace();
            }
            catch(Exception e){
                Kumulos.log(TAG, e.getMessage());
            }

            return itemsToPresent;
        }
    }

    static class SaveInAppMessagesCallable implements Callable<Pair<List<InAppMessage>, List<Integer>>> {

        private static final String TAG = SaveInAppMessagesCallable.class.getName();

        private Context mContext;
        private List<InAppMessage> mInAppMessages;

        SaveInAppMessagesCallable(Context context, List<InAppMessage> inAppMessages) {
            mContext = context.getApplicationContext();
            mInAppMessages = inAppMessages;
        }

        @Override
        public Pair<List<InAppMessage>, List<Integer>> call() {
            SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext);

            List<InAppMessage> itemsToPresent = new ArrayList<>();
            List<Integer> deliveredIds = new ArrayList<>();
            try {
                List<ContentValues> rows = this.assembleRows();

                SQLiteDatabase db = dbHelper.getWritableDatabase();

                deliveredIds = this.insertRows(db, rows);
                this.deleteRows(db);
                itemsToPresent = this.readRows(db);

                dbHelper.close();
                Kumulos.log(TAG, "Saved messages: "+mInAppMessages.size());
            }
            catch (SQLiteException e) {
                Kumulos.log(TAG, "Failed to save messages: "+mInAppMessages.size());
                e.printStackTrace();
            }
            catch(Exception e){
                Kumulos.log(TAG, e.getMessage());
            }

            return new Pair<>(itemsToPresent, deliveredIds);
        }

        private List<Integer> insertRows(SQLiteDatabase db, List<ContentValues> rows){
            List<Integer> deliveredIds = new ArrayList<>();

            for(ContentValues row : rows){
                int id = (int) db.insertWithOnConflict(InAppMessageTable.TABLE_NAME, null, row, CONFLICT_IGNORE);
                if (id == -1) {
                    db.update(InAppMessageTable.TABLE_NAME, row, InAppMessageTable.COL_ID+"=?", new String[] {""+row.getAsInteger(InAppMessageTable.COL_ID)});
                }
                //tracks all messages, which were received and saved/updated
                deliveredIds.add(row.getAsInteger(InAppMessageTable.COL_ID));
            }

            return deliveredIds;
        }

        private void deleteRows(SQLiteDatabase db){
            String messageExpiredCondition = String.format("(%s IS NOT NULL AND (DATETIME(%s) <= DATETIME('now'))",
                    InAppMessageTable.COL_EXPIRES_AT,
                    InAppMessageTable.COL_EXPIRES_AT);

            String noInboxAndMessageDismissed = String.format("(%s IS NULL AND %s IS NOT NULL)", InAppMessageTable.COL_INBOX_CONFIG_JSON,  InAppMessageTable.COL_DISMISSED_AT);
            String noInboxAndMessageExpired = String.format("(%s IS NULL AND %s))", InAppMessageTable.COL_INBOX_CONFIG_JSON, messageExpiredCondition);
            String inboxExpiredAndMessageDismissedOrExpired = String.format("(%s IS NOT NULL AND (DATETIME('now') > IFNULL(%s, '3970-01-01')) AND (%s IS NOT NULL OR %s)))",
                    InAppMessageTable.COL_INBOX_CONFIG_JSON,
                    InAppMessageTable.COL_INBOX_TO,
                    InAppMessageTable.COL_DISMISSED_AT,
                    messageExpiredCondition);


            String deleteSql ="DELETE FROM " + InAppMessageTable.TABLE_NAME +
                    " WHERE "+
                    noInboxAndMessageDismissed +
                    " OR " +
                    noInboxAndMessageExpired +
                    " OR " +
                    inboxExpiredAndMessageDismissedOrExpired;

            db.execSQL(deleteSql);
        }

        private List<InAppMessage> readRows(SQLiteDatabase db) {

            List<InAppMessage> itemsToPresent = new ArrayList<>();

            String[] projection = {InAppMessageTable.COL_ID, InAppMessageTable.COL_PRESENTED_WHEN, InAppMessageTable.COL_CONTENT_JSON};
            String selection = InAppMessageTable.COL_DISMISSED_AT+ " IS NULL";
            String sortOrder = InAppMessageTable.COL_UPDATED_AT + " ASC";

            Cursor cursor = db.query(InAppMessageTable.TABLE_NAME, projection, selection, null,null,null, sortOrder);

            while(cursor.moveToNext()) {
                int inAppId = cursor.getInt(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_ID));
                String content = cursor.getString(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_CONTENT_JSON));
                String presentedWhen = cursor.getString(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_PRESENTED_WHEN));
                InAppMessage m = new InAppMessage();
                m.setInAppId(inAppId);
                m.setPresentedWhen(presentedWhen);

                try{
                    m.setContent(new JSONObject(content));
                }
                catch(JSONException e) {
                    Kumulos.log(TAG, e.getMessage());
                    continue;
                }

                itemsToPresent.add(m);
            }

            cursor.close();

            return itemsToPresent;
        }


        private List<ContentValues> assembleRows() throws ParseException{
            List<ContentValues> rows = new ArrayList<>();

            for (InAppMessage message: mInAppMessages ){
                ContentValues values = new ContentValues();

                values.put(InAppMessageTable.COL_ID, message.getInAppId());

                if (message.getDismissedAt() != null){
                    values.put(InAppMessageTable.COL_DISMISSED_AT, dbDateFormat.format(message.getDismissedAt()));
                }

                values.put(InAppMessageTable.COL_EXPIRES_AT, dbDateFormat.format(message.getExpiresAt()));
                values.put(InAppMessageTable.COL_UPDATED_AT, dbDateFormat.format(message.getUpdatedAt()));
                values.put(InAppMessageTable.COL_PRESENTED_WHEN, message.getPresentedWhen());

                String inboxFrom = null;
                String inboxTo = null;
                JSONObject inbox = message.getInbox();

                if (inbox != null){
                    inboxFrom = this.getNullableString(inbox, "from");
                    if (inboxFrom != null){
                        inboxFrom = this.formatDateForDb(inboxFrom);
                    }
                    inboxTo = this.getNullableString(inbox, "to");
                    if (inboxTo != null){
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

        private String formatDateForDb(String date) throws ParseException{
            return dbDateFormat.format(incomingDateFormat.parse(date));
        }

        private String getNullableString(JSONObject json, String key){
            if (!json.has(key) || json.isNull(key)){
                return null;
            }

            return json.optString(key, null);
        }
    }

    static class ReadInAppInboxCallable implements Callable<List<InAppInboxItem>> {

        private static final String TAG = ReadInAppInboxCallable.class.getName();

        private Context mContext;

        ReadInAppInboxCallable(Context context) {
            mContext = context.getApplicationContext();
        }

        @Override
        public List<InAppInboxItem> call() {
            SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext);

            List<InAppInboxItem> inboxItems = new ArrayList<>();
            try {
                SQLiteDatabase db = dbHelper.getReadableDatabase();

                String columnList = InAppMessageTable.COL_ID + ", "
                        + InAppMessageTable.COL_DISMISSED_AT + ", "
                        + InAppMessageTable.COL_INBOX_FROM + ", "
                        + InAppMessageTable.COL_INBOX_TO + ", "
                        + InAppMessageTable.COL_INBOX_CONFIG_JSON;

                String selectSql ="SELECT "+ columnList +" FROM " + InAppMessageTable.TABLE_NAME +
                        " WHERE " +InAppMessageTable.COL_INBOX_CONFIG_JSON+" IS NOT NULL "+
                        " AND (datetime('now') BETWEEN IFNULL("+ InAppMessageTable.COL_INBOX_FROM+", '1970-01-01') AND IFNULL("+ InAppMessageTable.COL_INBOX_TO+", '3970-01-01'))" +
                        " ORDER BY "+InAppMessageTable.COL_UPDATED_AT+ " DESC";

                Cursor cursor = db.rawQuery(selectSql, new String[]{});

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

                while(cursor.moveToNext()) {
                    int inAppId = cursor.getInt(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_ID));
                    JSONObject inboxConfig = new JSONObject(cursor.getString(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_INBOX_CONFIG_JSON)));

                    Date availableFrom = this.getNullableDate(cursor, sdf, InAppMessageTable.COL_INBOX_FROM);
                    Date availableTo = this.getNullableDate(cursor, sdf, InAppMessageTable.COL_INBOX_TO);
                    Date dismissedAt = this.getNullableDate(cursor, sdf, InAppMessageTable.COL_DISMISSED_AT);

                    InAppInboxItem i = new InAppInboxItem();
                    i.setId(inAppId);
                    i.setDismissedAt(dismissedAt);
                    i.setAvailableTo(availableTo);
                    i.setAvailableFrom(availableFrom);

                    i.setTitle(inboxConfig.getString("title"));
                    i.setSubtitle(inboxConfig.getString("subtitle"));
                    inboxItems.add(i);
                }
                cursor.close();

                dbHelper.close();
            }
            catch (SQLiteException e) {
                e.printStackTrace();
            }
            catch(Exception e){
                Kumulos.log(TAG, e.getMessage());
            }

            return inboxItems;
        }

        private Date getNullableDate(Cursor cursor, SimpleDateFormat sdf, String column) throws ParseException{
            String date = cursor.getString(cursor.getColumnIndexOrThrow(column));

            return date == null ? null : sdf.parse(date);
        }
    }

    static class ReadInAppInboxMessageCallable implements Callable<InAppMessage> {
        private static final String TAG = ReadInAppInboxMessageCallable.class.getName();

        private Context mContext;
        private int mId;

        ReadInAppInboxMessageCallable(Context context, int id) {
            mContext = context.getApplicationContext();
            mId = id;
        }

        @Override
        public InAppMessage call() {
            SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext);

            InAppMessage inboxMessage = null;
            try {
                SQLiteDatabase db = dbHelper.getReadableDatabase();

                String[] projection = {InAppMessageTable.COL_ID, InAppMessageTable.COL_CONTENT_JSON};
                String selection = InAppMessageTable.COL_INBOX_CONFIG_JSON+ " IS NOT NULL AND "+ InAppMessageTable.COL_ID + " = ?";
                String[] selectionArgs = { mId+"" };

                Cursor cursor = db.query(InAppMessageTable.TABLE_NAME, projection, selection, selectionArgs,null,null, null);

                if (cursor.moveToFirst()){
                    String content = cursor.getString(cursor.getColumnIndexOrThrow(InAppMessageTable.COL_CONTENT_JSON));

                    inboxMessage = new InAppMessage();
                    inboxMessage.setInAppId(mId);
                    inboxMessage.setContent(new JSONObject(content));
                }

                cursor.close();

                dbHelper.close();
            }
            catch (SQLiteException e) {
                e.printStackTrace();
            }
            catch(Exception e){
                Kumulos.log(TAG, e.getMessage());
            }

            return inboxMessage;
        }
    }

}
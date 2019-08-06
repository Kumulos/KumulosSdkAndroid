package com.kumulos.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE;
import android.util.Pair;

class InAppContract {

    private InAppContract() {}

    static class InAppMessageTable {
        static final String TABLE_NAME = "in_app_messages";
        static final String COL_ID = "inAppId";
        static final String COL_OPENED_AT = "openedAt";
        static final String COL_UPDATED_AT = "updatedAt";
        static final String COL_PRESENTED_WHEN = "presentedWhen";
        static final String COL_INBOX_FROM = "inboxFrom";
        static final String COL_INBOX_TO = "inboxTo";
        static final String COL_INBOX_CONFIG_JSON = "inboxConfigJson";
        static final String COL_BADGE_CONFIG_JSON = "badgeConfigJson";
        static final String COL_DATA_JSON = "dataJson";
        static final String COL_CONTENT_JSON = "contentJson";
    }

    private static SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());


    static class TrackMessageOpenedRunnable implements Runnable {
        private static final String TAG = TrackMessageOpenedRunnable.class.getName();

        private Context mContext;
        private InAppMessage mInAppMessage;

        TrackMessageOpenedRunnable(Context context, InAppMessage message) {
            mContext = context.getApplicationContext();
            mInAppMessage = message;
        }

        @Override
        public void run() {
            SQLiteOpenHelper dbHelper = new InAppDbHelper(mContext);

            try {
                SQLiteDatabase db = dbHelper.getWritableDatabase();

                ContentValues values = new ContentValues();
                values.put(InAppMessageTable.COL_OPENED_AT, dbDateFormat.format(mInAppMessage.getOpenedAt()));

                String selection = InAppMessageTable.COL_ID + " = ?";
                String[] selectionArgs = { mInAppMessage.getInAppId()+"" };

                int count = db.update(InAppMessageTable.TABLE_NAME, values, selection, selectionArgs);

                if (count == 1){
                    Log.d("vlad", "opened_at written for inAppId: "+ mInAppMessage.getInAppId());
                }

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

                SQLiteDatabase db = dbHelper.getWritableDatabase();

                //READ all messages not shown before
                String[] projection = {InAppMessageTable.COL_ID, InAppMessageTable.COL_PRESENTED_WHEN, InAppMessageTable.COL_CONTENT_JSON};
                String selection = InAppMessageTable.COL_OPENED_AT+ " IS NULL";
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


            return new Pair<>(itemsToPresent,deliveredIds);



        }

        private List<Integer> insertRows(SQLiteDatabase db, List<ContentValues> rows){
            List<Integer> deliveredIds = new ArrayList<>();

            for(ContentValues row : rows){
                int id = (int) db.insertWithOnConflict(InAppMessageTable.TABLE_NAME, null, row, CONFLICT_IGNORE);
                if (id == -1) {
                    db.update(InAppMessageTable.TABLE_NAME, row, InAppMessageTable.COL_ID+"=?", new String[] {""+row.getAsInteger(InAppMessageTable.COL_ID)});
                }
                //FIXME : tracks all messages, which were received and saved/updated. delivered_at when message was opened
                deliveredIds.add(row.getAsInteger(InAppMessageTable.COL_ID));
            }

            return deliveredIds;
        }

        private void deleteRows(SQLiteDatabase db){
            String deleteSql ="DELETE FROM " + InAppMessageTable.TABLE_NAME +
                    " WHERE ("+ InAppMessageTable.COL_INBOX_CONFIG_JSON+" IS NULL AND "+ InAppMessageTable.COL_OPENED_AT+" IS NOT NULL) " +
                    " OR " +
                    "("+ InAppMessageTable.COL_INBOX_CONFIG_JSON+" IS NOT NULL " +
                    " AND (datetime('now') NOT BETWEEN IFNULL("+ InAppMessageTable.COL_INBOX_FROM+", '1970-01-01') AND IFNULL("+ InAppMessageTable.COL_INBOX_TO+", '3970-01-01')))";

            db.execSQL(deleteSql);
        }

        private List<InAppMessage> readRows(SQLiteDatabase db) {

            List<InAppMessage> itemsToPresent = new ArrayList<>();

            String[] projection = {InAppMessageTable.COL_ID, InAppMessageTable.COL_PRESENTED_WHEN, InAppMessageTable.COL_CONTENT_JSON};
            String selection = InAppMessageTable.COL_OPENED_AT+ " IS NULL";
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


        private List<ContentValues> assembleRows(){
            List<ContentValues> rows = new ArrayList<>();

            for (InAppMessage message: mInAppMessages ){
                ContentValues values = new ContentValues();

                values.put(InAppMessageTable.COL_ID, message.getInAppId());

                if (message.getOpenedAt() != null){
                    values.put(InAppMessageTable.COL_OPENED_AT, dbDateFormat.format(message.getOpenedAt()));
                }

                values.put(InAppMessageTable.COL_UPDATED_AT, dbDateFormat.format(message.getUpdatedAt()));
                values.put(InAppMessageTable.COL_PRESENTED_WHEN, message.getPresentedWhen());

                String inboxFrom = null;
                String inboxTo = null;
                JSONObject inbox = message.getInbox();

                if (inbox != null){
                    inboxFrom = this.getNullableString(inbox, "from");
                    inboxTo = this.getNullableString(inbox, "to");
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

        private String getNullableString(JSONObject json, String key){
            String str = json.optString(key, null);
            if(str.equals("null")) {
                return null;
            }
            return str;
        }
    }

}

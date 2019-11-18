package com.kumulos.android;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.kumulos.android.InAppContract.InAppMessageTable;

class InAppDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "k_in_app.db";
    private static final int DB_VERSION = 2;

    private static final String SQL_CREATE_IN_APP_MESSAGES
            = "CREATE TABLE " + InAppMessageTable.TABLE_NAME + "("
            + InAppMessageTable.COL_ID + " INTEGER PRIMARY KEY, "
            + InAppMessageTable.COL_CONTENT_JSON + " TEXT NOT NULL,"
            + InAppMessageTable.COL_PRESENTED_WHEN + " TEXT NOT NULL,"
            + InAppMessageTable.COL_DATA_JSON + " TEXT,"
            + InAppMessageTable.COL_BADGE_CONFIG_JSON + " TEXT,"
            + InAppMessageTable.COL_INBOX_CONFIG_JSON + " TEXT,"
            + InAppMessageTable.COL_INBOX_FROM + " DATETIME,"
            + InAppMessageTable.COL_INBOX_TO + " DATETIME,"
            + InAppMessageTable.COL_DISMISSED_AT + " DATETIME,"
            + InAppMessageTable.COL_UPDATED_AT + " DATETIME NOT NULL,"
            + InAppMessageTable.COL_TTL_HOURS + " INTEGER,"
            + InAppMessageTable.COL_SENT_AT + " DATETIME NOT NULL DEFAULT '1970-01-01')";

    InAppDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            db.execSQL(SQL_CREATE_IN_APP_MESSAGES);
        }
        catch (SQLException e) {
            Kumulos.log("Failed to create in app table");
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        switch(newVersion) {
            case 2:
                this.upgradeToVersion2(db);
            default:
                throw new IllegalStateException("onUpgrade() with unknown newVersion " + newVersion);
        }
    }

    private void upgradeToVersion2(SQLiteDatabase db){
        db.execSQL("ALTER TABLE " + InAppMessageTable.TABLE_NAME + " ADD COLUMN " + InAppMessageTable.COL_TTL_HOURS + " INTEGER;");
        db.execSQL("ALTER TABLE " + InAppMessageTable.TABLE_NAME + " ADD COLUMN " + InAppMessageTable.COL_SENT_AT + " DATETIME NOT NULL DEFAULT '1970-01-01';");
    }
}
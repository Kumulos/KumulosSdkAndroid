package com.kumulos.android;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.kumulos.android.AnalyticsContract.AnalyticsEvent;

/** package */ class AnalyticsDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "k_analytics.db";
    private static final int DB_VERSION = 2;

    private static final String SQL_CREATE_EVENTS
            = "CREATE TABLE " + AnalyticsEvent.TABLE_NAME + "("
            + AnalyticsEvent.COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + AnalyticsEvent.COL_HAPPENED_AT_MILLIS + " INTEGER NOT NULL,"
            + AnalyticsEvent.COL_EVENT_TYPE + " TEXT NOT NULL,"
            + AnalyticsEvent.COL_UUID + " TEXT UNIQUE NOT NULL,"
            + AnalyticsEvent.COL_PROPERTIES + " TEXT )";

    AnalyticsDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            db.execSQL(SQL_CREATE_EVENTS);
        }
        catch (SQLException e) {
            Kumulos.log("Failed to create analytics events table");
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // See: https://stackoverflow.com/a/26916986
        switch (oldVersion) {
            case 1:
                db.execSQL(String.format("ALTER TABLE %s ADD COLUMN %s TEXT DEFAULT NULL",
                        AnalyticsEvent.TABLE_NAME, AnalyticsEvent.COL_USER_IDENTIFIER));
                // nobreak: fallthrough for future version upgrades
        }
    }
}

package com.kumulos.android;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents the push notification sent by Kumulos
 */
public final class PushMessage implements Parcelable {

    public static final String EXTRAS_KEY = "com.kumulos.push.message";

    private String id;
    private String title;
    private String message;
    private JSONObject data;
    private long timeSent;
    private Uri url;
    private boolean runBackgroundHandler;

    /** package */ PushMessage(String id, @Nullable String title, @Nullable String message, @Nullable JSONObject data, long timeSent, @Nullable Uri url, boolean runBackgroundHandler) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.data = data;
        this.timeSent = timeSent;
        this.url = url;
        this.runBackgroundHandler = runBackgroundHandler;
    }

    private PushMessage(Parcel in) {
        id = in.readString();
        title = in.readString();
        message = in.readString();
        timeSent = in.readLong();
        runBackgroundHandler = (in.readInt() == 1);

        String dataString = in.readString();
        if (null != dataString) {
            try {
                data = new JSONObject(dataString);
            } catch (JSONException e) {
                data = null;
            }
        }

        String urlString = in.readString();
        if (null != urlString) {
            url = Uri.parse(urlString);
        }
    }

    public static final Creator<PushMessage> CREATOR = new Creator<PushMessage>() {
        @Override
        public PushMessage createFromParcel(Parcel in) {
            return new PushMessage(in);
        }

        @Override
        public PushMessage[] newArray(int size) {
            return new PushMessage[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        String dataString = (data != null) ? data.toString() : null;
        String urlString = (url != null) ? url.toString() : null;

        dest.writeString(id);
        dest.writeString(title);
        dest.writeString(message);
        dest.writeLong(timeSent);
        dest.writeInt(runBackgroundHandler ? 1 : 0);
        dest.writeString(dataString);
        dest.writeString(urlString);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public JSONObject getData() {
        return data;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public boolean hasTitleAndMessage() {
        return !TextUtils.isEmpty(title) && !TextUtils.isEmpty(message);
    }

    @Nullable
    public Uri getUrl() {
        return url;
    }

    public boolean runBackgroundHandler() {
        return runBackgroundHandler;
    }

}

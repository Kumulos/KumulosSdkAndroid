package com.kumulos.android;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public class PushChannel implements Parcelable {

    public String uuid;
    @Nullable
    public String name;
    public boolean isSubscribed;
    @Nullable
    public JSONObject meta;

    private PushChannel() { }

    private PushChannel(Parcel in) {
        uuid = in.readString();
        name = in.readString();
        isSubscribed = in.readInt() != 0;

        String metaStr = in.readString();
        if (null != metaStr) {
            try {
                meta = new JSONObject(metaStr);
            }
            catch (JSONException e) {
                meta = null;
            }
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        String metaStr = (meta != null) ? meta.toString() : null;

        dest.writeString(name);
        dest.writeString(uuid);
        dest.writeInt(isSubscribed ? 1 : 0);
        dest.writeString(metaStr);
    }

    public static final Creator<PushChannel> CREATOR = new Creator<PushChannel>() {
        @Override
        public PushChannel createFromParcel(Parcel in) {
            return new PushChannel(in);
        }

        @Override
        public PushChannel[] newArray(int size) {
            return new PushChannel[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    /** parckage */ static PushChannel fromJsonObject(JSONObject obj) throws JSONException {
        PushChannel channel = new PushChannel();

        channel.uuid= obj.getString("uuid");

        // Workaround 'bug': http://stackoverflow.com/a/23377941/543200
        if (!obj.isNull("name")) {
            channel.name = obj.getString("name");
        }

        channel.isSubscribed = obj.getBoolean("subscribed");
        channel.meta = obj.optJSONObject("meta");

        return channel;
    }

}

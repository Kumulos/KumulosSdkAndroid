package com.kumulos.android;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.Nullable;

/**
 * Represents the push notification sent by Kumulos
 */
public final class PushMessage implements Parcelable {

    public static final String EXTRAS_KEY = "com.kumulos.push.message";
    private static final int DEEP_LINK_TYPE_IN_APP = 1;
    public static final String TAG = PushMessage.class.getName();

    private final int id;
    private final @Nullable
    String title;
    private final @Nullable
    String message;
    private JSONObject data;
    private final long timeSent;
    private @Nullable
    Uri url;
    private final boolean runBackgroundHandler;
    private final int tickleId;
    private final @Nullable
    String pictureUrl;
    private @Nullable
    JSONArray buttons;
    private final @Nullable
    String sound;
    private final @Nullable
    String collapseKey;
    private final String channel;

    /**
     * package
     */
    PushMessage(int id,
                @Nullable String title,
                @Nullable String message,
                JSONObject data, long timeSent,
                @Nullable Uri url,
                boolean runBackgroundHandler,
                @Nullable String pictureUrl,
                @Nullable JSONArray buttons,
                @Nullable String sound,
                @Nullable String collapseKey) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.data = data;
        this.tickleId = this.getTickleId(data);
        this.timeSent = timeSent;
        this.url = url;
        this.runBackgroundHandler = runBackgroundHandler;
        this.pictureUrl = pictureUrl;
        this.buttons = buttons;
        this.sound = sound;
        this.collapseKey = collapseKey;
        this.channel = this.getChannel(data);
    }

    private PushMessage(Parcel in) {
        id = in.readInt();
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
        tickleId = in.readInt();
        pictureUrl = in.readString();

        String buttonsString = in.readString();
        if (null != buttonsString) {
            try {
                buttons = new JSONArray(buttonsString);
            } catch (JSONException e) {
                buttons = null;
            }
        }

        sound = in.readString();
        collapseKey = in.readString();
        channel = in.readString();
    }

    private String getChannel(JSONObject data) {
        String customChannel = data.optString("k.channel");
        String notificationType = data.optString("k.notificationType");

        if (!TextUtils.isEmpty(customChannel)) {
            return customChannel;
        }

        if (!TextUtils.isEmpty(notificationType) && notificationType.equals("important")) {
            return PushBroadcastReceiver.IMPORTANT_CHANNEL_ID;
        }

        return PushBroadcastReceiver.DEFAULT_CHANNEL_ID;
    }

    private Integer getTickleId(JSONObject data) {
        JSONObject deepLink = data.optJSONObject("k.deepLink");

        if (deepLink == null) {
            return -1;
        }

        int linkType = deepLink.optInt("type", -1);

        if (linkType != DEEP_LINK_TYPE_IN_APP) {
            return -1;
        }

        try {
            return deepLink.getJSONObject("data").getInt("id");
        } catch (JSONException e) {
            Kumulos.log(TAG, e.toString());
            return -1;
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
        String buttonsString = (buttons != null) ? buttons.toString() : null;

        dest.writeInt(id);
        dest.writeString(title);
        dest.writeString(message);
        dest.writeLong(timeSent);
        dest.writeInt(runBackgroundHandler ? 1 : 0);
        dest.writeString(dataString);
        dest.writeString(urlString);
        dest.writeInt(tickleId);
        dest.writeString(pictureUrl);
        dest.writeString(buttonsString);
        dest.writeString(sound);
        dest.writeString(collapseKey);
        dest.writeString(channel);
    }

    public int getId() {
        return id;
    }

    public @Nullable
    String getTitle() {
        return title;
    }

    public @Nullable
    String getMessage() {
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

    int getTickleId() {
        return tickleId;
    }

    public boolean runBackgroundHandler() {
        return runBackgroundHandler;
    }


    @Nullable
    public String getPictureUrl() {
        return this.pictureUrl;
    }

    @Nullable
    public JSONArray getButtons() {
        return this.buttons;
    }

    @Nullable
    public String getSound() {
        return this.sound;
    }

    @Nullable
    public String getCollapseKey() {
        return this.collapseKey;
    }

    public String getChannel() { return channel; }
}

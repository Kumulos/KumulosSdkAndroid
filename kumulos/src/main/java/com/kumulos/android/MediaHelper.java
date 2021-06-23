package com.kumulos.android;

import androidx.annotation.NonNull;

import java.net.MalformedURLException;
import java.net.URL;

public class MediaHelper {

    private static final String MEDIA_RESIZER_BASE_URL = "https://i.app.delivery";

    static @NonNull URL getCompletePictureUrl(String pictureUrl, int width) throws MalformedURLException{
        if (pictureUrl.substring(0, 8).equals("https://") || pictureUrl.substring(0, 7).equals("http://")){
            return new URL(pictureUrl);
        }

        return new URL(MEDIA_RESIZER_BASE_URL + "/" + width + "x/" + pictureUrl);
    }
}

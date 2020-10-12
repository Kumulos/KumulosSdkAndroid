package com.kumulos.android;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.webkit.URLUtil;

import androidx.annotation.Nullable;

import java.net.MalformedURLException;
import java.net.URL;

import static android.content.Context.CLIPBOARD_SERVICE;

public class DeferredDeepLinkHelper {

    public void checkForDeferredLink(Context context) {//TODO: modifier

        SharedPreferences preferences = context.getSharedPreferences(SharedPrefs.PREFS_FILE, Context.MODE_PRIVATE);
        boolean checked = preferences.getBoolean(SharedPrefs.DEFERRED_LINK_CHECKED_KEY, false);
        if (checked) {
            return;
        }

        String text = this.getClipText(context);
        if (text == null) {
            return;
        }

        URL url = this.getURL(text);
        if (url == null) {
            return;
        }

        if (!this.urlShouldBeHandled(url)) {
            return;
        }

        this.handleDeepLink(url);


        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(SharedPrefs.DEFERRED_LINK_CHECKED_KEY, true);
        editor.apply();
    }

    private @Nullable
    String getClipText(Context context) {//TODO: can ensure no need to check MIME type, wtf is  MIMETYPE_TEXT_URILIST. Can happen multiple items?
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return null;
        }

        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null) {
            return null;
        }

        if (clip.getItemCount() != 1) {
            return null;
        }

        CharSequence text = clip.getItemAt(0).getText();
        if (text == null) {
            return null;
        }

        return text.toString();
    }

    private @Nullable
    URL getURL(String text) {
        boolean isUrl = URLUtil.isValidUrl(text);
        if (!isUrl) {
            return null;
        }


        try {
            return new URL(text);

        } catch (MalformedURLException e) {
            return null;
        }


    }

    private boolean urlShouldBeHandled(URL url) {
        String host = url.getHost();
        KumulosConfig config = Kumulos.getConfig();
        URL cname = config.getDeepLinkCname();

        return host.endsWith("lnk.click") || (cname != null && host.equals(cname.getHost()));
    }

    private void handleDeepLink(URL url) {

    }


}



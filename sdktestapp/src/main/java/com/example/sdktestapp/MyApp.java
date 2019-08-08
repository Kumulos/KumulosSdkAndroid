package com.example.sdktestapp;

import android.app.Application;

import com.kumulos.android.Kumulos;
import com.kumulos.android.KumulosConfig;

public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        KumulosConfig.Builder config = new KumulosConfig.Builder(
                BuildConfig.K_API_KEY,
                BuildConfig.K_SECRET_KEY
        );

        config.enableInAppMessaging(KumulosConfig.InAppConsentStrategy.AUTO_ENROLL);

        Kumulos.initialize(this, config.build());
        Kumulos.setInAppDeepLinkHandler(new MyDeepLinkHandler());
        Kumulos.pushRegister(this);
    }
}

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

        Kumulos.initialize(this, config.build());
    }
}

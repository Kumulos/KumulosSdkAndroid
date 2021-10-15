package com.kumulos.android;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This API is intended for internal SDK use. Do not call this API or depend on it in your app.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
public @interface InternalSdkEmbeddingApi {
    String purpose();
}

package com.kumulos.android;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class UrlBuilder {

    public enum Service {
        BACKEND,
        CRASH,
        CRM,
        DDL,
        EVENTS,
        IAR,
        MEDIA,
        PUSH,
    }

    private final Map<Service, String> baseUrlMap;

    UrlBuilder(Map<Service, String> baseUrlMap) {
        for (Service s : Service.values()) {
            if (!baseUrlMap.containsKey(s)) {
                throw new IllegalArgumentException("baseUrlMap must contain an entry for every Service entry");
            }
        }

        this.baseUrlMap = baseUrlMap;
    }

    String urlForService(Service service, String path) {
        String baseUrl = baseUrlMap.get(service);

        return baseUrl + path;
    }

    static Map<Service, String> defaultMapping(@NonNull String region) {
        Map<Service, String> baseUrlMap = new HashMap<>(Service.values().length);

        baseUrlMap.put(Service.BACKEND, "https://api.kumulos.com");
        baseUrlMap.put(Service.CRASH, "https://crash.kumulos.com");
        baseUrlMap.put(Service.IAR, "https://iar.app.delivery");
        baseUrlMap.put(Service.MEDIA, "https://i.app.delivery");

        baseUrlMap.put(Service.PUSH, "https://push-" + region + ".kumulos.com");
        baseUrlMap.put(Service.CRM, "https://crm-" + region + ".kumulos.com");
        baseUrlMap.put(Service.EVENTS, "https://events-" + region + ".kumulos.com");
        baseUrlMap.put(Service.DDL, "https://links-" + region + ".kumulos.com");

        return baseUrlMap;
    }
}
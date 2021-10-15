package com.kumulos.android;

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

    static Map<Service, String> defaultMapping() {
        Map<Service, String> baseUrlMap = new HashMap<>(Service.values().length);

        baseUrlMap.put(Service.BACKEND, "https://api.kumulos.com");
        baseUrlMap.put(Service.CRASH, "https://crash.kumulos.com");
        baseUrlMap.put(Service.CRM, "https://crm.kumulos.com");
        baseUrlMap.put(Service.DDL, "https://links.kumulos.com");
        baseUrlMap.put(Service.IAR, "https://iar.app.delivery");
        baseUrlMap.put(Service.MEDIA, "https://i.app.delivery");
        baseUrlMap.put(Service.EVENTS, "https://events.kumulos.com");
        baseUrlMap.put(Service.PUSH, "https://push.kumulos.com");

        return baseUrlMap;
    }

}

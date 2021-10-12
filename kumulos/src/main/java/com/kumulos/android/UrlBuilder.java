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
        Map<Service, String> baseUrlMap = new HashMap<>(UrlBuilder.Service.values().length);

        baseUrlMap.put(UrlBuilder.Service.BACKEND, "https://api.kumulos.com/b2.2");
        baseUrlMap.put(UrlBuilder.Service.CRASH, "https://crash.kumulos.com");
        baseUrlMap.put(UrlBuilder.Service.CRM, "https://crm.kumulos.com");
        baseUrlMap.put(UrlBuilder.Service.DDL, "https://links.kumulos.com");
        baseUrlMap.put(UrlBuilder.Service.EVENTS, "https://events.kumulos.com");
        baseUrlMap.put(UrlBuilder.Service.PUSH, "https://push.kumulos.com");

        return baseUrlMap;
    }

}

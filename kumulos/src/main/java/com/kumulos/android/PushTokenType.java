package com.kumulos.android;

public enum PushTokenType {
    FCM(2),
    HCM(5);

    private final int type;

    PushTokenType(int type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return String.valueOf(type);
    }

    public int getValue() {
        return type;
    }
}
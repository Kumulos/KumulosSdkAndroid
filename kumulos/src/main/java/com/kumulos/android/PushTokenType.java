
package com.kumulos.android;

enum PushTokenType {
    ANDROID(2);

    private final int type;

    PushTokenType(int type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return String.valueOf(type);
    }

    public int getValue(){ return type;}
}

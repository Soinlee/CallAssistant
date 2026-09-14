package org.xdty.phone.number.model;

public interface NumberHandler<T extends INumber> {

    T find(String number);

    boolean isOnline();

    int getApiId();

    // Legacy hooks retained for binary/source compatibility. Modern providers do not need them.
    default String url() {
        return null;
    }

    default String key() {
        return null;
    }
}

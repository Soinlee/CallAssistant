package org.xdty.phone.number.model;

public interface INumber {

    int API_ID_CUSTOM = -2000;
    /** 国际号码国家识别（仅对非 +86/0086 的国际号码生效）。 */
    int API_ID_AREA = -1999;
    int API_ID_SPECIAL = -1000;
    int API_ID_COMMON = -200;
    int API_ID_CALLER = -150;
    int API_ID_MARKED = -100;
    int API_ID_MVNP = -50;
    int API_ID_OFFLINE = -2;
    int API_ID_GOOGLE = -1;

    String getName();

    String getProvince();

    Type getType();

    String getCity();

    String getNumber();

    String getProvider();

    int getCount();

    boolean isValid();

    boolean isOnline();

    boolean hasGeo();

    int getApiId();

    void patch(INumber i);
}

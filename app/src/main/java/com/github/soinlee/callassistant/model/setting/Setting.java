package com.github.soinlee.callassistant.model.setting;

import com.github.soinlee.callassistant.model.Status;

import java.util.ArrayList;

public interface Setting {

    String getIgnoreRegex();

    boolean isHidingOffHook();

    boolean isShowingOnOutgoing();

    boolean isIgnoreKnownContact();

    boolean isShowingContactOffline();

    boolean isForceChinese();

    int getWindowX();

    int getWindowY();

    void setWindow(int x, int y);

    int getScreenWidth();

    int getScreenHeight();

    int getStatusBarHeight();

    int getWindowHeight();

    int getDefaultHeight();

    boolean isShowCloseAnim();

    boolean isHidingWhenTouch();

    boolean isTransBackOnly();

    boolean isEnableTextColor();

    int getTextPadding();

    int getTextAlignment();

    int getTextSize();

    int getWindowTransparent();

    boolean isDisableMove();

    boolean isMarkingEnabled();

    boolean isCustomDbImported();

    void addPaddingMark(String number);

    void removePaddingMark(String number);

    ArrayList<String> getPaddingMarks();

    String getUid();

    void updateLastCheckDataUpdateTime(long timestamp);

    Status getStatus();

    void clear();

    int getNormalColor();

    int getPoiColor();

    int getReportColor();

    void setOutgoing(boolean isOutgoing);

    boolean isOutgoingPositionEnabled();
}

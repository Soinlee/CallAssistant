package com.github.soinlee.callassistant.utils;

import android.content.Context;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import android.util.Log;

import com.github.soinlee.callassistant.R;
import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.model.TextColorPair;
import com.github.soinlee.callassistant.service.FloatWindow;
import org.xdty.phone.number.model.INumber;

import wei.mark.standout.StandOutWindow;

public final class Window {

    private static final String TAG = Window.class.getSimpleName();

    private Context mContext;

    private boolean isShowing = false;

    public Window() {
        mContext = Application.getApplication();
    }

    public void showTextWindow(int resId, Type type) {
        isShowing = true;

        int frontType = type.value();
        Bundle bundle = new Bundle();
        bundle.putString(FloatWindow.NUMBER_INFO, Resource.getInstance().getResources().getString(resId));
        bundle.putInt(FloatWindow.WINDOW_COLOR, ContextCompat.getColor(mContext,
                R.color.colorPrimary));
        Log.d(TAG, "showTextWindow: " + Utils.bundleToString(bundle));
        // Always relay SHOW as a plain startService (NOT startForegroundService) so
        // we never cold-start an FGS from a background broadcast on Android 15+.
        // When the persistent foreground service is already resident this simply
        // delivers the command to it; when it is NOT resident (service crashed or
        // not yet started, sOngoing == false) we degrade gracefully to a silent
        // no-window case instead of throwing
        // ForegroundServiceStartNotAllowedException.
        FloatWindow.showQuietly(mContext, FloatWindow.class, frontType);
        FloatWindow.sendData(mContext, FloatWindow.class,
                frontType, 0, bundle, FloatWindow.class, 0);
    }

    public void sendData(String key, int value, Type type) {
        Log.d(TAG, "sendData");
        isShowing = true;

        int frontType = type.value();
        Bundle bundle = new Bundle();
        bundle.putInt(key, value);
        // See showTextWindow(): never cold-start the foreground service from a
        // background broadcast on Android 15+; degrade gracefully if not resident.
        FloatWindow.showQuietly(mContext, FloatWindow.class, frontType);
        FloatWindow.sendData(mContext, FloatWindow.class,
                frontType, 0, bundle, FloatWindow.class, 0);
    }

    public void closeWindow() {
        Log.d(TAG, "closeWindow");
        if (isShowing) {
            isShowing = false;
            FloatWindow.closeAll(mContext, FloatWindow.class);
        }
    }

    /**
     * Closes only the search (offline-attribution) floating window, leaving
     * the incoming-call / other windows untouched. Used to dismiss a leftover
     * search popup when the user starts dialing or opens the dial pad.
     */
    public void closeSearchWindow() {
        Log.d(TAG, "closeSearchWindow");
        StandOutWindow.close(mContext, FloatWindow.class, Type.SEARCH.value());
    }

    public void showWindow(INumber number, Type type) {
        isShowing = true;

        int frontType = type.value();

        TextColorPair textColor = TextColorPair.from(number);

        Bundle bundle = new Bundle();
        bundle.putString(FloatWindow.NUMBER_INFO, textColor.text);
        bundle.putInt(FloatWindow.WINDOW_COLOR, textColor.color);
        Log.d(TAG, "showWindow: " + Utils.bundleToString(bundle));
        // See showTextWindow(): relay SHOW as a plain startService; never cold-start
        // the foreground service from a background broadcast on Android 15+.
        FloatWindow.showQuietly(mContext, FloatWindow.class, frontType);
        FloatWindow.sendData(mContext, FloatWindow.class,
                frontType, 0, bundle, FloatWindow.class, 0);
    }

    public void hideWindow() {
        Log.d(TAG, "hideWindow");
        if (isShowing) {
            StandOutWindow.hide(mContext, FloatWindow.class, Type.CALLER.value());
        }
    }

    public boolean isShowing() {
        return isShowing;
    }

    public enum Type {

        CALLER(FloatWindow.CALLER_FRONT),
        POSITION(FloatWindow.SET_POSITION_FRONT),
        SETTING(FloatWindow.SETTING_FRONT),
        SEARCH(FloatWindow.SEARCH_FRONT);

        private final int mType;

        Type(int type) {
            mType = type;
        }

        public int value() {
            return mType;
        }
    }

}

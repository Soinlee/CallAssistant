package com.github.soinlee.callassistant.utils;

import android.util.Log;

import com.github.soinlee.callassistant.application.Application;

public final class Alarm {

    private static final String TAG = Alarm.class.getSimpleName();

    public Alarm() {
        Application.getApplication().getAppComponent().inject(this);
    }

    /**
     * The auto-report (ScheduleService) and offline-data upgrade (UpgradeWorker) features have
     * been removed. This method is retained as a no-op for backward compatibility with existing
     * callers.
     */
    public void alarm() {
        Log.v(TAG, "alarm");
    }
}

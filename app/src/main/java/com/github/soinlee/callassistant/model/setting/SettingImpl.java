package com.github.soinlee.callassistant.model.setting;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import com.github.soinlee.callassistant.R;
import com.github.soinlee.callassistant.model.Status;
import com.github.soinlee.callassistant.utils.Utils;
import org.xdty.phone.number.model.custom.CustomHandler;

import java.util.ArrayList;
import java.util.Arrays;

public class SettingImpl implements Setting {

    private static Gson gson = new Gson();
    private static Context sContext;
    private SharedPreferences mPrefs;
    private SharedPreferences mWindowPrefs;

    private boolean isOutgoing;

    private SettingImpl() {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(sContext);
        mWindowPrefs = sContext.getSharedPreferences("window", Context.MODE_PRIVATE);
    }

    public static void init(Context context) {
        sContext = context.getApplicationContext();
    }

    public static Setting getInstance() {
        if (sContext == null) {
            throw new IllegalStateException("Setting is not initialized!");
        }
        return SingletonHelper.INSTANCE;
    }

    @Override
    public int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    @Override
    public int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    @Override
    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = sContext.getResources()
                .getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = sContext.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    @Override
    public int getWindowHeight() {
        return mPrefs.getInt(getString(R.string.window_height_key), getDefaultHeight());
    }

    @Override
    public int getDefaultHeight() {
        return getScreenHeight() / 8;
    }

    @Override
    public boolean isShowCloseAnim() {
        return mPrefs.getBoolean(getString(R.string.window_close_anim_key), true);
    }

    @Override
    public boolean isTransBackOnly() {
        return mPrefs.getBoolean(getString(R.string.window_trans_back_only_key), true);
    }

    @Override
    public boolean isEnableTextColor() {
        return mPrefs.getBoolean(getString(R.string.window_text_color_key), false);
    }

    @Override
    public int getTextPadding() {
        return mPrefs.getInt(getString(R.string.window_text_padding_key), 0);
    }

    @Override
    public int getTextAlignment() {
        return mPrefs.getInt(getString(R.string.window_text_alignment_key), 1);
    }

    @Override
    public int getTextSize() {
        return mPrefs.getInt(getString(R.string.window_text_size_key), 20);
    }

    @Override
    public int getWindowTransparent() {
        return mPrefs.getInt(getString(R.string.window_transparent_key), 80);
    }

    @Override
    public boolean isDisableMove() {
        return mPrefs.getBoolean(getString(R.string.disable_move_key), false);
    }

    @Override
    public boolean isMarkingEnabled() {
        return mPrefs.getBoolean(getString(R.string.enable_marking_key), false);
    }

    @Override
    public boolean isCustomDbImported() {
        return mPrefs.getBoolean(CustomHandler.KEY_IMPORTED, false);
    }

    @Override
    public void addPaddingMark(String number) {
        String key = getString(R.string.padding_mark_numbers_key);
        ArrayList<String> list = getPaddingMarks();
        if (list.contains(number)) {
            return;
        }
        list.add(number);
        String paddingNumbers = gson.toJson(list);
        mPrefs.edit().putString(key, paddingNumbers).apply();
    }

    @Override
    public void removePaddingMark(String number) {
        String key = getString(R.string.padding_mark_numbers_key);
        ArrayList<String> list = getPaddingMarks();
        if (!list.contains(number)) {
            return;
        }
        list.remove(number);
        String paddingNumbers = gson.toJson(list);
        mPrefs.edit().putString(key, paddingNumbers).apply();
    }

    @Override
    public ArrayList<String> getPaddingMarks() {
        String key = getString(R.string.padding_mark_numbers_key);
        if (mPrefs.contains(key)) {
            String paddingNumbers = mPrefs.getString(key, null);
            return new ArrayList<>(
                    Arrays.asList(gson.fromJson(paddingNumbers, String[].class)));
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public String getUid() {
        String key = getString(R.string.uid_key);
        if (mPrefs.contains(key)) {
            return mPrefs.getString(key, "");
        } else {
            String uid = Utils.getDeviceId(sContext);
            mPrefs.edit().putString(key, uid).apply();
            return uid;
        }
    }

    @Override
    public void updateLastCheckDataUpdateTime(long timestamp) {
        mPrefs.edit()
                .putLong(getString(R.string.last_check_data_update_time_key), timestamp)
                .apply();
    }

    @Override
    public Status getStatus() {
        return new Status(
                mPrefs.getInt(getString(R.string.offline_status_version_key), 0),
                mPrefs.getInt(getString(R.string.offline_status_count_key), 0),
                mPrefs.getInt(getString(R.string.offline_status_new_count_key), 0),
                mPrefs.getLong(getString(R.string.offline_status_timestamp_key), 0),
                "",
                ""
        );
    }

    @Override
    public void clear() {
        mPrefs.edit().clear().apply();
        mWindowPrefs.edit().clear().apply();
    }

    @Override
    public int getNormalColor() {
        return mPrefs.getInt("color_normal", ContextCompat.getColor(sContext, R.color.blue_light));
    }

    @Override
    public int getPoiColor() {
        return mPrefs.getInt("color_poi", ContextCompat.getColor(sContext, R.color.orange_dark));
    }

    @Override
    public int getReportColor() {
        return mPrefs.getInt("color_report", ContextCompat.getColor(sContext, R.color.red_light));
    }

    @Override
    public void setOutgoing(boolean isOutgoing) {
        this.isOutgoing = isOutgoing;
    }

    @Override
    public boolean isOutgoingPositionEnabled() {
        return mPrefs.getBoolean(getString(R.string.outgoing_window_position_key), false);
    }

    @Override
    public boolean isHidingWhenTouch() {
        return mPrefs.getBoolean(getString(R.string.hide_when_touch_key), false);
    }

    @Override
    public String getIgnoreRegex() {
        return mPrefs.getString(getString(R.string.ignore_regex_key), "").replace("*",
                "[0-9]").replace(" ", "|");
    }

    @Override
    public boolean isHidingOffHook() {
        return mPrefs.getBoolean(getString(R.string.hide_when_off_hook_key), false);
    }

    @Override
    public boolean isShowingOnOutgoing() {
        return mPrefs.getBoolean(getString(R.string.display_on_outgoing_key), false);
    }

    @Override
    public boolean isIgnoreKnownContact() {
        return mPrefs.getBoolean(getString(R.string.ignore_known_contact_key), false);
    }

    @Override
    public boolean isShowingContactOffline() {
        return mPrefs.getBoolean(getString(R.string.contact_offline_key), false);
    }

    @Override
    public boolean isForceChinese() {
        return mPrefs.getBoolean(getString(R.string.force_chinese_key), false);
    }

    @Override
    public int getWindowX() {
        String prefix = isOutgoing && isOutgoingPositionEnabled() ? "out_" : "";
        return mWindowPrefs.getInt(prefix + "x", -1);
    }

    @Override
    public int getWindowY() {
        String prefix = isOutgoing && isOutgoingPositionEnabled() ? "out_" : "";
        return mWindowPrefs.getInt(prefix + "y", -1);
    }

    @Override
    public void setWindow(int x, int y) {
        String prefix = isOutgoing && isOutgoingPositionEnabled() ? "out_" : "";
        SharedPreferences.Editor editor = mWindowPrefs.edit();
        editor.putInt(prefix + "x", x);
        editor.putInt(prefix + "y", y);
        editor.apply();
    }

    private String getString(@StringRes int resId) {
        return sContext.getString(resId);
    }

    private static class SingletonHelper {
        private final static SettingImpl INSTANCE = new SettingImpl();
    }
}

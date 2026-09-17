package com.github.soinlee.callassistant.fragment;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.AppBarLayout;

import com.github.soinlee.callassistant.BuildConfig;
import com.github.soinlee.callassistant.R;
import com.github.soinlee.callassistant.activity.SettingsActivity;
import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.model.Status;
import com.github.soinlee.callassistant.model.setting.SettingImpl;
import org.xdty.phone.number.model.custom.CustomHandler;
import com.github.soinlee.callassistant.service.FloatWindow;
import com.github.soinlee.callassistant.utils.Utils;
import com.github.soinlee.callassistant.utils.Window;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import app.minimize.com.seek_bar_compat.SeekBarCompat;

import static com.github.soinlee.callassistant.utils.Utils.mask;

public class SettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceClickListener {

    private final static int SUMMARY_FLAG_NORMAL = 0x00000001;
    private final static int SUMMARY_FLAG_MASK = 0x00000002;
    private final static int SUMMARY_FLAG_NULL = 0x00000004;
    private final static int REQ_IMPORT_DB = 0x1001;

    @Inject
    Window mWindow;

    Toast toast;
    SharedPreferences sharedPrefs;

    private Point mPoint;
    private HashMap<String, Integer> keyMap = new HashMap<>();
    private HashMap<String, Preference> prefMap = new HashMap<>();

    // Tracks which preference switch triggered the READ_CONTACTS permission request, so
    // the Activity Result callback knows which switch to un-check on denial.
    private int mPendingContactsKey;

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Application.getApplication().getAppComponent().inject(this);

        addPreferencesFromResource(R.xml.settings);

        sharedPrefs = getPreferenceManager().getSharedPreferences();

        WindowManager mWindowManager = (WindowManager) getActivity().
                getSystemService(Context.WINDOW_SERVICE);
        Display display = mWindowManager.getDefaultDisplay();
        mPoint = new Point();
        display.getSize(mPoint);

        bindPreference(R.string.window_text_size_key);
        bindPreference(R.string.window_height_key);
        bindPreferenceList(R.string.window_text_alignment_key, R.array.align_type, 1);
        bindPreference(R.string.window_transparent_key);
        bindPreference(R.string.window_text_padding_key);
        bindPreference(R.string.ignore_known_contact_key);
        bindPreference(R.string.display_on_outgoing_key);
        bindPreference(R.string.ignore_regex_key, false);
        bindPreference(R.string.ignore_battery_optimizations_key);
        bindPreference(R.string.enable_marking_key);
        bindPreference(R.string.outgoing_window_position_key);
        bindCustomDbPreference();

        bindDataVersionPreference();
        bindVersionPreference();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // targetSdk 35 forces edge-to-edge, so the settings list draws under the
        // system navigation bar (bottom). The top bar is an AppBarLayout whose
        // fitsSystemWindows keeps the blue bar flush with the status bar, so the
        // list top needs no extra padding; the list bottom is padded in
        // {@link SettingsActivity} on the activity root (reliable inset dispatch).
        // Nested PreferenceScreen dialogs are handled separately in
        // {@link #setUpNestedScreen}.
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    private void bindCustomDbPreference() {
        int importKey = R.string.custom_db_import_key;
        String importKeyStr = getString(importKey);
        Preference importPref = findPreference(importKeyStr);
        importPref.setOnPreferenceClickListener(this);
        keyMap.put(importKeyStr, importKey);
        prefMap.put(importKeyStr, importPref);

        int statusKey = R.string.custom_db_status_key;
        String statusKeyStr = getString(statusKey);
        Preference statusPref = findPreference(statusKeyStr);
        statusPref.setSummary(SettingImpl.getInstance().isCustomDbImported()
                ? getString(R.string.custom_db_imported)
                : getString(R.string.custom_db_not_imported));
        prefMap.put(statusKeyStr, statusPref);
    }

    private void bindDataVersionPreference() {
        bindPreference(R.string.offline_data_version_key);
        Preference dataVersion = findPreference(getString(R.string.offline_data_version_key));
        Status status = SettingImpl.getInstance().getStatus();
        String summary = getString(R.string.offline_data_version_summary, status.getVersion(),
                status.getCount(), Utils.getDate(status.getTimestamp() * 1000));
        if (status.getVersion() == 0) {
            summary = getString(R.string.no_offline_data);
        }
        dataVersion.setSummary(summary);
    }

    private void bindVersionPreference() {

        bindPreference(R.string.version_key);
        Preference version = findPreference(getString(R.string.version_key));
        String versionString = BuildConfig.VERSION_NAME;
        if (BuildConfig.DEBUG) {
            versionString += "." + BuildConfig.BUILD_TYPE;
        }
        version.setSummary(versionString);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                                         Preference preference) {
        super.onPreferenceTreeClick(preferenceScreen, preference);
        if (preference instanceof PreferenceScreen) {
            setUpNestedScreen((PreferenceScreen) preference);
        }
        return false;
    }

    @Override
    public Preference findPreference(CharSequence key) {
        Preference pref = prefMap.get(key.toString());
        if (pref == null) {
            pref = super.findPreference(key);
        }
        return pref;
    }

    public void setUpNestedScreen(PreferenceScreen preferenceScreen) {
        final Dialog dialog = preferenceScreen.getDialog();

        AppBarLayout appBarLayout;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                || Build.VERSION.RELEASE.equals("7.0") || Build.VERSION.RELEASE.equals("N")) {
            ListView listView = (ListView) dialog.findViewById(android.R.id.list);
            ViewGroup root = (ViewGroup) listView.getParent();

            appBarLayout = (AppBarLayout) LayoutInflater.from(getActivity()).inflate(
                    R.layout.settings_toolbar, root, false);

            int height;
            TypedValue tv = new TypedValue();
            if (getActivity().getTheme().resolveAttribute(R.attr.actionBarSize, tv, true)) {
                height = TypedValue.complexToDimensionPixelSize(tv.data,
                        getResources().getDisplayMetrics());
            } else {
                height = appBarLayout.getHeight();
            }

            // Nested PreferenceScreen dialog window is also edge-to-edge on
            // targetSdk 35 (reaches y=0 and under the nav bar). The injected
            // toolbar (settings_toolbar, fitsSystemWindows) already shifts its
            // own content below the status bar, but the ListView itself starts
            // at y=0 with no insets: pad the top by status bar + action bar so
            // the first row clears the toolbar. The dialog does NOT report the
            // status-bar inset via WindowInsets, so read it from the system
            // resource (same approach as SettingImpl / MainBottomSheetFragment).
            // Bottom inset is read via root window insets for the nav-bar height.
            int statusBarHeight = 0;
            int statusRes = getResources().getIdentifier(
                    "status_bar_height", "dimen", "android");
            if (statusRes > 0) {
                statusBarHeight = getResources().getDimensionPixelSize(statusRes);
            }
            int topInset = height + statusBarHeight;

            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(listView);
            Insets systemBars = rootInsets == null
                    ? Insets.NONE
                    : rootInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int bottomInset = systemBars.bottom;
            listView.setPadding(0, topInset, 0, bottomInset);
            root.addView(appBarLayout, 0);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            LinearLayout root =
                    (LinearLayout) dialog.findViewById(android.R.id.list).getParent();
            appBarLayout = (AppBarLayout) LayoutInflater.from(getActivity()).inflate(
                    R.layout.settings_toolbar, root, false);
            root.addView(appBarLayout, 0);
        } else {
            ViewGroup root = (ViewGroup) dialog.findViewById(android.R.id.content);
            ListView content = (ListView) root.getChildAt(0);

            root.removeAllViews();

            appBarLayout = (AppBarLayout) LayoutInflater.from(getActivity()).inflate(
                    R.layout.settings_toolbar, root, false);

            int height;
            TypedValue tv = new TypedValue();
            if (getActivity().getTheme().resolveAttribute(R.attr.actionBarSize, tv, true)) {
                height = TypedValue.complexToDimensionPixelSize(tv.data,
                        getResources().getDisplayMetrics());
            } else {
                height = appBarLayout.getHeight();
            }

            content.setPadding((int) dpToPx(16), height, (int) dpToPx(16), 0);

            root.addView(content);
            root.addView(appBarLayout);
        }

        Toolbar toolbar = (Toolbar) appBarLayout.findViewById(R.id.toolbar);
        toolbar.setTitle(preferenceScreen.getTitle());
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    /**
     * Callback invoked by {@link SettingsActivity} when the READ_CONTACTS runtime
     * permission request (launched via the Activity Result API) completes. If denied,
     * un-check whichever preference switch triggered the request.
     */
    public void onContactsPermissionResult(boolean granted) {
        if (!granted && mPendingContactsKey != 0) {
            setChecked(mPendingContactsKey, false);
        }
        mPendingContactsKey = 0;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_IMPORT_DB) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                importCustomDb(data.getData());
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void importCustomDb(Uri uri) {
        Context context = getActivity();
        if (context == null) {
            return;
        }
        File tmp = new File(context.getCacheDir(), "import_custom.db");
        try {
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) {
                toastMessage(R.string.custom_db_import_failed);
                return;
            }
            FileOutputStream tmpOut = new FileOutputStream(tmp);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                tmpOut.write(buf, 0, len);
            }
            tmpOut.close();
            in.close();

            // 先校验是否为合法 SQLite3，区分格式错误与无归属地表
            SQLiteDatabase testDb = null;
            try {
                testDb = SQLiteDatabase.openDatabase(tmp.getAbsolutePath(), null,
                        SQLiteDatabase.OPEN_READONLY);
            } catch (SQLiteException e) {
                toastMessage(R.string.custom_db_bad_format);
                return;
            } finally {
                if (testDb != null) {
                    try {
                        testDb.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            String[] mapping = probeCustomDb(tmp);
            if (mapping == null) {
                toastMessage(R.string.custom_db_no_table);
                return;
            }
            // mapping 结构：[table, phone, province, city, isp, citycode]；
            // 若旧版库无 citycode 列，mapping[5] 可能为 null，需安全写入。

            File dir = context.getExternalFilesDir(null);
            if (dir == null || !dir.exists() && !dir.mkdirs()) {
                toastMessage(R.string.custom_db_import_failed);
                return;
            }
            File target = new File(dir, CustomHandler.DB_NAME);
            copyFile(tmp, target);

            android.content.SharedPreferences.Editor editor = sharedPrefs.edit()
                    .putBoolean(CustomHandler.KEY_IMPORTED, true)
                    .putString(CustomHandler.KEY_TABLE, mapping[0])
                    .putString(CustomHandler.KEY_COL_PHONE, mapping[1])
                    .putString(CustomHandler.KEY_COL_PROVINCE, mapping[2])
                    .putString(CustomHandler.KEY_COL_CITY, mapping[3])
                    .putString(CustomHandler.KEY_COL_ISP, mapping[4]);
            if (mapping.length > 5 && mapping[5] != null) {
                editor.putString(CustomHandler.KEY_COL_CITYCODE, mapping[5]);
            }
            editor.apply();
            CustomHandler.invalidate();

            updateCustomDbStatus();
            toastMessage(R.string.custom_db_import_success);
        } catch (Exception e) {
            e.printStackTrace();
            toastMessage(R.string.custom_db_bad_format);
        } finally {
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    private void updateCustomDbStatus() {
        int statusKey = R.string.custom_db_status_key;
        String statusKeyStr = getString(statusKey);
        Preference statusPref = findPreference(statusKeyStr);
        if (statusPref != null) {
            statusPref.setSummary(SettingImpl.getInstance().isCustomDbImported()
                    ? getString(R.string.custom_db_imported)
                    : getString(R.string.custom_db_not_imported));
        }
    }

    /**
     * 校验并探测自定义归属地库，返回 [table, phoneCol, provinceCol, cityCol, ispCol, citycodeCol]。
     * 非 SQLite 或未找到有效归属地表时返回 null。
     */
    private String[] probeCustomDb(File file) {
        String[] mapping = null;
        SQLiteDatabase db = null;
        Cursor tables = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            tables = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' "
                            + "AND name NOT LIKE 'sqlite_%'", null);
            while (tables.moveToNext()) {
                String table = tables.getString(0);
                String[] cols = tableColumns(db, table);
                String[] m = matchColumns(cols);
                if (m != null) {
                    // [table, phone, province, city, isp, citycode]
                    mapping = new String[]{table, m[0], m[1], m[2], m[3], m[4]};
                    break;
                }
            }
        } catch (SQLiteException e) {
            // 非 SQLite3 文件或损坏
            mapping = null;
        } finally {
            if (tables != null) {
                try {
                    tables.close();
                } catch (Exception ignored) {
                }
            }
            if (db != null) {
                try {
                    db.close();
                } catch (Exception ignored) {
                }
            }
        }
        return mapping;
    }

    private String[] tableColumns(SQLiteDatabase db, String table) {
        Cursor c = null;
        try {
            c = db.rawQuery("PRAGMA table_info(\"" + table + "\")", null);
            int size = c.getCount();
            if (size > 0) {
                String[] cols = new String[size];
                int i = 0;
                while (c.moveToNext()) {
                    cols[i++] = c.getString(1); // name
                }
                return cols;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }
        return new String[0];
    }

    /**
     * 从列名集合中匹配语义列。返回 [phone, province, city, isp] 或 null。
     */
    private String[] matchColumns(String[] cols) {
        String phone = null;
        String province = null;
        String city = null;
        String isp = null;
        String citycode = null;
        for (String col : cols) {
            if (col == null) {
                continue;
            }
            String lower = col.toLowerCase();
            if (phone == null && (lower.contains("phone") || lower.contains("number")
                    || lower.contains("prefix") || lower.contains("td"))) {
                phone = col;
            } else if (citycode == null && (lower.contains("citycode")
                    || lower.contains("city_code") || lower.contains("\u533a\u53f7"))) {
                // 必须在 city 之前判断，避免 city_code 被误判为 city
                citycode = col;
            } else if (province == null && (lower.contains("province") || lower.contains("\u7701"))) {
                province = col;
            } else if (city == null && (lower.contains("city") || lower.contains("\u5e02"))) {
                city = col;
            } else if (isp == null && (lower.contains("isp") || lower.contains("operator")
                    || lower.contains("carrier") || lower.contains("corp")
                    || lower.contains("\u8fd0\u8425\u5546"))) {
                isp = col;
            }
        }
        // citycode 属可选列，不影响“有效归属地表”的判定
        if (phone != null && (province != null || city != null || isp != null)) {
            return new String[]{phone, province, city, isp, citycode};
        }
        return null;
    }

    private void copyFile(File from, File to) throws Exception {
        InputStream in = new FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.close();
        in.close();
    }

    private void toastMessage(int resId) {
        if (toast == null) {
            toast = Toast.makeText(getActivity(), resId, Toast.LENGTH_SHORT);
        } else {
            toast.setText(resId);
        }
        toast.show();
    }

    private void showSeekBarDialog(int keyId, final String bundleKey, int defaultValue,
                                   int max, int title, int textRes) {
        final String key = getString(keyId);
        int value = sharedPrefs.getInt(key, defaultValue);
        AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(title));
        View layout = View.inflate(getActivity(), R.layout.dialog_seek, null);
        builder.setView(layout);

        final SeekBarCompat seekBar = (SeekBarCompat) layout.findViewById(R.id.seek_bar);
        seekBar.setMax(max);
        seekBar.setProgress(value);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress == 0) {
                    progress = 1;
                }
                mWindow.sendData(bundleKey, progress, Window.Type.SETTING);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int value = seekBar.getProgress();
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putInt(key, value);
                editor.apply();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mWindow.closeWindow();
            }
        });
        builder.show();

        mWindow.showTextWindow(textRes, Window.Type.SETTING);
    }


    private void showRadioDialog(int keyId, int title, int listId, int defValue) {
        showRadioDialog(keyId, title, listId, defValue, 0);
    }

    private void showRadioDialog(int keyId, int title, int listId, int defValue,
                                 final int offset) {
        final String key = getString(keyId);
        final List<String> list = Arrays.asList(getResources().getStringArray(listId));
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(title));
        View layout = View.inflate(getActivity(), R.layout.dialog_radio, null);
        builder.setView(layout);
        final AlertDialog dialog = builder.create();

        final RadioGroup radioGroup = (RadioGroup) layout.findViewById(R.id.radio);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        for (String s : list) {
            RadioButton radioButton = new RadioButton(getActivity());
            radioButton.setText(s);
            radioGroup.addView(radioButton, layoutParams);
        }

        RadioButton button =
                ((RadioButton) radioGroup.getChildAt(
                        sharedPrefs.getInt(key, defValue) - offset));
        button.setChecked(true);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int index = group.indexOfChild(group.findViewById(checkedId));
                Preference preference = findPreference(key);
                if (preference != null) {
                    preference.setSummary(list.get(index));
                }
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putInt(key, index + offset);
                editor.apply();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    private void showTextDialog(int title, int text) {
        showTextDialog(title, getString(text));
    }

    private void showTextDialog(int title, String text) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(title));
        View layout = View.inflate(getActivity(), R.layout.dialog_text, null);
        builder.setView(layout);

        TextView textView = (TextView) layout.findViewById(R.id.text);
        textView.setText(text);

        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }

    private void showConfirmDialog(int title, int text, final int key) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(title));
        View layout = View.inflate(getActivity(), R.layout.dialog_text, null);
        builder.setView(layout);

        TextView textView = (TextView) layout.findViewById(R.id.text);
        textView.setText(getString(text));
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onConfirmCanceled(key);
            }
        });
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onConfirmed(key);
            }
        });
        builder.show();
    }

    @SuppressWarnings("SameParameterValue")
    private void showEditDialog(int keyId, int title, final int defaultText, int hint,
                                final int help, final int helpText) {
        final String key = getString(keyId);
        AlertDialog.Builder builder =
                new AlertDialog.Builder(getActivity());
        builder.setTitle(getString(title));
        View layout = View.inflate(getActivity(), R.layout.dialog_edit, null);
        builder.setView(layout);

        final EditText editText = (EditText) layout.findViewById(R.id.text);
        editText.setText(sharedPrefs.getString(key, getString(defaultText)));
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        if (hint > 0) {
            editText.setHint(hint);
        }

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String value = editText.getText().toString();
                if (value.isEmpty()) {
                    value = getString(defaultText);
                }
                findPreference(key).setSummary(value);
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(key, value);
                editor.apply();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);

        if (help != 0) {
            builder.setNeutralButton(help, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showTextDialog(help, helpText);
                }
            });
        }

        builder.setCancelable(true);
        builder.show();
    }

    @SuppressWarnings("SameParameterValue")
    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getActivity().getResources().getDisplayMetrics());
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        int keyId = getKeyId(preference.getKey());
        switch (keyId) {
            case R.string.window_text_size_key:
                showSeekBarDialog(R.string.window_text_size_key, FloatWindow.TEXT_SIZE, 20, 60,
                        R.string.window_text_size, R.string.text_size);
                break;
            case R.string.window_height_key:
                showSeekBarDialog(R.string.window_height_key, FloatWindow.WINDOW_HEIGHT,
                        mPoint.y / 8, mPoint.y / 4, R.string.window_height,
                        R.string.window_height_message);
                break;
            case R.string.window_text_alignment_key:
                showRadioDialog(R.string.window_text_alignment_key,
                        R.string.window_text_alignment, R.array.align_type, 1);
                break;
            case R.string.window_transparent_key:
                showSeekBarDialog(R.string.window_transparent_key, FloatWindow.WINDOW_TRANS, 80,
                        100, R.string.window_transparent, R.string.text_transparent);
                break;
            case R.string.window_text_padding_key:
                showSeekBarDialog(R.string.window_text_padding_key, FloatWindow.TEXT_PADDING, 0,
                        mPoint.x / 2,
                        R.string.window_text_padding, R.string.text_padding);
                break;
            case R.string.ignore_known_contact_key:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int res = getActivity().checkSelfPermission(
                            Manifest.permission.READ_CONTACTS);
                    if (res != PackageManager.PERMISSION_GRANTED) {
                        // Request READ_CONTACTS via the Activity Result API. Remember
                        // which switch triggered it so the callback can un-check it on
                        // denial.
                        mPendingContactsKey = keyId;
                        ActivityResultLauncher<String> launcher =
                                ((SettingsActivity) getActivity())
                                        .getContactsPermissionLauncher();
                        launcher.launch(Manifest.permission.READ_CONTACTS);
                        return true;
                    }
                }
                return false;
            case R.string.display_on_outgoing_key:
                // 去电显示仅依赖 PHONE_STATE→OFFHOOK（动态 receiver 已监听），无需 PROCESS_OUTGOING_CALLS
                // （signature 级权限普通应用申请必失败）。允许直接勾选，靠 OFFHOOK 触发去电浮窗。
                return false;
            case R.string.custom_db_import_key:
                Intent importIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                importIntent.addCategory(Intent.CATEGORY_OPENABLE);
                importIntent.setType("*/*");
                startActivityForResult(importIntent, REQ_IMPORT_DB);
                return true;
            case R.string.ignore_regex_key:
                showEditDialog(R.string.ignore_regex_key, R.string.ignore_regex,
                        R.string.empty_string,
                        R.string.ignore_regex_hint, R.string.example, R.string.regex_example);
                return false;
            case R.string.version_key:
                return false;
            case R.string.ignore_battery_optimizations_key:
                showConfirmDialog(R.string.ignore_battery_optimizations,
                        R.string.ignore_battery_optimizations_description,
                        R.string.ignore_battery_optimizations_key);
                return false;
            case R.string.enable_marking_key:
                if (sharedPrefs.getBoolean(getString(R.string.enable_marking_key), false)) {
                    showConfirmDialog(R.string.enable_marking, R.string.mark_confirm,
                            R.string.enable_marking_key);
                }
                return false;
            case R.string.outgoing_window_position_key:
                if (sharedPrefs.getBoolean(getString(R.string.outgoing_window_position_key),
                        false)) {
                    showTextDialog(R.string.outgoing_window_position,
                            R.string.outgoing_window_position_message);
                }
                break;
        }

        return true;
    }

    private void onConfirmed(int key) {
        switch (key) {
            case R.string.ignore_battery_optimizations_key:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent intent = new Intent(
                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                }
                break;
        }
    }

    private void onConfirmCanceled(int key) {
        switch (key) {
            case R.string.enable_marking_key:
                ((SwitchPreference) findPreference(
                        getString(R.string.enable_marking_key))).setChecked(false);
                break;
            case R.string.ignore_battery_optimizations_key:
                ((SwitchPreference) findPreference(
                        getString(R.string.ignore_battery_optimizations_key))).setChecked(
                        Utils.ignoreBatteryOptimization(getActivity()));
                break;
        }
    }

    private void bindPreference(int keyId) {
        bindPreference(keyId, SUMMARY_FLAG_NULL, 0);
    }

    private void bindPreference(int keyId, boolean mask) {
        if (mask) {
            bindPreference(keyId, SUMMARY_FLAG_NORMAL | SUMMARY_FLAG_MASK, 0);
        } else {
            bindPreference(keyId, SUMMARY_FLAG_NORMAL, 0);
        }
    }

    private void bindPreference(int keyId, int summaryId) {
        bindPreference(keyId, SUMMARY_FLAG_NORMAL, summaryId);
    }

    private void bindPreferenceList(int keyId, int arrayId, int index) {
        bindPreferenceList(keyId, arrayId, index, 0);
    }

    private void bindPreferenceList(int keyId, int arrayId, int defValue, int offset) {
        String key = getString(keyId);
        Preference preference = findPreference(key);
        List<String> apiList = Arrays.asList(getResources().getStringArray(arrayId));
        preference.setOnPreferenceClickListener(this);
        preference.setSummary(apiList.get(sharedPrefs.getInt(key, defValue) - offset));
        keyMap.put(key, keyId);
        prefMap.put(key, preference);
    }

    private void bindPreference(int keyId, int summaryFlags, int summaryId) {
        String key = getString(keyId);
        Preference preference = findPreference(key);
        preference.setOnPreferenceClickListener(this);

        if ((summaryFlags & SUMMARY_FLAG_NORMAL) == SUMMARY_FLAG_NORMAL) {
            String defaultSummary = summaryId == 0 ? "" : getString(summaryId);
            String summary = sharedPrefs.getString(key, defaultSummary);

            if (summary.isEmpty() && !defaultSummary.isEmpty()) {
                summary = defaultSummary;
            }

            boolean mask = ((summaryFlags & SUMMARY_FLAG_MASK) == SUMMARY_FLAG_MASK);
            preference.setSummary(mask ? mask(summary) : summary);
        }
        keyMap.put(key, keyId);
        prefMap.put(key, preference);
    }

    private int getKeyId(String key) {
        return keyMap.get(key);
    }

    private void setChecked(int key, boolean checked) {
        SwitchPreference preference = (SwitchPreference) findPreference(getString(key));
        preference.setChecked(checked);
    }

}

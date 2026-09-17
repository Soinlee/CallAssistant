package com.github.soinlee.callassistant.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ListView;

import com.github.soinlee.callassistant.R;
import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.model.database.Database;
import com.github.soinlee.callassistant.data.CallerRepository;
import com.github.soinlee.callassistant.model.db.MarkedRecord;
import com.github.soinlee.callassistant.model.setting.Setting;
import com.github.soinlee.callassistant.utils.Alarm;

import java.util.ArrayList;

import javax.inject.Inject;

public class MarkActivity extends BaseActivity implements DialogInterface.OnDismissListener {
    public static final String NUMBER = "number";
    private static final String TAG = MarkActivity.class.getSimpleName();
    boolean isPaused = false;

    @Inject
    Setting mSetting;

    @Inject
    Database mDatabase;

    @Inject
    Alarm mAlarm;

    private AlertDialog mAlertDialog;
    private ArrayList<String> mNumberList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("");

        Application.getApplication().getAppComponent().inject(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPaused = false;
        Intent intent = getIntent();
        String number = intent.getStringExtra(NUMBER);

        if (!TextUtils.isEmpty(number)) {
            // Normalize the stored key before persisting so the mark can be
            // matched by the main list (same key as offline attribution).
            // The toolkit's +86/0086/international rules stay untouched.
            showAlertDialog(CallerRepository.fixNumber(number));
        } else {
            mNumberList = mSetting.getPaddingMarks();
            if (mNumberList.size() > 0) {
                showAlertDialog(mNumberList.get(0));
            } else {
                Log.e(TAG, "number is null or empty! " + number);
                finish();
            }
        }
    }

    private void showAlertDialog(final String number) {
        String title = getString(R.string.mark_number) + " (" + number + ")";
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MarkDialogStyle);
        builder.setTitle(title);
        builder.setOnDismissListener(this);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setCancelable(false);
        builder.setSingleChoiceItems(R.array.mark_type, -1,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE)
                                .setEnabled(true);
                    }
                });
        builder.setNeutralButton(R.string.ignore, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // ignore number
                MarkedRecord markedRecord = new MarkedRecord();
                markedRecord.setUid(mSetting.getUid());
                markedRecord.setNumber(number);
                markedRecord.setType(MarkedRecord.TYPE_IGNORE);
                markedRecord.setTypeName(getString(R.string.ignore_number));
                markedRecord.setReported(true);
                mDatabase.saveMarked(markedRecord);
                mDatabase.updateCaller(markedRecord);
            }
        });
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ListView lv = ((AlertDialog) dialog).getListView();
                String type = (String) lv.getAdapter().getItem(lv.getCheckedItemPosition());
                MarkedRecord markedRecord = new MarkedRecord();
                markedRecord.setUid(mSetting.getUid());
                markedRecord.setNumber(number);
                markedRecord.setType(lv.getCheckedItemPosition());
                markedRecord.setTypeName(type);
                mDatabase.saveMarked(markedRecord);
                mDatabase.updateCaller(markedRecord);
                mAlarm.alarm();
            }
        });
        mAlertDialog = builder.create();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAlertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            mAlertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        }
        mAlertDialog.show();
        mAlertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPaused = true;
        if (mAlertDialog != null) {
            mAlertDialog.cancel();
        }
        finish();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_mark;
    }

    @Override
    protected int getTitleId() {
        return R.string.app_name;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {

        if (mNumberList != null && mNumberList.size() > 0) {
            mSetting.removePaddingMark(mNumberList.get(0));
            mNumberList.remove(0);
            if (mNumberList.size() > 0) {
                showAlertDialog(mNumberList.get(0));
                return;
            }
        }

        if (!isPaused && (mNumberList == null || mNumberList.size() == 0)) {
            finish();
        }
    }
}

package com.github.soinlee.callassistant.presenter;

import android.util.Log;

import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.contract.MainBottomContact;
import com.github.soinlee.callassistant.data.CallerDataSource;
import com.github.soinlee.callassistant.data.CallerRepository;
import com.github.soinlee.callassistant.model.database.Database;
import com.github.soinlee.callassistant.model.db.Caller;
import com.github.soinlee.callassistant.model.db.InCall;
import com.github.soinlee.callassistant.model.db.MarkedRecord;
import com.github.soinlee.callassistant.model.setting.Setting;
import com.github.soinlee.callassistant.utils.Alarm;
import com.github.soinlee.callassistant.utils.Utils;

import javax.inject.Inject;

public class MainBottomPresenter implements MainBottomContact.Presenter {

    private static final String TAG = MainBottomPresenter.class.getSimpleName();

    @Inject
    Setting mSetting;
    @Inject
    CallerDataSource mCallerDataSource;
    @Inject
    Database mDatabase;
    @Inject
    Alarm mAlarm;

    private InCall mInCall;
    private Caller mCaller;

    private MainBottomContact.View mView;

    public MainBottomPresenter(MainBottomContact.View view) {
        mView = view;
        Application.getApplication().getAppComponent().inject(this);
    }

    @Override
    public void start() {
        if (mInCall != null) {
            mView.init(mInCall, mCaller);
        } else {
            Log.e(TAG, "mInCall is null");
        }
    }

    @Override
    public void bindData(InCall inCall) {
        mInCall = inCall;
        mCaller = mCallerDataSource.getCallerFromCache(inCall.getNumber());
    }

    @Override
    public boolean canMark() {
        return mCaller.isMark() || mCaller.canMark() && mInCall.getDuration() > 0;
    }

    @Override
    public void markClicked(int viewId) {
        MarkedRecord.MarkType type = MarkedRecord.MarkType.fromResourceId(viewId);

        if (type != MarkedRecord.MarkType.CUSTOM) {
            String typeText = Utils.typeFromId(type.toInt());
            // Persist under the normalized key (same as offline attribution /
            // main list) so the mark shows up after the list refresh.
            mCallerDataSource.updateCaller(
                    CallerRepository.fixNumber(mInCall.getNumber()), type.toInt(), typeText);
            mView.updateMarkName(typeText);
        }

        mView.updateMark(viewId, mCaller);
    }

    @Override
    public void markCustom(String text) {
        mCallerDataSource.updateCaller(
                CallerRepository.fixNumber(mInCall.getNumber()),
                MarkedRecord.MarkType.CUSTOM.toInt(), text);
        mView.updateMarkName(text);
    }

    @Override
    public void clearMark() {
        // Remove the mark so the number reverts to offline attribution; the
        // data-source triggers a list refresh via onDataUpdate.
        mCallerDataSource.clearMark(mInCall.getNumber());
        mView.updateMarkName(null);
    }
}

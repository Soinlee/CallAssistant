package com.github.soinlee.callassistant.feature.callmonitor;

import android.text.TextUtils;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.model.CallRecord;
import com.github.soinlee.callassistant.model.db.Caller;
import com.github.soinlee.callassistant.model.setting.Setting;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * ViewModel for the incoming-call monitor feature.
 *
 * Responsibilities (separated from the View and the platform):
 *  <ul>
 *    <li>Own the {@link CallRecord} call-state machine (ringing / off-hook / idle).</li>
 *    <li>Drive offline caller lookup through the {@link CallQueryRepository}.</li>
 *    <li>Expose the floating-window presentation state via {@link #getFloatWindowState()}
 *        as immutable {@link FloatWindowUiState} objects.</li>
 *    <li>Emit context-free {@link CallAction} side effects (save log, show mark, ...)
 *        via {@link #getCallAction()} for the View layer to perform.</li>
 *  </ul>
 *
 * The ViewModel is deliberately free of any {@code Context} / platform dependency so it
 * can be unit-tested in isolation.
 */
public class CallMonitorViewModel extends ViewModel {

    private static final String TAG = CallMonitorViewModel.class.getSimpleName();

    @Inject
    CallQueryRepository mRepository;

    @Inject
    Setting mSetting;

    private final CallRecord mCallRecord = new CallRecord();
    private String mIncomingNumber;

    /**
     * Monotonically increasing sequence used to invalidate in-flight lookups. Each
     * new query (and each call reset) bumps this counter; when an asynchronous
     * result arrives it is applied only if it still matches the latest sequence,
     * otherwise it is a stale result from a superseded call and must be dropped
     * (prevents a slow earlier number from overwriting the current call's window).
     */
    private int mQuerySeq;

    private final MutableLiveData<FloatWindowUiState> mFloatWindowState =
            new MutableLiveData<>(FloatWindowUiState.idle());
    private final MutableLiveData<CallAction> mCallAction = new MutableLiveData<>();
    private CallAction mPendingAction;

    public CallMonitorViewModel() {
        Application.getApplication().getAppComponent().inject(this);
    }

    /** Injectable constructor used by the factory / tests. */
    CallMonitorViewModel(CallQueryRepository repository, Setting setting) {
        mRepository = repository;
        mSetting = setting;
    }

    public LiveData<FloatWindowUiState> getFloatWindowState() {
        return mFloatWindowState;
    }

    public LiveData<CallAction> getCallAction() {
        return mCallAction;
    }

    /** Current (synchronous) state, used by the broadcast path that has no Lifecycle owner. */
    public FloatWindowUiState getCurrentState() {
        return mFloatWindowState.getValue();
    }

    /** Returns and clears the latest pending side-effect action. */
    public CallAction consumePendingAction() {
        CallAction action = mPendingAction;
        mPendingAction = null;
        return action;
    }

    public boolean matchIgnore(String number) {
        if (!TextUtils.isEmpty(number)) {
            String ignoreRegex = mSetting.getIgnoreRegex();
            return number.matches(ignoreRegex);
        }
        return false;
    }

    public void setOutGoingNumber(String number) {
        mIncomingNumber = number;
    }

    public void handleRinging(String number) {
        mCallRecord.ring();

        if (!TextUtils.isEmpty(number)) {
            mIncomingNumber = number;
            mCallRecord.setLogNumber(number);
            searchNumber(number);
        }
    }

    public void handleOffHook(String number) {
        if (System.currentTimeMillis() - mCallRecord.getHook() < 1000
                && mCallRecord.isEqual(number)) {
            Log.e(TAG, "duplicate hook, ignore.");
            return;
        }

        mCallRecord.hook();
        if (mCallRecord.isIncoming()) {
            if (mSetting.isHidingOffHook()) {
                mFloatWindowState.setValue(FloatWindowUiState.hidden());
            }
        } else {
            // outgoing call
            if (mSetting.isShowingOnOutgoing()) {
                if (TextUtils.isEmpty(number)) {
                    number = mIncomingNumber;
                    mCallRecord.setLogNumber(number);
                    mIncomingNumber = null;
                }
                if (!TextUtils.isEmpty(number)) {
                    searchNumber(number);
                }
            }
        }
    }

    public void handleIdle(String number) {
        mCallRecord.idle();

        if (checkClose(number)) {
            return;
        }

        if (isIncoming(mIncomingNumber) && !mRepository.isIgnoreContact(mIncomingNumber)) {
            emitCallAction(CallAction.saveInCall(mIncomingNumber,
                    mCallRecord.time(),
                    mCallRecord.ringDuration(),
                    mCallRecord.callDuration()));
            mIncomingNumber = null;
        }

        FloatWindowUiState current = mFloatWindowState.getValue();
        boolean windowActive = current != null
                && current.getType() != FloatWindowUiState.TYPE_CLOSED
                && current.getType() != FloatWindowUiState.TYPE_IDLE;

        if (windowActive) {
            if (mCallRecord.isValid()) {
                if (!mCallRecord.isNameValid()
                        && mSetting.isMarkingEnabled() && mCallRecord.isAnswered()
                        && !mRepository.isIgnoreContact(mCallRecord.getLogNumber())) {
                    emitCallAction(CallAction.showMark(mCallRecord.getLogNumber()));
                }
            }
        }

        resetCallRecord();
        mFloatWindowState.setValue(FloatWindowUiState.closed());
    }

    public void resetCallRecord() {
        // Bump the sequence so any in-flight async lookup from the previous call
        // is now considered stale and its (late) result gets dropped.
        mQuerySeq++;
        mCallRecord.reset();
    }

    public boolean checkClose(String number) {
        return TextUtils.isEmpty(number) && mCallRecord.callDuration() == -1;
    }

    public boolean isIncoming(String number) {
        return mCallRecord.isIncoming() && !TextUtils.isEmpty(number);
    }

    public boolean isRingOnce() {
        return mCallRecord.ringDuration() < 3000 && mCallRecord.callDuration() <= 0;
    }

    private void searchNumber(final String number) {
        if (TextUtils.isEmpty(number)) {
            Log.e(TAG, "searchNumber: number is null!");
            return;
        }

        int mode = mRepository.searchModeFor(number);
        if (mode == CallQueryRepository.MODE_IGNORE) {
            return;
        }

        mFloatWindowState.setValue(FloatWindowUiState.searching());
        resolveOffline(number);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored") // single-shot lookup, no composite
    private void resolveOffline(final String number) {
        final boolean active = mCallRecord.isActive();
        if (!active) {
            return;
        }

        // Really asynchronous lookup: query runs on the RxJava IO scheduler and the
        // result is delivered on the main thread. This removes the previous
        // blocking model (single-thread executor + CountDownLatch awaiting a
        // main-thread callback), which stalled the query thread and queued up
        // subsequent calls, causing the intermittent multi-second delays.
        final int queryId = ++mQuerySeq;
        Observable.fromCallable(() -> mRepository.queryOffline(number))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        caller -> onOfflineResult(queryId, number, caller),
                        t -> Log.e(TAG, "resolveOffline failed for " + number, t));
    }

    /**
     * Applies a finished offline lookup result on the main thread, guarded by the
     * query sequence so stale results (from a superseded or already-finished call)
     * never overwrite the current call's window.
     */
    private void onOfflineResult(int queryId, String number, Caller caller) {
        if (queryId != mQuerySeq || !mCallRecord.isActive()) {
            Log.d(TAG, "onOfflineResult: stale/done, drop result for " + number);
            // A newer call/query superseded this one, or the call already ended.
            return;
        }
        Log.d(TAG, "onOfflineResult: " + number + " -> "
                + (caller != null ? caller.getName() : "null"));
        if (caller != null && !caller.isEmpty()) {
            mCallRecord.setLogNumber(caller.getNumber());
            mCallRecord.setLogName(caller.getName());
            mCallRecord.setLogGeo(caller.getProvince() + " " + caller.getCity());
            if (mCallRecord.isActive()) {
                mFloatWindowState.setValue(FloatWindowUiState.showing(caller));
            }
        } else if (mCallRecord.isActive()) {
            mFloatWindowState.setValue(FloatWindowUiState.error(false));
        }
    }

    private void emitCallAction(CallAction action) {
        mPendingAction = action;
        mCallAction.setValue(action);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mFloatWindowState.setValue(FloatWindowUiState.closed());
    }
}

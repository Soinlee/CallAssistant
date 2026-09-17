package com.github.soinlee.callassistant.feature.callmonitor;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.github.soinlee.callassistant.R;
import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.service.FloatWindow;
import com.github.soinlee.callassistant.utils.Utils;
import com.github.soinlee.callassistant.utils.Window;

import javax.inject.Inject;

/**
 * The MVVM **View** for the call-monitor feature.
 *
 * A thin adapter that feeds platform call events into the {@link CallMonitorViewModel}
 * and *reactively observes* the ViewModel's {@link FloatWindowUiState} / {@link CallAction}
 * to drive the legacy {@link Window}/{@link FloatWindow} API, together with the platform
 * side effects (plugin-service call-log updates, the "mark" flow).
 *
 * Both the ViewModel and this View are app-scoped (they must survive individual
 * activities and are driven from {@code BroadcastReceiver} / {@code Service} contexts
 * that have no {@code LifecycleOwner}), so the observers are registered with
 * {@code observeForever}. A single View instance follows the whole process lifetime.
 */
public class CallMonitorView {

    private static final String TAG = CallMonitorView.class.getSimpleName();

    private static final String STATE_RINGING = "RINGING";
    private static final String STATE_OFFHOOK = "OFFHOOK";
    private static final String STATE_IDLE = "IDLE";

    @Inject
    Window mWindow;

    private final CallMonitorViewModel mViewModel;
    private Context mContext;

    private final Observer<FloatWindowUiState> mStateObserver = this::render;
    private final Observer<CallAction> mActionObserver = this::handleAction;

    public CallMonitorView(@NonNull Context context) {
        mContext = context.getApplicationContext();
        Application.getApplication().getAppComponent().inject(this);
        mViewModel = Application.getApplication()
                .getAppScopedViewModel(CallMonitorViewModel.class);
        mViewModel.getFloatWindowState().observeForever(mStateObserver);
        mViewModel.getCallAction().observeForever(mActionObserver);
    }

    public void setContext(Context context) {
        mContext = context.getApplicationContext();
    }

    public boolean canReadPhoneState() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || mContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean matchIgnore(String number) {
        return mViewModel.matchIgnore(number);
    }

    public void setOutGoingNumber(String number) {
        mViewModel.setOutGoingNumber(number);
    }

    public boolean isShowing() {
        return mWindow.isShowing();
    }

    public void onCallStateChanged(int state, String number) {
        switch (state) {
            case TelephonyManager.CALL_STATE_RINGING:
                onCallStateChanged(STATE_RINGING, number);
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                onCallStateChanged(STATE_OFFHOOK, number);
                break;
            case TelephonyManager.CALL_STATE_IDLE:
                onCallStateChanged(STATE_IDLE, number);
                break;
            default:
                break;
        }
    }

    public void onCallStateChanged(String state, String number) {
        if (!canReadPhoneState()) {
            Log.d(TAG, "onCallStateChanged: no READ_PHONE_STATE permission");
            return;
        }
        if (matchIgnore(number)) {
            return;
        }
        switch (state) {
            case STATE_RINGING:
                mViewModel.handleRinging(number);
                break;
            case STATE_OFFHOOK:
                mViewModel.handleOffHook(number);
                break;
            case STATE_IDLE:
                mViewModel.handleIdle(number);
                break;
            default:
                break;
        }
    }

    private void render(FloatWindowUiState state) {
        if (state == null || mContext == null) {
            return;
        }
        switch (state.getType()) {
            case FloatWindowUiState.TYPE_SEARCHING:
                mWindow.showTextWindow(R.string.searching, Window.Type.CALLER);
                break;
            case FloatWindowUiState.TYPE_SHOWING:
                if (state.getCaller() != null) {
                    mWindow.showWindow(state.getCaller(), Window.Type.CALLER);
                }
                break;
            case FloatWindowUiState.TYPE_ERROR:
                if (state.isOnline()) {
                    mWindow.sendData(FloatWindow.WINDOW_ERROR,
                            R.string.online_failed, Window.Type.CALLER);
                } else {
                    mWindow.showTextWindow(R.string.offline_failed, Window.Type.CALLER);
                }
                break;
            case FloatWindowUiState.TYPE_HIDDEN:
                mWindow.hideWindow();
                break;
            case FloatWindowUiState.TYPE_CLOSED:
                mWindow.closeWindow();
                break;
            case FloatWindowUiState.TYPE_IDLE:
            default:
                break;
        }
    }

    private void handleAction(CallAction action) {
        if (action == null) {
            return;
        }
        switch (action.getType()) {
            case CallAction.TYPE_SHOW_MARK:
                showMark(action.getNumber());
                break;
            case CallAction.TYPE_SAVE_IN_CALL:
            default:
                break;
        }
    }

    private void showMark(@Nullable String number) {
        if (mContext == null || number == null) {
            return;
        }
        KeyguardManager keyguardManager =
                (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        boolean isKeyguardLocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                && keyguardManager != null && keyguardManager.isKeyguardLocked();

        if (isKeyguardLocked) {
            Utils.showMarkNotification(mContext, number);
        } else {
            Utils.startMarkActivity(mContext, number);
        }
    }
}

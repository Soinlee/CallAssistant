package com.github.soinlee.callassistant.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.github.soinlee.callassistant.BuildConfig;
import com.github.soinlee.callassistant.feature.callmonitor.CallMonitorView;
import com.github.soinlee.callassistant.service.FloatWindow;
import com.github.soinlee.callassistant.utils.Utils;

/**
 * Thin platform adapter that bridges system telephony broadcasts into the MVVM
 * call-monitor stack.
 *
 * The receiver contains no business logic: it normalises the intent extras into
 * phone-state events and hands them to the {@link CallMonitorView} (the MVVM View),
 * which in turn drives the {@code CallMonitorViewModel}. All business decisions live
 * in the ViewModel / Repository layers.
 */
public class IncomingCall extends BroadcastReceiver {

    private final static String TAG = IncomingCall.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        CallMonitorView view = CallMonitorViewHolder.get(context);

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onReceive: " + intent.toString() + " " +
                    Utils.bundleToString(intent.getExtras()));
        }

        String action = intent.getAction();
        if (action == null) {
            return;
        }

        switch (action) {
            case Intent.ACTION_NEW_OUTGOING_CALL:
                if (intent.getExtras() != null) {
                    String number = intent.getExtras().getString(Intent.EXTRA_PHONE_NUMBER);
                    view.setOutGoingNumber(number);
                    view.onCallStateChanged(TelephonyManager.EXTRA_STATE_OFFHOOK, number);
                }
                break;
            case TelephonyManager.ACTION_PHONE_STATE_CHANGED:
                if (intent.getExtras() != null) {
                    String state = intent.getExtras().getString(TelephonyManager.EXTRA_STATE);
                    String number = intent.getExtras()
                            .getString(TelephonyManager.EXTRA_INCOMING_NUMBER);
                    view.onCallStateChanged(state, number);
                }
                break;
            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
                // Boot completed / quick boot: try to re-start the persistent
                // foreground service so the caller-info floating window feature is
                // ready. On Android 15+ a background broadcast (BOOT_COMPLETED has no
                // user-visible component) is NOT allowed to cold-start a foreground
                // service, which would throw ForegroundServiceStartNotAllowedException
                // (an IllegalStateException). If the service is already resident
                // (isOngoing()) there is nothing to do; otherwise we degrade gracefully
                // to a plain startService and only log on failure instead of crashing.
                if (FloatWindow.isOngoing()) {
                    Log.d(TAG, "boot: FloatWindow already ongoing, skip restart");
                    break;
                }
                try {
                    FloatWindow.start(context);
                } catch (IllegalStateException | SecurityException e) {
                    // ForegroundServiceStartNotAllowedException is a subclass of
                    // IllegalStateException; cold-start from a background broadcast is
                    // forbidden on Android 15+. Degrade to a plain startService and log
                    // without crashing. The service will be (re)started as a proper FGS
                    // next time the user opens the app (MainActivity.onStart).
                    Log.e(TAG, "boot: FGS cold start not allowed, degrade to startService", e);
                    try {
                        context.startService(
                                wei.mark.standout.StandOutWindow.getStartIntent(
                                        context, FloatWindow.class));
                    } catch (Exception e2) {
                        Log.e(TAG, "boot: degraded startService failed", e2);
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * App-scoped singleton holder for the MVVM View. The View/ViewModel are long-lived
     * (see {@link CallMonitorView}) so they are created once and reused for every
     * subsequent broadcast.
     */
    private static final class CallMonitorViewHolder {
        private static CallMonitorView sView;

        static CallMonitorView get(Context context) {
            if (sView == null) {
                sView = new CallMonitorView(context);
            } else {
                sView.setContext(context);
            }
            return sView;
        }
    }
}

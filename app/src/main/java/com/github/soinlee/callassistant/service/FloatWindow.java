package com.github.soinlee.callassistant.service;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.github.soinlee.callassistant.R;
import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.model.setting.Setting;
import com.github.soinlee.callassistant.utils.Utils;

import javax.inject.Inject;

import wei.mark.standout.StandOutWindow;
import wei.mark.standout.constants.StandOutFlags;
import wei.mark.standout.ui.Window;

public class FloatWindow extends StandOutWindow {

    public final static String TAG = FloatWindow.class.getSimpleName();

    public final static String NUMBER_INFO = "number_info";
    public final static String TEXT_SIZE = "text_size";
    public final static String TEXT_PADDING = "text_padding";
    public final static String WINDOW_HEIGHT = "window_height";
    public final static String WINDOW_TRANS = "window_trans";
    public final static String WINDOW_COLOR = "window_color";
    public final static String WINDOW_ERROR = "window_error";
    public final static int CALLER_FRONT = 1000;
    public final static int SET_POSITION_FRONT = 1001;
    public final static int SETTING_FRONT = 1002;
    public final static int SEARCH_FRONT = 1003;
    public final static int STATUS_CLOSE = 0;
    public final static int TEXT_ALIGN_LEFT = 0;
    public final static int TEXT_ALIGN_CENTER = 1;
    public final static int TEXT_ALIGN_RIGHT = 2;
    private final static int STATUS_SHOWING = 1;
    private final static int STATUS_HIDE = 2;
    private static int mShowingStatus = STATUS_CLOSE;

    @Inject
    Setting mSettings;

    private boolean isFirstShow = false;
    private boolean isFocused = false;

    /**
     * Whether this service was promoted to a persistent foreground service by the
     * {@code ACTION_START} command (i.e. {@link #start(Context)} was used while the
     * app was in the foreground). While set, call events must NEVER cold-start the
     * foreground service again - they only need to show a window inside the already
     * running service (Android 15 forbids starting an FGS from a background
     * broadcast).
     *
     * <p>Process-wide volatile flag (not a per-instance field). Previously the
     * caller decision relied on the unreliable {@link ActivityManager#getRunningServices(int)}
     * check, which could return {@code false} right after a process crash/restart
     * even though the service is about to come back up - that caused a background
     * {@code startForegroundService()} cold-start and a
     * {@link android.app.ForegroundServiceStartNotAllowedException} on Android 15+.
     * Now call events always relay SHOW as a plain {@code startService} and this
     * flag only records the realised resident-foreground state.
     */
    private static volatile boolean sOngoing = false;

    // TODO: move status at utils
    public static int status() {
        return mShowingStatus;
    }

    /**
     * Start the caller-info floating-window service as a persistent, ongoing
     * foreground service (no window is shown yet). This keeps the service resident
     * so incoming/outgoing calls only need to update the window data instead of
     * cold-starting a foreground service from the background (which Android 15
     * restricts). Must be called while the app is in the foreground (e.g. right
     * after the overlay permission is granted in {@code MainActivity}).
     */
    public static void start(Context context) {
        if (!isRunning(context)) {
            StandOutWindow.start(context, FloatWindow.class);
        }
    }

    /**
     * Whether this service was promoted to a persistent, ongoing foreground service
     * (via {@link #start(Context)}) and is therefore already resident. When this is
     * {@code true}, call/boot events must NOT cold-start the foreground service again
     * - they only need to show a window inside the already-running service (Android 15
     * forbids starting an FGS from a background broadcast).
     */
    public static boolean isOngoing() {
        return sOngoing;
    }

    /**
     * Whether the FloatWindow service is currently running.
     */
    private static boolean isRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        for (ActivityManager.RunningServiceInfo service :
                manager.getRunningServices(Integer.MAX_VALUE)) {
            if (FloatWindow.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Show a window inside an already-running {@link FloatWindow} service using a
     * plain {@link Context#startService(Intent)} instead of
     * {@link Context#startForegroundService(Intent)}.
     *
     * <p>The service is already in the foreground (promoted by {@link #start} on
     * app start), so {@code startForegroundService} is unnecessary here and - more
     * importantly - calling it from a background broadcast would throw
     * {@link android.app.ForegroundServiceStartNotAllowedException} on Android 15+.
     * A plain {@code startService} within the short broadcast-receiver window is
     * allowed and simply delivers the {@code ACTION_SHOW} command to the running
     * service.
     */
    public static void showQuietly(Context context,
            Class<? extends StandOutWindow> cls, int id) {
        context.startService(StandOutWindow.getShowIntent(context, cls, id));
    }

    /**
     * Stop the resident foreground service completely (used by the user-facing
     * "close floating window" action). Unlike closing the window, this also tears
     * down the persistent notification. {@code onDestroy()} (via
     * {@code StandOutWindow}) will close any remaining windows.
     */
    public static void stop(Context context) {
        context.stopService(getStartIntent(context, FloatWindow.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Inject first, but never let a cold-start injection failure crash the
        // service. If it crashed here, startForeground() would never be reached and
        // the 5-second ForegroundServiceDidNotStartInTimeException would be thrown.
        try {
            Application.getApplication().getAppComponent().inject(this);
        } catch (Exception e) {
            Log.e(TAG, "inject failed", e);
        }
        // Guarantee startForeground() is reached well within the 5-second window for
        // every command (idempotent if the service is already foreground).
        startForegroundOngoing();

        // Remember that this service was explicitly kept resident as a persistent
        // foreground service (via FloatWindow.start()). Call events then only need
        // to show a window inside this running service instead of cold-starting a
        // foreground service from a background broadcast (Android 15+ restriction).
        if (intent != null && StandOutWindow.ACTION_START.equals(intent.getAction())) {
            sOngoing = true;
        } else if (intent != null && (StandOutWindow.ACTION_SHOW.equals(intent.getAction())
                || StandOutWindow.ACTION_RESTORE.equals(intent.getAction()))) {
            // A SHOW/RESTORE reached this already-running service: it is certainly
            // in the foreground (startForegroundOngoing() above already promoted it
            // if needed), so keep the process-level flag in sync.
            sOngoing = true;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        sOngoing = false;
        super.onDestroy();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Context context = Utils.changeLang(newBase);
        super.attachBaseContext(context);
    }

    @Override
    public String getAppName() {
        return getResources().getString(R.string.app_name);
    }

    @Override
    public int getAppIcon() {
        return R.drawable.status_icon;
    }

    @Override
    public void createAndAttachView(int id, FrameLayout frame) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.float_window, frame, true);
    }

    // the window will be centered
    @Override
    public StandOutLayoutParams getParams(int id, Window window) {

        StandOutLayoutParams params = new StandOutLayoutParams(id, mSettings.getScreenWidth(),
                mSettings.getWindowHeight(), StandOutLayoutParams.CENTER,
                StandOutLayoutParams.CENTER);

        int x = mSettings.getWindowX();
        int y = mSettings.getWindowY();

        if (x != -1 && y != -1) {
            params.x = x;
            params.y = y;
        }

        if (id == SETTING_FRONT || id == SEARCH_FRONT) {
            params.y = (int) (mSettings.getDefaultHeight() * 1.5);
        }

        params.minWidth = mSettings.getScreenWidth();
        params.maxWidth = Math.max(mSettings.getScreenWidth(), mSettings.getScreenHeight());
        params.minHeight = mSettings.getDefaultHeight() / 4;
        if (isUnmovable(id)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params.type = StandOutLayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                params.type = StandOutLayoutParams.TYPE_SYSTEM_OVERLAY;
            }
        }
        return params;
    }

    // move the window by dragging the view
    @Override
    public int getFlags(int id) {
        if (isUnmovable(id)) {
            return super.getFlags(id) | StandOutFlags.FLAG_WINDOW_FOCUSABLE_DISABLE;
        } else {
            return super.getFlags(id) | StandOutFlags.FLAG_BODY_MOVE_ENABLE
                    | StandOutFlags.FLAG_WINDOW_EDGE_LIMITS_ENABLE
                    | StandOutFlags.FLAG_WINDOW_PINCH_RESIZE_ENABLE;
        }
    }

    private boolean isUnmovable(int id) {
        KeyguardManager km = (KeyguardManager) getSystemService(Activity.KEYGUARD_SERVICE);
        return id == SETTING_FRONT || id == SEARCH_FRONT || km.inKeyguardRestrictedInputMode();
    }

    @Override
    public String getPersistentNotificationTitle(int id) {
        return getAppName();
    }

    @Override
    public String getPersistentNotificationMessage(int id) {
        return getString(R.string.close_float_window);
    }

    @Override
    public Intent getPersistentNotificationIntent(int id) {
        return StandOutWindow.getCloseIntent(this, FloatWindow.class, id);
    }

    @Override
    public Animation getCloseAnimation(int id) {
        if (mSettings.isShowCloseAnim()) {
            return super.getCloseAnimation(id);
        } else {
            return null;
        }
    }

    @Override
    public boolean onShow(int id, Window window) {
        isFirstShow = true;
        mShowingStatus = STATUS_SHOWING;
        return super.onShow(id, window);
    }

    @Override
    public void onMove(int id, Window window, View view, MotionEvent event) {
        super.onMove(id, window, view, event);
        mSettings.setWindow(window.getLayoutParams().x, window.getLayoutParams().y);
    }

    @Override
    public boolean onClose(int id, Window window) {
        boolean keepResident = getExistingIds().size() == 0;
        super.onClose(id, window);
        if (keepResident && sOngoing) {
            // Keep the resident service alive. The persistent foreground service is
            // intentionally long-lived; closing the floating window must NOT tear
            // it down, otherwise every incoming/outgoing call would cold-start an
            // FGS from a background broadcast (forbidden on Android 15+).
            //
            // [Architectural trade-off / Task 4] We deliberately keep the service
            // resident rather than stopping it after each call ends. A "FGS only
            // during a call" design would require cold-starting the FGS from a
            // background broadcast on every call, which Android 15+/16 forbids
            // (ForegroundServiceStartNotAllowedException) and risks process reaping;
            // it would also defeat persistent keep-alive entirely. The cost is a
            // long-running specialUse FGS subject to the Android 15+ 6h/24h specialUse
            // time limit and stricter Play review - accepted here because the feature
            // is inherently a persistent floating window, and the BOOT path is
            // hardened (see IncomingCall) to never cold-start an FGS from the
            // background.
            Log.d(TAG, "onClose: keeping persistent service resident");
        } else if (!sOngoing) {
            // Not running as a persistent foreground service: the old behaviour was
            // to stop the service when the last window closed.
            stopService(getShowIntent(this, getClass(), id));
        }
        mShowingStatus = STATUS_CLOSE;
        return false;
    }

    @Override
    public boolean onHide(int id, Window window) {
        mShowingStatus = STATUS_HIDE;
        return super.onHide(id, window);
    }

    @Override
    public boolean onTouchBody(int id, Window window, View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_OUTSIDE:
                View layout = window.findViewById(R.id.window_layout);
                if (layout != null) {
                    layout.setBackgroundResource(0);
                }
                if (!isFocused && mSettings.isHidingWhenTouch() && id == CALLER_FRONT
                        && getWindow(id) != null) {
                    hide(id);
                }
                isFocused = false;
                break;
        }
        return super.onTouchBody(id, window, view, event);
    }

    @Override
    public void onReceiveData(int id, int requestCode, Bundle data,
            Class<? extends StandOutWindow> fromCls, int fromId) {
        int color = data.getInt(WINDOW_COLOR);
        String text = data.getString(NUMBER_INFO);
        int size = data.getInt(TEXT_SIZE);
        int height = data.getInt(WINDOW_HEIGHT);
        int trans = data.getInt(WINDOW_TRANS);
        int error = data.getInt(WINDOW_ERROR);
        int padding = data.getInt(TEXT_PADDING);
        Window window = getWindow(id);

        if (window == null) {
            return;
        }

        View layout = window.findViewById(R.id.content);
        TextView textView = (TextView) window.findViewById(R.id.number_info);
        TextView errorText = (TextView) window.findViewById(R.id.error);

        if (padding == 0) {
            padding = mSettings.getTextPadding();
        }

        if (id == CALLER_FRONT || id == SETTING_FRONT) {
            int alignType = mSettings.getTextAlignment();
            int gravity;
            switch (alignType) {
                case TEXT_ALIGN_LEFT:
                    gravity = Gravity.START | Gravity.CENTER;
                    textView.setPadding(padding, 0, 0, 0);
                    break;
                case TEXT_ALIGN_CENTER:
                    gravity = Gravity.CENTER;
                    textView.setPadding(0, padding, 0, 0);
                    break;
                case TEXT_ALIGN_RIGHT:
                    gravity = Gravity.END | Gravity.CENTER;
                    textView.setPadding(0, 0, padding, 0);
                    break;
                default:
                    gravity = Gravity.CENTER;
                    textView.setPadding(0, padding, 0, 0);
                    break;
            }
            errorText.setGravity(gravity);
            textView.setGravity(gravity);
        }

        if (size == 0) {
            size = mSettings.getTextSize();
        }

        if (height != 0) {
            StandOutLayoutParams params = window.getLayoutParams();
            window.edit().setSize(params.width, height).commit();
        }

        if (trans == 0) {
            trans = mSettings.getWindowTransparent();
        }

        if (color != 0) {
            layout.setBackgroundColor(color);
            if (mSettings.isEnableTextColor() && id == CALLER_FRONT) {
                textView.setTextColor(color);
            }
        }

        if (text != null) {
            textView.setText(text);
        }

        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);

        if (mSettings.isTransBackOnly()) {
            if (layout.getBackground() != null) {
                layout.getBackground().setAlpha((int) (trans / 100.0 * 255));
            }
        } else {
            layout.setAlpha(trans / 100f);
        }

        if (error != 0) {
            errorText.setVisibility(View.VISIBLE);
            errorText.setText(getString(error));
        }
    }

    @Override
    public boolean onFocusChange(int id, Window window, boolean focus) {
        View layout = window.findViewById(R.id.window_layout);
        if (focus && layout != null && !isFirstShow) {
            layout.setBackgroundResource(wei.mark.standout.R.drawable.border_focused);
            isFocused = true;
        }
        isFirstShow = false;
        return true;
    }

    @Override
    public boolean isDisableMove(int id) {
        return id == CALLER_FRONT && mSettings.isDisableMove();
    }
}
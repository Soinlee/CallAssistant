package com.github.soinlee.callassistant.application;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;

import com.github.soinlee.callassistant.di.AppComponent;
import com.github.soinlee.callassistant.di.DaggerAppComponent;
import com.github.soinlee.callassistant.feature.callmonitor.CallMonitorView;
import com.github.soinlee.callassistant.di.modules.AppModule;
import com.github.soinlee.callassistant.model.setting.Setting;
import com.github.soinlee.callassistant.utils.Resource;
import com.github.soinlee.callassistant.utils.Utils;

import javax.inject.Inject;

import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;

public class Application extends android.app.Application {
    public final static String TAG = Application.class.getSimpleName();

    protected AppComponent mAppComponent;

    protected static Application sApplication;

    private final ViewModelStore mAppViewModelStore = new ViewModelStore();

    @Inject
    Setting mSetting;

    public AppComponent getAppComponent() {
        return mAppComponent;
    }

    /**
     * Returns an application-scoped {@link ViewModel}. This is used for background
     * features (the call monitor) that must survive individual activities and are
     * driven from broadcast receivers / services.
     */
    @NonNull
    public <T extends ViewModel> T getAppScopedViewModel(@NonNull Class<T> modelClass) {
        // NewInstanceFactory is enough because CallMonitorViewModel self-injects via Dagger.
        return new ViewModelProvider(mAppViewModelStore,
                new ViewModelProvider.NewInstanceFactory()).get(modelClass);
    }

    public static Application getApplication() {
        return sApplication;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sApplication = this;

        RxJavaPlugins.setErrorHandler(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                Log.e(TAG, Log.getStackTraceString(throwable));
            }
        });

        init();

        registerPhoneReceiver();
    }

    /**
     * Dynamically register the phone-state (incoming / outgoing call) receivers.
     *
     * <p>Static registration is no longer reliable on modern Android:
     * {@code ACTION_NEW_OUTGOING_CALL} is no longer delivered to statically
     * declared receivers since Android 8, and {@code PROCESS_OUTGOING_CALLS} is a
     * restricted permission on Android 11+. Dynamic registration with
     * {@code Context.RECEIVER_EXPORTED} (required on Android 13+) keeps these
     * broadcasts flowing into the call-monitor MVVM stack.
     *
     * <p>The receiver lives with the application process, so it is cleaned up
     * automatically when the process dies (no explicit unregister is needed).
     */
    private void registerPhoneReceiver() {
        final CallMonitorView monitorView = PhoneReceiverViewHolder.get(this);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                String action = intent.getAction();
                if (action == null) {
                    return;
                }

                if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
                    if (intent.getExtras() != null) {
                        String number = intent.getExtras()
                                .getString(Intent.EXTRA_PHONE_NUMBER);
                        monitorView.setOutGoingNumber(number);
                        monitorView.onCallStateChanged(
                                TelephonyManager.EXTRA_STATE_OFFHOOK, number);
                    }
                } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                    if (intent.getExtras() != null) {
                        String state = intent.getExtras()
                                .getString(TelephonyManager.EXTRA_STATE);
                        String number = intent.getExtras()
                                .getString(TelephonyManager.EXTRA_INCOMING_NUMBER);
                        monitorView.onCallStateChanged(state, number);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    /**
     * App-scoped singleton holder for the MVVM View, reused for every broadcast.
     * Mirrors the holder previously used by the statically-registered
     * {@code IncomingCall} receiver.
     */
    private static final class PhoneReceiverViewHolder {
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

    protected void init() {
        mAppComponent = DaggerAppComponent.builder().appModule(new AppModule(this)).build();

        mAppComponent.inject(this);

        Resource.getInstance().init(Utils.changeLang(this));
    }
}

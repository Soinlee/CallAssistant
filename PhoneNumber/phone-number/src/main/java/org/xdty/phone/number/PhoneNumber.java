package org.xdty.phone.number;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import org.xdty.phone.number.model.INumber;
import org.xdty.phone.number.model.NumberHandler;
import org.xdty.phone.number.model.caller.CallerHandler;
import org.xdty.phone.number.model.caller.CallerNumber;
import org.xdty.phone.number.model.common.CommonHandler;
import org.xdty.phone.number.model.google.GoogleNumberHandler;
import org.xdty.phone.number.model.marked.MarkedHandler;
import org.xdty.phone.number.model.mvno.MvnoHandler;
import org.xdty.phone.number.model.offline.OfflineHandler;
import org.xdty.phone.number.model.special.SpecialNumber;
import org.xdty.phone.number.model.special.SpecialNumberHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PhoneNumber {
    private static final String TAG = PhoneNumber.class.getSimpleName();
    private final static String HANDLER_THREAD_NAME = "org.xdty.phone.number";
    private static Context sContext;
    private final Object lockObject = new Object();
    private Callback mCallback;
    private Handler mMainHandler;
    private Handler mHandler;
    private List<NumberHandler> mSupportHandlerList;
    private List<Callback> mCallbackList;

    private PhoneNumber() {
        this(sContext);
    }

    public PhoneNumber(Context context) {
        this(context, null);
    }

    public PhoneNumber(Context context, Callback callback) {
        if (sContext == null) {
            sContext = context.getApplicationContext();
        }

        mCallback = callback;
        mMainHandler = new Handler(sContext.getMainLooper());
        HandlerThread handlerThread = new HandlerThread(HANDLER_THREAD_NAME);
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (lockObject) {
                    addNumberHandler(new SpecialNumberHandler(sContext));
                    addNumberHandler(new CommonHandler(sContext));
                    addNumberHandler(new CallerHandler(sContext));
                    addNumberHandler(new MarkedHandler(sContext));
                    addNumberHandler(new OfflineHandler(sContext));
                    addNumberHandler(new MvnoHandler(sContext));
                    addNumberHandler(new GoogleNumberHandler(sContext));
                }
            }
        });
    }

    public static void init(Context context) {
        sContext = context.getApplicationContext();
    }

    public static PhoneNumber getInstance() {
        if (sContext == null) {
            throw new IllegalStateException("init(Context) has not been called yet.");
        }
        return SingletonHelper.INSTANCE;
    }

    @Deprecated
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void addNumberHandler(NumberHandler handler) {
        if (mSupportHandlerList == null) {
            mSupportHandlerList = Collections.synchronizedList(new ArrayList<NumberHandler>());
        }
        mSupportHandlerList.add(handler);
    }

    public void fetch(String... numbers) {
        for (final String number : numbers) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    final INumber offlineNumber = getOfflineNumber(number);
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (offlineNumber != null && offlineNumber.isValid()) {
                                onResponseOffline(offlineNumber);
                            } else {
                                onResponseFailed(offlineNumber);
                            }
                        }
                    });
                }
            });
        }
    }

    public INumber getOfflineNumber(String number) {
        synchronized (lockObject) {
            INumber iNumber = null;
            for (NumberHandler handler : mSupportHandlerList) {
                INumber i = handler.find(number);
                if (i != null && i.isValid()) {
                    if (i.hasGeo()) {
                        if (iNumber == null) { // return result
                            return i;
                        } else { // patch geo info to previous result
                            iNumber.patch(i);
                            return iNumber;
                        }
                    } else { // continue for geo info
                        iNumber = i;
                    }
                }
            }
            return iNumber;
        }
    }

    void onResponseOffline(INumber number) {
        if (mCallback != null) {
            mCallback.onResponseOffline(number);
        }

        if (mCallbackList != null) {
            final List<Callback> list = mCallbackList;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).onResponseOffline(number);
            }
        }
    }

    void onResponseFailed(INumber number) {
        if (mCallback != null) {
            mCallback.onResponseFailed(number);
        }

        if (mCallbackList != null) {
            final List<Callback> list = mCallbackList;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).onResponseFailed(number);
            }
        }
    }

    public void clear() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.getLooper().quit();
        mCallback = null;
    }

    public void addCallback(Callback callback) {
        if (mCallbackList == null) {
            mCallbackList = new ArrayList<>();
        }
        mCallbackList.add(callback);
    }

    public void removeCallback(Callback callback) {
        if (mCallbackList != null) {
            int i = mCallbackList.indexOf(callback);
            if (i >= 0) {
                mCallbackList.remove(i);
            }
        }
    }

    public interface Callback {
        void onResponseOffline(INumber number);

        void onResponseFailed(INumber number);
    }

    private static class SingletonHelper {
        private final static PhoneNumber INSTANCE = new PhoneNumber();
    }
}

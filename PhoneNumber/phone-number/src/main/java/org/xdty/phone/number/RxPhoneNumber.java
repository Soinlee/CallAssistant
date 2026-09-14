package org.xdty.phone.number;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.xdty.phone.number.model.INumber;
import org.xdty.phone.number.model.NumberHandler;

import java.util.Collection;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.parallel.ParallelFailureHandling;
import io.reactivex.schedulers.Schedulers;

public class RxPhoneNumber {
    private static final String TAG = RxPhoneNumber.class.getSimpleName();
    private Context mContext;
    private SharedPreferences mPref;

    public RxPhoneNumber() {
    }

    public RxPhoneNumber(Context context) {
        init(context);
    }

    public void init(Context context) {
        if (mContext == null) {
            mContext = context.getApplicationContext();
        }

        mPref = provideSharedPreferences();

        NumberProvider.init(mContext);
    }

    public SharedPreferences provideSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public Flowable<INumber> getNumber(final String number) {
        return getOfflineNumber(number);
    }

    public Flowable<INumber> getOfflineNumber(final String number) {
        return Flowable.fromIterable(NumberProvider.providers())
                .parallel(1)
                .runOn(Schedulers.io())
                .map(new Function<NumberHandler, INumber>() {
                    @Override
                    public INumber apply(NumberHandler numberHandler) throws Exception {
                        Log.e(TAG, "apply: " + numberHandler);
                        try {
                            return numberHandler.find(number);
                        } catch (Exception | Error e) {
                            e.printStackTrace();
                        }
                        return null;
                    }
                }, ParallelFailureHandling.SKIP)
                .sequential()
                .observeOn(AndroidSchedulers.mainThread());
    }
}

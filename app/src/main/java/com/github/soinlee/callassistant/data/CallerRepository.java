package com.github.soinlee.callassistant.data;

import android.annotation.SuppressLint;
import android.util.Log;

import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.model.SearchMode;
import com.github.soinlee.callassistant.model.database.Database;
import com.github.soinlee.callassistant.model.db.Caller;
import com.github.soinlee.callassistant.model.db.MarkedRecord;
import com.github.soinlee.callassistant.model.permission.Permission;
import com.github.soinlee.callassistant.model.setting.Setting;
import com.github.soinlee.callassistant.utils.Alarm;
import com.github.soinlee.callassistant.utils.Contact;
import org.xdty.phone.number.RxPhoneNumber;
import org.xdty.phone.number.model.INumber;
import org.xdty.phone.number.util.Utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

@SuppressWarnings("ResultOfMethodCallIgnored")
@SuppressLint("CheckResult")
public class CallerRepository implements CallerDataSource {

    private static final String TAG = CallerRepository.class.getSimpleName();

    @Inject
    Database mDatabase;

    @Inject
    RxPhoneNumber mPhoneNumber;

    @Inject
    Permission mPermission;

    @Inject
    Contact mContact;

    @Inject
    Setting mSetting;

    @Inject
    Alarm mAlarm;

    private Map<String, Caller> mCallerMap;

    private Set<String> mLoadingCache;

    private Map<String, Long> mErrorCache;

    private OnDataUpdateListener mOnDataUpdateListener;

    public CallerRepository() {
        mCallerMap = Collections.synchronizedMap(new HashMap<String, Caller>());
        mErrorCache = Collections.synchronizedMap(new HashMap<String, Long>());
        mLoadingCache = Collections.synchronizedSet(new HashSet<String>());
        Application.getApplication().getAppComponent().inject(this);
    }

    public static String fixNumber(String number) {
        if (number == null || number.length() == 0) {
            return number;
        }
        String s = number;
        // 国际号码判定：以 + 或 00 开头，且非国内(86) / 特服(400)；长度>9 防误伤短特服号。
        // 国际号保留 + / 00 前缀，供 AreaCodeHandler 识别国家。
        boolean isIntl = s.length() > 9
                && ((s.startsWith("+") && !s.startsWith("+86") && !s.startsWith("+400"))
                    || (s.startsWith("00") && !s.startsWith("0086")));
        if (isIntl) {
            return s;
        }

        // 以下为国内 / 特服处理（原逻辑）
        String fixedNumber = s;

        if (fixedNumber.startsWith("+86")) {
            fixedNumber = fixedNumber.replace("+86", "");
        }
        if (fixedNumber.startsWith("0086")) {
            fixedNumber = fixedNumber.replaceFirst("^0086", "");
        }

        if (fixedNumber.startsWith("86") && fixedNumber.length() > 9) {
            fixedNumber = fixedNumber.replaceFirst("^86", "");
        }

        if (fixedNumber.startsWith("+400")) {
            fixedNumber = fixedNumber.replace("+", "");
        }

        if (fixedNumber.startsWith("12583")) {
            fixedNumber = fixedNumber.replaceFirst("^12583.", "");
        }

        if (fixedNumber.startsWith("1259023")) {
            fixedNumber = fixedNumber.replaceFirst("^1259023", "");
        }

        if (fixedNumber.startsWith("1183348")) {
            fixedNumber = fixedNumber.replaceFirst("^1183348", "");
        }

        // 去除可能残留的 + 号（国内 400 已剥，此处兜底）
        if (fixedNumber.startsWith("+")) {
            fixedNumber = fixedNumber.replace("+", "");
        }

        return fixedNumber;
    }

    @Override
    public Caller getCallerFromCache(String number) {

        number = fixNumber(number);

        // return empty caller if it's in error cache.
        if (mErrorCache.containsKey(number) &&
                System.currentTimeMillis() - mErrorCache.get(number) < 60 * 1000) {
            return Caller.empty(true);
        }

        return getCallerFromCache(number, true);
    }

    private Caller getCallerFromCache(final String number, boolean fetchIfNotExist) {
        Caller caller = mCallerMap.get(number);
        if (caller == null && number.contains("+86")) {
            caller = mCallerMap.get(number.replace("+86", ""));
        }

        if (caller != null) {
            return caller;
        } else if (fetchIfNotExist) {
            getCaller(number).subscribe(new Consumer<Caller>() {
                @Override
                public void accept(Caller caller) {
                    Log.e(TAG, "call: " + number + "->" + caller.getNumber());
                    if (mOnDataUpdateListener != null) {
                        mOnDataUpdateListener.onDataUpdate(caller);
                    }
                }
            });
        }
        return Caller.empty(false);
    }

    @Override
    public Observable<Caller> getCaller(String number) {
        return getCaller(number, false);
    }

    @Override
    public Observable<Caller> getCaller(String numberOrigin, final boolean forceOffline) {

        Log.d(TAG, "getCaller: " + numberOrigin + ", forceOffline: " + forceOffline);

        final String number = fixNumber(numberOrigin);

        return Observable.create(new ObservableOnSubscribe<Caller>() {
            @Override
            public void subscribe(ObservableEmitter<Caller> emitter) throws Exception {

                try {
                    do {
                        // check loading cache
                        if (mLoadingCache.contains(number)) {
                            // return without onCompleted
                            return;
                        }
                        mLoadingCache.add(number);

                        // load from cache
                        Caller caller = getCallerFromCache(number, false);

                        if (caller != null && caller.isUpdated()) {
                            emitter.onNext(caller);
                            break;
                        }

                        // load from database
                        caller = mDatabase.findCallerSync(number);

                        if (caller != null) {
                            if (caller.isUpdated()) {
                                cache(caller);
                                emitter.onNext(caller);
                                break;
                            } else {
                                mDatabase.removeCaller(caller);
                            }
                        }

                        // load from phone number library offline data only (offline mode)
                        INumber iNumber = Utils.pathGeo(mPhoneNumber.getOfflineNumber(number).toList().blockingGet());

                        if (iNumber != null && iNumber.isValid()) {
                            emitter.onNext(handleResponse(iNumber, false));
                        } else {
                            emitter.onNext(Caller.empty(false));
                        }
                    } while (false);
                } catch (Exception e) {
                    Log.e(TAG, "getCaller failed: " + e.getMessage());
                    e.printStackTrace();
                }
                emitter.onComplete();
            }
        }).doOnNext(new Consumer<Caller>() {
            @Override
            public void accept(Caller caller) throws Exception {
                Log.d(TAG, "doOnNext: " + number);
                // add number to error cache
                if (caller.isEmpty()) {
                    mErrorCache.put(number, System.currentTimeMillis());
                } else {
                    mErrorCache.remove(number);
                }
            }
        }).doOnComplete(new Action() {
            @Override
            public void run() throws Exception {
                Log.d(TAG, "doOnCompleted: " + number);
                // remove number in loading cache
                mLoadingCache.remove(number);
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public Observable<Map<String, Caller>> loadCallerMap() {

        return Observable.fromCallable(new Callable<Map<String, Caller>>() {
            @Override
            public Map<String, Caller> call() throws Exception {
                mCallerMap.clear();

                List<Caller> callers = mDatabase.fetchCallersSync();
                for (Caller caller : callers) {
                    String number = caller.getNumber();
                    if (number != null && !number.isEmpty()) {
                        cache(caller);
                    }
                }
                return mCallerMap;
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public void setOnDataUpdateListener(OnDataUpdateListener listener) {
        mOnDataUpdateListener = listener;
    }

    @Override
    public Observable<Integer> clearCache() {

        mCallerMap.clear();
        mLoadingCache.clear();
        mErrorCache.clear();

        return Observable.fromCallable(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {

                return mDatabase.clearAllCallerSync();
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    public void updateCaller(String number, int type, String typeText) {
        // Normalize the persisted key so the mark matches the main-list key
        // (offline attribution). Evict both the prefixed and plain cache keys
        // in case a caller slipped through without normalization.
        final String fixedNumber = CallerRepository.fixNumber(number);
        mCallerMap.remove(fixedNumber);
        if (!fixedNumber.equals(number)) {
            mCallerMap.remove(number);
        }
        MarkedRecord markedRecord = new MarkedRecord();
        markedRecord.setUid(mSetting.getUid());
        markedRecord.setNumber(fixedNumber);
        markedRecord.setType(type);
        markedRecord.setTypeName(typeText);
        mDatabase.updateMarked(markedRecord);
        mDatabase.updateCaller(markedRecord);
        mAlarm.alarm();

        // Synchronously put the freshly-marked Caller into the in-memory cache
        // (with callerSource=-9999 so isMark()/getType() treat it as a user
        // mark) so the following onDataUpdate triggers an immediate list rebind
        // that hits the marked row. Without this, the rebind would read the
        // stale offline row and the mark would only appear after a manual
        // refresh (async getCaller race).
        Caller marked = new Caller();
        marked.setNumber(fixedNumber);
        marked.setName(typeText);
        marked.setType("report");
        marked.setOffline(false);
        marked.setLastUpdate(System.currentTimeMillis());
        // callerSource stays DEFAULT_SOURCE(-9999) so isMark()==true and
        // getType() resolves the mark type via Utils.markTypeFromName(name).
        mCallerMap.put(fixedNumber, marked);

        mOnDataUpdateListener.onDataUpdate(marked);
    }

    @Override
    public void clearMark(final String numberOrigin) {
        final String number = fixNumber(numberOrigin);
        // Evict in-memory caches so the next lookup re-runs offline resolution
        // instead of returning the stale marked record.
        mCallerMap.remove(number);
        mErrorCache.remove(number);
        mLoadingCache.remove(number);

        // Symmetric to updateCaller: synchronously build the resolved (unmarked)
        // caller, put it back into the cache and notify - so the main list
        // rebinds immediately to the offline row without needing a manual
        // refresh (previous code only sent an empty onDataUpdate and relied on
        // a second async getCaller that could re-read the not-yet-deleted row).
        Observable.fromCallable(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                // Delete both the marked record and its persisted CALLER row.
                // removeMarkedRecordSync deletes synchronously on this IO thread,
                // so by the time we re-resolve below the DB is already clean.
                mDatabase.removeMarkedRecordSync(number);

                Caller resolved = resolveOffline(number);
                // Symmetric to updateCaller: put the resolved caller (unmarked
                // number, offline attribution / empty) back into the cache so the
                // following onDataUpdate hits it.
                mCallerMap.put(number, resolved);
                return resolved;
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object o) {
                        // Notify listeners with the actually-resolved caller so the
                        // list rebinds to the unmarked row immediately.
                        if (mOnDataUpdateListener != null) {
                            mOnDataUpdateListener.onDataUpdate((Caller) o);
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        Log.e(TAG, "clearMark failed: " + throwable.getMessage());
                    }
                });
    }

    /**
     * Resolves the offline (unmarked) attribution for {@code number} on the
     * current thread and returns a caller ready to be cached / notified.
     *
     * <p>Used by {@link #clearMark} to build the "reverted" caller (symmetric to
     * {@link #updateCaller}'s marked caller): after the marked rows are deleted
     * from the DB this re-runs the same offline lookup the main list would use,
     * so the immediate rebind shows the unmarked offline row.
     */
    private Caller resolveOffline(final String number) {
        Caller caller = mDatabase.findCallerSync(number);
        if (caller != null && caller.isUpdated()) {
            cache(caller);
            return caller;
        }

        INumber iNumber = Utils.pathGeo(
                mPhoneNumber.getOfflineNumber(number).toList().blockingGet());
        if (iNumber != null && iNumber.isValid()) {
            return handleResponse(iNumber, false);
        }

        return Caller.empty(false);
    }

    private Caller handleResponse(INumber number, boolean isOnline) {
        if (number != null) {
            Caller caller = new Caller(number, !number.isOnline());

            cache(caller);

            return caller;
        }
        return Caller.empty(isOnline);
    }

    private void cache(Caller caller) {
        if (mPermission.canReadContact()) {
            String name = mContact.getName(fixNumber(caller.getNumber()));
            caller.setContactName(name);
        }
        mCallerMap.put(caller.getNumber(), caller);
    }

    @Override
    public SearchMode getSearchMode(String number) {
        SearchMode mode = SearchMode.ONLINE;
        if (isIgnoreContact(number)) {
            if (mSetting.isShowingContactOffline()) {
                mode = SearchMode.OFFLINE;
            } else {
                mode = SearchMode.IGNORE;
            }
        }
        return mode;
    }

    @Override
    public boolean isIgnoreContact(String number) {
        return mSetting.isIgnoreKnownContact() && mPermission.canReadContact()
                && (mContact.isExist(number) || mContact.isExist(fixNumber(number)));
    }
}

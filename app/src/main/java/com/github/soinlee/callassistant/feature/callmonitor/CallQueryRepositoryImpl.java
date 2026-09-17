package com.github.soinlee.callassistant.feature.callmonitor;

import androidx.annotation.Nullable;

import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.data.CallerDataSource;
import com.github.soinlee.callassistant.model.SearchMode;
import com.github.soinlee.callassistant.model.db.Caller;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

/**
 * Default {@link CallQueryRepository} backed by the existing {@link CallerDataSource}.
 *
 * The heavy lifting (database lookups and the bundled off-line number library) is
 * delegated to the pre-existing {@code CallerRepository}; this class only adapts its
 * RxJava API to the simple, synchronous interface the ViewModel expects so that the
 * ViewModel stays framework-agnostic and unit-testable.
 */
public class CallQueryRepositoryImpl implements CallQueryRepository {

    @Inject
    CallerDataSource mCallerDataSource;

    public CallQueryRepositoryImpl() {
        Application.getApplication().getAppComponent().inject(this);
    }

    @Nullable
    @Override
    public Caller queryOffline(String number) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Caller> result = new AtomicReference<>();
        mCallerDataSource.getCaller(number, true /* forceOffline */)
                .subscribe(caller -> {
                    result.set(caller);
                    latch.countDown();
                }, throwable -> latch.countDown());
        try {
            latch.await(8, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Caller caller = result.get();
        return (caller != null && !caller.isEmpty()) ? caller : null;
    }

    @Override
    public void markError(String number) {
        // The underlying CallerRepository maintains its own error cache; querying
        // again through it will naturally respect that window activity.
    }

    @Override
    public int clearCache() {
        return mCallerDataSource.clearCache().blockingFirst(-1);
    }

    @Override
    public void setOnDataUpdateListener(@Nullable OnDataUpdateListener listener) {
        if (listener == null) {
            mCallerDataSource.setOnDataUpdateListener(null);
            return;
        }
        mCallerDataSource.setOnDataUpdateListener(caller -> listener.onDataUpdate(caller));
    }

    @Override
    public int searchModeFor(String number) {
        SearchMode mode = mCallerDataSource.getSearchMode(number);
        switch (mode) {
            case OFFLINE:
                return MODE_OFFLINE;
            case IGNORE:
                return MODE_IGNORE;
            case ONLINE:
            default:
                return MODE_ONLINE;
        }
    }

    @Nullable
    @Override
    public Caller queryFromCache(String number) {
        Caller caller = mCallerDataSource.getCallerFromCache(number);
        return (caller != null && !caller.isEmpty()) ? caller : null;
    }

    @Override
    public boolean isIgnoreContact(String number) {
        return mCallerDataSource.isIgnoreContact(number);
    }
}

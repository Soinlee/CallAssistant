package com.github.soinlee.callassistant.data;

import com.github.soinlee.callassistant.model.SearchMode;
import com.github.soinlee.callassistant.model.db.Caller;

import java.util.Map;

import io.reactivex.Observable;

public interface CallerDataSource {

    Caller getCallerFromCache(String number);

    Observable<Caller> getCaller(String number);

    Observable<Caller> getCaller(String number, boolean forceOffline);

    Observable<Map<String, Caller>> loadCallerMap();

    void setOnDataUpdateListener(OnDataUpdateListener listener);

    Observable<Integer> clearCache();

    void updateCaller(String number, int type, String typeText);

    /**
     * Clears a user-applied mark for {@code number} (deletes the marked record
     * and the persisted CALLER row, and evicts the in-memory cache) so that a
     * subsequent lookup falls back to offline attribution again.
     */
    void clearMark(String number);

    boolean isIgnoreContact(String number);

    SearchMode getSearchMode(String number);

    interface OnDataUpdateListener {

        void onDataUpdate(Caller caller);
    }

}

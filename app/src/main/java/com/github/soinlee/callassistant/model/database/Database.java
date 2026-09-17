package com.github.soinlee.callassistant.model.database;

import com.github.soinlee.callassistant.model.db.Caller;
import com.github.soinlee.callassistant.model.db.InCall;
import com.github.soinlee.callassistant.model.db.MarkedRecord;
import org.xdty.phone.number.model.INumber;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;

public interface Database {

    Observable<List<InCall>> fetchInCalls();

    Observable<List<Caller>> fetchCallers();

    Observable<Integer> clearAllInCalls();

    void clearAllInCallSync();

    void removeInCall(InCall inCall);

    Observable<Caller> findCaller(String number);

    Caller findCallerSync(String number);

    void removeCaller(Caller caller);

    int clearAllCallerSync();

    void updateCaller(Caller caller);

    void saveInCall(InCall inCall);

    /**
     * Persists a call record idempotently: existing row (same {@code callLogId})
     * is updated, otherwise a new row is inserted.
     */
    void insertOrUpdateInCall(InCall inCall);

    /**
     * Returns the maximum {@code CALL_LOG_ID} stored locally, or {@code 0} when
     * the table is empty (used as the incremental-import anchor).
     */
    long getMaxCallLogId();

    /**
     * Returns all locally stored call records whose {@code CALL_LOG_ID} is
     * strictly greater than {@code sinceId}, newest first.
     */
    List<InCall> fetchInCallsSince(long sinceId);

    void saveMarked(MarkedRecord markedRecord);

    void updateMarked(MarkedRecord markedRecord);

    void updateCaller(MarkedRecord markedRecord);

    Observable<List<MarkedRecord>> fetchMarkedRecords();

    Observable<MarkedRecord> findMarkedRecord(String number);

    void updateMarkedRecord(String number);

    List<Caller> fetchCallersSync();

    List<InCall> fetchInCallsSync();

    List<MarkedRecord> fetchMarkedRecordsSync();

    void addCallers(List<Caller> callers);

    void addInCallers(List<InCall> inCalls);

    void addMarkedRecords(List<MarkedRecord> markedRecords);

    void clearAllMarkedRecordSync();

    int getInCallCount(String number);

    void addInCallersSync(List<InCall> inCalls);

    void saveMarkedRecord(INumber number, String uid);

    void removeRecord(MarkedRecord record);

    /**
     * Synchronously deletes the marked record and its persisted {@code CALLER}
     * row for {@code number} on the calling thread. Used by {@code clearMark}
     * so the DB is guaranteed clean before the unmarked resolution is cached.
     */
    void removeMarkedRecordSync(String number);
}

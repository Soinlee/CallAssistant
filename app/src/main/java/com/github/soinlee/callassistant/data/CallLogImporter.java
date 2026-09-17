package com.github.soinlee.callassistant.data;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.github.soinlee.callassistant.model.database.Database;
import com.github.soinlee.callassistant.model.db.Caller;
import com.github.soinlee.callassistant.model.db.InCall;
import com.github.soinlee.callassistant.model.permission.Permission;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * Incrementally imports the system call log ({@code content://call_log/calls})
 * into the local {@code IN_CALL} table.
 *
 * <p><b>Strategy (方案 A):</b> the system {@code CallLog.Calls._ID} is a
 * monotonically increasing cursor. On the very first run (no anchor) a full dump
 * is imported; afterwards only rows with {@code _ID > lastSyncedCallLogId} are
 * fetched, so every launch never re-walks/re-tags the whole history.
 *
 * <p><b>Attribution is performed exactly once per new row, at import time:</b>
 * the offline phone-number library resolves province/city/operator and the
 * result is written back into the local {@code CALLER} table (keyed by the
 * unique {@code NUMBER}). Later renders match numbers through the in-memory
 * caller map - no re-tagging happens on startup.
 *
 * <p><b>Known limitation (accepted):</b> deletions in the system call log are
 * not propagated to the local table (the anchor only grows). The user can clear
 * the local history manually.
 */
public class CallLogImporter {

    private static final String TAG = CallLogImporter.class.getSimpleName();

    /** SharedPreferences key holding the highest system CallLog {@code _ID} imported. */
    public static final String PREF_LAST_SYNCED_CALL_LOG_ID = "last_synced_calllog_id";

    /** {@code CallLog.Calls.TYPE} is missing on very old rows; use -1 (unknown). */
    private static final int CALL_TYPE_UNKNOWN = -1;

    private final Database mDatabase;
    private final Permission mPermission;
    private final CallerDataSource mCallerDataSource;

    @Inject
    public CallLogImporter(Database mDatabase, Permission mPermission,
            CallerDataSource mCallerDataSource) {
        this.mDatabase = mDatabase;
        this.mPermission = mPermission;
        this.mCallerDataSource = mCallerDataSource;
    }

    /**
     * Main entry point. Reads the system call log and imports the delta into the
     * local {@code IN_CALL} table, tagging attribution and persisting the new
     * anchor along the way.
     *
     * @param context application or activity context (only used to access the
     *                content resolver and preferences).
     * @return an observable emitting the freshly imported {@link InCall} rows
     *         (newest first), empty when there is nothing new or permission is
     *         missing. Emits on the main thread; never throws.
     */
    public Observable<List<InCall>> importCallLog(final Context context) {
        if (context == null) {
            return Observable.just(new ArrayList<InCall>());
        }
        return Observable.fromCallable(() -> importSync(context))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /**
     * Synchronous import, safe to run on a background / IO thread. Returns the
     * newly imported rows (newest first).
     */
    public List<InCall> importSync(Context context) {
        if (context == null) {
            return new ArrayList<>();
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "READ_CALL_LOG not granted, skip import");
            return new ArrayList<>();
        }

        long anchor = getLastSyncedCallLogId(context);
        long newAnchor = anchor;
        Set<String> taggedNumbers = new HashSet<>();
        List<InCall> imported = new ArrayList<>();

        Cursor cursor = null;
        try {
            Uri uri = CallLog.Calls.CONTENT_URI;
            String[] projection = {
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
            };
            String selection = anchor > 0 ? CallLog.Calls._ID + " > ?" : null;
            String[] selectionArgs = anchor > 0
                    ? new String[]{String.valueOf(anchor)} : null;
            cursor = context.getContentResolver().query(uri, projection, selection,
                    selectionArgs, CallLog.Calls._ID + " ASC");

            if (cursor == null) {
                Log.d(TAG, "call log cursor is null (no provider?)");
                return imported;
            }

            int idIdx = cursor.getColumnIndex(CallLog.Calls._ID);
            int numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE);
            int dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE);
            int durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION);

            while (cursor.moveToNext()) {
                long callLogId = idIdx >= 0 ? cursor.getLong(idIdx) : 0;
                String rawNumber = numIdx >= 0 ? cursor.getString(numIdx) : null;
                int callType = typeIdx >= 0 ? cursor.getInt(typeIdx) : CALL_TYPE_UNKNOWN;
                long date = dateIdx >= 0 ? cursor.getLong(dateIdx) : System.currentTimeMillis();
                long durationMillis = durIdx >= 0 ? cursor.getLong(durIdx) * 1000 : 0;

                if (TextUtils.isEmpty(rawNumber)) {
                    continue;
                }

                InCall inCall = new InCall();
                inCall.setNumber(rawNumber.replaceAll(" ", ""));
                inCall.setTime(date);
                inCall.setRingTime(0);            // CallLog has no ring-time column.
                inCall.setDuration(durationMillis);
                inCall.setCallLogId(callLogId);
                inCall.setCallType(callType >= 0 ? callType : CALL_TYPE_UNKNOWN);

                // Resolve the contact id (read-only); null when no contact matches.
                inCall.setContactId(findContactId(context, inCall.getNumber()));

                // Persist idempotently by CALL_LOG_ID.
                mDatabase.insertOrUpdateInCall(inCall);

                // Tag attribution once per number within this batch.
                String number = inCall.getNumber();
                if (!taggedNumbers.contains(number)) {
                    taggedNumbers.add(number);
                    tagAttribution(number);
                }

                imported.add(inCall);

                if (callLogId > newAnchor) {
                    newAnchor = callLogId;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "importSync error: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Persist the new anchor only after the batch succeeded.
        if (newAnchor > anchor) {
            persistAnchor(context, newAnchor);
            Log.d(TAG, "imported " + imported.size() + " calls, anchor "
                    + anchor + " -> " + newAnchor);
        }

        return imported;
    }

    /** Resolves the {@code Contacts._ID} for a number (read-only), or {@code null}. */
    private Long findContactId(Context context, String number) {
        if (!mPermission.canReadContact() || TextUtils.isEmpty(number)) {
            return null;
        }
        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone._ID,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null, null);
            if (c == null) {
                return null;
            }
            while (c.moveToNext()) {
                String candidate = c.getString(1);
                if (candidate != null) {
                    String digits = candidate.replaceAll("[^\\d]", "");
                    String target = number.replaceAll("[^\\d]", "");
                    if (TextUtils.equals(digits, target)) {
                        return c.getLong(0);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "findContactId failed for " + number + ": " + e.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    /**
     * Tags attribution for a single number and persists it into the local
     * {@code CALLER} table. Blocking on purpose: this is called from
     * {@link #importSync(Context)} which already runs on an IO thread, and we
     * need the attribution written before {@code importSync} returns so the UI
     * refresh sees the tagged callers. The repository caches results in memory
     * and in the DB, so on subsequent runs this is a cheap lookup.
     */
    private void tagAttribution(final String number) {
        try {
            final Caller caller = mCallerDataSource.getCaller(number, true)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .blockingFirst();
            if (caller != null && caller.getNumber() != null && !caller.isEmpty()) {
                mDatabase.updateCaller(caller);
                Log.d(TAG, "tagged " + number + " -> " + caller.getGeo());
            }
        } catch (Exception e) {
            Log.w(TAG, "tagAttribution failed for " + number + ": " + e.getMessage());
        }
    }

    private long getLastSyncedCallLogId(Context context) {
        return getPrefs(context).getLong(PREF_LAST_SYNCED_CALL_LOG_ID, 0L);
    }

    private void persistAnchor(Context context, long id) {
        getPrefs(context).edit().putLong(PREF_LAST_SYNCED_CALL_LOG_ID, id).apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}

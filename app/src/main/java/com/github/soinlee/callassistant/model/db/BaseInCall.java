package com.github.soinlee.callassistant.model.db;

import android.text.TextUtils;

import com.github.soinlee.callassistant.utils.Utils;

import io.requery.Column;
import io.requery.Entity;
import io.requery.Generated;
import io.requery.Index;
import io.requery.Key;
import io.requery.Table;
import io.requery.Transient;

@Table(name = "IN_CALL")
@Entity
public abstract class BaseInCall {

    @Key
    @Generated
    @Column(name = "ID")
    int id;

    @Column(name = "NUMBER")
    String number;

    @Column(name = "`TIME`")
    long time;

    @Column(name = "RING_TIME")
    long ringTime;

    @Column(name = "DURATION")
    long duration;

    /**
     * The {@code _ID} of the corresponding row in the system CallLog
     * (content://call_log/calls). Used as the incremental-import cursor:
     * rows with {@code _ID > lastSyncedCallLogId} are new entries.
     * Duplicates (multiple calls to the same number) are expected - this is a
     * per-call record table, not a per-number table.
     */
    @Index
    @Column(name = "CALL_LOG_ID")
    long callLogId;

    /**
     * The {@code CallLog.Calls.TYPE_*} of the call (INCOMING / OUTGOING /
     * MISSED / REJECTED / ...). {@code -1} means "unknown / not imported".
     */
    @Column(name = "CALL_TYPE")
    int callType = -1;

    /**
     * Optional {@code ContactsContract.Contacts._ID} resolved while importing
     * for read-only name association. {@code null} when the number does not
     * match a contact (never written back to the contacts database).
     */
    @Column(name = "CONTACT_ID")
    Long contactId;

    @Transient
    boolean isExpanded = false;

    public BaseInCall() {
    }

    public BaseInCall(String number, long time, long ringTime, long duration) {

        if (!TextUtils.isEmpty(number)) {
            number = number.replaceAll(" ", "");
        }

        this.number = number;
        this.time = time;
        this.ringTime = ringTime;
        this.duration = duration;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }

    public long getCallLogId() {
        return callLogId;
    }

    public void setCallLogId(long callLogId) {
        this.callLogId = callLogId;
    }

    public int getCallType() {
        return callType;
    }

    public void setCallType(int callType) {
        this.callType = callType;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public String getReadableTime() {
        return Utils.readableDate(time) + " " + Utils.getTime(time);
    }
}

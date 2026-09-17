package com.github.soinlee.callassistant.feature.callmonitor;

import androidx.annotation.Nullable;

/**
 * A one-shot, platform-dependent side effect that the MVVM View layer must perform
 * on behalf of the ViewModel (which is context-free and therefore unit-testable).
 */
public final class CallAction {

    /** Show the "mark this number" flow. */
    public static final int TYPE_SHOW_MARK = 0;

    /** Persist the completed call to the in-call history. */
    public static final int TYPE_SAVE_IN_CALL = 1;

    private final int type;

    @Nullable
    private final String number;

    @Nullable
    private final String name;

    private final long timestamp;

    private final long ringDuration;

    private final long callDuration;

    private CallAction(int type, @Nullable String number, @Nullable String name,
            long timestamp, long ringDuration, long callDuration) {
        this.type = type;
        this.number = number;
        this.name = name;
        this.timestamp = timestamp;
        this.ringDuration = ringDuration;
        this.callDuration = callDuration;
    }

    public static CallAction showMark(String number) {
        return new CallAction(TYPE_SHOW_MARK, number, null, 0, 0, 0);
    }

    public static CallAction saveInCall(String number, long timestamp,
            long ringDuration, long callDuration) {
        return new CallAction(TYPE_SAVE_IN_CALL, number, null, timestamp,
                ringDuration, callDuration);
    }

    public int getType() {
        return type;
    }

    @Nullable
    public String getNumber() {
        return number;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getRingDuration() {
        return ringDuration;
    }

    public long getCallDuration() {
        return callDuration;
    }
}

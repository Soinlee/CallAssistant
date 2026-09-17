package com.github.soinlee.callassistant.feature.callmonitor;

import androidx.annotation.Nullable;

import com.github.soinlee.callassistant.model.db.Caller;

/**
 * Declarative state describing what the incoming-call floating window should show.
 *
 * This is the single collection of all states the View layer can render. Keeping
 * presentation state outside the platform window/widget classes is what decouples
 * the MVVM architecture from the View.
 */
public final class FloatWindowUiState {

    /** Nothing to show yet / window closed. */
    public static final int TYPE_IDLE = 0;

    /** "searching..." indicator is being displayed. */
    public static final int TYPE_SEARCHING = 1;

    /** The number resolved to a valid {@link Caller}; show its info. */
    public static final int TYPE_SHOWING = 2;

    /** Query failed. */
    public static final int TYPE_ERROR = 3;

    /** Window should be hidden but kept alive. */
    public static final int TYPE_HIDDEN = 4;

    /** Window should be closed and service stopped. */
    public static final int TYPE_CLOSED = 5;

    private final int type;
    @Nullable
    private final Caller caller;

    /** true when {@link #type} is {@link #TYPE_ERROR} and the lookup used an online source. */
    private final boolean online;

    private FloatWindowUiState(int type, @Nullable Caller caller, boolean online) {
        this.type = type;
        this.caller = caller;
        this.online = online;
    }

    public static FloatWindowUiState idle() {
        return new FloatWindowUiState(TYPE_IDLE, null, false);
    }

    public static FloatWindowUiState searching() {
        return new FloatWindowUiState(TYPE_SEARCHING, null, false);
    }

    public static FloatWindowUiState showing(Caller caller) {
        return new FloatWindowUiState(TYPE_SHOWING, caller, false);
    }

    public static FloatWindowUiState error(boolean isOnline) {
        return new FloatWindowUiState(TYPE_ERROR, null, isOnline);
    }

    public static FloatWindowUiState hidden() {
        return new FloatWindowUiState(TYPE_HIDDEN, null, false);
    }

    public static FloatWindowUiState closed() {
        return new FloatWindowUiState(TYPE_CLOSED, null, false);
    }

    public int getType() {
        return type;
    }

    @Nullable
    public Caller getCaller() {
        return caller;
    }

    public boolean isOnline() {
        return online;
    }
}

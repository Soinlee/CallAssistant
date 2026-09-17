package com.github.soinlee.callassistant.feature.callmonitor;

import androidx.annotation.Nullable;

import com.github.soinlee.callassistant.model.db.Caller;

/**
 * Repository responsible for *off-line* caller lookup.
 *
 * This abstracts the data sources (in-memory cache, local database and bundled
 * off-line phone-number library) behind a single synchronous API so that the
 * ViewModel never touches platform / database details directly.
 *
 * This is the "Repository" leg of the MVVM triad for the call-monitor feature:
 *
 * <pre>
 *   Repository (offline query)  <->  ViewModel (floating-window state)  <->  View
 * </pre>
 */
public interface CallQueryRepository {

    /**
     * Offline-first lookup of {@code number}.
     *
     * Resolution order (matching the legacy behaviour):
     *  1. in-memory / error caches,
     *  2. local database,
     *  3. bundled off-line phone-number library.
     *
     * @return a valid {@link Caller}, or {@code null} when nothing is known.
     */
    @Nullable
    Caller queryOffline(String number);

    /** Marks {@code number} as recently failed so a repeated query within a short window reports empty. */
    void markError(String number);

    /** Drops every cached entry (memory + DB). */
    int clearCache();

    /** Informs interested parties (e.g. the window) that a {@link Caller} was refreshed. */
    void setOnDataUpdateListener(@Nullable OnDataUpdateListener listener);

    /** Returns the semantic search mode for {@code number}. */
    int searchModeFor(String number);

    /** Fast in-memory lookup, no IO. */
    @Nullable
    Caller queryFromCache(String number);

    /** True when {@code number} should be ignored because it is a known contact. */
    boolean isIgnoreContact(String number);

    interface OnDataUpdateListener {
        void onDataUpdate(Caller caller);
    }

    /** Mirrors {@link com.github.soinlee.callassistant.model.SearchMode}. */
    int MODE_ONLINE = 0;
    int MODE_OFFLINE = 1;
    int MODE_IGNORE = 2;
}

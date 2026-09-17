package com.github.soinlee.callassistant.contract;

import android.content.Context;

import com.github.soinlee.callassistant.model.db.Caller;
import com.github.soinlee.callassistant.model.db.InCall;
import org.xdty.phone.number.model.INumber;

import java.util.List;
import java.util.Map;

public interface MainContract {

    interface View extends BaseView<Presenter> {

        void showNoCallLog(boolean show);

        void showLoading(boolean active);

        void showCallLogs(List<InCall> inCalls);

        void showSearchResult(INumber number);

        void showSearching();

        void showSearchFailed(boolean isOnline);

        void attachCallerMap(Map<String, Caller> callerMap);

        Context getContext();

        void showBottomSheet(InCall inCall);

        /** Dials {@code number} via the system dialer (back-call from the list row). */
        void dial(String number);
    }

    interface Presenter extends BasePresenter {

        void result(int requestCode, int resultCode);

        void loadInCallList();

        void loadCallerMap();

        void removeInCallFromList(InCall inCall);

        void removeInCall(InCall inCall);

        void clearAll();

        void search(String number);

        boolean canDrawOverlays();

        int checkPermission(String permission);

        void clearSearch();

        Caller getCaller(String number);

        void clearCache();

        void itemOnLongClicked(InCall inCall);

        void invalidateDataUpdate(boolean isInvalidate);

        void dial(String number);
    }
}

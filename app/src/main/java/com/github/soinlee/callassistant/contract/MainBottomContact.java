package com.github.soinlee.callassistant.contract;

import com.github.soinlee.callassistant.model.db.Caller;
import com.github.soinlee.callassistant.model.db.InCall;

public interface MainBottomContact {
    interface View extends BaseView<MainContract.Presenter> {

        void init(InCall inCall, Caller caller);

        void updateMark(int viewId, Caller caller);

        void updateMarkName(String name);
    }

    interface Presenter extends BasePresenter {
        void bindData(InCall inCall);

        boolean canMark();

        void markClicked(int viewId);

        void markCustom(String text);

        void clearMark();
    }
}

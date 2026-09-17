package com.github.soinlee.callassistant.di.modules;

import com.github.soinlee.callassistant.contract.MainBottomContact;
import com.github.soinlee.callassistant.presenter.MainBottomPresenter;

import dagger.Module;
import dagger.Provides;

@Module
public class MainBottomModule {

    private MainBottomContact.View mView;

    public MainBottomModule(MainBottomContact.View view) {
        mView = view;
    }

    @Provides
    MainBottomContact.View provideView() {
        return mView;
    }

    @Provides
    MainBottomContact.Presenter providePresenter() {
        return new MainBottomPresenter(mView);
    }

}

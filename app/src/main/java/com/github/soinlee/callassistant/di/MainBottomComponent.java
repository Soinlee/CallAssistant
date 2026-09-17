package com.github.soinlee.callassistant.di;

import com.github.soinlee.callassistant.di.modules.AppModule;
import com.github.soinlee.callassistant.di.modules.MainBottomModule;
import com.github.soinlee.callassistant.fragment.MainBottomSheetFragment;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = { MainBottomModule.class, AppModule.class })
public interface MainBottomComponent {

    void inject(MainBottomSheetFragment mainBottomSheetFragment);
}

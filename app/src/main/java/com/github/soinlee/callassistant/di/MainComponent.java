package com.github.soinlee.callassistant.di;

import com.github.soinlee.callassistant.activity.MainActivity;
import com.github.soinlee.callassistant.di.modules.AppModule;
import com.github.soinlee.callassistant.di.modules.MainModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = { MainModule.class, AppModule.class })
public interface MainComponent {
    void inject(MainActivity view);
}

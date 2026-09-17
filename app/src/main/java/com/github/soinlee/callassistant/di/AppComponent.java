package com.github.soinlee.callassistant.di;

import com.github.soinlee.callassistant.activity.MarkActivity;
import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.data.CallerRepository;
import com.github.soinlee.callassistant.di.modules.AppModule;
import com.github.soinlee.callassistant.feature.callmonitor.CallMonitorView;
import com.github.soinlee.callassistant.feature.callmonitor.CallMonitorViewModel;
import com.github.soinlee.callassistant.feature.callmonitor.CallQueryRepositoryImpl;
import com.github.soinlee.callassistant.fragment.SettingsFragment;
import com.github.soinlee.callassistant.model.database.DatabaseImpl;
import com.github.soinlee.callassistant.presenter.MainBottomPresenter;
import com.github.soinlee.callassistant.presenter.MainPresenter;
import com.github.soinlee.callassistant.service.FloatWindow;
import com.github.soinlee.callassistant.utils.Alarm;
import com.github.soinlee.callassistant.utils.Contact;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = AppModule.class)
public interface AppComponent {
    void inject(MainPresenter presenter);

    void inject(Application application);

    void inject(FloatWindow service);

    void inject(Alarm alarm);

    void inject(MarkActivity markActivity);

    void inject(SettingsFragment settingsFragment);

    void inject(CallerRepository callerRepository);

    void inject(CallQueryRepositoryImpl callQueryRepository);

    void inject(CallMonitorViewModel callMonitorViewModel);

    void inject(CallMonitorView callMonitorView);

    void inject(MainBottomPresenter mainBottomPresenter);

    void inject(DatabaseImpl database);

    void inject(Contact contact);
}

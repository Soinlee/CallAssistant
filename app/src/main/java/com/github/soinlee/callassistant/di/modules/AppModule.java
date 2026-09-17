package com.github.soinlee.callassistant.di.modules;

import com.github.soinlee.callassistant.BuildConfig;
import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.data.CallLogImporter;
import com.github.soinlee.callassistant.data.CallerDataSource;
import com.github.soinlee.callassistant.data.CallerRepository;
import com.github.soinlee.callassistant.feature.callmonitor.CallQueryRepository;
import com.github.soinlee.callassistant.feature.callmonitor.CallQueryRepositoryImpl;
import com.github.soinlee.callassistant.model.database.Database;
import com.github.soinlee.callassistant.model.database.DatabaseImpl;
import com.github.soinlee.callassistant.model.db.Models;
import com.github.soinlee.callassistant.model.permission.Permission;
import com.github.soinlee.callassistant.model.permission.PermissionImpl;
import com.github.soinlee.callassistant.model.setting.Setting;
import com.github.soinlee.callassistant.model.setting.SettingImpl;
import com.github.soinlee.callassistant.utils.Alarm;
import com.github.soinlee.callassistant.utils.Contact;
import com.github.soinlee.callassistant.utils.Window;
import org.xdty.phone.number.RxPhoneNumber;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.requery.Persistable;
import io.requery.android.sqlite.DatabaseSource;
import io.requery.sql.Configuration;
import io.requery.sql.ConfigurationBuilder;
import io.requery.sql.EntityDataStore;

import static com.github.soinlee.callassistant.utils.Constants.DB_NAME;
import static com.github.soinlee.callassistant.utils.Constants.DB_VERSION;

@Module
public class AppModule {

    protected Application app;

    public AppModule(Application application) {
        app = application;
    }

    @Singleton
    @Provides
    public Application provideApplication() {
        return app;
    }

    @Singleton
    @Provides
    public RxPhoneNumber providePhoneNumber() {
        return new RxPhoneNumber(app);
    }

    @Singleton
    @Provides
    public Setting provideSetting() {
        SettingImpl.init(app);
        return SettingImpl.getInstance();
    }

    @Singleton
    @Provides
    public Database provideDatabase() {
        return DatabaseImpl.getInstance();
    }

    @Singleton
    @Provides
    public EntityDataStore<Persistable> provideDatabaseSource() {

        DatabaseSource source = new DatabaseSource(app, Models.DEFAULT, DB_NAME, DB_VERSION) {
            @Override
            protected void onConfigure(ConfigurationBuilder builder) {
                super.onConfigure(builder);
                builder.setQuoteColumnNames(true);
            }
        };
        source.setLoggingEnabled(BuildConfig.DEBUG);
        Configuration configuration = source.getConfiguration();

        return new EntityDataStore<>(configuration);
    }

    @Singleton
    @Provides
    public Permission providePermission() {
        return new PermissionImpl(app);
    }

    @Singleton
    @Provides
    public Alarm provideAlarm() {
        return new Alarm();
    }

    @Singleton
    @Provides
    public Window provideWindow() {
        return new Window();
    }

    @Singleton
    @Provides
    public Contact provideContact() {
        return Contact.getInstance();
    }

    @Singleton
    @Provides
    public CallerDataSource provideCallerDataSource() {
        return new CallerRepository();
    }

    @Singleton
    @Provides
    public CallLogImporter provideCallLogImporter() {
        return new CallLogImporter(provideDatabase(), providePermission(),
                provideCallerDataSource());
    }

    @Singleton
    @Provides
    public CallQueryRepository provideCallQueryRepository() {
        return new CallQueryRepositoryImpl();
    }

}

package com.github.soinlee.callassistant.activity;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.soinlee.callassistant.R;
import com.github.soinlee.callassistant.fragment.SettingsFragment;
import com.github.soinlee.callassistant.utils.Utils;

public class SettingsActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> mContactsPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // edge-to-edge 下顶部用 AppBarLayout(+Toolbar) 承接，蓝色栏贴顶
        setContentView(R.layout.activity_settings);
        setTitle(R.string.action_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // READ_CONTACTS runtime permission request, migrated from the legacy
        // requestPermissions + onRequestPermissionsResult model to the Jetpack
        // Activity Result API. The launcher must be registered here (on the
        // ComponentActivity), the SettingsFragment triggers it.
        mContactsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    SettingsFragment fragment = (SettingsFragment) getFragmentManager()
                            .findFragmentById(R.id.settings_content);
                    if (fragment != null) {
                        fragment.onContactsPermissionResult(granted);
                    }
                });

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.settings_content, SettingsFragment.newInstance())
                    .commit();
        }

        // targetSdk 35 forces edge-to-edge: the whole window draws under the
        // system navigation bar. Applying the bottom padding on the activity
        // root is far more reliable than dispatching insets down to the deep
        // android.R.id.list (whose requestApplyInsets from a fragment often fires
        // before attach and silently no-ops). The top bar is an AppBarLayout with
        // fitsSystemWindows, so top inset is handled there and must NOT be
        // double-applied here; only pad the bottom.
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.settings_root), (v, insets) -> {
                    Insets systemBars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars());
                    v.setPadding(0, 0, 0, systemBars.bottom);
                    // return the original insets so the AppBarLayout above still
                    // receives the top inset for its status-bar handling;
                    // the root only consumes its own bottom padding.
                    return insets;
                });
    }

    /**
     * Exposes the READ_CONTACTS permission launcher to the hosted
     * {@link SettingsFragment} (the legacy {@code PreferenceFragment} cannot register
     * an {@link ActivityResultLauncher} itself).
     */
    public ActivityResultLauncher<String> getContactsPermissionLauncher() {
        return mContactsPermissionLauncher;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        Context context = Utils.changeLang(newBase);
        super.attachBaseContext(context);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
        }
        return true;
    }

}
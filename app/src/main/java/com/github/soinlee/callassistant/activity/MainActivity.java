package com.github.soinlee.callassistant.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.soinlee.callassistant.R;
import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.data.CallLogImporter;
import com.github.soinlee.callassistant.contract.MainContract;
import com.github.soinlee.callassistant.di.DaggerMainComponent;

import io.reactivex.functions.Consumer;
import com.github.soinlee.callassistant.di.modules.AppModule;
import com.github.soinlee.callassistant.di.modules.MainModule;
import com.github.soinlee.callassistant.fragment.MainBottomSheetFragment;
import com.github.soinlee.callassistant.model.db.Caller;
import com.github.soinlee.callassistant.model.db.InCall;
import com.github.soinlee.callassistant.model.permission.Permission;
import com.github.soinlee.callassistant.model.setting.Setting;
import com.github.soinlee.callassistant.service.FloatWindow;
import com.github.soinlee.callassistant.utils.Window;
import com.github.soinlee.callassistant.view.CallerAdapter;
import com.github.soinlee.callassistant.view.DialpadView;
import org.xdty.phone.number.model.INumber;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

public class MainActivity extends BaseActivity implements MainContract.View {

    private final static String TAG = MainActivity.class.getSimpleName();

    @Inject
    MainContract.Presenter mPresenter;

    @Inject
    Permission mPermission;

    @Inject
    Setting mSetting;

    @Inject
    Window mWindow;

    @Inject
    CallLogImporter mCallLogImporter;

    private View mTopBar;
    private int mScreenWidth;
    private TextView mEmptyText;
    private RecyclerView mRecyclerView;
    private CallerAdapter mCallerAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private FrameLayout mMainLayout;
    private long mLastSearchTime;
    private FloatingActionButton mFabDialpad;
    private View mDialpadContainer;
    private DialpadView mDialpad;
    private android.widget.EditText mSearchInput;
    private android.text.TextWatcher mSearchTextWatcher;
    private Drawable mClearSearchDrawable;

    /** Full in-call list most recently loaded, used as the prefix-filter source. */
    private final List<InCall> mAllInCalls = new ArrayList<>();

    /**
     * Launchers registered via the Jetpack Activity Result API (must happen before
     * {@code onStart}, so they are created here in {@code onCreate}).
     */
    private ActivityResultLauncher<Intent> mOverlayPermissionLauncher;
    private ActivityResultLauncher<String[]> mRuntimePermissionsLauncher;
    private ActivityResultLauncher<String> mNotificationPermissionLauncher;

    /**
     * Re-entrancy guards for permission requests: the framework only allows one set of
     * permissions at a time. A same-frame second launch is suppressed with a
     * "Can request only one set of permissions at a time" warning and resolves
     * immediately as denied - which used to produce a spurious
     * "Notification permission not granted" toast while the READ_CALL_LOG dialog was
     * still on screen. These flags make each request path single-shot.
     */
    private volatile boolean mRuntimePermissionRequestInFlight = false;
    private volatile boolean mNotificationPermissionRequestInFlight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DaggerMainComponent.builder()
                .appModule(new AppModule(Application.getApplication()))
                .mainModule(new MainModule(this))
                .build()
                .inject(this);

        setupPermissionLaunchers();

        mTopBar = findViewById(R.id.top_bar);

        mScreenWidth = mSetting.getScreenWidth();

        mMainLayout = (FrameLayout) findViewById(R.id.main_layout);
        mEmptyText = (TextView) findViewById(R.id.empty_text);
        mRecyclerView = (RecyclerView) findViewById(R.id.history_list);
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);

        LinearLayoutManager layoutManager =
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(layoutManager);

        mCallerAdapter = new CallerAdapter(mPresenter);
        mRecyclerView.setAdapter(mCallerAdapter);

        initSearchBar();
        initDialpad();

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                // invalidate data update if is scrolling
                switch (newState) {
                    case RecyclerView.SCROLL_STATE_IDLE:
                        mPresenter.invalidateDataUpdate(false);
                        break;
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                    case RecyclerView.SCROLL_STATE_SETTLING:
                        mPresenter.invalidateDataUpdate(true);
                        break;
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // can scroll up and disable refresh
                if (recyclerView.canScrollVertically(-1)) {
                    mSwipeRefreshLayout.setEnabled(false);
                } else {
                    mSwipeRefreshLayout.setEnabled(true);
                }
            }
        });

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                    RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                final InCall inCall = mCallerAdapter.getItem(viewHolder.getAdapterPosition());
                mPresenter.removeInCallFromList(inCall);
                mCallerAdapter.notifyDataSetChanged();
                final Snackbar snackbar = Snackbar.make(mTopBar, R.string.deleted,
                        Snackbar.LENGTH_LONG);

                snackbar.setAction(getString(R.string.undo), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        snackbar.dismiss();
                        mPresenter.loadInCallList();
                    }
                });
                snackbar.setCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar snackbar, int event) {
                        switch (event) {
                            case DISMISS_EVENT_MANUAL:
                            case DISMISS_EVENT_ACTION:
                                break;
                            case DISMISS_EVENT_CONSECUTIVE:
                            case DISMISS_EVENT_SWIPE:
                            case DISMISS_EVENT_TIMEOUT:
                            default:
                                mPresenter.removeInCall(inCall);
                                break;
                        }
                        super.onDismissed(snackbar, event);
                    }
                });
                snackbar.show();
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                boolean swiping = actionState == ItemTouchHelper.ACTION_STATE_SWIPE;
                mSwipeRefreshLayout.setEnabled(!swiping);
            }

            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView,
                    RecyclerView.ViewHolder viewHolder,
                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState,
                        isCurrentlyActive);
                CallerAdapter.ViewHolder vh = (CallerAdapter.ViewHolder) viewHolder;
                vh.setAlpha(1 - Math.abs(dX) / mScreenWidth * 1.2f);
            }
        });

        itemTouchHelper.attachToRecyclerView(mRecyclerView);

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mPresenter.loadCallerMap();
            }
        });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected int getTitleId() {
        // The top bar intentionally shows no app title (clean search-bar only).
        return R.string.empty_string;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning from another activity (system dialer after ACTION_DIAL,
        // settings, permission pages, ...) must restore the list visibility.
        // executeSearch() hides the RecyclerView (INVISIBLE + darkened background)
        // and the only path that restored it was manual typing in the search
        // field. Dial-pad "search -> dial" does not go through that watcher, so
        // without this reset the list stayed invisible/blank after coming back.
        restoreListView();
        mPresenter.start();
    }

    /**
     * Registers the Activity Result launchers used for runtime permission requests and
     * the overlay-settings page. Must be called during {@code onCreate} so the launchers
     * are ready before {@code onStart} reacts to permissions.
     */
    @SuppressLint("InlinedApi")
    private void setupPermissionLaunchers() {
        // Overlay permission: opens the SYSTEM_ALERT_WINDOW settings page. On return we
        // re-check the grant, (re)start the persistent FGS if granted, then request the
        // runtime permissions (kept separate to avoid freezing the permission dialog on
        // Android 15 when it overlaps the settings page).
        mOverlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    boolean granted = mPresenter.canDrawOverlays();
                    if (!granted) {
                        Log.e(TAG, "SYSTEM_ALERT_WINDOW permission not granted...");
                        showOverlayPermissionGuide();
                    } else {
                        // User came back with the permission granted and the app in the
                        // foreground: start the persistent foreground service now.
                        FloatWindow.start(MainActivity.this);
                    }
                    // Note: runtime permissions are intentionally NOT requested here.
                    // When the user returns from the overlay settings page, onStart()
                    // runs first and calls requestRuntimePermissionsIfNeeded() itself
                    // (once the overlay grant takes effect). Requesting them again here
                    // would be a same-frame second launch, which the framework
                    // suppresses with "Can request only one set of permissions at a
                    // time" and resolves as denied - producing the spurious
                    // "Notification permission not granted" toast seen while the
                    // READ_CALL_LOG dialog was still up.
                });

        // Runtime permissions (READ_PHONE_STATE / READ_CALL_LOG) requested together.
        mRuntimePermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean phoneState = result.get(Manifest.permission.READ_PHONE_STATE);
                    Boolean callLog = result.get(Manifest.permission.READ_CALL_LOG);
                    if (phoneState != null && !phoneState) {
                        Toast.makeText(MainActivity.this, "Phone state permission denied",
                                Toast.LENGTH_SHORT).show();
                        showPermissionRationaleIfNeeded(Manifest.permission.READ_PHONE_STATE,
                                "Phone state permission is required for caller ID.");
                    }
                    if (callLog != null && !callLog) {
                        Toast.makeText(MainActivity.this, "Call log permission denied",
                                Toast.LENGTH_SHORT).show();
                        showPermissionRationaleIfNeeded(Manifest.permission.READ_CALL_LOG,
                                "Call log permission is required for call history.");
                    }

                    // Clear the in-flight guard: this callback is the single terminal
                    // edge for the runtime permission request.
                    mRuntimePermissionRequestInFlight = false;

                    // Request POST_NOTIFICATIONS only AFTER the runtime permission
                    // dialog has completed. The framework allows only one set of
                    // permissions at a time; launching the notification permission
                    // in the same frame would be suppressed ("Can request only one
                    // set of permissions at a time") and the dialog would be delayed.
                    requestNotificationsPermissionIfNeeded();
                });

        // POST_NOTIFICATIONS (Android 13 / API 33+). Denial does not block core
        // functionality: the FGS notification is still visible in the task manager.
        mNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    mNotificationPermissionRequestInFlight = false;
                    if (!granted) {
                        Toast.makeText(MainActivity.this,
                                "Notification permission not granted; you may not see the "
                                        + "persistent service notification.",
                                Toast.LENGTH_SHORT).show();
                        showPermissionRationaleIfNeeded(
                                Manifest.permission.POST_NOTIFICATIONS,
                                "Enable notification permission to see the floating-window "
                                        + "service status.");
                    }
                });
    }

    @SuppressLint("InlinedApi")
    @Override
    protected void onStart() {
        super.onStart();

        // If the SYSTEM_ALERT_WINDOW overlay permission has not been granted yet, open the
        // overlay settings page (via Activity Result API) and return; the runtime
        // permissions are requested when the user gets back (in the overlay launcher
        // callback). Requesting runtime permissions together with opening the settings
        // page can leave the running permission dialog frozen / unresponsive on Android 15.
        if (!mPresenter.canDrawOverlays()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            mOverlayPermissionLauncher.launch(intent);
            return;
        }

        requestRuntimePermissionsIfNeeded();

        // The app is in the foreground and the overlay permission is granted here:
        // start the caller-info service as a persistent foreground service so it is
        // already resident before any incoming/outgoing call arrives. This avoids
        // cold-starting a foreground service from the background (restricted on
        // Android 15) and the associated process-reaping race.
        FloatWindow.start(this);
    }

    private boolean lacksPermission(String permission) {
        return mPresenter.checkPermission(permission) != PackageManager.PERMISSION_GRANTED;
    }

    private void requestRuntimePermissionsIfNeeded() {
        // Re-entrancy guard: the framework allows only one set of permissions at a
        // time. A second launch while a request is in flight would be suppressed with
        // a "Can request only one set of permissions at a time" warning and would
        // resolve immediately as denied. This guard makes the request path single-shot.
        if (mRuntimePermissionRequestInFlight) {
            return;
        }
        List<String> permissions = new ArrayList<>();
        if (lacksPermission(Manifest.permission.READ_PHONE_STATE)) {
            permissions.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && lacksPermission(Manifest.permission.READ_CALL_LOG)) {
            permissions.add(Manifest.permission.READ_CALL_LOG);
        }
        if (!permissions.isEmpty()) {
            // Launch the runtime permissions; the notification permission is requested
            // afterwards in the runtime-permission callback (serialized), so the
            // framework never sees two simultaneous permission requests.
            mRuntimePermissionRequestInFlight = true;
            try {
                mRuntimePermissionsLauncher.launch(permissions.toArray(new String[0]));
            } catch (Exception e) {
                // e.g. the activity is finishing; never leave the guard stuck in-flight.
                mRuntimePermissionRequestInFlight = false;
                Log.w(TAG, "Failed to launch runtime permission request", e);
            }
        } else {
            // No runtime permission is needed; request the notification permission directly.
            requestNotificationsPermissionIfNeeded();
        }
    }

    @SuppressLint("InlinedApi")
    private void requestNotificationsPermissionIfNeeded() {
        // POST_NOTIFICATIONS is only relevant on Android 13+ (API 33 / TIRAMISU).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && lacksPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            if (mNotificationPermissionRequestInFlight) {
                return;
            }
            mNotificationPermissionRequestInFlight = true;
            try {
                mNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } catch (Exception e) {
                // e.g. the activity is finishing; never leave the guard stuck in-flight.
                mNotificationPermissionRequestInFlight = false;
                Log.w(TAG, "Failed to launch notification permission request", e);
            }
        }
    }

    /**
     * If the user denied a permission with "don't ask again" (rationale would no longer
     * be shown), guide them to the system settings page to enable it manually.
     */
    private void showPermissionRationaleIfNeeded(String permission, String message) {
        if (shouldShowRequestPermissionRationale(permission)) {
            // The user can still be asked again; show a friendly explanation.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        } else {
            // "Don't ask again": guide to settings.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(message
                            + "\n\nPlease enable it in the system settings.")
                    .setPositiveButton(R.string.action_settings,
                            (DialogInterface dialog, int which) ->
                                    startActivity(new Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    private void showOverlayPermissionGuide() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage("Overlay permission is required to show the floating window "
                        + "during calls.\n\nPlease enable it in the system settings.")
                .setPositiveButton(R.string.action_settings,
                        (DialogInterface dialog, int which) -> {
                            Intent intent = new Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            mOverlayPermissionLauncher.launch(intent);
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onStop() {
        // Hide any visible floating window but do NOT close the persistent
        // foreground service. The service is intentionally kept resident (started
        // in onStart()) so that incoming/outgoing calls only need to show a window
        // inside the running service instead of cold-starting a foreground service
        // from a background broadcast (forbidden on Android 15+).
        if (FloatWindow.status() != FloatWindow.STATUS_CLOSE) {
            mWindow.hideWindow();
        }
        mPresenter.clearSearch();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        // The dial pad is open: hide it first, before any other back handling.
        if (mDialpadContainer != null && mDialpadContainer.getVisibility() == View.VISIBLE) {
            hideDialpad();
            return;
        }
        if (FloatWindow.status() != FloatWindow.STATUS_CLOSE) {
            // Close the visible window but keep the persistent foreground service
            // resident so a later incoming/outgoing call does not cold-start an FGS
            // from the background (Android 15+ restriction).
            mWindow.closeWindow();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Binds the search bar (EditText + magnifier button) and the dial pad, and
     * wires up:
     * <ul>
     *   <li>dial-pad input -> real-time prefix filter of the call-log list AND
     *       mirrored into the search EditText (without triggering a search);</li>
     *   <li>search-bar text changes -> same real-time prefix filter;</li>
     *   <li>magnifier button / IME search -> show the offline attribution of the
     *       typed number in the floating window (search).</li>
     * </ul>
     */
    private void initSearchBar() {
        mSearchInput = findViewById(R.id.search_input);
        android.widget.ImageButton mSearchButton = findViewById(R.id.search_button);

        // Clear/back icon on the right of the search field: shown while the
        // query state is active (field non-empty), tapping exits it by clearing
        // the text, restoring the list, closing any search window and showing
        // the full list again. Improves discoverability of how to leave a search.
        mClearSearchDrawable = ContextCompat.getDrawable(this, R.drawable.ic_close_24dp);
        mSearchInput.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP
                    && isClearButtonTapped(event.getX())) {
                clearSearch();
                v.performClick(); // keep accessibility lint happy
                return true;
            }
            return false;
        });

        // Magnifier: show offline attribution for the current input.
        mSearchButton.setOnClickListener(v -> executeSearch());

        // Three-dot overflow: PopupMenu (the app no longer uses a Toolbar/options menu).
        android.widget.ImageButton overflow = findViewById(R.id.overflow_menu);
        overflow.setOnClickListener(v -> {
            android.widget.PopupMenu popup = new android.widget.PopupMenu(this, overflow);
            popup.getMenuInflater().inflate(R.menu.menu_main, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> onOptionsItemSelected(item));
            popup.show();
        });

        mSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                executeSearch();
                return true;
            }
            return false;
        });

        // Typing in the search bar filters the list in real time. Stored as a
        // field so {@link #syncSearchInput} can temporarily detach it while the
        // dial pad mirrors digits into the field.
        mSearchTextWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                // Never trigger a search while typing; only filter the list.
                mWindow.closeWindow();
                mRecyclerView.setVisibility(View.VISIBLE);
                mMainLayout.setBackgroundColor(ContextCompat.getColor(MainActivity.this,
                        R.color.transparent));
                applyFilter(s == null ? "" : s.toString());
                // Reflect the query state on the clear/back icon.
                updateClearButtonVisibility(s);
            }
        };
        mSearchInput.addTextChangedListener(mSearchTextWatcher);
    }

    /**
     * Binds the dial pad FAB / container and wires up the dial + prefix-filter
     * behaviour. Called once from {@link #onCreate}.
     */
    private void initDialpad() {
        mFabDialpad = (FloatingActionButton) findViewById(R.id.fab_dialpad);
        mDialpadContainer = findViewById(R.id.dialpad_container);
        mDialpad = (DialpadView) findViewById(R.id.dialpad);

        mFabDialpad.setOnClickListener(v -> toggleDialpad());

        mDialpad.setOnDialListener(new DialpadView.OnDialListener() {
            @Override
            public void onDial(String number) {
                dialNumber(number);
            }
        });

        // Prefix-filter the list in real time as the user types, and mirror the
        // digits into the search EditText so the magnifier can search them later.
        // Bottom-row collapse key (5th row, 3rd cell) hides the dial pad; the
        // FAB is restored by {@link #hideDialpad()} as the reopen entry point.
        mDialpad.setOnToggleListener(this::toggleDialpad);

        mDialpad.setOnInputChangedListener(new DialpadView.OnInputChangedListener() {
            @Override
            public void onInputChanged(String number) {
                // Dial-pad input goes through syncSearchInput (which detaches the
                // search watcher), so a leftover search attribution window would not
                // auto-dismiss. Close it explicitly here to avoid residue/stacking.
                mWindow.closeSearchWindow();
                applyFilter(number);
                syncSearchInput(number);
            }
        });
    }

    /**
     * Executes an offline lookup for the current search/dial-pad input and shows
     * the attribution in the floating window. No-op for empty input.
     */
    private void executeSearch() {
        String query = mSearchInput.getText() == null
                ? "" : mSearchInput.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        if (System.currentTimeMillis() - mLastSearchTime > 1000) {
            mPresenter.search(query);
            mRecyclerView.setVisibility(View.INVISIBLE);
            mMainLayout.setBackgroundColor(ContextCompat.getColor(MainActivity.this,
                    R.color.dark));
            mLastSearchTime = System.currentTimeMillis();
        }
    }

    /**
     * True when the {@code x} coordinate falls on the clear (drawableEnd) icon
     * of the search field, i.e. the tappable area to the right of the text.
     */
    private boolean isClearButtonTapped(float x) {
        return x >= mSearchInput.getWidth() - mSearchInput.getTotalPaddingRight()
                && x <= mSearchInput.getWidth() - mSearchInput.getPaddingRight();
    }

    /**
     * Shows the clear/back icon in the search field while the query state is
     * active (field non-empty); hides it otherwise.
     */
    private void updateClearButtonVisibility(CharSequence text) {
        if (mClearSearchDrawable == null) {
            return;
        }
        if (text != null && !text.toString().isEmpty()) {
            mSearchInput.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                    mClearSearchDrawable, null);
        } else {
            mSearchInput.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                    null, null);
        }
    }

    /**
     * Exits the search/query state from the clear/back button: empties the
     * search field, restores the list visibility/background, dismisses any
     * search floating window and resets to the full call-log list.
     */
    private void clearSearch() {
        mSearchInput.setText("");
        mWindow.closeWindow();
        applyFilter("");
        restoreListView();
        updateClearButtonVisibility("");
    }

    /**
     * Mirrors the dial-pad input into the search field without triggering a
     * search or re-filtering (the filter is applied by the dial-pad callback).
     */
    private void syncSearchInput(String number) {
        mSearchInput.removeTextChangedListener(mSearchTextWatcher);
        mSearchInput.setText(number);
        mSearchInput.setSelection(mSearchInput.getText() == null
                ? 0 : mSearchInput.getText().length());
        mSearchInput.addTextChangedListener(mSearchTextWatcher);
        // Dial-pad input bypasses the search watcher, so refresh the clear icon
        // manually to reflect the query state.
        updateClearButtonVisibility(number);
    }

    private void toggleDialpad() {
        if (mDialpadContainer.getVisibility() == View.VISIBLE) {
            hideDialpad();
        } else {
            showDialpad();
        }
    }

    private void showDialpad() {
        // Opening the dial pad dismisses any leftover search attribution window
        // so it does not remain on screen while the user dials.
        mWindow.closeSearchWindow();
        mDialpadContainer.setVisibility(View.VISIBLE);
        // The bottom-row collapse key now replaces the FAB as the close entry
        // while the pad is open; hide the FAB to avoid overlap with the call key.
        mFabDialpad.setVisibility(View.GONE);
        // Keep the top rows of the list visible above the dial pad by padding the
        // RecyclerView bottom with the dial pad height.
        mDialpadContainer.post(new Runnable() {
            @Override
            public void run() {
                int pad = mDialpadContainer.getHeight();
                mRecyclerView.setPadding(0, 0, 0, pad);
            }
        });
        mDialpad.requestFocus();
    }

    private void hideDialpad() {
        mDialpadContainer.setVisibility(View.GONE);
        mRecyclerView.setPadding(0, 0, 0, 0);
        mDialpad.clear();
        applyFilter("");
        // Leaving the search/query state is mandatory: the magnifier
        // (executeSearch) may have set the list INVISIBLE + darkened the
        // background, and without restoring it here the list stays blank after
        // collapsing the pad (via the bottom-row toggle or the system back key).
        restoreListView();
        // Restore the FAB as the reopen entry point once the pad is collapsed.
        mFabDialpad.setVisibility(View.VISIBLE);
    }

    /**
     * Restores the call-log list and background after a query/search state.
     * {@link #executeSearch()} hides the RecyclerView (INVISIBLE) and darkens
     * {@code main_layout}. Restore both so the list is clearly visible again.
     */
    private void restoreListView() {
        mRecyclerView.setVisibility(View.VISIBLE);
        mMainLayout.setBackgroundColor(ContextCompat.getColor(MainActivity.this,
                R.color.transparent));
    }

    /**
     * Dials the given number by launching the system dialer pre-filled via
     * {@code ACTION_DIAL} (no CALL_PHONE permission needed; the user confirms the
     * call in the system dialer).
     */
    private void dialNumber(String number) {
        hideDialpad();
        if (number == null || number.length() == 0) {
            Toast.makeText(this, R.string.dialpad_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "ACTION_DIAL failed: " + e.getMessage(), e);
            Toast.makeText(this, R.string.dialpad_hint, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Filters the call-log list by prefix of {@code input}; empty input restores
     * the full list. Numbers are compared by stripped digits (no spaces).
     */
    private void applyFilter(String input) {
        if (input == null || input.length() == 0) {
            mCallerAdapter.replaceData(mAllInCalls);
            mEmptyText.setVisibility(mAllInCalls.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
        final String query = input.replaceAll(" ", "");
        List<InCall> filtered = new ArrayList<>();
        for (InCall inCall : mAllInCalls) {
            String number = inCall.getNumber() == null ? "" : inCall.getNumber();
            if (number.replaceAll(" ", "").startsWith(query)) {
                filtered.add(inCall);
            }
        }
        mCallerAdapter.replaceData(filtered);
        mEmptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * Runs the incremental CallLog import on a background thread and refreshes the
     * list once done. Safe to call on every launch: the importer only fetches
     * {@code _ID > lastSync} beyond the first run, so no re-walk / re-tagging.
     */
    private void startCallLogImport() {
        mCallLogImporter.importCallLog(this)
                .subscribe(new Consumer<List<InCall>>() {
                    @Override
                    public void accept(List<InCall> imported) {
                        if (imported != null && !imported.isEmpty()) {
                            mPresenter.loadInCallList();
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) {
                        Log.w(TAG, "CallLog import failed: " + throwable.getMessage());
                    }
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.action_float_window:
                if (FloatWindow.status() == FloatWindow.STATUS_CLOSE) {
                    mWindow.showTextWindow(R.string.float_window_hint, Window.Type.POSITION);
                } else {
                    // User explicitly turned the floating-window feature off: close
                    // the window AND stop the resident foreground service (removes
                    // the persistent notification too).
                    mWindow.closeWindow();
                    FloatWindow.stop(this);
                }
                break;
            case R.id.action_clear_history:
                clearHistory();
                break;
            case R.id.action_clear_cache:
                clearCache();
                break;
        }

        return true;
    }

    private void clearHistory() {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.action_clear_history));
        builder.setMessage(getString(R.string.clear_history_message));
        builder.setCancelable(true);
        builder.setPositiveButton(getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mPresenter.clearAll();
                    }
                });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    private void clearCache() {

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.action_clear_cache));
        builder.setMessage(getString(R.string.clear_cache_confirm_message));
        builder.setCancelable(true);
        builder.setPositiveButton(getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mPresenter.clearCache();
                        Snackbar.make(mTopBar, R.string.clear_cache_message, Snackbar.LENGTH_LONG)
                                .setAction(getString(R.string.ok), null)
                                .show();
                    }
                });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }

    @Override
    public void showNoCallLog(boolean show) {
        mEmptyText.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void showLoading(boolean active) {
        mSwipeRefreshLayout.setRefreshing(active);
    }

    @Override
    public void showCallLogs(List<InCall> inCalls) {
        mAllInCalls.clear();
        mAllInCalls.addAll(inCalls);
        // If a dial-pad filter is active, re-apply it instead of showing everything.
        if (mDialpad != null && mDialpad.getInput().length() > 0) {
            applyFilter(mDialpad.getInput());
        } else {
            mCallerAdapter.replaceData(inCalls);
        }
    }

    @Override
    public void showSearchResult(INumber number) {
        Log.d(TAG, "showSearchResult: " + number.getNumber());
        mWindow.showWindow(number, Window.Type.SEARCH);
    }

    @Override
    public void showSearching() {
        Log.d(TAG, "showSearching");
        mWindow.showTextWindow(R.string.searching, Window.Type.SEARCH);
    }

    @Override
    public void showSearchFailed(boolean isOnline) {
        Log.d(TAG, "showSearchFailed: isOnline=" + isOnline);
        if (isOnline) {
            mWindow.sendData(FloatWindow.WINDOW_ERROR, R.string.online_failed, Window.Type.SEARCH);
        } else {
            mWindow.showTextWindow(R.string.offline_failed, Window.Type.SEARCH);
        }
    }

    @Override
    public Context getContext() {
        return this.getApplicationContext();
    }

    @Override
    public void showBottomSheet(InCall inCall) {
        // show bottom sheet dialog
        MainBottomSheetFragment.newInstance(inCall).show(getSupportFragmentManager(), "dialog");
    }

    @Override
    public void dial(String number) {
        dialNumber(number);
    }

    @Override
    public void attachCallerMap(Map<String, Caller> callers) {
        // Load the current list first, then run the incremental CallLog import
        // (idempotent: nothing happens unless there are rows newer than the last
        // synced anchor) which refreshes the list when new calls were found.
        mPresenter.loadInCallList();
        startCallLogImport();
    }

    @Override
    public void setPresenter(MainContract.Presenter presenter) {
        mPresenter = presenter;
    }
}

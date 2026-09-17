package com.github.soinlee.callassistant.fragment;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.appcompat.app.AppCompatDialogFragment;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.apmem.tools.layouts.FlowLayout;
import com.github.soinlee.callassistant.R;
import com.github.soinlee.callassistant.application.Application;
import com.github.soinlee.callassistant.contract.MainBottomContact;
import com.github.soinlee.callassistant.contract.MainContract;
import com.github.soinlee.callassistant.di.DaggerMainBottomComponent;
import com.github.soinlee.callassistant.di.modules.AppModule;
import com.github.soinlee.callassistant.di.modules.MainBottomModule;
import com.github.soinlee.callassistant.model.TextColorPair;
import com.github.soinlee.callassistant.model.db.Caller;
import com.github.soinlee.callassistant.model.db.InCall;
import com.github.soinlee.callassistant.model.db.MarkedRecord;
import com.github.soinlee.callassistant.model.setting.Setting;
import com.github.soinlee.callassistant.utils.Utils;

import javax.inject.Inject;

public class MainBottomSheetFragment extends AppCompatDialogFragment
        implements View.OnClickListener, MainBottomContact.View {

    @Inject
    Setting mSetting;
    @Inject
    MainBottomContact.Presenter mPresenter;
    private FrameLayout mFrameLayout;
    private View mBottomSheet;
    private TextView mNumber;
    private TextView mGeo;
    private TextView mTime;
    private TextView mRingTime;
    private TextView mDuration;
    private TextView mName;
    private TextView mSource;
    private FlowLayout mFlowLayout;
    private Button mHarassment;
    private Button mFraud;
    private Button mAdvertising;
    private Button mExpress;
    private Button mRestaurant;
    private Button mCustom;
    private Button mClearMark;
    private EditText mCustomText;
    private View mDivider;
    private TextView mEdit;
    private FloatingActionButton mFab;

    public MainBottomSheetFragment() {
        DaggerMainBottomComponent.builder()
                .appModule(new AppModule(Application.getApplication()))
                .mainBottomModule(new MainBottomModule(this))
                .build()
                .inject(this);
    }

    public static MainBottomSheetFragment newInstance(InCall inCall) {
        MainBottomSheetFragment fragment = new MainBottomSheetFragment();
        fragment.bindData(inCall);
        return fragment;
    }

    private void bindData(InCall inCall) {
        mPresenter.bindData(inCall);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Dialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_main_bottom_sheet);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT));
        }

        mFrameLayout = (FrameLayout) dialog.findViewById(R.id.design_bottom_sheet);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mFrameLayout.setBackground(new ColorDrawable(Color.TRANSPARENT));
        } else {
            //noinspection deprecation
            mFrameLayout.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        mBottomSheet = dialog.findViewById(R.id.bottom_sheet);
        mNumber = (TextView) dialog.findViewById(R.id.number);
        mGeo = (TextView) dialog.findViewById(R.id.geo);
        mTime = (TextView) dialog.findViewById(R.id.time);
        mRingTime = (TextView) dialog.findViewById(R.id.ring_time);
        mDuration = (TextView) dialog.findViewById(R.id.duration);
        mName = (TextView) dialog.findViewById(R.id.name);
        mSource = (TextView) dialog.findViewById(R.id.source);

        mFlowLayout = (FlowLayout) dialog.findViewById(R.id.tags);
        mHarassment = (Button) dialog.findViewById(R.id.harassment);
        mFraud = (Button) dialog.findViewById(R.id.fraud);
        mAdvertising = (Button) dialog.findViewById(R.id.advertising);
        mExpress = (Button) dialog.findViewById(R.id.express);
        mRestaurant = (Button) dialog.findViewById(R.id.restaurant);
        mCustom = (Button) dialog.findViewById(R.id.custom);
        mClearMark = (Button) dialog.findViewById(R.id.clear_mark);
        mCustomText = (EditText) dialog.findViewById(R.id.custom_text);
        mDivider = dialog.findViewById(R.id.divider);
        mEdit = (TextView) dialog.findViewById(R.id.edit);
        mFab = (FloatingActionButton) dialog.findViewById(R.id.fab);

        mHarassment.setOnClickListener(this);
        mFraud.setOnClickListener(this);
        mAdvertising.setOnClickListener(this);
        mExpress.setOnClickListener(this);
        mRestaurant.setOnClickListener(this);
        mCustom.setOnClickListener(this);

        // Clear-mark button: removes the applied mark (falls back to offline
        // attribution and refreshes the list) then closes the sheet.
        mClearMark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPresenter.clearMark();
                dismiss();
            }
        });

        mCustomText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mPresenter.markCustom(textView.getText().toString());
                    return true;
                }
                return false;
            }
        });

        mPresenter.start();

        return dialog;
    }

    private void selectTag(String name) {

        int type = Utils.typeFromString(name);
        switch (MarkedRecord.MarkType.fromInt(type)) {
            case HARASSMENT:
                mHarassment.setBackgroundResource(R.color.pressed);
                break;
            case FRAUD:
                mFraud.setBackgroundResource(R.color.pressed);
                break;
            case ADVERTISING:
                mAdvertising.setBackgroundResource(R.color.pressed);
                break;
            case EXPRESS_DELIVERY:
                mExpress.setBackgroundResource(R.color.pressed);
                break;
            case RESTAURANT_DELIVER:
                mRestaurant.setBackgroundResource(R.color.pressed);
                break;
            default:
                mCustom.setBackgroundResource(R.color.pressed);
                mCustomText.setVisibility(View.VISIBLE);
                mCustomText.setText(name);
                break;
        }
    }

    /**
     * Shows or hides the tag-assignment buttons (fraud/harassment/advertising/
     * express/restaurant/custom). Used to swap between "assign a tag" and
     * "clear the mark" UIs based on whether the number is already marked.
     */
    private void setTagButtonsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        mHarassment.setVisibility(visibility);
        mFraud.setVisibility(visibility);
        mAdvertising.setVisibility(visibility);
        mExpress.setVisibility(visibility);
        mRestaurant.setVisibility(visibility);
        mCustom.setVisibility(visibility);
    }

    @Override
    public void onClick(View view) {

        TypedValue normal = new TypedValue();
        getContext().getTheme().resolveAttribute(R.attr.selectableItemBackground, normal, true);

        mHarassment.setBackgroundResource(normal.resourceId);
        mFraud.setBackgroundResource(normal.resourceId);
        mAdvertising.setBackgroundResource(normal.resourceId);
        mExpress.setBackgroundResource(normal.resourceId);
        mRestaurant.setBackgroundResource(normal.resourceId);
        mCustom.setBackgroundResource(normal.resourceId);

        view.setBackgroundResource(R.color.pressed);

        mPresenter.markClicked(view.getId());
    }

    @Override
    public void setPresenter(MainContract.Presenter presenter) {

    }

    @Override
    public void init(InCall inCall, Caller caller) {
        mNumber.setText(inCall.getNumber());

        String geo = caller.getGeo().trim();
        if (geo.isEmpty()) {
            geo = getResources().getString(R.string.no_geo);
        }

        mGeo.setText(geo);
        mTime.setText(inCall.getReadableTime());
        mRingTime.setText(Utils.readableTime(inCall.getRingTime()));
        mDuration.setText(Utils.readableTime(inCall.getDuration()));

        String name = caller.getName();

        updateMarkName(name);

        mSource.setText(caller.getSource());

        // set bottom sheet background
        updateBackgroundColor(caller.getName());

        if (mPresenter.canMark()) {
            mDivider.setVisibility(View.VISIBLE);
            mFlowLayout.setVisibility(View.VISIBLE);
            mFab.setVisibility(View.VISIBLE);

            // The pink edit FAB now opens the dedicated mark-editing page
            // (MarkActivity) pre-filled with the current number, then closes the
            // bottom sheet. It is no longer a no-op "expand only" button.
            mFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String number = mNumber.getText() != null
                            ? mNumber.getText().toString() : null;
                    if (!android.text.TextUtils.isEmpty(number)) {
                        Utils.startMarkActivity(getContext(), number);
                    }
                    dismiss();
                }
            });

            if (caller.isMark()) {
                // Already marked: show only the "clear mark" button, hide the
                // tag-editing row so the mark can be removed (not re-assigned).
                mEdit.setVisibility(View.GONE);
                mClearMark.setVisibility(View.VISIBLE);
                mCustomText.setVisibility(View.GONE);
                setTagButtonsVisible(false);
            } else {
                mEdit.setVisibility(View.VISIBLE);
                mClearMark.setVisibility(View.GONE);
                mCustomText.setVisibility(View.GONE);
                setTagButtonsVisible(true);
                selectTag(name);
            }
        } else {
            BottomSheetBehavior.from(mFrameLayout).setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void updateMark(int viewId, Caller caller) {
        if (viewId == mCustom.getId()) {
            mCustomText.setVisibility(View.VISIBLE);
            mCustomText.setText(caller.getName() != null ? caller.getName() : "");
        } else {
            mCustomText.setVisibility(View.GONE);
        }
    }

    @Override
    public void updateMarkName(String name) {
        if (name == null || name.isEmpty()) {
            name = getResources().getString(R.string.no_marked_name);
        }

        mName.setText(name);
        updateBackgroundColor(name);
    }

    private void updateBackgroundColor(String name) {
        TextColorPair colorPair = TextColorPair.from(name);
        //noinspection ResourceAsColor
        mBottomSheet.setBackgroundColor(colorPair.color);
    }

    public class BottomSheetDialog extends com.google.android.material.bottomsheet.BottomSheetDialog {

        public BottomSheetDialog(@NonNull Context context) {
            super(context);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // fix dark status bar https://code.google.com/p/android/issues/detail?id=202691#c10
            int screenHeight = mSetting.getScreenHeight();
            int statusBarHeight = mSetting.getStatusBarHeight();
            // TargetSdk 35+ forces edge-to-edge: subtract the system navigation
            // bar height too, otherwise the sheet is drawn underneath the
            // three-button / gesture navbar and its lowest row (e.g. the
            // custom-tag editor) becomes un-tappable.
            int navBarHeight = 0;
            int navRes = getContext().getResources().getIdentifier(
                    "navigation_bar_height", "dimen", "android");
            if (navRes > 0) {
                navBarHeight = getContext().getResources().getDimensionPixelSize(navRes);
            }
            int dialogHeight = screenHeight - statusBarHeight - navBarHeight;
            if (getWindow() != null) {
                getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        dialogHeight == 0 ? ViewGroup.LayoutParams.MATCH_PARENT : dialogHeight);
            }
        }
    }
}

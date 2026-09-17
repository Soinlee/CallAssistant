package com.github.soinlee.callassistant.activity;

import android.os.Bundle;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;
import android.webkit.WebView;

import com.github.soinlee.callassistant.R;

public class LicensesActivity extends BaseActivity {

    private final static String ACTION_LICENSE = "com.github.soinlee.callassistant.action.VIEW_LICENSES";
    private final static String ACTION_PRIVACY = "com.github.soinlee.callassistant.action.VIEW_PRIVACY";
    private final static String ACTION_FEATURE = "com.github.soinlee.callassistant.action.VIEW_FEATURE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 用 AppBarLayout(+Toolbar) 承接顶部栏（edge-to-edge 下蓝色栏贴顶）。
        // 必须在基类 BaseActivity 完成 setContentView 之后接管。
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        WebView webView = (WebView) findViewById(R.id.webView);

        String action = getIntent().getAction();
        String url = "file:///android_res/raw/licenses.html";

        switch (action) {
            case ACTION_LICENSE:
                // 开源许可：标题用 license、默认 url 已是 licenses.html，无需重置
                setTitle(R.string.license);
                break;
            case ACTION_PRIVACY:
                setTitle(R.string.privacy_notice);
                url = "file:///android_res/raw/privacy_notice.html";
                break;
            case ACTION_FEATURE:
                setTitle(R.string.feature_notice);
                url = "file:///android_res/raw/feature_notice.html";
                break;
            default:
                setTitle(R.string.license);
                break;
        }
        if (webView != null) {
            webView.loadUrl(url);
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_licenses;
    }

    @Override
    protected int getTitleId() {
        return R.string.license;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

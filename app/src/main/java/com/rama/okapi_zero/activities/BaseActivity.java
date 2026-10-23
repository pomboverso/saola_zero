package com.rama.okapi_zero.activities;

import android.app.Activity;
import android.view.View;

import com.rama.okapi_zero.R;
import com.rama.okapi_zero.helpers.SystemBars;
import com.rama.okapi_zero.managers.PrefsManager;
import com.rama.okapi_zero.managers.ZoomManager;

public abstract class BaseActivity extends Activity {
    @Override
    protected void onResume() {
        super.onResume();
        SystemBars.setStatusBarVisible(this, PrefsManager.getInstance(this).isStatusBarVisible());
        View root = findViewById(R.id.root);
        if (root != null) {
            ZoomManager.apply(this, root);
        }
    }
}

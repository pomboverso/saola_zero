package com.rama.saola.activities;

import android.app.Activity;
import android.view.View;

import com.rama.saola.R;
import com.rama.saola.helpers.SystemBars;
import com.rama.saola.managers.PrefsManager;
import com.rama.saola.managers.ZoomManager;

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

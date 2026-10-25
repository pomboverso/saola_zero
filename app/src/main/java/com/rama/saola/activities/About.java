package com.rama.saola.activities;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.rama.saola.R;
import com.rama.saola.helpers.SystemBars;
import com.rama.saola.managers.FontManager;
import com.rama.saola.managers.ThemeManager;

public class About extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        View root = findViewById(R.id.root);
        SystemBars.applyInsets(root);
        FontManager.apply(root, FontManager.getJersey25(this));
        ThemeManager.applyTheme(this, root);
        TextView appName = findViewById(R.id.name_version);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            appName.setText(getString(R.string.app_version, getString(R.string.app_name), info.versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            appName.setText(R.string.app_name);
        }
        findViewById(R.id.go_back).setOnClickListener(v -> finish());
    }
}

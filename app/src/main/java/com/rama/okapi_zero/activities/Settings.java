package com.rama.okapi_zero.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import com.rama.okapi_zero.R;
import com.rama.okapi_zero.helpers.SystemBars;
import com.rama.okapi_zero.managers.FontManager;
import com.rama.okapi_zero.managers.PrefsManager;
import com.rama.okapi_zero.managers.ThemeManager;
import com.rama.okapi_zero.managers.ZoomManager;
import com.rama.okapi_zero.objects.Themes;
import com.rama.okapi_zero.widgets.WdCheckbox;
import com.rama.okapi_zero.widgets.WdRadioGroup;

import java.util.List;

public class Settings extends BaseActivity {
    private PrefsManager prefs;
    private View root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = PrefsManager.getInstance(this);
        root = findViewById(R.id.root);
        SystemBars.applyInsets(root);
        setupThemes();
        setupSystemSection();
        setupZoomSection();
        FontManager.apply(root, FontManager.getJersey25(this));
        ThemeManager.applyTheme(this, root);
        findViewById(R.id.hid_test_button).setOnClickListener(v -> startActivity(new Intent(Settings.this, HidTest.class)));
        findViewById(R.id.about_button).setOnClickListener(v -> startActivity(new Intent(Settings.this, About.class)));
        findViewById(R.id.go_back).setOnClickListener(v -> finish());
    }

    private void setupThemes() {
        if (Build.VERSION.SDK_INT < 11) {
            findViewById(R.id.themes_container).setVisibility(View.GONE);
            return;
        }
        setupThemeGroup((WdRadioGroup) findViewById(R.id.theme_dark_group), Themes.dark(), prefs.getDarkTheme(), false);
        setupThemeGroup((WdRadioGroup) findViewById(R.id.theme_light_group), Themes.light(), prefs.getLightTheme(), true);
    }

    private void setupThemeGroup(WdRadioGroup group, final List<Themes.Palette> palettes, String current, final boolean light) {
        int checkedId = -1;
        for (int i = 0; i < palettes.size(); i++) {
            int id = group.addOption(palettes.get(i).label).getId();
            if (palettes.get(i).id.equals(current)) checkedId = id;
        }
        group.check(checkedId);
        group.setOnCheckedChangeListener((g, id) -> {
            String themeId = palettes.get(g.getCheckedIndex()).id;
            if (light) {
                prefs.setLightTheme(themeId);
            } else {
                prefs.setDarkTheme(themeId);
            }
            ThemeManager.applyTheme(Settings.this, root);
        });
    }

    private void setupSystemSection() {
        bindCheckbox(R.id.show_status_bar, PrefsManager.KEY_STATUS_BAR, prefs.isStatusBarVisible());
        bindCheckbox(R.id.keep_screen_awake, PrefsManager.KEY_KEEP_AWAKE, prefs.isKeepScreenAwake());
        bindCheckbox(R.id.quick_erase, PrefsManager.KEY_QUICK_ERASE, prefs.isQuickEraseEnabled());
    }

    private void bindCheckbox(int id, final String key, boolean value) {
        WdCheckbox checkbox = findViewById(id);
        checkbox.setChecked(value);
        checkbox.setOnCheckedChangeListener(isChecked -> {
            prefs.setBoolean(key, isChecked);
            SystemBars.setStatusBarVisible(Settings.this, prefs.isStatusBarVisible());
        });
    }

    private void setupZoomSection() {
        final EditText zoom = findViewById(R.id.zoom);
        zoom.setText(String.valueOf(ZoomManager.getPercent(this)));
        zoom.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyZoom();
            }
            return false;
        });
        zoom.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                applyZoom();
            }
        });
    }

    private void applyZoom() {
        saveZoomFromField();
        ZoomManager.apply(this, root);
    }

    private void saveZoomFromField() {
        EditText zoom = findViewById(R.id.zoom);
        int percent;
        try {
            percent = ZoomManager.clamp(Integer.parseInt(zoom.getText().toString().trim()));
        } catch (NumberFormatException e) {
            percent = ZoomManager.getPercent(this);
        }
        String normalized = String.valueOf(percent);
        if (!normalized.equals(zoom.getText().toString())) {
            zoom.setText(normalized);
        }
        prefs.setZoomPercent(percent);
    }

    @Override
    protected void onPause() {
        saveZoomFromField();
        super.onPause();
    }
}

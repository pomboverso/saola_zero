package com.rama.okapi_zero.managers;

import android.content.Context;
import android.content.SharedPreferences;

import com.rama.okapi_zero.objects.PrefTheme;

public class PrefsManager {
    private static final String PREFS_NAME = "okapi";
    private static final String KEY_THEME = "settings:theme";
    private static final String KEY_ROLLED_THEME = "settings:rolled_theme";
    private static final String KEY_ZOOM_PERCENT = "settings:zoom_percent";
    public static final String KEY_STATUS_BAR = "settings:show_status_bar";
    public static final String KEY_KEEP_AWAKE = "settings:keep_screen_awake";
    private static PrefsManager instance;
    private final SharedPreferences prefs;

    private PrefsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized PrefsManager getInstance(Context context) {
        if (instance == null) {
            instance = new PrefsManager(context);
        }
        return instance;
    }

    public String getTheme() {
        return prefs.getString(KEY_THEME, PrefTheme.DEFAULT_THEME);
    }

    public void setTheme(String themeId) {
        prefs.edit().putString(KEY_THEME, themeId).commit();
    }

    /** The palette id the Random theme last picked, or "" if it never rolled. */
    public String getRolledThemeId() {
        return prefs.getString(KEY_ROLLED_THEME, "");
    }

    public void setRolledTheme(String themeId) {
        prefs.edit().putString(KEY_ROLLED_THEME, themeId).commit();
    }

    public int getZoomPercent() {
        return prefs.getInt(KEY_ZOOM_PERCENT, 100);
    }

    public void setZoomPercent(int percent) {
        prefs.edit().putInt(KEY_ZOOM_PERCENT, percent).commit();
    }

    public boolean isStatusBarVisible() {
        return getBoolean(KEY_STATUS_BAR, false);
    }

    public boolean isKeepScreenAwake() {
        return getBoolean(KEY_KEEP_AWAKE, true);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).commit();
    }
}
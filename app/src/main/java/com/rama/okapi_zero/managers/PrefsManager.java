package com.rama.okapi_zero.managers;

import android.content.Context;
import android.content.SharedPreferences;

import com.rama.okapi_zero.objects.PrefTheme;

public class PrefsManager {
    private static final String PREFS_NAME = "okapi";
    private static final String KEY_THEME_DARK = "settings:theme_dark";
    private static final String KEY_THEME_LIGHT = "settings:theme_light";
    private static final String KEY_LIGHT_MODE = "settings:light_mode";
    private static final String KEY_ZOOM_PERCENT = "settings:zoom_percent";
    public static final String KEY_STATUS_BAR = "settings:show_status_bar";
    public static final String KEY_KEEP_AWAKE = "settings:keep_screen_awake";
    public static final String KEY_QUICK_ERASE = "settings:quick_erase";
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

    public String getDarkTheme() {
        return prefs.getString(KEY_THEME_DARK, PrefTheme.DEFAULT_DARK);
    }

    public void setDarkTheme(String themeId) {
        prefs.edit().putString(KEY_THEME_DARK, themeId).commit();
    }

    public String getLightTheme() {
        return prefs.getString(KEY_THEME_LIGHT, PrefTheme.DEFAULT_LIGHT);
    }

    public void setLightTheme(String themeId) {
        prefs.edit().putString(KEY_THEME_LIGHT, themeId).commit();
    }

    public boolean isLightMode() {
        return getBoolean(KEY_LIGHT_MODE, false);
    }

    public void setLightMode(boolean value) {
        setBoolean(KEY_LIGHT_MODE, value);
    }

    public String getActiveTheme() {
        return isLightMode() ? getLightTheme() : getDarkTheme();
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

    public boolean isQuickEraseEnabled() {
        return getBoolean(KEY_QUICK_ERASE, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return prefs.getBoolean(key, defaultValue);
    }

    public void setBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).commit();
    }
}

package com.rama.saola.managers;

import android.content.Context;
import android.content.SharedPreferences;

import com.rama.saola.objects.PrefTheme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class PrefsManager {
    private static final String PREFS_NAME = "saola";
    public static final String DEFAULT_GROUP_ID = "ungrouped";
    public static final String DEFAULT_GROUP_LABEL = "Default";
    public static final String ARCHIVED_GROUP_ID = "archived";
    public static final String ARCHIVED_GROUP_LABEL = "Archived";
    private static final String KEY_COLLAPSE_ON_HOME = "settings:collapse_groups_on_home";
    private static final String KEY_ONLY_ONE_GROUP_OPEN = "settings:only_one_group_open";
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

    // ---- Groups ----

    private String key(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private List<String> splitCsv(String value) {
        List<String> result = new ArrayList<String>();
        if (value == null || value.length() == 0) return result;
        for (String part : value.split(",")) {
            if (part.length() > 0) result.add(part);
        }
        return result;
    }

    private String joinCsv(Collection<String> values) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String value : values) {
            if (!first) sb.append(',');
            sb.append(value);
            first = false;
        }
        return sb.toString();
    }

    /** The default and archived groups always exist and cannot be deleted. */
    public static boolean isProtectedGroup(String groupId) {
        return DEFAULT_GROUP_ID.equals(groupId) || ARCHIVED_GROUP_ID.equals(groupId);
    }

    /** Always starts with the default and archived groups, then the custom groups in creation order (see getGroupOrder for display order). */
    public List<String> getGroupIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<String>();
        ids.add(DEFAULT_GROUP_ID);
        ids.add(ARCHIVED_GROUP_ID);
        ids.addAll(splitCsv(prefs.getString(key("groups", "ids"), "")));
        return new ArrayList<String>(ids);
    }

    public void addGroupId(String groupId) {
        List<String> ids = getGroupIds();
        if (!ids.contains(groupId)) {
            ids.add(groupId);
            prefs.edit().putString(key("groups", "ids"), joinCsv(ids)).commit();
        }
    }

    public void removeGroupId(String groupId) {
        if (isProtectedGroup(groupId)) return;
        List<String> ids = getGroupIds();
        ids.remove(groupId);
        prefs.edit()
                .putString(key("groups", "ids"), joinCsv(ids))
                .remove(key("group", groupId, "label"))
                .remove(key("group", groupId, "order"))
                .remove(key("group", groupId, "expanded"))
                .remove(key("group", groupId, "visible"))
                .remove(key("group", groupId, "keep_expanded"))
                .commit();
    }

    public String getGroupLabel(String groupId) {
        String fallback = groupId;
        if (DEFAULT_GROUP_ID.equals(groupId)) fallback = DEFAULT_GROUP_LABEL;
        if (ARCHIVED_GROUP_ID.equals(groupId)) fallback = ARCHIVED_GROUP_LABEL;
        return prefs.getString(key("group", groupId, "label"), fallback);
    }

    public void setGroupLabel(String groupId, String label) {
        prefs.edit().putString(key("group", groupId, "label"), label).commit();
    }

    public int getGroupOrder(String groupId) {
        // Unordered custom groups sort before Archived until the user reorders them.
        int fallback = DEFAULT_GROUP_ID.equals(groupId) ? 0 : (ARCHIVED_GROUP_ID.equals(groupId) ? Integer.MAX_VALUE : Integer.MAX_VALUE - 1);
        return prefs.getInt(key("group", groupId, "order"), fallback);
    }

    public void setGroupOrder(String groupId, int order) {
        prefs.edit().putInt(key("group", groupId, "order"), order).commit();
    }

    public boolean isGroupExpanded(String groupId) {
        return prefs.getBoolean(key("group", groupId, "expanded"), true);
    }

    public void setGroupExpanded(String groupId, boolean expanded) {
        prefs.edit().putBoolean(key("group", groupId, "expanded"), expanded).commit();
    }

    public boolean isGroupVisible(String groupId) {
        // Archived starts hidden: archiving is meant to get entries out of the way (search still finds them).
        return prefs.getBoolean(key("group", groupId, "visible"), !ARCHIVED_GROUP_ID.equals(groupId));
    }

    public void setGroupVisible(String groupId, boolean visible) {
        prefs.edit().putBoolean(key("group", groupId, "visible"), visible).commit();
    }

    public boolean isGroupKeepExpanded(String groupId) {
        return prefs.getBoolean(key("group", groupId, "keep_expanded"), false);
    }

    public void setGroupKeepExpanded(String groupId, boolean value) {
        prefs.edit().putBoolean(key("group", groupId, "keep_expanded"), value).commit();
    }

    public boolean shouldCollapseGroupsOnHome() {
        return getBoolean(KEY_COLLAPSE_ON_HOME, false);
    }

    public void setCollapseGroupsOnHome(boolean value) {
        setBoolean(KEY_COLLAPSE_ON_HOME, value);
    }

    public boolean isOnlyOneGroupOpenEnabled() {
        return getBoolean(KEY_ONLY_ONE_GROUP_OPEN, false);
    }

    public void setOnlyOneGroupOpenEnabled(boolean value) {
        setBoolean(KEY_ONLY_ONE_GROUP_OPEN, value);
    }
}

package com.rama.okapi_zero.managers;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Group bookkeeping (labels, order, visibility, expansion) lives in prefs; entries only store a group id. */
public class GroupManager {
    private final PrefsManager prefs;
    private final EntriesManager db;

    public GroupManager(Context context, EntriesManager db) {
        this.prefs = PrefsManager.getInstance(context);
        this.db = db;
    }

    /** Group ids in display order. */
    public List<String> getGroupIds() {
        List<String> ids = new ArrayList<String>(prefs.getGroupIds());
        Collections.sort(ids, (a, b) -> compare(prefs.getGroupOrder(a), prefs.getGroupOrder(b)));
        return ids;
    }

    private static int compare(int a, int b) {
        return a < b ? -1 : (a == b ? 0 : 1);
    }

    public String getGroupLabel(String groupId) {
        return prefs.getGroupLabel(groupId);
    }

    public void renameGroup(String groupId, String label) {
        prefs.setGroupLabel(groupId, FontManager.sanitizeForFont(label));
    }

    public boolean isGroupExpanded(String groupId) {
        return prefs.isGroupKeepExpanded(groupId) || prefs.isGroupExpanded(groupId);
    }

    public boolean isGroupKeepExpanded(String groupId) {
        return prefs.isGroupKeepExpanded(groupId);
    }

    public void toggleGroupKeepExpanded(String groupId) {
        boolean pinned = !prefs.isGroupKeepExpanded(groupId);
        prefs.setGroupKeepExpanded(groupId, pinned);
        if (pinned) prefs.setGroupExpanded(groupId, true);
    }

    public void toggleGroupExpanded(String groupId) {
        if (prefs.isGroupKeepExpanded(groupId)) return;
        boolean shouldExpand = !prefs.isGroupExpanded(groupId);
        prefs.setGroupExpanded(groupId, shouldExpand);
        if (shouldExpand && prefs.isOnlyOneGroupOpenEnabled()) {
            for (String other : getGroupIds()) {
                if (!other.equals(groupId) && !prefs.isGroupKeepExpanded(other)) {
                    prefs.setGroupExpanded(other, false);
                }
            }
        }
    }

    /** Returns true if anything was collapsed. Groups pinned open stay open. */
    public boolean collapseAllGroups() {
        boolean changed = false;
        for (String id : getGroupIds()) {
            if (prefs.isGroupKeepExpanded(id)) continue;
            if (prefs.isGroupExpanded(id)) {
                prefs.setGroupExpanded(id, false);
                changed = true;
            }
        }
        return changed;
    }

    public boolean shouldCollapseGroupsOnHome() {
        return prefs.shouldCollapseGroupsOnHome();
    }

    public boolean isGroupVisible(String groupId) {
        return prefs.isGroupVisible(groupId);
    }

    public void toggleGroupVisible(String groupId) {
        prefs.setGroupVisible(groupId, !prefs.isGroupVisible(groupId));
    }

    /** The group an entry is shown in: its own, or the default group if that one no longer exists. */
    public String resolveGroupId(String groupId) {
        if (groupId == null || !prefs.getGroupIds().contains(groupId)) {
            return PrefsManager.DEFAULT_GROUP_ID;
        }
        return groupId;
    }

    public String createGroup(String baseLabel) {
        String id = "group_" + System.currentTimeMillis();
        prefs.addGroupId(id);
        prefs.setGroupLabel(id, generateUniqueLabel(baseLabel));
        prefs.setGroupVisible(id, true);
        prefs.setGroupExpanded(id, true);
        return id;
    }

    /** Id of the group with this label (case-insensitive), creating it if there is none. Blank means the default group. */
    public String findOrCreateByLabel(String label) {
        String clean = label == null ? "" : FontManager.sanitizeForFont(label.trim());
        if (clean.length() == 0) return PrefsManager.DEFAULT_GROUP_ID;
        for (String id : prefs.getGroupIds()) {
            if (prefs.getGroupLabel(id).trim().equalsIgnoreCase(clean)) return id;
        }
        String id = "group_" + System.currentTimeMillis() + "_" + prefs.getGroupIds().size();
        prefs.addGroupId(id);
        prefs.setGroupLabel(id, clean);
        prefs.setGroupVisible(id, true);
        prefs.setGroupExpanded(id, true);
        return id;
    }

    /** Deletes a group; its entries go to newGroupId. The default group cannot be deleted. */
    public void deleteGroup(String groupId, String newGroupId) {
        if (PrefsManager.isProtectedGroup(groupId) || groupId.equals(newGroupId)) return;
        db.reassignGroup(groupId, newGroupId);
        prefs.removeGroupId(groupId);
        reindexOrder();
    }

    public void moveGroup(String groupId, int direction) {
        List<String> ordered = getGroupIds();
        int index = ordered.indexOf(groupId);
        int targetIndex = index + direction;
        if (index < 0 || targetIndex < 0 || targetIndex >= ordered.size()) return;
        for (int i = 0; i < ordered.size(); i++) {
            prefs.setGroupOrder(ordered.get(i), i);
        }
        String otherId = ordered.get(targetIndex);
        prefs.setGroupOrder(groupId, targetIndex);
        prefs.setGroupOrder(otherId, index);
    }

    private String generateUniqueLabel(String base) {
        List<String> existing = new ArrayList<String>();
        for (String id : prefs.getGroupIds()) {
            existing.add(prefs.getGroupLabel(id).trim().toLowerCase(Locale.getDefault()));
        }
        String label = base;
        int counter = 1;
        while (existing.contains(label.trim().toLowerCase(Locale.getDefault()))) {
            counter++;
            label = base + " " + counter;
        }
        return label;
    }

    private void reindexOrder() {
        List<String> ids = getGroupIds();
        for (int i = 0; i < ids.size(); i++) {
            prefs.setGroupOrder(ids.get(i), i);
        }
    }
}

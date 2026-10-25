package com.rama.saola.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.rama.saola.R;
import com.rama.saola.managers.FontManager;
import com.rama.saola.managers.GroupManager;
import com.rama.saola.managers.ThemeManager;
import com.rama.saola.managers.ZoomManager;
import com.rama.saola.objects.Entry;
import com.rama.saola.objects.Themes;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Entries grouped under collapsible headers, optionally filtered by a search query, with multi-select to move entries between groups. */
public class EntryAdapter extends BaseAdapter {
    public static final int FIELD_USER = 0;
    public static final int FIELD_PASSWORD = 1;
    public static final int FIELD_TOTP = 2;
    public static final int FIELD_URL = 3;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ENTRY = 1;

    public interface Listener {
        void onType(Entry entry, int field);

        void onEdit(Entry entry);

        void onSelectionChanged(boolean active, int count);
    }

    private static class HeaderRow {
        final String groupId;
        final String label;

        HeaderRow(String groupId, String label) {
            this.groupId = groupId;
            this.label = label;
        }
    }

    private static final int[] ACTION_IDS = {R.id.row_user, R.id.row_password, R.id.row_totp, R.id.row_url, R.id.row_edit};

    private final Context context;
    private final GroupManager groupManager;
    private final Listener listener;
    private final DateFormat dateFormat;
    private final List<Object> items = new ArrayList<Object>();
    private final Set<Long> selectedIds = new HashSet<Long>();
    private List<Entry> entries = new ArrayList<Entry>();
    private String query = "";
    private boolean multiSelectMode;

    public EntryAdapter(Context context, GroupManager groupManager, Listener listener) {
        this.context = context;
        this.groupManager = groupManager;
        this.listener = listener;
        this.dateFormat = android.text.format.DateFormat.getDateFormat(context);
    }

    // ---- Data ----

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
        rebuild();
    }

    public void setQuery(String query) {
        this.query = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        rebuild();
    }

    public boolean hasQuery() {
        return query.length() > 0;
    }

    /** Re-reads group state (expanded, visible, renamed...) without reloading entries. */
    public void rebuild() {
        items.clear();
        Map<String, List<Entry>> byGroup = new HashMap<String, List<Entry>>();
        for (Entry e : entries) {
            if (hasQuery() && !matches(e)) continue;
            String groupId = groupManager.resolveGroupId(e.groupId);
            List<Entry> bucket = byGroup.get(groupId);
            if (bucket == null) {
                bucket = new ArrayList<Entry>();
                byGroup.put(groupId, bucket);
            }
            bucket.add(e);
        }
        for (String groupId : groupManager.getGroupIds()) {
            List<Entry> bucket = byGroup.get(groupId);
            if (bucket == null || bucket.isEmpty()) continue;
            // Hidden groups stay out of the way, but a search is an explicit request to find something.
            if (!hasQuery() && !groupManager.isGroupVisible(groupId)) continue;
            Collections.sort(bucket, (a, b) -> {
                int byTitle = a.title().compareToIgnoreCase(b.title());
                return byTitle != 0 ? byTitle : (a.id < b.id ? -1 : (a.id == b.id ? 0 : 1));
            });
            items.add(new HeaderRow(groupId, groupManager.getGroupLabel(groupId)));
            if (hasQuery() || groupManager.isGroupExpanded(groupId)) {
                items.addAll(bucket);
            }
        }
        pruneSelection();
        notifyDataSetChanged();
    }

    /** Selection only ever covers entries on screen, so "move" cannot touch rows hidden by a search or a collapsed group. */
    private void pruneSelection() {
        if (!multiSelectMode) return;
        Set<Long> visible = new HashSet<Long>();
        for (Object item : items) {
            if (item instanceof Entry) visible.add(((Entry) item).id);
        }
        int before = selectedIds.size();
        selectedIds.retainAll(visible);
        if (selectedIds.isEmpty()) multiSelectMode = false;
        if (multiSelectMode ? selectedIds.size() != before : before > 0) notifySelectionChanged();
    }

    /** Matches name, user and URL. Passwords and TOTP secrets are never searched. */
    private boolean matches(Entry e) {
        return contains(e.name) || contains(e.user) || contains(e.url);
    }

    private boolean contains(String field) {
        return field.toLowerCase(Locale.getDefault()).contains(query);
    }

    // ---- Multi-select ----

    public boolean isMultiSelectMode() {
        return multiSelectMode;
    }

    public Set<Long> getSelectedIds() {
        return new HashSet<Long>(selectedIds);
    }

    public void exitMultiSelectMode() {
        multiSelectMode = false;
        selectedIds.clear();
        notifySelectionChanged();
        notifyDataSetChanged();
    }

    private void enterMultiSelectMode(long id) {
        multiSelectMode = true;
        selectedIds.clear();
        selectedIds.add(id);
        notifySelectionChanged();
        notifyDataSetChanged();
    }

    private void toggleSelection(long id) {
        if (!selectedIds.remove(id)) selectedIds.add(id);
        if (selectedIds.isEmpty()) {
            exitMultiSelectMode();
            return;
        }
        notifySelectionChanged();
        notifyDataSetChanged();
    }

    private void notifySelectionChanged() {
        listener.onSelectionChanged(multiSelectMode, selectedIds.size());
    }

    // ---- BaseAdapter ----

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        Object item = items.get(position);
        return item instanceof Entry ? ((Entry) item).id : -1 - position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof HeaderRow ? TYPE_HEADER : TYPE_ENTRY;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object item = items.get(position);
        if (item instanceof HeaderRow) {
            return getHeaderView((HeaderRow) item, convertView, parent);
        }
        return getEntryView((Entry) item, convertView, parent);
    }

    private View getHeaderView(HeaderRow header, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_header, parent, false);
        }
        final String groupId = header.groupId;
        TextView label = view.findViewById(R.id.header_text);
        label.setTypeface(FontManager.getJersey25(context));
        label.setTextColor(ThemeManager.currentPalette(context).accent);
        // Searching shows every match, so headers are not collapsible then, same as groups pinned open.
        boolean fixed = hasQuery() || groupManager.isGroupKeepExpanded(groupId);
        boolean expanded = groupManager.isGroupExpanded(groupId);
        String symbol = fixed ? "" : (context.getString(expanded ? R.string.label_collapse_indicator : R.string.label_expand_indicator) + " ");
        label.setText(symbol + "------ " + header.label.toUpperCase(Locale.getDefault()));
        if (fixed) {
            view.setOnClickListener(null);
            view.setClickable(false);
        } else {
            view.setOnClickListener(v -> {
                groupManager.toggleGroupExpanded(groupId);
                rebuild();
            });
        }
        ZoomManager.apply(context, view);
        return view;
    }

    private View getEntryView(final Entry entry, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_entry, parent, false);
        }
        Themes.Palette palette = ThemeManager.currentPalette(context);

        ((TextView) view.findViewById(R.id.row_text)).setText(entry.title());
        ((TextView) view.findViewById(R.id.row_date)).setText(dateFormat.format(new Date(entry.updatedAt)));

        ImageView check = view.findViewById(R.id.selection_check);
        if (multiSelectMode) {
            check.setVisibility(selectedIds.contains(entry.id) ? View.VISIBLE : View.INVISIBLE);
        } else {
            check.setVisibility(View.GONE);
        }

        View.OnClickListener select = v -> {
            if (multiSelectMode) toggleSelection(entry.id);
        };
        View.OnLongClickListener longSelect = v -> {
            if (multiSelectMode) {
                toggleSelection(entry.id);
            } else {
                enterMultiSelectMode(entry.id);
            }
            return true;
        };
        int[] selectTargets = {R.id.row_top, R.id.row_text, R.id.row_date};
        for (int id : selectTargets) {
            View target = view.findViewById(id);
            target.setOnClickListener(select);
            target.setOnLongClickListener(longSelect);
        }

        view.findViewById(R.id.row_actions).setVisibility(multiSelectMode ? View.GONE : View.VISIBLE);
        bindAction(view, R.id.row_user, entry.user.length() > 0, () -> listener.onType(entry, FIELD_USER));
        bindAction(view, R.id.row_password, entry.password.length() > 0, () -> listener.onType(entry, FIELD_PASSWORD));
        bindAction(view, R.id.row_totp, entry.totp.length() > 0, () -> listener.onType(entry, FIELD_TOTP));
        bindAction(view, R.id.row_url, entry.url.length() > 0, () -> listener.onType(entry, FIELD_URL));
        bindAction(view, R.id.row_edit, true, () -> listener.onEdit(entry));

        // Rows are created after the activity was themed, so font and colors must be applied here.
        FontManager.apply(view, FontManager.getJersey25(context));
        ThemeManager.applyToTree(context, view);
        ZoomManager.apply(context, view);
        // Only the background signals enabled/disabled; the text color comes from the style.
        // Set after theming so it wins, and always set so recycled rows get reset.
        for (int id : ACTION_IDS) {
            Button button = view.findViewById(id);
            button.setBackgroundColor(button.isEnabled() ? palette.accent : palette.disabled);
        }
        return view;
    }

    private void bindAction(View row, int id, boolean enabled, final Runnable action) {
        Button button = row.findViewById(id);
        button.setEnabled(enabled);
        button.setOnClickListener(v -> action.run());
    }
}

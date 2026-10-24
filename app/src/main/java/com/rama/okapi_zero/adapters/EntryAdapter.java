package com.rama.okapi_zero.adapters;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.rama.okapi_zero.R;
import com.rama.okapi_zero.managers.FontManager;
import com.rama.okapi_zero.managers.ThemeManager;
import com.rama.okapi_zero.managers.ZoomManager;
import com.rama.okapi_zero.objects.Entry;
import com.rama.okapi_zero.objects.Themes;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class EntryAdapter extends BaseAdapter {
    public static final int FIELD_USER = 0;
    public static final int FIELD_PASSWORD = 1;
    public static final int FIELD_TOTP = 2;
    public static final int FIELD_URL = 3;

    public interface Listener {
        void onMoveUp(long id);

        void onMoveDown(long id);

        void onType(Entry entry, int field);

        void onEdit(Entry entry);
    }

    private final Context context;
    private final List<Entry> entries;
    private final boolean sortMode;
    private final Listener listener;
    private final DateFormat dateFormat;

    public EntryAdapter(Context context, List<Entry> entries, boolean sortMode, Listener listener) {
        this.context = context;
        this.entries = entries;
        this.sortMode = sortMode;
        this.listener = listener;
        this.dateFormat = android.text.format.DateFormat.getDateFormat(context);
    }

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public Entry getItem(int position) {
        return entries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return entries.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_entry, parent, false);
        }
        final Entry entry = getItem(position);
        Themes.Palette palette = ThemeManager.currentPalette(context);

        ((TextView) view.findViewById(R.id.row_text)).setText(entry.title());
        ((TextView) view.findViewById(R.id.row_date)).setText(dateFormat.format(new Date(entry.updatedAt)));

        bindMove(view, R.id.ascend_button, R.id.ascend_icon, sortMode && position > 0, palette);
        bindMove(view, R.id.descend_button, R.id.descend_icon, sortMode && position < getCount() - 1, palette);
        view.findViewById(R.id.ascend_button).setOnClickListener(v -> listener.onMoveUp(entry.id));
        view.findViewById(R.id.descend_button).setOnClickListener(v -> listener.onMoveDown(entry.id));

        view.findViewById(R.id.row_actions).setVisibility(sortMode ? View.GONE : View.VISIBLE);
        bindAction(view, R.id.row_user, entry.user.length() > 0, () -> listener.onType(entry, FIELD_USER));
        bindAction(view, R.id.row_password, entry.password.length() > 0, () -> listener.onType(entry, FIELD_PASSWORD));
        bindAction(view, R.id.row_totp, entry.totp.length() > 0, () -> listener.onType(entry, FIELD_TOTP));
        bindAction(view, R.id.row_url, entry.url.length() > 0, () -> listener.onType(entry, FIELD_URL));
        bindAction(view, R.id.row_edit, true, () -> listener.onEdit(entry));

        // Rows are created after the activity was themed, so font and colors must be applied here.
        FontManager.apply(view, FontManager.getJersey25(context));
        ThemeManager.applyToTree(context, view);
        ZoomManager.apply(context, view);
        return view;
    }

    private void bindAction(View row, int id, boolean enabled, final Runnable action) {
        Button button = row.findViewById(id);
        button.setEnabled(enabled);
        button.setTag(enabled ? "accent" : "disabled");
        button.setOnClickListener(v -> action.run());
    }

    private void bindMove(View row, int buttonId, int iconId, boolean enabled, Themes.Palette palette) {
        View button = row.findViewById(buttonId);
        ImageView icon = row.findViewById(iconId);
        button.setVisibility(sortMode ? View.VISIBLE : View.GONE);
        button.setEnabled(enabled);
        icon.setTag(enabled ? "accent" : "disabled");
        icon.setColorFilter(enabled ? palette.accent : palette.disabled, PorterDuff.Mode.SRC_IN);
    }
}

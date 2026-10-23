package com.rama.okapi_zero.adapters;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.rama.okapi_zero.R;
import com.rama.okapi_zero.managers.FontManager;
import com.rama.okapi_zero.managers.ThemeManager;
import com.rama.okapi_zero.managers.ZoomManager;
import com.rama.okapi_zero.objects.Message;
import com.rama.okapi_zero.objects.Themes;

import java.util.List;

public class MessageAdapter extends BaseAdapter {
    public interface Listener {
        void onMoveUp(long id);

        void onMoveDown(long id);
    }

    private final Context context;
    private final List<Message> messages;
    private final boolean sortMode;
    private final Listener listener;

    public MessageAdapter(Context context, List<Message> messages, boolean sortMode, Listener listener) {
        this.context = context;
        this.messages = messages;
        this.sortMode = sortMode;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return messages.size();
    }

    @Override
    public Message getItem(int position) {
        return messages.get(position);
    }

    @Override
    public long getItemId(int position) {
        return messages.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.list_item_message, parent, false);
        }
        final Message message = getItem(position);
        Themes.Palette palette = ThemeManager.currentPalette(context);

        view.setBackgroundColor(palette.base);
        TextView text = view.findViewById(R.id.row_text);
        text.setTypeface(FontManager.getJersey25(context));
        text.setTextColor(palette.text);
        text.setText(message.text);
        ((ImageView) view.findViewById(R.id.message_icon)).setColorFilter(palette.success, PorterDuff.Mode.SRC_IN);

        bindMove(view, R.id.ascend_button, R.id.ascend_icon, sortMode && position > 0, palette);
        bindMove(view, R.id.descend_button, R.id.descend_icon, sortMode && position < getCount() - 1, palette);
        view.findViewById(R.id.ascend_button).setOnClickListener(v -> listener.onMoveUp(message.id));
        view.findViewById(R.id.descend_button).setOnClickListener(v -> listener.onMoveDown(message.id));

        ZoomManager.apply(context, view);
        return view;
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

package com.rama.okapi_zero.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.rama.okapi_zero.R;
import com.rama.okapi_zero.adapters.MessageAdapter;
import com.rama.okapi_zero.helpers.BackHelper;
import com.rama.okapi_zero.helpers.HidGadget;
import com.rama.okapi_zero.helpers.SystemBars;
import com.rama.okapi_zero.managers.FontManager;
import com.rama.okapi_zero.managers.MessagesManager;
import com.rama.okapi_zero.managers.PrefsManager;
import com.rama.okapi_zero.managers.ThemeManager;
import com.rama.okapi_zero.objects.Message;
import com.rama.okapi_zero.objects.Themes;

import java.util.List;

public class Main extends BaseActivity implements MessageAdapter.Listener {
    private static final int MODE_LIST = 0;
    private static final int MODE_EDIT = 1;
    private static final int MODE_PREVIEW = 2;
    private static final float MIN_TEXT_SP = 50f;
    private static final float MAX_TEXT_SP = 300f;
    private static final long AUTOSAVE_DELAY_MS = 600;

    private PrefsManager prefs;
    private MessagesManager db;
    private View root;
    private View toolbarRow;
    private View listView;
    private View previewView;
    private View listBtn;
    private View deleteBtn;
    private View saveBtn;
    private View sortBtn;
    private View previewBtn;
    private View sendBtn;
    private View themeBtn;
    private View emptyLabel;
    private ImageView themeIcon;
    private ListView messagesList;
    private EditText editView;
    private TextView previewText;

    private int mode = MODE_LIST;
    private Long editingId;
    private boolean sortMode;

    private final Runnable fitEdit = new Runnable() {
        public void run() {
            resizeTextToFit(editView);
        }
    };
    private final Runnable autosave = new Runnable() {
        public void run() {
            performSave();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = PrefsManager.getInstance(this);
        db = new MessagesManager(this);
        root = findViewById(R.id.root);
        SystemBars.applyInsets(root);

        toolbarRow = findViewById(R.id.toolbar_row);
        listView = findViewById(R.id.list_view);
        previewView = findViewById(R.id.preview_view);
        listBtn = findViewById(R.id.list_btn);
        deleteBtn = findViewById(R.id.delete_btn);
        saveBtn = findViewById(R.id.save_btn);
        sortBtn = findViewById(R.id.sort_btn);
        previewBtn = findViewById(R.id.preview_btn);
        sendBtn = findViewById(R.id.send_btn);
        themeBtn = findViewById(R.id.theme_btn);
        emptyLabel = findViewById(R.id.empty_label);
        themeIcon = findViewById(R.id.theme_icon);
        messagesList = findViewById(R.id.messages_list);
        editView = findViewById(R.id.edit_view);
        previewText = findViewById(R.id.preview_text);
        editView.setTag(R.id.zoom_skip, Boolean.TRUE);
        previewText.setTag(R.id.zoom_skip, Boolean.TRUE);

        if (Build.VERSION.SDK_INT < 11) {
            themeBtn.setVisibility(View.GONE);
        }
        themeBtn.setOnClickListener(v -> {
            prefs.setLightMode(!prefs.isLightMode());
            applyTheme();
            refreshList();
        });
        sortBtn.setOnClickListener(v -> {
            sortMode = !sortMode;
            refreshList();
        });
        findViewById(R.id.settings_btn).setOnClickListener(v -> startActivity(new Intent(Main.this, Settings.class)));
        findViewById(R.id.add_btn).setOnClickListener(v -> openEdit(null));
        listBtn.setOnClickListener(v -> showList());
        deleteBtn.setOnClickListener(v -> deleteCurrent());
        saveBtn.setOnClickListener(v -> {
            performSave();
            Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
        });
        previewBtn.setOnClickListener(v -> showPreview());
        sendBtn.setOnClickListener(v -> sendOverUsb());
        findViewById(R.id.preview_close_btn).setOnClickListener(v -> closePreview());
        messagesList.setOnItemClickListener((parent, view, position, id) -> openEdit((Message) parent.getAdapter().getItem(position)));

        final GestureDetector doubleTap = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (prefs.isQuickEraseEnabled()) {
                    deleteCurrent();
                }
                return true;
            }
        });
        editView.setOnTouchListener((v, event) -> {
            doubleTap.onTouchEvent(event);
            return false;
        });
        editView.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                editView.removeCallbacks(fitEdit);
                if (s.length() == 0) {
                    editView.setTextSize(TypedValue.COMPLEX_UNIT_SP, MIN_TEXT_SP);
                } else {
                    editView.post(fitEdit);
                }
                if (mode == MODE_EDIT) {
                    editView.removeCallbacks(autosave);
                    editView.postDelayed(autosave, AUTOSAVE_DELAY_MS);
                }
            }
        });

        BackHelper.register(this, this::handleBack);
        showList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.isKeepScreenAwake()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        FontManager.apply(root, FontManager.getJersey25(this));
        applyTheme();
        if (mode == MODE_LIST) {
            refreshList();
        } else if (mode == MODE_EDIT) {
            editView.post(fitEdit);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mode == MODE_EDIT || mode == MODE_PREVIEW) {
            flushAutosave();
        }
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (mode == MODE_PREVIEW) {
            closePreview();
        } else if (mode == MODE_EDIT) {
            showList();
        } else {
            finish();
        }
    }

    private void showList() {
        editView.removeCallbacks(fitEdit);
        if (mode == MODE_EDIT) {
            flushAutosave();
        }
        mode = MODE_LIST;
        editingId = null;
        toolbarRow.setVisibility(View.VISIBLE);
        listView.setVisibility(View.VISIBLE);
        editView.setVisibility(View.GONE);
        previewView.setVisibility(View.GONE);
        listBtn.setVisibility(View.GONE);
        deleteBtn.setVisibility(View.GONE);
        saveBtn.setVisibility(View.GONE);
        previewBtn.setVisibility(View.GONE);
        sendBtn.setVisibility(View.GONE);
        sortBtn.setVisibility(View.VISIBLE);
        hideKeyboard();
        refreshList();
    }

    private void openEdit(Message message) {
        editingId = message == null ? null : message.id;
        editView.setText(message == null ? "" : message.text);
        editView.setSelection(editView.getText().length());
        mode = MODE_EDIT;
        toolbarRow.setVisibility(View.VISIBLE);
        listView.setVisibility(View.GONE);
        editView.setVisibility(View.VISIBLE);
        previewView.setVisibility(View.GONE);
        listBtn.setVisibility(View.VISIBLE);
        deleteBtn.setVisibility(View.VISIBLE);
        saveBtn.setVisibility(View.VISIBLE);
        previewBtn.setVisibility(View.VISIBLE);
        sendBtn.setVisibility(View.VISIBLE);
        sortBtn.setVisibility(View.GONE);
        editView.requestFocus();
        showKeyboard();
        editView.post(fitEdit);
    }

    private void sendOverUsb() {
        final String text = editView.getText().toString();
        if (text.length() == 0) return;
        new Thread(() -> {
            String result = HidGadget.type(text);
            if (result.length() > 0) {
                HidGadget.setup();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                result = HidGadget.type(text);
            }
            final String msg = result.length() == 0 ? getString(R.string.toast_sent) : result;
            runOnUiThread(() -> Toast.makeText(Main.this, msg, Toast.LENGTH_LONG).show());
        }).start();
    }

    private void showPreview() {
        if (editView.getText().toString().trim().length() == 0) return;
        flushAutosave();
        mode = MODE_PREVIEW;
        hideKeyboard();
        editView.clearFocus();
        toolbarRow.setVisibility(View.GONE);
        editView.setVisibility(View.GONE);
        previewView.setVisibility(View.VISIBLE);
        previewText.setTypeface(FontManager.getJersey25(this));
        previewText.setText(editView.getText());
        previewView.post(() -> resizeTextToFit(previewText));
    }

    private void closePreview() {
        mode = MODE_EDIT;
        toolbarRow.setVisibility(View.VISIBLE);
        previewView.setVisibility(View.GONE);
        editView.setVisibility(View.VISIBLE);
        editView.requestFocus();
        showKeyboard();
    }

    private void refreshList() {
        List<Message> messages = db.getAll();
        messagesList.setAdapter(new MessageAdapter(this, messages, sortMode, this));
        emptyLabel.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
        messagesList.setVisibility(messages.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onMoveUp(long id) {
        db.moveUp(id);
        refreshList();
    }

    @Override
    public void onMoveDown(long id) {
        db.moveDown(id);
        refreshList();
    }

    private void performSave() {
        String text = editView.getText().toString();
        if (text.trim().length() == 0) {
            if (editingId != null) db.delete(editingId);
            editingId = null;
        } else {
            editingId = db.save(editingId, text);
        }
    }

    private void flushAutosave() {
        editView.removeCallbacks(autosave);
        performSave();
    }

    private void deleteCurrent() {
        editView.removeCallbacks(autosave);
        if (editingId != null) db.delete(editingId);
        editingId = null;
        editView.setText("");
        resizeTextToFit(editView);
    }

    private void resizeTextToFit(TextView target) {
        String text = target.getText().toString();
        int width = target.getWidth() - target.getPaddingLeft() - target.getPaddingRight();
        int height = target.getHeight() - target.getPaddingTop() - target.getPaddingBottom();
        if (width <= 0 || height <= 0) return;
        if (text.length() == 0 || !fits(target, text, MIN_TEXT_SP, width, height)) {
            target.setTextSize(TypedValue.COMPLEX_UNIT_SP, MIN_TEXT_SP);
            return;
        }
        float lo = MIN_TEXT_SP;
        float hi = MAX_TEXT_SP;
        while (hi - lo > 1f) {
            float mid = (lo + hi) / 2f;
            if (fits(target, text, mid, width, height)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        target.setTextSize(TypedValue.COMPLEX_UNIT_SP, lo);
    }

    @SuppressWarnings("deprecation")
    private boolean fits(TextView target, String text, float sp, int width, int height) {
        TextPaint paint = new TextPaint(target.getPaint());
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics()));
        StaticLayout layout = new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        return layout.getHeight() <= height && !hasBrokenWord(layout, text);
    }

    private static boolean hasBrokenWord(StaticLayout layout, String text) {
        for (int i = 0; i < layout.getLineCount() - 1; i++) {
            int end = layout.getLineEnd(i);
            if (end >= text.length() || text.charAt(end - 1) == '\n') continue;
            if (!Character.isWhitespace(text.charAt(end - 1)) && !Character.isWhitespace(text.charAt(end))) {
                return true;
            }
        }
        return false;
    }

    private void applyTheme() {
        ThemeManager.applyTheme(this, root);
        Themes.Palette palette = ThemeManager.currentPalette(this);
        editView.setHintTextColor(palette.disabled);
        messagesList.setDivider(new ColorDrawable(palette.border));
        messagesList.setDividerHeight(1);
        themeIcon.setImageResource(prefs.isLightMode() ? R.drawable.px_moon : R.drawable.px_sun);
    }

    private void showKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editView, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editView.getWindowToken(), 0);
    }
}

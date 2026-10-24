package com.rama.okapi_zero.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.rama.okapi_zero.R;
import com.rama.okapi_zero.adapters.EntryAdapter;
import com.rama.okapi_zero.helpers.BackHelper;
import com.rama.okapi_zero.helpers.HidGadget;
import com.rama.okapi_zero.helpers.HidKeys;
import com.rama.okapi_zero.helpers.SystemBars;
import com.rama.okapi_zero.helpers.Totp;
import com.rama.okapi_zero.managers.EntriesManager;
import com.rama.okapi_zero.managers.FontManager;
import com.rama.okapi_zero.managers.PrefsManager;
import com.rama.okapi_zero.managers.ThemeManager;
import com.rama.okapi_zero.objects.Entry;

import java.util.List;

public class Main extends BaseActivity implements EntryAdapter.Listener {
    private static final int MODE_LIST = 0;
    private static final int MODE_EDIT = 1;
    private static final long DELETE_CONFIRM_MS = 3000;

    private PrefsManager prefs;
    private EntriesManager db;
    private View root;
    private View listView;
    private View editView;
    private View deleteBtn;
    private View sortBtn;
    private View emptyLabel;
    private ImageView themeIcon;
    private ListView entriesList;
    private EditText fieldName;
    private EditText fieldUser;
    private EditText fieldPassword;
    private EditText fieldUrl;
    private EditText fieldTotp;

    private int mode = MODE_LIST;
    private Entry editing; // null while adding a new entry
    private boolean sortMode;
    private long deleteArmedAt;
    private volatile boolean typing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = PrefsManager.getInstance(this);
        db = new EntriesManager(this);
        root = findViewById(R.id.root);
        SystemBars.applyInsets(root);

        listView = findViewById(R.id.list_view);
        editView = findViewById(R.id.edit_view);
        deleteBtn = findViewById(R.id.delete_btn);
        sortBtn = findViewById(R.id.sort_btn);
        emptyLabel = findViewById(R.id.empty_label);
//        themeIcon = findViewById(R.id.theme_icon);
        entriesList = findViewById(R.id.messages_list);
        fieldName = findViewById(R.id.field_name);
        fieldUser = findViewById(R.id.field_user);
        fieldPassword = findViewById(R.id.field_password);
        fieldUrl = findViewById(R.id.field_url);
        fieldTotp = findViewById(R.id.field_totp);

//        View themeBtn = findViewById(R.id.theme_btn);
//        if (Build.VERSION.SDK_INT < 11) {
//            themeBtn.setVisibility(View.GONE);
//        }
//        themeBtn.setOnClickListener(v -> {
//            prefs.setLightMode(!prefs.isLightMode());
//            applyTheme();
//            refreshList();
//        });
        sortBtn.setOnClickListener(v -> {
            sortMode = !sortMode;
            refreshList();
        });
        findViewById(R.id.settings_btn).setOnClickListener(v -> startActivity(new Intent(Main.this, Settings.class)));
        findViewById(R.id.add_btn).setOnClickListener(v -> openEdit(null));
        findViewById(R.id.save_btn).setOnClickListener(v -> save());
        findViewById(R.id.cancel).setOnClickListener(v -> showList());
        deleteBtn.setOnClickListener(v -> deleteCurrent());

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
        if (mode == MODE_EDIT) {
            showList();
        } else {
            finish();
        }
    }

    private void showList() {
        mode = MODE_LIST;
        editing = null;
        listView.setVisibility(View.VISIBLE);
        editView.setVisibility(View.GONE);
        deleteBtn.setVisibility(View.GONE);
        sortBtn.setVisibility(View.VISIBLE);
        hideKeyboard();
        refreshList();
    }

    private void openEdit(Entry entry) {
        editing = entry;
        deleteArmedAt = 0;
        fieldName.setText(entry == null ? "" : entry.name);
        fieldUser.setText(entry == null ? "" : entry.user);
        fieldPassword.setText(entry == null ? "" : entry.password);
        fieldUrl.setText(entry == null ? "" : entry.url);
        fieldTotp.setText(entry == null ? "" : entry.totp);
        mode = MODE_EDIT;
        listView.setVisibility(View.GONE);
        editView.setVisibility(View.VISIBLE);
        deleteBtn.setVisibility(entry == null ? View.GONE : View.VISIBLE);
        sortBtn.setVisibility(View.GONE);
        if (entry == null) {
            fieldName.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(fieldName, InputMethodManager.SHOW_IMPLICIT);
        } else {
            hideKeyboard();
        }
    }

    private void save() {
        String name = fieldName.getText().toString().trim();
        String user = fieldUser.getText().toString().trim();
        String password = fieldPassword.getText().toString(); // spaces may be intentional
        String url = fieldUrl.getText().toString().trim();
        String totp = fieldTotp.getText().toString().trim();
        if (name.length() + user.length() + password.length() + url.length() + totp.length() == 0) {
            Toast.makeText(this, R.string.toast_nothing_to_save, Toast.LENGTH_SHORT).show();
            return;
        }
        if (totp.length() > 0 && Totp.now(totp) == null) {
            Toast.makeText(this, R.string.toast_invalid_totp, Toast.LENGTH_LONG).show();
            return;
        }
        db.save(new Entry(editing == null ? 0 : editing.id, name, user, password, url, totp, 0, 0));
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
        showList();
    }

    private void deleteCurrent() {
        if (editing == null) return;
        long now = SystemClock.elapsedRealtime();
        if (deleteArmedAt != 0 && now - deleteArmedAt < DELETE_CONFIRM_MS) {
            db.delete(editing.id);
            showList();
        } else {
            deleteArmedAt = now;
            Toast.makeText(this, R.string.toast_confirm_delete, Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshList() {
        List<Entry> entries = db.getAll();
        entriesList.setAdapter(new EntryAdapter(this, entries, sortMode, this));
        emptyLabel.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        entriesList.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
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

    @Override
    public void onEdit(Entry entry) {
        openEdit(entry);
    }

    @Override
    public void onType(Entry entry, int field) {
        String text;
        switch (field) {
            case EntryAdapter.FIELD_USER:
                text = entry.user;
                break;
            case EntryAdapter.FIELD_PASSWORD:
                text = entry.password;
                break;
            case EntryAdapter.FIELD_URL:
                text = entry.url;
                break;
            default:
                text = Totp.now(entry.totp);
                if (text == null) {
                    Toast.makeText(this, R.string.toast_invalid_totp, Toast.LENGTH_LONG).show();
                    return;
                }
        }
        if (text.length() == 0 || typing) return;
        if (HidKeys.countUnsupported(text) > 0) {
            // Never type a partial password: refuse instead of silently skipping characters.
            Toast.makeText(this, R.string.toast_unsupported_chars, Toast.LENGTH_LONG).show();
            return;
        }
        typing = true;
        final String toType = text;
        new Thread(() -> {
            String result = HidGadget.typeEnsuringKeyboard(toType);
            final String msg = result.length() == 0 ? getString(R.string.toast_sent) : result;
            typing = false;
            runOnUiThread(() -> Toast.makeText(Main.this, msg, Toast.LENGTH_LONG).show());
        }).start();
    }

    private void applyTheme() {
        ThemeManager.applyTheme(this, root);
        themeIcon.setImageResource(prefs.isLightMode() ? R.drawable.px_moon : R.drawable.px_sun);
    }

    private void hideKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(), 0);
    }
}
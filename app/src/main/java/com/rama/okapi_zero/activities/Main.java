package com.rama.okapi_zero.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.rama.okapi_zero.R;
import com.rama.okapi_zero.adapters.EntryAdapter;
import com.rama.okapi_zero.helpers.BackHelper;
import com.rama.okapi_zero.helpers.DialogHelper;
import com.rama.okapi_zero.helpers.HidGadget;
import com.rama.okapi_zero.helpers.HidKeys;
import com.rama.okapi_zero.helpers.SystemBars;
import com.rama.okapi_zero.helpers.Totp;
import com.rama.okapi_zero.managers.EntriesManager;
import com.rama.okapi_zero.managers.FontManager;
import com.rama.okapi_zero.managers.GroupManager;
import com.rama.okapi_zero.managers.PrefsManager;
import com.rama.okapi_zero.managers.ThemeManager;
import com.rama.okapi_zero.objects.Entry;
import com.rama.okapi_zero.widgets.WdRadio;
import com.rama.okapi_zero.widgets.WdRadioGroup;

import java.util.List;
import java.util.Set;

public class Main extends BaseActivity implements EntryAdapter.Listener {
    private static final int MODE_LIST = 0;
    private static final int MODE_EDIT = 1;
    private static final long DELETE_CONFIRM_MS = 3000;

    private PrefsManager prefs;
    private EntriesManager db;
    private GroupManager groups;
    private EntryAdapter adapter;
    private View root;
    private View listView;
    private View editView;
//    private View deleteBtn;
    private View searchBox;
    private EditText search;
    private View clearSearchBtn;
    private View menuBar;
    private View addBtn;
    private TextView selectedCount;
    private TextView emptyLabel;
    private ListView entriesList;
    private TextView fieldGroup;
//    private View archiveBtn;
    private EditText fieldName;
    private EditText fieldUser;
    private EditText fieldPassword;
    private EditText fieldUrl;
    private EditText fieldTotp;

    private List<Entry> totalEntries = new java.util.ArrayList<Entry>();
    private int mode = MODE_LIST;
    private Entry editing; // null while adding a new entry
    private String editGroupId = PrefsManager.DEFAULT_GROUP_ID;
    private long deleteArmedAt;
    private volatile boolean typing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = PrefsManager.getInstance(this);
        db = new EntriesManager(this);
        groups = new GroupManager(this, db);
        root = findViewById(R.id.root);
        SystemBars.applyInsets(root);

        listView = findViewById(R.id.list_view);
        editView = findViewById(R.id.edit_view);
//        deleteBtn = findViewById(R.id.delete_btn);
        searchBox = findViewById(R.id.search_box);
        search = findViewById(R.id.search);
        clearSearchBtn = findViewById(R.id.clear_btn);
        menuBar = findViewById(R.id.menu_bar);
        addBtn = findViewById(R.id.add_btn);
        selectedCount = findViewById(R.id.selected_count);
        emptyLabel = findViewById(R.id.empty_label);
//        themeIcon = findViewById(R.id.theme_icon);
        entriesList = findViewById(R.id.messages_list);
        fieldName = findViewById(R.id.field_name);
        fieldUser = findViewById(R.id.field_user);
        fieldPassword = findViewById(R.id.field_password);
        fieldUrl = findViewById(R.id.field_url);
        fieldTotp = findViewById(R.id.field_totp);
        fieldGroup = findViewById(R.id.field_group);
//        archiveBtn = findViewById(R.id.archive_btn);

        adapter = new EntryAdapter(this, groups, this);
        entriesList.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearSearchBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                adapter.setQuery(s.toString());
                updateEmptyState();
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
        clearSearchBtn.setOnClickListener(v -> search.setText(""));
        fieldGroup.setOnClickListener(v -> showGroupPicker(editGroupId, id -> {
            editGroupId = id;
            fieldGroup.setText(groups.getGroupLabel(id));
//            if (editing != null) archiveBtn.setVisibility(PrefsManager.ARCHIVED_GROUP_ID.equals(id) ? View.GONE : View.VISIBLE);
        }));
        findViewById(R.id.multi_select_cancel_button).setOnClickListener(v -> adapter.exitMultiSelectMode());
        findViewById(R.id.archive_button).setOnClickListener(v -> {
            int count = adapter.getSelectedIds().size();
            moveSelectedToGroup(PrefsManager.ARCHIVED_GROUP_ID);
            Toast.makeText(this, getString(R.string.toast_archived) + (count > 1 ? " (" + count + ")" : ""), Toast.LENGTH_SHORT).show();
        });
//        archiveBtn.setOnClickListener(v -> {
//            // Saves any edits made in the form, and files the entry under Archived.
//            if (save(PrefsManager.ARCHIVED_GROUP_ID)) {
//                Toast.makeText(this, R.string.toast_archived, Toast.LENGTH_SHORT).show();
//            }
//        });
        findViewById(R.id.move_to_group_button).setOnClickListener(v -> showGroupPicker(null, this::moveSelectedToGroup));
        findViewById(R.id.settings_btn).setOnClickListener(v -> startActivity(new Intent(Main.this, Settings.class)));
        findViewById(R.id.add_btn).setOnClickListener(v -> openEdit(null));
        findViewById(R.id.save_btn).setOnClickListener(v -> save(null));
        findViewById(R.id.cancel).setOnClickListener(v -> showList());
//        deleteBtn.setOnClickListener(v -> deleteCurrent());

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
        ThemeManager.rollIfRandom(this);
        if (groups.shouldCollapseGroupsOnHome()) {
            groups.collapseAllGroups();
        }
        FontManager.apply(root, FontManager.getJersey25(this));
        applyTheme();
        search.setHintTextColor(ThemeManager.currentPalette(this).disabled);
        if (mode == MODE_LIST) {
            refreshList();
        } else {
            // The group being edited may have been renamed or deleted in Settings.
            editGroupId = groups.resolveGroupId(editGroupId);
            fieldGroup.setText(groups.getGroupLabel(editGroupId));
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
        if (mode == MODE_LIST && adapter.isMultiSelectMode()) {
            adapter.exitMultiSelectMode();
        } else if (mode == MODE_EDIT) {
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
//        deleteBtn.setVisibility(View.GONE);
        searchBox.setVisibility(View.VISIBLE);
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
        editGroupId = entry == null ? PrefsManager.DEFAULT_GROUP_ID : groups.resolveGroupId(entry.groupId);
        fieldGroup.setText(groups.getGroupLabel(editGroupId));
//        archiveBtn.setVisibility(entry == null || PrefsManager.ARCHIVED_GROUP_ID.equals(editGroupId) ? View.GONE : View.VISIBLE);
        mode = MODE_EDIT;
        listView.setVisibility(View.GONE);
        editView.setVisibility(View.VISIBLE);
//        deleteBtn.setVisibility(entry == null ? View.GONE : View.VISIBLE);
        searchBox.setVisibility(View.INVISIBLE);
        if (entry == null) {
            fieldName.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(fieldName, InputMethodManager.SHOW_IMPLICIT);
        } else {
            hideKeyboard();
        }
    }

    /** Saves the form; forceGroupId (if not null) overrides the chosen group. Returns false if nothing was saved. */
    private boolean save(String forceGroupId) {
        String name = fieldName.getText().toString().trim();
        String user = fieldUser.getText().toString().trim();
        String password = fieldPassword.getText().toString(); // spaces may be intentional
        String url = fieldUrl.getText().toString().trim();
        String totp = fieldTotp.getText().toString().trim();
        if (name.length() + user.length() + password.length() + url.length() + totp.length() == 0) {
            Toast.makeText(this, R.string.toast_nothing_to_save, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (totp.length() > 0 && Totp.now(totp) == null) {
            Toast.makeText(this, R.string.toast_invalid_totp, Toast.LENGTH_LONG).show();
            return false;
        }
        db.save(new Entry(editing == null ? 0 : editing.id, name, user, password, url, totp, 0, forceGroupId != null ? forceGroupId : editGroupId));
        if (forceGroupId == null) Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
        showList();
        return true;
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
        totalEntries = db.getAll();
        adapter.setEntries(totalEntries);
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = adapter.getCount() == 0;
        if (empty) {
            emptyLabel.setText(totalEntries.isEmpty() ? R.string.label_no_messages
                    : adapter.hasQuery() ? R.string.label_no_matches : R.string.label_all_hidden);
        }
        emptyLabel.setVisibility(empty ? View.VISIBLE : View.GONE);
        entriesList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onSelectionChanged(boolean active, int count) {
        menuBar.setVisibility(active ? View.VISIBLE : View.GONE);
        addBtn.setVisibility(active ? View.GONE : View.VISIBLE);
        selectedCount.setText(getString(R.string.multi_select_count, count));
    }

    private void moveSelectedToGroup(String groupId) {
        Set<Long> ids = adapter.getSelectedIds();
        adapter.exitMultiSelectMode();
        db.moveToGroup(ids, groupId);
        refreshList();
    }

    private interface GroupChoice {
        void onChosen(String groupId);
    }

    /** Lists every group; the one passed as current (if any) is pre-selected. */
    private void showGroupPicker(String current, final GroupChoice choice) {
        DialogHelper.show(this, R.layout.dialog_groups_pick, (view, dialog) -> {
            WdRadioGroup radioGroup = view.findViewById(R.id.groups);
            for (final String groupId : groups.getGroupIds()) {
                WdRadio radio = radioGroup.addOption(groups.getGroupLabel(groupId));
                radio.setChecked(groupId.equals(current));
                radio.setOnClickListener(v -> {
                    choice.onChosen(groupId);
                    dialog.dismiss();
                });
            }
        });
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
    }

    private void hideKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(), 0);
    }
}
package com.rama.okapi_zero.activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.graphics.PorterDuff;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.rama.okapi_zero.R;
import com.rama.okapi_zero.helpers.DataTransfer;
import com.rama.okapi_zero.helpers.DialogHelper;
import com.rama.okapi_zero.helpers.HidGadget;
import com.rama.okapi_zero.helpers.SystemBars;
import com.rama.okapi_zero.managers.EntriesManager;
import com.rama.okapi_zero.managers.FontManager;
import com.rama.okapi_zero.managers.GroupManager;
import com.rama.okapi_zero.managers.PrefsManager;
import com.rama.okapi_zero.managers.ThemeManager;
import com.rama.okapi_zero.managers.ZoomManager;
import com.rama.okapi_zero.objects.PrefTheme;
import com.rama.okapi_zero.objects.Themes;
import com.rama.okapi_zero.widgets.WdCheckbox;
import com.rama.okapi_zero.widgets.WdRadio;
import com.rama.okapi_zero.widgets.WdRadioGroup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.rama.okapi_zero.objects.Entry;

public class Settings extends BaseActivity {
    private PrefsManager prefs;
    private EntriesManager entriesDb;
    private GroupManager groupManager;
    private LinearLayout groupsContainer;
    private View root;
    private static final int REQ_EXPORT = 101;
    private static final int REQ_IMPORT = 102;
    private volatile boolean hidBusy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = PrefsManager.getInstance(this);
        entriesDb = new EntriesManager(this);
        groupManager = new GroupManager(this, entriesDb);
        groupsContainer = findViewById(R.id.groups_container);
        root = findViewById(R.id.root);
        SystemBars.applyInsets(root);
        setupThemes();
        setupSystemSection();
        setupZoomSection();
        setupGroupsSection();
        FontManager.apply(root, FontManager.getJersey25(this));
        ThemeManager.applyTheme(this, root);
        findViewById(R.id.about_button).setOnClickListener(v -> startActivity(new Intent(Settings.this, About.class)));
        findViewById(R.id.go_back).setOnClickListener(v -> finish());

        findViewById(R.id.hid_setup_btn).setOnClickListener(v -> setupKeyboard());
        findViewById(R.id.export_btn).setOnClickListener(v -> startExport());
        findViewById(R.id.import_btn).setOnClickListener(v -> startImport());
    }

    // ---- Data: export / import (JSON, via the system file picker, no storage permission needed) ----

    private File fallbackFile() {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        return new File(dir, DataTransfer.FILE_NAME);
    }

    private void startExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_TITLE, DataTransfer.FILE_NAME);
        try {
            startActivityForResult(intent, REQ_EXPORT);
        } catch (ActivityNotFoundException e) {
            // No file picker installed (e.g. Files app disabled): write to the app's own folder instead.
            runExport(null);
        }
    }

    private void startImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        try {
            startActivityForResult(intent, REQ_IMPORT);
        } catch (ActivityNotFoundException e) {
            runImport(null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_EXPORT) runExport(data.getData());
        if (requestCode == REQ_IMPORT) runImport(data.getData());
    }

    /** uri == null means: use the app's own folder. */
    private void runExport(final Uri uri) {
        new Thread(() -> {
            String message;
            EntriesManager db = new EntriesManager(this);
            try {
                List<Entry> entries = db.getAll();
                byte[] bytes = DataTransfer.toCsv(entries, new GroupManager(this, db)).getBytes("UTF-8");
                OutputStream out = null;
                File file = null;
                if (uri != null) {
                    out = getContentResolver().openOutputStream(uri, "wt");
                } else {
                    file = fallbackFile();
                    out = new FileOutputStream(file);
                }
                try {
                    out.write(bytes);
                } finally {
                    out.close();
                }
                message = file == null ? getString(R.string.data_exported, entries.size())
                        : getString(R.string.data_exported_to, entries.size(), file.getAbsolutePath());
            } catch (Exception e) {
                message = getString(R.string.data_error, String.valueOf(e.getMessage()));
            } finally {
                db.close();
            }
            showDataStatus(message);
        }).start();
    }

    private void runImport(final Uri uri) {
        new Thread(() -> {
            String message;
            EntriesManager db = new EntriesManager(this);
            try {
                InputStream in;
                File file = null;
                if (uri != null) {
                    in = getContentResolver().openInputStream(uri);
                } else {
                    file = fallbackFile();
                    if (!file.exists()) {
                        showDataStatus(getString(R.string.data_no_file, file.getAbsolutePath()));
                        return;
                    }
                    in = new FileInputStream(file);
                }
                String csv;
                try {
                    csv = DataTransfer.readAll(in);
                } finally {
                    in.close();
                }
                List<Entry> entries = DataTransfer.fromCsv(csv, new GroupManager(this, db));
                int added = db.importEntries(entries);
                message = getString(R.string.data_imported, added, entries.size() - added);
            } catch (Exception e) {
                message = getString(R.string.data_error, String.valueOf(e.getMessage()));
            } finally {
                db.close();
            }
            showDataStatus(message);
        }).start();
    }

    private void showDataStatus(final String message) {
        runOnUiThread(() -> {
            ((TextView) findViewById(R.id.data_status)).setText(message);
            Toast.makeText(Settings.this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void setupKeyboard() {
        if (hidBusy) return;
        hidBusy = true;
        final TextView log = findViewById(R.id.hid_setup_log);
        log.setVisibility(View.VISIBLE);
        log.setText(R.string.hid_setting_up);
        new Thread(() -> {
            final String out = HidGadget.setup();
            runOnUiThread(() -> log.setText(out.length() == 0 ? getString(R.string.hid_done) : out));
            hidBusy = false;
        }).start();
    }

    private void setupThemes() {
        if (Build.VERSION.SDK_INT < 11) {
            findViewById(R.id.themes_container).setVisibility(View.GONE);
            return;
        }
        final WdRadioGroup group = findViewById(R.id.theme_group);
        final List<Themes.Palette> palettes = Themes.all();
        String current = prefs.getTheme();
        boolean random = PrefTheme.CATPPUCCIN_MOCHA_RANDOM.equals(current);
        // Index 0 is always "Random"; palettes follow in order.
        int checkedId = group.addOption(getString(R.string.label_theme_random)).getId();
        if (!random) checkedId = -1;
        for (int i = 0; i < palettes.size(); i++) {
            int id = group.addOption(palettes.get(i).label).getId();
            if (palettes.get(i).id.equals(current)) checkedId = id;
        }
        group.check(checkedId);
        group.setOnCheckedChangeListener((g, id) -> {
            int index = g.getCheckedIndex();
            if (index == 0) {
                prefs.setTheme(PrefTheme.CATPPUCCIN_MOCHA_RANDOM);
                ThemeManager.roll(Settings.this);
            } else {
                prefs.setTheme(palettes.get(index - 1).id);
            }
            ThemeManager.applyTheme(Settings.this, root);
        });
    }

    private void setupSystemSection() {
        bindCheckbox(R.id.show_status_bar, PrefsManager.KEY_STATUS_BAR, prefs.isStatusBarVisible());
        bindCheckbox(R.id.keep_screen_awake, PrefsManager.KEY_KEEP_AWAKE, prefs.isKeepScreenAwake());
    }

    private void bindCheckbox(int id, final String key, boolean value) {
        WdCheckbox checkbox = findViewById(id);
        checkbox.setChecked(value);
        checkbox.setOnCheckedChangeListener(isChecked -> {
            prefs.setBoolean(key, isChecked);
            SystemBars.setStatusBarVisible(Settings.this, prefs.isStatusBarVisible());
        });
    }

    private void setupZoomSection() {
        final EditText zoom = findViewById(R.id.zoom);
        zoom.setText(String.valueOf(ZoomManager.getPercent(this)));
        zoom.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyZoom();
            }
            return false;
        });
        zoom.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                applyZoom();
            }
        });
    }

    private void applyZoom() {
        saveZoomFromField();
        ZoomManager.apply(this, root);
    }

    private void saveZoomFromField() {
        EditText zoom = findViewById(R.id.zoom);
        int percent;
        try {
            percent = ZoomManager.clamp(Integer.parseInt(zoom.getText().toString().trim()));
        } catch (NumberFormatException e) {
            percent = ZoomManager.getPercent(this);
        }
        String normalized = String.valueOf(percent);
        if (!normalized.equals(zoom.getText().toString())) {
            zoom.setText(normalized);
        }
        prefs.setZoomPercent(percent);
    }

    @Override
    protected void onPause() {
        saveZoomFromField();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        entriesDb.close();
        super.onDestroy();
    }

    // ---- Groups ----

    private void setupGroupsSection() {
        renderGroups();
        final WdCheckbox collapseOnHome = findViewById(R.id.collapse_groups_on_home);
        collapseOnHome.setChecked(prefs.shouldCollapseGroupsOnHome());
        collapseOnHome.setOnCheckedChangeListener(prefs::setCollapseGroupsOnHome);
        final WdCheckbox onlyOneOpen = findViewById(R.id.only_one_group_open);
        onlyOneOpen.setChecked(prefs.isOnlyOneGroupOpenEnabled());
        onlyOneOpen.setOnCheckedChangeListener(prefs::setOnlyOneGroupOpenEnabled);
        findViewById(R.id.add_group_button).setOnClickListener(v -> {
            groupManager.createGroup(getString(R.string.new_group_header));
            renderGroups();
        });
    }

    private void renderGroups() {
        groupsContainer.removeAllViews();
        for (String groupId : groupManager.getGroupIds()) {
            addGroupRow(groupId);
        }
        ThemeManager.applyTheme(this, groupsContainer);
    }

    private void setActionEnabled(View button, ImageView icon, boolean enabled) {
        button.setEnabled(enabled);
        icon.setTag(enabled ? null : "disabled");
        Themes.Palette palette = ThemeManager.currentPalette(this);
        icon.setColorFilter(enabled ? palette.text : palette.disabled, PorterDuff.Mode.SRC_IN);
    }

    private void addGroupRow(final String groupId) {
        View row = getLayoutInflater().inflate(R.layout.list_item_group, groupsContainer, false);
        FontManager.apply(row, FontManager.getJersey25(this));
        ZoomManager.apply(this, row);
        final EditText name = row.findViewById(R.id.group_name);
        View delete = row.findViewById(R.id.delete_group);
        View toggleVisibility = row.findViewById(R.id.toggle_visibility);
        final ImageView toggleVisibilityIcon = row.findViewById(R.id.toggle_visibility_img);
        View toggleKeepExpanded = row.findViewById(R.id.toggle_keep_expanded);
        final ImageView toggleKeepExpandedIcon = row.findViewById(R.id.toggle_keep_expanded_img);
        final View saveButton = row.findViewById(R.id.save_changes_button);
        final ImageView saveIcon = row.findViewById(R.id.save_changes_img);
        ImageView deleteIcon = row.findViewById(R.id.delete_group_img);
        name.setText(groupManager.getGroupLabel(groupId));
        setActionEnabled(saveButton, saveIcon, false);
        updateVisibilityIcon(toggleVisibilityIcon, groupId);
        updateKeepExpandedIcon(toggleKeepExpandedIcon, groupId);
        toggleVisibility.setOnClickListener(v -> {
            groupManager.toggleGroupVisible(groupId);
            updateVisibilityIcon(toggleVisibilityIcon, groupId);
        });
        toggleKeepExpanded.setOnClickListener(v -> {
            groupManager.toggleGroupKeepExpanded(groupId);
            updateKeepExpandedIcon(toggleKeepExpandedIcon, groupId);
            renderGroups();
        });
        final String originalText = name.getText().toString();
        name.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String current = s.toString();
                setActionEnabled(saveButton, saveIcon, !current.equals(originalText) && current.trim().length() > 0);
            }

            public void afterTextChanged(Editable s) {
            }
        });
        saveButton.setOnClickListener(v -> {
            String newLabel = name.getText().toString().trim();
            if (newLabel.length() > 0) {
                groupManager.renameGroup(groupId, newLabel);
                Toast.makeText(Settings.this, R.string.toast_group_label_updated, Toast.LENGTH_SHORT).show();
                renderGroups();
            }
        });
        row.findViewById(R.id.ascend_group).setOnClickListener(v -> {
            groupManager.moveGroup(groupId, -1);
            renderGroups();
        });
        row.findViewById(R.id.descend_group).setOnClickListener(v -> {
            groupManager.moveGroup(groupId, 1);
            renderGroups();
        });
        boolean deletable = !PrefsManager.isProtectedGroup(groupId);
        setActionEnabled(delete, deleteIcon, deletable);
        if (deletable) {
            delete.setOnClickListener(v -> showDeleteGroupDialog(groupId, name.getText().toString()));
        }
        groupsContainer.addView(row);
    }

    private void updateVisibilityIcon(ImageView icon, String groupId) {
        icon.setImageResource(groupManager.isGroupVisible(groupId) ? R.drawable.px_eye : R.drawable.px_eye_cross);
    }

    private void updateKeepExpandedIcon(ImageView icon, String groupId) {
        icon.setImageResource(groupManager.isGroupKeepExpanded(groupId) ? R.drawable.px_lock : R.drawable.px_lock_open);
    }

    private void showDeleteGroupDialog(final String groupId, String groupLabel) {
        DialogHelper.show(this, R.layout.dialog_groups_delete, (view, dialog) -> {
            ((TextView) view.findViewById(R.id.group_name)).setText(groupLabel);
            final WdRadioGroup radioGroup = view.findViewById(R.id.groups);
            final List<String> targetGroups = new ArrayList<String>();
            for (String targetId : groupManager.getGroupIds()) {
                if (targetId.equals(groupId)) continue;
                targetGroups.add(targetId);
                WdRadio radio = radioGroup.addOption(groupManager.getGroupLabel(targetId));
                if (targetGroups.size() == 1) radio.setChecked(true);
            }
            view.findViewById(R.id.yes_button).setOnClickListener(v -> {
                int index = radioGroup.getCheckedIndex();
                if (index < 0) {
                    Toast.makeText(Settings.this, R.string.toast_select_target_group, Toast.LENGTH_SHORT).show();
                    return;
                }
                groupManager.deleteGroup(groupId, targetGroups.get(index));
                renderGroups();
                dialog.dismiss();
            });
        });
    }
}

package com.rama.okapi_zero.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.rama.okapi_zero.R;
import com.rama.okapi_zero.helpers.HidGadget;
import com.rama.okapi_zero.helpers.HidKeys;
import com.rama.okapi_zero.helpers.SystemBars;
import com.rama.okapi_zero.managers.FontManager;
import com.rama.okapi_zero.managers.ThemeManager;

/** Minimal screen to verify the phone can act as a USB keyboard. */
public class HidTest extends BaseActivity {
    private static final int COUNTDOWN_SECONDS = 5;

    private TextView log;
    private EditText input;
    private volatile boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hid_test);
        View root = findViewById(R.id.root);
        SystemBars.applyInsets(root);
        FontManager.apply(root, FontManager.getJersey25(this));
        ThemeManager.applyTheme(this, root);

        log = findViewById(R.id.hid_log);
        input = findViewById(R.id.hid_input);

        findViewById(R.id.hid_check_btn).setOnClickListener(v -> runTask(null, HidGadget::diagnose));
        findViewById(R.id.hid_setup_btn).setOnClickListener(v -> runTask(null, HidGadget::setup));
//        findViewById(R.id.hid_type_btn).setOnClickListener(v -> {
//            final String text = input.getText().toString();
//            int skipped = HidKeys.countUnsupported(text);
//            if (skipped > 0) append(getString(R.string.hid_unsupported, skipped));
//            runTask(COUNTDOWN_SECONDS, () -> HidGadget.type(text));
//        });
//        findViewById(R.id.go_back).setOnClickListener(v -> finish());
    }

    private interface Job {
        String run();
    }

    private void runTask(final Integer countdown, final Job job) {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            try {
                if (countdown != null) {
                    for (int i = countdown; i > 0; i--) {
                        append(getString(R.string.hid_typing_in, i));
                        Thread.sleep(1000);
                    }
                }
                String out = job.run();
                append(out.length() == 0 ? getString(R.string.hid_done) : out);
            } catch (InterruptedException ignored) {
            } finally {
                busy = false;
            }
        }).start();
    }

    private void append(final String line) {
        runOnUiThread(() -> log.append(line + "\n\n"));
    }
}

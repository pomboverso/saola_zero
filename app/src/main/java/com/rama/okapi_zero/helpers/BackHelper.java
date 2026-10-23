package com.rama.okapi_zero.helpers;

import android.app.Activity;
import android.os.Build;
import android.window.OnBackInvokedDispatcher;

public final class BackHelper {
    private BackHelper() {
    }

    public static void register(Activity activity, final Runnable onBack) {
        if (Build.VERSION.SDK_INT < 33) return;
        activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> onBack.run());
    }
}

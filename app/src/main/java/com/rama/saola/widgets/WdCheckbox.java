package com.rama.saola.widgets;

import android.content.Context;
import android.util.AttributeSet;

import com.rama.saola.R;

public class WdCheckbox extends WdCompound {
    public WdCheckbox(Context context) {
        this(context, null);
    }

    public WdCheckbox(Context context, AttributeSet attrs) {
        super(context, attrs, R.layout.wd_checkbox, true, "android.widget.CheckBox");
    }
}

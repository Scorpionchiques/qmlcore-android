package com.pureqml.android.runtime;

import android.content.Context;

public final class EditText extends android.support.v7.widget.AppCompatEditText {
    static final String TAG = "EditText";

    EditText(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setSingleLine();
    }
}

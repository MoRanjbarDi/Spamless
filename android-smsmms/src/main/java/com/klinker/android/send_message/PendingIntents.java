package com.klinker.android.send_message;

import android.app.PendingIntent;
import android.os.Build;

public final class PendingIntents {
    private PendingIntents() {}
    public static int immutable(int flags) {
        return flags | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }
    public static int mutable(int flags) {
        return flags | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
    }
}

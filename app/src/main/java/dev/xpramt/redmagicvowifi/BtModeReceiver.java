package dev.xpramt.redmagicvowifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Receives the report that HookEntry sends from inside com.android.bluetooth when the Bluetooth process
 * starts, and stores which Bluetooth role mode that process is really running in. The receiver page shows
 * it, so the user can tell whether a mode change has already taken effect.
 */
public final class BtModeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Config.ACTION_BT_MODE_REPORT.equals(intent.getAction())) {
            return;
        }
        String mode = intent.getStringExtra(Config.EXTRA_BT_MODE);
        if (!Config.BT_MODE_RECEIVER.equals(mode) && !Config.BT_MODE_NORMAL.equals(mode)) {
            return;
        }
        SharedPreferences prefs = Config.appPrefs(context);
        prefs.edit()
                .putString(Config.KEY_BT_ACTIVE_MODE, mode)
                .putLong(Config.KEY_BT_ACTIVE_AT, System.currentTimeMillis())
                .apply();
    }
}

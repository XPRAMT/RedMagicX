package dev.xpramt.redmagicvowifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Reapplies the last-used wireless ADB setting after boot, so the user no longer has to open the ADB page.
 * Uses root (su) exactly like the ADB page: setprop service.adb.tcp.port + ctl.restart adbd.
 */
public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "RedMagicX-Boot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        final SharedPreferences prefs;
        try {
            prefs = Config.appPrefs(context);
        } catch (Throwable t) {
            Log.w(TAG, "prefs unavailable", t);
            return;
        }
        if (!prefs.getBoolean(Config.KEY_WIRELESS_ADB_ENABLED, false)) {
            return;
        }
        final int port = prefs.getInt(Config.KEY_WIRELESS_ADB_PORT, Config.DEFAULT_WIRELESS_ADB_PORT);
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                for (int attempt = 0; attempt < 6; attempt++) {
                    if (applyAndCheck(port)) {
                        Log.i(TAG, "wireless adb enabled on port " + port);
                        return;
                    }
                    Thread.sleep(3000);
                }
                Log.w(TAG, "wireless adb restore failed");
            } catch (Throwable t) {
                Log.w(TAG, "wireless adb restore error", t);
            } finally {
                pending.finish();
            }
        }, "wireless-adb-boot").start();
    }

    private static boolean applyAndCheck(int port) throws Exception {
        String cmd = "if [ \"$(getprop service.adb.tcp.port)\" != \"" + port + "\" ]; then "
                + "setprop service.adb.tcp.port " + port + "; setprop ctl.restart adbd; sleep 2; fi; "
                + "getprop service.adb.tcp.port";
        Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
        StringBuilder out = new StringBuilder();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line.trim());
            }
        }
        return p.waitFor() == 0 && out.toString().endsWith(String.valueOf(port));
    }
}
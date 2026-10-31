package dev.xpramt.redmagicvowifi;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Boot-time restore, run in one async thread with independent steps:
 * 1) wireless ADB: reapply the last port via su (setprop service.adb.tcp.port + ctl.restart adbd).
 * 2) third-party launcher: the stock launcher's Quickstep ignores the default HOME after boot until the
 *    home activity is set again, so rerun the same `cmd package set-home-activity` as the in-app switch,
 *    then start the chosen HOME once via su `am start`.
 */
public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "RedMagicX-Boot";
    private static final String STOCK_LAUNCHER_PACKAGE = "com.zte.mifavor.launcher";

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
        final boolean adbEnabled = prefs.getBoolean(Config.KEY_WIRELESS_ADB_ENABLED, false);
        final int port = prefs.getInt(Config.KEY_WIRELESS_ADB_PORT, Config.DEFAULT_WIRELESS_ADB_PORT);
        final String launcher = launcherComponent(prefs);
        if (!adbEnabled && launcher == null) {
            return;
        }
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                if (adbEnabled) {
                    restoreWirelessAdb(port);
                }
                if (launcher != null) {
                    restoreLauncher(launcher);
                }
            } finally {
                pending.finish();
            }
        }, "redmagicx-boot").start();
    }

    private static String launcherComponent(SharedPreferences prefs) {
        try {
            if (!prefs.getBoolean(Config.KEY_LAUNCHER_OVERRIDE_ENABLED, false)) {
                return null;
            }
            ComponentName cn = ComponentName.unflattenFromString(prefs.getString(Config.KEY_LAUNCHER_COMPONENT, ""));
            if (cn == null || STOCK_LAUNCHER_PACKAGE.equals(cn.getPackageName())
                    || "com.android.settings".equals(cn.getPackageName())) {
                return null;
            }
            return cn.flattenToShortString();
        } catch (Throwable t) {
            Log.w(TAG, "launcher pref read error", t);
            return null;
        }
    }

    private static void restoreWirelessAdb(int port) {
        try {
            for (int attempt = 0; attempt < 6; attempt++) {
                String cmd = "if [ \"$(getprop service.adb.tcp.port)\" != \"" + port + "\" ]; then "
                        + "setprop service.adb.tcp.port " + port + "; setprop ctl.restart adbd; sleep 2; fi; "
                        + "getprop service.adb.tcp.port";
                Result r = su(cmd);
                if (r.exit == 0 && r.out.endsWith(String.valueOf(port))) {
                    Log.i(TAG, "wireless adb enabled on port " + port);
                    return;
                }
                Thread.sleep(3000);
            }
            Log.w(TAG, "wireless adb restore failed");
        } catch (Throwable t) {
            Log.w(TAG, "wireless adb restore error", t);
        }
    }

    private static void restoreLauncher(String component) {
        try {
            Thread.sleep(5000);
            for (int attempt = 0; attempt < 5; attempt++) {
                Result set = su("cmd package set-home-activity --user 0 '" + component + "'");
                if (set.exit == 0) {
                    Result start = su("am start --user 0 -a android.intent.action.MAIN"
                            + " -c android.intent.category.HOME -f 0x10200000 -n '" + component + "'");
                    Log.i(TAG, "launcher restored " + component + " set=" + set.out
                            + " start=" + start.exit + " " + start.out);
                    return;
                }
                Log.w(TAG, "set-home-activity failed exit=" + set.exit + " " + set.out);
                Thread.sleep(3000);
            }
            Log.w(TAG, "launcher restore failed " + component);
        } catch (Throwable t) {
            Log.w(TAG, "launcher restore error", t);
        }
    }

    private static final class Result {
        final int exit;
        final String out;
        Result(int exit, String out) { this.exit = exit; this.out = out; }
    }

    private static Result su(String cmd) throws Exception {
        Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
        StringBuilder out = new StringBuilder();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line.trim()).append(' ');
            }
        }
        return new Result(p.waitFor(), out.toString().trim());
    }
}
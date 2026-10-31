package dev.xpramt.redmagicvowifi;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * "Use AOSP volume dialog" in SystemUI (com.zte.feature.volume.MfvVolumeDialogImpl on RedMagicOS 11).
 *
 * Crash fixed here (2026-10-31): with the AOSP layout, mfvInitDialog/prepareDialog were skipped, so MFV-only
 * fields (mDialogMain, mDialogRowsView2, ringer RadioGroup/RadioButtons, expand button) stayed null, and
 * MfvVolumeDialogImpl.updateRowsH (called from VolumeDialogImpl.initDialog during SystemUI startup) did
 * mDialogMain.setVisibility -> NPE -> SystemUI crash loop.
 *
 * Fix layers:
 * 1) prepareDialog: when the AOSP layout has no MFV containers, fill mDialogMain / mDialogRowsView2 with detached
 *    dummy views so every MFV visibility/margin call becomes harmless.
 * 2) Skip MFV-only methods that need ringer/expand views (no-op).
 * 3) Safety net: every declared MfvVolumeDialogImpl method gets an after-hook that swallows any Throwable while the
 *    AOSP path is active (logs once per method, returns a type default).
 * 4) Crash-loop guard: SystemUI start times with AOSP on are recorded in SystemUI's device-protected data dir; with
 *    >= 3 starts inside 90 s the AOSP path is disabled for that process (falls back to the stock MFV dialog).
 */
final class AospVolumeHooks {
    private static final String MFV = "com.zte.feature.volume.MfvVolumeDialogImpl";
    private static final String ADAPT = "com.zte.adapt.mifavor.volume.VolumeDialogImplAdapt";
    private static final String GUARD_FILE = "redmagicx_aosp_volume_starts";
    private static final long GUARD_WINDOW_MS = 90_000L;
    private static final int GUARD_MAX_STARTS = 3;

    private static volatile boolean guardTripped;
    private static final Set<String> loggedFailures = Collections.synchronizedSet(new HashSet<>());

    private AospVolumeHooks() {
    }

    static boolean active() {
        if (guardTripped) {
            return false;
        }
        try {
            return Config.loadForHook().aospVolumeDialog;
        } catch (Throwable t) {
            return false;
        }
    }

    static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        checkCrashLoopGuard(lpparam);
        installLayoutHooks(lpparam);
        Class<?> mfv = XposedHelpers.findClassIfExists(MFV, lpparam.classLoader);
        if (mfv == null) {
            log("MfvVolumeDialogImpl not found; AOSP volume dialog MFV hooks skipped");
            return;
        }
        installSafetyNet(mfv);
        installMfvHooks(mfv);
    }

    private static void checkCrashLoopGuard(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (!Config.loadForHook().aospVolumeDialog) {
                return;
            }
            String dir = lpparam.appInfo == null ? null : lpparam.appInfo.deviceProtectedDataDir;
            if (dir == null) {
                return;
            }
            File file = new File(dir, GUARD_FILE);
            long now = System.currentTimeMillis();
            List<Long> starts = new ArrayList<>();
            if (file.exists()) {
                for (String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.US_ASCII).split("\n")) {
                    try {
                        long t = Long.parseLong(line.trim());
                        if (now - t >= 0 && now - t < GUARD_WINDOW_MS) {
                            starts.add(t);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            starts.add(now);
            StringBuilder sb = new StringBuilder();
            for (Long t : starts) {
                sb.append(t).append('\n');
            }
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
            }
            if (starts.size() >= GUARD_MAX_STARTS) {
                guardTripped = true;
                log("AOSP volume dialog crash-loop guard tripped: " + starts.size()
                        + " SystemUI starts in " + (GUARD_WINDOW_MS / 1000) + "s; falling back to MFV dialog");
            }
        } catch (Throwable t) {
            log("AOSP volume dialog crash-loop guard error: " + t);
        }
    }

    private static void installLayoutHooks(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> adapt = XposedHelpers.findClassIfExists(ADAPT, lpparam.classLoader);
        if (adapt == null) {
            log("VolumeDialogImplAdapt not found; AOSP volume dialog layout hooks skipped");
            return;
        }
        XC_MethodHook passThrough = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (active()) {
                    param.setResult(param.args[0]);
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(adapt, "getDialogLayoutResId", int.class, passThrough);
            XposedHelpers.findAndHookMethod(adapt, "getRowLayoutResId", int.class, passThrough);
            log("AOSP volume dialog layout hooks installed on VolumeDialogImplAdapt");
        } catch (Throwable t) {
            log("AOSP volume dialog layout hooks failed: " + t);
        }
    }

    /** After-hook on every declared method: when AOSP is active, never let an MFV method throw into SystemUI. */
    private static void installSafetyNet(Class<?> mfv) {
        int count = 0;
        for (Method method : mfv.getDeclaredMethods()) {
            if (Modifier.isAbstract(method.getModifiers())) {
                continue;
            }
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook(XC_MethodHook.PRIORITY_LOWEST) {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Throwable thrown = param.getThrowable();
                        if (thrown == null || !active()) {
                            return;
                        }
                        String name = ((Member) param.method).getName();
                        if (loggedFailures.add(name)) {
                            log("AOSP volume dialog swallowed " + thrown.getClass().getSimpleName()
                                    + " in MfvVolumeDialogImpl." + name + ": " + thrown.getMessage());
                        }
                        param.setResult(defaultValue(((Method) param.method).getReturnType()));
                    }
                });
                count++;
            } catch (Throwable t) {
                log("AOSP volume dialog safety net failed on " + method.getName() + ": " + t);
            }
        }
        log("AOSP volume dialog safety net installed on " + count + " MfvVolumeDialogImpl methods");
    }

    private static void installMfvHooks(Class<?> mfv) {
        // MFV-only UI setup: ringer RadioGroup, expand button, keypad, MFV listeners.
        skip(mfv, "mfvInitDialog");
        // Ringer/expand UI that only exists in the MFV layout.
        skip(mfv, "updateExpandedH", boolean.class);
        skip(mfv, "updateRingerMfv");
        skip(mfv, "updateRingerTypeface", int.class);
        skip(mfv, "initRingerButtonXPositionsInParent");
        skip(mfv, "startExpandedAnimation", boolean.class,
                "com.android.systemui.volume.VolumeDialogImpl$VolumeRow");
        skip(mfv, "prepareForCollapse");
        skip(mfv, "updateRowsWidth");

        // prepareDialog: keep MFV containers non-null even when the AOSP layout lacks them.
        hook(mfv, "prepareDialog", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!active()) {
                    return;
                }
                Object self = param.thisObject;
                View dialogView = (View) XposedHelpers.getObjectField(self, "mDialogView");
                Context ctx = dialogView != null ? dialogView.getContext()
                        : (Context) XposedHelpers.getObjectField(self, "mContext");
                XposedHelpers.setObjectField(self, "mDialogMain", new FrameLayout(ctx));
                XposedHelpers.setObjectField(self, "mDialogRowsView2", new FrameLayout(ctx));
                param.setResult(null);
            }
        });

        // Put every row (including MFV "row2" streams) into the AOSP rows container.
        XC_MethodHook rowView = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!active()) {
                    return;
                }
                Object view = XposedHelpers.getObjectField(param.args[0], "view");
                Object rows = XposedHelpers.getObjectField(param.thisObject, "mDialogRowsView");
                if (rows instanceof ViewGroup && view instanceof View) {
                    ViewGroup group = (ViewGroup) rows;
                    View child = (View) view;
                    boolean add = "addRowView".equals(((Member) param.method).getName());
                    if (add && child.getParent() == null) {
                        group.addView(child);
                    } else if (!add && child.getParent() == group) {
                        group.removeView(child);
                    }
                }
                param.setResult(null);
            }
        };
        hook(mfv, "addRowView", rowView, "com.android.systemui.volume.VolumeDialogImpl$VolumeRow");
        hook(mfv, "removeRowView", rowView, "com.android.systemui.volume.VolumeDialogImpl$VolumeRow");
        log("AOSP volume dialog MFV skip hooks installed on MfvVolumeDialogImpl");
    }

    private static void skip(Class<?> clazz, String name, Object... params) {
        hook(clazz, name, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (active()) {
                    param.setResult(defaultValue(((Method) param.method).getReturnType()));
                }
            }
        }, params);
    }

    private static void hook(Class<?> clazz, String name, XC_MethodHook callback, Object... params) {
        try {
            Object[] args = new Object[params.length + 1];
            System.arraycopy(params, 0, args, 0, params.length);
            args[params.length] = callback;
            XposedHelpers.findAndHookMethod(clazz, name, args);
        } catch (Throwable t) {
            log("AOSP volume dialog hook " + name + " failed: " + t);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return Boolean.FALSE;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }

    private static void log(String message) {
        XposedBridge.log("RedMagicX: " + message);
    }
}
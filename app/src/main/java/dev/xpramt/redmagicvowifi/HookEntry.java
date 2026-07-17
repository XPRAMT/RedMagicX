package dev.xpramt.redmagicvowifi;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ComponentName;
import android.media.AudioManager;
import android.hardware.camera2.CameraManager;
import android.content.pm.PackageManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    private static final String SETTINGS = "com.android.settings";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String ANDROID = "android";
    private static final String STOCK_LAUNCHER = "com.zte.mifavor.launcher";
    private static final int STREAM_MUSIC = AudioManager.STREAM_MUSIC;
    private static final int ADJUST_LOWER = AudioManager.ADJUST_LOWER;
    private static final int ADJUST_RAISE = AudioManager.ADJUST_RAISE;

    private final ThreadLocal<Boolean> applyingVolume = new ThreadLocal<>();
    private boolean torchEnabled;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (ANDROID.equals(lpparam.packageName)) {
            hookAudioService(lpparam);
            hookRecentTasksFilter(lpparam);
            return;
        }
        if (!SETTINGS.equals(lpparam.packageName)
                && !SYSTEM_UI.equals(lpparam.packageName)
                && !STOCK_LAUNCHER.equals(lpparam.packageName)) {
            return;
        }
        Config.Snapshot config = Config.loadForHook();
        log("loaded in " + lpparam.packageName
                + " wfc=" + config.enableWfcSettings
                + " icon=" + config.enableStatusIcon
                + " style=" + config.iconStyle
                + " volumeStep=" + config.volumeStepEnabled + "/" + config.volumeStep
                + " assistant=" + config.assistantRedirectEnabled + "/" + config.assistantTarget
                + " launcher=" + config.launcherOverrideEnabled + "/" + config.launcherPackage);
        if (SETTINGS.equals(lpparam.packageName) && config.enableWfcSettings) {
            hookSettingsWfcGate(lpparam);
        }
        if (SYSTEM_UI.equals(lpparam.packageName)) {
            hookSystemUiStartAssist(lpparam);
            hookSystemUiAssistantBroadcast(lpparam);
            hookRecentUiFilter(lpparam);
            if (config.enableStatusIcon) {
                hookSystemUiAbroad(lpparam);
            }
            if (Config.STYLE_GEN_BD.equals(config.iconStyle)) {
                hookSystemUiVariantGenBd(lpparam);
            } else if (Config.STYLE_ARRAY_HOOK.equals(config.iconStyle)) {
                hookSystemUiBdArray(lpparam);
            }
        }
        if (STOCK_LAUNCHER.equals(lpparam.packageName)) {
            hookRecentUiFilter(lpparam);
        }
    }

    private void hookSystemUiAssistantBroadcast(XC_LoadPackage.LoadPackageParam lpparam) {
        hookBroadcastMethods(ContextWrapper.class, "ContextWrapper");
        Class<?> contextImpl = findClassIfExists("android.app.ContextImpl", lpparam.classLoader);
        if (contextImpl != null) {
            hookBroadcastMethods(contextImpl, "ContextImpl");
        } else {
            log("ContextImpl not found; assistant broadcast hook partially installed");
        }
    }

    private void hookBroadcastMethods(Class<?> contextClass, String label) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Intent intent = firstIntentArg(param.args);
                if (intent == null || !isZteAssistantWakeAction(intent.getAction())) {
                    return;
                }
                Config.Snapshot config = Config.loadForHook();
                if (!config.assistantRedirectEnabled) {
                    log("assistant broadcast observed but redirect disabled action=" + intent.getAction());
                    return;
                }
                Context context = contextFromObject(param.thisObject);
                if (context == null) {
                    log("assistant broadcast context unavailable action=" + intent.getAction());
                    return;
                }
                if (launchAssistantTarget(context, config.assistantTarget)) {
                    log("redirected SystemUI assistant broadcast action=" + intent.getAction()
                            + " target=" + config.assistantTarget);
                    param.setResult(null);
                }
            }
        };
        try {
            XposedBridge.hookAllMethods(contextClass, "sendBroadcast", hook);
            XposedBridge.hookAllMethods(contextClass, "sendBroadcastAsUser", hook);
            XposedBridge.hookAllMethods(contextClass, "sendOrderedBroadcast", hook);
            XposedBridge.hookAllMethods(contextClass, "sendOrderedBroadcastAsUser", hook);
            log(label + " assistant broadcast hooks installed");
        } catch (Throwable throwable) {
            log(label + " assistant broadcast hook failed: " + throwable);
        }
    }

    private Intent firstIntentArg(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Intent) {
                return (Intent) arg;
            }
        }
        return null;
    }

    private void hookSystemUiStartAssist(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> commandQueue = findClassIfExists("com.android.systemui.statusbar.CommandQueue", lpparam.classLoader);
        if (commandQueue == null) {
            log("CommandQueue not found; assistant redirect skipped");
            return;
        }
        try {
            findAndHookMethod(commandQueue, "startAssist", android.os.Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Config.Snapshot config = Config.loadForHook();
                    if (!config.assistantRedirectEnabled) {
                        return;
                    }
                    Context context = contextFromObject(param.thisObject);
                    if (context == null) {
                        log("CommandQueue context unavailable");
                        return;
                    }
                    if (launchAssistantTarget(context, config.assistantTarget)) {
                        log("redirected SystemUI startAssist target=" + config.assistantTarget);
                        param.setResult(null);
                    }
                }
            });
            log("SystemUI startAssist hook installed");
        } catch (Throwable throwable) {
            log("SystemUI startAssist hook failed: " + throwable);
        }
    }

    private boolean isZteAssistantWakeAction(String action) {
        if (action == null) {
            return false;
        }
        return "event_Home_Longpressed".equals(action)
                || "event_Home_Longpressed_above26".equals(action)
                || "event_home_Verylongpressed".equals(action)
                || "com.zte.aiassistant.START_FLOW".equals(action)
                || "com.zte.aiassistant.action.WAKEUP_TO_LISTEN".equals(action)
                || "com.zte.aiassistant.action.AI_HALF_ENTRY_OUTER".equals(action)
                || "com.zte.aiassistant.action.EXTERNAL_REQUEST".equals(action);
    }

    private boolean launchAssistantTarget(Context context, String target) {
        if (target == null || target.isEmpty() || Config.ASSISTANT_TARGET_DEFAULT.equals(target)) {
            target = Config.TARGET_PREFIX_ACTION + Config.ACTION_DEFAULT_ASSIST;
        } else if (Config.ASSISTANT_TARGET_GOOGLE_VOICE.equals(target)) {
            target = Config.TARGET_PREFIX_ACTION + Config.ACTION_GOOGLE_VOICE;
        } else if (Config.ASSISTANT_TARGET_CHATGPT.equals(target)) {
            target = Config.TARGET_PREFIX_APP + "com.openai.chatgpt";
        }
        if (target.startsWith(Config.TARGET_PREFIX_APP)) {
            return launchApp(context, target.substring(Config.TARGET_PREFIX_APP.length()));
        }
        if (!target.startsWith(Config.TARGET_PREFIX_ACTION)) {
            return launchSystemAction(context, Config.ACTION_DEFAULT_ASSIST);
        }
        return launchSystemAction(context, target.substring(Config.TARGET_PREFIX_ACTION.length()));
    }

    private boolean launchSystemAction(Context context, String action) {
        if (Config.ACTION_RECENTS.equals(action)) {
            return runShellCommand("input keyevent 187");
        }
        if (Config.ACTION_SCREENSHOT.equals(action)) {
            return runShellCommand("input keyevent 120");
        }
        if (Config.ACTION_FLASHLIGHT.equals(action)) {
            return toggleFlashlight(context);
        }
        Intent intent;
        if (Config.ACTION_GOOGLE_VOICE.equals(action)) {
            intent = new Intent(Intent.ACTION_VOICE_COMMAND);
            intent.setPackage("com.google.android.googlequicksearchbox");
        } else {
            intent = new Intent(Intent.ACTION_ASSIST);
        }
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (Throwable throwable) {
            log("system action launch failed action=" + action + " " + throwable);
            return false;
        }
    }

    private boolean launchApp(Context context, String packageName) {
        try {
            PackageManager packageManager = context.getPackageManager();
            Intent intent = packageManager.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                log("app launch intent unavailable package=" + packageName);
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable throwable) {
            log("app launch failed package=" + packageName + " " + throwable);
            return false;
        }
    }

    private boolean runShellCommand(String command) {
        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            return true;
        } catch (Throwable throwable) {
            log("shell action failed command=" + command + " " + throwable);
            return false;
        }
    }

    private boolean toggleFlashlight(Context context) {
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) {
                return false;
            }
            for (String cameraId : cameraManager.getCameraIdList()) {
                Boolean available = cameraManager.getCameraCharacteristics(cameraId)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = cameraManager.getCameraCharacteristics(cameraId)
                        .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(available)
                        && facing != null
                        && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                    torchEnabled = !torchEnabled;
                    cameraManager.setTorchMode(cameraId, torchEnabled);
                    return true;
                }
            }
        } catch (Throwable throwable) {
            log("flashlight toggle failed: " + throwable);
        }
        return false;
    }

    private Context contextFromObject(Object object) {
        if (object instanceof Context) {
            return (Context) object;
        }
        String[] fieldNames = new String[]{"mContext", "context"};
        for (String fieldName : fieldNames) {
            try {
                Object value = XposedHelpers.getObjectField(object, fieldName);
                if (value instanceof Context) {
                    return (Context) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void hookAudioService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> audioService = XposedHelpers.findClass("com.android.server.audio.AudioService", lpparam.classLoader);
            XposedBridge.hookAllMethods(audioService, "adjustStreamVolume", new VolumeAdjustHook());
            XposedBridge.hookAllMethods(audioService, "adjustSuggestedStreamVolume", new VolumeAdjustHook());
            log("AudioService volume step hooks installed");
        } catch (Throwable throwable) {
            log("AudioService hook failed: " + throwable);
        }
    }

    private void hookRecentTasksFilter(XC_LoadPackage.LoadPackageParam lpparam) {
        hookRecentTasksMethod(lpparam, "com.android.server.wm.ActivityTaskManagerService", "getRecentTasks");
        hookRecentTasksMethod(lpparam, "com.android.server.wm.RecentTasks", "getRecentTasks");
        hookRecentTasksMethod(lpparam, "com.android.server.wm.RecentTasks", "getRecentTasksImpl");
    }

    private void hookRecentUiFilter(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] classNames = new String[]{
                "com.android.quickstep.RecentTasksList",
                "com.android.quickstep.RecentsModel",
                "com.android.quickstep.views.RecentsView",
                "com.android.quickstep.TaskOverlayFactory",
                "com.android.systemui.shared.recents.model.RecentsTaskLoader",
                "com.android.systemui.recents.RecentsImplementation"
        };
        for (String className : classNames) {
            hookRecentUiClass(lpparam, className);
        }
    }

    private void hookRecentUiClass(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        Class<?> clazz = findClassIfExists(className, lpparam.classLoader);
        if (clazz == null) {
            return;
        }
        int hooked = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            String name = method.getName();
            if (!name.toLowerCase().contains("recent") && !name.toLowerCase().contains("task")) {
                continue;
            }
            try {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        filterRecentArgs(param);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        filterRecentMethodResult(param);
                    }
                });
                hooked++;
            } catch (Throwable ignored) {
            }
        }
        if (hooked > 0) {
            log(className + " recent UI filters installed count=" + hooked);
        }
    }

    private void hookRecentTasksMethod(XC_LoadPackage.LoadPackageParam lpparam, String className, String methodName) {
        Class<?> clazz = findClassIfExists(className, lpparam.classLoader);
        if (clazz == null) {
            return;
        }
        try {
            XposedBridge.hookAllMethods(clazz, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Config.Snapshot config = Config.loadForHook();
                    if (!config.launcherOverrideEnabled || config.launcherPackage.isEmpty()) {
                        return;
                    }
                    int removed = filterRecentMethodResult(param);
                    if (removed > 0) {
                        log("filtered launcher from recent tasks package=" + config.launcherPackage + " count=" + removed);
                    }
                }
            });
            log(className + "#" + methodName + " recent task filter installed");
        } catch (Throwable throwable) {
            log(className + "#" + methodName + " recent task filter failed: " + throwable);
        }
    }

    private void filterRecentArgs(XC_MethodHook.MethodHookParam param) {
        Config.Snapshot config = Config.loadForHook();
        if (!config.launcherOverrideEnabled || config.launcherPackage.isEmpty() || param.args == null) {
            return;
        }
        for (int i = 0; i < param.args.length; i++) {
            Object arg = param.args[i];
            if (arg instanceof List) {
                param.args[i] = filteredRecentList((List<?>) arg, config.launcherPackage);
            } else {
                filterRecentResult(arg, config.launcherPackage);
            }
        }
    }

    private int filterRecentMethodResult(XC_MethodHook.MethodHookParam param) {
        Config.Snapshot config = Config.loadForHook();
        if (!config.launcherOverrideEnabled || config.launcherPackage.isEmpty()) {
            return 0;
        }
        Object result = param.getResult();
        if (result instanceof List) {
            List<?> filtered = filteredRecentList((List<?>) result, config.launcherPackage);
            if (filtered != result) {
                param.setResult(filtered);
            }
            return Math.max(0, ((List<?>) result).size() - filtered.size());
        }
        return filterRecentResult(result, config.launcherPackage);
    }

    private int filterRecentResult(Object result, String packageName) {
        if (result == null) {
            return 0;
        }
        if (result instanceof List) {
            return filterRecentListInPlace((List<?>) result, packageName);
        }
        try {
            Object list = XposedHelpers.callMethod(result, "getList");
            if (list instanceof List) {
                return filterRecentListInPlace((List<?>) list, packageName);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private List<?> filteredRecentList(List<?> tasks, String packageName) {
        ArrayList<Object> filtered = new ArrayList<>();
        for (Object task : tasks) {
            if (!recentTaskBelongsToPackage(task, packageName)) {
                filtered.add(task);
            }
        }
        return filtered.size() == tasks.size() ? tasks : filtered;
    }

    private int filterRecentListInPlace(List<?> tasks, String packageName) {
        int removed = 0;
        try {
            Iterator<?> iterator = tasks.iterator();
            while (iterator.hasNext()) {
                Object task = iterator.next();
                if (recentTaskBelongsToPackage(task, packageName)) {
                    iterator.remove();
                    removed++;
                }
            }
        } catch (Throwable throwable) {
            log("recent task list filter failed: " + throwable);
        }
        return removed;
    }

    private boolean recentTaskBelongsToPackage(Object task, String packageName) {
        if (task == null || packageName == null || packageName.isEmpty()) {
            return false;
        }
        String[] componentFields = new String[]{"topActivity", "baseActivity", "origActivity", "realActivity"};
        for (String field : componentFields) {
            try {
                Object value = XposedHelpers.getObjectField(task, field);
                if (value instanceof ComponentName && packageName.equals(((ComponentName) value).getPackageName())) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        String[] intentFields = new String[]{"baseIntent", "intent"};
        for (String field : intentFields) {
            try {
                Object value = XposedHelpers.getObjectField(task, field);
                if (value instanceof Intent) {
                    ComponentName component = ((Intent) value).getComponent();
                    if (component != null && packageName.equals(component.getPackageName())) {
                        return true;
                    }
                    String intentPackage = ((Intent) value).getPackage();
                    if (packageName.equals(intentPackage)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (objectGraphContainsPackage(task, packageName, 0)) {
            return true;
        }
        try {
            String text = String.valueOf(task);
            if (text.contains(packageName)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean objectGraphContainsPackage(Object object, String packageName, int depth) {
        if (object == null || depth > 2) {
            return false;
        }
        if (object instanceof ComponentName) {
            return packageName.equals(((ComponentName) object).getPackageName());
        }
        if (object instanceof Intent) {
            ComponentName component = ((Intent) object).getComponent();
            return component != null && packageName.equals(component.getPackageName());
        }
        if (object instanceof String) {
            return packageName.equals(object);
        }
        Class<?> clazz = object.getClass();
        String className = clazz.getName();
        if (className.startsWith("java.") || className.startsWith("android.graphics.")) {
            return false;
        }
        for (Field field : clazz.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(object);
                if (objectGraphContainsPackage(value, packageName, depth + 1)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private final class VolumeAdjustHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (Boolean.TRUE.equals(applyingVolume.get())) {
                return;
            }
            Config.Snapshot config = Config.loadForHook();
            if (!config.volumeStepEnabled) {
                return;
            }
            int stream = intArg(param, 0, Integer.MIN_VALUE);
            int direction = intArg(param, 1, 0);
            int flags = intArg(param, 2, 0);
            if (!shouldHandleVolume(stream, direction)) {
                return;
            }
            AudioManager audioManager = audioManager(param.thisObject);
            if (audioManager == null) {
                log("AudioManager unavailable");
                return;
            }
            try {
                applyingVolume.set(true);
                int current = audioManager.getStreamVolume(STREAM_MUSIC);
                int min = audioManager.getStreamMinVolume(STREAM_MUSIC);
                int max = audioManager.getStreamMaxVolume(STREAM_MUSIC);
                int delta = direction == ADJUST_RAISE ? config.volumeStep : -config.volumeStep;
                int target = clamp(current + delta, min, max);
                if (target != current) {
                    audioManager.setStreamVolume(STREAM_MUSIC, target, flags);
                }
                param.setResult(null);
                log("music volume " + current + " -> " + target + " step=" + config.volumeStep);
            } catch (Throwable throwable) {
                log("custom volume step failed: " + throwable);
            } finally {
                applyingVolume.remove();
            }
        }
    }

    private boolean shouldHandleVolume(int stream, int direction) {
        if (direction != ADJUST_RAISE && direction != ADJUST_LOWER) {
            return false;
        }
        return stream == STREAM_MUSIC || stream == AudioManager.USE_DEFAULT_STREAM_TYPE;
    }

    private int intArg(XC_MethodHook.MethodHookParam param, int index, int fallback) {
        if (param.args == null || index < 0 || index >= param.args.length || !(param.args[index] instanceof Integer)) {
            return fallback;
        }
        return (Integer) param.args[index];
    }

    private AudioManager audioManager(Object audioService) {
        try {
            Object context = XposedHelpers.getObjectField(audioService, "mContext");
            if (context instanceof Context) {
                return (AudioManager) ((Context) context).getSystemService(Context.AUDIO_SERVICE);
            }
        } catch (Throwable throwable) {
            log("mContext lookup failed: " + throwable);
        }
        return null;
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private void hookSettingsWfcGate(XC_LoadPackage.LoadPackageParam lpparam) {
        hookZteBooleanProperty(
                lpparam,
                "ro.vendor.feature.zte_feature_need_wfc_for_domestic",
                true
        );
        hookAndroidBooleanProperty(
                lpparam,
                "ro.vendor.feature.zte_feature_need_wfc_for_domestic",
                true
        );
    }

    private void hookSystemUiAbroad(XC_LoadPackage.LoadPackageParam lpparam) {
        hookZteStringProperty(lpparam, "ro.vendor.mifavor.custom", "abroad", true);
        hookZteStringProperty(lpparam, "ro.mifavor.custom", "abroad", true);
        hookAndroidStringProperty(lpparam, "ro.vendor.mifavor.custom", "abroad", true);
        hookAndroidStringProperty(lpparam, "ro.mifavor.custom", "abroad", true);
        hookFlavorIsAbroad(lpparam, true);
    }

    private void hookSystemUiVariantGenBd(XC_LoadPackage.LoadPackageParam lpparam) {
        hookZteStringProperty(lpparam, "persist.custom.variant.id", "GEN_BD", false);
        hookAndroidStringProperty(lpparam, "persist.custom.variant.id", "GEN_BD", false);
    }

    private void hookSystemUiBdArray(XC_LoadPackage.LoadPackageParam lpparam) {
        hookSystemUiVariantGenBd(lpparam);
        Class<?> imsClass = findClassIfExists("com.zte.feature.signal.ImsUpdateFeature", lpparam.classLoader);
        if (imsClass == null) {
            log("ImsUpdateFeature not found; array hook skipped");
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(imsClass, "getSingleCardImsIconArrayResId", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int resId = getArrayId("bd_single_card_volte_vowifi_icons_array", lpparam.classLoader);
                    if (resId != 0) {
                        param.setResult(resId);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("array hook failed: " + throwable);
        }
    }

    private int getArrayId(String name, ClassLoader classLoader) {
        try {
            Class<?> rArray = XposedHelpers.findClass("com.android.systemui.R$array", classLoader);
            return XposedHelpers.getStaticIntField(rArray, name);
        } catch (Throwable throwable) {
            log("array id not found: " + name + " " + throwable);
            return 0;
        }
    }

    private void hookFlavorIsAbroad(XC_LoadPackage.LoadPackageParam lpparam, boolean imsOnly) {
        String[] classNames = new String[]{
                "com.zte.utils.FlavorUtils$Companion",
                "com.zte.utils.FlavorUtils"
        };
        for (String className : classNames) {
            Class<?> clazz = findClassIfExists(className, lpparam.classLoader);
            if (clazz == null) {
                continue;
            }
            hookNoArgBoolean(clazz, "isAbroad", true, imsOnly);
            hookNoArgBoolean(clazz, "isAbroadProject", true, imsOnly);
        }
    }

    private void hookNoArgBoolean(Class<?> clazz, String methodName, boolean result, boolean imsOnly) {
        try {
            XposedHelpers.findAndHookMethod(clazz, methodName, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!imsOnly || isImsIconStack()) {
                        param.setResult(result);
                    }
                }
            });
        } catch (Throwable ignored) {
            // Method name varies between framework builds. Missing method is harmless.
        }
    }

    private void hookZteStringProperty(XC_LoadPackage.LoadPackageParam lpparam, String key, String value, boolean imsOnly) {
        Class<?> propertyClass = findZtePropertyClass(lpparam.classLoader);
        if (propertyClass == null) {
            log("SystemPropertiesZTE not found for " + key);
            return;
        }
        hookStringGet(propertyClass, key, value, imsOnly);
    }

    private void hookZteBooleanProperty(XC_LoadPackage.LoadPackageParam lpparam, String key, boolean value) {
        Class<?> propertyClass = findZtePropertyClass(lpparam.classLoader);
        if (propertyClass == null) {
            log("SystemPropertiesZTE not found for " + key);
            return;
        }
        try {
            findAndHookMethod(propertyClass, "getBoolean", String.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (key.equals(param.args[0])) {
                        param.setResult(value);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("getBoolean hook failed for " + key + ": " + throwable);
        }
    }

    private void hookAndroidStringProperty(XC_LoadPackage.LoadPackageParam lpparam, String key, String value, boolean imsOnly) {
        Class<?> propertyClass = findClassIfExists("android.os.SystemProperties", lpparam.classLoader);
        if (propertyClass == null) {
            log("android.os.SystemProperties not found for " + key);
            return;
        }
        hookStringGet(propertyClass, key, value, imsOnly);
    }

    private void hookAndroidBooleanProperty(XC_LoadPackage.LoadPackageParam lpparam, String key, boolean value) {
        Class<?> propertyClass = findClassIfExists("android.os.SystemProperties", lpparam.classLoader);
        if (propertyClass == null) {
            log("android.os.SystemProperties not found for " + key);
            return;
        }
        try {
            findAndHookMethod(propertyClass, "getBoolean", String.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (key.equals(param.args[0])) {
                        param.setResult(value);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("android getBoolean hook failed for " + key + ": " + throwable);
        }
    }

    private void hookStringGet(Class<?> propertyClass, String key, String value, boolean imsOnly) {
        try {
            findAndHookMethod(propertyClass, "get", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (key.equals(param.args[0])) {
                        String scopedValue = scopedSystemUiFlavorValue(value, imsOnly);
                        if (scopedValue != null) {
                            param.setResult(scopedValue);
                        }
                    }
                }
            });
        } catch (Throwable ignored) {
        }
        try {
            findAndHookMethod(propertyClass, "get", String.class, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (key.equals(param.args[0])) {
                        String scopedValue = scopedSystemUiFlavorValue(value, imsOnly);
                        if (scopedValue != null) {
                            param.setResult(scopedValue);
                        }
                    }
                }
            });
        } catch (Throwable throwable) {
            log("get hook failed for " + key + ": " + throwable);
        }
    }

    private String scopedSystemUiFlavorValue(String value, boolean imsOnly) {
        if (!imsOnly) {
            return value;
        }
        if (isHomeHandleAssistantStack()) {
            return "home";
        }
        if (isImsIconStack()) {
            return value;
        }
        return null;
    }

    private boolean isHomeHandleAssistantStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className == null) {
                continue;
            }
            if (className.contains("GestureStub")
                    || className.contains("GestureController")
                    || className.contains("NavigationBar")
                    || className.contains("NavigationHandle")
                    || className.contains("OverviewProxy")
                    || className.contains("RecentsAnimationDeviceState")
                    || className.contains("Assist")
                    || className.contains("QuickStep")
                    || className.contains("Launcher")) {
                return true;
            }
        }
        return false;
    }

    private boolean isImsIconStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className == null) {
                continue;
            }
            if (className.contains("Gesture")
                    || className.contains("NavigationBar")
                    || className.contains("NavigationHandle")
                    || className.contains("OverviewProxy")
                    || className.contains("Assist")
                    || className.contains("Keyguard")
                    || className.contains("Launcher")) {
                return false;
            }
            if (className.startsWith("com.zte.feature.signal.")
                    || className.startsWith("com.zte.statusbar.signal.")
                    || className.startsWith("com.zte.adapt.mifavor.signal.")
                    || className.startsWith("com.zte.qs.tiles.VoWifiTile")
                    || className.startsWith("com.zte.qs.tiles.VolteTile")
                    || className.startsWith("com.android.systemui.statusbar.connectivity.")) {
                return true;
            }
        }
        return false;
    }

    private Class<?> findZtePropertyClass(ClassLoader classLoader) {
        String[] classNames = new String[]{
                "com.zte.settings.utils.SystemPropertiesZTE",
                "com.zte.utils.SystemPropertiesZTE",
                "com.zte.common.SystemPropertiesZTE",
                "com.zte.mifavor.utils.SystemPropertiesZTE"
        };
        for (String className : classNames) {
            Class<?> clazz = findClassIfExists(className, classLoader);
            if (clazz != null) {
                return clazz;
            }
        }
        return null;
    }

    private void log(String message) {
        XposedBridge.log("RedMagicX: " + message);
    }
}

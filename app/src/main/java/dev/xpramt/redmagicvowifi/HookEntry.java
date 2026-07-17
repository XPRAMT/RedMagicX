package dev.xpramt.redmagicvowifi;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.media.AudioManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    private static final String SETTINGS = "com.android.settings";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String ANDROID = "android";
    private static final int STREAM_MUSIC = AudioManager.STREAM_MUSIC;
    private static final int ADJUST_LOWER = AudioManager.ADJUST_LOWER;
    private static final int ADJUST_RAISE = AudioManager.ADJUST_RAISE;

    private final ThreadLocal<Boolean> applyingVolume = new ThreadLocal<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (ANDROID.equals(lpparam.packageName)) {
            hookAudioService(lpparam);
            return;
        }
        if (!SETTINGS.equals(lpparam.packageName) && !SYSTEM_UI.equals(lpparam.packageName)) {
            return;
        }
        Config.Snapshot config = Config.loadForHook();
        log("loaded in " + lpparam.packageName
                + " mode=" + config.operationMode
                + " wfc=" + config.enableWfcSettings
                + " icon=" + config.enableStatusIcon
                + " style=" + config.iconStyle
                + " volumeStep=" + config.volumeStepEnabled + "/" + config.volumeStep
                + " assistant=" + config.assistantRedirectEnabled + "/" + config.assistantTarget);
        if (!Config.MODE_LSPOSED.equals(config.operationMode)) {
            return;
        }
        if (SETTINGS.equals(lpparam.packageName) && config.enableWfcSettings) {
            hookSettingsWfcGate(lpparam);
        }
        if (SYSTEM_UI.equals(lpparam.packageName)) {
            hookSystemUiStartAssist(lpparam);
            hookSystemUiAssistantBroadcast(lpparam);
            if (config.enableStatusIcon) {
                hookSystemUiAbroad(lpparam);
            }
            if (Config.STYLE_GEN_BD.equals(config.iconStyle)) {
                hookSystemUiVariantGenBd(lpparam);
            } else if (Config.STYLE_ARRAY_HOOK.equals(config.iconStyle)) {
                hookSystemUiBdArray(lpparam);
            }
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
        Intent intent;
        if (Config.ASSISTANT_TARGET_CHATGPT.equals(target)) {
            intent = new Intent(Intent.ACTION_ASSIST);
            intent.setPackage("com.openai.chatgpt");
        } else if (Config.ASSISTANT_TARGET_GOOGLE_VOICE.equals(target)) {
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
        } catch (Throwable first) {
            log("assistant target launch failed: " + first);
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("googlequicksearchbox://assistant.google.com"));
                fallback.setPackage("com.google.android.googlequicksearchbox");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                return true;
            } catch (Throwable second) {
                log("assistant fallback launch failed: " + second);
                return false;
            }
        }
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
                    if (key.equals(param.args[0]) && (!imsOnly || isImsIconStack())) {
                        param.setResult(value);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
        try {
            findAndHookMethod(propertyClass, "get", String.class, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (key.equals(param.args[0]) && (!imsOnly || isImsIconStack())) {
                        param.setResult(value);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("get hook failed for " + key + ": " + throwable);
        }
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
        XposedBridge.log("RedMagicVoWiFi: " + message);
    }
}

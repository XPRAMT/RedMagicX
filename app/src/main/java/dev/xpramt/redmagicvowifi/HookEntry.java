package dev.xpramt.redmagicvowifi;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClassIfExists;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    private static final String SETTINGS = "com.android.settings";
    private static final String SYSTEM_UI = "com.android.systemui";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SETTINGS.equals(lpparam.packageName) && !SYSTEM_UI.equals(lpparam.packageName)) {
            return;
        }
        Config.Snapshot config = Config.loadForHook();
        log("loaded in " + lpparam.packageName
                + " mode=" + config.operationMode
                + " wfc=" + config.enableWfcSettings
                + " icon=" + config.enableStatusIcon
                + " style=" + config.iconStyle);
        if (!Config.MODE_LSPOSED.equals(config.operationMode)) {
            return;
        }
        if (SETTINGS.equals(lpparam.packageName) && config.enableWfcSettings) {
            hookSettingsWfcGate(lpparam);
        }
        if (SYSTEM_UI.equals(lpparam.packageName)) {
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
        hookZteStringProperty(lpparam, "ro.vendor.mifavor.custom", "abroad");
        hookZteStringProperty(lpparam, "ro.mifavor.custom", "abroad");
        hookAndroidStringProperty(lpparam, "ro.vendor.mifavor.custom", "abroad");
        hookAndroidStringProperty(lpparam, "ro.mifavor.custom", "abroad");
        hookFlavorIsAbroad(lpparam);
    }

    private void hookSystemUiVariantGenBd(XC_LoadPackage.LoadPackageParam lpparam) {
        hookZteStringProperty(lpparam, "persist.custom.variant.id", "GEN_BD");
        hookAndroidStringProperty(lpparam, "persist.custom.variant.id", "GEN_BD");
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

    private void hookFlavorIsAbroad(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] classNames = new String[]{
                "com.zte.utils.FlavorUtils$Companion",
                "com.zte.utils.FlavorUtils"
        };
        for (String className : classNames) {
            Class<?> clazz = findClassIfExists(className, lpparam.classLoader);
            if (clazz == null) {
                continue;
            }
            hookNoArgBoolean(clazz, "isAbroad", true);
            hookNoArgBoolean(clazz, "isAbroadProject", true);
        }
    }

    private void hookNoArgBoolean(Class<?> clazz, String methodName, boolean result) {
        try {
            XposedHelpers.findAndHookMethod(clazz, methodName, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(result);
                }
            });
        } catch (Throwable ignored) {
            // Method name varies between framework builds. Missing method is harmless.
        }
    }

    private void hookZteStringProperty(XC_LoadPackage.LoadPackageParam lpparam, String key, String value) {
        Class<?> propertyClass = findZtePropertyClass(lpparam.classLoader);
        if (propertyClass == null) {
            log("SystemPropertiesZTE not found for " + key);
            return;
        }
        hookStringGet(propertyClass, key, value);
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

    private void hookAndroidStringProperty(XC_LoadPackage.LoadPackageParam lpparam, String key, String value) {
        Class<?> propertyClass = findClassIfExists("android.os.SystemProperties", lpparam.classLoader);
        if (propertyClass == null) {
            log("android.os.SystemProperties not found for " + key);
            return;
        }
        hookStringGet(propertyClass, key, value);
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

    private void hookStringGet(Class<?> propertyClass, String key, String value) {
        try {
            findAndHookMethod(propertyClass, "get", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (key.equals(param.args[0])) {
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
                    if (key.equals(param.args[0])) {
                        param.setResult(value);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("get hook failed for " + key + ": " + throwable);
        }
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

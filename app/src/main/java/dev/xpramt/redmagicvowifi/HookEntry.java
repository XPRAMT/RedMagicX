package dev.xpramt.redmagicvowifi;

import static dev.xpramt.redmagicvowifi.Xp.findAndHookMethod;
import static dev.xpramt.redmagicvowifi.Xp.findClassIfExists;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ComponentName;
import android.app.Activity;
import android.media.AudioManager;
import android.hardware.camera2.CameraManager;
import android.app.WallpaperManager;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dev.xpramt.redmagicvowifi.Xp.MethodHook;
import dev.xpramt.redmagicvowifi.Xp.Param;
import dev.xpramt.redmagicvowifi.Xp.Target;
import io.github.libxposed.api.XposedModule;

public class HookEntry extends XposedModule {
    private static final String SETTINGS = "com.android.settings";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String ANDROID = "android";
    private static final String BLUETOOTH = "com.android.bluetooth";
    private static final String STOCK_LAUNCHER = "com.zte.mifavor.launcher";
    private static final String BEAUTIFY = "com.zte.beautify";
    private static final String BEAUTIFY_ADAPTER = "com.zte.beautifyadapter";
    private static final String EXTRA_OPEN_DOWNLOADED_THEMES =
            "dev.xpramt.redmagicvowifi.OPEN_DOWNLOADED_THEMES";
    private static final String THEME_SELECTOR_TAG = "redmagicx_theme_selector";
    private static final String BEAUTIFY_SPLASH_REDIRECTED = "redmagicx_splash_redirected";
    private static final int STREAM_MUSIC = AudioManager.STREAM_MUSIC;
    private static final int ADJUST_LOWER = AudioManager.ADJUST_LOWER;
    private static final int ADJUST_RAISE = AudioManager.ADJUST_RAISE;

    private final ThreadLocal<Boolean> applyingVolume = new ThreadLocal<>();
    private final Map<Object, Boolean> fullThemeIconPolicies =
            Collections.synchronizedMap(new WeakHashMap<>());
    private boolean torchEnabled;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        Xp.init(this);
        Config.useRemotePrefs(this);
        Xp.log("module loaded in " + param.getProcessName() + " api=" + getApiVersion()
                + " framework=" + getFrameworkName() + " " + getFrameworkVersion()
                + " systemServer=" + param.isSystemServer());
    }

    /** system_server: the former "android" scope. */
    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        Target target = new Target(ANDROID, param.getClassLoader(), null);
        hookAudioService(target);
        hookRecentTasksFilter(target);
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        handlePackage(new Target(param.getPackageName(), param.getClassLoader(), param.getApplicationInfo()));
    }

    private void handlePackage(Target lpparam) {
        if (!SETTINGS.equals(lpparam.packageName)
                && !SYSTEM_UI.equals(lpparam.packageName)
                && !STOCK_LAUNCHER.equals(lpparam.packageName)
                && !BEAUTIFY.equals(lpparam.packageName)
                && !BEAUTIFY_ADAPTER.equals(lpparam.packageName)
                && !BLUETOOTH.equals(lpparam.packageName)) {
            return;
        }
        Config.Snapshot config = Config.loadForHook();
        log("loaded in " + lpparam.packageName
                + " wfc=" + config.enableWfcSettings
                + " icon=" + config.enableStatusIcon
                + " style=" + config.iconStyle
                + " volumeStep=" + config.volumeStepEnabled + "/" + config.volumeStep
                + " aospVolumeDialog=" + config.aospVolumeDialog
                + " assistant=" + config.assistantRedirectEnabled + "/" + config.assistantTarget
                + " launcher=" + config.launcherOverrideEnabled + "/" + config.launcherPackage
                + " themeSelect=" + config.themeSelectEnabled
                + " skipBeautifySplash=" + config.skipBeautifySplash
                + " btReceiver=" + config.btReceiverEnabled);
        if (BLUETOOTH.equals(lpparam.packageName)) {
            hookBluetoothRoles(lpparam, config.btReceiverEnabled);
            return;
        }
        if (BEAUTIFY.equals(lpparam.packageName)) {
            hookBeautifySplash(lpparam);
            hookBeautifyThemeSelector(lpparam);
            return;
        }
        if (BEAUTIFY_ADAPTER.equals(lpparam.packageName)) {
            hookBeautifyAdapterResourceTypes(lpparam);
            return;
        }
        if (SETTINGS.equals(lpparam.packageName) && config.enableWfcSettings) {
            hookSettingsWfcGate(lpparam);
        }
        if (SYSTEM_UI.equals(lpparam.packageName)) {
            hookSystemUiStartAssist(lpparam);
            hookSystemUiAssistantBroadcast(lpparam);
            hookAospVolumeDialog(lpparam);
            log("Recent UI hooks disabled to avoid Recents restart loop");
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
            hookLauncherRecentTasksData(lpparam);
        }
    }

    /**
     * Bluetooth receiver mode. The Android Bluetooth stack on this phone does not run the A2DP source role
     * (headphones) and the A2DP sink role (other phones stream to this one) together reliably, so the two
     * profile services are mutually exclusive. The Bluetooth app decides which profile services to start in
     * Config.PROFILE_SERVICES_AND_FLAGS, built from the two static isEnabled() methods below (the only
     * readers of the sink/source system properties in the app). In normal mode nothing is hooked except the
     * status report; the choice is read once when the Bluetooth process starts, so a mode change needs the
     * Bluetooth stack to be restarted.
     */
    private void hookBluetoothRoles(Target lpparam, boolean receiver) {
        reportBluetoothMode(receiver ? Config.BT_MODE_RECEIVER : Config.BT_MODE_NORMAL, lpparam);
        if (!receiver) {
            log("Bluetooth: normal (A2DP source) mode, no role hooks");
            return;
        }
        try {
            Class<?> sink = Xp.findClass(
                    "com.android.bluetooth.a2dpsink.A2dpSinkService", lpparam.classLoader);
            Class<?> source = Xp.findClass(
                    "com.android.bluetooth.a2dp.A2dpService", lpparam.classLoader);
            findAndHookMethod(sink, "isEnabled", Xp.returnConstant(Boolean.TRUE));
            findAndHookMethod(source, "isEnabled", Xp.returnConstant(Boolean.FALSE));
            log("Bluetooth: receiver (A2DP sink) mode, A2dpSinkService on and A2dpService off");
        } catch (Throwable throwable) {
            log("Bluetooth receiver hooks failed: " + throwable);
        }
    }

    /** Tells RedMagicX which mode this Bluetooth process started in (shown on the receiver page). */
    private void reportBluetoothMode(String mode, Target target) {
        // Application.onCreate is empty and AdapterApp may not call it, so report from AdapterService.onCreate,
        // which runs once whenever the Bluetooth stack starts.
        Class<?> adapter = findClassIfExists("com.android.bluetooth.btservice.AdapterService", target.classLoader);
        try {
            findAndHookMethod(adapter != null ? adapter : Application.class, "onCreate", new MethodHook() {
                private boolean sent;

                @Override
                protected void after(Param param) {
                    if (sent) {
                        return;
                    }
                    sent = true;
                    try {
                        Context context = (Context) param.thisObject;
                        Intent intent = new Intent(Config.ACTION_BT_MODE_REPORT);
                        intent.setPackage(Config.PACKAGE_NAME);
                        intent.putExtra(Config.EXTRA_BT_MODE, mode);
                        context.sendBroadcast(intent);
                        log("Bluetooth mode reported: " + mode);
                    } catch (Throwable throwable) {
                        log("Bluetooth mode report failed: " + throwable);
                    }
                }
            });
            log("Bluetooth mode report hook installed on "
                    + (adapter != null ? "AdapterService" : "Application") + ".onCreate");
        } catch (Throwable throwable) {
            log("Bluetooth mode report hook failed: " + throwable);
        }
    }

    private void hookBeautifySplash(Target lpparam) {
        Class<?> logoActivityClass = findClassIfExists(
                "com.zte.beautify.view.beauty.splashscreen.LogoActivity",
                lpparam.classLoader);
        if (logoActivityClass == null) {
            log("Beautify LogoActivity not found");
            return;
        }
        findAndHookMethod(logoActivityClass, "onCreate", Bundle.class, new MethodHook() {
            @Override
            protected void after(Param param) {
                redirectBeautifySplash((Activity) param.thisObject, "onCreate");
            }
        });
        findAndHookMethod(logoActivityClass, "ShowProgressTask", new MethodHook() {
            @Override
            protected void before(Param param) {
                if (!Config.loadForHook().skipBeautifySplash) {
                    return;
                }
                redirectBeautifySplash((Activity) param.thisObject, "ShowProgressTask");
                param.setResult(null);
            }
        });
       log("Beautify splash hook installed");
   }

    private void redirectBeautifySplash(Activity activity, String source) {
        if (!Config.loadForHook().skipBeautifySplash
                || Boolean.TRUE.equals(Xp.getAdditionalInstanceField(
                activity, BEAUTIFY_SPLASH_REDIRECTED))) {
            return;
        }
        Xp.setAdditionalInstanceField(activity, BEAUTIFY_SPLASH_REDIRECTED, true);
        Intent home = new Intent();
        home.setClassName(BEAUTIFY, "com.zte.beautify.view.beauty.home.HomeActivity");
        activity.startActivity(home);
        activity.finish();
        activity.overridePendingTransition(0, 0);
        log("Beautify splash skipped from " + source);
    }

    private void hookBeautifyAdapterTrial(Target lpparam) {
        findAndHookMethod("h.a", lpparam.classLoader, "b", Context.class, int.class, new MethodHook() {
            @Override
            protected void before(Param param) {
                if (Config.loadForHook().removeTrialLimit) {
                    param.setResult(null);
                    log("Beautify trial removal: blocked schedule()");
                }
            }
        });
        log("Beautify adapter trial hook installed");
    }

    private void hookBeautifyThemeSelector(Target lpparam) {
        hookBeautifyDownloadedThemesEntry(lpparam);
        hookBeautifyThemeApplyRoute(lpparam);
        Class<?> adapterClass = findClassIfExists(
                "com.zte.beautify.view.common.adapter.AdvertiseRecyclerViewAdapter",
                lpparam.classLoader);
        if (adapterClass == null) {
            log("Beautify preview adapter not found");
            return;
        }
        Xp.hookAllMethods(adapterClass, "onBindTitleView", new MethodHook() {
            @Override
            protected void after(Param param) {
                Config.Snapshot config = Config.loadForHook();
                if (!config.themeSelectEnabled || param.args == null || param.args.length == 0
                        || !param.thisObject.getClass().getName().contains("ThemePreviewAdapter")) {
                    return;
                }
                Object itemViewValue = Xp.getObjectField(param.args[0], "itemView");
                if (!(itemViewValue instanceof LinearLayout)) {
                    return;
                }
                LinearLayout itemView = (LinearLayout) itemViewValue;
                Context context = itemView.getContext();
                int introId = context.getResources().getIdentifier("theme_intro", "string", BEAUTIFY);
                String expectedTitle = introId == 0 ? "" : context.getString(introId);
                if (itemView.getChildCount() == 0 || !(itemView.getChildAt(0) instanceof TextView)
                        || !expectedTitle.contentEquals(((TextView) itemView.getChildAt(0)).getText())) {
                    return;
                }
                injectThemeSelector(itemView, config);
            }
        });
        log("Beautify theme selector adapter hook installed");
    }

    private void hookBeautifyDownloadedThemesEntry(Target lpparam) {
        Class<?> fragmentClass = findClassIfExists(
                "com.zte.beautify.view.themewallpaper.home.HomePageFragment",
                lpparam.classLoader);
        if (fragmentClass == null) {
            log("Beautify HomePageFragment not found");
            return;
        }
        Xp.hookAllMethods(fragmentClass, "onCreateView", new MethodHook() {
            @Override
            protected void after(Param param) {
                Object activity = Xp.callMethod(param.thisObject, "getActivity");
                if (activity == null) {
                    return;
                }
                Intent intent = (Intent) Xp.callMethod(activity, "getIntent");
                if (intent == null || !intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADED_THEMES, false)) {
                    return;
                }
                intent.removeExtra(EXTRA_OPEN_DOWNLOADED_THEMES);
                Object viewPager = Xp.getObjectField(param.thisObject, "viewPager");
                Object tabHost = Xp.getObjectField(param.thisObject, "tabHost");
                Xp.callMethod(viewPager, "setCurrentItem", 1);
                Xp.callMethod(tabHost, "setCurrentTab", 1);
                log("Beautify opened on downloaded themes tab");
            }
        });
        log("Beautify downloaded themes entry hook installed");
    }

    private void hookBeautifyThemeApplyRoute(Target lpparam) {
        Class<?> managerClass = findClassIfExists(
                "com.zte.beautify.view.base.BaseSetWallPaperManager", lpparam.classLoader);
        if (managerClass == null) {
            log("Beautify BaseSetWallPaperManager not found");
            return;
        }
        Xp.hookAllMethods(managerClass, "setThemeAsync", new MethodHook() {
            @Override
            protected void before(Param param) {
                Config.Snapshot config = Config.loadForHook();
                if (!config.themeSelectEnabled || param.args == null || param.args.length < 2) {
                    return;
                }
                if (config.themeFull) {
                    if (!config.themeBeautifyThirdPartyIcons) {
                        fullThemeIconPolicies.put(param.args[1], true);
                    }
                    return;
                }
                boolean trial = param.args.length >= 4 && Boolean.TRUE.equals(param.args[3]);
                if (config.themeType1) {
                    applySystemResourcesPreservingIcons(
                            (Context) param.args[0], param.thisObject, param.args[1],
                            !hasThemeIcons(config));
                }
                if (hasThemeWallpaper(config)) {
                    Xp.callMethod(param.thisObject, "setThemeDesktopWallPaperAsync",
                            param.args[0], param.args[1]);
                }
                if (hasThemeIcons(config)) {
                    Xp.callMethod(param.thisObject, "setIconChangeSelfDefine",
                            param.args[0], param.args[1], "",
                            !config.themeBeautifyThirdPartyIcons);
                }
                if (config.themeLockscreen) {
                    applyThemeLockscreen((Context) param.args[0], param.thisObject, param.args[1]);
                }
                param.setResult(null);
                log("Beautify selective apply systemResources=" + config.themeType1
                        + " wallpaper=" + hasThemeWallpaper(config)
                        + " icons=" + hasThemeIcons(config)
                        + " lockscreen=" + config.themeLockscreen
                        + " trial=" + trial);
            }
        });
        hookBeautifyFullThemeIconPolicy(lpparam);
        hookBeautifyWallpaperCompletion(lpparam);
        log("Beautify theme apply route hook installed");
    }

    private void hookBeautifyFullThemeIconPolicy(Target lpparam) {
        Class<?> taskClass = findClassIfExists(
                "com.zte.beautify.view.base.BaseSetWallPaperManager$SetThemeAsyncTask",
                lpparam.classLoader);
        if (taskClass == null) {
            log("Beautify full-theme task not found");
            return;
        }
        Xp.hookAllMethods(taskClass, "doCommonBackgroudAction", new MethodHook() {
            @Override
            protected void after(Param param) {
                Object themeBean = Xp.getObjectField(param.thisObject, "mBean");
                if (!Boolean.TRUE.equals(fullThemeIconPolicies.remove(themeBean))) {
                    return;
                }
                Object managerReference = Xp.getObjectField(param.thisObject, "mManager");
                Object contextReference = Xp.getObjectField(param.thisObject, "mContext");
                if (!(managerReference instanceof WeakReference)
                        || !(contextReference instanceof WeakReference)) {
                    log("Beautify full-theme icon policy references unavailable");
                    return;
                }
                Object manager = ((WeakReference<?>) managerReference).get();
                Object context = ((WeakReference<?>) contextReference).get();
                if (manager == null || !(context instanceof Context)) {
                    log("Beautify full-theme icon policy targets unavailable");
                    return;
                }
                Xp.callMethod(manager, "setIconChangeSelfDefine",
                        context, themeBean, "", true);
                log("Beautify full-theme third-party icon beautification disabled");
            }
        });
        log("Beautify full-theme icon policy hook installed");
    }

    private void hookBeautifyWallpaperCompletion(Target lpparam) {
        Class<?> taskClass = findClassIfExists(
                "com.zte.beautify.view.base.BaseSetWallPaperManager$SetThemeDesktopWallPaperAsyncTask",
                lpparam.classLoader);
        if (taskClass == null) {
            log("Beautify wallpaper task not found");
            return;
        }
        Xp.hookAllMethods(taskClass, "doCommonBackgroudAction", new MethodHook() {
            @Override
            protected void after(Param param) {
                Config.Snapshot config = Config.loadForHook();
                if (!config.themeSelectEnabled || config.themeFull || !hasThemeWallpaper(config)) {
                    return;
                }
                Object reference = Xp.getObjectField(param.thisObject, "mContext");
                if (!(reference instanceof WeakReference)) {
                    return;
                }
                Object value = ((WeakReference<?>) reference).get();
                if (!(value instanceof Context)) {
                    return;
                }
                Context context = (Context) value;
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "已套用桌布", Toast.LENGTH_SHORT).show());
            }
        });
        log("Beautify wallpaper completion toast hook installed");
    }

    private boolean hasThemeWallpaper(Config.Snapshot config) {
        return config.themeType2 || config.themeType6;
    }

    private boolean hasThemeIcons(Config.Snapshot config) {
        return config.themeType17 || config.themeType23;
    }

    private void injectThemeSelector(LinearLayout itemView, Config.Snapshot config) {
        Context context = itemView.getContext();
        for (int i = 0; i < itemView.getChildCount(); i++) {
            if (THEME_SELECTOR_TAG.equals(itemView.getChildAt(i).getTag())) {
                return;
            }
        }

        LinearLayout panel = new LinearLayout(context);
        panel.setTag(THEME_SELECTOR_TAG);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(18, 18, 24));
        background.setCornerRadius(dp(context, 8));
        panel.setBackground(background);

        Switch fullTheme = new Switch(context);
        fullTheme.setText("完整主題");
        fullTheme.setTextColor(Color.WHITE);
        fullTheme.setTextSize(15);
        fullTheme.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        fullTheme.setChecked(config.themeFull);
        applyThemeAccentTint(context, fullTheme);
        fullTheme.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Switch beautifyThirdPartyIcons = new Switch(context);
        beautifyThirdPartyIcons.setText("美化第三方應用程式圖示");
        beautifyThirdPartyIcons.setTextColor(Color.WHITE);
        beautifyThirdPartyIcons.setTextSize(14);
        beautifyThirdPartyIcons.setChecked(config.themeBeautifyThirdPartyIcons);
        applyThemeAccentTint(context, beautifyThirdPartyIcons);
        beautifyThirdPartyIcons.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        CheckBox systemResources = themeCheckBox(context, "系統資源", config.themeType1);
        CheckBox wallpaper = themeCheckBox(context, "桌布",
                config.themeType2 || config.themeType6);
        CheckBox icons = themeCheckBox(context, "圖標",
                config.themeType17 || config.themeType23);
        CheckBox lockscreen = themeCheckBox(context, "鎖屏", config.themeLockscreen);
        panel.addView(fullTheme);
        panel.addView(beautifyThirdPartyIcons);
        panel.addView(themeSelectorRow(context, systemResources, icons));
        panel.addView(themeSelectorRow(context, wallpaper, lockscreen));

        CheckBox[] detailControls = {systemResources, icons, wallpaper, lockscreen};
        updateThemeDetailControls(detailControls, !fullTheme.isChecked());
        updateThirdPartyIconControl(beautifyThirdPartyIcons,
                fullTheme.isChecked() || icons.isChecked());
        CompoundButton.OnCheckedChangeListener detailListener = (button, checked) ->
                saveThemeSelection(context, fullTheme, beautifyThirdPartyIcons,
                        systemResources, wallpaper, icons, lockscreen);
        systemResources.setOnCheckedChangeListener(detailListener);
        wallpaper.setOnCheckedChangeListener(detailListener);
        icons.setOnCheckedChangeListener((button, checked) -> {
            updateThirdPartyIconControl(beautifyThirdPartyIcons,
                    fullTheme.isChecked() || checked);
            saveThemeSelection(context, fullTheme, beautifyThirdPartyIcons,
                    systemResources, wallpaper, icons, lockscreen);
        });
        lockscreen.setOnCheckedChangeListener(detailListener);
        beautifyThirdPartyIcons.setOnCheckedChangeListener((button, checked) ->
                saveThemeSelection(context, fullTheme, beautifyThirdPartyIcons,
                        systemResources, wallpaper, icons, lockscreen));
        fullTheme.setOnCheckedChangeListener((button, checked) -> {
            updateThemeDetailControls(detailControls, !checked);
            updateThirdPartyIconControl(beautifyThirdPartyIcons, checked || icons.isChecked());
            saveThemeSelection(context, fullTheme, beautifyThirdPartyIcons,
                    systemResources, wallpaper, icons, lockscreen);
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 12));
        itemView.addView(panel, params);
        log("Beautify theme selector injected");
    }

    private LinearLayout themeSelectorRow(Context context, CheckBox first, CheckBox second) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(first);
        if (second != null) {
            row.addView(second);
        } else {
            View spacer = new View(context);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
            row.addView(spacer);
        }
        return row;
    }

    private CheckBox themeCheckBox(Context context, String label, boolean checked) {
        CheckBox checkBox = new CheckBox(context);
        checkBox.setText(label);
        checkBox.setTextColor(Color.WHITE);
        checkBox.setTextSize(14);
        checkBox.setChecked(checked);
        checkBox.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return checkBox;
    }

    private void updateThemeDetailControls(CheckBox[] controls, boolean enabled) {
        for (CheckBox control : controls) {
            control.setEnabled(enabled);
            control.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    private void updateThirdPartyIconControl(Switch control, boolean enabled) {
        control.setEnabled(enabled);
        control.setAlpha(enabled ? 1f : 0.4f);
    }

    private void applyThemeAccentTint(Context context, Switch control) {
        int colorId = context.getResources().getIdentifier(
                "mfvc_primary_main_color", "color", BEAUTIFY);
        Integer accent = colorId == 0 ? null : context.getColor(colorId);
        if (accent == null) {
            accent = resolveThemeColor(context, android.R.attr.colorAccent);
        }
        if (accent == null) {
            return;
        }
        int[][] states = {
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        ColorStateList oldThumb = control.getThumbTintList();
        ColorStateList oldTrack = control.getTrackTintList();
        control.setThumbTintList(new ColorStateList(states, new int[]{
                tintColorForState(oldThumb, states[0], 0xFF666666),
                accent,
                tintColorForState(oldThumb, states[2], 0xFFBDBDBD)
        }));
        control.setTrackTintList(new ColorStateList(states, new int[]{
                tintColorForState(oldTrack, states[0], 0x33666666),
                withAlpha(accent, 0x66),
                tintColorForState(oldTrack, states[2], 0x4DFFFFFF)
        }));
    }

    private Integer resolveThemeColor(Context context, int attrId) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attrId, value, true)) {
            return null;
        }
        if (value.resourceId != 0) {
            return context.getColor(value.resourceId);
        }
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return null;
    }

    private int tintColorForState(ColorStateList colors, int[] state, int fallback) {
        return colors == null ? fallback : colors.getColorForState(state, colors.getDefaultColor());
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private void applySystemResourcesPreservingIcons(Context context, Object manager, Object themeBean,
                                                      boolean restoreCurrentIcons) {
        ClassLoader classLoader = manager.getClass().getClassLoader();
        Object currentIconBean = null;
        String currentIconConfig = "";
        if (restoreCurrentIcons) {
            try {
                Class<?> utilities = Xp.findClass(
                        "com.zte.beautify.view.common.tools.Utilities", classLoader);
                String currentIconTheme = (String) Xp.callStaticMethod(
                        utilities, "getIconFlavorChooseIconId");
                currentIconConfig = (String) Xp.callStaticMethod(
                        utilities, "getCurrentIconConfigId");
                Class<?> resourceManager = Xp.findClass(
                        "com.zte.beautify.model.ResourceManager", classLoader);
                Object resources = Xp.callStaticMethod(resourceManager, "getInstance");
                currentIconBean = Xp.callMethod(
                        resources, "getThemeBeanInfoById", currentIconTheme);
            } catch (Throwable throwable) {
                log("Unable to capture current icons before system-resource apply: " + throwable);
            }
        }

        Object iconBean = currentIconBean;
        String iconConfig = currentIconConfig == null ? "" : currentIconConfig;
        new Thread(() -> {
            try {
                Xp.callMethod(manager, "setTheme", themeBean, true, false);
                if (restoreCurrentIcons && iconBean != null) {
                    Xp.callMethod(
                            manager, "setIconChangeSelfDefine", iconBean, iconConfig, false);
                    log("Beautify restored previous icons after system-resource apply");
                }
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "已套用系統資源", Toast.LENGTH_SHORT).show());
            } catch (Throwable throwable) {
                log("Beautify system-resource apply failed: " + throwable);
            }
        }, "RedMagicX-ThemeResources").start();
    }

    private void applyThemeLockscreen(Context context, Object manager, Object themeBean) {
        String type;
        try {
            type = (String) Xp.callMethod(themeBean, "getLockScreenWallpaperType");
        } catch (Throwable throwable) {
            log("Unable to read theme lockscreen type: " + throwable);
            return;
        }
        log("Beautify theme lockscreen type=" + type);
        if ("3".equals(type)) {
            Xp.callMethod(manager, "setDynamicLockerAsync", context, themeBean);
            log("Beautify dynamic theme lockscreen apply requested");
            return;
        }
        applyStaticThemeLockscreen(context, themeBean);
    }

    private void applyStaticThemeLockscreen(Context context, Object themeBean) {
        new Thread(() -> {
            try {
                byte[] wallpaper = readThemeLockscreen(themeBean);
                if (wallpaper == null) {
                    log("Beautify theme has no supported lockscreen wallpaper");
                    return;
                }
                try (InputStream input = new ByteArrayInputStream(wallpaper)) {
                    WallpaperManager.getInstance(context).setStream(
                            input, null, false, WallpaperManager.FLAG_LOCK);
                }
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "已套用鎖屏", Toast.LENGTH_SHORT).show());
            } catch (Throwable throwable) {
                log("Beautify static theme lockscreen apply failed: " + throwable);
            }
        }, "RedMagicX-ThemeLockscreen").start();
    }

    private byte[] readThemeLockscreen(Object themeBean) throws Exception {
        ClassLoader classLoader = themeBean.getClass().getClassLoader();
        Class<?> resourceManagerClass = Xp.findClass(
                "com.zte.beautify.model.ResourceManager", classLoader);
        Object resourceManager = Xp.callStaticMethod(resourceManagerClass, "getInstance");
        InputStream themeStream = (InputStream) Xp.callMethod(
                resourceManager, "getThemeZipInputStream", themeBean);
        if (themeStream == null) {
            return null;
        }

        byte[] best = null;
        int bestRank = Integer.MAX_VALUE;
        try (ZipInputStream zip = new ZipInputStream(themeStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                int rank = lockscreenEntryRank(entry.getName());
                if (!entry.isDirectory() && rank < bestRank) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    best = output.toByteArray();
                    bestRank = rank;
                    log("Beautify selected lockscreen entry=" + entry.getName());
                    if (rank == 0) {
                        break;
                    }
                }
                zip.closeEntry();
            }
        }
        return best;
    }

    private int lockscreenEntryRank(String name) {
        String normalized = name.startsWith("/") ? name.substring(1) : name;
        if ("lockscreen/wallpaper1.jpg".equalsIgnoreCase(normalized)) {
            return 0;
        }
        if ("lockscreen/wallpaper1.png".equalsIgnoreCase(normalized)) {
            return 1;
        }
        if ("lockscreen/wallpaper169.jpg".equalsIgnoreCase(normalized)) {
            return 2;
        }
        if ("lockscreen/wallpaper169.png".equalsIgnoreCase(normalized)) {
            return 3;
        }
        return Integer.MAX_VALUE;
    }

    private void saveThemeSelection(Context context, Switch fullTheme,
                                    Switch beautifyThirdPartyIcons,
                                    CheckBox systemResources, CheckBox wallpaper,
                                    CheckBox icons, CheckBox lockscreen) {
        Bundle values = new Bundle();
        values.putBoolean(Config.KEY_THEME_FULL, fullTheme.isChecked());
        values.putBoolean(Config.KEY_THEME_BEAUTIFY_THIRD_PARTY_ICONS,
                beautifyThirdPartyIcons.isChecked());
        values.putBoolean(Config.KEY_THEME_LOCKSCREEN, lockscreen.isChecked());
        values.putBoolean(Config.KEY_THEME_TYPE_1, systemResources.isChecked());
        values.putBoolean(Config.KEY_THEME_TYPE_2, wallpaper.isChecked());
        values.putBoolean(Config.KEY_THEME_TYPE_4, false);
        values.putBoolean(Config.KEY_THEME_TYPE_6, wallpaper.isChecked());
        values.putBoolean(Config.KEY_THEME_TYPE_16, false);
        values.putBoolean(Config.KEY_THEME_TYPE_17, icons.isChecked());
        values.putBoolean(Config.KEY_THEME_TYPE_23, icons.isChecked());
        try {
            Bundle result = context.getContentResolver().call(
                    Uri.parse("content://" + ThemeConfigProvider.AUTHORITY),
                    ThemeConfigProvider.METHOD_SET_SELECTION, null, values);
            if (result == null || !result.getBoolean("saved", false)) {
                log("Theme selection provider did not confirm save");
            }
        } catch (Throwable throwable) {
            log("Theme selection save failed: " + throwable);
        }
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private void hookBeautifyAdapterResourceTypes(Target lpparam) {
        hookBeautifyAdapterTrial(lpparam);
        Class<?> serviceClass = findClassIfExists(
                "com.zte.beautifyadapter.ThemeApplyService", lpparam.classLoader);
        if (serviceClass == null) {
            log("Beautify adapter ThemeApplyService not found");
            return;
        }
        Xp.hookAllMethods(serviceClass, "x", new MethodHook() {
            @Override
            protected void before(Param param) {
                Config.Snapshot config = Config.loadForHook();
                if (!config.themeSelectEnabled || param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof Integer)) {
                    return;
                }
                int type = (Integer) param.args[0];
                if (!config.allowsThemeType(type)) {
                    param.setResult(1);
                    log("Beautify applyResource blocked type=" + type);
                }
            }
        });
        log("Beautify adapter resource type hook installed");
    }

    private void hookSystemUiAssistantBroadcast(Target lpparam) {
        hookBroadcastMethods(ContextWrapper.class, "ContextWrapper");
        Class<?> contextImpl = findClassIfExists("android.app.ContextImpl", lpparam.classLoader);
        if (contextImpl != null) {
            hookBroadcastMethods(contextImpl, "ContextImpl");
        } else {
            log("ContextImpl not found; assistant broadcast hook partially installed");
        }
    }

    private void hookBroadcastMethods(Class<?> contextClass, String label) {
        MethodHook hook = new MethodHook() {
            @Override
            protected void before(Param param) {
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
            Xp.hookAllMethods(contextClass, "sendBroadcast", hook);
            Xp.hookAllMethods(contextClass, "sendBroadcastAsUser", hook);
            Xp.hookAllMethods(contextClass, "sendOrderedBroadcast", hook);
            Xp.hookAllMethods(contextClass, "sendOrderedBroadcastAsUser", hook);
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

    private void hookSystemUiStartAssist(Target lpparam) {
        Class<?> commandQueue = findClassIfExists("com.android.systemui.statusbar.CommandQueue", lpparam.classLoader);
        if (commandQueue == null) {
            log("CommandQueue not found; assistant redirect skipped");
            return;
        }
        try {
            findAndHookMethod(commandQueue, "startAssist", android.os.Bundle.class, new MethodHook() {
                @Override
                protected void before(Param param) {
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
                Object value = Xp.getObjectField(object, fieldName);
                if (value instanceof Context) {
                    return (Context) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void hookAudioService(Target lpparam) {
        try {
            Class<?> audioService = Xp.findClass("com.android.server.audio.AudioService", lpparam.classLoader);
            Xp.hookAllMethods(audioService, "adjustStreamVolume", new VolumeAdjustHook());
            Xp.hookAllMethods(audioService, "adjustSuggestedStreamVolume", new VolumeAdjustHook());
            log("AudioService volume step hooks installed");
            hookVolumeChangedBroadcastDelay(lpparam);
        } catch (Throwable throwable) {
            log("AudioService hook failed: " + throwable);
        }
    }

private final java.util.concurrent.atomic.AtomicBoolean volumeBroadcastUndelayLogged =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * BroadcastProcessQueue.updateRunnableAt() gives non-foreground, non-cached receivers
     * (e.g. Ultra Volume at procstate 5) DELAY_NORMAL_MILLIS (500 ms, reason 2 = NORMAL).
     * AudioService sends VOLUME_CHANGED_ACTION with deferralPolicy UNTIL_ACTIVE, so
     * FLAG_RECEIVER_FOREGROUND alone does not help (it is counted in mCountForegroundDeferred).
     * Only for VOLUME_CHANGED_ACTION and only when the reason is NORMAL, make it runnable at
     * enqueue time. Cached/frozen deferral, delivery-group (most recent) policy and all other
     * broadcasts are untouched.
     */
    private void hookVolumeChangedBroadcastDelay(Target lpparam) {
        if (!Config.loadVolumeBroadcastUndelayForHook()) {
            log("VOLUME_CHANGED_ACTION broadcast undelay disabled by pref; hook not installed");
            return;
        }
        try {
            Class<?> queueClass = Xp.findClass("com.android.server.am.BroadcastProcessQueue", lpparam.classLoader);
            Xp.hookAllMethods(queueClass, "updateRunnableAt", new MethodHook() {
                @Override
                protected void after(Param param) {
                    try {
                        Object queue = param.thisObject;
                        if (Xp.getIntField(queue, "mRunnableAtReason") != 2) {
                            return;
                        }
                        Object args = Xp.callMethod(queue, "peekNextBroadcast");
                        if (args == null) {
                            return;
                        }
                        Object record = Xp.getObjectField(args, "arg1");
                        Object intent = record == null ? null : Xp.getObjectField(record, "intent");
                        if (!(intent instanceof Intent)
                                || !"android.media.VOLUME_CHANGED_ACTION".equals(((Intent) intent).getAction())) {
                            return;
                        }
                        long enqueueTime = Xp.getLongField(record, "enqueueTime");
                        Xp.setLongField(queue, "mRunnableAt", enqueueTime);
                        if (volumeBroadcastUndelayLogged.compareAndSet(false, true)) {
                            log("VOLUME_CHANGED_ACTION normal delay removed (first applied) for "
                                    + Xp.getObjectField(queue, "processName"));
                        }
                    } catch (Throwable throwable) {
                        // leave original runnableAt untouched
                    }
                }
            });
            log("VOLUME_CHANGED_ACTION broadcast undelay hook installed");
        } catch (Throwable throwable) {
            log("VOLUME_CHANGED_ACTION broadcast undelay hook failed: " + throwable);
        }
    }

    private void hookRecentTasksFilter(Target lpparam) {
        hookRecentTasksMethod(lpparam, "com.android.server.wm.ActivityTaskManagerService", "getRecentTasks");
        hookRecentTasksMethod(lpparam, "com.android.server.wm.RecentTasks", "getRecentTasks");
        hookRecentTasksMethod(lpparam, "com.android.server.wm.RecentTasks", "getRecentTasksImpl");
    }

    private void hookRecentUiFilter(Target lpparam) {
        hookLauncherRecentTasksList(lpparam);
        String[] classNames = new String[]{
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

    private void hookShellRecentTasksProvider(Target lpparam) {
        Class<?> clazz = findClassIfExists("com.android.wm.shell.recents.RecentTasksController", lpparam.classLoader);
        if (clazz == null) {
            log("Shell RecentTasksController not found; provider filter skipped");
            return;
        }
        try {
            Xp.hookAllMethods(clazz, "getRecentTasks", new MethodHook() {
                @Override
                protected void after(Param param) {
                    int removed = filterRecentMethodResult(param);
                    if (removed > 0) {
                        Config.Snapshot config = Config.loadForHook();
                        log("Shell RecentTasksController#getRecentTasks filtered package="
                                + config.launcherPackage + " count=" + removed);
                    }
                }
            });
            log("Shell RecentTasksController#getRecentTasks filter installed");
        } catch (Throwable throwable) {
            log("Shell RecentTasksController#getRecentTasks hook failed: " + throwable);
        }
        hookShellRecentTasksControllerState(clazz);
        hookShellRecentTaskListenerCallbacks(lpparam);
    }

    private void hookShellRecentTasksControllerState(Class<?> clazz) {
        try {
            Xp.hookAllMethods(clazz, "onVisibleTasksChanged", new MethodHook() {
                @Override
                protected void before(Param param) {
                    int removed = filterRecentListArg(param, 0);
                    if (removed > 0) {
                        Config.Snapshot config = Config.loadForHook();
                        log("Shell RecentTasksController#onVisibleTasksChanged filtered package="
                                + config.launcherPackage + " count=" + removed);
                    }
                }
            });
            Xp.hookAllMethods(clazz, "generateList", new MethodHook() {
                @Override
                protected void before(Param param) {
                    int removed = filterRecentListArg(param, 0);
                    if (removed > 0) {
                        Config.Snapshot config = Config.loadForHook();
                        log("Shell RecentTasksController#generateList filtered package="
                                + config.launcherPackage + " count=" + removed);
                    }
                }
            });
            String[] methods = new String[]{
                    "notifyRunningTaskAppeared",
                    "notifyRunningTaskChanged",
                    "notifyTaskInfoChanged",
                    "notifyTaskMovedToFront"
            };
            for (String method : methods) {
                Xp.hookAllMethods(clazz, method, new MethodHook() {
                    @Override
                    protected void before(Param param) {
                        Config.Snapshot config = Config.loadForHook();
                        if (!config.launcherOverrideEnabled || config.launcherPackage.isEmpty()
                                || param.args == null || param.args.length == 0) {
                            return;
                        }
                        if (recentTaskBelongsToPackage(param.args[0], config.launcherPackage)) {
                            log("Shell RecentTasksController#" + method + " suppressed package="
                                    + config.launcherPackage);
                            param.setResult(null);
                        }
                    }
                });
            }
            log("Shell RecentTasksController state filters installed");
        } catch (Throwable throwable) {
            log("Shell RecentTasksController state hook failed: " + throwable);
        }
    }

    private int filterRecentListArg(Param param, int index) {
        Config.Snapshot config = Config.loadForHook();
        if (!config.launcherOverrideEnabled || config.launcherPackage.isEmpty()
                || param.args == null || param.args.length <= index || !(param.args[index] instanceof List)) {
            return 0;
        }
        List<?> original = (List<?>) param.args[index];
        List<?> filtered = filteredRecentList(original, config.launcherPackage);
        if (filtered != original) {
            param.args[index] = filtered;
        }
        return Math.max(0, original.size() - filtered.size());
    }

    private void hookShellRecentTaskListenerCallbacks(Target lpparam) {
        Class<?> clazz = findClassIfExists("com.android.wm.shell.recents.IRecentTasksListener$Stub$Proxy", lpparam.classLoader);
        if (clazz == null) {
            log("Shell IRecentTasksListener proxy not found; listener filter skipped");
            return;
        }
        try {
            Xp.hookAllMethods(clazz, "onVisibleTasksChanged", new MethodHook() {
                @Override
                protected void before(Param param) {
                    Config.Snapshot config = Config.loadForHook();
                    if (!config.launcherOverrideEnabled || config.launcherPackage.isEmpty()
                            || param.args == null || param.args.length == 0 || param.args[0] == null) {
                        return;
                    }
                    Object original = param.args[0];
                    Object filtered = filteredRecentArray(original, config.launcherPackage);
                    if (filtered != original) {
                        param.args[0] = filtered;
                        log("Shell IRecentTasksListener#onVisibleTasksChanged filtered package="
                                + config.launcherPackage
                                + " count=" + (Array.getLength(original) - Array.getLength(filtered)));
                    }
                }
            });
            Xp.hookAllMethods(clazz, "onTaskMovedToFront", new MethodHook() {
                @Override
                protected void before(Param param) {
                    Config.Snapshot config = Config.loadForHook();
                    if (!config.launcherOverrideEnabled || config.launcherPackage.isEmpty()
                            || param.args == null || param.args.length == 0) {
                        return;
                    }
                    if (recentTaskBelongsToPackage(param.args[0], config.launcherPackage)) {
                        log("Shell IRecentTasksListener#onTaskMovedToFront suppressed package="
                                + config.launcherPackage);
                        param.setResult(null);
                    }
                }
            });
            log("Shell IRecentTasksListener callback filters installed");
        } catch (Throwable throwable) {
            log("Shell IRecentTasksListener callback hook failed: " + throwable);
        }
    }

    private void hookLauncherSystemUiProxyRecentTasks(Target lpparam) {
        Class<?> clazz = findClassIfExists("com.android.quickstep.SystemUiProxy", lpparam.classLoader);
        if (clazz == null) {
            log("Launcher_MFV SystemUiProxy not found; launcher recents filter skipped");
            return;
        }
        try {
            Xp.hookAllMethods(clazz, "getRecentTasks", new MethodHook() {
                @Override
                protected void after(Param param) {
                    int removed = filterRecentMethodResult(param);
                    if (removed > 0) {
                        Config.Snapshot config = Config.loadForHook();
                        log("Launcher_MFV SystemUiProxy#getRecentTasks filtered package="
                                + config.launcherPackage + " count=" + removed);
                    }
                }
            });
            log("Launcher_MFV SystemUiProxy#getRecentTasks filter installed");
        } catch (Throwable throwable) {
            log("Launcher_MFV SystemUiProxy#getRecentTasks hook failed: " + throwable);
        }
    }

    private void hookLauncherRecentTasksList(Target lpparam) {
        Class<?> clazz = findClassIfExists("com.android.quickstep.RecentTasksList", lpparam.classLoader);
        if (clazz == null) {
            return;
        }
        hookLauncherRecentConsumerMethod(clazz, "getTasks");
        hookLauncherRecentConsumerMethod(clazz, "getTaskKeys");
        try {
            Xp.hookAllMethods(clazz, "loadTasksInBackground", new MethodHook() {
                @Override
                protected void after(Param param) {
                    int removed = filterRecentMethodResult(param);
                    if (removed > 0) {
                        Config.Snapshot config = Config.loadForHook();
                        log("Launcher_MFV loadTasksInBackground filtered package="
                                + config.launcherPackage + " count=" + removed);
                    }
                }
            });
            log("Launcher_MFV RecentTasksList loadTasksInBackground filter installed");
        } catch (Throwable throwable) {
            log("Launcher_MFV loadTasksInBackground hook failed: " + throwable);
        }
    }

    private void hookLauncherRecentTasksData(Target lpparam) {
        Class<?> clazz = findClassIfExists("com.android.quickstep.RecentTasksList", lpparam.classLoader);
        if (clazz == null) {
            log("Launcher_MFV RecentTasksList not found; data filter skipped");
        } else {
            hookLauncherRecentConsumerMethod(clazz, "getTasks");
            hookLauncherRecentConsumerMethod(clazz, "getTaskKeys");
            try {
                Xp.hookAllMethods(clazz, "loadTasksInBackground", new MethodHook() {
                    @Override
                    protected void after(Param param) {
                        Config.Snapshot config = Config.loadForHook();
                        if (!config.launcherOverrideEnabled || config.launcherPackage.isEmpty()) {
                            return;
                        }
                        int removed = filterRecentResult(param.getResult(), config.launcherPackage);
                        if (removed > 0) {
                            log("Launcher_MFV loadTasksInBackground filtered in-place package="
                                    + config.launcherPackage + " count=" + removed);
                        }
                    }
                });
                log("Launcher_MFV RecentTasksList data filters installed");
            } catch (Throwable throwable) {
                log("Launcher_MFV RecentTasksList data hook failed: " + throwable);
            }
        }
        hookLauncherGestureCurrentTask(lpparam);
    }

    private void hookLauncherGestureCurrentTask(Target lpparam) {
        Class<?> clazz = findClassIfExists("com.android.quickstep.views.RecentsView", lpparam.classLoader);
        if (clazz == null) {
            log("Launcher_MFV RecentsView not found; gesture current task filter skipped");
            return;
        }
        try {
            Xp.hookAllMethods(clazz, "onGestureAnimationStart", new MethodHook() {
                @Override
                protected void before(Param param) {
                    Config.Snapshot config = Config.loadForHook();
                    if (!config.launcherOverrideEnabled || config.launcherPackage.isEmpty()
                            || param.args == null || param.args.length == 0) {
                        return;
                    }
                    Object groupedTaskInfo = param.args[0];
                    if (!recentTaskBelongsToPackage(groupedTaskInfo, config.launcherPackage)) {
                        return;
                    }
                    param.args[0] = null;
                    log("Launcher_MFV onGestureAnimationStart skipped launcher running task package="
                            + config.launcherPackage);
                }
            });
            log("Launcher_MFV gesture current task filter installed");
        } catch (Throwable throwable) {
            log("Launcher_MFV gesture current task hook failed: " + throwable);
        }
    }

    private void hookLauncherRecentConsumerMethod(Class<?> clazz, String methodName) {
        try {
            Xp.hookAllMethods(clazz, methodName, new MethodHook() {
                @Override
                protected void before(Param param) {
                    wrapRecentConsumerArgs(param);
                }
            });
            log("Launcher_MFV RecentTasksList#" + methodName + " consumer filter installed");
        } catch (Throwable throwable) {
            log("Launcher_MFV RecentTasksList#" + methodName + " hook failed: " + throwable);
        }
    }

    private void hookRecentUiClass(Target lpparam, String className) {
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
                Xp.hookMethod(method, new MethodHook() {
                    @Override
                    protected void before(Param param) {
                        filterRecentArgs(param);
                    }

                    @Override
                    protected void after(Param param) {
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

    private void hookRecentTasksMethod(Target lpparam, String className, String methodName) {
        Class<?> clazz = findClassIfExists(className, lpparam.classLoader);
        if (clazz == null) {
            return;
        }
        try {
            Xp.hookAllMethods(clazz, methodName, new MethodHook() {
                @Override
                protected void after(Param param) {
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

    private void filterRecentArgs(Param param) {
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

    private void wrapRecentConsumerArgs(Param param) {
        Config.Snapshot config = Config.loadForHook();
        if (!config.launcherOverrideEnabled || config.launcherPackage.isEmpty() || param.args == null) {
            return;
        }
        for (int i = 0; i < param.args.length; i++) {
            Object arg = param.args[i];
            if (arg != null && implementsInterface(arg.getClass(), "java.util.function.Consumer")) {
                param.args[i] = filteredRecentConsumer(arg, config.launcherPackage);
            }
        }
    }

    private Object filteredRecentConsumer(Object originalConsumer, String packageName) {
        ClassLoader classLoader = originalConsumer.getClass().getClassLoader();
        Class<?> consumerClass;
        try {
            consumerClass = Class.forName("java.util.function.Consumer", false, classLoader);
        } catch (Throwable throwable) {
            consumerClass = java.util.function.Consumer.class;
        }
        InvocationHandler handler = (proxy, method, args) -> {
            if ("accept".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof List) {
                List<?> original = (List<?>) args[0];
                List<?> filtered = filteredRecentList(original, packageName);
                if (filtered != original) {
                    log("Launcher_MFV consumer filtered package="
                            + packageName + " count=" + (original.size() - filtered.size()));
                    args[0] = filtered;
                }
            }
            return method.invoke(originalConsumer, args);
        };
        return Proxy.newProxyInstance(classLoader, new Class[]{consumerClass}, handler);
    }

    private boolean implementsInterface(Class<?> clazz, String interfaceName) {
        while (clazz != null) {
            for (Class<?> iface : clazz.getInterfaces()) {
                if (interfaceName.equals(iface.getName()) || implementsInterface(iface, interfaceName)) {
                    return true;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    private int filterRecentMethodResult(Param param) {
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
            Object list = Xp.callMethod(result, "getList");
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

    private Object filteredRecentArray(Object tasks, String packageName) {
        Class<?> arrayClass = tasks.getClass();
        if (!arrayClass.isArray()) {
            return tasks;
        }
        int length = Array.getLength(tasks);
        ArrayList<Object> filtered = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            Object task = Array.get(tasks, i);
            if (!recentTaskBelongsToPackage(task, packageName)) {
                filtered.add(task);
            }
        }
        if (filtered.size() == length) {
            return tasks;
        }
        Object result = Array.newInstance(arrayClass.getComponentType(), filtered.size());
        for (int i = 0; i < filtered.size(); i++) {
            Array.set(result, i, filtered.get(i));
        }
        return result;
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
        try {
            Object contains = Xp.callMethod(task, "containsPackage", packageName);
            if (Boolean.TRUE.equals(contains)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object tasks = Xp.callMethod(task, "getTasks");
            if (tasks instanceof List) {
                for (Object childTask : (List<?>) tasks) {
                    if (recentTaskBelongsToPackage(childTask, packageName)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object taskInfo1 = Xp.callMethod(task, "getTaskInfo1");
            if (recentTaskBelongsToPackage(taskInfo1, packageName)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object taskInfo2 = Xp.callMethod(task, "getTaskInfo2");
            if (recentTaskBelongsToPackage(taskInfo2, packageName)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object firstTask = Xp.callMethod(task, "getFirstTask");
            if (recentTaskBelongsToPackage(firstTask, packageName)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object tasks = Xp.callMethod(task, "E");
            if (tasks instanceof List) {
                for (Object childTask : (List<?>) tasks) {
                    if (recentTaskBelongsToPackage(childTask, packageName)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (isThirdPartyHomeTask(task)) {
            return true;
        }
        try {
            Object key = Xp.getObjectField(task, "key");
            if (isThirdPartyHomeTask(key)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        String[] componentFields = new String[]{"topActivity", "baseActivity", "origActivity", "realActivity"};
        for (String field : componentFields) {
            try {
                Object value = Xp.getObjectField(task, field);
                if (value instanceof ComponentName && packageName.equals(((ComponentName) value).getPackageName())) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        String[] intentFields = new String[]{"baseIntent", "intent"};
        for (String field : intentFields) {
            try {
                Object value = Xp.getObjectField(task, field);
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

    private boolean isThirdPartyHomeTask(Object task) {
        if (task == null) {
            return false;
        }
        String[] intentFields = new String[]{"baseIntent", "intent"};
        for (String field : intentFields) {
            try {
                Object value = Xp.getObjectField(task, field);
                if (value instanceof Intent && isThirdPartyHomeIntent((Intent) value)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (task instanceof Intent) {
            return isThirdPartyHomeIntent((Intent) task);
        }
        return false;
    }

    private boolean isThirdPartyHomeIntent(Intent intent) {
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) {
            return false;
        }
        ComponentName component = intent.getComponent();
        if (component == null) {
            return false;
        }
        String packageName = component.getPackageName();
        return packageName != null && !STOCK_LAUNCHER.equals(packageName);
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

    private final class VolumeAdjustHook extends MethodHook {
        @Override
        protected void before(Param param) {
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
                int callerUid = Binder.getCallingUid();
                if (target != current) {
                    // Run as system_server (uid 1000) so AudioService's package/uid appops check
                    // ("android" belongs to 1000), AUDIO_MEDIA_VOLUME op and HardeningEnforcer
                    // (MODIFY_AUDIO_SETTINGS_PRIVILEGED via checkCallingOrSelfPermission) all pass,
                    // regardless of which app (e.g. Ultra Volume) issued the adjust call.
                    long ident = Binder.clearCallingIdentity();
                    try {
                        audioManager.setStreamVolume(STREAM_MUSIC, target, flags);
                    } finally {
                        Binder.restoreCallingIdentity(ident);
                    }
                }
                notifyZteAppVolume(param.thisObject, flags);
                param.setResult(null);
                log("music volume " + current + " -> " + target + " step=" + config.volumeStep
                        + " callerUid=" + callerUid);
            } catch (Throwable throwable) {
                log("custom volume step failed: " + android.util.Log.getStackTraceString(throwable));
            } finally {
                applyingVolume.remove();
            }
        }
    }

    // Cleared after the first failure so ROMs without the ZTE field (e.g. HyperOS) skip it silently.
    private volatile boolean zteAppVolumeSupported = true;

    private void notifyZteAppVolume(Object audioService, int flags) {
        if (!zteAppVolumeSupported) {
            return;
        }
        try {
            Object zAudioService = Xp.getObjectField(audioService, "mZAudioService");
            Object volumeController = Xp.getObjectField(audioService, "mVolumeController");
            Object controller = Xp.callMethod(volumeController, "getController");
            if (zAudioService != null && controller != null) {
                Xp.callMethod(zAudioService, "notifyShowAppVolume", controller, flags);
            }
        } catch (Throwable throwable) {
            zteAppVolumeSupported = false;
            log("ZTE app volume notification unavailable, skipping from now on: " + throwable);
        }
    }

    private boolean shouldHandleVolume(int stream, int direction) {
        if (direction != ADJUST_RAISE && direction != ADJUST_LOWER) {
            return false;
        }
        return stream == STREAM_MUSIC || stream == AudioManager.USE_DEFAULT_STREAM_TYPE;
    }

    private int intArg(Param param, int index, int fallback) {
        if (param.args == null || index < 0 || index >= param.args.length || !(param.args[index] instanceof Integer)) {
            return fallback;
        }
        return (Integer) param.args[index];
    }

    private AudioManager audioManager(Object audioService) {
        try {
            Object context = Xp.getObjectField(audioService, "mContext");
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

    private void hookSettingsWfcGate(Target lpparam) {
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



    /**
     * Prefer AOSP/legacy volume layouts without flipping ClassAdaptor.enabled().
     * Forcing enabled()=false still left MfvVolumeDialogImpl.mfvInitDialog() running; that method
     * findViewById()s MFV-only widgets (expand / ringer RadioGroup) and NPEs after legacy inflate,
     * which crashed SystemUI. Keep Adapt.enabled() true and instead:
     *  - return the AOSP layout ids passed into getDialogLayoutResId / getRowLayoutResId
     *  - skip MFV-only init / row-panel paths that assume volume_dialog_mfv
     */
    private void hookAospVolumeDialog(Target lpparam) {
        try {
            AospVolumeHooks.install(lpparam);
        } catch (Throwable throwable) {
            log("AOSP volume dialog hooks failed: " + throwable);
        }
    }

    private void hookSystemUiAbroad(Target lpparam) {
        hookZteStringProperty(lpparam, "ro.vendor.mifavor.custom", "abroad", true);
        hookZteStringProperty(lpparam, "ro.mifavor.custom", "abroad", true);
        hookAndroidStringProperty(lpparam, "ro.vendor.mifavor.custom", "abroad", true);
        hookAndroidStringProperty(lpparam, "ro.mifavor.custom", "abroad", true);
        hookFlavorIsAbroad(lpparam, true);
    }

    private void hookSystemUiVariantGenBd(Target lpparam) {
        hookZteStringProperty(lpparam, "persist.custom.variant.id", "GEN_BD", false);
        hookAndroidStringProperty(lpparam, "persist.custom.variant.id", "GEN_BD", false);
    }

    private void hookSystemUiBdArray(Target lpparam) {
        hookSystemUiVariantGenBd(lpparam);
        Class<?> imsClass = findClassIfExists("com.zte.feature.signal.ImsUpdateFeature", lpparam.classLoader);
        if (imsClass == null) {
            log("ImsUpdateFeature not found; array hook skipped");
            return;
        }
        try {
            Xp.findAndHookMethod(imsClass, "getSingleCardImsIconArrayResId", new MethodHook() {
                @Override
                protected void after(Param param) {
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
            Class<?> rArray = Xp.findClass("com.android.systemui.R$array", classLoader);
            return Xp.getStaticIntField(rArray, name);
        } catch (Throwable throwable) {
            log("array id not found: " + name + " " + throwable);
            return 0;
        }
    }

    private void hookFlavorIsAbroad(Target lpparam, boolean imsOnly) {
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
            Xp.findAndHookMethod(clazz, methodName, new MethodHook() {
                @Override
                protected void before(Param param) {
                    if (!imsOnly || isImsIconStack()) {
                        param.setResult(result);
                    }
                }
            });
        } catch (Throwable ignored) {
            // Method name varies between framework builds. Missing method is harmless.
        }
    }

    private void hookZteStringProperty(Target lpparam, String key, String value, boolean imsOnly) {
        Class<?> propertyClass = findZtePropertyClass(lpparam.classLoader);
        if (propertyClass == null) {
            log("SystemPropertiesZTE not found for " + key);
            return;
        }
        hookStringGet(propertyClass, key, value, imsOnly);
    }

    private void hookZteBooleanProperty(Target lpparam, String key, boolean value) {
        Class<?> propertyClass = findZtePropertyClass(lpparam.classLoader);
        if (propertyClass == null) {
            log("SystemPropertiesZTE not found for " + key);
            return;
        }
        try {
            findAndHookMethod(propertyClass, "getBoolean", String.class, boolean.class, new MethodHook() {
                @Override
                protected void before(Param param) {
                    if (key.equals(param.args[0])) {
                        param.setResult(value);
                    }
                }
            });
        } catch (Throwable throwable) {
            log("getBoolean hook failed for " + key + ": " + throwable);
        }
    }

    private void hookAndroidStringProperty(Target lpparam, String key, String value, boolean imsOnly) {
        Class<?> propertyClass = findClassIfExists("android.os.SystemProperties", lpparam.classLoader);
        if (propertyClass == null) {
            log("android.os.SystemProperties not found for " + key);
            return;
        }
        hookStringGet(propertyClass, key, value, imsOnly);
    }

    private void hookAndroidBooleanProperty(Target lpparam, String key, boolean value) {
        Class<?> propertyClass = findClassIfExists("android.os.SystemProperties", lpparam.classLoader);
        if (propertyClass == null) {
            log("android.os.SystemProperties not found for " + key);
            return;
        }
        try {
            findAndHookMethod(propertyClass, "getBoolean", String.class, boolean.class, new MethodHook() {
                @Override
                protected void before(Param param) {
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
            findAndHookMethod(propertyClass, "get", String.class, new MethodHook() {
                @Override
                protected void before(Param param) {
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
            findAndHookMethod(propertyClass, "get", String.class, String.class, new MethodHook() {
                @Override
                protected void before(Param param) {
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
        Xp.log(message);
    }
}

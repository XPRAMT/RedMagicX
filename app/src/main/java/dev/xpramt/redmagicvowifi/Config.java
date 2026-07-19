package dev.xpramt.redmagicvowifi;

import android.content.Context;
import android.content.SharedPreferences;

import de.robv.android.xposed.XSharedPreferences;

final class Config {
    static final String PACKAGE_NAME = "dev.xpramt.redmagicvowifi";
    static final String PREFS_NAME = "module";

    static final String KEY_ENABLE_WFC_SETTINGS = "enable_wfc_settings";
    static final String KEY_ENABLE_STATUS_ICON = "enable_status_icon";
    static final String KEY_ICON_STYLE = "icon_style";
    static final String KEY_VOLUME_STEP_ENABLED = "volume_step_enabled";
    static final String KEY_VOLUME_STEP = "volume_step";
    static final String KEY_ASSISTANT_REDIRECT_ENABLED = "assistant_redirect_enabled";
    static final String KEY_ASSISTANT_TARGET = "assistant_target";
    static final String KEY_LAUNCHER_OVERRIDE_ENABLED = "launcher_override_enabled";
    static final String KEY_LAUNCHER_COMPONENT = "launcher_component";
    static final String KEY_LAUNCHER_PACKAGE = "launcher_package";
    static final String KEY_THEME_SELECT_ENABLED = "theme_select_enabled";
    static final String KEY_THEME_TYPE_1 = "theme_type_1";
    static final String KEY_THEME_TYPE_2 = "theme_type_2";
    static final String KEY_THEME_TYPE_4 = "theme_type_4";
    static final String KEY_THEME_TYPE_6 = "theme_type_6";
    static final String KEY_THEME_TYPE_16 = "theme_type_16";
    static final String KEY_THEME_TYPE_17 = "theme_type_17";
    static final String KEY_THEME_TYPE_23 = "theme_type_23";
    static final String KEY_AUTO_CHECK_UPDATES = "auto_check_updates";
    static final String KEY_WIRELESS_ADB_PORT = "wireless_adb_port";
    static final String KEY_WIRELESS_ADB_ENABLED = "wireless_adb_enabled";
    static final String KEY_ADB_SPOOF_ENABLED = "adb_spoof_enabled";

    static final String STYLE_DEFAULT = "default";
    static final String STYLE_GEN_BD = "gen_bd";
    static final String STYLE_ARRAY_HOOK = "array_hook";

    static final String ASSISTANT_TARGET_DEFAULT = "default_assist";
    static final String ASSISTANT_TARGET_GOOGLE_VOICE = "google_voice";
    static final String ASSISTANT_TARGET_CHATGPT = "chatgpt";
    static final String TARGET_PREFIX_ACTION = "action:";
    static final String TARGET_PREFIX_APP = "app:";
    static final String ACTION_DEFAULT_ASSIST = "default_assist";
    static final String ACTION_GOOGLE_VOICE = "google_voice";
    static final String ACTION_RECENTS = "recents";
    static final String ACTION_SCREENSHOT = "screenshot";
    static final String ACTION_FLASHLIGHT = "flashlight";

    static final int DEFAULT_VOLUME_STEP = 1;
    static final int MIN_VOLUME_STEP = 1;
    static final int MAX_VOLUME_STEP = 10;
    static final int DEFAULT_WIRELESS_ADB_PORT = 5555;

    private Config() {
    }

    static SharedPreferences appPrefs(Context context) {
        try {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        } catch (SecurityException exception) {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    static Snapshot loadForHook() {
        XSharedPreferences prefs = new XSharedPreferences(PACKAGE_NAME, PREFS_NAME);
        prefs.reload();
        return new Snapshot(
                prefs.getBoolean(KEY_ENABLE_WFC_SETTINGS, false),
                prefs.getBoolean(KEY_ENABLE_STATUS_ICON, false),
                prefs.getString(KEY_ICON_STYLE, STYLE_DEFAULT),
                prefs.getBoolean(KEY_VOLUME_STEP_ENABLED, false),
                prefs.getInt(KEY_VOLUME_STEP, DEFAULT_VOLUME_STEP),
                prefs.getBoolean(KEY_ASSISTANT_REDIRECT_ENABLED, false),
                prefs.getString(KEY_ASSISTANT_TARGET, ASSISTANT_TARGET_DEFAULT),
                prefs.getBoolean(KEY_LAUNCHER_OVERRIDE_ENABLED, false),
                prefs.getString(KEY_LAUNCHER_COMPONENT, ""),
                prefs.getString(KEY_LAUNCHER_PACKAGE, ""),
                prefs.getBoolean(KEY_THEME_SELECT_ENABLED, false),
                prefs.getBoolean(KEY_THEME_TYPE_1, true),
                prefs.getBoolean(KEY_THEME_TYPE_2, true),
                prefs.getBoolean(KEY_THEME_TYPE_4, true),
                prefs.getBoolean(KEY_THEME_TYPE_6, true),
                prefs.getBoolean(KEY_THEME_TYPE_16, true),
                prefs.getBoolean(KEY_THEME_TYPE_17, true),
                prefs.getBoolean(KEY_THEME_TYPE_23, true)
        );
    }

    static int clampVolumeStep(int value) {
        if (value < MIN_VOLUME_STEP) return MIN_VOLUME_STEP;
        if (value > MAX_VOLUME_STEP) return MAX_VOLUME_STEP;
        return value;
    }

    static final class Snapshot {
        final boolean enableWfcSettings;
        final boolean enableStatusIcon;
        final String iconStyle;
        final boolean volumeStepEnabled;
        final int volumeStep;
        final boolean assistantRedirectEnabled;
        final String assistantTarget;
        final boolean launcherOverrideEnabled;
        final String launcherComponent;
        final String launcherPackage;
        final boolean themeSelectEnabled;
        final boolean themeType1;
        final boolean themeType2;
        final boolean themeType4;
        final boolean themeType6;
        final boolean themeType16;
        final boolean themeType17;
        final boolean themeType23;

        Snapshot(boolean enableWfcSettings, boolean enableStatusIcon, String iconStyle, boolean volumeStepEnabled,
                 int volumeStep, boolean assistantRedirectEnabled, String assistantTarget,
                 boolean launcherOverrideEnabled, String launcherComponent, String launcherPackage,
                 boolean themeSelectEnabled, boolean themeType1, boolean themeType2,
                 boolean themeType4, boolean themeType6, boolean themeType16,
                 boolean themeType17, boolean themeType23) {
            this.enableWfcSettings = enableWfcSettings;
            this.enableStatusIcon = enableStatusIcon;
            this.iconStyle = iconStyle == null ? STYLE_DEFAULT : iconStyle;
            this.volumeStepEnabled = volumeStepEnabled;
            this.volumeStep = clampVolumeStep(volumeStep);
            this.assistantRedirectEnabled = assistantRedirectEnabled;
            this.assistantTarget = assistantTarget == null ? ASSISTANT_TARGET_DEFAULT : assistantTarget;
            this.launcherOverrideEnabled = launcherOverrideEnabled;
            this.launcherComponent = launcherComponent == null ? "" : launcherComponent;
            this.launcherPackage = launcherPackage == null ? "" : launcherPackage;
            this.themeSelectEnabled = themeSelectEnabled;
            this.themeType1 = themeType1;
            this.themeType2 = themeType2;
            this.themeType4 = themeType4;
            this.themeType6 = themeType6;
            this.themeType16 = themeType16;
            this.themeType17 = themeType17;
            this.themeType23 = themeType23;
        }

        boolean allowsThemeType(int type) {
            switch (type) {
                case 1: return themeType1;
                case 2: return themeType2;
                case 4: return themeType4;
                case 6: return themeType6;
                case 16: return themeType16;
                case 17: return themeType17;
                case 23: return themeType23;
                default: return true;
            }
        }
    }
}

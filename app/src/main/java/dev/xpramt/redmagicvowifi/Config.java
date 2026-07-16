package dev.xpramt.redmagicvowifi;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

final class Config {
    static final String PACKAGE_NAME = "dev.xpramt.redmagicvowifi";
    static final String PREFS_NAME = "module";
    static final String GLOBAL_PREFIX = "redmagic_vowifi_";

    static final String KEY_ENABLE_WFC_SETTINGS = "enable_wfc_settings";
    static final String KEY_ENABLE_STATUS_ICON = "enable_status_icon";
    static final String KEY_ICON_STYLE = "icon_style";
    static final String KEY_OPERATION_MODE = "operation_mode";

    static final String STYLE_DEFAULT = "default";
    static final String STYLE_GEN_BD = "gen_bd";
    static final String STYLE_ARRAY_HOOK = "array_hook";

    static final String MODE_LSPOSED = "lsposed";
    static final String MODE_ROOT_GLOBAL = "root_global";

    private Config() {
    }

    static SharedPreferences appPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    static Snapshot loadForHook(Context context) {
        return new Snapshot(
                getGlobal(context, KEY_OPERATION_MODE, MODE_LSPOSED),
                Boolean.parseBoolean(getGlobal(context, KEY_ENABLE_WFC_SETTINGS, "false")),
                Boolean.parseBoolean(getGlobal(context, KEY_ENABLE_STATUS_ICON, "false")),
                getGlobal(context, KEY_ICON_STYLE, STYLE_DEFAULT)
        );
    }

    private static String getGlobal(Context context, String key, String fallback) {
        String value = Settings.Global.getString(context.getContentResolver(), GLOBAL_PREFIX + key);
        return value == null ? fallback : value;
    }

    static final class Snapshot {
        final boolean enableWfcSettings;
        final boolean enableStatusIcon;
        final String iconStyle;
        final String operationMode;

        Snapshot(String operationMode, boolean enableWfcSettings, boolean enableStatusIcon, String iconStyle) {
            this.operationMode = operationMode == null ? MODE_LSPOSED : operationMode;
            this.enableWfcSettings = enableWfcSettings;
            this.enableStatusIcon = enableStatusIcon;
            this.iconStyle = iconStyle == null ? STYLE_DEFAULT : iconStyle;
        }
    }
}

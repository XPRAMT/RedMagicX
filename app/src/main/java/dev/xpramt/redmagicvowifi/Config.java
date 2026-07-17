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
    static final String KEY_OPERATION_MODE = "operation_mode";
    static final String KEY_VOLUME_STEP_ENABLED = "volume_step_enabled";
    static final String KEY_VOLUME_STEP = "volume_step";
    static final String KEY_ASSISTANT_REDIRECT_ENABLED = "assistant_redirect_enabled";
    static final String KEY_ASSISTANT_TARGET = "assistant_target";

    static final String STYLE_DEFAULT = "default";
    static final String STYLE_GEN_BD = "gen_bd";
    static final String STYLE_ARRAY_HOOK = "array_hook";

    static final String MODE_LSPOSED = "lsposed";
    static final String MODE_ROOT_GLOBAL = "root_global";

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
                prefs.getString(KEY_OPERATION_MODE, MODE_LSPOSED),
                prefs.getBoolean(KEY_ENABLE_WFC_SETTINGS, false),
                prefs.getBoolean(KEY_ENABLE_STATUS_ICON, false),
                prefs.getString(KEY_ICON_STYLE, STYLE_DEFAULT),
                prefs.getBoolean(KEY_VOLUME_STEP_ENABLED, false),
                prefs.getInt(KEY_VOLUME_STEP, DEFAULT_VOLUME_STEP),
                prefs.getBoolean(KEY_ASSISTANT_REDIRECT_ENABLED, false),
                prefs.getString(KEY_ASSISTANT_TARGET, ASSISTANT_TARGET_DEFAULT)
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
        final String operationMode;
        final boolean volumeStepEnabled;
        final int volumeStep;
        final boolean assistantRedirectEnabled;
        final String assistantTarget;

        Snapshot(String operationMode, boolean enableWfcSettings, boolean enableStatusIcon, String iconStyle,
                 boolean volumeStepEnabled, int volumeStep, boolean assistantRedirectEnabled, String assistantTarget) {
            this.operationMode = operationMode == null ? MODE_LSPOSED : operationMode;
            this.enableWfcSettings = enableWfcSettings;
            this.enableStatusIcon = enableStatusIcon;
            this.iconStyle = iconStyle == null ? STYLE_DEFAULT : iconStyle;
            this.volumeStepEnabled = volumeStepEnabled;
            this.volumeStep = clampVolumeStep(volumeStep);
            this.assistantRedirectEnabled = assistantRedirectEnabled;
            this.assistantTarget = assistantTarget == null ? ASSISTANT_TARGET_DEFAULT : assistantTarget;
        }
    }
}

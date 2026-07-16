package dev.xpramt.redmagicvowifi;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Config.appPrefs(this);
        ensureDefaults();
        setContentView(createContent());
        makePrefsReadable();
    }

    private void ensureDefaults() {
        if (!prefs.contains(Config.KEY_ENABLE_WFC_SETTINGS)) {
            prefs.edit()
                    .putBoolean(Config.KEY_ENABLE_WFC_SETTINGS, true)
                    .putBoolean(Config.KEY_ENABLE_STATUS_ICON, true)
                    .putString(Config.KEY_ICON_STYLE, Config.STYLE_GEN_BD)
                    .putString(Config.KEY_OPERATION_MODE, Config.MODE_LSPOSED)
                    .commit();
        }
    }

    private ScrollView createContent() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scrollView.addView(root);

        TextView title = text("RedMagic VoWiFi", 22, true);
        root.addView(title);
        root.addView(text("支援 Root 全域 resetprop 與 Root + LSPosed hook 兩種模式。", 14, false));
        root.addView(modeSection());

        root.addView(sectionSwitch(
                "開啟 VoWiFi 設定",
                "作用進程：com.android.settings\n等效參數：ro.vendor.feature.zte_feature_need_wfc_for_domestic=true\n用途：讓 Settings 的 ZTE 國內 WFC gate 通過，顯示 Wi-Fi Calling/VoWiFi 開關。仍需要 Pixel IMS 或 carrier config 啟用 WFC。",
                Config.KEY_ENABLE_WFC_SETTINGS
        ));

        root.addView(sectionSwitch(
                "開啟狀態列 VoWiFi 圖標",
                "作用進程：com.android.systemui\n等效參數：ro.vendor.mifavor.custom=abroad / ro.mifavor.custom=abroad\n用途：讓 SystemUI 走 abroad IMS icon 分支，不再只顯示 HD/HD+。",
                Config.KEY_ENABLE_STATUS_ICON
        ));

        root.addView(styleSection());

        root.addView(actionSection());
        return scrollView;
    }

    private LinearLayout modeSection() {
        LinearLayout box = sectionBox();
        box.addView(text("操作模式", 18, true));
        box.addView(text("Root 全域模式直接 resetprop。Root + LSPosed 模式不永久改全域屬性，只在 Settings/SystemUI 進程內偽造讀值。", 13, false));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        addModeRadio(group, Config.MODE_ROOT_GLOBAL, "Root 全域模式：不 hook，直接套用 resetprop 全域參數");
        addModeRadio(group, Config.MODE_LSPOSED, "Root + LSPosed 模式：hook Settings/SystemUI，較少全域副作用");
        String current = prefs.getString(Config.KEY_OPERATION_MODE, Config.MODE_LSPOSED);
        group.check(modeToId(current));
        group.setOnCheckedChangeListener((radioGroup, checked) -> {
            prefs.edit().putString(Config.KEY_OPERATION_MODE, idToMode(checked)).commit();
            makePrefsReadable();
        });
        box.addView(group);
        return box;
    }

    private LinearLayout sectionSwitch(String title, String description, String key) {
        LinearLayout box = sectionBox();
        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextSize(18);
        sw.setChecked(prefs.getBoolean(key, true));
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).commit();
            makePrefsReadable();
        });
        box.addView(sw);
        box.addView(text(description, 13, false));
        return box;
    }

    private LinearLayout styleSection() {
        LinearLayout box = sectionBox();
        box.addView(text("VoWiFi 圖標樣式", 18, true));
        box.addView(text("作用進程：com.android.systemui\n等效參數：persist.custom.variant.id=GEN_BD\n用途：控制 SystemUI 選用哪一套 VoWiFi/VoLTE icon array。圖標樣式切換後重啟 SystemUI 即可生效。", 13, false));

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        addRadio(group, Config.STYLE_DEFAULT, "預設：不改 persist.custom.variant.id，通常使用 vowifi / vowifi_card1/2/12");
        addRadio(group, Config.STYLE_GEN_BD, "GEN_BD：等效 persist.custom.variant.id=GEN_BD，使用 bd_stat_vowifi / bd_vowifi_card1/2/12");
        addRadio(group, Config.STYLE_ARRAY_HOOK, "Hook array：直接嘗試把 ImsUpdateFeature 指到 BD array，較乾淨但較依賴 ROM 版本");

        String current = prefs.getString(Config.KEY_ICON_STYLE, Config.STYLE_GEN_BD);
        int checkedId = styleToId(current);
        if (checkedId != 0) {
            group.check(checkedId);
        }
        group.setOnCheckedChangeListener((radioGroup, checked) -> {
            prefs.edit().putString(Config.KEY_ICON_STYLE, idToStyle(checked)).commit();
            makePrefsReadable();
        });
        box.addView(group);
        return box;
    }

    private LinearLayout actionSection() {
        LinearLayout box = sectionBox();
        box.addView(text("套用 / 重啟", 18, true));
        box.addView(text("套用只同步目前開關對應的全域參數，不會重啟進程。Root 按鈕會使用 su。", 13, false));

        Button applyGlobal = new Button(this);
        applyGlobal.setText("套用");
        applyGlobal.setOnClickListener(view -> runRootCommands(
                globalApplyCommands(),
                "已套用目前開關值",
                "套用失敗"
        ));
        box.addView(applyGlobal);

        Button restartSettings = new Button(this);
        restartSettings.setText("重啟 Settings");
        restartSettings.setOnClickListener(view -> runRootCommand(
                prefsPermissionCommand() + "; am force-stop com.android.settings",
                "已執行：am force-stop com.android.settings",
                "重啟 Settings 失敗"
        ));
        box.addView(restartSettings);

        Button restartSystemUi = new Button(this);
        restartSystemUi.setText("重啟 SystemUI");
        restartSystemUi.setOnClickListener(view -> runRootCommand(
                prefsPermissionCommand() + "; kill -9 $(pidof com.android.systemui)",
                "已執行：kill -9 $(pidof com.android.systemui)",
                "重啟 SystemUI 失敗"
        ));
        box.addView(restartSystemUi);

        Button restartBoth = new Button(this);
        restartBoth.setText("重啟 Settings + SystemUI");
        restartBoth.setOnClickListener(view -> runRootCommand(
                prefsPermissionCommand() + "; am force-stop com.android.settings; kill -9 $(pidof com.android.systemui)",
                "已重啟 Settings + SystemUI",
                "重啟失敗"
        ));
        box.addView(restartBoth);

        return box;
    }

    private void addModeRadio(RadioGroup group, String mode, String label) {
        RadioButton button = new RadioButton(this);
        button.setId(modeToId(mode));
        button.setText(label);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER_VERTICAL);
        group.addView(button);
    }

    private void addRadio(RadioGroup group, String style, String label) {
        RadioButton button = new RadioButton(this);
        button.setId(styleToId(style));
        button.setText(label);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER_VERTICAL);
        group.addView(button);
    }

    private int styleToId(String style) {
        if (Config.STYLE_GEN_BD.equals(style)) return 1002;
        if (Config.STYLE_ARRAY_HOOK.equals(style)) return 1003;
        return 1001;
    }

    private String idToStyle(int id) {
        if (id == 1002) return Config.STYLE_GEN_BD;
        if (id == 1003) return Config.STYLE_ARRAY_HOOK;
        return Config.STYLE_DEFAULT;
    }

    private int modeToId(String mode) {
        if (Config.MODE_ROOT_GLOBAL.equals(mode)) return 2002;
        return 2001;
    }

    private String idToMode(int id) {
        if (id == 2002) return Config.MODE_ROOT_GLOBAL;
        return Config.MODE_LSPOSED;
    }

    private LinearLayout sectionBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(18), 0, dp(10));
        box.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return box;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setLineSpacing(0, 1.12f);
        if (bold) {
            textView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return textView;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void makePrefsReadable() {
        File dir = new File(getApplicationInfo().dataDir, "shared_prefs");
        File file = new File(dir, Config.PREFS_NAME + ".xml");
        dir.setExecutable(true, false);
        dir.setReadable(true, false);
        file.setReadable(true, false);
        runRootCommandQuietly(prefsPermissionCommand());
    }

    private String prefsPermissionCommand() {
        String pkg = getPackageName();
        String file = Config.PREFS_NAME + ".xml";
        return "chmod 755 /data/user/0/" + pkg + " /data/user/0/" + pkg + "/shared_prefs "
                + "/data/data/" + pkg + " /data/data/" + pkg + "/shared_prefs 2>/dev/null; "
                + "chmod 644 /data/user/0/" + pkg + "/shared_prefs/" + file + " "
                + "/data/data/" + pkg + "/shared_prefs/" + file + " 2>/dev/null";
    }

    private String resetpropSet(String key, String value) {
        return "if [ -x /data/adb/ksu/bin/resetprop ]; then /data/adb/ksu/bin/resetprop -n "
                + key + " " + value + "; else resetprop -n " + key + " " + value + "; fi";
    }

    private String resetpropDelete(String key) {
        return "if [ -x /data/adb/ksu/bin/resetprop ]; then /data/adb/ksu/bin/resetprop -d "
                + key + " || true; else resetprop -d " + key + " || true; fi";
    }

    private List<String> globalApplyCommands() {
        List<String> commands = new ArrayList<>();
        commands.add(resetpropSet(
                "ro.vendor.feature.zte_feature_need_wfc_for_domestic",
                prefs.getBoolean(Config.KEY_ENABLE_WFC_SETTINGS, true) ? "true" : "false"
        ));
        if (prefs.getBoolean(Config.KEY_ENABLE_STATUS_ICON, true)) {
            commands.add(resetpropSet("ro.vendor.mifavor.custom", "abroad"));
            commands.add(resetpropSet("ro.mifavor.custom", "abroad"));
        } else {
            commands.add(resetpropSet("ro.vendor.mifavor.custom", "home"));
            commands.add(resetpropSet("ro.mifavor.custom", "home"));
        }
        String style = prefs.getString(Config.KEY_ICON_STYLE, Config.STYLE_GEN_BD);
        if (Config.STYLE_GEN_BD.equals(style) || Config.STYLE_ARRAY_HOOK.equals(style)) {
            commands.add(resetpropSet("persist.custom.variant.id", "GEN_BD"));
        } else {
            commands.add(resetpropDelete("persist.custom.variant.id"));
        }
        return commands;
    }

    private void runRootCommandQuietly(String command) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            process.waitFor();
        } catch (IOException ignored) {
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void runRootCommand(String command, String successMessage, String errorMessage) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            int exitCode = process.waitFor();
            Toast.makeText(this, exitCode == 0 ? successMessage : errorMessage + " (" + exitCode + ")", Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            Toast.makeText(this, errorMessage + "：無法取得 root", Toast.LENGTH_LONG).show();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Toast.makeText(this, errorMessage + "：執行中斷", Toast.LENGTH_LONG).show();
        }
    }

    private void runRootCommands(List<String> commands, String successMessage, String errorMessage) {
        try {
            for (String command : commands) {
                Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    Toast.makeText(this, errorMessage + " (" + exitCode + ")：" + command, Toast.LENGTH_LONG).show();
                    return;
                }
            }
            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            Toast.makeText(this, errorMessage + "：無法取得 root", Toast.LENGTH_LONG).show();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Toast.makeText(this, errorMessage + "：執行中斷", Toast.LENGTH_LONG).show();
        }
    }
}

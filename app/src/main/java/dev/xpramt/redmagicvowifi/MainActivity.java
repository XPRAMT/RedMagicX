package dev.xpramt.redmagicvowifi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.Window;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int APP_BAR_COLOR = Color.rgb(23, 33, 44);

    private SharedPreferences prefs;
    private TextView actualValuesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(APP_BAR_COLOR);
        getWindow().setNavigationBarColor(Color.BLACK);
        prefs = Config.appPrefs(this);
        ensureDefaults();
        setContentView(createContent());
        setSystemBarIconColors(getWindow());
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

    private LinearLayout createContent() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.BLACK);
        screen.addView(appBar());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(20), dp(10), dp(20), dp(28));
        scrollView.addView(root);

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

        root.addView(actualValuesSection());
        root.addView(actionSection());
        refreshActualValues();
        screen.addView(scrollView);
        return screen;
    }

    private LinearLayout appBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(APP_BAR_COLOR);
        bar.setPadding(dp(20), dp(10), dp(8), dp(10));
        bar.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
            view.setPadding(dp(20), top + dp(10), dp(8), dp(10));
            return insets;
        });
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("RedMagic VoWiFi", 20, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        bar.addView(title);

        TextView menu = text("⋮", 24, false);
        menu.setGravity(Gravity.CENTER);
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setText("⋮");
        menu.setMinWidth(dp(48));
        menu.setMinHeight(dp(48));
        menu.setOnClickListener(view -> showOverflowMenu(menu));
        bar.addView(menu);
        return bar;
    }

    private void setSystemBarIconColors(Window window) {
        WindowInsetsController controller = window.getDecorView().getWindowInsetsController();
        if (controller != null) {
            controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
    }

    private void showOverflowMenu(TextView anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("關於");
        popup.setOnMenuItemClickListener(item -> {
            showAboutDialog();
            return true;
        });
        popup.show();
    }

    private void showAboutDialog() {
        String message = "作者：XPRAMT\nGitHub：https://github.com/XPRAMT/Redmagic-VoWiFi\n版本：" + versionName();
        SpannableString spannable = new SpannableString(message);
        String url = "https://github.com/XPRAMT/Redmagic-VoWiFi";
        int start = message.indexOf(url);
        if (start >= 0) {
            spannable.setSpan(new URLSpan(url), start, start + url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView content = text("", 14, false);
        content.setText(spannable);
        content.setMovementMethod(LinkMovementMethod.getInstance());
        content.setPadding(dp(24), dp(8), dp(24), 0);

        new AlertDialog.Builder(this)
                .setTitle("關於")
                .setView(content)
                .setPositiveButton("確定", null)
                .show();
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            return "unknown";
        }
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
            applyCurrentState("已切換模式並寫入目前設定", "模式切換寫入失敗");
        });
        box.addView(group);
        return box;
    }

    private LinearLayout actualValuesSection() {
        LinearLayout box = sectionBox();
        box.addView(text("Root 全域實際值", 18, true));
        actualValuesView = text("讀取中...", 13, false);
        box.addView(actualValuesView);
        return box;
    }

    private LinearLayout sectionSwitch(String title, String description, String key) {
        LinearLayout box = sectionBox();
        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextSize(18);
        sw.setTextColor(Color.WHITE);
        sw.setChecked(prefs.getBoolean(key, true));
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).commit();
            applyCurrentState("已寫入：" + title, "寫入失敗");
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
        addRadio(group, Config.STYLE_ARRAY_HOOK, "Hook array：僅 Root + LSPosed 模式生效，替換 ImsUpdateFeature 的 IMS icon array；已實測雙卡可用，依賴目前 ROM 方法名");

        String current = prefs.getString(Config.KEY_ICON_STYLE, Config.STYLE_GEN_BD);
        int checkedId = styleToId(current);
        if (checkedId != 0) {
            group.check(checkedId);
        }
        group.setOnCheckedChangeListener((radioGroup, checked) -> {
            prefs.edit().putString(Config.KEY_ICON_STYLE, idToStyle(checked)).commit();
            applyCurrentState("已寫入：VoWiFi 圖標樣式", "圖標樣式寫入失敗");
        });
        box.addView(group);
        return box;
    }

    private LinearLayout actionSection() {
        LinearLayout box = sectionBox();
        box.addView(text("重啟", 18, true));
        box.addView(text("開關變更會自動寫入。重啟按鈕只負責讓 Settings/SystemUI 重新讀取目前設定。Root 按鈕會使用 su。", 13, false));

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
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER_VERTICAL);
        group.addView(button);
    }

    private void addRadio(RadioGroup group, String style, String label) {
        RadioButton button = new RadioButton(this);
        button.setId(styleToId(style));
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
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
        textView.setTextColor(Color.WHITE);
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

    private void applyCurrentState(String successMessage, String errorMessage) {
        makePrefsReadable();
        if (Config.MODE_ROOT_GLOBAL.equals(prefs.getString(Config.KEY_OPERATION_MODE, Config.MODE_LSPOSED))) {
            runRootCommands(globalApplyCommands(), successMessage, errorMessage);
        } else {
            refreshActualValues();
            Toast.makeText(this, "已保存 LSPosed hook 設定", Toast.LENGTH_LONG).show();
        }
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
        if (Config.STYLE_GEN_BD.equals(style)) {
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
            refreshActualValues();
            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            Toast.makeText(this, errorMessage + "：無法取得 root", Toast.LENGTH_LONG).show();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Toast.makeText(this, errorMessage + "：執行中斷", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshActualValues() {
        if (actualValuesView == null) {
            return;
        }
        if (!Config.MODE_ROOT_GLOBAL.equals(prefs.getString(Config.KEY_OPERATION_MODE, Config.MODE_LSPOSED))) {
            actualValuesView.setText("LSPosed 模式不修改全域屬性，這些 getprop 實際值不代表 hook 是否生效。請以 Settings/SystemUI 行為或 LSPosed log 判斷。");
            return;
        }
        actualValuesView.setText(
                "ro.vendor.feature.zte_feature_need_wfc_for_domestic = " + displayValue(getprop("ro.vendor.feature.zte_feature_need_wfc_for_domestic")) + "\n"
                        + "ro.vendor.mifavor.custom = " + displayValue(getprop("ro.vendor.mifavor.custom")) + "\n"
                        + "ro.mifavor.custom = " + displayValue(getprop("ro.mifavor.custom")) + "\n"
                        + "persist.custom.variant.id = " + displayValue(getprop("persist.custom.variant.id"))
        );
    }

    private String getprop(String key) {
        try {
            Process process = new ProcessBuilder("getprop", key).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String value = reader.readLine();
            int exitCode = process.waitFor();
            if (exitCode != 0 || value == null) {
                return "";
            }
            return value.trim();
        } catch (IOException exception) {
            return "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private String displayValue(String value) {
        return value == null || value.isEmpty() ? "(空)" : value;
    }
}

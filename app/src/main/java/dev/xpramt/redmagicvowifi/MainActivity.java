package dev.xpramt.redmagicvowifi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int APP_BAR_COLOR = Color.rgb(23, 33, 44);
    private static final int PAGE_HOME = 0;
    private static final int PAGE_VOWIFI = 1;
    private static final int PAGE_VOLUME = 2;
    private static final int PAGE_ASSISTANT = 3;

    private SharedPreferences prefs;
    private LinearLayout screen;
    private LinearLayout contentRoot;
    private TextView titleView;
    private TextView backView;
    private TextView actualValuesView;
    private int currentPage = PAGE_HOME;
    private int assistantTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(APP_BAR_COLOR);
        getWindow().setNavigationBarColor(Color.BLACK);
        prefs = Config.appPrefs(this);
        ensureDefaults();
        setContentView(createShell());
        setSystemBarIconColors(getWindow());
        showHome();
    }

    @Override
    public void onBackPressed() {
        if (currentPage != PAGE_HOME) {
            showHome();
            return;
        }
        super.onBackPressed();
    }

    private void ensureDefaults() {
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        if (!prefs.contains(Config.KEY_ENABLE_WFC_SETTINGS)) {
            editor.putBoolean(Config.KEY_ENABLE_WFC_SETTINGS, true);
            editor.putBoolean(Config.KEY_ENABLE_STATUS_ICON, true);
            editor.putString(Config.KEY_ICON_STYLE, Config.STYLE_GEN_BD);
            editor.putString(Config.KEY_OPERATION_MODE, Config.MODE_LSPOSED);
            changed = true;
        }
        if (!prefs.contains(Config.KEY_VOLUME_STEP_ENABLED)) {
            editor.putBoolean(Config.KEY_VOLUME_STEP_ENABLED, false);
            editor.putInt(Config.KEY_VOLUME_STEP, Config.DEFAULT_VOLUME_STEP);
            changed = true;
        }
        if (!prefs.contains(Config.KEY_ASSISTANT_REDIRECT_ENABLED)) {
            editor.putBoolean(Config.KEY_ASSISTANT_REDIRECT_ENABLED, false);
            editor.putString(Config.KEY_ASSISTANT_TARGET, Config.ASSISTANT_TARGET_DEFAULT);
            changed = true;
        }
        if (changed) {
            editor.commit();
        }
    }

    private LinearLayout createShell() {
        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Color.BLACK);
        screen.addView(appBar());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        contentRoot = new LinearLayout(this);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        contentRoot.setBackgroundColor(Color.BLACK);
        contentRoot.setPadding(dp(20), dp(14), dp(20), dp(28));
        scrollView.addView(contentRoot);
        screen.addView(scrollView);
        return screen;
    }

    private LinearLayout appBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(APP_BAR_COLOR);
        bar.setPadding(dp(8), dp(10), dp(8), dp(10));
        bar.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
            view.setPadding(dp(8), top + dp(10), dp(8), dp(10));
            return insets;
        });
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        backView = text("<", 24, false);
        backView.setGravity(Gravity.CENTER);
        backView.setMinWidth(dp(48));
        backView.setMinHeight(dp(48));
        backView.setOnClickListener(view -> showHome());
        bar.addView(backView);

        titleView = text("RedMagic 工具", 20, true);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        bar.addView(titleView);

        TextView menu = text("⋮", 24, false);
        menu.setGravity(Gravity.CENTER);
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setMinWidth(dp(48));
        menu.setMinHeight(dp(48));
        menu.setOnClickListener(view -> showOverflowMenu(menu));
        bar.addView(menu);
        return bar;
    }

    private void showHome() {
        currentPage = PAGE_HOME;
        titleView.setText("RedMagic 工具");
        backView.setVisibility(View.INVISIBLE);
        contentRoot.removeAllViews();
        contentRoot.addView(text("選擇要調整的功能", 18, true));
        contentRoot.addView(text("VoWiFi UI 修正處理中國版系統的設定頁與狀態列圖標。音量步進調整用 LSPosed 修改音量鍵每次增減的格數。", 13, false));
        contentRoot.addView(featureButton(
                "VoWiFi UI 修正",
                "Wi-Fi Calling 設定、狀態列 VoWiFi 圖標、VoWiFi 圖標樣式",
                view -> showVoWifiPage()
        ));
        contentRoot.addView(featureButton(
                "音量步進調整",
                "自訂音量鍵每次增減 1 到 10 格，作用於媒體音量",
                view -> showVolumePage()
        ));
        contentRoot.addView(featureButton(
                "魔姬手勢替換",
                "攔截 SystemUI 的小白條長按 Assistant 入口，改啟動系統動作、使用者 App 或系統 App",
                view -> showAssistantPage()
        ));
    }

    private void showVoWifiPage() {
        currentPage = PAGE_VOWIFI;
        titleView.setText("VoWiFi UI 修正");
        backView.setVisibility(View.VISIBLE);
        contentRoot.removeAllViews();

        contentRoot.addView(text("支援 Root 全域 resetprop 與 Root + LSPosed hook 兩種模式。", 14, false));
        contentRoot.addView(modeSection());
        contentRoot.addView(sectionSwitch(
                "開啟 VoWiFi 設定",
                "作用進程：com.android.settings\n等效參數：ro.vendor.feature.zte_feature_need_wfc_for_domestic=true\n用途：讓 Settings 的 ZTE 國內 WFC gate 通過，顯示 Wi-Fi Calling/VoWiFi 開關。仍需要 Pixel IMS 或 carrier config 啟用 WFC。",
                Config.KEY_ENABLE_WFC_SETTINGS
        ));
        contentRoot.addView(sectionSwitch(
                "開啟狀態列 VoWiFi 圖標",
                "作用進程：com.android.systemui\nLSPosed 模式：只讓 IMS/訊號圖標相關呼叫讀到 ro.vendor.mifavor.custom=abroad / ro.mifavor.custom=abroad；小白條、assistant、navigation 相關呼叫會固定讀到 home，避免手勢被 abroad 分支影響。\nRoot 全域模式：仍會全域 resetprop，可能影響其它 abroad 分支。",
                Config.KEY_ENABLE_STATUS_ICON
        ));
        contentRoot.addView(styleSection());
        contentRoot.addView(actualValuesSection());
        contentRoot.addView(actionSection());
        refreshActualValues();
    }

    private void showVolumePage() {
        currentPage = PAGE_VOLUME;
        titleView.setText("音量步進調整");
        backView.setVisibility(View.VISIBLE);
        contentRoot.removeAllViews();
        contentRoot.addView(volumeSection());
        contentRoot.addView(text("生效條件：LSPosed 需勾選 android scope，並重啟手機讓 system_server 載入模組。設定值會即時寫入，已載入 hook 後通常不需要重新安裝 APK。", 13, false));
    }

    private void showAssistantPage() {
        currentPage = PAGE_ASSISTANT;
        titleView.setText("魔姬手勢替換");
        backView.setVisibility(View.VISIBLE);
        contentRoot.removeAllViews();
        contentRoot.addView(assistantSection());
        contentRoot.addView(text("生效條件：LSPosed 需勾選 com.android.systemui scope，並重啟 SystemUI 或手機。此功能不修改系統預設 assistant 設定，也不需要魔姬存在；它在 SystemUI 發出小白條長按 assistant 事件前攔截，改啟動指定目標。選擇目標後會立即保存。", 13, false));
    }

    private LinearLayout featureButton(String title, String description, View.OnClickListener listener) {
        LinearLayout box = sectionBox();
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextSize(18);
        button.setOnClickListener(listener);
        box.addView(button);
        box.addView(text(description, 13, false));
        return box;
    }

    private LinearLayout volumeSection() {
        LinearLayout box = sectionBox();
        Switch enabled = new Switch(this);
        enabled.setText("啟用自訂音量步進");
        enabled.setTextSize(18);
        enabled.setTextColor(Color.WHITE);
        enabled.setChecked(prefs.getBoolean(Config.KEY_VOLUME_STEP_ENABLED, false));
        box.addView(enabled);

        TextView stepLabel = text("", 16, true);
        box.addView(stepLabel);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(Config.MAX_VOLUME_STEP - Config.MIN_VOLUME_STEP);
        seekBar.setProgress(Config.clampVolumeStep(prefs.getInt(Config.KEY_VOLUME_STEP, Config.DEFAULT_VOLUME_STEP)) - Config.MIN_VOLUME_STEP);
        box.addView(seekBar);

        Runnable updateLabel = () -> stepLabel.setText("目前設定：" + volumeStepFromSeekBar(seekBar) + " / 10");
        updateLabel.run();

        enabled.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(Config.KEY_VOLUME_STEP_ENABLED, isChecked).commit();
            Toast.makeText(this, "已寫入音量步進開關", Toast.LENGTH_LONG).show();
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateLabel.run();
                if (fromUser) {
                    prefs.edit().putInt(Config.KEY_VOLUME_STEP, volumeStepFromSeekBar(bar)).commit();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                prefs.edit().putInt(Config.KEY_VOLUME_STEP, volumeStepFromSeekBar(bar)).commit();
                Toast.makeText(MainActivity.this, "已寫入音量步進值", Toast.LENGTH_SHORT).show();
            }
        });
        box.addView(text("Hook 目標：android / com.android.server.audio.AudioService\n處理：adjustStreamVolume、adjustSuggestedStreamVolume\n範圍：只攔截媒體音量的音量鍵升降，步進值 1 到 10。", 13, false));
        return box;
    }

    private int volumeStepFromSeekBar(SeekBar seekBar) {
        return Config.MIN_VOLUME_STEP + seekBar.getProgress();
    }

    private LinearLayout assistantSection() {
        LinearLayout box = sectionBox();
        Switch enabled = new Switch(this);
        enabled.setText("啟用魔姬手勢替換");
        enabled.setTextSize(18);
        enabled.setTextColor(Color.WHITE);
        enabled.setChecked(prefs.getBoolean(Config.KEY_ASSISTANT_REDIRECT_ENABLED, false));
        enabled.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(Config.KEY_ASSISTANT_REDIRECT_ENABLED, isChecked).commit();
            Toast.makeText(this, "已寫入魔姬手勢替換開關", Toast.LENGTH_LONG).show();
        });
        box.addView(enabled);
        box.addView(text("攔截目標：SystemUI 內 Context.sendBroadcast / sendBroadcastAsUser 發出的 event_Home_Longpressed\n原理：保留原廠小白條長按判斷，在 SystemUI 發送魔姬喚醒事件前阻止原廣播，改啟動指定目標。", 13, false));

        box.addView(text("目前目標：" + assistantTargetLabel(prefs.getString(Config.KEY_ASSISTANT_TARGET, Config.ASSISTANT_TARGET_DEFAULT)), 14, true));
        box.addView(assistantTabs());
        if (assistantTab == 0) {
            addSystemActions(box);
        } else {
            addAppList(box, assistantTab == 2);
        }
        return box;
    }

    private LinearLayout assistantTabs() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, dp(12), 0, dp(8));
        tabs.addView(tabButton("系統動作", 0));
        tabs.addView(tabButton("User App", 1));
        tabs.addView(tabButton("System App", 2));
        return tabs;
    }

    private Button tabButton(String label, int tab) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(13);
        button.setEnabled(assistantTab != tab);
        button.setOnClickListener(view -> {
            assistantTab = tab;
            showAssistantPage();
        });
        button.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return button;
    }

    private void addSystemActions(LinearLayout box) {
        box.addView(targetButton("語音助手", "啟動系統預設 android.intent.action.ASSIST", Config.TARGET_PREFIX_ACTION + Config.ACTION_DEFAULT_ASSIST));
        box.addView(targetButton("Google 語音助手", "啟動 android.intent.action.VOICE_COMMAND 並指定 Google app", Config.TARGET_PREFIX_ACTION + Config.ACTION_GOOGLE_VOICE));
        box.addView(targetButton("最近應用", "送出 KEYCODE_APP_SWITCH", Config.TARGET_PREFIX_ACTION + Config.ACTION_RECENTS));
        box.addView(targetButton("螢幕截圖", "送出 KEYCODE_SYSRQ", Config.TARGET_PREFIX_ACTION + Config.ACTION_SCREENSHOT));
        box.addView(targetButton("手電筒", "透過 CameraManager 切換背面閃光燈", Config.TARGET_PREFIX_ACTION + Config.ACTION_FLASHLIGHT));
    }

    private void addAppList(LinearLayout box, boolean systemApps) {
        List<ApplicationInfo> apps = installedApps(systemApps);
        box.addView(text((systemApps ? "系統應用" : "使用者應用") + "：" + apps.size() + " 個", 14, true));
        for (ApplicationInfo app : apps) {
            String label = String.valueOf(app.loadLabel(getPackageManager()));
            box.addView(targetButton(label, app.packageName, Config.TARGET_PREFIX_APP + app.packageName));
        }
    }

    private List<ApplicationInfo> installedApps(boolean systemApps) {
        List<ApplicationInfo> result = new ArrayList<>();
        PackageManager packageManager = getPackageManager();
        for (ApplicationInfo app : packageManager.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (app.packageName.equals(getPackageName())) {
                continue;
            }
            if (packageManager.getLaunchIntentForPackage(app.packageName) == null) {
                continue;
            }
            boolean isSystem = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    || (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (isSystem == systemApps) {
                result.add(app);
            }
        }
        Collections.sort(result, Comparator.comparing(app -> String.valueOf(app.loadLabel(packageManager)), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private LinearLayout targetButton(String title, String description, String target) {
        LinearLayout row = sectionBox();
        row.setPadding(0, dp(8), 0, dp(6));
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextSize(15);
        button.setOnClickListener(view -> {
            prefs.edit().putString(Config.KEY_ASSISTANT_TARGET, target).commit();
            Toast.makeText(this, "已選擇：" + title, Toast.LENGTH_SHORT).show();
            showAssistantPage();
        });
        row.addView(button);
        row.addView(text(description, 12, false));
        return row;
    }

    private String assistantTargetLabel(String target) {
        if (target == null || target.isEmpty() || Config.ASSISTANT_TARGET_DEFAULT.equals(target)) {
            return "語音助手";
        }
        if (Config.ASSISTANT_TARGET_GOOGLE_VOICE.equals(target)) {
            return "Google 語音助手";
        }
        if (Config.ASSISTANT_TARGET_CHATGPT.equals(target)) {
            return "ChatGPT";
        }
        if (target.startsWith(Config.TARGET_PREFIX_ACTION)) {
            String action = target.substring(Config.TARGET_PREFIX_ACTION.length());
            if (Config.ACTION_DEFAULT_ASSIST.equals(action)) return "語音助手";
            if (Config.ACTION_GOOGLE_VOICE.equals(action)) return "Google 語音助手";
            if (Config.ACTION_RECENTS.equals(action)) return "最近應用";
            if (Config.ACTION_SCREENSHOT.equals(action)) return "螢幕截圖";
            if (Config.ACTION_FLASHLIGHT.equals(action)) return "手電筒";
            return action;
        }
        if (target.startsWith(Config.TARGET_PREFIX_APP)) {
            String packageName = target.substring(Config.TARGET_PREFIX_APP.length());
            try {
                ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
                return info.loadLabel(getPackageManager()) + " (" + packageName + ")";
            } catch (PackageManager.NameNotFoundException exception) {
                return packageName;
            }
        }
        return target;
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
                "am force-stop com.android.settings",
                "已執行：am force-stop com.android.settings",
                "重啟 Settings 失敗"
        ));
        box.addView(restartSettings);

        Button restartSystemUi = new Button(this);
        restartSystemUi.setText("重啟 SystemUI");
        restartSystemUi.setOnClickListener(view -> runRootCommand(
                "kill -9 $(pidof com.android.systemui)",
                "已執行：kill -9 $(pidof com.android.systemui)",
                "重啟 SystemUI 失敗"
        ));
        box.addView(restartSystemUi);

        Button restartBoth = new Button(this);
        restartBoth.setText("重啟 Settings + SystemUI");
        restartBoth.setOnClickListener(view -> runRootCommand(
                "am force-stop com.android.settings; kill -9 $(pidof com.android.systemui)",
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

    private String resetpropSet(String key, String value) {
        return "if [ -x /data/adb/ksu/bin/resetprop ]; then /data/adb/ksu/bin/resetprop -n "
                + key + " " + value + "; else resetprop -n " + key + " " + value + "; fi";
    }

    private String resetpropDelete(String key) {
        return "if [ -x /data/adb/ksu/bin/resetprop ]; then /data/adb/ksu/bin/resetprop -d "
                + key + " || true; else resetprop -d " + key + " || true; fi";
    }

    private void applyCurrentState(String successMessage, String errorMessage) {
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

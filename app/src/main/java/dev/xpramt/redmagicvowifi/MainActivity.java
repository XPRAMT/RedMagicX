package dev.xpramt.redmagicvowifi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.InputType;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.URL;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int APP_BAR_COLOR = Color.rgb(23, 33, 44);
    private static final int PAGE_HOME = 0;
    private static final int PAGE_VOWIFI = 1;
    private static final int PAGE_VOLUME = 2;
    private static final int PAGE_ASSISTANT = 3;
    private static final int PAGE_LAUNCHER = 4;
    private static final int PAGE_ADB_CONTROL = 5;
    private static final int PAGE_QUICK_ENTRY = 6;
    private static final int CARD_COLOR = Color.rgb(18, 18, 24);
    private static final int CARD_SELECTED_COLOR = Color.rgb(23, 33, 44);
    private static final String SETTINGS_FALLBACK_HOME_PACKAGE = "com.android.settings";
    private static final String SETTINGS_FALLBACK_HOME_CLASS = "com.android.settings.FallbackHome";
    private static final String STOCK_LAUNCHER_PACKAGE = "com.zte.mifavor.launcher";
    private static final String STOCK_LAUNCHER_CLASS = "com.android.launcher3.uioverrides.QuickstepLauncher";
    private static final String STOCK_DIALER_PACKAGE = "com.android.contacts";
    private static final String STOCK_DIALER_CLASS = "com.android.contacts.activities.DialtactsActivity";
    private static final int SHIZUKU_REQUEST_CODE_HOME = 1001;
    private static final int SHIZUKU_REQUEST_CODE_QUICK_ENTRY = 1002;
    private static final String RELEASES_LATEST_URL = "https://api.github.com/repos/XPRAMT/RedMagicX/releases/latest";
    private static final Pattern JSON_STRING_FIELD = Pattern.compile("\\\"(tag_name|html_url)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private SharedPreferences prefs;
    private LinearLayout screen;
    private LinearLayout contentRoot;
    private TextView titleView;
    private TextView backView;
    private int currentPage = PAGE_HOME;
    private int assistantTab = 0;
    private String pendingHomeCommand;
    private String pendingHomeSuccessMessage;
    private String pendingHomeErrorMessage;
    private String pendingQuickEntryCommand;
    private String pendingQuickEntrySuccessMessage;
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService rootExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable wirelessAdbPollTask;
    private boolean updatingWirelessAdbSwitch;
    private boolean updatingDeveloperAdbSwitch;
    private boolean updatingAdbInstallSwitch;
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_REQUEST_CODE_HOME) {
                    return;
                }
                if (grantResult == PackageManager.PERMISSION_GRANTED && pendingHomeCommand != null) {
                    runHomeCommandWithShizuku(pendingHomeCommand, pendingHomeSuccessMessage, pendingHomeErrorMessage);
                } else {
                    showToast("Shizuku 未授權，無法套用預設啟動器");
                }
                pendingHomeCommand = null;
                pendingHomeSuccessMessage = null;
                pendingHomeErrorMessage = null;
            };
    private final Shizuku.OnRequestPermissionResultListener quickEntryShizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_REQUEST_CODE_QUICK_ENTRY) {
                    return;
                }
                if (grantResult == PackageManager.PERMISSION_GRANTED && pendingQuickEntryCommand != null) {
                    runQuickEntryWithShizuku(pendingQuickEntryCommand, pendingQuickEntrySuccessMessage);
                } else {
                    showToast("Shizuku 未授權，無法使用快速入口");
                }
                pendingQuickEntryCommand = null;
                pendingQuickEntrySuccessMessage = null;
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(APP_BAR_COLOR);
        getWindow().setNavigationBarColor(Color.BLACK);
        prefs = Config.appPrefs(this);
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        Shizuku.addRequestPermissionResultListener(quickEntryShizukuPermissionListener);
        ensureDefaults();
        setContentView(createShell());
        setSystemBarIconColors(getWindow());
        showHome();
        if (prefs.getBoolean(Config.KEY_AUTO_CHECK_UPDATES, true)) {
            checkForUpdates(false);
        }
    }

    @Override
    protected void onDestroy() {
        stopWirelessAdbPolling();
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        Shizuku.removeRequestPermissionResultListener(quickEntryShizukuPermissionListener);
        updateExecutor.shutdownNow();
        rootExecutor.shutdownNow();
        super.onDestroy();
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
        if (!prefs.contains(Config.KEY_LAUNCHER_OVERRIDE_ENABLED)) {
            editor.putBoolean(Config.KEY_LAUNCHER_OVERRIDE_ENABLED, false);
            editor.putString(Config.KEY_LAUNCHER_COMPONENT, "");
            editor.putString(Config.KEY_LAUNCHER_PACKAGE, "");
            changed = true;
        }
        String launcherComponent = prefs.getString(Config.KEY_LAUNCHER_COMPONENT, "");
        if (isSettingsFallbackHome(launcherComponent)) {
            ComponentName stockLauncher = stockLauncherComponent();
            editor.putString(Config.KEY_LAUNCHER_COMPONENT, stockLauncher.flattenToString());
            editor.putString(Config.KEY_LAUNCHER_PACKAGE, stockLauncher.getPackageName());
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

        titleView = text("RedMagicX", 20, true);
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
        stopWirelessAdbPolling();
        currentPage = PAGE_HOME;
        titleView.setText("RedMagicX");
        backView.setVisibility(View.INVISIBLE);
        contentRoot.removeAllViews();
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
        contentRoot.addView(featureButton(
                "第三方啟動器",
                "設定預設 HOME，並從最近使用列表隱藏選定啟動器",
                view -> showLauncherPage()
        ));
        contentRoot.addView(featureButton(
                "ADB 控制中心",
                "快速控制 USB 偵錯、ADB 偽裝與無線 ADB",
                view -> showAdbControlPage()
        ));
        contentRoot.addView(featureButton(
                "快速入口",
                "工程模式撥號碼與常用系統入口",
                view -> showQuickEntryPage()
        ));
    }

    private void showVoWifiPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_VOWIFI;
        titleView.setText("VoWiFi UI 修正");
        backView.setVisibility(View.VISIBLE);
        contentRoot.removeAllViews();

        contentRoot.addView(detailText("此功能修正 VoWiFi UI 顯示。"));
        LinearLayout wfcSettingsSection = sectionSwitch(
                "在設定顯示 VoWiFi 設定",
                "在設定顯示 Wi-Fi Calling / VoWiFi 開關。仍需要 Pixel IMS 啟用 WFC。",
                "com.android.settings",
                "ro.vendor.feature.zte_feature_need_wfc_for_domestic=true",
                Config.KEY_ENABLE_WFC_SETTINGS
        );
        Button openWfcSettings = new Button(this);
        openWfcSettings.setText("打開 WiFi 通話設定");
        styleButton(openWfcSettings, false, false);
        openWfcSettings.setOnClickListener(view -> openWifiCallingSettings());
        wfcSettingsSection.addView(openWfcSettings);
        contentRoot.addView(wfcSettingsSection);
        contentRoot.addView(sectionSwitch(
                "開啟狀態列 VoWiFi 圖標",
                "更改狀態列圖標來源，解決 VoWiFi 圖標無法顯示的問題。開啟後 4G 通話圖標由 HD 變為 VoLTE。修改只作用於 IMS / 訊號圖標相關呼叫，避免其它功能受到影響。",
                "com.android.systemui",
                "ro.vendor.mifavor.custom=abroad\nro.mifavor.custom=abroad",
                Config.KEY_ENABLE_STATUS_ICON
        ));
        contentRoot.addView(styleSection());
        contentRoot.addView(actionSection());
    }

    private void openWifiCallingSettings() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(
                "com.android.settings",
                "com.android.settings.Settings$WifiCallingSettingsActivity"
        ));
        try {
            startActivity(intent);
        } catch (SecurityException | android.content.ActivityNotFoundException exception) {
            showToast("無法打開 WiFi 通話設定");
        }
    }

    private void showVolumePage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_VOLUME;
        titleView.setText("音量步進調整");
        backView.setVisibility(View.VISIBLE);
        contentRoot.removeAllViews();
        contentRoot.addView(volumeSection());
    }

    private void showAssistantPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_ASSISTANT;
        titleView.setText("魔姬手勢替換");
        backView.setVisibility(View.VISIBLE);
        contentRoot.removeAllViews();
        contentRoot.addView(assistantSection());
    }

    private void showLauncherPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_LAUNCHER;
        titleView.setText("第三方啟動器");
        backView.setVisibility(View.VISIBLE);
        contentRoot.removeAllViews();
        contentRoot.addView(launcherSection());
    }

    private void showAdbControlPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_ADB_CONTROL;
        titleView.setText("ADB 控制中心");
        backView.setVisibility(View.VISIBLE);
        contentRoot.removeAllViews();
        contentRoot.addView(wirelessAdbSection());
    }

    private void showQuickEntryPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_QUICK_ENTRY;
        titleView.setText("快速入口");
        backView.setVisibility(View.VISIBLE);
        contentRoot.removeAllViews();
        contentRoot.addView(quickEntrySection());
    }

    private LinearLayout quickEntrySection() {
        LinearLayout box = sectionBox();
        box.addView(detailText("以下功能可使用 root 或 Shizuku 授權。"));
        box.addView(verticalSpace(14));
        box.addView(text("工程模式", 18, true));
        box.addView(detailText("首次使用時，請先解鎖工程模式；完成解鎖後可直接進入工程模式主選單。"));
        box.addView(verticalSpace(14));

        Button unlock = new Button(this);
        unlock.setText("解鎖工程模式");
        styleButton(unlock, false, false);
        unlock.setOnClickListener(view -> openEngineeringMode("*983*673636#", "已送出工程模式解鎖碼"));
        box.addView(unlock);
        box.addView(detailText("模擬輸入 *983*673636#。只需要在首次使用時執行。"));

        box.addView(verticalSpace(14));
        Button enter = new Button(this);
        enter.setText("進入工程模式");
        styleButton(enter, false, false);
        enter.setOnClickListener(view -> openEngineeringModeMenu());
        box.addView(enter);
        box.addView(detailText("透過原廠電話鍵盤模擬輸入 *983*0#，完成工程模式初始化並開啟主選單。"));

        box.addView(verticalSpace(22));
        box.addView(text("手機資訊", 18, true));
        box.addView(detailText("開啟系統電話服務的手機資訊頁面，可查看行動網路、IMS 與無線電狀態。"));
        box.addView(verticalSpace(14));
        Button phoneInfo = new Button(this);
        phoneInfo.setText("開啟手機資訊");
        styleButton(phoneInfo, false, false);
        phoneInfo.setOnClickListener(view -> openPhoneInfo());
        box.addView(phoneInfo);
        return box;
    }

    private void openPhoneInfo() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName("com.android.phone", "com.android.phone.settings.RadioInfo"));
        try {
            startActivity(intent);
            showToast("已開啟手機資訊");
        } catch (SecurityException | android.content.ActivityNotFoundException exception) {
            openQuickEntryCommand(
                    "am start -n com.android.phone/.settings.RadioInfo",
                    "已開啟手機資訊"
            );
        }
    }

    private void openEngineeringMode(String dialCode, String successMessage) {
        if (!isStockDialerAvailable()) {
            showToast("找不到支援的原廠電話鍵盤");
            return;
        }
        openQuickEntryCommand(engineeringDialCommand(dialCode), successMessage);
    }

    private boolean isStockDialerAvailable() {
        try {
            return getPackageManager().getActivityInfo(
                    new ComponentName(STOCK_DIALER_PACKAGE, STOCK_DIALER_CLASS),
                    0
            ).enabled;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    private void openEngineeringModeMenu() {
        openEngineeringMode("*983*0#", "已開啟工程模式");
    }

    private void openQuickEntryCommand(String command, String successMessage) {
        rootExecutor.execute(() -> {
            int exitCode = runProcess(new ProcessBuilder("su", "-c", command));
            if (exitCode == 0) {
                mainHandler.post(() -> showToast(successMessage + "（root）"));
                return;
            }
            mainHandler.post(() -> runQuickEntryWithShizukuPermission(command, successMessage));
        });
    }

    private void runQuickEntryWithShizukuPermission(String command, String successMessage) {
        if (!isShizukuAvailable()) {
            showToast("無法使用快速入口：無 root，且 Shizuku 未連線");
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            pendingQuickEntryCommand = command;
            pendingQuickEntrySuccessMessage = successMessage;
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE_QUICK_ENTRY);
            showToast("請授權 Shizuku 後自動繼續");
            return;
        }
        runQuickEntryWithShizuku(command, successMessage);
    }

    private void runQuickEntryWithShizuku(String command, String successMessage) {
        rootExecutor.execute(() -> {
            try {
                Method newProcess = Shizuku.class.getDeclaredMethod(
                        "newProcess", String[].class, String[].class, String.class);
                newProcess.setAccessible(true);
                Process process = (Process) newProcess.invoke(null,
                        new String[]{"sh", "-c", command}, null, null);
                int exitCode = process.waitFor();
                mainHandler.post(() -> showToast(exitCode == 0
                        ? successMessage + "（Shizuku）"
                        : "無法執行快速入口（Shizuku " + exitCode + "）"));
            } catch (Throwable throwable) {
                mainHandler.post(() -> showToast("無法執行快速入口：Shizuku 執行失敗"));
            }
        });
    }

    private String engineeringDialCommand(String dialCode) {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int deleteX = Math.round(1125 * width / 1216f);
        int deleteY = Math.round(1370 * height / 2688f);
        int[] onePoint = dialPadPoint('1', width, height);
        StringBuilder command = new StringBuilder(
                "cmd package resolve-activity --brief -n com.android.contacts/.activities.DialtactsActivity"
                        + " >/dev/null 2>&1 || exit 20; "
                        + "am start -n com.android.contacts/.activities.DialtactsActivity"
                        + " -a android.intent.action.DIAL || exit 21"
        );
        command.append("; sleep 0.5; input tap ")
                .append(onePoint[0]).append(' ').append(onePoint[1])
                .append("; sleep 0.1; input swipe ")
                .append(deleteX).append(' ').append(deleteY).append(' ')
                .append(deleteX).append(' ').append(deleteY).append(" 600")
                .append("; sleep 0.3; am start -n com.android.contacts/.activities.DialtactsActivity")
                .append(" -a android.intent.action.DIAL || exit 21; sleep 0.5");
        for (int index = 0; index < dialCode.length(); index++) {
            int[] point = dialPadPoint(dialCode.charAt(index), width, height);
            command.append("; input tap ").append(point[0]).append(' ').append(point[1]);
        }
        return command.toString();
    }

    private int[] dialPadPoint(char digit, int width, int height) {
        int column;
        int row;
        if (digit == '*') {
            column = 0;
            row = 3;
        } else if (digit == '#') {
            column = 2;
            row = 3;
        } else if (digit == '0') {
            column = 1;
            row = 3;
        } else {
            int value = digit - '1';
            column = value % 3;
            row = value / 3;
        }
        int[] x = {235, 607, 980};
        int[] y = {1569, 1764, 1959, 2154};
        return new int[]{
                Math.round(x[column] * width / 1216f),
                Math.round(y[row] * height / 2688f)
        };
    }

    private LinearLayout featureButton(String title, String description, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dp(12), 0, dp(4));
        card.setLayoutParams(cardParams);
        card.setBackground(cardBackground(CARD_COLOR));
        card.setClickable(true);
        card.setOnClickListener(listener);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        labels.addView(text(title, 17, true));
        TextView detail = detailText(description);
        detail.setTextColor(Color.rgb(190, 196, 205));
        labels.addView(detail);
        card.addView(labels);
        return card;
    }

    private LinearLayout volumeSection() {
        LinearLayout box = sectionBox();
        Switch enabled = new Switch(this);
        enabled.setText("啟用自訂音量步進");
        enabled.setTextSize(18);
        enabled.setTextColor(Color.WHITE);
        enabled.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        enabled.setChecked(prefs.getBoolean(Config.KEY_VOLUME_STEP_ENABLED, false));
        box.addView(enabled);

        TextView stepLabel = text("", 16, true);
        stepLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(stepLabel);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(Config.MAX_VOLUME_STEP - Config.MIN_VOLUME_STEP);
        seekBar.setProgress(Config.clampVolumeStep(prefs.getInt(Config.KEY_VOLUME_STEP, Config.DEFAULT_VOLUME_STEP)) - Config.MIN_VOLUME_STEP);
        box.addView(seekBar);

        Runnable updateLabel = () -> stepLabel.setText(volumeStepFromSeekBar(seekBar) + " / 10");
        updateLabel.run();

        enabled.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(Config.KEY_VOLUME_STEP_ENABLED, isChecked).commit();
            showToast("已寫入音量步進開關");
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
                showToast("已寫入音量步進值");
            }
        });
        box.addView(detailText("攔截媒體音量的音量鍵升降步進值，可設為 1 到 10。第一次載入可能需要重啟手機才能生效。"));
        addDetailField(box, "作用進程", "android / System Framework");
        addDetailField(box, "攔截方法", "adjustStreamVolume\nadjustSuggestedStreamVolume");
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
        enabled.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        enabled.setChecked(prefs.getBoolean(Config.KEY_ASSISTANT_REDIRECT_ENABLED, false));
        enabled.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(Config.KEY_ASSISTANT_REDIRECT_ENABLED, isChecked).commit();
            showToast("已寫入魔姬手勢替換開關");
        });
        box.addView(enabled);
        box.addView(detailText("攔截小白條長按喚出魔姬助手的事件，改為啟動指定目標。可能需要重啟 SystemUI 才能生效。"));
        addDetailField(box, "作用進程", "com.android.systemui");
        addDetailField(box, "攔截方法", "Context.sendBroadcast / sendBroadcastAsUser 發出的 event_Home_Longpressed");
        addDetailField(box, "目前目標", assistantTargetLabel(prefs.getString(Config.KEY_ASSISTANT_TARGET, Config.ASSISTANT_TARGET_DEFAULT)));
        box.addView(assistantTabs());
        if (assistantTab == 0) {
            addSystemActions(box);
        } else {
            addAppList(box, assistantTab == 2);
        }
        return box;
    }

    private LinearLayout launcherSection() {
        LinearLayout box = sectionBox();
        Switch enabled = new Switch(this);
        enabled.setText("從最近使用列表隱藏選定啟動器");
        enabled.setTextSize(18);
        enabled.setTextColor(Color.WHITE);
        enabled.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        enabled.setChecked(prefs.getBoolean(Config.KEY_LAUNCHER_OVERRIDE_ENABLED, false));
        enabled.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(Config.KEY_LAUNCHER_OVERRIDE_ENABLED, isChecked).commit();
            showToast("已寫入最近任務隱藏開關");
        });
        box.addView(enabled);
        box.addView(detailText("避免使用第三方啟動器時，啟動器被補成最近任務卡片。只在手勢模式、目前任務為選定第三方 HOME 時生效。"));
        addDetailField(box, "作用進程", "com.zte.mifavor.launcher");
        addDetailField(box, "攔截方法", "RecentsView#onGestureAnimationStart");
        box.addView(verticalSpace(14));
        box.addView(text("更換啟動器", 18, true));
        box.addView(detailText("優先使用 root 執行系統 set-home-activity；無 root 時可透過 Shizuku 授權，以 shell 權限套用。"));
        addDetailField(box, "指令", "cmd package set-home-activity --user 0 <component>");

        String component = prefs.getString(Config.KEY_LAUNCHER_COMPONENT, "");
        if (isSettingsFallbackHome(component)) {
            component = stockLauncherComponent().flattenToString();
            prefs.edit()
                    .putString(Config.KEY_LAUNCHER_COMPONENT, component)
                    .putString(Config.KEY_LAUNCHER_PACKAGE, STOCK_LAUNCHER_PACKAGE)
                    .commit();
        }

        List<ResolveInfo> launchers = installedLaunchers();
        if (launchers.isEmpty()) {
            box.addView(detailText("未找到可處理 HOME intent 的啟動器。"));
        }
        for (ResolveInfo info : launchers) {
            String packageName = info.activityInfo.packageName;
            String className = info.activityInfo.name;
            if (isSettingsFallbackHome(packageName, className)) {
                continue;
            }
            ComponentName componentName = new ComponentName(packageName, className);
            CharSequence label = info.loadLabel(getPackageManager());
            String title = label == null ? packageName : label.toString();
            String description = componentName.flattenToShortString();
            Drawable icon = info.loadIcon(getPackageManager());
            box.addView(launcherCard(title, description, componentName.flattenToString(), packageName, icon));
        }
        return box;
    }

    private LinearLayout wirelessAdbSection() {
        LinearLayout box = sectionBox();
        box.addView(detailText("以下功能皆需 root 權限。"));
        box.addView(verticalSpace(14));

        Switch developerAdb = new Switch(this);
        developerAdb.setText("啟用 ADB（開發人員選項）");
        developerAdb.setTextSize(18);
        developerAdb.setTextColor(Color.WHITE);
        developerAdb.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        developerAdb.setEnabled(false);
        box.addView(developerAdb);
        box.addView(detailText("快速控制開發人員選項的「USB 偵錯」開關。關閉後也會中斷無線 ADB。"));
        box.addView(verticalSpace(14));

        Switch fakeAdb = new Switch(this);
        fakeAdb.setText("偽裝 ADB 狀態");
        fakeAdb.setTextSize(18);
        fakeAdb.setTextColor(Color.WHITE);
        fakeAdb.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        fakeAdb.setEnabled(false);
        box.addView(fakeAdb);
        box.addView(detailText("寫入 adb_enabled=2。系統會把大於零的值視為 ADB 已開啟，只能欺騙精確檢查 adb_enabled 是否等於 1 的部分 App。對於會檢查非零值或其它偵錯狀態的 App 無效。"));
        TextView developerAdbStatus = wirelessAdbField(box, "系統狀態", "讀取中...");

        box.addView(verticalSpace(14));

        Switch enabled = new Switch(this);
        enabled.setText("啟用無線 ADB");
        enabled.setTextSize(18);
        enabled.setTextColor(Color.WHITE);
        enabled.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        enabled.setChecked(prefs.getBoolean(Config.KEY_WIRELESS_ADB_ENABLED, false));
        enabled.setEnabled(false);
        box.addView(enabled);
        box.addView(detailText("立即設定 ADB TCP 連接埠並重啟 adbd。開啟後會保留使用者設定；ADB 已開啟時每 5 秒確認一次，未執行時會自動恢復。"));

        TextView customPortLabel = text("自訂連接埠", 14, true);
        customPortLabel.setPadding(0, dp(10), 0, 0);
        box.addView(customPortLabel);
        EditText customPort = new EditText(this);
        customPort.setText(String.valueOf(prefs.getInt(Config.KEY_WIRELESS_ADB_PORT, Config.DEFAULT_WIRELESS_ADB_PORT)));
        customPort.setTextSize(14);
        customPort.setTextColor(Color.WHITE);
        customPort.setHintTextColor(Color.rgb(190, 196, 205));
        customPort.setSingleLine(true);
        customPort.setSelectAllOnFocus(false);
        customPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        customPort.setBackground(cardBackground(CARD_COLOR));
        customPort.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams portParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        portParams.setMargins(0, dp(4), 0, 0);
        customPort.setLayoutParams(portParams);
        box.addView(customPort);

        TextView statusValue = wirelessAdbField(box, "狀態", "讀取中...");
        TextView endpointValue = wirelessAdbField(box, "IP 位址", "讀取中...");
        TextView commandValue = wirelessAdbField(box, "連線指令", "-");

        Button copyCommand = new Button(this);
        copyCommand.setText("複製連線指令");
        styleButton(copyCommand, false, false);
        copyCommand.setEnabled(false);
        copyCommand.setOnClickListener(view -> {
            String command = String.valueOf(commandValue.getText());
            if (!"-".equals(command)) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("ADB connect", command));
                showToast("已複製連線指令");
            }
        });
        box.addView(copyCommand);

        Button applyPort = new Button(this);
        applyPort.setText("套用");
        styleButton(applyPort, false, false);
        applyPort.setOnClickListener(view -> {
            int port = parseWirelessAdbPort(customPort);
            if (port == -1) {
                showToast("連接埠必須介於 1 到 65535");
                return;
            }
            prefs.edit().putInt(Config.KEY_WIRELESS_ADB_PORT, port).commit();
            applyWirelessAdbPort(port, enabled, statusValue, endpointValue, commandValue, copyCommand);
        });
        box.addView(applyPort);

        box.addView(verticalSpace(14));
        Switch adbInstall = new Switch(this);
        adbInstall.setText("允許 ADB 安裝");
        adbInstall.setTextSize(18);
        adbInstall.setTextColor(Color.WHITE);
        adbInstall.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        adbInstall.setEnabled(false);
        box.addView(adbInstall);
        box.addView(detailText("控制開發人員選項的「允許 USB 安裝」，開啟時寫入 adb_install_enabled=1。"));

        enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updatingWirelessAdbSwitch) {
                int port = parseWirelessAdbPort(customPort);
                if (port == -1) {
                    updatingWirelessAdbSwitch = true;
                    enabled.setChecked(false);
                    updatingWirelessAdbSwitch = false;
                    showToast("連接埠必須介於 1 到 65535");
                    return;
                }
                prefs.edit()
                        .putInt(Config.KEY_WIRELESS_ADB_PORT, port)
                        .putBoolean(Config.KEY_WIRELESS_ADB_ENABLED, isChecked)
                        .commit();
                toggleWirelessAdb(isChecked, port, enabled, statusValue, endpointValue, commandValue, copyCommand);
            }
        });
        developerAdb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updatingDeveloperAdbSwitch) {
                toggleDeveloperAdb(isChecked, developerAdb, fakeAdb, developerAdbStatus,
                        enabled, statusValue, endpointValue, commandValue, copyCommand);
            }
        });
        fakeAdb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updatingDeveloperAdbSwitch) {
                toggleFakeAdb(isChecked, developerAdb, fakeAdb, developerAdbStatus,
                        enabled, statusValue, endpointValue, commandValue, copyCommand);
            }
        });
        adbInstall.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updatingAdbInstallSwitch) {
                toggleAdbInstall(isChecked, adbInstall);
            }
        });
        refreshDeveloperAdbStatus(developerAdb, fakeAdb, developerAdbStatus);
        refreshAdbInstallStatus(adbInstall);
        startWirelessAdbPolling(enabled, statusValue, endpointValue, commandValue, copyCommand);
        return box;
    }

    private TextView wirelessAdbField(LinearLayout box, String label, String value) {
        addDetailField(box, label, value);
        return (TextView) box.getChildAt(box.getChildCount() - 1);
    }

    private void refreshDeveloperAdbStatus(Switch toggle, Switch fakeToggle, TextView statusValue) {
        toggle.setEnabled(false);
        fakeToggle.setEnabled(false);
        statusValue.setText("讀取中...");
        rootExecutor.execute(() -> {
            String adbSetting = readRootCommandOutput("settings get global adb_enabled");
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                syncAdbSpoofPreference(adbSetting);
                updateDeveloperAdbViews(adbSetting, toggle, fakeToggle, statusValue);
            });
        });
    }

    private void toggleDeveloperAdb(boolean enabled, Switch toggle, Switch fakeToggle, TextView statusValue,
                                    Switch wirelessToggle, TextView wirelessStatusValue, TextView endpointValue,
                                    TextView commandValue, Button copyCommand) {
        toggle.setEnabled(false);
        fakeToggle.setEnabled(false);
        statusValue.setText("套用中...");
        rootExecutor.execute(() -> {
            int targetValue = enabled
                    ? (prefs.getBoolean(Config.KEY_ADB_SPOOF_ENABLED, false) ? 2 : 1)
                    : 0;
            int exitCode = runProcess(new ProcessBuilder("su", "-c", "settings put global adb_enabled " + targetValue));
            String adbSetting = readRootCommandOutput("settings get global adb_enabled");
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateDeveloperAdbViews(adbSetting, toggle, fakeToggle, statusValue);
                if (enabled) {
                    restartWirelessAdbPollingAfterDelay(
                            wirelessToggle, wirelessStatusValue, endpointValue, commandValue, copyCommand
                    );
                } else {
                    refreshWirelessAdbStatus(wirelessToggle, wirelessStatusValue, endpointValue, commandValue, copyCommand);
                }
                showToast(exitCode == 0 && String.valueOf(targetValue).equals(adbSetting)
                        ? (enabled ? "ADB 已開啟" : "ADB 已關閉")
                        : "ADB 未套用，請確認 root 權限");
            });
        });
    }

    private void toggleFakeAdb(boolean enabled, Switch toggle, Switch fakeToggle, TextView statusValue,
                               Switch wirelessToggle, TextView wirelessStatusValue, TextView endpointValue,
                               TextView commandValue, Button copyCommand) {
        toggle.setEnabled(false);
        fakeToggle.setEnabled(false);
        statusValue.setText("套用中...");
        prefs.edit().putBoolean(Config.KEY_ADB_SPOOF_ENABLED, enabled).commit();
        rootExecutor.execute(() -> {
            String currentSetting = readRootCommandOutput("settings get global adb_enabled");
            boolean adbEnabled = isAdbEnabled(currentSetting);
            int targetValue = enabled ? 2 : 1;
            int exitCode = adbEnabled
                    ? runProcess(new ProcessBuilder("su", "-c", "settings put global adb_enabled " + targetValue))
                    : 0;
            String adbSetting = readRootCommandOutput("settings get global adb_enabled");
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateDeveloperAdbViews(adbSetting, toggle, fakeToggle, statusValue);
                refreshWirelessAdbStatus(wirelessToggle, wirelessStatusValue, endpointValue, commandValue, copyCommand);
                boolean applied = !adbEnabled || (exitCode == 0 && String.valueOf(targetValue).equals(adbSetting));
                showToast(applied
                        ? (adbEnabled
                            ? (enabled ? "ADB 偽裝模式已開啟" : "ADB 偽裝模式已關閉")
                            : "已儲存 ADB 偽裝設定")
                        : "ADB 偽裝模式未套用，請確認 root 權限");
            });
        });
    }

    private void updateDeveloperAdbViews(String adbSetting, Switch toggle, Switch fakeToggle, TextView statusValue) {
        boolean available = adbSetting != null;
        boolean enabled = isAdbEnabled(adbSetting);
        boolean fakeEnabled = prefs.getBoolean(Config.KEY_ADB_SPOOF_ENABLED, false);
        updatingDeveloperAdbSwitch = true;
        toggle.setChecked(enabled);
        fakeToggle.setChecked(fakeEnabled);
        updatingDeveloperAdbSwitch = false;
        toggle.setEnabled(available);
        fakeToggle.setEnabled(available);
        statusValue.setText(available
                ? "adb_enabled=" + adbSetting
                : "無法取得 root 狀態");
    }

    private void syncAdbSpoofPreference(String adbSetting) {
        if ("2".equals(adbSetting) && !prefs.getBoolean(Config.KEY_ADB_SPOOF_ENABLED, false)) {
            prefs.edit().putBoolean(Config.KEY_ADB_SPOOF_ENABLED, true).commit();
        }
    }

    private void refreshAdbInstallStatus(Switch toggle) {
        toggle.setEnabled(false);
        rootExecutor.execute(() -> {
            String setting = readRootCommandOutput("settings get system adb_install_enabled");
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateAdbInstallSwitch(setting, toggle);
            });
        });
    }

    private void toggleAdbInstall(boolean enabled, Switch toggle) {
        toggle.setEnabled(false);
        rootExecutor.execute(() -> {
            int exitCode = runProcess(new ProcessBuilder(
                    "su", "-c", "settings put system adb_install_enabled " + (enabled ? 1 : 0)
            ));
            String setting = readRootCommandOutput("settings get system adb_install_enabled");
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateAdbInstallSwitch(setting, toggle);
                showToast(exitCode == 0 && String.valueOf(enabled ? 1 : 0).equals(setting)
                        ? (enabled ? "已允許 ADB 安裝" : "已關閉 ADB 安裝")
                        : "ADB 安裝設定未套用，請確認 root 權限");
            });
        });
    }

    private void updateAdbInstallSwitch(String setting, Switch toggle) {
        boolean available = setting != null;
        updatingAdbInstallSwitch = true;
        toggle.setChecked("1".equals(setting));
        updatingAdbInstallSwitch = false;
        toggle.setEnabled(available);
    }

    private boolean isAdbEnabled(String adbSetting) {
        try {
            return Integer.parseInt(adbSetting) > 0;
        } catch (NumberFormatException | NullPointerException exception) {
            return false;
        }
    }

    private String readRootCommandOutput(String command) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            String output = readFully(process.getInputStream());
            return process.waitFor() == 0 ? output.trim() : null;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private void startWirelessAdbPolling(Switch enabled, TextView statusValue, TextView endpointValue,
                                         TextView commandValue, Button copyCommand) {
        scheduleWirelessAdbPolling(enabled, statusValue, endpointValue, commandValue, copyCommand, 0);
    }

    private void restartWirelessAdbPollingAfterDelay(Switch enabled, TextView statusValue, TextView endpointValue,
                                                      TextView commandValue, Button copyCommand) {
        scheduleWirelessAdbPolling(enabled, statusValue, endpointValue, commandValue, copyCommand, 5000);
    }

    private void scheduleWirelessAdbPolling(Switch enabled, TextView statusValue, TextView endpointValue,
                                            TextView commandValue, Button copyCommand, long initialDelayMillis) {
        stopWirelessAdbPolling();
        wirelessAdbPollTask = new Runnable() {
            @Override
            public void run() {
                if (currentPage != PAGE_ADB_CONTROL) {
                    return;
                }
                refreshWirelessAdbStatus(enabled, statusValue, endpointValue, commandValue, copyCommand, false);
                mainHandler.postDelayed(this, 5000);
            }
        };
        mainHandler.postDelayed(wirelessAdbPollTask, initialDelayMillis);
    }

    private void stopWirelessAdbPolling() {
        if (wirelessAdbPollTask != null) {
            mainHandler.removeCallbacks(wirelessAdbPollTask);
            wirelessAdbPollTask = null;
        }
    }

    private void refreshWirelessAdbStatus(Switch enabled, TextView statusValue, TextView endpointValue,
                                          TextView commandValue, Button copyCommand) {
        refreshWirelessAdbStatus(enabled, statusValue, endpointValue, commandValue, copyCommand, true);
    }

    private void refreshWirelessAdbStatus(Switch enabled, TextView statusValue, TextView endpointValue,
                                          TextView commandValue, Button copyCommand, boolean showLoading) {
        if (showLoading) {
            enabled.setEnabled(false);
            statusValue.setText("讀取中...");
        }
        rootExecutor.execute(() -> {
            WirelessAdbStatus status = readWirelessAdbStatus();
            boolean shouldRestore = prefs.getBoolean(Config.KEY_WIRELESS_ADB_ENABLED, false)
                    && status.available
                    && !status.enabled
                    && isAdbEnabled(readRootCommandOutput("settings get global adb_enabled"));
            if (shouldRestore) {
                int port = prefs.getInt(Config.KEY_WIRELESS_ADB_PORT, Config.DEFAULT_WIRELESS_ADB_PORT);
                applyWirelessAdbCommand(wirelessAdbCommand(port));
                status = readWirelessAdbStatus();
            }
            WirelessAdbStatus result = status;
            String ipAddress = wirelessIpv4Address();
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateWirelessAdbViews(result, ipAddress, enabled, statusValue, endpointValue, commandValue, copyCommand);
            });
        });
    }

    private int parseWirelessAdbPort(EditText input) {
        try {
            int port = Integer.parseInt(String.valueOf(input.getText()).trim());
            return port >= 1 && port <= 65535 ? port : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private void applyWirelessAdbPort(int port, Switch enabled, TextView statusValue, TextView endpointValue,
                                      TextView commandValue, Button copyCommand) {
        enabled.setEnabled(false);
        statusValue.setText("套用中...");
        rootExecutor.execute(() -> {
            WirelessAdbStatus current = readWirelessAdbStatus();
            int exitCode = current.enabled
                    ? applyWirelessAdbCommand(wirelessAdbCommand(port))
                    : 0;
            WirelessAdbStatus status = readWirelessAdbStatus();
            String ipAddress = wirelessIpv4Address();
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateWirelessAdbViews(status, ipAddress, enabled, statusValue, endpointValue, commandValue, copyCommand);
                boolean applied = !current.enabled || (status.enabled && String.valueOf(port).equals(status.port));
                showToast(exitCode == 0 && applied
                        ? (current.enabled ? "無線 ADB 已改為連接埠 " + port : "已儲存連接埠 " + port)
                        : "連接埠未套用，請確認 root 與 adbd 狀態");
            });
        });
    }

    private void toggleWirelessAdb(boolean enabled, int port, Switch toggle, TextView statusValue, TextView endpointValue,
                                   TextView commandValue, Button copyCommand) {
        toggle.setEnabled(false);
        statusValue.setText("套用中...");
        String command = enabled
                ? wirelessAdbCommand(port)
                : "setprop service.adb.tcp.port ''; setprop ctl.restart adbd";
        rootExecutor.execute(() -> {
            int exitCode = applyWirelessAdbCommand(command);
            WirelessAdbStatus status = readWirelessAdbStatus();
            String ipAddress = wirelessIpv4Address();
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateWirelessAdbViews(status, ipAddress, toggle, statusValue, endpointValue, commandValue, copyCommand);
                boolean applied = enabled
                        ? status.enabled && String.valueOf(port).equals(status.port)
                        : !status.enabled;
                showToast(exitCode == 0 && applied
                        ? (status.enabled ? "無線 ADB 已開啟，連接埠 " + status.port : "無線 ADB 已關閉")
                        : "無線 ADB 未套用，請確認 root 與 adbd 狀態");
            });
        });
    }

    private String wirelessAdbCommand(int port) {
        return "setprop service.adb.tcp.port " + port + "; setprop ctl.restart adbd";
    }

    private int applyWirelessAdbCommand(String command) {
        int exitCode = runProcess(new ProcessBuilder("su", "-c", command));
        try {
            Thread.sleep(1500);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return -1;
        }
        return exitCode;
    }

    private WirelessAdbStatus readWirelessAdbStatus() {
        try {
            Process process = new ProcessBuilder("su", "-c", "getprop service.adb.tcp.port; getprop init.svc.adbd")
                    .redirectErrorStream(true)
                    .start();
            String output = readFully(process.getInputStream());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return WirelessAdbStatus.unavailable();
            }
            String[] lines = output.trim().split("\\n");
            String port = lines.length > 0 ? lines[0].trim() : "";
            String daemonState = lines.length > 1 ? lines[1].trim() : "";
            return new WirelessAdbStatus(port, daemonState, true);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return WirelessAdbStatus.unavailable();
        }
    }

    private void updateWirelessAdbViews(WirelessAdbStatus status, String ipAddress, Switch enabled,
                                        TextView statusValue, TextView endpointValue,
                                        TextView commandValue, Button copyCommand) {
        boolean desiredEnabled = prefs.getBoolean(Config.KEY_WIRELESS_ADB_ENABLED, false);
        updatingWirelessAdbSwitch = true;
        enabled.setChecked(desiredEnabled);
        updatingWirelessAdbSwitch = false;
        enabled.setEnabled(status.available);
        statusValue.setText(status.available ? (status.enabled ? "已開啟，adbd 正在執行" : "已關閉") : "無法取得 root 狀態");
        String endpoint = status.enabled && !"未連線 Wi-Fi".equals(ipAddress)
                ? ipAddress + ":" + status.port
                : "-";
        endpointValue.setText(endpoint);
        String command = !"-".equals(endpoint)
                ? "adb connect " + endpoint
                : "-";
        commandValue.setText(command);
        copyCommand.setEnabled(!"-".equals(command));
    }

    private String wirelessIpv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "未連線 Wi-Fi";
    }

    private static final class WirelessAdbStatus {
        final String port;
        final String daemonState;
        final boolean available;
        final boolean enabled;

        WirelessAdbStatus(String port, String daemonState, boolean available) {
            this.port = port;
            this.daemonState = daemonState;
            this.available = available;
            this.enabled = available && "running".equals(daemonState) && port.matches("[1-9][0-9]*");
        }

        static WirelessAdbStatus unavailable() {
            return new WirelessAdbStatus("", "", false);
        }
    }

    private void applyLauncherComponent(String component, String launcherName) {
        if (component == null || component.isEmpty() || isSettingsFallbackHome(component)) {
            ComponentName stockLauncher = stockLauncherComponent();
            component = stockLauncher.flattenToString();
            launcherName = launcherLabel(component);
            prefs.edit()
                    .putString(Config.KEY_LAUNCHER_COMPONENT, component)
                    .putString(Config.KEY_LAUNCHER_PACKAGE, stockLauncher.getPackageName())
                    .commit();
        }
        String commandComponent = ComponentName.unflattenFromString(component) == null
                ? component
                : ComponentName.unflattenFromString(component).flattenToShortString();
        runHomeCommand(
                "cmd package set-home-activity --user 0 " + shellQuote(commandComponent),
                "已將 " + launcherName + " 設為預設啟動器",
                "套用預設啟動器失敗"
        );
    }

    private List<ResolveInfo> installedLaunchers() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> launchers = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        Collections.sort(launchers, Comparator.comparing(info -> {
            CharSequence label = info.loadLabel(getPackageManager());
            return label == null ? info.activityInfo.packageName : label.toString();
        }, String.CASE_INSENSITIVE_ORDER));
        return launchers;
    }

    private ComponentName stockLauncherComponent() {
        return new ComponentName(STOCK_LAUNCHER_PACKAGE, STOCK_LAUNCHER_CLASS);
    }

    private boolean isSettingsFallbackHome(String flattenedComponent) {
        ComponentName componentName = ComponentName.unflattenFromString(flattenedComponent);
        return componentName != null
                && isSettingsFallbackHome(componentName.getPackageName(), componentName.getClassName());
    }

    private boolean isSettingsFallbackHome(String packageName, String className) {
        return SETTINGS_FALLBACK_HOME_PACKAGE.equals(packageName)
                && SETTINGS_FALLBACK_HOME_CLASS.equals(className);
    }

    private LinearLayout launcherCard(String title, String description, String component, String packageName, Drawable icon) {
        boolean selected = component.equals(prefs.getString(Config.KEY_LAUNCHER_COMPONENT, ""));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dp(8), 0, dp(4));
        card.setLayoutParams(cardParams);
        card.setBackground(cardBackground(selected ? CARD_SELECTED_COLOR : CARD_COLOR));
        card.setClickable(true);
        card.setOnClickListener(view -> {
            prefs.edit()
                    .putString(Config.KEY_LAUNCHER_COMPONENT, component)
                    .putString(Config.KEY_LAUNCHER_PACKAGE, packageName)
                    .commit();
            applyLauncherComponent(component, title);
            showLauncherPage();
        });

        if (icon != null) {
            ImageView image = new ImageView(this);
            image.setImageDrawable(icon);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            imageParams.setMargins(0, 0, dp(12), 0);
            image.setLayoutParams(imageParams);
            card.addView(image);
        }

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        labels.addView(text(title, 15, true));
        TextView descriptionView = detailText(description);
        descriptionView.setTextSize(12);
        descriptionView.setTextColor(Color.rgb(190, 196, 205));
        labels.addView(descriptionView);
        card.addView(labels);

        TextView selectedMarker = text(selected ? "●" : "", 18, true);
        selectedMarker.setTextColor(Color.WHITE);
        selectedMarker.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(28), dp(44));
        markerParams.setMargins(dp(8), 0, 0, 0);
        selectedMarker.setLayoutParams(markerParams);
        card.addView(selectedMarker);
        return card;
    }

    private String launcherLabel(String component) {
        if (component == null || component.isEmpty()) {
            return "未選擇";
        }
        ComponentName componentName = ComponentName.unflattenFromString(component);
        if (componentName == null) {
            return component;
        }
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(componentName.getPackageName(), 0);
            return info.loadLabel(getPackageManager()) + " (" + componentName.flattenToShortString() + ")";
        } catch (PackageManager.NameNotFoundException exception) {
            return componentName.flattenToShortString();
        }
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
        styleButton(button, assistantTab == tab, true);
        button.setOnClickListener(view -> {
            if (assistantTab == tab) {
                return;
            }
            assistantTab = tab;
            showAssistantPage();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void addSystemActions(LinearLayout box) {
        box.addView(targetCard("小助手", "啟動系統預設 android.intent.action.ASSIST", Config.TARGET_PREFIX_ACTION + Config.ACTION_DEFAULT_ASSIST, null, null));
        box.addView(targetCard("語音助手", "啟動 android.intent.action.VOICE_COMMAND，實際助手依使用者系統設定", Config.TARGET_PREFIX_ACTION + Config.ACTION_GOOGLE_VOICE, null, null));
        box.addView(targetCard("最近應用", "送出 KEYCODE_APP_SWITCH", Config.TARGET_PREFIX_ACTION + Config.ACTION_RECENTS, null, null));
        box.addView(targetCard("螢幕截圖", "送出 KEYCODE_SYSRQ", Config.TARGET_PREFIX_ACTION + Config.ACTION_SCREENSHOT, null, null));
        box.addView(targetCard("手電筒", "透過 CameraManager 切換背面閃光燈", Config.TARGET_PREFIX_ACTION + Config.ACTION_FLASHLIGHT, null, null));
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void addAppList(LinearLayout box, boolean systemApps) {
        List<ApplicationInfo> apps = installedApps(systemApps);
        box.addView(text((systemApps ? "系統應用" : "使用者應用") + "：" + apps.size() + " 個", 14, true));
        for (ApplicationInfo app : apps) {
            String label = String.valueOf(app.loadLabel(getPackageManager()));
            box.addView(targetCard(label, app.packageName, Config.TARGET_PREFIX_APP + app.packageName, app.loadIcon(getPackageManager()), null));
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

    private LinearLayout targetCard(String title, String description, String target, Drawable icon, String fallbackIconText) {
        boolean selected = target.equals(prefs.getString(Config.KEY_ASSISTANT_TARGET, Config.ASSISTANT_TARGET_DEFAULT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, dp(8), 0, dp(4));
        card.setLayoutParams(cardParams);
        card.setBackground(cardBackground(selected ? CARD_SELECTED_COLOR : CARD_COLOR));
        card.setClickable(true);
        card.setOnClickListener(view -> {
            prefs.edit().putString(Config.KEY_ASSISTANT_TARGET, target).commit();
            showToast("已選擇：" + title);
            showAssistantPage();
        });

        if (icon != null) {
            ImageView image = new ImageView(this);
            image.setImageDrawable(icon);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            imageParams.setMargins(0, 0, dp(12), 0);
            image.setLayoutParams(imageParams);
            card.addView(image);
        } else if (fallbackIconText != null && !fallbackIconText.isEmpty()) {
            TextView fallback = text(fallbackIconText == null ? "•" : fallbackIconText, 18, true);
            fallback.setGravity(Gravity.CENTER);
            fallback.setBackground(cardBackground(Color.rgb(42, 48, 58)));
            LinearLayout.LayoutParams fallbackParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            fallbackParams.setMargins(0, 0, dp(12), 0);
            fallback.setLayoutParams(fallbackParams);
            card.addView(fallback);
        }

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = text(title, 15, true);
        labels.addView(titleView);
        TextView descriptionView = detailText(description);
        descriptionView.setTextSize(12);
        descriptionView.setTextColor(Color.rgb(190, 196, 205));
        labels.addView(descriptionView);
        card.addView(labels);

        TextView selectedMarker = text(selected ? "●" : "", 18, true);
        selectedMarker.setTextColor(Color.WHITE);
        selectedMarker.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(dp(28), dp(44));
        markerParams.setMargins(dp(8), 0, 0, 0);
        selectedMarker.setLayoutParams(markerParams);
        card.addView(selectedMarker);

        return card;
    }

    private String assistantTargetLabel(String target) {
        if (target == null || target.isEmpty() || Config.ASSISTANT_TARGET_DEFAULT.equals(target)) {
            return "小助手";
        }
        if (Config.ASSISTANT_TARGET_GOOGLE_VOICE.equals(target)) {
            return "語音助手";
        }
        if (Config.ASSISTANT_TARGET_CHATGPT.equals(target)) {
            return "ChatGPT";
        }
        if (target.startsWith(Config.TARGET_PREFIX_ACTION)) {
            String action = target.substring(Config.TARGET_PREFIX_ACTION.length());
            if (Config.ACTION_DEFAULT_ASSIST.equals(action)) return "小助手";
            if (Config.ACTION_GOOGLE_VOICE.equals(action)) return "語音助手";
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
        popup.getMenu().add("設定");
        popup.setOnMenuItemClickListener(item -> {
            if ("關於".contentEquals(item.getTitle())) {
                showAboutDialog();
            } else if ("設定".contentEquals(item.getTitle())) {
                showSettingsDialog();
            }
            return true;
        });
        popup.show();
    }

    private void showSettingsDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), 0);

        Switch autoCheck = new Switch(this);
        autoCheck.setText("自動檢測更新");
        autoCheck.setTextSize(16);
        autoCheck.setTextColor(Color.WHITE);
        autoCheck.setChecked(prefs.getBoolean(Config.KEY_AUTO_CHECK_UPDATES, true));
        autoCheck.setOnCheckedChangeListener((buttonView, checked) ->
                prefs.edit().putBoolean(Config.KEY_AUTO_CHECK_UPDATES, checked).apply());
        content.addView(autoCheck);

        TextView description = detailText("開啟後，啟動 App 時會檢查 GitHub 最新正式 Release。偵測到新版本時會顯示通知。");
        description.setPadding(0, 0, 0, dp(12));
        content.addView(description);

        Button checkNow = new Button(this);
        checkNow.setText("手動檢查更新");
        styleButton(checkNow, false, false);
        checkNow.setOnClickListener(view -> {
            checkNow.setEnabled(false);
            checkForUpdates(true, () -> checkNow.setEnabled(true));
        });
        content.addView(checkNow);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("設定")
                .setView(content)
                .setPositiveButton("關閉", null)
                .create();
        showThemedDialog(dialog);
    }

    private void checkForUpdates(boolean userInitiated) {
        checkForUpdates(userInitiated, null);
    }

    private void checkForUpdates(boolean userInitiated, Runnable completed) {
        updateExecutor.execute(() -> {
            ReleaseInfo release = null;
            String error = null;
            try {
                release = fetchLatestRelease();
            } catch (IOException exception) {
                error = "無法連線 GitHub";
            }

            ReleaseInfo result = release;
            String failure = error;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (completed != null) {
                    completed.run();
                }
                if (failure != null) {
                    if (userInitiated) showToast(failure);
                    return;
                }
                if (isNewerVersion(result.tagName, versionName())) {
                    showUpdateDialog(result);
                } else if (userInitiated) {
                    showToast("已是最新版本 " + versionName());
                }
            });
        });
    }

    private ReleaseInfo fetchLatestRelease() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASES_LATEST_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "RedMagicX-Android");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub response error");
            }
            String body = readFully(connection.getInputStream());
            Matcher matcher = JSON_STRING_FIELD.matcher(body);
            String tagName = null;
            String htmlUrl = null;
            while (matcher.find()) {
                if ("tag_name".equals(matcher.group(1))) tagName = matcher.group(2);
                if ("html_url".equals(matcher.group(1))) htmlUrl = matcher.group(2);
            }
            if (tagName == null || htmlUrl == null) throw new IOException("Malformed GitHub response");
            return new ReleaseInfo(tagName, htmlUrl);
        } finally {
            connection.disconnect();
        }
    }

    private String readFully(InputStream input) throws IOException {
        StringBuilder output = new StringBuilder();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
        }
        input.close();
        return output.toString();
    }

    private boolean isNewerVersion(String latestTag, String installedVersion) {
        String[] latest = latestTag.replaceFirst("^[vV]", "").split("\\.");
        String[] installed = installedVersion.replaceFirst("^[vV]", "").split("\\.");
        int length = Math.max(latest.length, installed.length);
        for (int i = 0; i < length; i++) {
            int left = versionPart(latest, i);
            int right = versionPart(installed, i);
            if (left != right) return left > right;
        }
        return false;
    }

    private int versionPart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        Matcher matcher = Pattern.compile("\\d+").matcher(parts[index]);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }

    private void showUpdateDialog(ReleaseInfo release) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("發現新版本")
                .setMessage("RedMagicX " + release.tagName + " 已發布，目前版本為 " + versionName())
                .setNegativeButton("稍後", null)
                .setPositiveButton("前往下載", (ignoredDialog, which) -> {
                    Intent browser = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(release.htmlUrl));
                    startActivity(browser);
                })
                .create();
        showThemedDialog(dialog);
    }

    private static final class ReleaseInfo {
        final String tagName;
        final String htmlUrl;

        ReleaseInfo(String tagName, String htmlUrl) {
            this.tagName = tagName;
            this.htmlUrl = htmlUrl;
        }
    }

    private void showAboutDialog() {
        String message = "作者：XPRAMT\nGitHub：https://github.com/XPRAMT/RedMagicX\n版本：" + versionName();
        SpannableString spannable = new SpannableString(message);
        String url = "https://github.com/XPRAMT/RedMagicX";
        int start = message.indexOf(url);
        if (start >= 0) {
            spannable.setSpan(new URLSpan(url), start, start + url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView content = text("", 14, false);
        content.setText(spannable);
        content.setLinkTextColor(Color.rgb(156, 199, 255));
        content.setMovementMethod(LinkMovementMethod.getInstance());
        content.setPadding(dp(24), dp(8), dp(24), 0);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("關於")
                .setView(content)
                .setPositiveButton("確定", null)
                .create();
        showThemedDialog(dialog);
    }

    private void showThemedDialog(AlertDialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        }

        TextView title = dialog.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(Color.WHITE);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(Color.WHITE);
        }
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), true);
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), false);
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), false);
    }

    private void styleDialogButton(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setBackground(cardBackground(selected ? CARD_SELECTED_COLOR : CARD_COLOR));
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            return "unknown";
        }
    }

    private LinearLayout sectionSwitch(String title, String description, String process, String parameter, String key) {
        LinearLayout box = sectionBox();
        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextSize(18);
        sw.setTextColor(Color.WHITE);
        sw.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sw.setChecked(prefs.getBoolean(key, true));
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).commit();
            showToast("已保存 LSPosed 設定：" + title);
        });
        box.addView(sw);
        box.addView(detailText(description));
        addDetailField(box, "作用進程", process);
        addDetailField(box, "參數", parameter);
        return box;
    }

    private LinearLayout styleSection() {
        LinearLayout box = sectionBox();
        box.addView(text("VoWiFi 圖標樣式", 18, true));
        box.addView(detailText("控制 SystemUI 選用哪一套 VoWiFi / VoLTE 圖標。GEN_BD 模式會讀取下列參數；Hook array 模式則直接替換 IMS icon array。"));
        addDetailField(box, "作用進程", "com.android.systemui");
        addDetailField(box, "參數", "persist.custom.variant.id=GEN_BD");

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        addRadio(group, Config.STYLE_DEFAULT, "預設：不改 persist.custom.variant.id，通常使用 vowifi / vowifi_card1/2/12");
        addRadio(group, Config.STYLE_GEN_BD, "GEN_BD：等效 persist.custom.variant.id=GEN_BD，使用 bd_stat_vowifi / bd_vowifi_card1/2/12");
        addRadio(group, Config.STYLE_ARRAY_HOOK, "Hook array：替換 ImsUpdateFeature 的 IMS icon array；已實測雙卡可用，依賴目前 ROM 方法名");

        String current = prefs.getString(Config.KEY_ICON_STYLE, Config.STYLE_GEN_BD);
        int checkedId = styleToId(current);
        if (checkedId != 0) {
            group.check(checkedId);
        }
        group.setOnCheckedChangeListener((radioGroup, checked) -> {
            prefs.edit().putString(Config.KEY_ICON_STYLE, idToStyle(checked)).commit();
            showToast("已保存 LSPosed 設定：VoWiFi 圖標樣式");
        });
        box.addView(group);
        return box;
    }

    private LinearLayout actionSection() {
        LinearLayout box = sectionBox();
        box.addView(text("重啟", 18, true));
        box.addView(detailText("開關變更會自動保存到 LSPosed 設定。重啟 Settings / SystemUI 後才會重新讀取設定；重啟操作需要 root 權限。"));

        Button restartSettings = new Button(this);
        restartSettings.setText("重啟 Settings");
        styleButton(restartSettings, false, false);
        restartSettings.setOnClickListener(view -> runRootCommand(
                "am force-stop com.android.settings",
                "已執行：am force-stop com.android.settings",
                "重啟 Settings 失敗"
        ));
        box.addView(restartSettings);

        Button restartSystemUi = new Button(this);
        restartSystemUi.setText("重啟 SystemUI");
        styleButton(restartSystemUi, false, false);
        restartSystemUi.setOnClickListener(view -> runRootCommand(
                "kill -9 $(pidof com.android.systemui)",
                "已執行：kill -9 $(pidof com.android.systemui)",
                "重啟 SystemUI 失敗"
        ));
        box.addView(restartSystemUi);

        Button restartBoth = new Button(this);
        restartBoth.setText("重啟 Settings + SystemUI");
        styleButton(restartBoth, false, false);
        restartBoth.setOnClickListener(view -> runRootCommand(
                "am force-stop com.android.settings; kill -9 $(pidof com.android.systemui)",
                "已重啟 Settings + SystemUI",
                "重啟失敗"
        ));
        box.addView(restartBoth);

        return box;
    }

    private void styleButton(Button button, boolean selected, boolean compact) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(compact ? 13 : 14);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setMinimumHeight(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(cardBackground(selected ? CARD_SELECTED_COLOR : CARD_COLOR));
        if (button.getLayoutParams() == null) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(compact ? 44 : 48)
            );
            params.setMargins(0, dp(10), 0, 0);
            button.setLayoutParams(params);
        }
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

    private TextView detailText(String value) {
        TextView textView = text(value, 13, false);
        textView.setGravity(Gravity.START);
        textView.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
        return textView;
    }

    private void addDetailField(LinearLayout box, String label, String value) {
        TextView labelView = text(label, 14, true);
        labelView.setPadding(0, dp(10), 0, 0);
        box.addView(labelView);
        box.addView(detailText(value));
    }

    private View verticalSpace(int dp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(dp)
        ));
        return view;
    }

    private GradientDrawable cardBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(1, Color.rgb(45, 51, 60));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void runHomeCommand(String command, String successMessage, String errorMessage) {
        int rootExitCode = runProcess(new ProcessBuilder("su", "-c", command));
        if (rootExitCode == 0) {
            showToast(successMessage + "（root）");
            return;
        }
        if (!isShizukuAvailable()) {
            showToast(errorMessage + "：無 root，且 Shizuku 未連線");
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            pendingHomeCommand = command;
            pendingHomeSuccessMessage = successMessage;
            pendingHomeErrorMessage = errorMessage;
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE_HOME);
            showToast("請授權 Shizuku 後自動套用預設啟動器");
            return;
        }
        runHomeCommandWithShizuku(command, successMessage, errorMessage);
    }

    private void runHomeCommandWithShizuku(String command, String successMessage, String errorMessage) {
        try {
            Method newProcess = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
            Process process = (Process) newProcess.invoke(null,
                    new String[]{"sh", "-c", command}, null, null);
            int exitCode = process.waitFor();
            showToast(exitCode == 0
                    ? successMessage + "（Shizuku）"
                    : errorMessage + "（Shizuku " + exitCode + "）");
        } catch (Throwable throwable) {
            showToast(errorMessage + "：Shizuku 執行失敗");
        }
    }

    private boolean isShizukuAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int runProcess(ProcessBuilder processBuilder) {
        try {
            Process process = processBuilder.redirectErrorStream(true).start();
            return process.waitFor();
        } catch (IOException exception) {
            return -1;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private void runRootCommand(String command, String successMessage, String errorMessage) {
        try {
            Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            int exitCode = process.waitFor();
            showToast(exitCode == 0 ? successMessage : errorMessage + " (" + exitCode + ")");
        } catch (IOException exception) {
            showToast(errorMessage + "：無法取得 root");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            showToast(errorMessage + "：執行中斷");
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

}

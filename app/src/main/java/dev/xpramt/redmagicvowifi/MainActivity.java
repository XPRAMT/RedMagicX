package dev.xpramt.redmagicvowifi;

import android.app.Activity;
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
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
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
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int PAGE_HOME = 0;
    private static final int PAGE_VOWIFI = 1;
    private static final int PAGE_VOLUME = 2;
    private static final int PAGE_ASSISTANT = 3;
    private static final int PAGE_LAUNCHER = 4;
    private static final int PAGE_THEME = 5;
    private static final int PAGE_ADB_CONTROL = 6;
    private static final int PAGE_QUICK_ENTRY = 7;
    private static final int PAGE_OTHER = 8;
    private static final int PAGE_BT_RECEIVER = 9;
    private static final int MIN_SCREEN_OFF_MINUTES = 1;
    private static final int MAX_SCREEN_OFF_MINUTES = 60;
    private static final int DEFAULT_SCREEN_OFF_MINUTES = 10;
    private static final String SETTINGS_FALLBACK_HOME_PACKAGE = "com.android.settings";
    private static final String SETTINGS_FALLBACK_HOME_CLASS = "com.android.settings.FallbackHome";
    private static final String STOCK_LAUNCHER_PACKAGE = "com.zte.mifavor.launcher";
    private static final String STOCK_LAUNCHER_CLASS = "com.android.launcher3.uioverrides.QuickstepLauncher";
    private static final String STOCK_DIALER_PACKAGE = "com.android.contacts";
    private static final String STOCK_DIALER_CLASS = "com.android.contacts.activities.DialtactsActivity";
    private static final int SHIZUKU_REQUEST_CODE_HOME = 1001;
    private static final int SHIZUKU_REQUEST_CODE_QUICK_ENTRY = 1002;
    private static final int SHIZUKU_REQUEST_CODE_SCREEN_TIMEOUT = 1003;
    private static final int SHIZUKU_REQUEST_CODE_BT_RESTART = 1004;
    // Restart the Bluetooth stack only when it is on; wait for the real adapter states with a time limit.
    private static final String BT_RESTART_COMMAND =
            "if [ \"$(settings get global bluetooth_on)\" = \"1\" ]; then "
                    + "cmd bluetooth_manager disable; "
                    + "timeout 25 cmd bluetooth_manager wait-for-state:STATE_OFF; "
                    + "sleep 2; "
                    + "cmd bluetooth_manager enable; "
                    + "timeout 40 cmd bluetooth_manager wait-for-state:STATE_ON; "
                    + "fi";
    private static final String RELEASES_LATEST_URL = "https://api.github.com/repos/XPRAMT/RedMagicX/releases/latest";
    private static final String RELEASES_PAGE_URL = "https://github.com/XPRAMT/RedMagicX/releases";
    private static final Pattern JSON_STRING_FIELD = Pattern.compile("\\\"(tag_name|html_url)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private SharedPreferences prefs;
    private LinearLayout screen;
    private LinearLayout contentRoot;
    private MaterialToolbar toolbar;
        private int currentPage = PAGE_HOME;
    private int assistantTab = 0;
    private String pendingHomeCommand;
    private String pendingHomeSuccessMessage;
    private String pendingHomeErrorMessage;
    private String pendingQuickEntryCommand;
    private String pendingQuickEntrySuccessMessage;
    private String pendingScreenTimeoutCommand;
    private int pendingScreenTimeoutMinutes;
    private Slider pendingScreenTimeoutSeekBar;
    private TextView pendingScreenTimeoutValueLabel;
    private boolean pendingBtRestart;
    private boolean btRestartRunning;
    private TextView btStatusView;
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
    private final Shizuku.OnRequestPermissionResultListener screenTimeoutShizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_REQUEST_CODE_SCREEN_TIMEOUT) {
                    return;
                }
                String command = pendingScreenTimeoutCommand;
                int minutes = pendingScreenTimeoutMinutes;
                Slider seekBar = pendingScreenTimeoutSeekBar;
                TextView valueLabel = pendingScreenTimeoutValueLabel;
                clearPendingScreenTimeout();
                if (grantResult == PackageManager.PERMISSION_GRANTED && command != null) {
                    runScreenTimeoutWithShizuku(command, minutes, seekBar, valueLabel);
                } else {
                    finishScreenTimeoutApply(seekBar, valueLabel, "螢幕亮屏時間套用失敗：Shizuku 未授權");
                }
            };

    private final Shizuku.OnRequestPermissionResultListener btRestartShizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_REQUEST_CODE_BT_RESTART) {
                    return;
                }
                boolean pending = pendingBtRestart;
                pendingBtRestart = false;
                if (grantResult == PackageManager.PERMISSION_GRANTED && pending) {
                    runBtRestartWithShizuku();
                } else {
                    finishBtRestart("藍牙重啟失敗：Shizuku 未授權，請手動把藍牙關閉再開啟");
                }
            };
    // Updates the receiver page when the Bluetooth process reports the mode it started in.
    private final SharedPreferences.OnSharedPreferenceChangeListener btStatusPrefsListener =
            (sharedPreferences, key) -> {
                if (Config.KEY_BT_ACTIVE_MODE.equals(key) || Config.KEY_BT_ACTIVE_AT.equals(key)) {
                    mainHandler.post(this::refreshBtStatus);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);
        prefs = Config.appPrefs(this);
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        Shizuku.addRequestPermissionResultListener(quickEntryShizukuPermissionListener);
        Shizuku.addRequestPermissionResultListener(screenTimeoutShizukuPermissionListener);
        Shizuku.addRequestPermissionResultListener(btRestartShizukuPermissionListener);
        prefs.registerOnSharedPreferenceChangeListener(btStatusPrefsListener);
        ensureDefaults();
        setContentView(R.layout.activity_main);
        screen = findViewById(R.id.screen);
        contentRoot = findViewById(R.id.content);
        setupToolbar();
        // Edge to edge: the page owns the system bar insets.
        ViewCompat.setOnApplyWindowInsetsListener(screen, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
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
        Shizuku.removeRequestPermissionResultListener(screenTimeoutShizukuPermissionListener);
        Shizuku.removeRequestPermissionResultListener(btRestartShizukuPermissionListener);
        prefs.unregisterOnSharedPreferenceChangeListener(btStatusPrefsListener);
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
        if (!prefs.contains(Config.KEY_VOLUME_BROADCAST_UNDELAY)) {
            editor.putBoolean(Config.KEY_VOLUME_BROADCAST_UNDELAY, true);
            changed = true;
        }
        if (!prefs.contains(Config.KEY_AOSP_VOLUME_DIALOG)) {
            editor.putBoolean(Config.KEY_AOSP_VOLUME_DIALOG, false);
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
        if (!prefs.contains(Config.KEY_THEME_SELECT_ENABLED)) {
            editor.putBoolean(Config.KEY_THEME_SELECT_ENABLED, false);
            editor.putBoolean(Config.KEY_THEME_FULL, false);
            editor.putBoolean(Config.KEY_THEME_BEAUTIFY_THIRD_PARTY_ICONS, false);
            editor.putBoolean(Config.KEY_THEME_LOCKSCREEN, false);
            editor.putBoolean(Config.KEY_THEME_TYPE_1, true);
            editor.putBoolean(Config.KEY_THEME_TYPE_2, true);
            editor.putBoolean(Config.KEY_THEME_TYPE_4, false);
            editor.putBoolean(Config.KEY_THEME_TYPE_6, true);
            editor.putBoolean(Config.KEY_THEME_TYPE_16, false);
            editor.putBoolean(Config.KEY_THEME_TYPE_17, true);
            editor.putBoolean(Config.KEY_THEME_TYPE_23, true);
            changed = true;
        }
        if (!prefs.contains(Config.KEY_SKIP_BEAUTIFY_SPLASH)) {
            editor.putBoolean(Config.KEY_SKIP_BEAUTIFY_SPLASH, false);
            changed = true;
        }
        if (!prefs.contains(Config.KEY_REMOVE_TRIAL_LIMIT)) {
            editor.putBoolean(Config.KEY_REMOVE_TRIAL_LIMIT, false);
            changed = true;
        }
        if (!prefs.contains(Config.KEY_THEME_FULL)) {
            editor.putBoolean(Config.KEY_THEME_FULL, false);
            changed = true;
        }
        if (!prefs.contains(Config.KEY_THEME_BEAUTIFY_THIRD_PARTY_ICONS)) {
            editor.putBoolean(Config.KEY_THEME_BEAUTIFY_THIRD_PARTY_ICONS, false);
            changed = true;
        }
        if (!prefs.contains(Config.KEY_THEME_LOCKSCREEN)) {
            editor.putBoolean(Config.KEY_THEME_LOCKSCREEN, false);
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

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationContentDescription("返回");
        toolbar.setNavigationOnClickListener(view -> showHome());
        toolbar.getMenu().add("關於").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.getMenu().add("設定").setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.setOnMenuItemClickListener(item -> {
            if ("關於".contentEquals(item.getTitle())) {
                showAboutDialog();
            } else if ("設定".contentEquals(item.getTitle())) {
                showSettingsDialog();
            }
            return true;
        });
    }

    private void setHeader(String title, boolean showBack) {
        toolbar.setTitle(title);
        if (showBack) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        } else {
            toolbar.setNavigationIcon(null);
        }
    }

    private void showHome() {
        stopWirelessAdbPolling();
        currentPage = PAGE_HOME;
        setHeader(getString(R.string.app_name), false);
        View page = inflatePage(R.layout.page_home);
        onClick(page, R.id.home_vowifi, view -> showVoWifiPage());
        onClick(page, R.id.home_volume, view -> showVolumePage());
        onClick(page, R.id.home_bt, view -> showBtReceiverPage());
        onClick(page, R.id.home_assistant, view -> showAssistantPage());
        onClick(page, R.id.home_launcher, view -> showLauncherPage());
        onClick(page, R.id.home_theme, view -> showThemePage());
        onClick(page, R.id.home_adb, view -> showAdbControlPage());
        onClick(page, R.id.home_quick, view -> showQuickEntryPage());
        onClick(page, R.id.home_other, view -> showOtherPage());
    }

    /** Replaces the page content with a layout from res/layout and returns it. */
    private View inflatePage(int layout) {
        contentRoot.removeAllViews();
        View page = getLayoutInflater().inflate(layout, contentRoot, false);
        contentRoot.addView(page);
        return page;
    }

    private void onClick(View page, int id, View.OnClickListener listener) {
        page.findViewById(id).setOnClickListener(listener);
    }

    /** A switch that mirrors one boolean preference and writes it on every change. */
    private MaterialSwitch bindSwitch(View page, int id, String key, boolean defaultValue,
                                      String onMessage, String offMessage) {
        MaterialSwitch toggle = page.findViewById(id);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).commit();
            showToast(isChecked ? onMessage : offMessage);
        });
        return toggle;
    }

    private MaterialSwitch bindSwitch(View page, int id, String key, boolean defaultValue, String message) {
        return bindSwitch(page, id, key, defaultValue, message, message);
    }

    private void showVoWifiPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_VOWIFI;
        setHeader(getString(R.string.home_vowifi_title), true);
        View page = inflatePage(R.layout.page_vowifi);

        bindSwitch(page, R.id.vowifi_wfc_switch, Config.KEY_ENABLE_WFC_SETTINGS, true,
                "已保存 LSPosed 設定：" + getString(R.string.vowifi_wfc));
        onClick(page, R.id.vowifi_open_wfc, view -> openWifiCallingSettings());
        bindSwitch(page, R.id.vowifi_icon_switch, Config.KEY_ENABLE_STATUS_ICON, true,
                "已保存 LSPosed 設定：" + getString(R.string.vowifi_icon));

        RadioGroup styleGroup = page.findViewById(R.id.vowifi_style_group);
        int checkedId = styleToId(prefs.getString(Config.KEY_ICON_STYLE, Config.STYLE_GEN_BD));
        styleGroup.check(checkedId);
        styleGroup.setOnCheckedChangeListener((radioGroup, checked) -> {
            prefs.edit().putString(Config.KEY_ICON_STYLE, idToStyle(checked)).commit();
            showToast("已保存 LSPosed 設定：VoWiFi 圖標樣式");
        });

        onClick(page, R.id.vowifi_restart_settings, view -> runRootCommand(
                "am force-stop com.android.settings",
                "已執行：am force-stop com.android.settings",
                "重啟 Settings 失敗"
        ));
        onClick(page, R.id.vowifi_restart_systemui, view -> runRootCommand(
                "kill -9 $(pidof com.android.systemui)",
                "已執行：kill -9 $(pidof com.android.systemui)",
                "重啟 SystemUI 失敗"
        ));
        onClick(page, R.id.vowifi_restart_both, view -> runRootCommand(
                "am force-stop com.android.settings; kill -9 $(pidof com.android.systemui)",
                "已重啟 Settings + SystemUI",
                "重啟失敗"
        ));
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
        setHeader(getString(R.string.home_volume_title), true);
        View page = inflatePage(R.layout.page_volume);

        bindSwitch(page, R.id.volume_step_switch, Config.KEY_VOLUME_STEP_ENABLED, false, "已寫入音量步進開關");
        TextView stepLabel = page.findViewById(R.id.volume_step_label);
        Slider seekBar = page.findViewById(R.id.volume_step_slider);
        seekBar.setValue(Config.clampVolumeStep(prefs.getInt(Config.KEY_VOLUME_STEP, Config.DEFAULT_VOLUME_STEP)));
        int volumeSegments = mediaVolumeSegments();
        Runnable updateLabel = () -> stepLabel.setText(
                "共 " + volumeSegments + " 段音量，每次步進 " + volumeStepFromSeekBar(seekBar));
        updateLabel.run();
        seekBar.addOnChangeListener((slider, value, fromUser) -> {
            updateLabel.run();
            if (fromUser) {
                prefs.edit().putInt(Config.KEY_VOLUME_STEP, volumeStepFromSeekBar(slider)).commit();
            }
        });
        seekBar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                prefs.edit().putInt(Config.KEY_VOLUME_STEP, volumeStepFromSeekBar(slider)).commit();
                showToast("已寫入音量步進值");
            }
        });

        bindSwitch(page, R.id.volume_undelay_switch, Config.KEY_VOLUME_BROADCAST_UNDELAY, true,
                "已寫入音量變化通知開關，重新開機後生效");
        bindSwitch(page, R.id.volume_aosp_switch, Config.KEY_AOSP_VOLUME_DIALOG, false,
                "已寫入 AOSP 音量條開關；按下方「重啟 SystemUI」或重新開機後生效");
        onClick(page, R.id.volume_restart_systemui, view -> restartSystemUi());
    }

    private void showBtReceiverPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_BT_RECEIVER;
        setHeader(getString(R.string.home_bt_title), true);
        View page = inflatePage(R.layout.page_bt_receiver);
        btStatusView = page.findViewById(R.id.bt_status);

        MaterialSwitch receiver = page.findViewById(R.id.bt_receiver_switch);
        receiver.setChecked(prefs.getBoolean(Config.KEY_BT_RECEIVER_ENABLED, false));
        receiver.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(Config.KEY_BT_RECEIVER_ENABLED, isChecked).commit();
            if (prefs.getBoolean(Config.KEY_BT_AUTO_RESTART, false)) {
                restartBluetooth();
            } else {
                showToast("已保存。請把藍牙關閉再開啟，新模式才會生效");
            }
            refreshBtStatus();
        });
        bindSwitch(page, R.id.bt_auto_restart_switch, Config.KEY_BT_AUTO_RESTART, false,
                "已開啟：切換模式時會自動重啟藍牙", "已關閉自動重啟藍牙");
        onClick(page, R.id.bt_restart_now, view -> restartBluetooth());
        refreshBtStatus();
    }

    /** Shows the configured mode next to the mode the running Bluetooth process reported. */
    private void refreshBtStatus() {
        if (btStatusView == null || currentPage != PAGE_BT_RECEIVER) {
            return;
        }
        boolean wantReceiver = prefs.getBoolean(Config.KEY_BT_RECEIVER_ENABLED, false);
        String active = prefs.getString(Config.KEY_BT_ACTIVE_MODE, "");
        long at = prefs.getLong(Config.KEY_BT_ACTIVE_AT, 0L);
        String message;
        if (btRestartRunning) {
            message = "藍牙重啟中…";
        } else if (active.isEmpty()) {
            message = "藍牙目前實際模式：尚未收到回報。請確認 LSPosed 已勾選藍牙作用域，並讓藍牙重啟一次。";
        } else {
            boolean activeReceiver = Config.BT_MODE_RECEIVER.equals(active);
            String activeName = activeReceiver ? "接收器模式" : "一般模式（可連耳機）";
            String time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date(at));
            if (activeReceiver == wantReceiver) {
                message = "藍牙目前實際模式：" + activeName + "（已生效，" + time + " 啟動）";
            } else {
                message = "已設定為" + (wantReceiver ? "接收器模式" : "一般模式")
                        + "，但藍牙目前仍是" + activeName + "（" + time + " 啟動）。請重啟藍牙。";
            }
        }
        btStatusView.setText(message);
    }

    private void restartBluetooth() {
        if (btRestartRunning) {
            showToast("藍牙正在重啟中");
            return;
        }
        btRestartRunning = true;
        refreshBtStatus();
        showToast("正在重啟藍牙…");
        rootExecutor.execute(() -> {
            int exitCode = runProcess(new ProcessBuilder("su", "-c", BT_RESTART_COMMAND));
            if (exitCode == 0) {
                mainHandler.post(() -> finishBtRestart("藍牙已重啟（root）"));
            } else {
                mainHandler.post(this::restartBluetoothFallback);
            }
        });
    }

    private void restartBluetoothFallback() {
        if (!isShizukuAvailable()) {
            finishBtRestart("藍牙重啟失敗：無 root，且 Shizuku 未連線，請手動把藍牙關閉再開啟");
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            pendingBtRestart = true;
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE_BT_RESTART);
            showToast("請授權 Shizuku 後自動重啟藍牙");
            return;
        }
        runBtRestartWithShizuku();
    }

    private void runBtRestartWithShizuku() {
        rootExecutor.execute(() -> {
            int exitCode = -1;
            try {
                Method newProcess = Shizuku.class.getDeclaredMethod(
                        "newProcess", String[].class, String[].class, String.class);
                newProcess.setAccessible(true);
                Process process = (Process) newProcess.invoke(
                        null,
                        new String[]{"sh", "-c", BT_RESTART_COMMAND},
                        null,
                        null
                );
                exitCode = process.waitFor();
            } catch (Throwable ignored) {
                // The exit code below reports the failure.
            }
            int finalExitCode = exitCode;
            mainHandler.post(() -> finishBtRestart(finalExitCode == 0
                    ? "藍牙已重啟（Shizuku）"
                    : "藍牙重啟失敗：Shizuku " + finalExitCode));
        });
    }

    private void finishBtRestart(String message) {
        btRestartRunning = false;
        showToast(message);
        refreshBtStatus();
        // The Bluetooth process reports its mode a moment after it starts.
        mainHandler.postDelayed(this::refreshBtStatus, 3000);
    }

    private void showAssistantPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_ASSISTANT;
        setHeader(getString(R.string.home_assistant_title), true);
        View page = inflatePage(R.layout.page_assistant);

        bindSwitch(page, R.id.assistant_switch, Config.KEY_ASSISTANT_REDIRECT_ENABLED, false, "已寫入魔姬手勢替換開關");
        ((TextView) page.findViewById(R.id.assistant_target_value)).setText(assistantTargetLabel(
                prefs.getString(Config.KEY_ASSISTANT_TARGET, Config.ASSISTANT_TARGET_DEFAULT)));

        MaterialButtonToggleGroup tabs = page.findViewById(R.id.assistant_tabs);
        tabs.check(assistantTab == 0 ? R.id.assistant_tab_system
                : assistantTab == 1 ? R.id.assistant_tab_user : R.id.assistant_tab_sysapp);
        tabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int tab = checkedId == R.id.assistant_tab_system ? 0 : checkedId == R.id.assistant_tab_user ? 1 : 2;
            if (tab != assistantTab) {
                assistantTab = tab;
                showAssistantPage();
            }
        });

        LinearLayout list = page.findViewById(R.id.assistant_list);
        if (assistantTab == 0) {
            addSystemActions(list);
        } else {
            addAppList(list, page.findViewById(R.id.assistant_list_header), assistantTab == 2);
        }
    }

    private void showLauncherPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_LAUNCHER;
        setHeader(getString(R.string.home_launcher_title), true);
        View page = inflatePage(R.layout.page_launcher);

        bindSwitch(page, R.id.launcher_hide_switch, Config.KEY_LAUNCHER_OVERRIDE_ENABLED, false, "已寫入最近任務隱藏開關");

        String component = prefs.getString(Config.KEY_LAUNCHER_COMPONENT, "");
        if (isSettingsFallbackHome(component)) {
            component = stockLauncherComponent().flattenToString();
            prefs.edit()
                    .putString(Config.KEY_LAUNCHER_COMPONENT, component)
                    .putString(Config.KEY_LAUNCHER_PACKAGE, STOCK_LAUNCHER_PACKAGE)
                    .commit();
        }

        LinearLayout list = page.findViewById(R.id.launcher_list);
        List<ResolveInfo> launchers = installedLaunchers();
        if (launchers.isEmpty()) {
            page.findViewById(R.id.launcher_empty).setVisibility(View.VISIBLE);
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
            list.addView(launcherCard(list, title, description, componentName.flattenToString(), packageName, icon));
        }
    }

    private void showAdbControlPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_ADB_CONTROL;
        setHeader(getString(R.string.home_adb_title), true);
        View page = inflatePage(R.layout.page_adb);

        MaterialSwitch developerAdb = page.findViewById(R.id.adb_developer_switch);
        MaterialSwitch fakeAdb = page.findViewById(R.id.adb_fake_switch);
        TextView developerAdbStatus = page.findViewById(R.id.adb_system_value);
        MaterialSwitch enabled = page.findViewById(R.id.adb_wireless_switch);
        enabled.setChecked(prefs.getBoolean(Config.KEY_WIRELESS_ADB_ENABLED, false));
        TextInputEditText customPort = page.findViewById(R.id.adb_port);
        customPort.setText(String.valueOf(prefs.getInt(Config.KEY_WIRELESS_ADB_PORT, Config.DEFAULT_WIRELESS_ADB_PORT)));
        TextView statusValue = page.findViewById(R.id.adb_status_value);
        TextView endpointValue = page.findViewById(R.id.adb_endpoint_value);
        TextView commandValue = page.findViewById(R.id.adb_command_value);
        MaterialButton copyCommand = page.findViewById(R.id.adb_copy);
        MaterialButton applyPort = page.findViewById(R.id.adb_apply);
        MaterialSwitch adbInstall = page.findViewById(R.id.adb_install_switch);

        copyCommand.setOnClickListener(view -> {
            String command = String.valueOf(commandValue.getText());
            if (!"-".equals(command)) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("ADB connect", command));
                showToast("已複製連線指令");
            }
        });
        applyPort.setOnClickListener(view -> {
            int port = parseWirelessAdbPort(customPort);
            if (port == -1) {
                showToast("連接埠必須介於 1 到 65535");
                return;
            }
            prefs.edit().putInt(Config.KEY_WIRELESS_ADB_PORT, port).commit();
            applyWirelessAdbPort(port, enabled, statusValue, endpointValue, commandValue, copyCommand);
        });

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
    }

    private void showThemePage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_THEME;
        setHeader(getString(R.string.home_theme_title), true);
        View page = inflatePage(R.layout.page_theme);

        bindSwitch(page, R.id.theme_select_switch, Config.KEY_THEME_SELECT_ENABLED, false,
                "已啟用主題元素選擇", "已停用主題元素選擇");
        bindSwitch(page, R.id.theme_splash_switch, Config.KEY_SKIP_BEAUTIFY_SPLASH, false,
                "已啟用跳過開屏畫面", "已停用跳過開屏畫面");
        bindSwitch(page, R.id.theme_trial_switch, Config.KEY_REMOVE_TRIAL_LIMIT, false,
                "已啟用移除試用時間限制", "已停用移除試用時間限制");
        onClick(page, R.id.theme_open_beautify, view -> {
            Intent intent = new Intent("zte.mfv.intent.action.beauty.main");
            intent.setPackage("com.zte.beautify");
            intent.putExtra("dev.xpramt.redmagicvowifi.OPEN_DOWNLOADED_THEMES", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                startActivity(intent);
            } catch (SecurityException | android.content.ActivityNotFoundException exception) {
                showToast("無法打開下載佈景主題");
            }
        });
    }

    private void showQuickEntryPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_QUICK_ENTRY;
        setHeader(getString(R.string.home_quick_title), true);
        View page = inflatePage(R.layout.page_quick_entry);
        onClick(page, R.id.quick_unlock, view -> openEngineeringMode("*983*673636#", "已送出工程模式解鎖碼"));
        onClick(page, R.id.quick_enter, view -> openEngineeringModeMenu());
        onClick(page, R.id.quick_phone_info, view -> openPhoneInfo());
    }

    private void showOtherPage() {
        stopWirelessAdbPolling();
        currentPage = PAGE_OTHER;
        setHeader(getString(R.string.home_other_title), true);
        View page = inflatePage(R.layout.page_other);

        TextView valueLabel = page.findViewById(R.id.other_timeout_label);
        Slider seekBar = page.findViewById(R.id.other_timeout_slider);
        seekBar.setValue(readScreenOffTimeoutMinutes());
        Runnable updateLabel = () -> valueLabel.setText(
                "螢幕關閉時間：" + screenOffMinutesFromSeekBar(seekBar) + " 分鐘"
        );
        updateLabel.run();
        seekBar.addOnChangeListener((slider, value, fromUser) -> updateLabel.run());
        seekBar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                applyScreenOffTimeout(screenOffMinutesFromSeekBar(slider), seekBar, valueLabel);
            }
        });
    }

    private int readScreenOffTimeoutMinutes() {
        int timeoutMillis;
        try {
            timeoutMillis = Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    DEFAULT_SCREEN_OFF_MINUTES * 60_000
            );
        } catch (SecurityException exception) {
            timeoutMillis = DEFAULT_SCREEN_OFF_MINUTES * 60_000;
        }
        int minutes = Math.round(timeoutMillis / 60_000f);
        return Math.max(MIN_SCREEN_OFF_MINUTES, Math.min(MAX_SCREEN_OFF_MINUTES, minutes));
    }

    private int screenOffMinutesFromSeekBar(Slider seekBar) {
        return (int) seekBar.getValue();
    }

    /** One slider look for the whole app: integer steps, no tick marks, no value balloon. */
    private Slider slider(int min, int max, int value) {
        Slider slider = new Slider(this);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setStepSize(1f);
        slider.setValue(Math.max(min, Math.min(max, value)));
        slider.setTickVisible(false);
        slider.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE);
        return slider;
    }

    private void applyScreenOffTimeout(int minutes, Slider seekBar, TextView valueLabel) {
        int timeoutMillis = minutes * 60_000;
        seekBar.setEnabled(false);
        valueLabel.setText("套用中...");
        rootExecutor.execute(() -> {
            int exitCode = runProcess(new ProcessBuilder(
                    "su", "-c", "settings put system screen_off_timeout " + timeoutMillis
            ));
            int actualMinutes = readScreenOffTimeoutMinutes();
            if (exitCode == 0 && actualMinutes == minutes) {
                mainHandler.post(() -> finishScreenTimeoutApply(
                        seekBar, valueLabel, "已設定螢幕亮屏時間：" + actualMinutes + " 分鐘（root）"));
                return;
            }
            mainHandler.post(() -> runScreenTimeoutFallback(
                    "settings put system screen_off_timeout " + timeoutMillis,
                    minutes,
                    seekBar,
                    valueLabel
            ));
        });
    }

    private void runScreenTimeoutFallback(
            String command,
            int minutes,
            Slider seekBar,
            TextView valueLabel
    ) {
        if (!isShizukuAvailable()) {
            finishScreenTimeoutApply(seekBar, valueLabel, "套用失敗：無 root，且 Shizuku 未連線");
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            pendingScreenTimeoutCommand = command;
            pendingScreenTimeoutMinutes = minutes;
            pendingScreenTimeoutSeekBar = seekBar;
            pendingScreenTimeoutValueLabel = valueLabel;
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE_SCREEN_TIMEOUT);
            showToast("請授權 Shizuku 後自動套用");
            return;
        }
        runScreenTimeoutWithShizuku(command, minutes, seekBar, valueLabel);
    }

    private void runScreenTimeoutWithShizuku(
            String command,
            int minutes,
            Slider seekBar,
            TextView valueLabel
    ) {
        rootExecutor.execute(() -> {
            int exitCode = -1;
            try {
                Method newProcess = Shizuku.class.getDeclaredMethod(
                        "newProcess", String[].class, String[].class, String.class);
                newProcess.setAccessible(true);
                Process process = (Process) newProcess.invoke(
                        null,
                        new String[]{"sh", "-c", command},
                        null,
                        null
                );
                exitCode = process.waitFor();
            } catch (Throwable ignored) {
                // Keep the verified setting value as the source of truth below.
            }
            int actualMinutes = readScreenOffTimeoutMinutes();
            String message = exitCode == 0 && actualMinutes == minutes
                    ? "已設定螢幕亮屏時間：" + actualMinutes + " 分鐘（Shizuku）"
                    : "螢幕亮屏時間套用失敗：Shizuku " + exitCode;
            mainHandler.post(() -> finishScreenTimeoutApply(seekBar, valueLabel, message));
        });
    }

    private void finishScreenTimeoutApply(Slider seekBar, TextView valueLabel, String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        int actualMinutes = readScreenOffTimeoutMinutes();
        if (seekBar != null) {
            seekBar.setValue(Math.max(MIN_SCREEN_OFF_MINUTES, Math.min(MAX_SCREEN_OFF_MINUTES, actualMinutes)));
            seekBar.setEnabled(true);
        }
        if (valueLabel != null) {
            valueLabel.setText("螢幕關閉時間：" + actualMinutes + " 分鐘");
        }
        showToast(message);
    }

    private void clearPendingScreenTimeout() {
        pendingScreenTimeoutCommand = null;
        pendingScreenTimeoutMinutes = 0;
        pendingScreenTimeoutSeekBar = null;
        pendingScreenTimeoutValueLabel = null;
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

    private void restartSystemUi() {
        new Thread(() -> {
            String result;
            try {
                Process process = new ProcessBuilder("su", "-c",
                        "p=$(pidof com.android.systemui); if [ -z \"$p\" ]; then echo 'SystemUI not running'; exit 2; fi; kill $p")
                        .redirectErrorStream(true).start();
                StringBuilder out = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (out.length() > 0) out.append(' ');
                        out.append(line.trim());
                    }
                }
                int exitCode = process.waitFor();
                result = exitCode == 0
                        ? "已重啟 SystemUI"
                        : "重啟 SystemUI 失敗（" + exitCode + "）" + (out.length() > 0 ? "：" + out : "");
            } catch (IOException exception) {
                result = "重啟 SystemUI 失敗：無法取得 root（" + exception.getMessage() + "）";
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                result = "重啟 SystemUI 失敗：執行中斷";
            }
            String message = result;
            runOnUiThread(() -> showToast(message));
        }, "restart-systemui").start();
    }

    private int volumeStepFromSeekBar(Slider seekBar) {
        return (int) seekBar.getValue();
    }

    private int mediaVolumeSegments() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        return audioManager == null
                ? Config.MAX_VOLUME_STEP
                : audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    private void refreshDeveloperAdbStatus(MaterialSwitch toggle, MaterialSwitch fakeToggle, TextView statusValue) {
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

    private void toggleDeveloperAdb(boolean enabled, MaterialSwitch toggle, MaterialSwitch fakeToggle, TextView statusValue,
                                    MaterialSwitch wirelessToggle, TextView wirelessStatusValue, TextView endpointValue,
                                    TextView commandValue, MaterialButton copyCommand) {
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

    private void toggleFakeAdb(boolean enabled, MaterialSwitch toggle, MaterialSwitch fakeToggle, TextView statusValue,
                               MaterialSwitch wirelessToggle, TextView wirelessStatusValue, TextView endpointValue,
                               TextView commandValue, MaterialButton copyCommand) {
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

    private void updateDeveloperAdbViews(String adbSetting, MaterialSwitch toggle, MaterialSwitch fakeToggle, TextView statusValue) {
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

    private void refreshAdbInstallStatus(MaterialSwitch toggle) {
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

    private void toggleAdbInstall(boolean enabled, MaterialSwitch toggle) {
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

    private void updateAdbInstallSwitch(String setting, MaterialSwitch toggle) {
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

    private void startWirelessAdbPolling(MaterialSwitch enabled, TextView statusValue, TextView endpointValue,
                                         TextView commandValue, MaterialButton copyCommand) {
        scheduleWirelessAdbPolling(enabled, statusValue, endpointValue, commandValue, copyCommand, 0);
    }

    private void restartWirelessAdbPollingAfterDelay(MaterialSwitch enabled, TextView statusValue, TextView endpointValue,
                                                      TextView commandValue, MaterialButton copyCommand) {
        scheduleWirelessAdbPolling(enabled, statusValue, endpointValue, commandValue, copyCommand, 5000);
    }

    private void scheduleWirelessAdbPolling(MaterialSwitch enabled, TextView statusValue, TextView endpointValue,
                                            TextView commandValue, MaterialButton copyCommand, long initialDelayMillis) {
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

    private void refreshWirelessAdbStatus(MaterialSwitch enabled, TextView statusValue, TextView endpointValue,
                                          TextView commandValue, MaterialButton copyCommand) {
        refreshWirelessAdbStatus(enabled, statusValue, endpointValue, commandValue, copyCommand, true);
    }

    private void refreshWirelessAdbStatus(MaterialSwitch enabled, TextView statusValue, TextView endpointValue,
                                          TextView commandValue, MaterialButton copyCommand, boolean showLoading) {
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

    private void applyWirelessAdbPort(int port, MaterialSwitch enabled, TextView statusValue, TextView endpointValue,
                                      TextView commandValue, MaterialButton copyCommand) {
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

    private void toggleWirelessAdb(boolean enabled, int port, MaterialSwitch toggle, TextView statusValue, TextView endpointValue,
                                   TextView commandValue, MaterialButton copyCommand) {
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

    private void updateWirelessAdbViews(WirelessAdbStatus status, String ipAddress, MaterialSwitch enabled,
                                        TextView statusValue, TextView endpointValue,
                                        TextView commandValue, MaterialButton copyCommand) {
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

    private MaterialCardView launcherCard(ViewGroup parent, String title, String description, String component,
                                          String packageName, Drawable icon) {
        boolean selected = component.equals(prefs.getString(Config.KEY_LAUNCHER_COMPONENT, ""));
        return choiceCard(parent, title, description, icon, selected, view -> {
            prefs.edit()
                    .putString(Config.KEY_LAUNCHER_COMPONENT, component)
                    .putString(Config.KEY_LAUNCHER_PACKAGE, packageName)
                    .commit();
            applyLauncherComponent(component, title);
            showLauncherPage();
        });
    }

    /** One selectable row (res/layout/item_choice.xml): selected = primary container, otherwise outlined. */
    private MaterialCardView choiceCard(ViewGroup parent, String title, String description, Drawable icon,
                                        boolean selected, View.OnClickListener listener) {
        MaterialCardView card = (MaterialCardView) getLayoutInflater().inflate(R.layout.item_choice, parent, false);
        TextView titleView = card.findViewById(R.id.choice_title);
        TextView descriptionView = card.findViewById(R.id.choice_desc);
        titleView.setText(title);
        descriptionView.setText(description);
        if (icon != null) {
            ImageView image = card.findViewById(R.id.choice_icon);
            image.setImageDrawable(icon);
            image.setVisibility(View.VISIBLE);
        }
        if (selected) {
            int onContainer = color(com.google.android.material.R.attr.colorOnPrimaryContainer);
            card.setCardBackgroundColor(color(com.google.android.material.R.attr.colorPrimaryContainer));
            card.setStrokeWidth(0);
            titleView.setTextColor(onContainer);
            descriptionView.setTextColor(onContainer);
        }
        card.setOnClickListener(listener);
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

    private void addSystemActions(LinearLayout list) {
        list.addView(targetCard(list, "小助手", "啟動系統預設 android.intent.action.ASSIST", Config.TARGET_PREFIX_ACTION + Config.ACTION_DEFAULT_ASSIST, null));
        list.addView(targetCard(list, "語音助手", "啟動 android.intent.action.VOICE_COMMAND，實際助手依使用者系統設定", Config.TARGET_PREFIX_ACTION + Config.ACTION_GOOGLE_VOICE, null));
        list.addView(targetCard(list, "最近應用", "送出 KEYCODE_APP_SWITCH", Config.TARGET_PREFIX_ACTION + Config.ACTION_RECENTS, null));
        list.addView(targetCard(list, "螢幕截圖", "送出 KEYCODE_SYSRQ", Config.TARGET_PREFIX_ACTION + Config.ACTION_SCREENSHOT, null));
        list.addView(targetCard(list, "手電筒", "透過 CameraManager 切換背面閃光燈", Config.TARGET_PREFIX_ACTION + Config.ACTION_FLASHLIGHT, null));
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void addAppList(LinearLayout list, TextView header, boolean systemApps) {
        List<ApplicationInfo> apps = installedApps(systemApps);
        header.setText((systemApps ? "系統應用" : "使用者應用") + "：" + apps.size() + " 個");
        header.setVisibility(View.VISIBLE);
        for (ApplicationInfo app : apps) {
            String label = String.valueOf(app.loadLabel(getPackageManager()));
            list.addView(targetCard(list, label, app.packageName, Config.TARGET_PREFIX_APP + app.packageName, app.loadIcon(getPackageManager())));
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

    private MaterialCardView targetCard(ViewGroup parent, String title, String description, String target, Drawable icon) {
        boolean selected = target.equals(prefs.getString(Config.KEY_ASSISTANT_TARGET, Config.ASSISTANT_TARGET_DEFAULT));
        return choiceCard(parent, title, description, icon, selected, view -> {
            prefs.edit().putString(Config.KEY_ASSISTANT_TARGET, target).commit();
            showToast("已選擇：" + title);
            showAssistantPage();
        });
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

    private void showSettingsDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        MaterialSwitch autoCheck = content.findViewById(R.id.dialog_auto_check);
        autoCheck.setChecked(prefs.getBoolean(Config.KEY_AUTO_CHECK_UPDATES, true));
        autoCheck.setOnCheckedChangeListener((buttonView, checked) ->
                prefs.edit().putBoolean(Config.KEY_AUTO_CHECK_UPDATES, checked).apply());
        MaterialButton checkNow = content.findViewById(R.id.dialog_check_now);
        checkNow.setOnClickListener(view -> {
            checkNow.setEnabled(false);
            checkForUpdates(true, () -> checkNow.setEnabled(true));
        });
        showThemedDialog(new MaterialAlertDialogBuilder(this)
                .setTitle("設定")
                .setView(content)
                .setPositiveButton("關閉", null)
                .create());
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
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("發現新版本")
                .setMessage("RedMagicX " + release.tagName + " 已發布，目前版本為 " + versionName())
                .setNegativeButton("稍後", null)
                .setPositiveButton("前往下載", (ignoredDialog, which) -> {
                    Intent browser = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(RELEASES_PAGE_URL));
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
        String url = "https://github.com/XPRAMT/RedMagicX";
        String message = "作者：XPRAMT\nGitHub：" + url + "\n版本：" + versionName();
        SpannableString spannable = new SpannableString(message);
        int start = message.indexOf(url);
        if (start >= 0) {
            spannable.setSpan(new URLSpan(url), start, start + url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView content = (TextView) getLayoutInflater().inflate(R.layout.dialog_about, null);
        content.setText(spannable);
        content.setMovementMethod(LinkMovementMethod.getInstance());
        showThemedDialog(new MaterialAlertDialogBuilder(this)
                .setTitle("關於")
                .setView(content)
                .setPositiveButton("確定", null)
                .create());
    }

    private void showThemedDialog(AlertDialog dialog) {
        dialog.show();
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            return "unknown";
        }
    }

    private int styleToId(String style) {
        if (Config.STYLE_GEN_BD.equals(style)) return R.id.vowifi_style_gen_bd;
        if (Config.STYLE_ARRAY_HOOK.equals(style)) return R.id.vowifi_style_array_hook;
        return R.id.vowifi_style_default;
    }

    private String idToStyle(int id) {
        if (id == R.id.vowifi_style_gen_bd) return Config.STYLE_GEN_BD;
        if (id == R.id.vowifi_style_array_hook) return Config.STYLE_ARRAY_HOOK;
        return Config.STYLE_DEFAULT;
    }

    // Extra top spacing before every section title except the first child of a section box (SECTION_TITLE_TOP_SPACING_DP).

    private int color(int attr) {
        return MaterialColors.getColor(this, attr, Color.MAGENTA);
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

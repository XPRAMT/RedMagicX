package dev.xpramt.redmagicvowifi;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;

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
                    .apply();
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
        root.addView(text("修改只在 LSPosed 目標進程內生效，不直接永久寫入全域屬性。", 14, false));

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

        root.addView(text("套用方式：改完後重啟對應進程。Settings：am force-stop com.android.settings。SystemUI：su -c 'kill -9 $(pidof com.android.systemui)'。", 13, false));
        return scrollView;
    }

    private LinearLayout sectionSwitch(String title, String description, String key) {
        LinearLayout box = sectionBox();
        Switch sw = new Switch(this);
        sw.setText(title);
        sw.setTextSize(18);
        sw.setChecked(prefs.getBoolean(key, true));
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            prefs.edit().putBoolean(key, isChecked).apply();
            makePrefsReadable();
        });
        box.addView(sw);
        box.addView(text(description, 13, false));
        return box;
    }

    private LinearLayout styleSection() {
        LinearLayout box = sectionBox();
        box.addView(text("VoWiFi 圖標樣式", 18, true));
        box.addView(text("作用進程：com.android.systemui\n用途：控制 SystemUI 選用哪一套 VoWiFi/VoLTE icon array。", 13, false));

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
            prefs.edit().putString(Config.KEY_ICON_STYLE, idToStyle(checked)).apply();
            makePrefsReadable();
        });
        box.addView(group);
        return box;
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
    }
}

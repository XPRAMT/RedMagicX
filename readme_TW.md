# RedMagic VoWiFi

語言：[English](README.md) | 繁體中文

目標裝置：RedMagic / Nubia NX809J 中國版 ROM。

本 App 只支援兩種模式：

- `Root 全域模式`：不 hook App，直接透過 `resetprop` 寫入全域屬性。
- `Root + LSPosed 模式`：只 hook `com.android.settings` 與 `com.android.systemui`，避免持久修改全域屬性。

仍需要 Pixel IMS 或等效 carrier-config 修改，讓電信商 WFC/VoWiFi 能力本身啟用。

## 能力邊界

| 功能 / 開關 | Root 全域模式 | Root + LSPosed 模式 |
|---|---|---|
| `開啟 VoWiFi 設定` | 支援。透過 `resetprop` 寫入 `ro.vendor.feature.zte_feature_need_wfc_for_domestic=true/false`。 | 支援。Hook `com.android.settings`，讓 `ro.vendor.feature.zte_feature_need_wfc_for_domestic` 讀值跟隨開關。 |
| `開啟狀態列 VoWiFi 圖標` | 支援。透過 `resetprop` 寫入 `ro.vendor.mifavor.custom=abroad/home` 與 `ro.mifavor.custom=abroad/home`。 | 支援。Hook `com.android.systemui`，讓 SystemUI 在開啟時走 `abroad` IMS icon 分支。 |
| `VoWiFi 圖標樣式 = GEN_BD` | 支援。透過 `resetprop` 寫入或刪除 `persist.custom.variant.id=GEN_BD`。切換後重啟 SystemUI 生效。 | 支援。Hook `com.android.systemui`，讓它讀到 `persist.custom.variant.id=GEN_BD`。切換後重啟 SystemUI 生效。 |
| `VoWiFi 圖標樣式 = Hook array` | 不支援。Root 全域模式不 hook SystemUI，且此選項會避免寫入 `persist.custom.variant.id=GEN_BD`，防止非預期全域 variant 行為。 | 支援。Hook `com.android.systemui`，把 IMS icon array 回傳結果替換成 BD array。目前 ROM 已實測雙卡可用，但依賴目前 ROM 方法名。 |

## 安裝

下載 APK 後直接在手機上安裝即可。

安裝後：

1. 在 LSPosed 內啟用本模組。
2. 勾選作用域：
   - `com.android.settings`
   - `com.android.systemui`
3. 打開 App 選擇需要的模式與開關。
4. 按 `重啟 Settings + SystemUI` 讓目標進程重新讀取設定。

## 編譯

```powershell
& 'C:\Users\XPRAMT\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat' -p 'D:\Android\ZTE\VoWiFI\lsposed-redmagic-vowifi' assembleDebug
```

APK 輸出位置：

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Root 全域模式

適合不使用 LSPosed hook、只想直接改全域屬性的情境。

開關變更後，App 會立即透過 root 執行 `resetprop` 同步目前狀態。這不會自動重啟 Settings/SystemUI；變更後請使用 App 內的重啟按鈕。

App 內的 `Root 全域實際值` 會顯示目前 `getprop` 讀到的全域屬性，並在開關變更後自動更新。

開關對應：

- `開啟 VoWiFi 設定`
  - 開：`ro.vendor.feature.zte_feature_need_wfc_for_domestic=true`
  - 關：`ro.vendor.feature.zte_feature_need_wfc_for_domestic=false`
- `開啟狀態列 VoWiFi 圖標`
  - 開：`ro.vendor.mifavor.custom=abroad`、`ro.mifavor.custom=abroad`
  - 關：`ro.vendor.mifavor.custom=home`、`ro.mifavor.custom=home`
- `VoWiFi 圖標樣式 = GEN_BD`
  - 開：`persist.custom.variant.id=GEN_BD`
  - 預設 / 關：刪除 `persist.custom.variant.id`
  - 切換圖標樣式後重啟 SystemUI 生效。
- `VoWiFi 圖標樣式 = Hook array`
  - Root 全域模式不生效。
  - 會刪除或保持 `persist.custom.variant.id` 為空，避免非預期全域 variant 行為。

注意：

- 此模式會修改全域進程可見屬性，直到重置、重啟或其他持久化工具再次改寫。
- `ro.vendor.mifavor.custom=abroad` 與 `persist.custom.variant.id=GEN_BD` 可能影響 VoWiFi 圖標以外的功能。

## Root + LSPosed 模式

適合已安裝 LSPosed，且希望降低全域副作用的情境。

App 使用 LSPosed 新版 `XSharedPreferences` 流程保存 hook 設定。模組宣告：

```text
xposedminversion=93
xposedsharedprefs=true
```

App 端使用 `MODE_WORLD_READABLE` 建立偏好設定檔，hook 端透過 `XSharedPreferences` 讀取。

LSPosed 作用域：

- `com.android.settings`
- `com.android.systemui`
- 如果 LSPosed 需要模組載入 Android/System Framework，再依 LSPosed 提示勾選。

開關對應：

- `開啟 VoWiFi 設定`
  - Hook 目標：`com.android.settings`
  - 偽造值：`ro.vendor.feature.zte_feature_need_wfc_for_domestic=true`
  - Hook class candidate：`com.zte.settings.utils.SystemPropertiesZTE`
- `開啟狀態列 VoWiFi 圖標`
  - Hook 目標：`com.android.systemui`
  - 偽造值：`ro.vendor.mifavor.custom=abroad`、`ro.mifavor.custom=abroad`
  - 效果：讓 SystemUI abroad 分支把 WFC 狀態映射到 VoWiFi 圖標。
- `VoWiFi 圖標樣式 = GEN_BD`
  - Hook 目標：`com.android.systemui`
  - 偽造值：`persist.custom.variant.id=GEN_BD`
  - 效果：使用 BD 樣式 VoWiFi icon 資源。
  - 切換圖標樣式後重啟 SystemUI 生效。
- `VoWiFi 圖標樣式 = Hook array`
  - Hook 目標：`com.android.systemui`
  - 不修改全域屬性。
  - 將 `ImsUpdateFeature#getSingleCardImsIconArrayResId()` 替換為 `bd_single_card_volte_vowifi_icons_array`。
  - 目前 ROM 已實測雙卡可用，但依賴目前 ROM 方法名。

`Root 全域實際值` 只對 Root 全域模式有意義。LSPosed 模式不修改全域屬性，因此 `getprop` 數值不能用來判斷 hook 是否生效。

開關變更後，App 會立即保存 hook 設定。若 `XSharedPreferences` 無法讀取，hook 會 fail closed：

- WFC Settings hook：關閉
- SystemUI abroad hook：關閉
- 圖標樣式覆寫：預設 / 關閉

## 主題

App 使用 Android 非 light Material theme，亮色 / 暗色外觀跟隨系統設定。

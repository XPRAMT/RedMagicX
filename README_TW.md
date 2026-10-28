# RedMagicX

[English](README.md) | [繁體中文](README_TW.md)

RedMagicX 是非官方 RedMagic / Nubia 系統調整工具，支援 LSPosed、root、Shizuku 使用情境。主要改進或解決中國版 ROM UI 行為：VoWiFi UI、媒體音量鍵步進、藍牙接收器模式、小白條助手手勢替換、第三方啟動器控制、ADB 控制，以及工程模式與手機資訊快速入口。

目前版本：`0.4.0`

測試裝置：RedMagic / Nubia NX809J 中國版 ROM。其它 RedMagic / Nubia 機型尚未測試，但若 ROM 使用相同 ZTE 類別與屬性，理論上可能可用。

<img src="img/app截圖main.jpg" alt="RedMagicX 主畫面" width="360">

## 目錄

- [安裝](#安裝)
- [LSPosed 作用域](#lsposed-作用域)
- [功能](#功能)
  - [VoWiFi UI 修正](#vowifi-ui-修正)
  - [音量步進調整](#音量步進調整)
  - [藍牙接收器](#藍牙接收器)
  - [魔姬手勢替換](#魔姬手勢替換)
  - [第三方啟動器](#第三方啟動器)
  - [ADB 控制中心](#adb-控制中心)
  - [快速入口](#快速入口)
- [KernelSU 模組：高取樣率解鎖](#kernelsu-模組高取樣率解鎖)
- [編譯](#編譯)
- [注意事項](#注意事項)

## 安裝

從 [GitHub Releases](https://github.com/XPRAMT/RedMagicX/releases) 下載 APK 後，直接在手機上安裝即可。

VoWiFi 電信商能力本身仍需要 [Pixel IMS](https://github.com/kyujin-cho/pixel-volte-patch) 或等效 carrier-config 修改。請先安裝 Pixel IMS 並用它開啟 VoWiFi。RedMagicX 主要修正中國版 ROM 的 UI 行為：設定頁缺少 Wi-Fi Calling / VoWiFi 開關，以及狀態列 VoWiFi 圖標顯示問題。

Root 只用於需要執行 shell 指令的動作，例如重啟 Settings/SystemUI，以及未使用 Shizuku 時套用預設啟動器。

<img src="img/授予root權限.jpg" alt="授予 root 權限" width="360">

在 LSPosed 內選擇 RedMagicX 作用域：

<img src="img/選擇lsposed作用域.jpg" alt="選擇 LSPosed 作用域" width="360">

## LSPosed 作用域

在 LSPosed 啟用 RedMagicX，並依照使用功能勾選作用域：

| 功能           | 需要作用域                                         |
| ------------ | --------------------------------------------- |
| VoWiFi UI 修正 | `com.android.settings`、`com.android.systemui` |
| 音量步進調整       | `android` / System Framework                  |
| 藍牙接收器 | `com.android.bluetooth` |
| 魔姬手勢替換       | `com.android.systemui`                        |
| 第三方啟動器最近任務隱藏 | `com.zte.mifavor.launcher`                    |

設定透過 LSPosed `XSharedPreferences` 保存。若目標進程在修改設定前已經載入，請使用 App 內提供的重啟按鈕重啟相關進程；若目標是 `android`，則需要重啟手機。

## 功能

### VoWiFi UI 修正

只使用 LSPosed hook，不透過 `resetprop` 全域修改系統屬性。

<img src="img/app截圖1.jpg" alt="VoWiFi UI 修正設定" width="360">

| 開關                       | 作用域                    | 修改內容                                                                                                                                            |
| ------------------------ | ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| 開啟 VoWiFi 設定             | `com.android.settings` | 讓 Settings 讀到 `ro.vendor.feature.zte_feature_need_wfc_for_domestic=true`，顯示 Wi-Fi Calling / VoWiFi 開關。仍需要 Pixel IMS 或 carrier config 啟用 WFC 能力。 |
| 開啟狀態列 VoWiFi 圖標          | `com.android.systemui` | 只讓 IMS/狀態列圖標相關程式碼讀到 `ro.vendor.mifavor.custom=abroad` / `ro.mifavor.custom=abroad`，navigation/assistant 相關程式碼維持 `home`，避免小白條手勢失效。               |
| VoWiFi 圖標樣式 = GEN_BD     | `com.android.systemui` | 讓 SystemUI 讀到 `persist.custom.variant.id=GEN_BD`，使用 BD 樣式 VoWiFi 資源。切換後重啟 SystemUI 生效。                                                          |
| VoWiFi 圖標樣式 = Hook array | `com.android.systemui` | 把 IMS icon array 回傳結果替換成 BD array。目前 NX809J ROM 已實測雙卡可用，但依賴目前 ROM 方法名。                                                                          |

「打開 WiFi 通話設定」按鈕可直接開啟紅魔設定的 `WifiCallingSettingsActivity`，不需要 root 或 Shizuku。

VoWiFi 圖標樣式對照：

預設樣式使用 `statusbar_vowifi.svg`：

<img src="img/icons/statusbar_vowifi.svg" alt="預設 statusbar_vowifi 圖標" width="220">

BD 樣式使用 `bd_stat_vowifi.svg`：

<img src="img/icons/bd_stat_vowifi.svg" alt="BD bd_stat_vowifi 圖標" width="220">

### 音量步進調整

自訂按一次實體音量鍵時，媒體音量增減的格數。

<img src="img/app截圖2.jpg" alt="音量步進調整設定" width="360">

- 範圍：`1` 到 `10`
- 作用域：`android` / System Framework
- Hook 目標：`com.android.server.audio.AudioService`
- 修改行為：媒體音量鍵升降步進
- 生效方式：開啟功能並設定步進值後，重啟手機讓 system_server 載入 LSPosed 模組

### 藍牙接收器

讓手機變成藍牙喇叭：另一支手機配對後，可以把音樂串流到這支手機。手機一次只能擔任一種藍牙音訊角色，因為在測試的手機上，Android 藍牙堆疊無法穩定地同時擔任耳機端（A2DP source）與喇叭端（A2DP sink）。接收器模式下，藍牙耳機無法連線。

- **藍牙接收器模式**開關（預設關閉）。設定會一直保留，重開機後仍然有效，直到你關閉為止。
- **自動重啟藍牙**開關（預設關閉）。開啟後，每次切換模式都會自動把藍牙關閉再開啟，新模式立刻生效。需要 root 或 Shizuku，藍牙原本是關閉時不會被自動打開。關閉此選項時，切換後請自己把藍牙關閉再開啟。另有**立即重啟藍牙**按鈕可隨時手動重啟。
- 狀態列：顯示藍牙行程實際回報的模式與設定的模式，可以看出切換是否已生效。
- 作用域：`com.android.bluetooth`
- Hook 目標：`A2dpSinkService.isEnabled()` 與 `A2dpService.isEnabled()`，它們在藍牙行程啟動時決定要啟動哪些設定檔服務（所以切換模式後需要重啟藍牙）。一般模式不會安裝任何角色 hook。
- 使用方式：開啟模式並讓藍牙重啟，在這支手機開啟「設定 > 藍牙 > 配對新裝置」，再從另一支手機配對並播放。要用耳機時，關閉模式並讓藍牙重啟。
- 限制：只能接收 SBC 與 AAC（堆疊沒有 LDAC、aptX、LHDC 的解碼器）。對方看到的裝置類型仍是手機。只在 NX809J 搭配一支傳送手機測試過。

### 魔姬手勢替換

攔截 RedMagic 底部小白條長按 assistant 事件，改啟動指定目標。

<img src="img/app截圖3.jpg" alt="魔姬手勢替換設定" width="360">

可選目標：

- 系統動作：小助手、語音助手、最近應用、螢幕截圖、手電筒
- 使用者 App
- 系統 App

此功能不修改 Android 系統預設 assistant 設定，而是在 SystemUI 開啟原本紅魔助手目標前攔截，改啟動選定目標。

### 第三方啟動器

將選定啟動器設為預設 HOME，並可在手勢模式下從紅魔最近使用列表隱藏該啟動器。

<img src="img/app截圖4.jpg" alt="第三方啟動器設定" width="360">

- 更換啟動器：使用 Android 內建 `cmd package set-home-activity --user 0 <component>`。
- 權限方式：優先使用 root；無 root 時可透過 Shizuku shell 權限套用。
- 隱藏最近任務：需要 LSPosed 勾選 `com.zte.mifavor.launcher` scope。Hook 紅魔 Launcher 的 `RecentsView#onGestureAnimationStart`，只在手勢模式 current task 是選定第三方 HOME 時阻止它被補成最近任務卡片。

### ADB 控制中心

ADB 控制中心的所有動作都需要 root 權限。

- 啟用 ADB：透過 `adb_enabled=1` 控制開發人員選項的 USB 偵錯。
- 偽裝 ADB 狀態：寫入 `adb_enabled=2`。Android 將大於零的值視為已啟用；只會影響精確檢查 `adb_enabled == 1` 的部分 App，無法繞過檢查其它偵錯狀態的 App。
- 啟用無線 ADB：設定 TCP 連接埠並重啟 `adbd`。開關保存的是使用者意圖，不會隨 daemon 實際狀態自動關閉。ADB 已開啟時，RedMagicX 每五秒檢查一次；若無線 ADB 未執行，會以已保存的連接埠自動恢復。
- 允許 ADB 安裝：控制紅魔開發人員選項的 `adb_install_enabled=1`，開啟後才可透過 `adb install` 安裝 APK。

### 快速入口

自動撥號進入紅魔工程模式，並提供手機資訊入口。

- 解鎖工程模式：透過紅魔原廠撥號盤自動輸入 `*983*673636#`，通常只需執行一次。
- 進入工程模式：自動輸入 `*983*0#`，讓 EMode 完成必要初始化並載入完整測試選單。
- 撥號盤檢查：注入輸入前會確認 `com.android.contacts/.activities.DialtactsActivity` 存在且已啟用；找不到或啟動失敗時立即停止，不會誤報成功。
- 權限方式：優先使用 root；無 root 時可透過 Shizuku shell 權限操作。
- 手機資訊：在已測試的 NX809J ROM 可直接開啟 `com.android.phone/.settings.RadioInfo`，不需要 root 或 Shizuku。

## KernelSU 模組：高取樣率解鎖

獨立的 KernelSU 模組，放在 [`ksu-hires-unlocker/`](ksu-hires-unlocker)。它不是 LSPosed 模組，也不在 APK 內。

原廠 ROM 的系統混音固定 48 kHz，所有音軌都會先被重新取樣成 48 kHz，才送到藍牙、有線耳機或 USB 音訊。此模組讓混音器跟隨音檔本身的取樣率。

支援 Android 16 的 RedMagic NX809J：安裝程式會檢查機型與 Android 版本，不符就拒絕安裝。開發與測試環境是 RedMagicOS 11.0.22（incremental `20260204.095106`），同機型的其他版本沒有測試。安裝程式依指令位元組找補丁位置，找不到預期結果就中止安裝；開機時若模組所依據的系統檔案已改變（例如 OTA 更新後），模組會自動停用，之後需重新安裝。

| 項目 | 修改內容 |
|---|---|
| `audio_module_config_primary.xml`（音訊 HAL 設定） | 每個取樣率一個 deep-buffer 混音埠：藍牙 A2DP 為 44.1 / 48 / 88.2 / 96 kHz，有線耳機與 USB 音訊另加 176.4 / 192 kHz。 |
| `libaudiopolicymanagerdefault.so`（2 bytes） | AOSP 只在請求高於 48 kHz 時比較取樣率，所以 44.1 / 48 kHz 音軌會落在最先開啟的輸出。補丁讓所有請求都參與評分，每個音軌都選到自己取樣率的埠。 |
| `libaudiocorehal.qti.so`（2 處，各 32 bytes） | deep-buffer 大小是 40 ms 的 frame 數，44.1 / 88.2 kHz 時為 1764 / 3528 frames，不是 16 的倍數。AudioFlinger 會進位到 16 的倍數，使每次寫入被拆成兩次而產生爆音。補丁把 HAL 的大小進位到 16 的倍數。 |
| `audioserver`（AudioFlinger，1 個指令）+ `ro.audio.flinger_standbytime_ms` | AudioFlinger 讓每條連接藍牙的輸出執行緒至少等待 3 秒才進入待機。舊執行緒等待待機期間，輸出後端仍被占用，不同取樣率的串流無法啟動，HAL 會丟棄開頭的寫入（約 3 秒靜音）。補丁拿掉這個 3 秒下限（一個分支指令），模組在開機時把所有輸出執行緒的待機延遲設為 500 ms。 |

三種輸出的比較（手機上的實測）：

| | 藍牙（A2DP） | 3.5 mm 有線耳機 | USB 音訊（DAC） |
|---|---|---|---|
| 混音器跟隨的取樣率 | 44.1 / 48 / 88.2 / 96 kHz | 44.1 / 48 / 88.2 / 96 / 176.4 / 192 kHz | 44.1 / 48 / 88.2 / 96 / 176.4 / 192 kHz |
| 96 kHz 以上的埠 | 沒有（LDAC 最高 96 kHz） | 有 | 有 |
| 最終輸出取樣率由誰決定 | 藍牙編碼設定：測試機上 LDAC 的 DSP 後端維持 96 kHz | PAL 耳機裝置跟隨串流取樣率 | PAL 的 `USB_AUDIO-RX` 後端跟隨串流取樣率，受 DAC 支援範圍限制（測試的 DAC：32 到 384 kHz，16 / 24 / 32 位元） |
| 歌曲交界處更換取樣率 | 若前一個串流結束不到約 0.5 秒，新歌開頭約 0.4 到 0.5 秒會靜音（v0.8.0 之前約 2.7 秒）；間隔更長或取樣率相同則不會遺失 | 新歌開頭靜音不到 0.3 秒（v0.8.0 之前約 2 秒） | 沒有延遲（四種組合連續播放，無起播錯誤） |
| 待機延遲 | 500 ms（原 3 秒） | 500 ms（原 2 秒） | 500 ms（原 2 秒） |
| 44.1 / 88.2 kHz 爆音修復 | 聆聽測試無爆音 | 44.1 kHz 的 HAL 寫入抖動（標準差）18.7 ms 降到 2.0 ms；聆聽無爆音 | 各取樣率抖動（標準差）4.2 到 4.9 ms；聆聽無爆音 |
| 驗證方式 | 每個音軌的混音執行緒、LDAC 後端紀錄、聆聽 | 混音執行緒、PAL 裝置設定、抖動統計、聆聽 | 混音執行緒、PAL USB 媒體設定、抖動統計、連續播放、聆聽 |
| 尚未驗證 | LDAC 以外的編碼 | DAC 時脈本身 | DAC 時脈本身（ADSP 卸載讓 ALSA 看不到）；只測了一台 DAC |

限制：

- 喇叭與聽筒維持 48 kHz（固定的硬體後端與智慧功放）。
- 藍牙與有線的輸出後端一次只能跑一個取樣率。若前一個串流仍在播放時就啟動不同取樣率的串流（淡入淡出、預載下一首），新串流會靜音到舊串流播完再加 0.5 秒待機，因為重試啟動期間 HAL 會丟棄寫入。
- 0.5 秒待機是全域的。藍牙輸出路徑裡約排著 0.35 到 0.44 秒的音訊，所以延遲不宜再調低（曲尾可能被截）。暫停超過 0.5 秒後再播，開頭約晚 0.1 秒出聲（HAL 重啟）；喇叭上的短音效約晚 30 到 50 毫秒，都不會遺失。此變更是改 `audioserver`；若想避開，可使用 v0.7.0 release（除了待機變更以外的全部功能）。
- App 透過 `AudioTrack` 最高只能要求 192 kHz；USB 上原本就有的 384 kHz `hifi_playback` 輸出沒有改動。
- 只測試了一台 USB DAC（Sony Audio）。
- 3.5 mm 耳機與 USB DAC 同時連接時，Android 會從 3.5 mm 耳機播放。

安裝：

1. 從 [GitHub Releases](https://github.com/XPRAMT/RedMagicX/releases) 下載 `bt_hires_mixer_v0.8.0.zip`（標籤 `hires-mixer-v0.8.0`）。
2. 在 KernelSU 管理器安裝後重新開機。
3. 若重開機後沒有聲音，在 KernelSU 管理器停用模組（或進入 KernelSU 安全模式）再重開機。

更新已安裝的模組時，安裝程式會使用它保存在 `/data/adb/bt_hires_mixer_stock/` 的原廠設定備份。若沒有這個備份（從沒有備份功能的舊版升級），請先停用模組、重開機，再重新安裝。

zip 內不含任何廠商檔案：安裝程式會複製手機自己的函式庫並修改幾個位元組，依手機自己的設定檔產生混音埠設定，再驗證結果。

AOSP 的這個取樣率判斷已回報上游：[Issue Tracker 578577149](https://issuetracker.google.com/issues/578577149)，另有進行中的草稿變更 [4396965](https://android-review.googlesource.com/c/platform/frameworks/av/+/4396965)。

使用風險自負。本專案為非官方專案，與 RedMagic、Nubia、ZTE、Qualcomm 無關。

## 編譯

```powershell
& 'C:\Users\XPRAMT\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat' -p 'D:\Android\ZTE\VoWiFI\lsposed-redmagic-vowifi' assembleDebug
```

APK 輸出位置：

```text
app\build\outputs\apk\debug\app-debug.apk
```

## 注意事項

- 模組宣告 `xposedminversion=93` 與 `xposedsharedprefs=true`。
- Shizuku 用於無 root 時套用預設啟動器，以及操作工程模式快速入口。
- 本專案為非官方工具，與 RedMagic、Nubia、ZTE 無關。

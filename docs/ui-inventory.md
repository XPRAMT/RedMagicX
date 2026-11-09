# UI 元素清單（Material 3，XML 版）

每個畫面都是 `app/src/main/res/layout/` 下的 XML；文字在 `values/strings.xml`，間距在 `values/dimens.xml`，樣式在 `values/styles.xml`。

## 共用外框（工具列、捲動區）（`activity_main.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 工具列 | toolbar |  |

## 首頁（`page_home.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 卡片 | home_vowifi |  |
| 文字 |  | VoWiFi UI 修正 |
| 文字 |  | Wi-Fi Calling 設定、狀態列 VoWiFi 圖標、VoWiFi 圖標樣式 |
| 卡片 | home_volume |  |
| 文字 |  | 音量條 |
| 文字 |  | 自訂音量鍵步進，或改用 AOSP 音量條 |
| 卡片 | home_bt |  |
| 文字 |  | 藍牙接收器 |
| 文字 |  | 讓手機變成藍牙喇叭，其他手機可串流音樂過來（與藍牙耳機互斥），可自動重啟藍牙 |
| 卡片 | home_assistant |  |
| 文字 |  | 魔姬手勢替換 |
| 文字 |  | 攔截 SystemUI 的小白條長按 Assistant 入口，改啟動系統動作、使用者 App 或系統 App |
| 卡片 | home_launcher |  |
| 文字 |  | 第三方啟動器 |
| 文字 |  | 設定預設 HOME，並從最近使用列表隱藏選定啟動器 |
| 卡片 | home_theme |  |
| 文字 |  | 主題 |
| 文字 |  | 在原廠主題預覽頁選擇允許套用的原廠資源類型 |
| 卡片 | home_adb |  |
| 文字 |  | ADB 控制中心 |
| 文字 |  | 快速控制 USB 偵錯、ADB 偽裝與無線 ADB |
| 卡片 | home_quick |  |
| 文字 |  | 快速入口 |
| 文字 |  | 自動撥號進入工程模式與手機資訊入口 |
| 卡片 | home_other |  |
| 文字 |  | 其它 |
| 文字 |  | 未分類的系統設定與未來可擴充功能 |

## VoWiFi UI 修正（`page_vowifi.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 文字 |  | 此功能修正 VoWiFi UI 顯示。 |
| 開關 | vowifi_wfc_switch | 在設定顯示 VoWiFi 設定 |
| 文字 |  | 在設定顯示 Wi-Fi Calling / VoWiFi 開關。仍需要 Pixel IMS 啟用 WFC。 |
| 文字 |  | 作用進程 |
| 文字 |  | com.android.settings |
| 文字 |  | 參數 |
| 文字 |  | ro.vendor.feature.zte_feature_need_wfc_for_domestic=true |
| 按鈕 | vowifi_open_wfc | 打開 WiFi 通話設定 |
| 開關 | vowifi_icon_switch | 開啟狀態列 VoWiFi 圖標 |
| 文字 |  | 更改狀態列圖標來源，解決 VoWiFi 圖標無法顯示的問題。開啟後 4G 通話圖標由 HD 變為 VoLTE。修改只作用於 IMS / 訊號圖標相關呼叫，避免其它功能受到影響。 |
| 文字 |  | 作用進程 |
| 文字 |  | com.android.systemui |
| 文字 |  | 參數 |
| 文字 |  | ro.vendor.mifavor.custom=abroad\nro.mifavor.custom=abroad |
| 文字 |  | VoWiFi 圖標樣式 |
| 文字 |  | 控制 SystemUI 選用哪一套 VoWiFi / VoLTE 圖標。GEN_BD 模式會讀取下列參數；Hook array 模式則直接替換 IMS icon array。 |
| 文字 |  | 作用進程 |
| 文字 |  | com.android.systemui |
| 文字 |  | 參數 |
| 文字 |  | persist.custom.variant.id=GEN_BD |
| 單選群組 | vowifi_style_group |  |
| 單選 | vowifi_style_default | 預設：不改 persist.custom.variant.id，通常使用 vowifi / vowifi_card1/2/12 |
| 單選 | vowifi_style_gen_bd | GEN_BD：等效 persist.custom.variant.id=GEN_BD，使用 bd_stat_vowifi / bd_vowifi_card1/2/12 |
| 單選 | vowifi_style_array_hook | Hook array：替換 ImsUpdateFeature 的 IMS icon array；已實測雙卡可用，依賴目前 ROM 方法名 |
| 文字 |  | 重啟 |
| 文字 |  | 開關變更會自動保存到 LSPosed 設定。重啟 Settings / SystemUI 後才會重新讀取設定；重啟操作需要 root 權限。 |
| 按鈕 | vowifi_restart_settings | 重啟 Settings |
| 按鈕 | vowifi_restart_systemui | 重啟 SystemUI |
| 按鈕 | vowifi_restart_both | 重啟 Settings + SystemUI |

## 音量條（`page_volume.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 開關 | volume_step_switch | 啟用自訂音量步進 |
| 文字 | volume_step_label |  |
| 滑桿 | volume_step_slider |  |
| 文字 |  | 攔截媒體音量的音量鍵升降步進值，可設為 1 到 10。第一次載入可能需要重啟手機才能生效。 |
| 文字 |  | 作用進程 |
| 文字 |  | android / System Framework |
| 文字 |  | 攔截方法 |
| 文字 |  | adjustStreamVolume\nadjustSuggestedStreamVolume |
| 開關 | volume_undelay_switch | 音量變化通知即時送達 |
| 文字 |  | 讓背景的音量面板 App（如 Ultra Volume）即時收到音量變化通知，不再被系統延後約 0.5 秒。只影響音量變化通知（VOLUME_CHANGED_ACTION），不影響其他廣播。修改後需重新開機生效。 |
| 文字 |  | 作用進程 |
| 文字 |  | android / System Framework |
| 文字 |  | 攔截方法 |
| 文字 |  | BroadcastProcessQueue.updateRunnableAt |
| 開關 | volume_aosp_switch | 使用AOSP音量條 |
| 文字 |  | 開啟後改用 AOSP／legacy 音量條 layout（volume_dialog_legacy + volume_dialog_row），並跳過 MFV 專用初始化，避開 ColorSeekBarZTE。不會再強制 Adapt.enabled()=false。變更後按下方「重啟 SystemUI」或重新開機套用。 |
| 文字 |  | 作用進程 |
| 文字 |  | com.android.systemui |
| 文字 |  | 攔截方法 |
| 文字 |  | getDialogLayoutResId / getRowLayoutResId\nmfvInitDialog / prepareDialog |
| 按鈕 | volume_restart_systemui | 重啟 SystemUI |

## 藍牙接收器（`page_bt_receiver.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 開關 | bt_receiver_switch | 藍牙接收器模式 |
| 文字 |  | 開啟後，這支手機會變成藍牙喇叭：其他手機配對後可以把音樂串流過來（SBC 或 AAC）。此模式下手機無法連接藍牙耳機，因為 Android 的藍牙堆疊無法同時穩定地擔任耳機端與喇叭端。設定會一直保留，重開機後仍然有效，直到你關閉為止。 |
| 開關 | bt_auto_restart_switch | 自動重啟藍牙 |
| 文字 |  | 打開後，每次切換接收器模式都會自動把藍牙關閉再開啟，新模式立刻生效。需要 root 或 Shizuku；藍牙原本是關閉時不會被自動打開。關閉此選項時，切換模式後請自己把藍牙關閉再開啟。 |
| 文字 | bt_status |  |
| 按鈕 | bt_restart_now | 立即重啟藍牙 |
| 文字 |  | 使用方式 |
| 文字 |  | 1. 開啟「藍牙接收器模式」，並讓藍牙重啟。2. 在這支手機的系統設定開啟「藍牙 > 配對新裝置」，讓它可被搜尋。3. 在另一支手機配對這支手機並選它當音訊輸出。4. 要用耳機時，關閉接收器模式並讓藍牙重啟。 |
| 文字 |  | LSPosed 作用域 |
| 文字 |  | 藍牙（com.android.bluetooth）。第一次使用請在 LSPosed 為 RedMagicX 勾選此作用域，然後讓藍牙重啟。 |
| 文字 |  | 限制 |
| 文字 |  | 只能解 SBC / AAC（沒有 LDAC、aptX、LHDC 的接收解碼）。對方看到的裝置類型仍是手機。接收模式下藍牙耳機無法連線。 |

## 魔姬手勢替換（`page_assistant.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 開關 | assistant_switch | 啟用魔姬手勢替換 |
| 文字 |  | 攔截小白條長按喚出魔姬助手的事件，改為啟動指定目標。可能需要重啟 SystemUI 才能生效。 |
| 文字 |  | 作用進程 |
| 文字 |  | com.android.systemui |
| 文字 |  | 攔截方法 |
| 文字 |  | Context.sendBroadcast / sendBroadcastAsUser 發出的 event_Home_Longpressed |
| 文字 |  | 目前目標 |
| 文字 | assistant_target_value |  |
| 分頁切換 | assistant_tabs |  |
| 按鈕 | assistant_tab_system | 系統動作 |
| 按鈕 | assistant_tab_user | User App |
| 按鈕 | assistant_tab_sysapp | System App |
| 文字 | assistant_list_header |  |

## 第三方啟動器（`page_launcher.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 開關 | launcher_hide_switch | 從最近使用列表隱藏選定啟動器 |
| 文字 |  | 避免使用第三方啟動器時，啟動器被補成最近任務卡片。只在手勢模式、目前任務為選定第三方 HOME 時生效。 |
| 文字 |  | 作用進程 |
| 文字 |  | com.zte.mifavor.launcher |
| 文字 |  | 攔截方法 |
| 文字 |  | RecentsView#onGestureAnimationStart |
| 文字 |  | 更換啟動器 |
| 文字 |  | 優先使用 root 執行系統 set-home-activity；無 root 時可透過 Shizuku 授權，以 shell 權限套用。 |
| 文字 |  | 指令 |
| 文字 |  | cmd package set-home-activity --user 0 <component> |
| 文字 | launcher_empty | 未找到可處理 HOME intent 的啟動器。 |

## 主題（`page_theme.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 開關 | theme_select_switch | 啟用主題元素選擇 |
| 文字 |  | 開啟後，原廠主題預覽頁會在自我介紹下方顯示系統資源、圖標、桌布與鎖屏選項，可分別選擇要套用的元素。 |
| 開關 | theme_splash_switch | 跳過開屏畫面 |
| 文字 |  | 啟動原廠主題 App 時跳過蒲公英開屏倒數，直接進入主題首頁。 |
| 開關 | theme_trial_switch | 移除試用時間限制 |
| 文字 |  | 免費試用主題、字體、桌布、動態桌布、熄屏時，不會有 5 分鐘時間限制。 |
| 文字 |  | 可選元素 |
| 文字 |  | 系統資源\n圖標\n桌布\n鎖屏 |
| 文字 |  | 作用進程 |
| 文字 |  | com.zte.beautify\ncom.zte.beautifyadapter |
| 文字 |  | 生效條件 |
| 文字 |  | LSPosed 需要勾選上述兩個作用域。變更作用域後請重新開啟原廠主題 App。 |
| 按鈕 | theme_open_beautify | 打開下載佈景主題 |

## ADB 控制中心（`page_adb.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 文字 |  | 以下功能皆需 root 權限。 |
| 開關 | adb_developer_switch | 啟用 ADB（開發人員選項） |
| 文字 |  | 快速控制開發人員選項的「USB 偵錯」開關。關閉後也會中斷無線 ADB。 |
| 開關 | adb_fake_switch | 偽裝 ADB 狀態 |
| 文字 |  | 寫入 adb_enabled=2。系統會把大於零的值視為 ADB 已開啟，只能欺騙精確檢查 adb_enabled 是否等於 1 的部分 App。對於會檢查非零值或其它偵錯狀態的 App 無效。 |
| 文字 |  | 系統狀態 |
| 文字 | adb_system_value | 讀取中... |
| 開關 | adb_wireless_switch | 啟用無線 ADB |
| 文字 |  | 立即設定 ADB TCP 連接埠並重啟 adbd。開啟後會保留使用者設定；ADB 已開啟時每 5 秒確認一次，未執行時會自動恢復。 |
| 輸入框 | adb_port |  |
| 文字 |  | 狀態 |
| 文字 | adb_status_value | 讀取中... |
| 文字 |  | IP 位址 |
| 文字 | adb_endpoint_value | 讀取中... |
| 文字 |  | 連線指令 |
| 文字 | adb_command_value | - |
| 按鈕 | adb_copy | 複製連線指令 |
| 按鈕 | adb_apply | 套用 |
| 開關 | adb_install_switch | 允許 ADB 安裝 |
| 文字 |  | 控制開發人員選項的「允許 USB 安裝」，開啟時寫入 adb_install_enabled=1。 |

## 快速入口（`page_quick_entry.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 文字 |  | 以下功能可使用 root 或 Shizuku 授權。 |
| 文字 |  | 工程模式 |
| 文字 |  | 首次使用時，請先解鎖工程模式；完成解鎖後可直接進入工程模式主選單。 |
| 按鈕 | quick_unlock | 解鎖工程模式 |
| 文字 |  | 模擬輸入 *983*673636#。只需要在首次使用時執行。 |
| 按鈕 | quick_enter | 進入工程模式 |
| 文字 |  | 透過原廠電話鍵盤模擬輸入 *983*0#，完成工程模式初始化並開啟主選單。 |
| 文字 |  | 手機資訊 |
| 文字 |  | 開啟系統電話服務的手機資訊頁面，可查看行動網路、IMS 與無線電狀態。 |
| 按鈕 | quick_phone_info | 開啟手機資訊 |

## 其它（`page_other.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 文字 |  | 螢幕亮屏時間設定 |
| 文字 |  | 設定螢幕無操作後維持亮屏的時間，範圍為 1 到 60 分鐘。每次進入此頁面時會重新讀取系統目前值。套用優先使用 root；沒有 root 時改用 Shizuku。 |
| 文字 | other_timeout_label |  |
| 滑桿 | other_timeout_slider |  |
| 文字 |  | 系統參數 |
| 文字 |  | Settings.System.screen_off_timeout |

## 清單項目（啟動器／助手目標）（`item_choice.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 卡片 | choice_card |  |
| 圖示 | choice_icon |  |
| 文字 | choice_title |  |
| 文字 | choice_desc |  |

## 設定對話框（`dialog_settings.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 開關 | dialog_auto_check | 自動檢測更新 |
| 文字 |  | 開啟後，啟動 App 時會檢查 GitHub 最新正式 Release。偵測到新版本時會顯示通知。 |
| 按鈕 | dialog_check_now | 手動檢查更新 |

## 關於對話框（`dialog_about.xml`）

| 類型 | id | 文字 |
|---|---|---|
| 文字 | about_text |  |

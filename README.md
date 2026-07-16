# RedMagic VoWiFi

Target device: RedMagic / Nubia NX809J China ROM.

This app supports two operation modes:

- `Root 全域模式`: do not hook apps. Write global properties with `resetprop`.
- `Root + LSPosed 模式`: hook only `com.android.settings` and `com.android.systemui`; avoid persistent global property changes.

Pixel IMS or equivalent carrier-config changes are still required for carrier WFC support.

## Capability Boundary

| Feature / switch | No root / Shizuku only | Root global mode | Root + LSPosed mode |
|---|---|---|---|
| `開啟 VoWiFi 設定` | Partial only. Pixel IMS/Shizuku can set carrier WFC config, but cannot bypass ZTE Settings domestic gate. | Supported. Writes `ro.vendor.feature.zte_feature_need_wfc_for_domestic=true/false` with `resetprop`. | Supported. Hooks `com.android.settings` so reads of `ro.vendor.feature.zte_feature_need_wfc_for_domestic` follow the switch. |
| `開啟狀態列 VoWiFi 圖標` | Not supported. Shizuku cannot change `ro.vendor.mifavor.custom` or hook SystemUI. | Supported. Writes `ro.vendor.mifavor.custom=abroad/home` and `ro.mifavor.custom=abroad/home` with `resetprop`. | Supported. Hooks `com.android.systemui` so SystemUI behaves as `abroad` when enabled. |
| `VoWiFi 圖標樣式 = GEN_BD` | Not supported. Shizuku cannot change `persist.custom.variant.id` or hook SystemUI icon arrays. | Supported. Writes or deletes `persist.custom.variant.id=GEN_BD` with `resetprop`. Restart SystemUI after changing this style. | Supported. Hooks `com.android.systemui` so it reads `persist.custom.variant.id=GEN_BD`. Restart SystemUI after changing this style. |
| `VoWiFi 圖標樣式 = Hook array` | Not supported. | Not supported. Root global mode does not hook SystemUI, and this option intentionally avoids writing `persist.custom.variant.id=GEN_BD`. | Supported. Hooks `com.android.systemui` and tries to replace the IMS icon array result with the BD array. |

No-root users can usually enable carrier WFC capability with Pixel IMS/Shizuku, but this ROM still hides Settings and SystemUI behavior behind ZTE project properties. The three switches in this app require root property changes or LSPosed hooks.

## Build

```powershell
& 'C:\Users\XPRAMT\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat' -p 'D:\Android\ZTE\VoWiFI\lsposed-redmagic-vowifi' assembleDebug
```

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Install

ADB install may fail on this ROM with `install session [-1]`.
If that happens, push the APK and open the system installer:

```powershell
& 'C:\APP\scrcpy-win64-v3.3.1\adb.exe' -s '192.168.1.9:5555' push 'D:\Android\ZTE\VoWiFI\lsposed-redmagic-vowifi\app\build\outputs\apk\debug\app-debug.apk' '/sdcard/Download/redmagic-vowifi-lsposed.apk'
& 'C:\APP\scrcpy-win64-v3.3.1\adb.exe' -s '192.168.1.9:5555' shell am start -a android.intent.action.VIEW -d 'file:///sdcard/Download/redmagic-vowifi-lsposed.apk' -t 'application/vnd.android.package-archive'
```

Then tap install on the phone.

## Mode 1: Root Global Mode

Use this mode when you do not want LSPosed hooks.
Changing a switch immediately runs `resetprop` through root and synchronizes the current switch state.
It does not restart Settings/SystemUI. Use the restart buttons after changing values.
The app also shows the current system property values in `目前實際值` and updates them automatically after switch changes.

Switch mapping:

- `開啟 VoWiFi 設定`
  - On: `ro.vendor.feature.zte_feature_need_wfc_for_domestic=true`
  - Off: `ro.vendor.feature.zte_feature_need_wfc_for_domestic=false`
- `開啟狀態列 VoWiFi 圖標`
  - On: `ro.vendor.mifavor.custom=abroad`, `ro.mifavor.custom=abroad`
  - Off: `ro.vendor.mifavor.custom=home`, `ro.mifavor.custom=home`
- `VoWiFi 圖標樣式 = GEN_BD`
  - On: `persist.custom.variant.id=GEN_BD`
  - Default/off: delete `persist.custom.variant.id`
  - Changing icon style takes effect after restarting SystemUI.
- `VoWiFi 圖標樣式 = Hook array`
  - No-op in Root global mode. It deletes or keeps `persist.custom.variant.id` empty to avoid unexpected global variant behavior.

After changing values, restart separately:

```sh
su
am force-stop com.android.settings
kill -9 $(pidof com.android.systemui)
```

Notes:

- This mode changes global process-visible properties until reset/reboot/persistence tooling changes them again.
- `ro.vendor.mifavor.custom=abroad` and `persist.custom.variant.id=GEN_BD` may affect more than VoWiFi icons.

## Mode 2: Root + LSPosed Mode

Use this mode when LSPosed is available and you want reduced global side effects.
The app stores hook settings with LSPosed's new `XSharedPreferences` flow.
The module declares `xposedminversion=93` and `xposedsharedprefs=true`, and the app creates the preference file with `MODE_WORLD_READABLE`.

LSPosed scope:

- `com.android.settings`
- `com.android.systemui`
- Android/System Framework only if LSPosed requires it for the module to load.

Switch mapping:

- `開啟 VoWiFi 設定`
  - Hook target: `com.android.settings`
  - Faked value: `ro.vendor.feature.zte_feature_need_wfc_for_domestic=true`
  - Hook class candidate: `com.zte.settings.utils.SystemPropertiesZTE`
- `開啟狀態列 VoWiFi 圖標`
  - Hook target: `com.android.systemui`
  - Faked values: `ro.vendor.mifavor.custom=abroad`, `ro.mifavor.custom=abroad`
  - Effect: SystemUI abroad branch maps WFC state to VoWiFi icon.
- `VoWiFi 圖標樣式 = GEN_BD`
  - Hook target: `com.android.systemui`
  - Faked value: `persist.custom.variant.id=GEN_BD`
  - Effect: BD-style VoWiFi icon resources.
  - Changing icon style takes effect after restarting SystemUI.
- `VoWiFi 圖標樣式 = Hook array`
  - Hook target: `com.android.systemui`
  - Keeps global properties unchanged.
  - Tries to replace `ImsUpdateFeature#getSingleCardImsIconArrayResId()` with `bd_single_card_volte_vowifi_icons_array`.

After changing switches, press:

```text
重啟 Settings + SystemUI
```

The app saves hook settings immediately when switches change.
If `XSharedPreferences` cannot be read, hooks fail closed:

- WFC Settings hook: off
- SystemUI abroad hook: off
- icon style override: default/off

## Theme

The app uses Android's non-light Material theme so light/dark appearance follows the system setting.

# RedMagic VoWiFi

Language: English | [繁體中文](readme_TW.md)

Target device: RedMagic / Nubia NX809J China ROM.
This app has been tested on NX809J. Other RedMagic / Nubia models have not been tested, but they may work in theory if their ZTE Settings/SystemUI implementation uses the same properties and classes.

This app supports two modes only:

- `Root Global Mode`: does not hook apps. Writes global properties with `resetprop`.
- `Root + LSPosed Mode`: hooks only `com.android.settings` and `com.android.systemui` to avoid persistent global property changes.

Pixel IMS or equivalent carrier-config changes are still required to enable carrier WFC/VoWiFi capability.
Install [Pixel IMS](https://github.com/kyujin-cho/pixel-volte-patch) first and use it to enable VoWiFi.
This app mainly fixes the VoWiFi UI problems on the China ROM: the missing Settings toggle and the missing/status-bar icon behavior.

## Capability Boundary

| Feature / switch | Root Global Mode | Root + LSPosed Mode |
|---|---|---|
| `Enable VoWiFi settings` | Supported. Writes `ro.vendor.feature.zte_feature_need_wfc_for_domestic=true/false` with `resetprop`. | Supported. Hooks `com.android.settings` so reads of `ro.vendor.feature.zte_feature_need_wfc_for_domestic` follow the switch. |
| `Enable status bar VoWiFi icon` | Supported. Writes `ro.vendor.mifavor.custom=abroad/home` and `ro.mifavor.custom=abroad/home` with `resetprop`. | Supported. Hooks `com.android.systemui` so SystemUI enters the `abroad` IMS icon branch when enabled. |
| `VoWiFi icon style = GEN_BD` | Supported. Writes or deletes `persist.custom.variant.id=GEN_BD` with `resetprop`. Restart SystemUI after changing this style. | Supported. Hooks `com.android.systemui` so it reads `persist.custom.variant.id=GEN_BD`. Restart SystemUI after changing this style. |
| `VoWiFi icon style = Hook array` | Not supported. Root Global Mode does not hook SystemUI, and this option intentionally avoids writing `persist.custom.variant.id=GEN_BD` to prevent unexpected global variant behavior. | Supported. Hooks `com.android.systemui` and replaces the IMS icon array result with the BD array. Tested working with dual SIM on the current ROM, but depends on the current ROM method name. |

## Installation

Download the APK from [GitHub Releases](https://github.com/XPRAMT/Redmagic-VoWiFi/releases) and install it directly on the phone.

After installing:

1. Install [Pixel IMS](https://github.com/kyujin-cho/pixel-volte-patch) and enable VoWiFi.
2. Enable this module in LSPosed.
3. Select the scopes:
   - `com.android.settings`
   - `com.android.systemui`
4. Open the app and choose the required mode and switches.
5. Press `Restart Settings + SystemUI` so the target processes reload the settings.

## Screenshots

App settings:

<img src="img/app截圖.jpg" alt="App settings" width="360">

Grant root permission when using Root Global Mode or the restart buttons:

<img src="img/授予root權限.jpg" alt="Grant root permission" width="360">

Select the LSPosed scopes for Settings and SystemUI:

<img src="img/選擇lsposed作用域.jpg" alt="Select LSPosed scopes" width="360">

VoWiFi icon style comparison:

Default style uses `statusbar_vowifi.svg`:

<img src="img/icons/statusbar_vowifi.svg" alt="Default statusbar_vowifi icon" width="220">

After changing to the BD style, the icon uses `bd_stat_vowifi.svg`:

<img src="img/icons/bd_stat_vowifi.svg" alt="BD bd_stat_vowifi icon" width="220">

## Build

```powershell
& 'C:\Users\XPRAMT\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat' -p 'D:\Android\ZTE\VoWiFI\lsposed-redmagic-vowifi' assembleDebug
```

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Root Global Mode

Use this mode when you do not want LSPosed hooks and only want to change global properties directly.

When a switch changes, the app immediately runs `resetprop` through root and synchronizes the current state. This does not restart Settings/SystemUI automatically; use the in-app restart buttons after changing values.

The in-app `Root Global Actual Values` section shows the current global properties from `getprop` and updates automatically after switch changes.

Switch mapping:

- `Enable VoWiFi settings`
  - On: `ro.vendor.feature.zte_feature_need_wfc_for_domestic=true`
  - Off: `ro.vendor.feature.zte_feature_need_wfc_for_domestic=false`
- `Enable status bar VoWiFi icon`
  - On: `ro.vendor.mifavor.custom=abroad`, `ro.mifavor.custom=abroad`
  - Off: `ro.vendor.mifavor.custom=home`, `ro.mifavor.custom=home`
- `VoWiFi icon style = GEN_BD`
  - On: `persist.custom.variant.id=GEN_BD`
  - Default / off: delete `persist.custom.variant.id`
  - Restart SystemUI after changing the icon style.
- `VoWiFi icon style = Hook array`
  - No-op in Root Global Mode.
  - Deletes or keeps `persist.custom.variant.id` empty to avoid unexpected global variant behavior.

Notes:

- This mode changes global process-visible properties until they are reset, rebooted, or changed again by persistence tooling.
- `ro.vendor.mifavor.custom=abroad` and `persist.custom.variant.id=GEN_BD` may affect more than VoWiFi icons.

## Root + LSPosed Mode

Use this mode when LSPosed is available and you want reduced global side effects.

The app stores hook settings with LSPosed's new `XSharedPreferences` flow. The module declares:

```text
xposedminversion=93
xposedsharedprefs=true
```

The app creates the preference file with `MODE_WORLD_READABLE`, and the hook side reads it through `XSharedPreferences`.

LSPosed scopes:

- `com.android.settings`
- `com.android.systemui`
- If LSPosed requires Android/System Framework for the module to load, select it as prompted by LSPosed.

Switch mapping:

- `Enable VoWiFi settings`
  - Hook target: `com.android.settings`
  - Faked value: `ro.vendor.feature.zte_feature_need_wfc_for_domestic=true`
  - Hook class candidate: `com.zte.settings.utils.SystemPropertiesZTE`
- `Enable status bar VoWiFi icon`
  - Hook target: `com.android.systemui`
  - Faked values: `ro.vendor.mifavor.custom=abroad`, `ro.mifavor.custom=abroad`
  - Effect: makes the SystemUI abroad branch map WFC state to a VoWiFi icon.
- `VoWiFi icon style = GEN_BD`
  - Hook target: `com.android.systemui`
  - Faked value: `persist.custom.variant.id=GEN_BD`
  - Effect: uses BD-style VoWiFi icon resources.
  - Restart SystemUI after changing the icon style.
- `VoWiFi icon style = Hook array`
  - Hook target: `com.android.systemui`
  - Does not modify global properties.
  - Replaces `ImsUpdateFeature#getSingleCardImsIconArrayResId()` with `bd_single_card_volte_vowifi_icons_array`.
  - Tested working with dual SIM on the current ROM, but depends on the current ROM method name.

`Root Global Actual Values` is meaningful only in Root Global Mode. LSPosed Mode does not modify global properties, so `getprop` values do not indicate whether hooks are active.

After switches change, the app saves hook settings immediately. If `XSharedPreferences` cannot be read, hooks fail closed:

- WFC Settings hook: off
- SystemUI abroad hook: off
- icon style override: default / off

## Theme

The app uses Android's non-light Material theme, so the light/dark appearance follows the system setting.

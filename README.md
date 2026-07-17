# RedMagicX

Language: English | [繁體中文](readme_TW.md)

Unofficial RedMagic tweak toolkit for VoWiFi UI, volume step, and assistant gesture control.

Tested device: RedMagic / Nubia NX809J China ROM. Other RedMagic / Nubia models have not been tested, but may work in theory if their Settings/SystemUI implementation uses the same ZTE properties and classes.

<img src="img/app截圖main.jpg" alt="RedMagicX main screen" width="360">

## Table of Contents

- [Installation](#installation)
- [LSPosed Scopes](#lsposed-scopes)
- [Features](#features)
  - [VoWiFi UI Fix](#vowifi-ui-fix)
  - [Volume Step Control](#volume-step-control)
  - [Assistant Gesture Redirect](#assistant-gesture-redirect)
- [Build](#build)
- [Notes](#notes)

## Installation

Download the APK from [GitHub Releases](https://github.com/XPRAMT/RedMagicX/releases) and install it directly on the phone.

For VoWiFi carrier capability, install [Pixel IMS](https://github.com/kyujin-cho/pixel-volte-patch) first and use it to enable VoWiFi. RedMagicX mainly fixes China ROM UI behavior: missing VoWiFi Settings toggle and missing/status-bar icon behavior.

Grant root permission for the in-app process restart buttons:

<img src="img/授予root權限.jpg" alt="Grant root permission" width="360">

## LSPosed Scopes

Enable RedMagicX in LSPosed, then select scopes based on the features you use:

| Feature | Required scope |
|---|---|
| VoWiFi UI Fix | `com.android.settings`, `com.android.systemui` |
| Volume Step Control | `android` / System Framework |
| Assistant Gesture Redirect | `com.android.systemui` |

After changing VoWiFi or assistant settings, restart Settings/SystemUI from the app so the target process reloads the settings.

Select LSPosed scopes:

<img src="img/選擇lsposed作用域.jpg" alt="Select LSPosed scopes" width="360">

## Features

### VoWiFi UI Fix

Fixes China ROM VoWiFi UI behavior through LSPosed hooks only. It does not write global `resetprop` values.

<img src="img/app截圖1.jpg" alt="VoWiFi UI Fix settings" width="360">

Switch mapping:

| Switch | Hook target | Behavior |
|---|---|---|
| `Enable VoWiFi settings` | `com.android.settings` | Makes Settings read `ro.vendor.feature.zte_feature_need_wfc_for_domestic=true` so the Wi-Fi Calling / VoWiFi toggle appears. |
| `Enable status bar VoWiFi icon` | `com.android.systemui` | Makes IMS/status-icon code read `ro.vendor.mifavor.custom=abroad` / `ro.mifavor.custom=abroad`, while navigation/assistant code stays on `home` to avoid breaking the gesture bar. |
| `VoWiFi icon style = GEN_BD` | `com.android.systemui` | Makes SystemUI read `persist.custom.variant.id=GEN_BD`, using BD-style VoWiFi resources. Restart SystemUI after changing. |
| `VoWiFi icon style = Hook array` | `com.android.systemui` | Replaces the IMS icon array result with the BD array. Tested working with dual SIM on the current NX809J ROM, but depends on the current ROM method name. |

VoWiFi icon style comparison:

Default style uses `statusbar_vowifi.svg`:

<img src="img/icons/statusbar_vowifi.svg" alt="Default statusbar_vowifi icon" width="220">

BD style uses `bd_stat_vowifi.svg`:

<img src="img/icons/bd_stat_vowifi.svg" alt="BD bd_stat_vowifi icon" width="220">

### Volume Step Control

Lets you customize how many media-volume levels one hardware volume-key press changes.

<img src="img/app截圖2.jpg" alt="Volume Step Control settings" width="360">

- Range: `1` to `10`
- Hook target: Android/System Framework
- Effect: modifies media-volume key adjustment behavior through LSPosed

### Assistant Gesture Redirect

Redirects the RedMagic bottom gesture-bar long-press assistant event.

<img src="img/app截圖3.jpg" alt="Assistant Gesture Redirect settings" width="360">

Targets:

- System actions: assistant, voice assistant, recents, screenshot, flashlight
- User apps
- System apps

This does not modify the system default assistant setting. It intercepts SystemUI before the RedMagic assistant broadcast opens the original assistant target.

## Build

```powershell
& 'C:\Users\XPRAMT\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat' -p 'D:\Android\ZTE\VoWiFI\lsposed-redmagic-vowifi' assembleDebug
```

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Notes

- RedMagicX uses LSPosed `XSharedPreferences`.
- The module declares `xposedminversion=93` and `xposedsharedprefs=true`.
- The package name is intentionally kept as `dev.xpramt.redmagicvowifi` for upgrade compatibility.
- This is an unofficial project and is not affiliated with RedMagic, Nubia, or ZTE.

# RedMagicX

Language: English | [繁體中文](readme_TW.md)

RedMagicX is an unofficial RedMagic / Nubia tweak app for LSPosed, root, and Shizuku workflows. It focuses on China ROM UI behavior that can be fixed without replacing the ROM: VoWiFi UI, media volume-key step size, bottom gesture-bar assistant redirection, and third-party launcher control.

Tested device: RedMagic / Nubia NX809J China ROM. Other RedMagic / Nubia models have not been tested, but may work if their ROM uses the same ZTE classes and properties.

<img src="img/app截圖main.jpg" alt="RedMagicX main screen" width="360">

## Table of Contents

- [Installation](#installation)
- [LSPosed Scopes](#lsposed-scopes)
- [Features](#features)
  - [VoWiFi UI Fix](#vowifi-ui-fix)
  - [Volume Step Control](#volume-step-control)
  - [Assistant Gesture Redirect](#assistant-gesture-redirect)
  - [Third-Party Launcher](#third-party-launcher)
- [Build](#build)
- [Notes](#notes)

## Installation

Download the APK from [GitHub Releases](https://github.com/XPRAMT/RedMagicX/releases) and install it directly on the phone.

For VoWiFi carrier capability, install [Pixel IMS](https://github.com/kyujin-cho/pixel-volte-patch) first and use it to enable VoWiFi. RedMagicX mainly fixes China ROM UI behavior: the missing Wi-Fi Calling / VoWiFi Settings toggle and status-bar VoWiFi icon behavior.

Root is used only for actions that execute shell commands, such as restarting Settings/SystemUI and applying the default launcher when Shizuku is not used.

<img src="img/授予root權限.jpg" alt="Grant root permission" width="360">

Select RedMagicX scopes in LSPosed:

<img src="img/選擇lsposed作用域.jpg" alt="Select LSPosed scopes" width="360">

## LSPosed Scopes

Enable RedMagicX in LSPosed, then select scopes based on the features you use:

| Feature | Required scope |
|---|---|
| VoWiFi UI Fix | `com.android.settings`, `com.android.systemui` |
| Volume Step Control | `android` / System Framework |
| Assistant Gesture Redirect | `com.android.systemui` |
| Third-Party Launcher recent-task hiding | `com.zte.mifavor.launcher` |

Settings are saved with LSPosed `XSharedPreferences`. If a target process already loaded before a setting change, restart the related process from the app when available, or reboot when the target is `android` / system_server.

## Features

### VoWiFi UI Fix

Uses LSPosed hooks only. It does not globally modify system properties with `resetprop`.

<img src="img/app截圖1.jpg" alt="VoWiFi UI Fix settings" width="360">

| Switch | Scope | What it changes |
|---|---|---|
| Enable VoWiFi settings | `com.android.settings` | Makes Settings read `ro.vendor.feature.zte_feature_need_wfc_for_domestic=true`, so the Wi-Fi Calling / VoWiFi toggle appears. Pixel IMS or carrier config is still required to enable WFC capability. |
| Enable status-bar VoWiFi icon | `com.android.systemui` | Makes IMS/status-icon code read `ro.vendor.mifavor.custom=abroad` / `ro.mifavor.custom=abroad`, while navigation and assistant code stays on `home` to avoid breaking the gesture bar. |
| VoWiFi icon style = GEN_BD | `com.android.systemui` | Makes SystemUI read `persist.custom.variant.id=GEN_BD`, selecting BD-style VoWiFi resources. Restart SystemUI after changing. |
| VoWiFi icon style = Hook array | `com.android.systemui` | Replaces the IMS icon array result with the BD array. Tested working with dual SIM on the current NX809J ROM; it depends on the current ROM method name. |

VoWiFi icon style comparison:

Default style uses `statusbar_vowifi.svg`:

<img src="img/icons/statusbar_vowifi.svg" alt="Default statusbar_vowifi icon" width="220">

BD style uses `bd_stat_vowifi.svg`:

<img src="img/icons/bd_stat_vowifi.svg" alt="BD bd_stat_vowifi icon" width="220">

### Volume Step Control

Customizes how many media-volume levels one hardware volume-key press changes.

<img src="img/app截圖2.jpg" alt="Volume Step Control settings" width="360">

- Range: `1` to `10`
- Scope: `android` / System Framework
- Hook target: `com.android.server.audio.AudioService`
- Hooked behavior: media-volume key up/down step size
- Activation: enable the switch, set the step value, then reboot so system_server loads the LSPosed module

### Assistant Gesture Redirect

Redirects the RedMagic bottom gesture-bar long-press assistant event.

<img src="img/app截圖3.jpg" alt="Assistant Gesture Redirect settings" width="360">

Targets:

- System actions: Assistant, voice assistant, recents, screenshot, flashlight
- User apps
- System apps

This feature does not change Android's default assistant setting. It intercepts the SystemUI broadcast path before the original RedMagic assistant target opens, then launches the selected target.

### Third-Party Launcher

Sets a selected launcher as the default HOME activity and can hide that launcher from RedMagic's recent apps list in gesture mode.

- Change launcher: uses Android's own `cmd package set-home-activity --user 0 <component>`.
- Permission path: root is tried first; if root is unavailable, Shizuku shell permission is used.
- Result notice: after the command finishes, the app shows one Toast such as `Set <launcher> as default launcher`.
- Hide from recents: requires LSPosed scope `com.zte.mifavor.launcher`. It hooks RedMagic Launcher `RecentsView#onGestureAnimationStart`; only when gesture mode current task is the selected third-party HOME, it prevents that task from being inserted as a recent-task card.

## Build

```powershell
& 'C:\Users\XPRAMT\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat' -p 'D:\Android\ZTE\VoWiFI\lsposed-redmagic-vowifi' assembleDebug
```

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Notes

- The package name is intentionally kept as `dev.xpramt.redmagicvowifi` for upgrade compatibility.
- The module declares `xposedminversion=93` and `xposedsharedprefs=true`.
- Shizuku support is used only for applying the default launcher without root.
- This is an unofficial project and is not affiliated with RedMagic, Nubia, or ZTE.

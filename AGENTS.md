# RedMagicX Project Notes

## Current State

- Android application and LSPosed module for RedMagicOS features.
- Main UI is implemented programmatically in `app/src/main/java/dev/xpramt/redmagicvowifi/MainActivity.java`.
- Current feature pages include VoWiFi UI, volume step, assistant gesture, launcher, theme, ADB control, quick entry, and other system settings.
- The application version remains `0.4.0` unless a release request changes it.

## Other Settings Page

- Home page includes an `其它` card below `快速入口`.
- `其它` currently contains `螢幕亮屏時間設定`.
- The slider range is 1 to 60 minutes with 1-minute steps.
- Entering the page reads `Settings.System.SCREEN_OFF_TIMEOUT` again so the slider reflects the current system value rather than a cached value.
- Applying a value first writes `Settings.System.screen_off_timeout` through `su`; no device reboot is required.
- If root is unavailable or the root write cannot be verified, the app falls back to Shizuku. When Shizuku is not yet authorized, it requests permission and automatically retries after permission is granted.
- The slider is disabled while the root/Shizuku write is running and then reflects the verified value. The displayed result identifies whether root or Shizuku performed the write.

## Bluetooth Receiver Page

- Home page has a `藍牙接收器` card between `音量步進調整` and `魔姬手勢替換`. The page lets the phone act as an A2DP receiver (another phone streams audio to it) instead of an A2DP source (headphones). The two roles are mutually exclusive because the Android Bluetooth stack on the NX809J did not run them together reliably.
- Implementation: LSPosed hook in `com.android.bluetooth` (`HookEntry.hookBluetoothRoles`). In receiver mode `A2dpSinkService.isEnabled()` returns true and `A2dpService.isEnabled()` returns false; these two static methods are the only readers of the sink/source system properties in the Bluetooth app, and `Config.PROFILE_SERVICES_AND_FLAGS` is built from them when the process starts. Normal mode installs no role hooks. The choice is read once at process start, so a mode change needs the Bluetooth stack restarted.
- Settings (prefs `module`): `bt_receiver_enabled` (default off) and `bt_auto_restart` (default off). The mode persists across reboots until switched off. With `bt_auto_restart` on, every mode switch restarts Bluetooth through root (`su`), falling back to Shizuku: `cmd bluetooth_manager disable`, wait for `STATE_OFF`, `enable`, wait for `STATE_ON`; it only runs when Bluetooth is currently on. A manual `立即重啟藍牙` button does the same.
- Status: the hook reports the mode it started in by broadcast (`dev.xpramt.redmagicvowifi.BT_MODE_REPORT`, handled by `BtModeReceiver`, stored as `bt_active_mode` / `bt_active_at`), and the page compares it with the configured mode. The receiver needs an `intent-filter` for that action, because the hook sends an action + package broadcast, not a component broadcast.
- LSPosed scope: `com.android.bluetooth` must be ticked for RedMagicX (it is in the recommended `xposed_scope` array, but the user has to enable it in the LSPosed manager).
- Limits: only SBC and AAC are decoded by the stack; the other phone still sees a phone-class device; headphones cannot connect in receiver mode.
- Not tested on a device yet: the hook itself (needs the scope), the page flow with root and with Shizuku, and the interplay with the experimental KernelSU module `ksu-hires-unlocker` (branch `a2dp-sink-experiment`, which sets the same two properties through `btmode.sh`); do not use both at the same time.

## Build

- Gradle wrapper is not checked into this repository.
- Known local Gradle command:

```powershell
& 'C:\Users\XPRAMT\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat' -p 'D:\Android\ZTE\VoWiFI\lsposed-redmagic-vowifi' assembleDebug
```

- Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Device Safety

- Do not reboot the phone automatically.
- Avoid `adb shell monkey`; on NX809J it can change automatic rotation. Use explicit `am start` when launching RedMagicX.

## APK Installation on NX809J

- Verified on 2026-09-18 that the connected NX809J has `global.adb_enabled=2`, `system.adb_install_enabled=1`, and root access through `su`.
- The installed RedMagicX package is `dev.xpramt.redmagicvowifi`; the current installed package uses the normal `/data/app` path.
- Every time a new APK is built, install it directly to the connected phone and launch RedMagicX for runtime verification. Do not stop after a successful build.
- `adb install -r` is safe on the NX809J and is the preferred installation method. The previous automatic-rotation observation was caused by launching with `adb shell monkey`, not by APK installation.

```powershell
& 'C:\APP\scrcpy-win64-v3.3.1\adb.exe' -s '192.168.1.9:5555' install -r 'app\build\outputs\apk\debug\app-debug.apk'
```

- If host-side installation is rejected, use the explicit device-side package-manager flow as a fallback:

```powershell
& 'C:\APP\scrcpy-win64-v3.3.1\adb.exe' -s '192.168.1.9:5555' push 'app\build\outputs\apk\debug\app-debug.apk' '/data/local/tmp/redmagicx.apk'
& 'C:\APP\scrcpy-win64-v3.3.1\adb.exe' -s '192.168.1.9:5555' shell su -c 'pm install -r /data/local/tmp/redmagicx.apk'
```

- Verify the returned package-manager result and `dumpsys package dev.xpramt.redmagicvowifi` after installation. Do not reboot the phone as part of installation verification.
- Launch the app without `adb shell monkey`, because `monkey` can change automatic rotation on NX809J:

```powershell
& 'C:\APP\scrcpy-win64-v3.3.1\adb.exe' -s '192.168.1.9:5555' shell am start -n 'dev.xpramt.redmagicvowifi/.MainActivity'
```

- After launching, verify that the activity starts successfully and inspect the targeted feature or logcat for the changed behavior. If a reboot is required for a feature, explain why and ask the user to reboot; never reboot automatically.

## KernelSU Module (ksu-hires-unlocker)

- `ksu-hires-unlocker/` is a separate KernelSU module (not an LSPosed module, not part of the APK) that lets the NX809J system mixer follow the audio file's sample rate on Bluetooth, wired headphones and USB audio. Details and measurements are in the README section "KernelSU Module: Hi-Res Unlocker" (English and Traditional Chinese, keep both in sync).
- The module is versioned independently of the app (module `v0.8.0`, app `0.4.0`). Its releases use the tag `hires-mixer-vX.Y.Z`, are not marked Latest, and attach only the module zip.
- Never commit or attach vendor files (stock or patched `.so`, stock or generated config). The installer builds the patched libraries and the mix-port config on the phone from its own files; `original/` is git-ignored for files pulled from the phone.
- Supported: model NX809J on Android 16 (checked by the installer and at boot). Tested only on RedMagicOS 11.0.22 (incremental `20260204.095106`); the installer finds patch sites by instruction bytes and aborts when they are not found as expected.
- The development history, the investigation data and the measurement tools live outside this repository (workspace `D:\Android\ZTE\VoWiFI`, own Git repository `ksu-hires-unlocker`).

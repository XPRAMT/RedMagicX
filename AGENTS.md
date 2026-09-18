# RedMagicX Project Notes

## Current State

- Android application and LSPosed module for RedMagicOS features.
- Main UI is implemented programmatically in `app/src/main/java/dev/xpramt/redmagicvowifi/MainActivity.java`.
- Current feature pages include VoWiFi UI, volume step, assistant gesture, launcher, theme, ADB control, quick entry, and other system settings.
- The application version remains `0.3.1` unless a release request changes it.

## Other Settings Page

- Home page includes an `其它` card below `快速入口`.
- `其它` currently contains `螢幕亮屏時間設定`.
- The slider range is 1 to 60 minutes with 1-minute steps.
- Entering the page reads `Settings.System.SCREEN_OFF_TIMEOUT` again so the slider reflects the current system value rather than a cached value.
- Applying a value first writes `Settings.System.screen_off_timeout` through `su`; no device reboot is required.
- If root is unavailable or the root write cannot be verified, the app falls back to Shizuku. When Shizuku is not yet authorized, it requests permission and automatically retries after permission is granted.
- The slider is disabled while the root/Shizuku write is running and then reflects the verified value. The displayed result identifies whether root or Shizuku performed the write.

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
- Do not assume `adb install -r` is the correct deployment path for this device. Prefer the explicit device-side package-manager flow when host-side installation is rejected:

```powershell
& 'C:\APP\scrcpy-win64-v3.3.1\adb.exe' -s '192.168.1.9:5555' push 'app\build\outputs\apk\debug\app-debug.apk' '/data/local/tmp/redmagicx.apk'
& 'C:\APP\scrcpy-win64-v3.3.1\adb.exe' -s '192.168.1.9:5555' shell su -c 'pm install -r /data/local/tmp/redmagicx.apk'
```

- Verify the returned package-manager result and `dumpsys package dev.xpramt.redmagicvowifi` after installation. Do not reboot the phone as part of installation verification.

# RedMagicX Project Notes

## Current State

- Android application and LSPosed module for RedMagicOS features.
- Main UI is implemented programmatically in `app/src/main/java/dev/xpramt/redmagicvowifi/MainActivity.java`.
- Current feature pages include VoWiFi UI, volume bar (step + AOSP dialog), assistant gesture, launcher, theme, ADB control, quick entry, Bluetooth receiver, and other system settings.
- The application version remains `0.4.0` unless a release request changes it.


## UI Spacing

All MainActivity spacing uses the constants at the top of `MainActivity` (dp); never hardcode `dp(N)` for margin/padding/gaps. Scale 4/8/12/16/20/24 (`SPACE_XS..SPACE_XXL`).
- Page: `PAGE_MARGIN_H`=20, `PAGE_MARGIN_TOP`=16, `PAGE_MARGIN_BOTTOM`=24. Toolbar: `TOOLBAR_PADDING_H/V`=8, `TOOLBAR_TITLE_INDENT`=24.
- Section (`sectionBox()`): `SECTION_PADDING_TOP`=16, `SECTION_PADDING_BOTTOM`=8; `SECTION_TITLE_TOP_SPACING_DP`=20 added automatically by `sectionBox().addView` before every title (bold TextView/Switch >= 17sp) except the first child.
- Items: `ITEM_GAP`=12 (`verticalSpace`, row buttons), `GROUP_GAP`=24, `FIELD_LABEL_TOP`=8 (`addDetailField` labels).
- Cards (`featureButton`, list cards): `CARD_PADDING_H`=16, `CARD_PADDING_V`=12, `CARD_MARGIN_TOP`=8, `CARD_MARGIN_BOTTOM`=4, `ICON_GAP`=12.
- Inputs/buttons/dialogs: `INPUT_PADDING_H`=12, `BUTTON_PADDING_H`=16, `DIALOG_PADDING_H`=24, `DIALOG_PADDING_TOP`=8.
- Sizes (48dp touch targets, 44dp icons, corner radius 8) are not spacing and stay literal. HookEntry's injected SystemUI panel has its own dp values, outside this spec.
## Volume Bar Page

- Page order: 啟用自訂音量步進 (+ seekbar) → 音量變化通知即時送達 → 使用AOSP音量條 (bottom) → 重啟 SystemUI button. The button (no confirm dialog) immediately runs on a background thread `su -c 'p=$(pidof com.android.systemui); [ -z $p ] && exit 2; kill $p'` and Toasts the result (exit code + output on failure). Agents must never press it or run that command without the user's explicit yes.

- Home card title is `音量條` (renamed from `音量步進調整`).
- Prefs key `aosp_volume_dialog` (default **off**).
- **Do not** force `ClassAdaptor.enabled()` / `VolumeDialogImplAdapt.enabled()` to false. On NX809J the live controller is `MfvVolumeDialogImpl`; after a legacy inflate, `mfvInitDialog()` still `findViewById`s MFV-only widgets and NPEs, which crashed SystemUI.
- Safe path when the switch is on (SystemUI scope):
  - `VolumeDialogImplAdapt.getDialogLayoutResId` / `getRowLayoutResId` return the call-site AOSP ids (`volume_dialog_legacy` / `volume_dialog_row`).
  - Skip `MfvVolumeDialogImpl.mfvInitDialog` / `prepareDialog` / MFV expand+ringer updates; route row add/remove to `mDialogRowsView`.
- Prefs keys `volume_step_enabled` / `volume_step` are unchanged (AudioService hooks in `android`).
- `VolumeAdjustHook` calls `setStreamVolume` inside `Binder.clearCallingIdentity()`/`restoreCallingIdentity`, so it runs as uid 1000 even when the adjust comes from a third-party app (e.g. Ultra Volume `com.treydev.volume`). Without it, AppOps rejected package "android" under the app uid, `AUDIO_MEDIA_VOLUME` was denied and HardeningEnforcer ignored the call, while the hook still swallowed the original adjust (keys did nothing). Verified against the device services.jar: `HardeningEnforcer.blockVolumeMethod` returns false when `checkCallingOrSelfPermission(MODIFY_AUDIO_SETTINGS_PRIVILEGED)` passes, and `setStreamVolume` uses `Binder.getCallingUid()` for `checkNoteAppOp`. The log records success with `callerUid`, or the full exception stack. Takes effect only after a reboot (android scope).
- `hookVolumeChangedBroadcastDelay` (android scope, toggle pref `volume_broadcast_undelay`, default true; unreadable pref = true; when false the hook is not installed and logs "broadcast undelay disabled by pref"; UI switch 音量變化通知即時送達 on the 音量條 page; change needs reboot): `BroadcastProcessQueue.updateRunnableAt` after-hook. When the reason is 2 (NORMAL, `DELAY_NORMAL_MILLIS` 500 ms) and the next record is `VOLUME_CHANGED_ACTION`, set `mRunnableAt` to the record enqueue time. Reason: background volume apps (Ultra Volume, procstate 5) got every volume broadcast about 500 ms late, so the panel lagged. `FLAG_RECEIVER_FOREGROUND` was not used because AudioService sends with deferralPolicy UNTIL_ACTIVE, and foreground+deferred is excluded (`mCountForeground > mCountForegroundDeferred`). Delivery-group (most recent) policy and cached/frozen deferral are untouched. Logs: "VOLUME_CHANGED_ACTION broadcast undelay hook installed" and one "normal delay removed (first applied)".
- Changing the AOSP volume dialog switch requires restarting SystemUI (or a reboot). Do not reboot automatically.
- `xposed_scope` already includes `com.android.systemui`.


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


## Wireless ADB Boot Restore

- The ADB page enables wireless ADB through root: `su -c "setprop service.adb.tcp.port <port>; setprop ctl.restart adbd"` (no Xposed hook). Prefs `wireless_adb_enabled` / `wireless_adb_port` (default 5555).
- `BootReceiver` (BOOT_COMPLETED / QUICKBOOT_POWERON / MY_PACKAGE_REPLACED, `RECEIVE_BOOT_COMPLETED`) reapplies the saved setting on every boot when `wireless_adb_enabled` is true; retries su up to 6 times, 3 s apart. Always on, no UI toggle (user request).
- Verified on NX809J 2026-10-31: after `adb reboot`, `adb connect 192.168.1.10:5555` worked ~1 min after boot without opening the app.
- Boot launcher restore: `BootReceiver` (BOOT_COMPLETED / QUICKBOOT_POWERON / MY_PACKAGE_REPLACED), when `launcher_override_enabled` is true and `launcher_component` is a non-stock, non-FallbackHome component, waits 5 s and then reruns `su -c cmd package set-home-activity --user 0 <component>` (same as the in-app switch, up to 5 tries 3 s apart), then `am start` HOME on that component once. Reason: after a reboot the stock mifavor Quickstep keeps using its own desktop for the home gesture, even when the system default is a third-party launcher. It only switches to fallback (mifavor RecentsActivity -> default HOME) after set-home-activity is run again. Runs in the same async thread as the wireless ADB restore, with independent try/catch. Log tag `RedMagicX-Boot`. No toggle; this is the default behavior. Note: on RedMagicOS, MY_PACKAGE_REPLACED from `adb install -r` did not start the app process (2026-10-31), so this only takes effect on a real boot.

- Boot launcher restore update: `restoreLauncher` first sets HOME to the stock `com.zte.mifavor.launcher/com.android.launcher3.uioverrides.QuickstepLauncher`, waits 1 s, then sets the saved component and runs `am start` on it. Reason: in the full-reboot test on 2026-10-31 20:08 (UTC+8), re-setting the same HOME succeeded but the stock Quickstep still kept the home gesture. The manual switch only worked because the default actually changed.
- RedMagicOS autolaunch policy must allow RedMagicX (startup management / 自啟動). Otherwise ZBrPolicy logs `BootReceiver ... SKIPPED ... autolaunch policy` for BOOT_COMPLETED and MY_PACKAGE_REPLACED, and nothing runs.
- A soft reboot (system_server restart; `/proc/stat` btime unchanged) keeps `service.adb.tcp.port`, so it does not prove the wireless ADB restore. The full-reboot test at 2026-10-31 20:08 (UTC+8) passed: `RedMagicX-Boot: wireless adb enabled on port 5555` at 20:09:23.

## AOSP volume dialog (SystemUI) - crash notes (2026-10-31)
- Code: `AospVolumeHooks.java` (called from `HookEntry.hookAospVolumeDialog`).
- Crash cause: old hooks skipped `mfvInitDialog`/`prepareDialog`, leaving MFV-only fields null (`mDialogMain`, `mDialogRowsView2`, ringer buttons, expand button). `MfvVolumeDialogImpl.updateRowsH` (from `VolumeDialogImpl.initDialog` at SystemUI start) calls `mDialogMain.setVisibility` -> NPE -> SystemUI crash loop (402 crashes 20:18-20:25, reboot also looped).
- Fix: `prepareDialog` fills `mDialogMain`/`mDialogRowsView2` with detached dummy FrameLayouts; skip `mfvInitDialog`, `updateExpandedH`, `updateRingerMfv`, `updateRingerTypeface`, `initRingerButtonXPositionsInParent`, `startExpandedAnimation`, `prepareForCollapse`, `updateRowsWidth`; `addRowView`/`removeRowView` use `mDialogRowsView`.
- Safety net: after-hook on every declared `MfvVolumeDialogImpl` method swallows Throwables while AOSP mode is active (logs once per method).
- Crash-loop guard: start timestamps in `<systemui deviceProtectedDataDir>/redmagicx_aosp_volume_starts`; >=3 starts in 90 s disables AOSP mode for that process (MFV fallback, logged).
- Do NOT use `invokeOriginalMethod` on the super `VolumeDialogImpl.updateRowsH`: it dispatches virtually back to the subclass (recursion).
- Live pref read by hooks: `/data/misc/apexdata/a6a1d3cf-da33-4201-81e9-3d26fa1071c3/prefs/dev.xpramt.redmagicvowifi/module.xml` (`/data/data/<pkg>/shared_prefs/module.xml` is NOT what hooks read). Emergency recovery: set `aospVolumeDialog` to false there.
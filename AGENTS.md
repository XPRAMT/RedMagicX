# RedMagicX Project Notes

## Current State

- Android application and LSPosed module for RedMagicOS features. The module uses the modern libxposed API 102 only (no legacy Xposed API, no compatibility layer; user rule 2026-11-10). It needs an LSPosed build with API 102 (NX809J LSPosed 2.2.1 reports `api=102`); the Xiaomi Pad 6 runs LSPosed 2.0.2, which has not been checked for API 102 and may not load the module.
- Main UI is implemented programmatically in `app/src/main/java/dev/xpramt/redmagicvowifi/MainActivity.java`.
- Current feature pages include VoWiFi UI, volume bar (step + AOSP dialog), assistant gesture, launcher, theme, ADB control, quick entry, Bluetooth receiver, and other system settings.
- The application version remains `0.6.0` unless a release request changes it.


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
- `META-INF/xposed/scope.list` already includes `com.android.systemui`.


## Modern API layout

- Entry: `HookEntry extends XposedModule` (`META-INF/xposed/java_init.list`). `onModuleLoaded` calls `Xp.init` and `Config.useRemotePrefs`; `onSystemServerStarting` installs the system_server hooks (AudioService volume step, recent tasks filter); `onPackageReady` installs the per-package hooks (Settings, SystemUI, Bluetooth, launcher, Beautify). There is no `assets/xposed_init` and no `xposed*` manifest meta-data (those made the LSPosed manager show the legacy / deprecated warning).
- `META-INF/xposed/module.prop`: `minApiVersion=102`, `targetApiVersion=102`, `staticScope=true`. `scope.list`: `system` (system_server; the old `android` scope), `com.android.settings`, `com.android.systemui`, `com.android.bluetooth`, `com.zte.mifavor.launcher`, `com.zte.beautify`, `com.zte.beautifyadapter`. Module description is `android:description` in the manifest.
- `Xp.java`: `MethodHook.before/after` + `Param` (thisObject, args, setResult, getThrowable) on top of `hook(executable).intercept(chain -> ...)` with `ExceptionMode.PASSTHROUGH` and the helper's own try/catch (a failing callback is logged and the original call proceeds); reflection helpers (`findClass`, `getObjectField`, `callMethod`, ...) because the modern API ships none. Hooks keep the old before/after shape; do not reintroduce `XposedHelpers`/`XposedBridge`/`XC_MethodHook`.
- Build: AGP 9.2.1, Gradle 9.7.1, compileSdk 37 (libxposed 102 requires it), Java 17; `compileOnly io.github.libxposed:api:102.0.0`, `implementation io.github.libxposed:service:102.0.0`.
- The module app is no longer hooked by itself.

## Hot reload (API 102)

- `module.prop` has `autoHotReload=true`: installing a new APK reloads the module in every hooked process without restarting it (verified 2026-11-10 on NX809J, LSPosed 2.2.1: `system`, systemui, settings, launcher, bluetooth, beautifyadapter reloaded within 50 ms of `adb install -r`, all `hooks reinstalled`, no errors). A process that is not running (for example `com.zte.beautify`) simply loads the new code at its next start.
- `HookEntry.onHotReloading` keeps every hooked package (name + class loader, JDK types only) in `setSavedInstanceState`, stops `ProviderPrefs`' thread and returns true (false when nothing was hooked yet). `onHotReloaded` calls `super` (unhooks the old generation), re-inits `Xp`/`Config` and reinstalls the hooks for each saved package (`Xp.hotReloading` is true meanwhile so a reload is not counted as a SystemUI start by the AOSP volume crash-loop guard).
- Every log line starts with `[<build marker>]` (`Config.BUILD_MARKER` = `BuildConfig.BUILD_MARKER`, a per-build timestamp). After a reload no line without the new marker may appear; one without it means an old hook was not unhooked.
- Manual trigger (adb shell / root only): `content call --uri content://dev.xpramt.redmagicvowifi.themeconfig --method hot_reload --arg <process name>`; the result lists every hooked target and its state (`UP_TO_DATE`/`STALE`). The first install of the hot-reload-capable build needs one restart of each target process (system_server: soft reboot) because the old code has no reload callbacks.
- Not covered by a hot reload: the Bluetooth role (receiver / source) is decided when the Bluetooth process starts; a reload reinstalls the hooks but does not switch the mode. Not tested: repeated reloads in a row, a reload while a hooked call is running, the Xiaomi Pad.

## Settings Storage (framework independent)

- **Source of truth: the app's own file** `<app files>/settings.json`, implemented by `FilePrefs` (a `SharedPreferences` backed by atomic JSON writes). It lives in `filesDir`, which no framework touches. `Config.appPrefs()` returns it; every page, receiver and the provider use it.
- **Framework copy (remote preferences, group `module`):** `FrameworkSync` (app side) registers `XposedServiceHelper` once per process (started from `Config.appPrefs`, which `ThemeConfigProvider.onCreate` also triggers, so a hooked process asking the provider starts the app and the binder arrives), and publishes the app's values to `XposedService.getRemotePreferences("module")` when the service binds and on every committed change (clear + put all, one commit; skipped when equal). It checks `PROP_CAP_REMOTE`.
- **Hook side:** `Config.hookPrefs()` reads the remote preferences (`module.getRemotePreferences`, obtained in `onModuleLoaded`) when they hold values, otherwise asks the app through `ThemeConfigProvider` method `get_all` (`ProviderPrefs`: cached, refreshed every 3 s in the background, never blocks `system_server`; other processes wait at most 1.5 s for the first answer), otherwise nothing is set (every default, i.e. every feature off).
- **One-time import** (`Config.importOlderCopy`, only when `settings.json` does not exist yet): from `shared_prefs/module.xml`, parsed straight from the file.
- Why: LSPosed's `uninstall.sh` runs `rm -rf /data/adb/lspd`, which holds the remote preferences, so a replaced or reinstalled framework starts empty; the app file survives and republishes at the next app start.
- Verified on 2026-11-10 on NX809J (LSPosed 2.2.1, api=102): the new APK installs, the app publishes 29 `module` keys to the framework database, and after a soft reboot the module loaded in system_server (volume step 5, broadcast undelay, recent-task filter), SystemUI, Settings, launcher, beautifyadapter, Bluetooth (receiver mode, mode report reaches `BtModeReceiver`) and Beautify (theme page, user-confirmed), all reading the settings with no hook errors. Not verified: the Xiaomi Pad (LSPosed 2.0.2 may not support API 102), and the LSPosed manager no longer showing the legacy / deprecated warning.

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
- LSPosed scope: `com.android.bluetooth` must be ticked for RedMagicX (it is in `META-INF/xposed/scope.list`; `staticScope=true` fixes the scope to that list).
- Limits: only SBC and AAC are decoded by the stack; the other phone still sees a phone-class device; headphones cannot connect in receiver mode.
- Not tested on a device yet: the hook itself (needs the scope), the page flow with root and with Shizuku, and the interplay with the experimental A2DP sink build of the KernelSU module (branch `a2dp-sink-experiment` in the old development repository `D:\Android\ZTE\VoWiFI\ksu-hires-unlocker`, not in the standalone repository; it sets the same two properties through `btmode.sh`); do not use both at the same time.

## Build

- Gradle wrapper is not checked into this repository.
- Known local Gradle command:

```powershell
& 'C:\Users\XPRAMT\.gradle\wrapper\dists\gradle-9.7.1-bin\1w1c7tv4s851m17nbqdsro2tv\gradle-9.7.1\bin\gradle.bat' -p 'D:\Android\ZTE\VoWiFI\lsposed-redmagic-vowifi' assembleDebug
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

- The Hi-Res Unlocker KernelSU module lives in its own repository, https://github.com/XPRAMT/ksu-hires-unlocker (local `D:\Android\ksu-hires-unlocker`): code, docs, releases (`hires-mixer-v0.7.0`, `hires-mixer-v0.8.0`, later `vX.Y.Z` (one release with every device's zip)). It is not part of the APK.
- This repository contains no module files and no module releases; the README only links to the standalone repository.


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
- Hooks no longer depend on the framework's folder for the settings; see "Settings Storage" above. Emergency recovery for the AOSP volume dialog: set `aosp_volume_dialog` to false in the app's own `files/settings.json` (and in the framework copy, if there is one).
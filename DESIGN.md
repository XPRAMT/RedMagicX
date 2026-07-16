# Design

## Goal

Provide three independent LSPosed toggles for NX809J China ROM VoWiFi behavior:

1. Show and enable the VoWiFi setting entry.
2. Show the status-bar VoWiFi icon by entering SystemUI's abroad IMS icon branch.
3. Match the international ROM's BD-style VoWiFi icon resources.

## Toggle 1: Enable VoWiFi

UI label: `開啟 VoWiFi 設定`

Exact effective parameter:

```properties
ro.vendor.feature.zte_feature_need_wfc_for_domestic=true
```

Target package:

```text
com.android.settings
```

Known code path:

```text
com.zte.settings.network.telephony.ZteWifiCallingRepository#wifiCallingReadyFlow()
```

The China Settings app hides WFC when the project is not abroad and this property is not true.
The hook should return `true` for boolean property reads of this key inside Settings.

This toggle does not replace Pixel IMS. Pixel IMS or equivalent carrier config is still needed for:

```properties
carrier_wfc_ims_available_bool=true
show_wifi_calling_icon_in_status_bar_bool=true
```

## Toggle 2: Enable Status-Bar VoWiFi Icon

UI label: `開啟狀態列 VoWiFi 圖標`

Exact effective parameters:

```properties
ro.vendor.mifavor.custom=abroad
ro.mifavor.custom=abroad
```

Target package:

```text
com.android.systemui
```

Known code path:

```text
com.zte.feature.signal.ImsUpdateFeature#updateImsStateAndResources(int)
```

Observed behavior:

- Non-abroad branch only shows HD / HD+.
- Abroad branch maps WFC state to `PhoneStatus.SHOW_VOWIFI`.

The hook should make SystemUI's flavor/property reads behave as abroad. Scope this to SystemUI only.

## Toggle 3: VoWiFi Icon Style

UI label: `VoWiFi 圖標樣式`

Options:

```text
default
gen_bd
array_hook
```

### Option: default

No override. With toggle 2 enabled, China ROM usually uses:

```text
vowifi
vowifi_card1
vowifi_card2
vowifi_card12
```

### Option: gen_bd

Exact effective parameter:

```properties
persist.custom.variant.id=GEN_BD
```

Target package:

```text
com.android.systemui
```

Known code path:

```text
com.zte.customize.OperatorUtils
com.zte.feature.signal.ImsUpdateFeature#getSingleCardImsIconArrayResId()
```

SystemUI maps `GEN_BD`, `OPT_AU`, and `UG_BG` to BD-style IMS icon arrays:

```text
bd_single_card_volte_vowifi_icons_array
bd_dual_card_volte_vowifi_icons_array
```

This selects resources matching the international ROM observation:

```text
bd_stat_vowifi
bd_vowifi_card1
bd_vowifi_card2
bd_vowifi_card12
```

`GEN_BD` is preferred over `OPT_AU` because `OPT_AU` also enables Australia emergency and other AU-specific branches.

### Option: array_hook

Exact effective behavior:

```text
Force ImsUpdateFeature single-card icon array to bd_single_card_volte_vowifi_icons_array.
Map default dual-card array selection to bd_dual_card_volte_vowifi_icons_array before first use when possible.
```

This is the cleanest long-term behavior but is more brittle because it depends on resource names and method availability.
The first implementation should ship `gen_bd`; `array_hook` can be enabled after validation.

## Process Restart Requirements

`OperatorUtils` and `ImsUpdateFeature` cache values during class/static initialization.
After toggling SystemUI options, restart SystemUI:

```sh
su
kill -9 $(pidof com.android.systemui)
```

After toggling Settings:

```sh
am force-stop com.android.settings
```

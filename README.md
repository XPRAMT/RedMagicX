# RedMagic VoWiFi LSPosed Module

Target device: RedMagic / Nubia NX809J China ROM.

This module is designed around the findings in `D:\Android\ZTE\VoWiFI\agent.md`.
It limits changes to target processes instead of permanently changing global properties.

## Switches

1. Enable VoWiFi Settings
   - Target package: `com.android.settings`
   - Effective parameter: `ro.vendor.feature.zte_feature_need_wfc_for_domestic=true`
   - Implementation: hook ZTE property reads in Settings so the domestic WFC gate returns true.
   - This exposes the Settings VoWiFi switch. Carrier IMS availability still depends on Pixel IMS / carrier config.

2. Enable Status-Bar VoWiFi Icon
   - Target package: `com.android.systemui`
   - Effective parameter: `ro.vendor.mifavor.custom=abroad` / `ro.mifavor.custom=abroad`
   - Implementation: hook SystemUI property/flavor reads so `FlavorUtils.isAbroad()`-gated icon logic uses the abroad branch.
   - This changes SystemUI icon state selection from HD-only to VoLTE/VoWiFi capable.

3. VoWiFi Icon Style
   - Target package: `com.android.systemui`
   - Mode `default`: no style override.
   - Mode `gen_bd`: effective parameter `persist.custom.variant.id=GEN_BD` only inside SystemUI.
   - Mode `array_hook`: directly force `ImsUpdateFeature.getSingleCardImsIconArrayResId()` to BD array when possible.
   - `gen_bd` is the safer first implementation because it uses existing ZTE operator branch logic.

## Apply Changes

After changing settings, restart the affected process:

```sh
su
kill -9 $(pidof com.android.systemui)
am force-stop com.android.settings
```

For LSPosed, scope this module to:

- Android System / System Framework if needed by LSPosed
- `com.android.settings`
- `com.android.systemui`

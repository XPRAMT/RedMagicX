#!/system/bin/sh
# Safety guard: after a ROM update the stock files change; an outdated overlay (especially the audio
# policy and HAL libraries) must not be mounted, so the whole module is skipped (skip_mount).
MODDIR=${0%/*}
. "$MODDIR/common.sh"
ok=1
[ "$(getprop ro.product.model)" = "$REQ_MODEL" ] || ok=0
[ "$(getprop ro.build.version.sdk)" = "$REQ_SDK" ] || ok=0
if [ -f "$MODDIR/guard.conf" ]; then . "$MODDIR/guard.conf"; else ok=0; fi
if [ "$ok" = 1 ]; then
  cx=$(sha256sum /vendor/etc/audio/sku_canoe/audio_module_config_primary.xml 2>/dev/null | cut -d' ' -f1)
  { [ "$cx" = "$XML_STOCK_SHA" ] || [ "$cx" = "$XML_OUT_SHA" ]; } || ok=0
  [ "$(stock_sha /system/lib64/libaudiopolicymanagerdefault.so "$APM_OLD_OCT" $APM_OFFS)" = "$APM_STOCK_SHA" ] || ok=0
  [ "$(stock_sha /vendor/lib64/hw/libaudiocorehal.qti.so "$HAL_OLD_OCT" $HAL_OFFS)" = "$HAL_STOCK_SHA" ] || ok=0
  [ "$(stock_sha /system/bin/audioserver "$AS_OLD_OCT" $AS_OFFS)" = "$AS_STOCK_SHA" ] || ok=0
fi
if [ "$ok" = 1 ]; then
  rm -f "$MODDIR/skip_mount"
  RP=$(command -v resetprop || echo /data/adb/ksu/bin/resetprop)
  "$RP" -n ro.audio.flinger_standbytime_ms "$STANDBY_MS"      # read by audioserver when it starts
else
  touch "$MODDIR/skip_mount"
fi

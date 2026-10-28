#!/system/bin/sh
# Installer. Supported: model NX809J on Android 16. Nothing vendor-owned is shipped in this zip:
# the patched libraries and the mix-port config are built here from the phone's own files. Patch
# sites are found by their instruction bytes and must match exactly once, otherwise the install aborts.
SRCDIR=${MODPATH:-${0%/*}}
. "$SRCDIR/common.sh"
: "${XML:=/vendor/etc/audio/sku_canoe/audio_module_config_primary.xml}"
: "${LIB:=/system/lib64/libaudiopolicymanagerdefault.so}"
: "${HAL:=/vendor/lib64/hw/libaudiocorehal.qti.so}"
: "${STOCKDIR:=/data/adb/bt_hires_mixer_stock}"
: "${MODEL:=$(getprop ro.product.model)}"
: "${SDK:=$(getprop ro.build.version.sdk)}"

[ "$MODEL" = "$REQ_MODEL" ] || abort "! Unsupported device '$MODEL' (this module is for $REQ_MODEL only)."
[ "$SDK" = "$REQ_SDK" ] || abort "! Unsupported Android version (SDK $SDK, need Android 16 = SDK $REQ_SDK)."

# build_lib <source> <dest> <old hex> <new hex> <old oct> <new oct> <bytes before the patch> <exact site count> <name>
# Copies the library and patches every site (or keeps it if already patched). Records the patch
# offsets and the stock-equivalent SHA-256 in $GUARD for the boot-time check.
build_lib() {
  src=$1; dst=$2; oh=$3; nh=$4; oo=$5; no=$6; skip=$7; need=$8; name=$9
  set -- $(find_pat "$src" "$oh"); co=$1; shift; po="$*"
  set -- $(find_pat "$src" "$nh"); cn=$1; shift; pn="$*"
  mkdir -p "${dst%/*}" || abort "! Cannot create ${dst%/*}"
  cp "$src" "$dst" || abort "! Cannot copy $src"
  tot=$((co + cn))
  if [ "$tot" -eq "$need" ]; then
    offs=""; for o in $po $pn; do offs="$offs $((o + skip))"; done
    if [ "$co" -gt 0 ]; then                                 # patch the sites that are still stock
      stk=""; for o in $po; do stk="$stk $((o + skip))"; done
      patch_at "$dst" "$no" $stk || abort "! Patch of $dst failed."
    fi
  else
    ui_print "! $src: patch site not found as expected (stock x$co, patched x$cn)"
    abort "! Aborted: this build is not supported, module not installed."
  fi
  set -- $(find_pat "$dst" "$oh"); [ "$1" = 0 ] || abort "! Patch of $dst failed."
  set -- $(find_pat "$dst" "$nh"); [ "$1" -ge 1 ] || abort "! Patch of $dst failed."
  echo "${name}_OFFS=\"$offs\"" >> "$GUARD"
  echo "${name}_STOCK_SHA=$(stock_sha "$dst" "$oo" $offs)" >> "$GUARD"
}

# gen_xml <stock xml> <out xml>: one DEEP_BUFFER mix port per sample rate, used by the Bluetooth A2DP
# sinks (44.1/48/88.2/96 kHz) and the wired headphone sinks (additionally 176.4/192 kHz).
gen_xml() {
  # pass 1: add the new mix ports after the stock deep_buffer_out port
  awk '
  BEGIN {
    n = split("deep_buffer_rate_48k_out deep_buffer_rate_44k_out deep_buffer_rate_88k_out deep_buffer_rate_96k_out deep_buffer_rate_176k_out deep_buffer_rate_192k_out", name, " ")
    split("48000 44100 88200 96000 176400 192000", rate, " ")
    split("INT_16_BIT FIXED_Q_8_24 INT_24_BIT INT_32_BIT", type, " ")
  }
  {
    print
    if (!done && index($0, "<mixPort name=\"deep_buffer_out\"")) { match($0, /^ */); ind = substr($0, 1, RLENGTH); inport = 1 }
    if (inport && index($0, "</mixPort>")) {
      for (k = 1; k <= n; k++) {
        printf "%s<mixPort name=\"%s\" role=\"source\" flags=\"DEEP_BUFFER\">\n", ind, name[k]
        for (j = 1; j <= 4; j++)
          printf "%s    <profile samplingRates=\"%s\" channelLayouts=\"LAYOUT_STEREO\" formatType=\"PCM\" pcmType=\"%s\" />\n", ind, rate[k], type[j]
        printf "%s</mixPort>\n", ind
      }
      inport = 0; done = 1
    }
  }
  END { if (!done) exit 3 }
  ' "$1" > "$2.tmp" || { rm -f "$2.tmp"; return 1; }
  # pass 2: the five sinks use the new ports instead of the 48 kHz deep_buffer_out (first route of each sink)
  awk '
  BEGIN {
    bt = "deep_buffer_rate_48k_out,deep_buffer_rate_44k_out,deep_buffer_rate_88k_out,deep_buffer_rate_96k_out"
    wd = bt ",deep_buffer_rate_176k_out,deep_buffer_rate_192k_out"
    nsink = split("bt_a2dp_out bt_a2dp_headphones bt_a2dp_speaker wired_headset wired_headphones", sink, " ")
    for (k = 1; k <= nsink; k++) want[sink[k]] = (k <= 3) ? bt : wd
  }
  {
    if (match($0, /<route type="mix" sink="[^"]*" sources="[^"]*"/)) {
      seg = substr($0, RSTART, RLENGTH)
      name = seg; sub(/^<route type="mix" sink="/, "", name); sub(/".*$/, "", name)
      if ((name in want) && !(name in seen)) {
        src = seg; sub(/^.* sources="/, "", src); sub(/"$/, "", src)
        n = split(src, a, ","); out = ""; found = 0
        for (i = 1; i <= n; i++) {
          v = a[i]; if (v == "deep_buffer_out") { v = want[name]; found = 1 }
          out = out (i > 1 ? "," : "") v
        }
        if (!found) bad = 1
        seen[name] = 1
        $0 = substr($0, 1, RSTART - 1) "<route type=\"mix\" sink=\"" name "\" sources=\"" out "\"" substr($0, RSTART + RLENGTH)
      }
    }
    print
  }
  END { c = 0; for (k in seen) c++; if (c != nsink || bad) exit 3 }
  ' "$2.tmp" > "$2"; rc=$?
  rm -f "$2.tmp"
  return $rc
}

GUARD="$SRCDIR/guard.conf"
: > "$GUARD"
ui_print "- Hi-Res Unlocker v0.6.1 ($MODEL, Android 16)"

ui_print "  * audio policy library"
build_lib "$LIB" "$SRCDIR/system/lib64/libaudiopolicymanagerdefault.so" "$APM_OLD_HEX" "$APM_NEW_HEX" "$APM_OLD_OCT" "$APM_NEW_OCT" 0 1 APM
ui_print "  * audio HAL library"
build_lib "$HAL" "$SRCDIR/system/vendor/lib64/hw/libaudiocorehal.qti.so" "$HAL_OLD_HEX" "$HAL_NEW_HEX" "$HAL_OLD_OCT" "$HAL_NEW_OCT" 4 2 HAL

ui_print "  * audio HAL config"
mkdir -p "$STOCKDIR"
if grep -q 'deep_buffer_rate_' "$XML"; then                  # this module's config is currently mounted
  [ -f "$STOCKDIR/audio_module_config_primary.xml" ] || abort "! Stock config backup missing: disable the module, reboot, then install again."
else
  cp "$XML" "$STOCKDIR/audio_module_config_primary.xml" || abort "! Cannot back up $XML"
fi
OUTXML="$SRCDIR/system/vendor/etc/audio/sku_canoe/audio_module_config_primary.xml"
mkdir -p "${OUTXML%/*}"
gen_xml "$STOCKDIR/audio_module_config_primary.xml" "$OUTXML" || abort "! The audio config layout is not supported (deep_buffer_out or a Bluetooth/wired route not found)."
grep -q 'deep_buffer_rate_192k_out' "$OUTXML" || abort "! Generated config is incomplete."
echo "XML_STOCK_SHA=$(sha256sum "$STOCKDIR/audio_module_config_primary.xml" | cut -d' ' -f1)" >> "$GUARD"
echo "XML_OUT_SHA=$(sha256sum "$OUTXML" | cut -d' ' -f1)" >> "$GUARD"

ui_print "  * per-rate deep-buffer mix ports: 44.1/48/88.2/96 kHz for Bluetooth A2DP, plus 176.4/192 kHz for wired headphones"
ui_print "  * audio policy: score the sampling rate for requests <= 48 kHz too"
ui_print "  * audio HAL: deep-buffer buffer rounded up to 16 frames (fixes 44.1/88.2 kHz crackle)"
set_perm_recursive "$SRCDIR/system/vendor/etc/audio" 0 0 0755 0644 u:object_r:vendor_configs_file:s0
set_perm "$SRCDIR/system/lib64/libaudiopolicymanagerdefault.so" 0 0 0644 u:object_r:system_lib_file:s0
set_perm "$SRCDIR/system/vendor/lib64/hw/libaudiocorehal.qti.so" 0 0 0644 u:object_r:vendor_file:s0

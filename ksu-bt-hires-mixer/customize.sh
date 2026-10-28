#!/system/bin/sh
# Installer: build the patched libraries from the phone's own files. No vendor binary is shipped in
# this zip; the original library is copied, its SHA-256 is verified, a few bytes are patched, and the
# result is verified again. Anything unexpected aborts the install (nothing is mounted).
XML=/vendor/etc/audio/sku_canoe/audio_module_config_primary.xml
LIB=/system/lib64/libaudiopolicymanagerdefault.so
HAL=/vendor/lib64/hw/libaudiocorehal.qti.so
# known audio_module_config_primary.xml: stock and every released module version
X_STOCK=8afbce6c280b731fb4d9aacc463f98db6be5f07c33489fb3f7a2e268645e30be
X_V1=c347220963db9c934095bc03692040af3564ea050af5f3757ecb68285ba8fc1f
X_V2=03157e48ea2bfec50941ecd02b9fb86852942cde6e36cafc092230621763c311
X_V3=59364c812716987249fefb909c1b512ae94aae83675fda3524ec20e35cf7b85c
X_V4=ed34ecffe6ae02fb94406425e7948004e0954b5f61a3d81abe7aa9312d0d3e4b
X_V5=aafefb0a93399327e222108074600c940ec1e0e39c4ca7fb379ee4dba923940b
# libaudiopolicymanagerdefault.so: stock and patched
L_STOCK=d8a516c2c83ed712072f2f4cc5b85b7e7de10ba70e739c21909f3defd0ab7a93
L_PATCH=e4b9406b60187d6a6487e7a8f251b9a9bbf24eba54d2fadd8b4a52d08a3b29c8
# libaudiocorehal.qti.so: stock and patched (deep-buffer frame count rounded up to 16)
H_STOCK=6333e6a0fdcbfe12bac0dcd28e5149e8ab304ab9680038fea0936db014e36439
H_PATCH=5e7806a7841c82788f0fe38a4fe3051dbfb34a50fe58415078109aae7eab261c

sum() { sha256sum "$1" 2>/dev/null | cut -d' ' -f1; }

# build_lib <source> <dest> <stock-sha> <patched-sha> <offset> <patch bytes as printf octal escapes>
build_lib() {
  src=$1; dst=$2; stock=$3; patched=$4; off=$5; bytes=$6
  cur=$(sum "$src")
  mkdir -p "${dst%/*}" || abort "! Cannot create ${dst%/*}"
  cp "$src" "$dst" || abort "! Cannot copy $src"
  case "$cur" in
    "$patched") ;;                                        # already patched (module reinstall): keep as is
    "$stock") printf "$bytes" | dd of="$dst" bs=1 seek="$off" conv=notrunc 2>/dev/null ;;
    *) ui_print "! $src differs from the one this module was built for ($cur)"
       abort "! Aborted: ROM version mismatch, module not installed." ;;
  esac
  [ "$(sum "$dst")" = "$patched" ] || abort "! Patched $dst does not match the expected hash, module not installed."
}

CX=$(sum "$XML")
case "$CX" in
  "$X_STOCK"|"$X_V1"|"$X_V2"|"$X_V3"|"$X_V4"|"$X_V5") ;;
  *) ui_print "! audio_module_config_primary.xml differs from the one this module was built for ($CX)"
     abort "! Aborted: ROM version mismatch, module not installed." ;;
esac

ui_print "- BT Hi-Res Mixer v0.6.1"
ui_print "  * building patched libraries from this phone's own files"
# audio policy: mov w8,#0xbb81 -> mov w8,#1 (score the sampling rate for requests <= 48 kHz too)
build_lib "$LIB" "$MODPATH/system/lib64/libaudiopolicymanagerdefault.so" "$L_STOCK" "$L_PATCH" 328537 '\000\200'
# audio HAL: DeepBufferPlayback::getFrameCount rounded up to 16 frames
build_lib "$HAL" "$MODPATH/system/vendor/lib64/hw/libaudiocorehal.qti.so" "$H_STOCK" "$H_PATCH" 1370320 \
  '\251\357\207\322\251\306\253\362\037\040\003\325\111\067\311\362\211\101\340\362\010\115\035\221\010\175\311\233\000\355\174\222'

ui_print "  * per-rate deep-buffer mix ports: 44.1/48/88.2/96 kHz for Bluetooth A2DP, plus 176.4/192 kHz for wired headphones"
ui_print "  * audio policy: score the sampling rate for requests <= 48 kHz too"
ui_print "  * audio HAL: deep-buffer buffer rounded up to 16 frames (fixes 44.1/88.2 kHz crackle)"
set_perm_recursive $MODPATH/system/vendor/etc/audio 0 0 0755 0644 u:object_r:vendor_configs_file:s0
set_perm $MODPATH/system/lib64/libaudiopolicymanagerdefault.so 0 0 0644 u:object_r:system_lib_file:s0
set_perm $MODPATH/system/vendor/lib64/hw/libaudiocorehal.qti.so 0 0 0644 u:object_r:vendor_file:s0

#!/system/bin/sh
# Safety guard: after a ROM update the stock files change; an outdated overlay (especially the
# audio policy library) must not be mounted, so the whole module is skipped (skip_mount).
MODDIR=${0%/*}
XML=/vendor/etc/audio/sku_canoe/audio_module_config_primary.xml
LIB=/system/lib64/libaudiopolicymanagerdefault.so
HAL=/vendor/lib64/hw/libaudiocorehal.qti.so
X_STOCK=8afbce6c280b731fb4d9aacc463f98db6be5f07c33489fb3f7a2e268645e30be
X_V1=c347220963db9c934095bc03692040af3564ea050af5f3757ecb68285ba8fc1f
X_V2=03157e48ea2bfec50941ecd02b9fb86852942cde6e36cafc092230621763c311
X_V3=59364c812716987249fefb909c1b512ae94aae83675fda3524ec20e35cf7b85c
X_V4=ed34ecffe6ae02fb94406425e7948004e0954b5f61a3d81abe7aa9312d0d3e4b
X_V5=aafefb0a93399327e222108074600c940ec1e0e39c4ca7fb379ee4dba923940b
L_STOCK=d8a516c2c83ed712072f2f4cc5b85b7e7de10ba70e739c21909f3defd0ab7a93
L_PATCH=e4b9406b60187d6a6487e7a8f251b9a9bbf24eba54d2fadd8b4a52d08a3b29c8
# known libaudiocorehal.qti.so: stock and patched (deep-buffer frame count rounded up to 16)
H_STOCK=6333e6a0fdcbfe12bac0dcd28e5149e8ab304ab9680038fea0936db014e36439
H_PATCH=5e7806a7841c82788f0fe38a4fe3051dbfb34a50fe58415078109aae7eab261c

CX=$(sha256sum "$XML" 2>/dev/null | cut -d' ' -f1)
CL=$(sha256sum "$LIB" 2>/dev/null | cut -d' ' -f1)
CH=$(sha256sum "$HAL" 2>/dev/null | cut -d' ' -f1)
OKX=0; OKL=0; OKH=0
case "$CX" in "$X_STOCK"|"$X_V1"|"$X_V2"|"$X_V3"|"$X_V4"|"$X_V5") OKX=1 ;; esac
case "$CL" in "$L_STOCK"|"$L_PATCH") OKL=1 ;; esac
case "$CH" in "$H_STOCK"|"$H_PATCH") OKH=1 ;; esac
if [ "$OKX" = 1 ] && [ "$OKL" = 1 ] && [ "$OKH" = 1 ]; then
  rm -f "$MODDIR/skip_mount"
else
  touch "$MODDIR/skip_mount"
fi

# Shared helpers (sourced by customize.sh and post-fs-data.sh).
REQ_MODEL=NX809J
REQ_SDK=36          # Android 16

# find_pat <file> <hex pattern> -> "<count> <byte offset> <byte offset> ..." (byte-aligned matches only)
find_pat() {
  od -An -v -tx1 "$1" | tr -d ' \n' | awk -v pat="$2" '
    { s = $0; p = 0
      while ((i = index(substr(s, p + 1), pat)) > 0) {
        pos = p + i
        if ((pos - 1) % 2 == 0) { n++; offs = offs " " (pos - 1) / 2 }
        p = pos
      } }
    END { print n + 0 offs }'
}

# Instruction patterns (little-endian bytes).
# libaudiopolicymanagerdefault.so, AudioPolicyManager::selectOutput:
#   stock   mov w8,#0xbb81 (48001) ; cmp w21,w8
#   patched mov w8,#1              ; cmp w21,w8
APM_OLD_HEX=28709752bf02086b
APM_NEW_HEX=28008052bf02086b
APM_OLD_OCT='\050\160\227\122'
APM_NEW_OCT='\050\000\200\122'
# libaudiocorehal.qti.so, DeepBufferPlayback::getFrameCount: add x8,x8,w0,sxtw #2 followed by the
# 32 bytes below. The patch rounds the returned frame count up to a multiple of 16.
HAL_OLD_HEX=08c9208be9f99ed2696abcf208f17dd3a974d3f28918e4f208fd43d3087dc99b00fd44d3
HAL_NEW_HEX=08c9208ba9ef87d2a9c6abf21f2003d54937c9f28941e0f2084d1d91087dc99b00ed7c92
HAL_OLD_OCT='\351\371\236\322\151\152\274\362\010\361\175\323\251\164\323\362\211\030\344\362\010\375\103\323\010\175\311\233\000\375\104\323'
HAL_NEW_OCT='\251\357\207\322\251\306\253\362\037\040\003\325\111\067\311\362\211\101\340\362\010\115\035\221\010\175\311\233\000\355\174\222'

# patch_at <file> <printf octal bytes> <offset> [<offset> ...]
patch_at() {
  f=$1; b=$2; shift 2
  for o in "$@"; do printf "$b" | dd of="$f" bs=1 seek="$o" conv=notrunc 2>/dev/null || return 1; done
}

# stock_sha <file> <old bytes (octal)> <offset> [<offset> ...]: SHA-256 of the file with every patch
# site reverted to the stock bytes, so it gives the same value whether the file is stock or patched.
stock_sha() {
  t="${TMPDIR:-/data/local/tmp}/.btm_norm.$$"
  f=$1; b=$2; shift 2
  cp "$f" "$t" && patch_at "$t" "$b" "$@" && sha256sum "$t" | cut -d' ' -f1
  rm -f "$t"
}

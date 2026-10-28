"""Patch DeepBufferPlayback::getFrameCount in libaudiocorehal.qti.so so the deep-buffer HAL
buffer is a multiple of 16 frames.

Stock: frames = rate * 40 / 1000  (44.1 kHz -> 1764, 88.2 kHz -> 3528; 48/96/176.4/192 kHz are
already multiples of 16).  AudioFlinger (Threads.cpp readOutputParameters_l) rounds its own
frame count up to a multiple of 16 (1764 -> 1776) but the HAL only accepts 1764 frames per write,
so every cycle ends with an extra 12-frame write -> irregular consumption -> audible crackle.

New: frames = ceil16(rate * 40 / 1000), done in place without a code cave:
    x8 = rate*5 (+1875)            ; add x8,x8,#1875   (replaces the 'lsr x8,x8,#3')
    x9 = ceil(2^64/125)            ; 4 mov/movk immediates changed
    x8 = umulh(x8, x9)             ; = floor((rate*5 + 1875) / 125)
    x0 = x8 & ~15                  ; and x0,x8,#-16    (replaces the 'lsr x0,x8,#4')
and the matching 'lsl x8,x8,#3' (which cancelled the lsr) becomes a nop.
Identity: ceil(floor(x/125)/16)*16 == floor((x+1875)/125) & ~15.

usage: python -I patch_hal.py <stock.so> <out.so>   (refuses any file but the stock hash)
"""
import hashlib
import struct
import sys

STOCK_SHA256 = "6333e6a0fdcbfe12bac0dcd28e5149e8ab304ab9680038fea0936db014e36439"
BASE = 0x14e8d0                       # file offset == vaddr in this library
M = -(-(1 << 64) // 125)              # ceil(2^64/125)

def w(i): return struct.pack("<I", i)
def movz(v, imm): return 0xd2800000 | (imm << 5) | v
def movk(v, imm, sh): return 0xf2800000 | ((sh // 16) << 21) | (imm << 5) | v

OLD = [0xd29ef9e9, 0xf2bc6a69, 0xd37df108, 0xf2d374a9, 0xf2e41889, 0xd343fd08, 0x9bc97d08, 0xd344fd00]
NEW = [movz(9, M & 0xffff), movk(9, (M >> 16) & 0xffff, 16), 0xd503201f,   # nop
       movk(9, (M >> 32) & 0xffff, 32), movk(9, (M >> 48) & 0xffff, 48),
       0x91000000 | (1875 << 10) | (8 << 5) | 8,                            # add x8,x8,#1875
       0x9bc97d08,                                                          # umulh x8,x8,x9
       0x927ced00]                                # and x0,x8,#-16

def model(rate):
    x8 = (rate * 5 + 1875) & (2**64 - 1)
    return ((x8 * M) >> 64) & ~15

if __name__ == "__main__":
    for r in range(1, 400001):
        stock = (r * 5 // 125)
        assert model(r) == -(-stock // 16) * 16, r
    stock_path, out_path = sys.argv[1], sys.argv[2]
    data = bytearray(open(stock_path, "rb").read())
    assert hashlib.sha256(data).hexdigest() == STOCK_SHA256, "not the expected stock library"
    assert bytes(data[BASE:BASE + 32]) == b"".join(w(i) for i in OLD), "unexpected bytes at the patch site"
    data[BASE:BASE + 32] = b"".join(w(i) for i in NEW)
    open(out_path, "wb").write(data)
    print("patched 32 bytes at 0x%x; sha256 %s" % (BASE, hashlib.sha256(data).hexdigest()))

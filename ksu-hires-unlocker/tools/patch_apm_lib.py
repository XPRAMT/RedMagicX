"""Patch the sample-rate gate in AudioPolicyManager::selectOutput.

The stock library compares the requested sampling rate with 48001 and skips the sampling-rate
criterion for every request <= 48000 Hz (AOSP: `if (samplingRate > SAMPLE_RATE_HZ_DEFAULT)`).
This changes the immediate of `mov w8, #0xbb81` (48001) to `mov w8, #1`, so only an unspecified
rate (0) skips the criterion and every real request is scored (nearest rate >= request wins).

usage: python -I patch_apm_lib.py <stock.so> <out.so>
Only valid for the exact stock file below; refuses to patch anything else.
"""
import hashlib
import sys

STOCK_SHA256 = "d8a516c2c83ed712072f2f4cc5b85b7e7de10ba70e739c21909f3defd0ab7a93"
OFFSET = 0x50358                              # file offset == vaddr in this library
OLD = bytes.fromhex("28709752")               # mov w8, #0xbb81  (48001)
NEW = bytes.fromhex("28008052")               # mov w8, #1
CMP_AFTER = bytes.fromhex("bf02086b")         # cmp w21, w8  (must follow the mov)

stock_path, out_path = sys.argv[1], sys.argv[2]
data = bytearray(open(stock_path, "rb").read())
assert hashlib.sha256(data).hexdigest() == STOCK_SHA256, "not the expected stock library"
assert data[OFFSET:OFFSET + 4] == OLD, "unexpected bytes at the patch offset"
assert data[OFFSET + 4:OFFSET + 8] == CMP_AFTER, "unexpected instruction after the patch site"
pattern = OLD + CMP_AFTER
assert bytes(data).count(pattern) == 1, "patch pattern is not unique in the file"

data[OFFSET:OFFSET + 4] = NEW
open(out_path, "wb").write(data)
print("patched %d bytes at 0x%x; sha256 %s" % (4, OFFSET, hashlib.sha256(data).hexdigest()))

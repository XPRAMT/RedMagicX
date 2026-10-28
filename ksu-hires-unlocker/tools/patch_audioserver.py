"""Reference implementation of the audioserver patch that customize.sh applies on the phone.

AudioFlinger (built into /system/bin/audioserver) raises the standby delay of every output thread
that is connected to an A2DP device to at least 3 s (AOSP PlaybackThread::cacheParameters_l(),
kDefaultStandbyTimeInNsecs). While an A2DP thread waits for that standby, the Bluetooth backend stays
busy and a stream with a different sample rate cannot start (the HAL drops its writes), which mutes the
first ~3 s of the next track. The patch turns the floor's b.ge into an unconditional branch, so the
thread uses ro.audio.flinger_standbytime_ms (the module sets it to 500 at boot).

Pattern (24 bytes, exactly one match):
  ldr x9,[x19,#0x2698] ; mov w8,#0x5e00 ; movk w8,#0xb2d0,lsl#16 ; cmp x9,x8 ; b.ge +8 ; str x8,[x19,#0x2698]
  694e53f9 08c08b52 085ab672 3f0108eb 4a000054 684e13f9  ->  ... 02000014 ...   (b.ge -> b +8)

usage: python -I patch_audioserver.py <in audioserver> <out audioserver>
"""
import hashlib
import sys

OLD = bytes.fromhex("694e53f908c08b52085ab6723f0108eb4a000054684e13f9")
NEW = bytes.fromhex("694e53f908c08b52085ab6723f0108eb02000014684e13f9")

data = bytearray(open(sys.argv[1], "rb").read())
sites = []
i = data.find(OLD)
while i >= 0:
    sites.append(i)
    i = data.find(OLD, i + 1)
assert len(sites) == 1, "expected exactly one patch site, found %d" % len(sites)
data[sites[0]:sites[0] + len(OLD)] = NEW
open(sys.argv[2], "wb").write(data)
print("patched the b.ge at 0x%x; sha256 %s" % (sites[0] + 16, hashlib.sha256(data).hexdigest()))

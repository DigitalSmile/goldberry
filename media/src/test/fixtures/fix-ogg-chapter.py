"""Rewrites one byte run inside an Ogg page and fixes that page's CRC.

FFmpeg 8's Vorbis comment writer adds a second to a chapter time with a zero
hours field (00:00:00.700 comes out as 00:00:01.700); this puts the time the
chapter metadata said back, so the fixture states what the test expects.
"""
import sys

def crc_table():
    table = []
    for i in range(256):
        r = i << 24
        for _ in range(8):
            r = ((r << 1) ^ 0x04C11DB7) if r & 0x80000000 else (r << 1)
            r &= 0xFFFFFFFF
        table.append(r)
    return table

TABLE = crc_table()

def crc(data):
    r = 0
    for b in data:
        r = ((r << 8) & 0xFFFFFFFF) ^ TABLE[((r >> 24) & 0xFF) ^ b]
    return r

path, old, new = sys.argv[1], sys.argv[2].encode(), sys.argv[3].encode()
assert len(old) == len(new)
data = bytearray(open(path, "rb").read())
at = data.find(old)
assert at >= 0, "not found"
data[at:at + len(old)] = new
# The page holding it: the last capture pattern before it.
page = data.rfind(b"OggS", 0, at)
segments = data[page + 26]
length = 27 + segments + sum(data[page + 27:page + 27 + segments])
data[page + 22:page + 26] = b"\0\0\0\0"
data[page + 22:page + 26] = crc(bytes(data[page:page + length])).to_bytes(4, "little")
open(path, "wb").write(bytes(data))

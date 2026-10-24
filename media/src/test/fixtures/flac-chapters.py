"""Writes Xiph CHAPTERnnn comments into a FLAC file's VORBIS_COMMENT block.

FFmpeg's FLAC muxer does not write chapters, so the fixture's comments are put
in here: every other metadata block is kept, the comment block replaced.
"""
import struct, sys

path = sys.argv[1]
chapters = [("00:00:00.000", "Intro"), ("00:00:00.300", "Middle"), ("00:00:00.700", "Coda")]
data = open(path, "rb").read()
assert data[:4] == b"fLaC"
at, blocks = 4, []
while True:
    header = data[at]
    kind, length = header & 0x7F, int.from_bytes(data[at + 1:at + 4], "big")
    blocks.append((kind, data[at + 4:at + 4 + length]))
    at += 4 + length
    if header & 0x80:
        break
frames = data[at:]
comments = []
for i, (time, name) in enumerate(chapters):
    comments += [f"CHAPTER{i:03d}={time}", f"CHAPTER{i:03d}NAME={name}"]
vendor = b"goldberry fixtures"
body = struct.pack("<I", len(vendor)) + vendor + struct.pack("<I", len(comments))
for comment in comments:
    raw = comment.encode()
    body += struct.pack("<I", len(raw)) + raw
blocks = [b for b in blocks if b[0] != 4]
blocks.insert(1, (4, body))
out = bytearray(b"fLaC")
for i, (kind, payload) in enumerate(blocks):
    last = 0x80 if i == len(blocks) - 1 else 0
    out += bytes([kind | last]) + len(payload).to_bytes(3, "big") + payload
open(path, "wb").write(bytes(out + frames))

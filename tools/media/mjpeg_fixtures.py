#!/usr/bin/env python3
"""Writes the JPEG frames goldberry-camera's MJPG tests decode.

A test card of seven colour bars over a grey ramp, as a UVC camera sends
Motion JPEG: baseline, 4:2:2, with the standard Huffman tables of JPEG Annex
K.3 (libjpeg writes them unless asked to optimise). Three files:

  card.jpg             64x48, as encoded, with its DHT segments
  card-no-huffman.jpg  the same bytes with every DHT segment cut out, as a
                       camera that relies on the standard tables sends it
  card-32x24.jpg       the card at another size, as after a mode change

Encoded by Pillow (libjpeg), not by the code under test, so the tables the
tests insert are checked against an independent encoder's.

    python3 -I tools/media/mjpeg_fixtures.py camera/src/test/resources/dev/goldberry/camera/mjpeg
"""

import io
import pathlib
import sys

from PIL import Image


# White, yellow, cyan, green, magenta, red, blue.
BARS = [
    (235, 235, 235),
    (235, 235, 16),
    (16, 235, 235),
    (16, 235, 16),
    (235, 16, 235),
    (235, 16, 16),
    (16, 16, 235),
]


def card(width, height):
    image = Image.new("RGB", (width, height))
    pixels = image.load()
    bars_bottom = height * 2 // 3
    for y in range(height):
        for x in range(width):
            if y < bars_bottom:
                pixels[x, y] = BARS[x * len(BARS) // width]
            else:
                grey = round(16 + 219 * x / (width - 1))
                pixels[x, y] = (grey, grey, grey)
    return image


def without_huffman_tables(jpeg):
    """`jpeg` with its DHT (0xFFC4) segments removed, up to the first scan."""
    out = bytearray(jpeg[:2])
    at = 2
    while at < len(jpeg):
        if jpeg[at] != 0xFF:
            raise ValueError(f"no marker at {at}")
        marker = jpeg[at + 1]
        if marker == 0xDA:
            out += jpeg[at:]
            return bytes(out)
        length = int.from_bytes(jpeg[at + 2 : at + 4], "big")
        if marker != 0xC4:
            out += jpeg[at : at + 2 + length]
        at += 2 + length
    raise ValueError("no scan")


def encode(width, height):
    buffer = io.BytesIO()
    card(width, height).save(buffer, "JPEG", quality=90, subsampling=1, optimize=False, progressive=False)
    return buffer.getvalue()


def main(directory):
    target = pathlib.Path(directory)
    target.mkdir(parents=True, exist_ok=True)
    jpeg = encode(64, 48)
    (target / "card.jpg").write_bytes(jpeg)
    (target / "card-no-huffman.jpg").write_bytes(without_huffman_tables(jpeg))
    (target / "card-32x24.jpg").write_bytes(encode(32, 24))


if __name__ == "__main__":
    main(sys.argv[1])

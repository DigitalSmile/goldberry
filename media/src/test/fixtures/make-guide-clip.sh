#!/bin/sh
# Regenerates guide-clip.mkv in src/test/resources/dev/goldberry/media/fixtures/:
# the clip the guide's pictures of the media widgets are taken from.
#
# Our own content, as every fixture is: one still drawn below with Pillow (a
# lake at dusk, in the Nord palette), held for twelve seconds of VP9 at
# 1280x720 and 25 fps; a quiet 220 Hz sine as Opus; and a SubRip track tagged
# `eng` with one two-line cue from 3 s to 6 s.
#
# Twelve seconds so the controls read like a film's and not a test's: the
# picture is taken paused at 4 s, a third of the way along the seek bar.
#
# Needs Python 3 with Pillow and a full FFmpeg with libvpx and libopus on PATH.
# Run from this directory: ./make-guide-clip.sh
set -eu
out=../resources/dev/goldberry/media/fixtures
work=$(mktemp -d)
ff="ffmpeg -hide_banner -loglevel error -y -bitexact"

python3 - "$work/frame.png" <<'PY'
import math, sys
from PIL import Image, ImageDraw, ImageFilter

W, H, S = 1280, 720, 2          # drawn at twice the size, then brought down
w, h = W * S, H * S

def hexrgb(s):
    return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))

def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

def gradient(t, stops):
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            return mix(c0, c1, max(0.0, (t - t0) / (t1 - t0)))
    return stops[-1][1]

SKY = [(0.0, hexrgb("#2E3440")), (0.45, hexrgb("#5E81AC")), (0.72, hexrgb("#B48EAD")),
       (0.90, hexrgb("#D08770")), (1.0, hexrgb("#EBCB8B"))]
shore = int(h * 0.66)

img = Image.new("RGB", (w, h))
px = ImageDraw.Draw(img)
for y in range(h):
    if y < shore:
        colour = gradient(y / shore, SKY)
    else:
        # The lake: the sky upside down, darker and bluer with depth.
        t = (y - shore) / (h - shore)
        colour = mix(gradient(1 - t * 0.9, SKY), hexrgb("#2E3440"), 0.35 + 0.45 * t)
    px.line([(0, y), (w, y)], fill=colour)

# The sun on the horizon, with a glow over sky and water.
cx, cy, r = int(w * 0.64), int(shore - 18 * S), 64 * S
glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
g = ImageDraw.Draw(glow)
for k, a in ((5.0, 50), (3.2, 80), (2.0, 120)):
    g.ellipse([cx - r * k, cy - r * k * 0.7, cx + r * k, cy + r * k * 0.7], fill=(235, 203, 139, a))
glow = glow.filter(ImageFilter.GaussianBlur(48 * S))
img.paste(glow, (0, 0), glow)
px = ImageDraw.Draw(img)
px.ellipse([cx - r, cy - r, cx + r, cy + r], fill=hexrgb("#F4E0B0"))

# Hills along the far shore, farthest first.
def ridge(base, amp, freq, phase, colour):
    pts = [(0, shore + 2 * S)]
    for x in range(0, w + 8, 8):
        y = base - amp * (0.6 + 0.4 * math.sin(x / w * freq * math.pi + phase)) \
            - amp * 0.3 * math.sin(x / w * freq * 3.1 * math.pi + phase * 1.7)
        pts.append((x, min(y, shore)))
    pts.append((w, shore + 2 * S))
    px.polygon(pts, fill=colour)

ridge(shore, h * 0.16, 2.4, 0.3, hexrgb("#8C7A99"))
ridge(shore, h * 0.11, 1.7, 2.2, hexrgb("#5D6479"))
ridge(shore, h * 0.06, 3.3, 4.0, hexrgb("#434C5E"))

# The sun's path on the water: short gold strokes, narrowing toward the shore.
for k in range(34):
    y = shore + 6 * S + k * k * 0.9 * S
    if y >= h:
        break
    half = (14 + k * 5.5) * S * (0.55 + 0.45 * math.sin(k * 1.9) ** 2)
    shade = mix(hexrgb("#F4E0B0"), hexrgb("#D08770"), min(1.0, k / 30))
    px.rounded_rectangle([cx - half, y, cx + half, y + 3 * S], radius=2 * S, fill=shade)

# A near headland on the left, in shadow.
pts = [(0, h)]
for x in range(0, int(w * 0.42), 8):
    t = x / (w * 0.42)
    pts.append((x, h * 0.80 + h * 0.20 * t ** 2.2 - h * 0.04 * math.sin(t * 5)))
pts.append((w * 0.42, h))
px.polygon(pts, fill=hexrgb("#262B35"))

img = img.resize((W, H), Image.LANCZOS)
img.save(sys.argv[1])
PY
cat > "$work/subs.srt" <<'SRT'
1
00:00:03,000 --> 00:00:06,000
The lake holds the last of the light,
and the hills go blue around it.
SRT

$ff -loop 1 -framerate 25 -i "$work/frame.png" -f lavfi -i "sine=frequency=220:sample_rate=48000:duration=12" \
    -i "$work/subs.srt" -map 0:v -map 1:a -map 2:s -t 12 \
    -c:v libvpx-vp9 -pix_fmt yuv420p -crf 34 -b:v 0 -g 250 -row-mt 1 \
    -c:a libopus -b:a 12k -ac 1 -af "volume=0.2" \
    -c:s srt -metadata:s:s:0 language=eng \
    "$out/guide-clip.mkv"
rm -rf "$work"
ls -l "$out/guide-clip.mkv"

#!/bin/sh
# Regenerates the Media screen's sample clips in
# src/main/resources/io/github/digitalsmile/goldberry/example/media/.
#
# One synthetic piece, written sample by sample below: twelve seconds of a
# plucked arpeggio over a drone, 48 kHz stereo, panned note by note. It is our own
# content, so the clips carry no third-party licence. Encoded by a full FFmpeg on
# the developer's machine (`brew install ffmpeg`); nothing of it ships.
#
# Run from this directory: ./make-media-samples.sh
set -eu
out=../src/main/resources/io/github/digitalsmile/goldberry/example/media
work=$(mktemp -d)
ff="ffmpeg -hide_banner -loglevel error -y -bitexact"

python3 - "$work/arpeggio.wav" <<'PY'
import math, struct, sys
rate, seconds = 48000, 12
# A minor ninth arpeggio, eight steps a bar, four bars: i - VI - III - VII.
chords = [[57, 60, 64, 67, 71], [53, 57, 60, 64, 67], [48, 52, 55, 59, 62], [55, 59, 62, 65, 69]]
step = 0.1875  # seconds per note: 80 bpm sixteenths
frames = []
for i in range(rate * seconds):
    t = i / rate
    bar = int(t / (8 * step)) % 4
    n = int(t / step)
    pos = t - n * step
    chord = chords[bar]
    note = chord[[0, 1, 2, 3, 4, 3, 2, 1][n % 8]] + 12
    f = 440 * 2 ** ((note - 69) / 12)
    pluck = math.exp(-pos * 9) * (math.sin(2 * math.pi * f * t) + 0.3 * math.sin(4 * math.pi * f * t))
    root = 440 * 2 ** ((chord[0] - 12 - 69) / 12)
    drone = 0.35 * math.sin(2 * math.pi * root * t) * (0.8 + 0.2 * math.sin(2 * math.pi * 0.25 * t))
    fade = min(1.0, t / 0.05, (seconds - t) / 0.8)
    pan = 0.5 + 0.35 * math.sin(n * 1.7)
    left = fade * (0.45 * pluck * (1 - pan) * 2 + drone) * 0.55
    right = fade * (0.45 * pluck * pan * 2 + drone) * 0.55
    frames.append(struct.pack("<hh", round(max(-1, min(1, left)) * 32767), round(max(-1, min(1, right)) * 32767)))
data = b"".join(frames)
header = (b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVEfmt "
          + struct.pack("<IHHIIHH", 16, 1, 2, rate, rate * 4, 4, 16) + b"data" + struct.pack("<I", len(data)))
open(sys.argv[1], "wb").write(header + data)
PY
wav="$work/arpeggio.wav"

$ff -i "$wav" -c:a libopus -b:a 96k -metadata title="Arpeggio (Opus)" "$out/arpeggio.opus"
$ff -i "$wav" -c:a vorbis -strict experimental -q:a 4 -metadata title="Arpeggio (Vorbis)" "$out/arpeggio.ogg"
$ff -i "$wav" -ac 1 -ar 24000 -c:a flac -compression_level 8 "$out/arpeggio.flac"

# MP3 with cover art: a Nord-blue gradient square in an attached-picture stream.
$ff -f lavfi -i "gradients=s=192x192:c0=0x5e81ac:c1=0x88c0d0:x0=0:y0=0:x1=192:y1=192:d=1" \
    -frames:v 1 "$work/cover.png"
$ff -i "$wav" -i "$work/cover.png" -map 0:a -map 1:v -c:a libmp3lame -b:a 128k -c:v png \
    -disposition:v attached_pic -metadata title="Arpeggio (MP3)" "$out/arpeggio.mp3"

# The patent-pool pair the published natives do not decode: the error state.
$ff -f lavfi -i "testsrc2=s=160x90:r=25:d=1" -f lavfi -i "sine=f=440:d=1" \
    -c:v libx264 -preset ultrafast -c:a aac -b:a 48k -shortest "$out/h264-aac.mp4"

rm -r "$work"
ls -l "$out"

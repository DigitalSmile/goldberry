#!/bin/sh
# Regenerates the encoded test clips in
# src/test/resources/io/github/digitalsmile/goldberry/media/fixtures/.
#
# Every clip is made from synthetic signals, so the clips are our own content
# and carry no third-party licence:
#
#   audio  one second of a 440 Hz sine at amplitude 12000/32768, 48 kHz stereo,
#          written sample by sample below, so a lossless clip can be checked
#          against the formula exactly;
#   video  one second of FFmpeg's `testsrc2` pattern at 160x90, 25 fps.
#
# The media module's own FFmpeg is built without encoders (goldberry-media.md
# sec. 2), so the encoding is done by a full FFmpeg on the developer's machine
# (`brew install ffmpeg`). Nothing of that FFmpeg is shipped or linked.
#
# Run from this directory: ./make-fixtures.sh
set -eu
out=../resources/io/github/digitalsmile/goldberry/media/fixtures
work=$(mktemp -d)
ff="ffmpeg -hide_banner -loglevel error -y -bitexact"
# SVT-AV1 logs through its own channel; 1 is errors only.
export SVT_LOG=1

python3 - "$work/tone.wav" <<'PY'
import math, struct, sys
rate, n = 48000, 48000
data = b"".join(struct.pack("<hh", *([round(12000 * math.sin(2 * math.pi * 440 * i / rate))] * 2)) for i in range(n))
header = (b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVEfmt "
          + struct.pack("<IHHIIHH", 16, 1, 2, rate, rate * 4, 4, 16) + b"data" + struct.pack("<I", len(data)))
open(sys.argv[1], "wb").write(header + data)
PY
tone="$work/tone.wav"

# --- Audio: one clip per container and codec pair the published natives play.
$ff -i "$tone" -c:a flac "$out/tone.flac"
$ff -i "$tone" -c:a flac -f mp4 "$out/tone-flac.mp4"
$ff -i "$tone" -c:a flac "$out/tone-flac.mkv"
$ff -i "$tone" -c:a libopus -b:a 96k "$out/tone-opus.mp4"
$ff -i "$tone" -c:a libopus -b:a 96k "$out/tone.opus"
$ff -i "$tone" -c:a libopus -b:a 96k "$out/tone-opus.webm"
$ff -i "$tone" -c:a vorbis -strict experimental -ac 2 -q:a 5 "$out/tone.ogg"
$ff -i "$tone" -c:a libmp3lame -b:a 128k "$out/tone.mp3"

# An MP3 with cover art: a 64x64 PNG in an attached-picture stream.
$ff -f lavfi -i "color=c=0x5e81ac:s=64x64:d=1" -frames:v 1 "$work/cover.png"
$ff -i "$tone" -i "$work/cover.png" -map 0:a -map 1:v -c:a libmp3lame -b:a 128k -c:v png \
    -disposition:v attached_pic "$out/tone-cover.mp3"

# --- Video, for phase 3: one second, with the tone as audio.
pattern="testsrc2=s=160x90:r=25:d=1"
$ff -f lavfi -i "$pattern" -i "$tone" -c:v libvpx -b:v 200k -c:a libopus -b:a 64k -shortest "$out/clip-vp8.webm"
$ff -f lavfi -i "$pattern" -i "$tone" -c:v libvpx-vp9 -b:v 200k -c:a libopus -b:a 64k -shortest "$out/clip-vp9.webm"
$ff -f lavfi -i "$pattern" -i "$tone" -c:v libsvtav1 -preset 12 -crf 45 -c:a libopus -b:a 64k -shortest \
    "$out/clip-av1.mkv"
$ff -f lavfi -i "$pattern" -i "$tone" -c:v libsvtav1 -preset 12 -crf 45 -c:a flac -shortest -f mp4 \
    "$out/clip-av1.mp4"

# Two pictures outside 8-bit 4:2:0, silent and a fifth of a second each: VP9
# profile 1 (4:4:4), which the built-in decoder converts to I420, and profile 2
# (10-bit 4:2:0), which it lends as I010.
short="testsrc2=s=160x90:r=25:d=0.2"
$ff -f lavfi -i "$short" -c:v libvpx-vp9 -pix_fmt yuv444p -b:v 200k "$out/clip-vp9-444.webm"
$ff -f lavfi -i "$short" -c:v libvpx-vp9 -pix_fmt yuv420p10le -b:v 200k "$out/clip-vp9-10bit.webm"

# --- Two audio tracks, for track selection (phase 7): two seconds each, the
# first at 440 Hz tagged English and titled, the second at 880 Hz tagged French,
# both FLAC so a switch can be checked to the sample.
python3 - "$work/a440.wav" "$work/a880.wav" <<'PY'
import math, struct, sys
rate, n = 48000, 96000
for path, freq in ((sys.argv[1], 440), (sys.argv[2], 880)):
    data = b"".join(struct.pack("<hh", *([round(12000 * math.sin(2 * math.pi * freq * i / rate))] * 2)) for i in range(n))
    header = (b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVEfmt "
              + struct.pack("<IHHIIHH", 16, 1, 2, rate, rate * 4, 4, 16) + b"data" + struct.pack("<I", len(data)))
    open(path, "wb").write(header + data)
PY
$ff -i "$work/a440.wav" -i "$work/a880.wav" -map 0:a -map 1:a -c:a flac \
    -metadata:s:a:0 language=eng -metadata:s:a:0 title="Concert pitch" -metadata:s:a:1 language=fra \
    -disposition:a:0 default -disposition:a:1 0 "$out/tones-two-tracks.mkv"

# --- The patent-pool pair the published natives deliberately do not decode
# (goldberry-media.md S7): H.264 video and AAC audio in MP4. It opens, and it
# must report UNSUPPORTED_CODEC naming both.
$ff -f lavfi -i "$pattern" -i "$tone" -c:v libx264 -preset ultrafast -c:a aac -b:a 64k -shortest \
    "$out/clip-h264-aac.mp4"

rm -r "$work"
ls -l "$out"

#!/bin/sh
# Regenerates the encoded test clips in
# src/test/resources/io/github/digitalsmile/goldberry/media/platform/fixtures/.
#
# Every clip is made from synthetic signals, so the clips are our own content
# and carry no third-party licence. They are the patent-pool codecs the published
# natives deliberately do not decode (goldberry-media.md sec. 5): the operating
# system's decoders play them through this module's providers.
#
#   audio  one second of a 440 Hz sine at amplitude 12000/32768, 48 kHz stereo,
#          or six channels with a frequency of their own each (below);
#   video  FFmpeg's `testsrc2` pattern at 25 fps.
#
# Beside each video clip, the MD5 of every picture as FFmpeg's own decoder sees
# it, in the layout the provider hands over (NV12, or P010 for 10-bit). H.264 and
# HEVC decoding is bit-exact by specification, so VideoToolbox must agree with
# them byte for byte, and in the same order.
#
# The encoding is done by a full FFmpeg on the developer's machine
# (`brew install ffmpeg`). Nothing of that FFmpeg is shipped or linked.
#
# Run from this directory: ./make-fixtures.sh
set -eu
out=../resources/io/github/digitalsmile/goldberry/media/platform/fixtures
work=$(mktemp -d)
ff="ffmpeg -hide_banner -loglevel error -y -bitexact"

python3 - "$work/tone.wav" <<'PY'
import math, struct, sys
rate, n = 48000, 48000
data = b"".join(struct.pack("<hh", *([round(12000 * math.sin(2 * math.pi * 440 * i / rate))] * 2)) for i in range(n))
header = (b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVEfmt "
          + struct.pack("<IHHIIHH", 16, 1, 2, rate, rate * 4, 4, 16) + b"data" + struct.pack("<I", len(data)))
open(sys.argv[1], "wb").write(header + data)
PY
tone="$work/tone.wav"

# The MD5 of every picture of $1's first video track, as $2 (nv12 or p010le).
framemd5() {
    $ff -i "$out/$1" -map 0:v:0 -pix_fmt "$2" -f framemd5 "$out/$1.framemd5"
}

# --- H.264 -------------------------------------------------------------------
# High profile with B-frames in a pyramid: the pictures leave the decoder out of
# order, so the provider's reordering is what puts them right. A keyframe every
# ten pictures, so a seek has somewhere to land. 160x90 is coded as 160x96, so
# the crop is tested too.
pattern="testsrc2=s=160x90:r=25:d=1"
$ff -f lavfi -i "$pattern" -i "$tone" -c:v libx264 -profile:v high -crf 30 \
    -x264-params bframes=3:b-pyramid=normal:keyint=10:min-keyint=10:open-gop=0:scenecut=0 \
    -c:a aac -b:a 64k -shortest "$out/clip-h264-high.mp4"
framemd5 clip-h264-high.mp4 nv12
# The same streams in Matroska, which stores no decoding timestamps.
$ff -i "$out/clip-h264-high.mp4" -c copy "$out/clip-h264-high.mkv"

# Full range and BT.709 in the VUI: the picture says so, and the frame must too.
short="testsrc2=s=160x90:r=25:d=0.2"
$ff -f lavfi -i "$short" -c:v libx264 -crf 30 -pix_fmt yuv420p -color_range pc \
    -colorspace bt709 -color_primaries bt709 -color_trc bt709 "$out/clip-h264-full-709.mp4"
framemd5 clip-h264-full-709.mp4 nv12

# 720 rows, untagged: large enough for the hardware decoder, and BT.709 by the
# same default the built-in decoder applies.
$ff -f lavfi -i "testsrc2=s=1280x720:r=25:d=0.2" -c:v libx264 -crf 35 -preset veryfast "$out/clip-h264-720p.mp4"
framemd5 clip-h264-720p.mp4 nv12

# --- HEVC --------------------------------------------------------------------
# `hvc1`, the tag Apple's own players ask for; B-frames, as above.
x265="bframes=3:keyint=10:min-keyint=10:scenecut=0:open-gop=0:log-level=error"
$ff -f lavfi -i "$pattern" -c:v libx265 -crf 30 -tag:v hvc1 -x265-params "$x265" "$out/clip-hevc.mp4"
framemd5 clip-hevc.mp4 nv12
# Main 10, tagged as HDR video is: BT.2020 in the VUI, which the frame must say.
$ff -f lavfi -i "$short" -c:v libx265 -crf 30 -tag:v hvc1 -pix_fmt yuv420p10le -profile:v main10 \
    -colorspace bt2020nc -color_primaries bt2020 -color_trc smpte2084 \
    -x265-params "$x265" "$out/clip-hevc-10bit.mp4"
framemd5 clip-hevc-10bit.mp4 p010le

# --- Audio -------------------------------------------------------------------
# AAC in Matroska: the configuration arrives as CodecPrivate, not as `esds`.
$ff -i "$tone" -c:a aac -b:a 96k "$out/tone-aac.mkv"
# E-AC-3 in MP4.
$ff -i "$tone" -c:a eac3 -b:a 192k "$out/tone-eac3.mp4"

# Six channels, each its own frequency, in FFmpeg's 5.1 order (FL FR FC LFE BL
# BR). A decoder that hands them over in another order is caught by which
# frequency each channel carries. The LFE gets 60 Hz, under every encoder's
# low-pass for it.
six="aevalsrc=exprs=0.3*sin(2*PI*200*t)|0.3*sin(2*PI*300*t)|0.3*sin(2*PI*400*t)|0.3*sin(2*PI*60*t)|0.3*sin(2*PI*500*t)|0.3*sin(2*PI*600*t):s=48000:d=1:c=5.1"
$ff -f lavfi -i "$six" -c:a aac -b:a 384k "$out/tones-aac-5.1.mp4"
$ff -f lavfi -i "$six" -c:a ac3 -b:a 448k "$out/tones-ac3-5.1.mkv"

rm -r "$work"
ls -l "$out"

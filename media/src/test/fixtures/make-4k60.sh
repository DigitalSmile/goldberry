#!/usr/bin/env bash
# The 4K60 clip docs/gpu-plan.md phase 6 measures GPU present against (and
# media-plan.md phase 5's exit criterion): a minute of FFmpeg's testsrc2 at
# 3840x2160 and 60 fps in VP9 at about 20 Mb/s, with a 440 Hz tone in Opus so
# the audio clock is the master clock, as it is for a film.
#
# Too large to commit (about 150 MB), so it is made on purpose, into the
# build directory, where :media:videoPresentProbe looks for it:
#
#   media/src/test/fixtures/make-4k60.sh [seconds] [bits]
#
# `bits` is 8 (yuv420p, NV12 from VideoToolbox) or 10 (yuv420p10le, profile 2,
# P010 from VideoToolbox). Needs a full FFmpeg with libvpx and libopus.
set -euo pipefail
seconds="${1:-60}"
bits="${2:-8}"
out="$(cd "$(dirname "$0")/../../.." && pwd)/build/probe"
mkdir -p "$out"
if [ "$bits" = 10 ]; then
    pix=yuv420p10le; name="clip-4k60-vp9-10bit.webm"
else
    pix=yuv420p; name="clip-4k60-vp9.webm"
fi
ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "testsrc2=s=3840x2160:r=60:d=${seconds}" \
    -f lavfi -i "sine=frequency=440:sample_rate=48000:duration=${seconds}" \
    -c:v libvpx-vp9 -pix_fmt "$pix" -b:v 20M -maxrate 25M -bufsize 40M \
    -deadline realtime -cpu-used 8 -row-mt 1 -tile-columns 4 -threads 10 -g 120 \
    -c:a libopus -b:a 96k -ac 2 \
    "$out/$name"
echo "$out/$name"

#!/bin/sh
# Writes FFmpeg's trace of every header of the video fixtures the bitstream
# readers are held to: src/test/resources/dev/goldberry/media/bitstream/*.trace.
#
# `trace_headers` prints each syntax element FFmpeg's own parser reads, with its
# bit position and value; the parsers in …media.bitstream must read the same
# values from the same bytes. Only the element lines are kept, without the
# log prefix: "<section>" lines name each unit, "<bit> <name> <bits> = <value>"
# lines follow it.
#
# Run from this directory after make-fixtures.sh and make-platform-fixtures.sh:
# ./make-traces.sh
set -eu
out=../resources/dev/goldberry/media/bitstream
fixtures=../resources/dev/goldberry/media/fixtures
platform=../resources/dev/goldberry/media/platform/fixtures
mkdir -p "$out"

# Section headings are the lines without a bit position; SEI sections (x265
# writes its whole command line as user data, a line a character) are dropped.
trace() {
    ffmpeg -hide_banner -loglevel trace -i "$1" -map 0:v:0 -c copy -bsf:v trace_headers -f null - 2>&1 \
        | sed -n 's/^\[trace_headers @ 0x[0-9a-f]*\] //p' \
        | awk '/^[0-9]+ / { if (keep) print; next }
               { keep = ($0 !~ /Supplemental Enhancement|User Data|Payload|Message|Recovery Point|Mastering|Content Light|Buffering|Picture Timing|Decoded Picture Hash|Alternative/); if (keep) print }' \
        > "$out/$2.trace"
}

# AV1's: FFmpeg's cbs_av1 prints each OBU's header, the sequence header, the
# frame header and a tile group's own fields, never the tile data. Its summary
# after each frame header ("Frame 16: size 160x90 upscaled 160 render 160x90
# ... tiles 1x1.") is kept: the sizes it worked out. The column padding is
# squeezed out, which halves the files.
trace_av1() {
    ffmpeg -hide_banner -loglevel trace -i "$1" -map 0:v:0 -c copy -bsf:v trace_headers -f null - 2>&1 \
        | sed -n 's/^\[trace_headers @ 0x[0-9a-f]*\] //p' | tr -s ' ' > "$out/$2.trace"
}

trace "$platform/clip-hevc.mp4" clip-hevc-mp4
trace "$platform/clip-hevc-10bit.mp4" clip-hevc-10bit-mp4
trace "$platform/clip-hevc-pq.mp4" clip-hevc-pq-mp4
trace "$platform/clip-h264-high.mp4" clip-h264-high-mp4
trace "$platform/clip-h264-full-709.mp4" clip-h264-full-709-mp4
# VP9: each frame's uncompressed header, a superframe's index before its
# frames, and FFmpeg's "Frame:  size …" summary after each header.
trace "$fixtures/clip-vp9-full.webm" clip-vp9-full-webm
trace "$fixtures/clip-vp9-444.webm" clip-vp9-444-webm
trace "$fixtures/clip-vp9-2020-10bit.webm" clip-vp9-2020-10bit-webm
trace "$fixtures/clip-vp9-444-10bit.webm" clip-vp9-444-10bit-webm
trace "$fixtures/clip-vp9-superframes.webm" clip-vp9-superframes-webm
# Six frames libvpx's encoder does not make, written field by field by the
# tests' HeaderWriter and kept as they are in vp9-hand-made.ivf (Vp9HeadersTest
# holds the file to what HeaderWriter writes): a frame shown again, an intra-only
# frame, an error-resilient one of its own size, a render size, tiles, deltas.
trace "$out/vp9-hand-made.ivf" vp9-hand-made-ivf
# AV1: OBU headers, sequence and frame headers, tile groups.
trace_av1 "$fixtures/clip-av1.mkv" clip-av1-mkv
trace_av1 "$fixtures/clip-av1-pq.mkv" clip-av1-pq-mkv
trace_av1 "$fixtures/clip-av1-tools.mkv" clip-av1-tools-mkv
trace_av1 "$fixtures/clip-av1-superres.mkv" clip-av1-superres-mkv
trace_av1 "$fixtures/clip-av1-resilient.mkv" clip-av1-resilient-mkv
trace_av1 "$fixtures/clip-av1-still.mkv" clip-av1-still-mkv
trace_av1 "$fixtures/clip-av1-timing.mkv" clip-av1-timing-mkv
ls -l "$out"

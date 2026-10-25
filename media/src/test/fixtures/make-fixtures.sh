#!/bin/sh
# Regenerates the encoded test clips in
# src/test/resources/dev/goldberry/media/fixtures/.
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
out=../resources/dev/goldberry/media/fixtures
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

# Theora, the pattern in Ogg beside Vorbis (an .ogv as it is met) and in
# Matroska beside Opus: a keyframe every ten frames, for the seeks. libtheora
# codes the 160x90 picture in a 160x96 frame, the six rows to spare at the
# bottom, so a decoder must crop. Then 4:4:4 and 4:2:2, silent and a fifth of
# a second each, which the built-in decoder subsamples to I420.
$ff -f lavfi -i "$pattern" -i "$tone" -c:v libtheora -q:v 7 -g 10 -c:a vorbis -strict experimental -ac 2 -q:a 3 \
    -shortest "$out/clip-theora.ogv"
$ff -f lavfi -i "$pattern" -i "$tone" -c:v libtheora -q:v 7 -g 10 -c:a libopus -b:a 64k -shortest \
    "$out/clip-theora.mkv"
$ff -f lavfi -i "testsrc2=s=160x90:r=25:d=0.2" -c:v libtheora -pix_fmt yuv444p -q:v 7 "$out/clip-theora-444.ogv"
$ff -f lavfi -i "testsrc2=s=160x90:r=25:d=0.2" -c:v libtheora -pix_fmt yuv422p -q:v 7 "$out/clip-theora-422.ogv"

# The MP4 demuxer's other shapes, remuxed from the clips above: the moov before
# the mdat (a source that cannot seek reads it forward), the cover art in
# udta/meta/ilst/covr, and a fragmented file of five moof boxes.
$ff -i "$out/tone-flac.mp4" -c copy -strict -2 -movflags faststart -f mp4 "$out/tone-flac-faststart.mp4"
$ff -i "$out/tone-opus.mp4" -i "$out/tone-cover.mp3" -map 0:a -map 1:v -c copy -disposition:v attached_pic \
    -f mp4 "$out/tone-cover.m4a"
$ff -i "$out/clip-av1.mp4" -c copy -strict -2 -movflags frag_keyframe+empty_moov+default_base_moof \
    -frag_duration 200000 -f mp4 "$out/clip-av1-fragmented.mp4"

# Three pictures outside 8-bit 4:2:0, silent and a fifth of a second each: VP9
# profile 1 (4:4:4), which the built-in decoder converts to I420, profile 2
# (10-bit 4:2:0), which it lends as I010, and profile 3 (10-bit 4:4:4), which
# it converts to I010.
short="testsrc2=s=160x90:r=25:d=0.2"
$ff -f lavfi -i "$short" -c:v libvpx-vp9 -pix_fmt yuv444p -b:v 200k "$out/clip-vp9-444.webm"
$ff -f lavfi -i "$short" -c:v libvpx-vp9 -pix_fmt yuv420p10le -b:v 200k "$out/clip-vp9-10bit.webm"
$ff -f lavfi -i "$short" -c:v libvpx-vp9 -pix_fmt yuv444p10le -b:v 200k "$out/clip-vp9-444-10bit.webm"

# Three pictures tagged with the colour GPU present has to honour,
# silent and a fifth of a second each: BT.709, BT.2020 in
# 10 bits, and BT.601 in full range. The pattern is converted into each matrix
# and range by the scale filter, and the stream is tagged to match, so the
# decoder reports what it was encoded with.
$ff -f lavfi -i "$short" -vf "scale=out_color_matrix=bt709:out_range=tv,format=yuv420p" \
    -c:v libvpx-vp9 -b:v 200k -colorspace bt709 -color_primaries bt709 -color_trc bt709 -color_range tv \
    "$out/clip-vp9-709.webm"
$ff -f lavfi -i "$short" -vf "scale=out_color_matrix=bt2020:out_range=tv,format=yuv420p10le" \
    -c:v libvpx-vp9 -b:v 200k -colorspace bt2020nc -color_primaries bt2020 -color_trc bt709 -color_range tv \
    "$out/clip-vp9-2020-10bit.webm"
$ff -f lavfi -i "$short" -vf "scale=out_color_matrix=bt601:out_range=pc,format=yuv420p" \
    -c:v libvpx-vp9 -b:v 200k -colorspace smpte170m -color_primaries smpte170m -color_trc smpte170m \
    -color_range pc "$out/clip-vp9-full.webm"

# --- Superframes: VP9 with hidden alternate reference frames, each packed with
# the frame shown after it into one packet behind a superframe index (Annex B).
# libvpx makes them in two passes only. 160x128 for 0.6 s, silent, with
# variance AQ, so that segmentation is coded too (a map, temporal prediction,
# feature data); 128 rows at least, which Vulkan Video's VP9 decode wants.
# FFmpeg's sums of its fifteen pictures, I420, go beside the other VP9 clips'
# in ../container/.
for pass in 1 2; do
    target=/dev/null
    [ "$pass" = 2 ] && target="$out/clip-vp9-superframes.webm"
    $ff -f lavfi -i "testsrc2=s=160x128:r=25:d=0.6" -c:v libvpx-vp9 -b:v 200k -auto-alt-ref 1 \
        -lag-in-frames 16 -aq-mode 1 -pass "$pass" -passlogfile "$work/vp9-superframes" -f webm "$target"
done
$ff -i "$out/clip-vp9-superframes.webm" -map 0:v:0 -pix_fmt yuv420p -f framemd5 - | grep -v '^#' \
    > ../resources/dev/goldberry/media/container/clip-vp9-superframes-webm.framemd5

# --- A sticker: VP9 with an alpha channel, as Telegram sends one, 64x64 at 10 fps
# for one second. Red everywhere; the alpha is the picture. In the top half an
# opaque 16x16 square at x = 4n, y 8..23 in frame n, transparent around it; in
# the bottom 24 rows half alpha (128); between them transparent. Lossless, so
# the decoded alpha is exactly that. libvpx encodes the alpha as a second VP9
# stream in each block's BlockAdditional and marks the track with AlphaMode.
$ff -f lavfi -i "color=c=red:s=64x64:r=10:d=1,format=rgba,geq=r='255':g='0':b='0':a='if(lt(Y,32),if(between(X,4*N,4*N+15)*between(Y,8,23),255,0),if(gte(Y,40),128,0))'" \
    -c:v libvpx-vp9 -pix_fmt yuva420p -lossless 1 -auto-alt-ref 0 "$out/sticker-vp9-alpha.webm"

# --- Ten seconds with a single keyframe, at zero, and an Opus sine: a paused
# accurate seek decodes from there, so the audio before a target 9.5 s in must
# not fill its queue and stop the demuxer short of the target. 32x18, so it stays
# small.
$ff -f lavfi -i "testsrc2=s=32x18:r=25:d=10" -f lavfi -i "sine=f=440:d=10:r=48000" \
    -c:v libvpx-vp9 -crf 50 -b:v 0 -g 250 -keyint_min 250 -c:a libopus -b:a 24k -shortest \
    "$out/clip-vp9-long-gop.webm"

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

# --- Two video tracks, for video track switching (phase 7): the VP9 `testsrc2`
# at 160x90 titled "Wide" and marked default, and SMPTE bars in VP8 at 96x54
# titled "Close", so a switch shows in the picture's size alone; the tone in
# Opus, so the switch is timed on the audio clock.
$ff -f lavfi -i "$pattern" -f lavfi -i "smptebars=s=96x54:r=25:d=1" -i "$tone" -map 0:v -map 1:v -map 2:a \
    -c:v:0 libvpx-vp9 -b:v:0 200k -c:v:1 libvpx -b:v:1 100k -c:a libopus -b:a 64k -shortest \
    -metadata:s:v:0 title=Wide -metadata:s:v:1 title=Close -disposition:v:0 default -disposition:v:1 0 \
    "$out/clip-two-angles.mkv"

# --- Subtitles, for phase 7: the VP9 clip with a SubRip track tagged English and
# an ASS track tagged French, whose overrides and \N the Engine takes out.
cat > "$work/subs.srt" <<'SRT'
1
00:00:00,100 --> 00:00:00,400
First line

2
00:00:00,500 --> 00:00:00,900
<i>Second</i> line
SRT
cat > "$work/subs.ass" <<'ASS'
[Script Info]
ScriptType: v4.00+
PlayResX: 160
PlayResY: 90

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,12,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,2,10,10,10,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.20,0:00:00.60,Default,,0,0,0,,{\i1}Première{\i0}\Nligne
ASS
$ff -i "$out/clip-vp9.webm" -i "$work/subs.srt" -i "$work/subs.ass" -map 0:v -map 1:s -map 2:s -c:v copy -c:s:0 srt \
    -c:s:1 ass -metadata:s:s:0 language=eng -metadata:s:s:1 language=fra "$out/clip-vp9-subs.mkv"

# --- The patent-pool pair the published natives deliberately do not decode
# (goldberry-media.md S7): H.264 video and AAC audio in MP4. It opens, and it
# must report UNSUPPORTED_CODEC naming both.
$ff -f lavfi -i "$pattern" -i "$tone" -c:v libx264 -preset ultrafast -c:a aac -b:a 64k -shortest \
    "$out/clip-h264-aac.mp4"

# --- Containers. AVI is demuxed: an Xvid and AC-3 rip opens and names
# both codecs it cannot decode, and MP3 in AVI plays. MPEG-TS and FLV have no
# demuxer in this build, and are named from their first bytes.
tiny="testsrc2=s=160x90:r=25:d=0.2"
$ff -f lavfi -i "$tiny" -i "$tone" -t 0.2 -c:v mpeg4 -vtag XVID -c:a ac3 -b:a 96k "$out/clip-xvid-ac3.avi"
$ff -i "$tone" -c:a libmp3lame -b:a 128k -f avi "$out/tone-mp3.avi"
$ff -f lavfi -i "$tiny" -i "$tone" -t 0.2 -c:v mpeg2video -c:a mp2 -f mpegts "$out/clip-mpeg2.ts"
$ff -f lavfi -i "$tiny" -c:v flv1 -an -f flv "$out/clip-flv1.flv"

# --- Chapters: the tone with three, Intro at 0, Middle at 0.3 s and Coda at
# 0.7 s, in every container that states chapters. Matroska and the M4A (a
# QuickTime chapter track and a Nero chpl both) from FFmpeg; the MP3's are
# ID3v2 CHAP frames. Ogg and FLAC write them as CHAPTERnnn comments: FFmpeg
# writes the Ogg Opus one a second late for Coda, which fix-ogg-chapter.py
# puts back, and does not write FLAC's at all, which flac-chapters.py does.
cat > "$work/chapters.txt" <<'META'
;FFMETADATA1
[CHAPTER]
TIMEBASE=1/1000
START=0
END=300
title=Intro
[CHAPTER]
TIMEBASE=1/1000
START=300
END=700
title=Middle
[CHAPTER]
TIMEBASE=1/1000
START=700
END=1000
title=Coda
META
chapters="-i $work/chapters.txt -map_metadata 1 -map_chapters 1"
$ff -i "$tone" $chapters -c:a flac "$out/tone-chapters.mkv"
$ff -i "$tone" $chapters -c:a aac -b:a 32k "$out/tone-chapters.m4a"
$ff -i "$tone" $chapters -c:a libmp3lame -b:a 64k "$out/tone-chapters.mp3"
$ff -i "$tone" $chapters -c:a libopus -b:a 32k "$out/tone-chapters.opus"
python3 -I fix-ogg-chapter.py "$out/tone-chapters.opus" "CHAPTER002=00:00:01.700" "CHAPTER002=00:00:00.700"
$ff -i "$tone" -c:a flac "$out/tone-chapters.flac"
python3 -I flac-chapters.py "$out/tone-chapters.flac"

# --- HDR: the colour description from the codec, from the container, and the
# light levels. HDR10's AV1, 10-bit PQ in BT.2020, with a mastering display of
# 1000 nits and MaxCLL 1000 in metadata OBUs, which SVT-AV1 writes from its own
# parameters; the sequence header carries the primaries and transfer function.
# The same stream in MP4, where FFmpeg writes them as `colr`. VP9 has no
# transfer function in its bitstream, so HLG in BT.2020 is the WebM `Colour`
# element's alone.
pq="color-primaries=9:transfer-characteristics=16:matrix-coefficients=9"
mastering="mastering-display=G(0.265,0.690)B(0.150,0.060)R(0.680,0.320)WP(0.3127,0.3290)L(1000,0.0001)"
$ff -f lavfi -i "$short" -pix_fmt yuv420p10le -c:v libsvtav1 -preset 12 -crf 45 \
    -svtav1-params "$pq:$mastering:content-light=1000,400" "$out/clip-av1-pq.mkv"
$ff -i "$out/clip-av1-pq.mkv" -c copy "$out/clip-av1-pq.mp4"
$ff -f lavfi -i "$short" \
    -vf "format=yuv420p10le,setparams=color_primaries=bt2020:color_trc=arib-std-b67:colorspace=bt2020nc" \
    -c:v libvpx-vp9 -profile:v 2 -b:v 200k "$out/clip-vp9-hlg.webm"

# --- Past the 8K cap: one flat picture each. 6000x6000 is 36 megapixels with
# both sides within 8192; 8256 is one side past it.
$ff -f lavfi -i "color=c=0x336699:s=6000x6000:r=25:d=0.04" -frames:v 1 -c:v libsvtav1 -preset 12 -crf 63 \
    "$out/clip-av1-36mp.mkv"
$ff -f lavfi -i "color=c=0x336699:s=8256x64:r=25:d=0.04" -frames:v 1 -c:v libsvtav1 -preset 12 -crf 63 \
    "$out/clip-av1-8256-wide.mkv"
$ff -f lavfi -i "color=c=0x336699:s=6000x6000:r=25:d=0.04" -frames:v 1 -c:v libvpx-vp9 -crf 63 -b:v 0 \
    -deadline realtime -cpu-used 8 "$out/clip-vp9-36mp.webm"
$ff -f lavfi -i "color=c=0x336699:s=8256x16:r=25:d=0.04" -frames:v 1 -c:v libvpx-vp9 -crf 63 -b:v 0 \
    "$out/clip-vp9-8256-wide.webm"

# --- AV1 for the bitstream readers, which make-traces.sh holds to FFmpeg: the
# frame header's coding tools that the pattern clips never use. A pan across a
# noisy pattern at 320x180, so that libaom finds global motion and grain to
# model. libaom, eight frames: 2x2 tiles, film grain, variance segmentation,
# delta q and delta lf, quantizer matrices, loop restoration, global motion.
# SVT-AV1, twelve frames: superres, each frame coded at 8/12 of its width, and
# an open GOP, whose second GOP starts on an intra-only frame. libaom realtime,
# error resilient, six frames: frame ids, every reference's order hint, tiles
# of uneven sizes (two and three superblocks wide, one and two high), and each
# frame's four tiles in two tile group OBUs after a frame header OBU.
pan="testsrc2=s=480x270:r=25:d=1,crop=320:180:x='t*120':y='t*50'"
$ff -f lavfi -i "testsrc2=s=480x270:r=25:d=1,noise=alls=14:allf=t,crop=320:180:x='t*120':y='t*50'" -frames:v 8 \
    -pix_fmt yuv420p -c:v libaom-av1 -cpu-used 1 -crf 45 -tiles 2x2 -row-mt 0 -denoise-noise-level 12 \
    -aq-mode variance -enable-restoration 1 -enable-global-motion 1 \
    -aom-params "deltaq-mode=1:delta-lf-mode=1:enable-qm=1" "$out/clip-av1-tools.mkv"
$ff -f lavfi -i "$pan" -frames:v 12 -g 6 -pix_fmt yuv420p -c:v libsvtav1 -preset 10 -crf 45 \
    -svtav1-params "irefresh-type=1:superres-mode=1:superres-denom=12" "$out/clip-av1-superres.mkv"
$ff -f lavfi -i "testsrc2=s=320x180:r=25:d=1" -frames:v 6 -pix_fmt yuv420p -c:v libaom-av1 -usage realtime \
    -cpu-used 8 -crf 45 -error-resilience default -aom-params "tile-width=2,3:tile-height=1,2:num-tile-groups=2" \
    "$out/clip-av1-resilient.mkv"
# One lossless monochrome still picture, 64x64, with the reduced sequence
# header; and three frames whose sequence header carries timing info and a
# decoder model, so each frame header has its presentation and removal times.
$ff -f lavfi -i "testsrc2=s=64x64:r=25:d=1" -frames:v 1 -pix_fmt gray -c:v libaom-av1 -still-picture 1 -crf 0 \
    -cpu-used 6 "$out/clip-av1-still.mkv"
$ff -f lavfi -i "testsrc2=s=64x64:r=25:d=1" -frames:v 3 -pix_fmt yuv420p -c:v libaom-av1 -cpu-used 6 -crf 50 \
    -aom-params "timing-info=model" "$out/clip-av1-timing.mkv"

# A bare FLAC with cover art: the tone again, and a small PNG as a PICTURE block.
$ff -i "$out/tone.flac" -i "$work/cover.png" -map 0:a -map 1:v -c copy -disposition:v attached_pic -f flac "$out/tone-cover.flac"

rm -r "$work"
ls -l "$out"

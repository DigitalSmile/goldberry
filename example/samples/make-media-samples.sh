#!/bin/sh
# Regenerates the Media screen's sample clips in
# src/main/resources/io/github/digitalsmile/goldberry/example/media/.
#
# One synthetic piece of audio, written sample by sample below: twelve seconds of a
# plucked arpeggio over a drone, 48 kHz stereo, panned note by note. It is our own
# content, so the clips carry no third-party licence. The video is FFmpeg's own
# synthetic sources (a Mandelbrot zoom, the Game of Life), ours for the same reason. Encoded by a full FFmpeg on
# the developer's machine (`brew install ffmpeg`); nothing of it ships.
#
# Run from this directory: ./make-media-samples.sh
set -eu
out=../src/main/resources/io/github/digitalsmile/goldberry/example/media
work=$(mktemp -d)
ff="ffmpeg -hide_banner -loglevel error -y -bitexact"
# SVT-AV1 logs through its own channel; 1 is errors only.
export SVT_LOG=1

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

# Video, from FFmpeg's own synthetic sources so it is ours too. VP9 with the
# arpeggio's first eight seconds as Opus: pictures timed by the audio clock.
$ff -f lavfi -i "mandelbrot=s=320x180:rate=25:end_pts=300" -i "$wav" -t 8 -map 0:v -map 1:a \
    -c:v libvpx-vp9 -pix_fmt yuv420p -b:v 250k -row-mt 1 -g 50 -c:a libopus -b:a 64k \
    -metadata title="Mandelbrot (VP9 and Opus)" "$out/mandelbrot.webm"
# AV1 with no sound at all: pictures timed by the free-running clock.
$ff -f lavfi -i "life=s=320x180:rate=25:mold=10:ratio=0.1:death_color=#2e3440:life_color=#88c0d0" -t 4 \
    -c:v libsvtav1 -preset 10 -crf 55 -g 50 -metadata title="Life (AV1)" "$out/life.mkv"

# --- Phase 7 samples: track menus and subtitles. ------------------------------
# The arpeggio an octave down (half the rate, half the speed, so its first twelve
# seconds), as a second voice for the track menus.
$ff -i "$wav" -af "asetrate=24000,aresample=48000" -t 12 "$work/octave.wav"

# Two audio tracks, in Matroska audio: the arpeggio tagged English and titled,
# and the octave below tagged French. The audio-player's track menu names both.
$ff -i "$wav" -i "$work/octave.wav" -map 0:a -map 1:a -c:a libopus -b:a 96k \
    -metadata:s:a:0 language=eng -metadata:s:a:0 title="Concert pitch" \
    -metadata:s:a:1 language=fra -metadata:s:a:1 title="Une octave plus bas" \
    -disposition:a:0 default -disposition:a:1 0 -metadata title="Arpeggio, two voices" \
    "$out/arpeggio-two-voices.mka"

# Subtitles: the Mandelbrot zoom with a SubRip track tagged English and an ASS
# track tagged French, whose styling the engine strips to plain lines. The same
# cues as files, for "Load subtitles": SubRip and WebVTT.
cat > "$out/mandelbrot.srt" <<'SRT'
1
00:00:00,500 --> 00:00:02,000
A Mandelbrot zoom, in VP9

2
00:00:02,300 --> 00:00:04,000
<i>Every picture</i> is timed
by the audio clock

3
00:00:04,300 --> 00:00:06,000
Subtitles are read in Java

4
00:00:06,300 --> 00:00:07,800
and drawn over the picture
SRT
cat > "$out/mandelbrot.vtt" <<'VTT'
WEBVTT

00:00.500 --> 00:02.000
Loaded from a WebVTT file

00:02.300 --> 00:04.000
<b>Cues</b> from a file
replace a track's

00:04.300 --> 00:06.000
"Hide subtitles" takes them away

00:06.300 --> 00:07.800
and the menu offers them again
VTT
cat > "$work/mandelbrot-fra.ass" <<'ASS'
[Script Info]
ScriptType: v4.00+
PlayResX: 320
PlayResY: 180

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,14,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,2,10,10,10,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.50,0:00:02.00,Default,,0,0,0,,Un zoom sur {\i1}Mandelbrot{\i0}
Dialogue: 0,0:00:02.30,0:00:04.00,Default,,0,0,0,,Chaque image suit\Nl'horloge audio
Dialogue: 0,0:00:04.30,0:00:06.00,Default,,0,0,0,,{\b1}Sous-titres{\b0} lus en Java
Dialogue: 0,0:00:06.30,0:00:07.80,Default,,0,0,0,,et dessinés sur l'image
ASS
$ff -i "$out/mandelbrot.webm" -i "$out/mandelbrot.srt" -i "$work/mandelbrot-fra.ass" \
    -map 0:v -map 0:a -map 1:s -map 2:s -c:v copy -c:a copy -c:s:0 srt -c:s:1 ass \
    -metadata:s:s:0 language=eng -metadata:s:s:1 language=fra \
    -metadata title="Mandelbrot, subtitled" "$out/mandelbrot-subtitled.mkv"

# Two angles: the Mandelbrot zoom and a Sierpinski carpet at 4:3, each with a
# title, over the two voices of the arpeggio. Both track menus, one file.
$ff -f lavfi -i "sierpinski=s=240x180:rate=25:type=carpet" -t 8 \
    -c:v libvpx-vp9 -pix_fmt yuv420p -b:v 200k -row-mt 1 -g 50 "$work/carpet.webm"
$ff -i "$out/mandelbrot.webm" -i "$work/carpet.webm" -i "$work/octave.wav" \
    -map 0:v -map 1:v -map 0:a -map 2:a -t 8 -c:v copy -c:a:0 copy -c:a:1 libopus -b:a:1 64k \
    -metadata:s:v:0 title="Zoom" -metadata:s:v:1 title="Carpet" \
    -disposition:v:0 default -disposition:v:1 0 \
    -metadata:s:a:0 language=eng -metadata:s:a:1 language=fra -metadata:s:a:1 title="Une octave plus bas" \
    -disposition:a:0 default -disposition:a:1 0 -metadata title="Two angles, two voices" \
    "$out/two-angles.mkv"

# The patent-pool pair the published natives do not decode: the error state.
$ff -f lavfi -i "testsrc2=s=160x90:r=25:d=1" -f lavfi -i "sine=f=440:d=1" \
    -c:v libx264 -preset ultrafast -c:a aac -b:a 48k -shortest "$out/h264-aac.mp4"

rm -r "$work"
ls -l "$out"

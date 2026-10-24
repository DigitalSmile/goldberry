# Encoded test clips

Made by `media/src/test/fixtures/make-fixtures.sh` (a full FFmpeg on the
developer's machine, `brew install ffmpeg`) from synthetic signals, so every clip
is our own content and carries no third-party licence:

- **audio:** one second of a 440 Hz sine at 12000/32768, 48 kHz stereo;
- **video:** one second of FFmpeg's `testsrc2` pattern, 160×90 at 25 fps, with
  the tone as audio.

| File | Container | Codecs | Used for |
|------|-----------|--------|----------|
| `tone.flac` | FLAC | FLAC | sample-exact decode and seek |
| `tone-cover.flac` | FLAC | FLAC, the 64×64 PNG as a PICTURE block | the bare FLAC container's cover art |
| `tone-flac.mp4` | MP4 | FLAC | the `mov` demuxer, losslessly |
| `tone-flac.mkv` | Matroska | FLAC | the `matroska` demuxer, losslessly |
| `tone-opus.mp4` | MP4 | Opus | |
| `tone.opus` | Ogg | Opus | the `ogg` demuxer |
| `tone-opus.webm` | WebM | Opus | |
| `tone-flac.oga` | Ogg | FLAC | the `ogg` demuxer's FLAC mapping: `ffmpeg -i tone.flac -c copy tone-flac.oga` |
| `tone.ogg` | Ogg | Vorbis | |
| `tone.mp3` | MP3 | MP3 | |
| `tone-cover.mp3` | MP3 | MP3, PNG cover art | the attached-picture track |
| `tone-chapters.mkv` | Matroska | FLAC; three chapters, Intro 0–0.3 s, Middle 0.3–0.7 s, Coda 0.7–1 s, in `Chapters` | chapters: an edition's atoms, their ends and titles |
| `tone-chapters.m4a` | MP4 | AAC; the same chapters as a QuickTime chapter track (`tref/chap`) and a Nero `chpl` | chapters: the chapter track is read, and is not a subtitle track |
| `tone-chapters.mp3` | MP3 | MP3; the same chapters as ID3v2.4 `CHAP` frames titled by `TIT2` | chapters: a podcast's |
| `tone-chapters.opus` | Ogg | Opus; the same chapters as `CHAPTERnnn` comments, Coda's time put back by `fix-ogg-chapter.py` | chapters: Xiph comments in `OpusTags` |
| `tone-chapters.flac` | FLAC | FLAC; the same chapters as `CHAPTERnnn` comments, written by `flac-chapters.py` | chapters: the `VORBIS_COMMENT` block |
| `clip-vp8.webm` | WebM | VP8, Opus | phase 3: decode, and the byte-exact goldens of S5 |
| `clip-vp9.webm` | WebM | VP9, Opus | phase 3 |
| `clip-av1.mkv` | Matroska | AV1, Opus | phase 3 (dav1d) |
| `clip-av1.mp4` | MP4 | AV1, FLAC | phase 3 |
| `clip-av1-fragmented.mp4` | MP4, fragmented | the AV1 and FLAC of `clip-av1.mp4`, remuxed with `empty_moov` into five `moof` fragments | the MP4 demuxer's `moof/traf/trun` path |
| `tone-flac-faststart.mp4` | MP4, `moov` before `mdat` | the FLAC of `tone-flac.mp4` | the MP4 demuxer on a source that cannot seek |
| `tone-cover.m4a` | MP4 | the Opus of `tone-opus.mp4`, the PNG cover of `tone-cover.mp3` in `udta/meta/ilst/covr` | the MP4 demuxer's attached-picture track |
| `clip-vp9-long-gop.webm` | WebM | VP9 `testsrc2` at 32×18, one keyframe, at zero; Opus sine; ten seconds | a paused accurate seek 9.5 s past its keyframe, in a source with audio |
| `clip-vp9-444.webm` | WebM | VP9 profile 1 (4:4:4), 0.2 s, silent | the built-in decoder's conversion to I420 |
| `clip-vp9-10bit.webm` | WebM | VP9 profile 2 (10-bit 4:2:0), 0.2 s, silent | I010, and video with no audio on the free-running clock |
| `clip-vp9-444-10bit.webm` | WebM | VP9 profile 3 (10-bit 4:4:4), 0.2 s, silent | the built-in decoder's subsampling of 16-bit planes to I010 |
| `clip-vp9-709.webm` | WebM | VP9, tagged BT.709, limited range, 0.2 s, silent | GPU present's parity for the HD matrix (ADR-0484) |
| `clip-vp9-2020-10bit.webm` | WebM | VP9 profile 2, tagged BT.2020 (non-constant luminance), limited range, 0.2 s, silent | GPU present's parity for the UHD matrix in 10 bits |
| `clip-vp9-full.webm` | WebM | VP9, tagged BT.601 (SMPTE 170M), full range, 0.2 s, silent | GPU present's parity for full-range pictures |
| `sticker-vp9-alpha.webm` | WebM | VP9 lossless with an alpha stream (`AlphaMode`, BlockAdditional), 64×64 at 10 fps, one second, silent: red, an opaque square moving 4 px a frame in the top half, half alpha in the bottom 24 rows | the video sticker: the side data, the alpha plane, premultiplied pictures, and a loop |
| `tones-two-tracks.mkv` | Matroska | FLAC twice: 440 Hz tagged `eng` and titled "Concert pitch", 880 Hz tagged `fra`; two seconds each | track selection: a switch lands on the new track's sample, and a track menu's labels |
| `clip-two-angles.mkv` | Matroska | VP9 `testsrc2` at 160×90 titled "Wide" (default), VP8 SMPTE bars at 96×54 titled "Close", Opus tone; one second | video track switching: which track shows is the picture's size |
| `clip-vp9-subs.mkv` | Matroska | the VP9 of `clip-vp9.webm`, a SubRip track tagged `eng` (two cues, one in `<i>`), an ASS track tagged `fra` (one cue with `{\i1}` overrides and a `\N` break) | subtitles: a track's cues at their times, markup taken out |
| `guide-clip.mkv` | Matroska | VP9 still of a lake at dusk drawn with Pillow, 1280×720 at 25 fps; Opus 220 Hz sine; SubRip track tagged `eng` with one two-line cue from 3 s to 6 s; twelve seconds. Made by `make-guide-clip.sh` | the guide's pictures of `media-player` and `audio-player` (`MediaPicturesTest`) |
| `clip-h264-aac.mp4` | MP4 | H.264, AAC | S7: opens, and reports `UNSUPPORTED_CODEC` |
| `clip-xvid-ac3.avi` | AVI | MPEG-4 Part 2 (`XVID`), AC-3; 0.2 s | ADR-0471: an AVI rip opens and names both codecs |
| `tone-mp3.avi` | AVI | MP3 | the AVI demuxer plays what the build decodes |
| `clip-mpeg2.ts` | MPEG-TS | MPEG-2 video, MP2; 0.2 s | ADR-0471: no demuxer, named `MPEG-TS` from its sync bytes |
| `clip-flv1.flv` | FLV | Sorenson H.263; 0.2 s | ADR-0471: no demuxer, named `FLV` |

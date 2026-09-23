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
| `tone-flac.mp4` | MP4 | FLAC | the `mov` demuxer, losslessly |
| `tone-flac.mkv` | Matroska | FLAC | the `matroska` demuxer, losslessly |
| `tone-opus.mp4` | MP4 | Opus | |
| `tone.opus` | Ogg | Opus | the `ogg` demuxer |
| `tone-opus.webm` | WebM | Opus | |
| `tone.ogg` | Ogg | Vorbis | |
| `tone.mp3` | MP3 | MP3 | |
| `tone-cover.mp3` | MP3 | MP3, PNG cover art | the attached-picture track |
| `clip-vp8.webm` | WebM | VP8, Opus | phase 3 |
| `clip-vp9.webm` | WebM | VP9, Opus | phase 3 |
| `clip-av1.mkv` | Matroska | AV1, Opus | phase 3 (dav1d) |
| `clip-av1.mp4` | MP4 | AV1, FLAC | phase 3 |
| `clip-h264-aac.mp4` | MP4 | H.264, AAC | S7: opens, and reports `UNSUPPORTED_CODEC` |

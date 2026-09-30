# Encoded test clips for the platform decoders

Made by `media-platform/src/test/fixtures/make-fixtures.sh` (a full FFmpeg on
the developer's machine, `brew install ffmpeg`) from synthetic signals, so every
clip is our own content and carries no third-party licence. They are the
patent-pool codecs the published natives deliberately do not decode; the
operating system's decoders play them (ADR-0472): VideoToolbox and AudioToolbox,
GStreamer, and Media Foundation (ADR-0489).

- **audio:** one second of a 440 Hz sine at 12000/32768, 48 kHz stereo; or six
  channels, one frequency each (200, 300, 400, 60, 500 and 600 Hz in FFmpeg's
  5.1 order: FL FR FC LFE BL BR);
- **video:** FFmpeg's `testsrc2` pattern at 25 fps.

Each video clip has a `.framemd5` beside it: the MD5 of every picture as FFmpeg's
own decoder made it, as NV12 (or P010 for 10-bit), which every platform decoder
must match byte for byte. A decoder that hands over planar I420 or I010 is
hashed as its semi-planar twin (`Fixtures.md5`).

| File | Container | Codecs | Used for |
|------|-----------|--------|----------|
| `clip-h264-high.mp4` | MP4 | H.264 High, B-frames in a pyramid, a keyframe every 10; AAC | bit-exact decode and reordering, the 160×96→160×90 crop, seeking, AAC in MP4, S8 end to end |
| `clip-h264-high.mkv` | Matroska | the same streams | reordering with no decoding times |
| `clip-h264-full-709.mp4` | MP4 | H.264, full range, BT.709 in the VUI; 0.2 s | colour from the stream |
| `clip-h264-720p.mp4` | MP4 | H.264 at 1280×720, untagged; 0.2 s | the media engine, and the BT.709 default |
| `clip-hevc.mp4` | MP4 | HEVC Main (`hvc1`), B-frames | bit-exact HEVC, S8 end to end |
| `clip-hevc-10bit.mp4` | MP4 | HEVC Main 10, BT.2020 in the VUI; 0.2 s | P010, colour from an HEVC stream |
| `tone-aac.mkv` | Matroska | AAC | AAC configured from `CodecPrivate`; seeking |
| `tone-eac3.mp4` | MP4 | E-AC-3 | E-AC-3 |
| `tones-aac-5.1.mp4` | MP4 | AAC 5.1 | channel order |
| `tones-ac3-5.1.mkv` | Matroska | AC-3 5.1(side) | channel order |

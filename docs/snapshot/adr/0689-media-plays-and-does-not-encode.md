# ADR-0689: Media plays and does not encode

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0651 to ADR-0658 (the media stack without FFmpeg)

## Context

A messenger asked for Opus and VP9 encoders (with k-SVC layers for group
calls) and for the decoders to be callable from application code (Tessera's
G-06, here T-06). `goldberry-media` builds libvpx with its VP9 encoder
disabled; libopus contains its encoder but `libgoldberry-media` exports none
of it; libyuv is built without the ARGB→I420 direction a camera path needs.
The decoder SPI (`media.codec`, `Decoder`/`DecoderProvider`) is exported
already.

## Decision

**Declined.** `goldberry-media` is a playback module: it demuxes, decodes and
presents. It does not encode.

An encoder is the half of a call stack whose tuning is the application's:
bitrate control, SVC layering, keyframe requests from the far end, the
jitter buffer it feeds. It would be the first part of `:media` whose
correct settings depend on a network, and the VP9 encoder alone would take
the library past its 6 MB size gate. An application that sends media brings
its own encoders in its own native build, as Tessera's D-07 plans, and
decodes what it receives through the exported `Decoder` SPI.

## Consequences

- No encoder SPI and no new exports from `libgoldberry-media`.
- Decoded pictures reach the screen through `PictureRenderer` today, and
  through a public picture source once W2.7 lands.
- Camera capture (W3.4) produces `VideoPlanes`; what an application encodes
  them with stays its own.

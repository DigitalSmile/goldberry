# 385. WebP is written, and animated

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "PNG is the only format written" for WebP,
and the animated-WebP half of "One frame only" that ADR-0382 left open.

## Context

ADR-0329 linked libwebp's `webpdecoder` target — the decoder alone — and said
why: "Goldberry reads one still frame and writes PNG, so the encoder would be
several hundred kilobytes of code nothing can reach." The encoder, the muxer and
the animation demuxer were all switched off.

Two things have happened since. `Offscreen` renders a picture an application
wants to *write* — a thumbnail, a preview, a screenshot of a canvas — and PNG is
the only thing it can write. And ADR-0382 built animated GIF, leaving animated
WebP as the whole of what remained of "one frame only".

Both live in targets upstream already builds. `webp` is the decoder plus the
encoder; `webpdemux` is the animation reader over it. Neither is new code.

## Decision

**Link `webp` and `webpdemux`; export the encoder and the animation decoder.**

- `Image.encodeWebp()` is **lossless**, and that is the default on purpose:
  VP8's transform is worst at flat colour and hard edges, which is exactly what
  a user interface is made of. `encodeWebp(quality)` is the lossy path, for a
  photograph. The tests say both halves — lossless round-trips a drawn picture
  exactly and beats its PNG; lossy at 80 is a long way off on random noise.
- `Image.decodeAnimation` reads an animated WebP the same way it reads a GIF,
  and the work is upstream's: `WebPAnimDecoderGetNext` hands back a **fully
  composited canvas**, so the disposal model ADR-0382 had to write in Java is
  libwebp's here.
- libwebp reports the moment a frame *stops* being shown; the binding differences
  those into durations, because a duration is what a caller can use.
- `WebPAnimDecoderOptionsInit` and `WebPAnimDecoderNew` are static inlines in
  `demux.h`, so what is exported is the `…Internal` pair they forward to, with
  the ABI version passed from Java and checked by the library — which is what the
  inline does.
- The **muxer stays off**: nothing here assembles an animation, and writing one
  is a different decision with a different API.
- `ImageEncodeException` is new, for one case: WebP holds 16383 pixels on a side
  at most, so a very tall screenshot is a refusal rather than a bug.

## Consequences

- `libgoldberry` grows by the encoder and the demuxer. That is the cost ADR-0329
  declined to pay for nothing; it is paid now for two features.
- An animated WebP no longer decodes to its first frame — it decodes to all of
  them, and a still one is still a still.
- **JPEG encoding is still not built**, and is now the only entry left on that
  line. Blend2D ships a JPEG *decoder* and no encoder, so writing one means a
  third codec library or a written one, and neither is worth it while a lossless
  WebP is a third of a PNG and reads everywhere.
- The animated fixture is three 4×4 frames at three different delays, written by
  a script rather than found: what is being asserted is the timing model, and a
  photograph of a cat would assert it no better.

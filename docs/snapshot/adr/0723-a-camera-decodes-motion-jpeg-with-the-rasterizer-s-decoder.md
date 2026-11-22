# ADR-0723: A camera decodes Motion JPEG with the rasterizer's decoder

- **Status:** Accepted, as phase 4b of `docs/camera-and-mic.md` (W3.4,
  Tessera's T-05). Takes D4 (d); amends ADR-0722's decision 4 and its MJPG
  consequence.
- **Date:** 2026-11-22
- **Relates to:** ADR-0719 (the plan's decisions, D4 (a) among them),
  ADR-0722 (the camera module, uncompressed modes only)

## Context

ADR-0719 took D4 (a), uncompressed modes only, because the plan said the tree
had no JPEG decoder; libjpeg-turbo, the other way (D4 (c)), was refused for
tying the camera to `:media`'s natives. Both reasons turned out wrong. The
rasterizer inside `libgoldberry` decodes JPEG, and `core.image` reaches it
through `Image.decodeInto`, which writes BGRA into the caller's memory. Phase 4
had already tied the camera to `:media` and its natives (D1, D2).

A USB 2 camera gives its large sizes and fast rates only as Motion JPEG. The
C922 here gives 1280×720 at 10 and 1920×1080 at 5 uncompressed, so a call at
720p30 opened 640×480. A probe on that camera decoded 90 frames a mode with
no failure: a median of 4.7 ms for a 1280×720 frame and 11.6 ms for a
1920×1080 one, on one thread into a reused buffer.

## Decision

1. **Every mode is openable.** `CameraSpec.choose` takes the mode nearest in
   the same distance as before (log size plus twice the rate shortfall),
   compressed modes included. A tie goes to an uncompressed mode, which needs
   no decoding, then to the larger, then to the rate nearer the one asked
   rather than the faster, so a call at 30 does not decode 60. On the C922,
   `CameraSpec.CALL` opens 1280×720 at 30 as Motion JPEG, and `PREVIEW` opens
   640×480 at 30 uncompressed.
2. **SDL hands MJPG through.** The backend asks SDL for `MJPG` in a compressed
   mode. SDL's drivers do not convert it, and they report the frame's byte
   count as its pitch, which is the extent `SdlFrameLayout` already gave an
   MJPG frame. No native code and no new symbol.
3. **The capture thread decodes.** A lent frame is sealed, `Nv12Frame` or
   `JpegFrame`. A JPEG frame goes from the driver's buffer straight into a
   three-slot `BgraPool` through `Image.decodeInto` and is shown as an opaque
   `VideoPicture`, so the borrowing rule holds as it does for planes. The pool
   names its next slot without taking it until the decode is done, so a frame
   that fails takes no slot. A frame of a new size makes a new pool.
4. **A frame that does not decode is dropped and counted.** The camera stays
   `Active`, the first failure is logged at warn and later ones at debug, and
   `Camera.droppedFrames()` reports the count.
5. **Frames without Huffman tables get the standard ones.** The UVC payload
   format lets a camera leave the DHT segment out, and the rasterizer refuses
   such a frame. A scan of the markers before the first scan (`JpegHeader`)
   finds whether a DHT segment is there. Where none is, the frame is copied
   into a reused buffer with JPEG Annex K.3's four tables inserted before its
   scan (`StandardHuffmanTables`).
6. **In an unexported package of its own**, `dev.goldberry.camera.mjpeg`. The
   mirror flips BGRA pictures as it flips NV12 ones, keeping their alpha.
7. **Tested against an independent encoder.** The fixtures were written by
   Pillow (libjpeg), not by the code under test, with
   `tools/media/mjpeg_fixtures.py`: a test card with its tables, the same
   bytes with them cut out, and a second size. The inserted tables equal
   libjpeg's byte for byte. A frame without tables decodes to the same pixels
   as the one with them, and the rasterizer alone refuses it. The decode path
   is tested through the fake backend, on `DeviceCamera`'s own capture
   thread, rather than through a JPEG mode on `SyntheticCamera` as the plan
   proposed: that would have tested a copy of the path.

## Consequences

- A call opens 720p30 on a USB 2 camera, at roughly a sixth of a core on the
  C922 (1080p30 costs a third). An application that wants no decoding asks
  for a size the camera sends uncompressed.
- A camera's pictures are NV12 planes in one mode and BGRA in another. Both
  are `Picture`s, so the views draw them the same way; a consumer that reads
  the bytes (a call's encoder) has to handle both.
- The decoder's speed is the rasterizer's. libjpeg-turbo would be faster, but
  it is not needed at these sizes.
- The pixels were checked against libjpeg's own decode only through the
  fixture's bar colours, within quantisation, not pixel by pixel.

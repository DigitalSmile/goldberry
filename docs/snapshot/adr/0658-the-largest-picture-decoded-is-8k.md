# ADR-0658: The largest picture decoded is 8K

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0656 (damaged media is fuzzed), ADR-0651 (the reference codecs bound directly)

## Context

ADR-0656 left one thing open. Neither built-in video decoder had a limit on
the picture it would allocate: dav1d's `frame_size_limit` was left at its
default, none, and libvpx was built without `--size-limit`. A damaged or
hostile header may state 65536 × 65536, which is six gigabytes for one 8-bit
picture before dav1d's reference frames, and an `OutOfMemoryError` or worse in
native code is not a `MediaException`. No mutation of the fuzz corpus reached
it, since a flat mutation rarely lands on a size field that still parses;
a crafted file reaches it at once.

The system decoders and an application's providers have their own limits, or
none; and a container's stated size is read before any decoder exists.

## Decision

**Nothing past 8K is decoded: no side longer than 8192, and no more pixels
than 8192 × 4352**, the largest picture the highest levels of AV1 (6.x) and
VP9 (6.x) define. `PictureLimit` in `dev.goldberry.media.codec` holds the two
numbers and is public, so an application's own decoder can keep them too. It
is held at each step a size passes:

- **dav1d** is opened with `frame_size_limit` at the pixel count, and refuses
  a frame header past it before it allocates (`DAV1D_ERR(ERANGE)`, reported as
  a frame past the largest picture). The sides are checked as a picture comes
  out.
- **libvpx** is built with `--size-limit=8192x8192`, and refuses a frame with a
  side past it before it allocates ("Dimensions of 8256x16 beyond allowed
  size"). The pixel count is checked as a picture comes out, before it is
  converted.
- **The player** refuses a video track whose container states a size past the
  cap as it opens, naming the track and the size; so does a video sticker.
- **Any picture**, from any provider, is checked by the video thread before it
  is queued.

Every refusal is `MediaError.InvalidData`, which fails the playback rather
than trying the next decoder: the file is at fault, not the decoder.

## Alternatives

- **16384 on a side**, which VP9 level 6 also allows. It leaves libvpx free
  to allocate 268 megapixels for one frame before Java sees it, and buys
  panoramas wider than 8192 that nobody has asked to play. Chosen: 8K.
- **An application-settable cap** (`MediaPlayer.builder().maxPictureSize`).
  Nobody has asked for 16K; it can be added without breaking anything when
  someone does.
- **Only the native limits.** They stop the allocation, but leave a
  provider's picture and a stated size unchecked, and dav1d's limit is a
  pixel count with no side.

## Consequences

- A conformant 8K stream plays, portrait too; anything larger fails with a
  message naming its size and the cap.
- The library is rebuilt for libvpx's limit; its ABI is unchanged, and an
  older library still has the Java checks.
- Four fixtures past the cap, one for each layer that refuses it, and the fuzz
  harness holds every decoded picture to the cap instead of 65536.

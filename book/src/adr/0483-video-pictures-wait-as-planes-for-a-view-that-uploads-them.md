# 483. Video pictures wait as planes for a view that uploads them, and every view says which form it draws

Date: 2026-09-25

## Status

Accepted. `docs/gpu-plan.md`'s D8 and the first item of phase 6 (media phase
4, GPU present). It refines
[ADR-0463](0463-video-is-converted-as-it-is-decoded-and-paced-by-the-picture.md),
whose consequences said the queue's slots are what GPU present changes, and it
uses [ADR-0469](0469-a-video-track-is-switched-over-the-same-frame-queue.md)'s
seek.

## Context

CPU present converts every kept picture to premultiplied BGRA on the video
thread (ADR-0463). GPU present uploads the Y'CbCr planes and converts them in
`yuv2.frag` or `yuv3.frag` (ADR-0477), so a view that uploads planes has no use
for BGRA. The conversion also costs the most. Measured on an M1 Pro with
`PlaneCopyBenchmark`, one 3840×2160 picture, median of 30:

| Layout | Bytes | Planes copied | Row by row (padded rows) | Converted to BGRA (`SWS_BITEXACT`) |
|--------|------:|--------------:|-------------------------:|-----------------------------------:|
| NV12 | 12.4 MB | 0.35 ms | 0.46 ms | 12.22 ms |
| I420 | 12.4 MB | 0.34 ms | 0.43 ms | 12.00 ms |
| P010 | 24.9 MB | 0.80 ms | 1.46 ms | 13.60 ms |
| I010 | 24.9 MB | 0.80 ms | 1.28 ms | 12.78 ms |

At 60 fps a picture has 16.7 ms. The conversion alone takes three quarters of
that, on the thread that also decodes. The copy takes a twentieth.

D8 left three things open:

- **What a picture is**, once there are two forms.
- **Who chooses the form.** A player can be shown by more than one view, and a
  view that draws on the CPU cannot draw planes.
- **What the change costs.** D8 said "a flush plus an accurate reseek", in both
  directions.

## Decision

**A picture is one of two forms.** `Picture` is a sealed interface over
`VideoPicture`, which is BGRA and unchanged, and `VideoPlanes`, which is new.
`PictureForm` names the two forms, `CONVERTED` and `PLANES`.

`VideoPlanes` holds:

- the frame contract's layout (`NV12`, `I420`, `P010` or `I010`);
- the size;
- one read-only, little-endian plane per layout plane, with its stride;
- the matrix and the range;
- the time.

Its bytes are the decoder's, copied and not changed, so a shader's output can be
compared with swscale's from the same input. It is borrowed under
`VideoPicture`'s rule: a picture handed out keeps its bytes until two more have
been handed out.

**The queue's slots have a shape.** `FrameQueue.Shape` is the size plus either
"BGRA" or a plane layout. A slot is one direct buffer, and each plane starts at
its own offset with 64-byte-aligned rows. When a free slot has the wrong shape,
the collector takes it, just as it already did when a stream changed size. The
pool is still seven slots. A 4K P010 slot is 25 MB, where a BGRA slot is 33 MB.

**The video thread reads the form for each picture.** For `CONVERTED` it runs
swscale, as before. For `PLANES` it copies each plane: one copy when the strides
match, otherwise one copy per row. The late-picture drop and accurate seeks work
the same way in both forms.

**Views attach, and the player decides.**

- `MediaPlayer.attachView(PictureForm)` returns an `Attachment`. The attachment
  can change its form (`setForm`) and closes idempotently.
- The pictures are `PLANES` only while at least one view is attached and every
  attached view asks for planes. With no view attached, or with any `CONVERTED`
  view, they are `CONVERTED`.
- `MediaPlayer.pictureForm()` reports the form in use, and it carries over to
  the next source opened.
- `video-view` and `media-player` attach as `CONVERTED` while they show
  pictures, from `FollowingState`. A view that uploads planes therefore cannot
  leave a CPU view on the same player with nothing to draw.
- `MediaPlayer.shownPicture()` hands out whichever form is shown.
- `currentPicture()` still hands out BGRA only. It is empty while the picture
  shown is planes.

**The two directions of a change differ.** This corrects D8.

- **To planes: nothing else happens.** The converted pictures already queued
  play out, because a view that asks for planes must also draw a
  `VideoPicture` (`PictureForm.PLANES` says so). No seek and no flush means no
  gap in the sound when a GPU view attaches in the middle of playback.
- **Back to converted: an accurate seek to the position**
  (`Playback.setPictureForm`). This is the track switch's seek. It flushes the
  planes, which a CPU view cannot draw, and the picture that covers the
  position comes back converted.

## Alternatives considered

- **A flush and a reseek in both directions**, as D8 wrote it. Going to planes
  would then cost a sound flush and a decode from the keyframe, to replace
  pictures the new view can already draw.
- **Converting the queued planes in place** when the form goes back to
  converted, on the video thread. This would avoid the seek, but it needs a
  path for the paused and ended thread, and a swap of slots under the queue's
  lock while the UI thread presents. The case it serves is a GPU view detaching
  while a CPU view stays, or a device lost (phase 7), and neither needs to be
  seamless.
- **A form set on the player by the application**, not by views. A player
  shown by two views, or by a view whose window has no GPU, would then be the
  application's problem to get right.
- **`currentPicture()` returning `Optional<Picture>`.** That changes a
  signature every CPU caller uses, and gains them nothing.
- **The UI thread copying planes out of the decoder's frame.** Frames are
  borrowed until the decoder's next call (ADR-0463), so the copy must happen
  where the decode happens.

## Consequences

- **The video thread's cost for 4K drops from 12–14 ms a picture to 0.3–0.8
  ms** while every view draws planes. This is the headroom phase 6's 4K60
  measurement needs.
- **D8's other half is still open.** The UI thread uploads each shown picture's
  planes through staging memory, 25 MB a picture at 4K P010, which is 1.5 GB/s
  at 60 fps. It is measured when `video-view` becomes a GPU layer (phase 6's
  next item). If it is too slow, the slots become mapped transfer buffers
  written by the video thread.
- **Going back to converted costs a seek.** The sound is flushed. The picture
  shown stays up until the covering one replaces it, but a CPU view draws its
  background until then, because the picture it holds is in planes. A player
  at its end plays its last picture again and ends again.
- **Parity is testable without a GPU.** `VideoPlaybackTest` converts the planes
  handed out with swscale and compares them byte for byte with the CPU goldens,
  including the 10-bit I010 golden. The GPU parity tests (phase 6) compare their
  shader against those same planes.
- **Hardware decoders' copy-back is copied a second time**, from the
  copied-back frame into the slot. Zero-copy (D9, phase 6b) is where that goes
  away.

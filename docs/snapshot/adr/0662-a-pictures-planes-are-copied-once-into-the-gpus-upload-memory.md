# ADR-0662: A picture's planes are copied once, into the GPU's upload memory

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0483 and ADR-0484 (GPU present), ADR-0651 (the reference codecs bound directly)

## Context

Phase 4 of `ffmpeg-out.md` asked for zero-copy import of the system decoders'
surfaces into SDL_GPU. SDL 3.4.18's GPU API imports no outside texture on any
backend; upstream's PR #14157 adds it for Direct3D 12 and Metal, is open and
contested, and has no Vulkan side. The user chose on 2026-10-25 to carry no
SDL patch, and to cut a copy instead.

A picture shown on the GPU was copied twice on the CPU: the video thread
copied the decoder's planes into a frame queue slot (the decoder's memory is
only lent until its next call), and the UI thread copied the slot into SDL's
staging memory as it recorded the upload. The second copy is on the UI
thread's frame budget: 2.6 ms of every frame for 4K NV12 here, 5.1 ms for
P010 (`:gpu:videoLayerProbe`, NVIDIA, median).

SDL's GPU calls belong to the device's thread, and `SdlGpuDevice`'s resource
book-keeping is not thread-safe; but the bytes of a mapped transfer buffer
are only memory, and a shared FFM arena makes a write that races an unmap
fail in Java rather than reach freed memory.

## Decision

**The video thread copies a picture's planes straight into the view's upload
memory, and the UI thread records the upload with no copy.** Every SDL call
stays on the device's thread.

- `:gpu`'s public API gains `UploadBuffer` (`GpuDevice.createUploadBuffer`),
  upload memory a producer on another thread fills while it is mapped, and
  `CopyPass.upload(GpuTexture, UploadBuffer, offset, rowBytes)`, which records
  from it with rows padded (SDL's Direct3D 12 backend realigns unpadded rows
  with a CPU copy of its own; rows are padded to 256 bytes, planes to 512).
  On Metal the rows are packed instead: SDL's Metal backend reads every
  upload's rows as packed whatever `pixels_per_row` says (found on the first
  macOS run, 2026-10-25), so `GpuDevice.uploadsPaddedRows()` answers false
  there, the staging packs its rows, and a padded upload through the API is
  recorded a row at a time.
- `VideoLayer.staging()` is a `PlaneStaging`: a pool of mapped buffers per
  picture shape, `obtain` from any thread without waiting. A shape asked for is
  remembered, and the layer makes eight buffers of it on its next render; a
  `StagedPlanes` released comes back retired and is mapped again with SDL's
  cycling on the next render before it is handed out, so the next picture is
  never written where the GPU still reads the last. `VideoImage.Staged` is
  uploaded by unmapping for the instant the pass is recorded, and mapping the
  same memory again, so its holder still reads it.
- `:media`'s SPI gains `PlaneStore` and `PlaneBuffer` (`media.picture`), and
  `MediaPlayer.Attachment.setPlaneStore`. The player uses a view's store while
  it is the one view and draws planes; the frame queue's slots copy into a
  buffer the store gives, keep their own memory (made only when first needed)
  when it gives none, and give the buffer back whenever the slot goes back to
  the pool. `VideoPlanes.staged()` names a staged picture. `video-view`'s GPU
  presenter offers its layer's staging; `PictureRenderer.planeStore()` offers
  a renderer's to an application drawing a player's pictures itself.

## Alternatives

- **Zero-copy import, patching SDL** (the plan's phase 4). Declined by the
  user: a patch on three backends, two of which only CI could run, against an
  API upstream has not settled.
- **Mapping and unmapping on the video thread.** SDL may allow it, but
  `SdlGpuDevice` tracks resources in a plain set, and a device closed by the
  UI thread while the video thread is in `SDL_MapGPUTransferBuffer` would race
  in native code. Rejected for the pool mapped and cycled on the device's
  thread.
- **Lending the decoder's own buffers to the view** (a `GstSample` or
  `CVPixelBuffer` held until the picture is gone). Removes the video thread's
  copy rather than the UI thread's; GStreamer's pools are small enough that
  holding seven pictures stalls the decoder, and the built-in decoders reuse
  theirs on the next call.

## Consequences

- Showing a new 4K picture costs the UI thread 0.5 ms for NV12, I420 and
  P010 alike, from 2.6, 3.2 and 5.1 ms (`:gpu:videoLayerProbe`, NVIDIA).
- The first picture of a shape is copied as before: the staging has nothing
  until the layer's next render. A second view on the same player, or a
  device lost, falls back to the player's own memory; a staged picture whose
  device went before it was drawn is not drawn, and the last one stays up.
- A staged `VideoPlanes` read on another thread at the instant the layer
  records its upload, or after its device is gone, throws
  `IllegalStateException`; on the UI thread, and through the "two newer
  pictures" rule, it reads as any other.
- Zero-copy import stays open, upstream's to make possible.

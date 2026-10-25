# Staged planes: the guide's pieces

Parked until the release, as `docs/snapshot/README.md` says. Two pieces; the
record is ADR-0662. They say what is, and cite no record.

## `book/src/components/media.md`, `### video-view`, after the paragraph on GPU present

> On the GPU, a picture's planes are copied once: the video thread writes
> them straight into the view's upload memory, and the window's thread
> uploads them as they are, so a 4K picture costs the UI thread half a
> millisecond. This happens by itself for `video-view`. An application that
> draws a player's pictures with `PictureRenderer` gets the same by offering
> the renderer's memory: `player.attachView(PictureForm.PLANES)` and then
> `setPlaneStore(renderer.planeStore())` on what it returns.

## `book/src/components/gpu.md`, `### Uploads`, at its end

> **Upload memory written elsewhere.** `GpuDevice.createUploadBuffer` makes
> memory a producer on another thread fills while it is mapped, and
> `CopyPass.upload(texture, buffer, offset, rowBytes)` records the upload
> from it with no copy. Mapping, unmapping and uploading are the device's
> thread's; only the bytes may be written elsewhere, and unmapping ends the
> mapping for every thread. Rows padded to 256 bytes upload in one copy where
> `GpuDevice.uploadsPaddedRows()` is true, Vulkan and Direct3D 12; Metal reads
> an upload's rows as packed, so there padded rows are recorded one at a time
> and packed rows are the ones to stage.

# ADR-0722: A camera is a picture source, copied out of the driver

- **Status:** Accepted, as phase 4 of `docs/camera-and-mic.md` (W3.4,
  Tessera's T-05). Builds ADR-0719's D1, D2, D4, D5, D6 and D7.
- **Date:** 2026-11-22
- **Relates to:** ADR-0719 (the plan's decisions and the SDL surface),
  ADR-0720 (the microphone's module, the same shape), ADR-0698 (pictures an
  application pushes), ADR-0190 (content modules)

## Context

ADR-0719 put SDL's cameras and device events in `:natives`, exported to
`dev.goldberry.camera`, and took D1 (a camera is a `PictureSource` of
`:media`'s), D2 (planes are converted on the CPU where no GPU shows them), D4
(uncompressed modes only), D5 (frames copied out at once) and D7 (the first
front-facing camera by default). What was left was the module's shape, how
modes are chosen, where the copy goes, how permission is followed without a
window loop, what `camera-view` shows besides a picture, and where the mirror
lives.

## Decision

1. **`:media` draws planes on the CPU (D2).** `PictureView` hands its surface
   a `PlanesOnCpu`, which converts a `VideoPlanes` picture to BGRA with the
   media natives' libyuv only when the GPU did not place it, once a picture,
   into one reused buffer. A player's views are unchanged: they already ask
   the player for converted pictures. Without the natives the view draws
   nothing, as before, and logs once.
2. **`goldberry-camera` is an optional module with no natives of its own**,
   requiring `goldberry-media` (`api`; `:gpu` compile-only, as `:media` takes
   it). Packages: `dev.goldberry.camera` (`Cameras`, `Camera`, `CameraInfo`,
   `CameraFormat`, `CameraSpec`, `CameraPosition`, `CameraStatus`,
   `CameraChange`, `CameraException`), `…camera.synthetic`, `…camera.view`, and
   `…camera.backend`, not exported.
3. **`Camera` is a sealed interface** over a device camera (package-private)
   and `SyntheticCamera`: `info`, `format`, `status`, `onStatus`, and the
   picture source. A synthetic camera paints a test card into NV12 as a
   function of the picture number when advanced; its status is the test's.
4. **A mode is chosen, not asked for (D4).** `CameraSpec.choose` takes the
   uncompressed mode nearest in natural-log distance of size plus twice the
   shortfall in rate, the larger of a tie. SDL is asked for NV12 in that mode,
   in the mode's own colorspace, so its camera thread converts YUY2 without a
   colour change; the colorspace's matrix and range travel with the picture.
5. **A capture thread per camera copies (D5)** each lent frame into a
   `PlanePool` of three NV12 slots and gives it back at once, so a picture
   keeps its bytes for two more, the borrowing rule. The thread also polls the
   permission while it is pending (every 20 ms), so `Asking` becomes `Active`
   or `Denied` with or without a window loop; the events only wake it (D6).
   Status is sealed: `Asking`, `Active`, and final `Denied`, `Lost`, `Closed`.
6. **`camera-view` is a stateful `@Markup` widget** over a `CameraBox` node:
   while active, a `PictureView` of the camera; otherwise a message from
   six new toolkit labels. Its state is a class on the node; `camera=` naming
   nothing shows "No camera".
7. **The mirror is the view's.** `Mirror.AUTO` mirrors unless the camera faces
   away (a desktop camera says nothing and faces the user). A mirrored view
   draws a `MirroredPictures` wrapper that flips each NV12 picture into a pool
   of its own once, when first drawn; the camera's pictures, which a call
   sends, are untouched. CSS `transform` was not used: a GPU layer is placed
   by its transformed bounding box, so it would not flip.

## Consequences

- A call tile of pushed planes and a camera both draw in a CPU-presented
  window and in every offscreen golden; the goldens need the media natives.
- MJPG modes wait for a JPEG decoder; on a USB 2 camera that caps an
  uncompressed call at 640×480 at 30 or 1280×720 at 10.
- Mirroring costs a copy per picture on the UI thread for a mirrored view
  only.
- `camera.css` styles the picture with a descendant selector: the
  `picture-view` element between `camera-view` and `video-view` keeps a child
  selector from matching.
- The guide chapter is parked; its `kdl` samples need `:camera` on
  `:example`'s path before they move into the book.

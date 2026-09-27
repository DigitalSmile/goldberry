# 484. `video-view` shows its pictures through a GPU layer when `:gpu` is present

Date: 2026-09-25

## Status

Accepted. `docs/gpu-plan.md`'s phase 6 (media phase 4, GPU present): the
video layer, the parity tests and the first rung of the fallback ladder. It
builds on ADR-0481's GPU layers and ADR-0483's frame queue of planes. It
corrects [ADR-0477](0477-the-gpu-composites-with-sdl-gpu-directly.md)'s
chroma siting.

## Context

ADR-0483 made the frame queue hold a picture's planes while every view
attached to the player draws planes. Nothing drew planes yet. The plan's
phase 6 left five things open:

- **Where the layer lives.** The Y'CbCr shaders and their arithmetic are in
  `:gpu`'s unexported `render` package. `video-view` is in `:media`. `:gpu`
  must not depend on `:media`, and `:media` must play video without `:gpu`.
- **Who letterboxes.** `video-view` places a picture by `Fit` over a box of the
  stylesheet's size. A GPU layer is an opaque rectangle.
- **When a view asks for planes.** Only a paint knows whether its frame can show
  a GPU layer (`Frame.gpuLayer` returns false without one), and a window can
  lose the ability to show layers while a view is shown.
- **What parity means.** Parity is "within tolerance of swscale's
  `SWS_BITEXACT` picture", and nothing had measured the gap.
- **The upload's cost to the UI thread**, D8's second half.

## Decision

### The layer is `:gpu`'s, exported to `:media` alone

`io.github.digitalsmile.goldberry.gpu.video` contains:

- `VideoLayer`, a `GpuLayer`;
- `VideoImage`, sealed over `Planes` and `Bgra`;
- `PlaneLayout`, the four layouts of the frame contract;
- `ColorMatrix`.

The package's vocabulary is its own, because `:gpu` does not know `:media`.
`:gpu` exports it with `exports … to io.github.digitalsmile.goldberry.media`
under `@SuppressWarnings("module")`, as `:natives` exports to its readers.
`:media` `requires static` `:gpu` and has it `compileOnly`.

**The layer shows one image, stretched over its whole box.**
`show(image, source)` takes the picture and the part of it `Fit` keeps. The
caller letterboxes by where it places the layer: over `Fit`'s rectangle only.
The rest of the box is the stylesheet's background, painted on the CPU as
before.

How the layer draws each form:

- **Planes:** each plane is uploaded into a texture of its own (`R8`, `R8G8`,
  `R16`, `R16G16`), then `yuv2.frag` or `yuv3.frag` draws them with
  `YuvConversion`'s uniforms, sampled linearly.
- **BGRA:** uploaded into one texture and drawn by `texture.frag`. This form
  covers the converted pictures still queued after a switch to planes, and the
  case where another view keeps the player converted.

The shaders are the toolkit's own, loaded through the public
`ShaderCode.load`. **Images are compared by identity.** A picture is uploaded
once, on the frame it is first shown. A frame repainted for something beside
the video renders nothing new.

### `:media` finds `:gpu` once, and names its types in one class

`GpuVideo` probes once:

- it looks up `gpu.video.VideoLayer` with `Class.forName(…, false, …)`;
- it then checks that `:media`'s module reads that class's module.

`GpuVideoPresenter` is the only `:media` class that names `:gpu` types, and it
is loaded only when the probe says yes. It maps a `VideoPicture` or
`VideoPlanes` to one `VideoImage` per picture. The view sees it through
`VideoPresenter`, an interface written in `:media`'s own types.

### A view asks for planes when its paint placed the layer

`FollowingState`, the state behind `video-view` and `media-player`:

- attaches to the player as `CONVERTED`;
- holds a presenter when `:gpu` is here;
- when the presenter reports that a paint placed the picture on the GPU,
  changes the attachment to `PLANES`;
- when a paint could not place it, changes it back to `CONVERTED`.

The presenter reports only changes.

This makes the fallback ladder's first rung a matter of each paint:

- **A frame that cannot show a layer** (no GPU, `goldberry.gpu=off`, a group):
  the picture is drawn on the CPU, and the view asks for converted pictures.
  ADR-0483's seek brings those back.
- **A view that has not been painted yet** asks for nothing it may not be able
  to draw.
- **Without `:gpu`** nothing changes from phase 3's behaviour.

### Chroma is centred, as swscale centres it

The first parity run was 113 levels from swscale at worst, at 27 dB. The GPU
matched its own Java reference exactly, so the gap had to be a difference in
what was computed, not a fault in how.

CPU present converts with `SWS_BILINEAR | SWS_FULL_CHR_H_INT`. Fitting
references with several chroma positions to swscale's picture found that
swscale reads chroma **centred** horizontally. The reference is within one
level of swscale's picture, with no pixel over two. ADR-0477 had sited chroma
left, "as swscale assumes", a quarter of a chroma texel off.

The fix:

- `YuvConversion.uniforms` now sites chroma centred (`Siting.CENTRED`, an
  offset of 0).
- `Siting.LEFT` remains for the day a frame carries its own chroma location.
- The shader is unchanged; its comment is rewritten, and the committed hashes
  with it.

**Parity is then pixel by pixel.** Every fixture with a golden is drawn through
the layer, composited and read back, and compared with CPU present's golden.
That is VP8, VP9 and AV1, 8-bit and 10-bit, BT.601, 709 and 2020, limited and
full range. The bound is **2 levels**; every fixture measures **1**. Three
tagged fixtures were added for the matrices and range the old ones lacked:
`clip-vp9-709`, `clip-vp9-2020-10bit` and `clip-vp9-full`. The layer is also
held to `PlanesReference`, a Java model of the same sampling and conversion:
0 levels.

## Alternatives considered

- **A public video API in `:gpu`.** Its only caller is `video-view`, and an
  application shows video with that. A public API would have one caller and an
  obligation to keep it (ADR-0019).
- **`:gpu` providing a service that `:media` finds.** The service interface
  would have to live in `:media`, which `:gpu` cannot depend on, or in `:core`,
  which knows nothing of video.
- **A layer over the whole box, letterboxed by the shader.** The layer would
  have to clear to the box's background. That colour is the stylesheet's, and
  the CPU already paints it.
- **Point-sampled chroma, to match a replicating conversion.** swscale does not
  replicate. The measurement showed that, and linear sampling is also what
  scales the picture.
- **A statistical bound, such as PSNR, against swscale.** The first run
  suggested one. The siting fix removed the reason for it.

## Consequences

- **GPU present works through `video-view`.** It is composited or read back
  like any layer, byte-exact for BGRA and within one level for planes. It falls
  back to CPU present where a frame has no GPU.
- **Two new test runs in `:media`:**
  - `gpuTest`: decoded pictures on a real device, run on the first thread;
  - `testWithoutGpu`: the whole suite with `:gpu` off the class path, as an
    application without it runs.

  Both are part of `check`.
- **D8's second half, measured** by `:gpu:videoLayerProbe` on an M1 Pro,
  median per 4K picture. The UI thread copies each new picture into staging
  memory in 2.5 ms (NV12), 2.9 ms (I420) or 5.2 ms (P010). That is about 5 GB/s,
  because SDL's Metal backend makes upload buffers write-combined. Submitting
  costs 0.1 ms. At 4K60 that is up to a third of a frame on the UI thread. D8's
  second step, mapped transfer buffers written by the video thread, would move
  that cost off the UI thread. Whether it is needed is decided by phase 6's 4K60
  run.
- **A device that fails mid-render** shows black where the layer is: the
  compositor catches the throw (ADR-0481). The view does not learn of it, so it
  does not fall back. Device loss is phase 7's.
- **A native image finds `:gpu` through a registration.** The probe class is
  declared for reflection in `:media`'s metadata. The showcase's image with the
  GPU tab is phase 7's.
- **Chroma location is not carried.** Both paths site chroma centred, whatever
  the stream says. MPEG-2, H.264 and HEVC content sited left is a quarter of a
  chroma texel off on both, identically. Carrying `AVFrame.chroma_location`
  through the frame contract would fix both paths together.

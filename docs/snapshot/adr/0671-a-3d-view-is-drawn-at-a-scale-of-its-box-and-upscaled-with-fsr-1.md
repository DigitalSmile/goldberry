# ADR-0671: A 3D view is drawn at a scale of its box, and upscaled with FSR 1

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0482 (`canvas3d` is a GPU layer an application renders into), ADR-0477 (the GPU's arithmetic is held to a Java reference), ADR-0670 (a window presents in the mode asked for), ADR-0560 (no clock bounds in tests)

## Context

`docs/gpu-gaming-plan.md`'s phase 5 asks for the setting a game's options
screen calls "render scale": draw fewer pixels than the window has and bring
them up to its size, buying frame rate for sharpness. The plan names two
upscalers: bilinear, and AMD FidelityFX Super Resolution 1 (FSR 1), whose
two passes are EASU (Edge Adaptive Spatial Upsampling) and RCAS (Robust
Contrast Adaptive Sharpening). FSR 1 is MIT-licensed, spatial, and needs nothing
from the renderer: no motion vectors and no depth.

What was there:

- `Canvas3dLayer` gave its renderer the compositor's target, at the box's size
  in physical pixels, and nothing else. A renderer could draw small itself, but
  had no upscaler to bring the picture up, and the layer resized it only when
  the box changed.
- `GpuFrame` had render passes with any of the toolkit's built-in shaders
  (`quad.vert`, `texture.frag`), and `ComputePass.bindSamplers` since phase 1.
  The plan said "EASU and RCAS as compute shaders".

## Decision

**`canvas3d` has a `RenderScale`.** It is a record of a factor (0.5 to 2), an
`UpscaleFilter` (`LINEAR` or `FSR1`) and a sharpness (0 to 1). In markup it is
`render-scale=`, `upscale=` and `sharpness=`. `Canvas3d` gains the component,
and its five-argument constructor stays. At a factor other than 1 the layer
gives the renderer a colour texture of its own at the scaled size, rounded and
at least a pixel, and a depth texture of the same size. The renderer is told
that size by `resize`, and the layer then upscales into the compositor's
target. At 1 the renderer draws straight into the target, as before, and
nothing else runs. A new scale changes the painter, so the box is damaged and an
on-demand canvas is drawn again; the renderer keeps its resources.

**The passes are an `Upscaler`, public in a new exported package
`dev.goldberry.gpu.upscale`.** A renderer that draws into textures of its own
can upscale one the same way the canvas does. Which passes run is a pure
function of the filter, the sharpness and the two sizes, tested without a
device:

- `LINEAR`: one bilinear pass, sampling from texel centres.
- `FSR1` smaller on an axis and larger on neither: EASU, then RCAS unless the
  sharpness is 0.
- `FSR1` at the same size: RCAS alone, or a copy at no sharpness.
- `FSR1` larger on either axis: bilinear, because EASU is made to upscale.
  A factor of 2 sampled bilinearly from texel centres is an exact box filter.

**EASU and RCAS are fragment shaders, not compute.** The layer's target is
`B8G8R8A8_UNORM`, which SDL_GPU cannot bind as a storage texture on every
driver. Compute would need an RGBA storage texture and a copy into the target
every frame. A quad over the target (`quad.vert`) with `SV_Position` as the
pixel does the same work in the passes `GpuFrame` already has. EASU draws into
a texture of the upscaler's own at the target's size, and RCAS draws from it
into the target. The upscaler keeps that texture while the size and format
stay.

**The shaders are a port of `ffx_fsr1.h` in 32-bit floats**, checked line by
line against upstream at `a21ffb8f`: AMD's constants (`Fsr1.easu`, `Fsr1.rcas`,
in Java), its four gathers, its twelve taps and its bit-trick reciprocals. They
are `easu.frag.hlsl` and `rcas.frag.hlsl`, sharing `fsr.hlsli`. **One change:**
RCAS's two limiters divide by the ring's extremes, which are 0 on a ring all
black or 1 on one all white. AMD's divide then makes 0 times infinity, and the
resulting NaN is resolved differently by different GPUs' `max`. The divisors
are kept off zero. The numerator is zero exactly when they would be, so the
limiter gives the 0 it means. RCAS's optional denoise is left out.

**Sharpness is RCAS's lobe scale, 0 to 1.** FSR calls the same thing
`exp2(-stops)`: 1 is its sharpest, and each halving is a stop less. The
default, 0.87, is AMD's recommended 0.2 stops. 0 skips RCAS.

**The shaders are held to a Java reference** (`Fsr1Reference`, in the tests),
as the Y'CbCr shaders are held to `YuvConversion` (ADR-0477). It is a
transliteration with the same approximations, the same constants and the same
order of summation. The GPU tests read the passes back and compare them with
it, channel by channel:

| | lavapipe | GTX 1660 Ti |
|---|---|---|
| EASU alone | exact | 1 level |
| RCAS alone | exact | 1 level |
| EASU then RCAS | exact | 3 levels |
| bilinear | 1 level | 1 level |

A single pass is allowed 2 levels: the GPU's rounding, its fixed-point
bilinear weights and a fused multiply-add. EASU then RCAS is allowed 4.
RCAS sharpens what EASU gave it, so a pixel a level off going in comes out
`1 / (1 - 4 * 0.1875 * sharpness)` levels off: 2.9 at the default, before its
own rounding.

**Goldens at 0.5 and 0.75 (FSR 1) and 0.5 (bilinear), blessed on lavapipe**,
beside the existing 1.0 one blessed on Metal. Their tolerance is written beside
the test: 4 levels anywhere, and 0.3% of pixels further. That is `Tolerance.GPU`'s
stray share times the three or so box pixels a flipped silhouette pixel is
upscaled into. The 1660 Ti is 3 levels from lavapipe on the FSR goldens and
1 on the bilinear one, with no pixel further than 4.

**FSR 1's licence** is in `licenses/fidelityfx-fsr.txt`, verbatim from
upstream, with a row in `THIRD-PARTY-NOTICES.md` and a line in `NOTICE`. The
shipped bytecode is a port of AMD's arithmetic.

## Consequences

- A game's settings can offer a render-scale slider and FSR 1's quality modes
  (ultra quality 0.77, quality 0.67, balanced 0.59, performance 0.5) on every
  SDL_GPU driver, through `canvas3d render-scale=` or `Upscaler` directly.
- An upscaled canvas costs one or two extra full-box passes and one or two
  textures: the scaled colour target, and the EASU texture for FSR 1.
- FSR 1 expects perceptual values. A `B8G8R8A8_UNORM` target holds them, as a
  canvas's does. A renderer that upscales a linear-light or sRGB-format texture
  of its own gets softer results than AMD intends.
- RCAS's medium-precision reciprocal underestimates by up to 0.32%, so a flat
  area may come out a level darker. That is FSR's own behaviour, and the
  reference has it too.
- The new shaders' DXIL was compiled on Linux with DXC 1.9.2609, which signs
  it. The unchanged shipped DXIL was restored from the macOS build, and the
  three new hashes were added to `shaders.properties` by hand.
- Not done: a mip bias for the renderer's samplers at a reduced scale, which
  AMD recommends, `log2(factor)` below zero. `Canvas3dTarget` does not carry the
  scale; a renderer that wants the bias knows the scale it set. Temporal
  upscalers (FSR 2/3, DLSS, XeSS) stay out of the plan, as it says.

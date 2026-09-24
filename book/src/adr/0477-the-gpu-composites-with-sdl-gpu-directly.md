# 477. The GPU composites with SDL_GPU directly, and converts Y'CbCr itself

Date: 2026-09-24

## Status

Accepted. D1 of `docs/gpu-plan.md`, taken on phase 0's measurements. The
colour half of media phase 4 (GPU present) is built here.

## Context

The composition pass could be written against `SDL_GPU` itself, or against
SDL 3.4's `SDL_CreateGPURenderer`. The renderer is an `SDL_Renderer` on an
`SDL_GPU` device. Its textures take NV12 and P010 with a colorspace, and
`SDL_GPURenderState` gives it custom fragment shaders. The plan leaned to
`SDL_GPU`, and set a condition for the renderer: choose it only if it is clearly
smaller at equal fidelity.

Phases 0 to 2 built the direct path far enough to measure it
(ADR-0475, ADR-0476):

- **Fidelity.** Two shaders, `yuv2.frag` (NV12, P010) and `yuv3.frag` (I420,
  I010), with one uniform block. The block holds the bit-depth scale, the range
  offset and gain, the matrix coefficients, and a left-siting offset for chroma.
  Against a Java reference (`YuvConversion`), every layout × matrix
  (BT.601/709/2020) × range × eight colours read back **exactly**: worst
  difference 0 in 255, on Metal. Chroma stays on its own side of an edge.
- **Cost** (`:gpu:gpuVideoProbe`, M1 Pro, 2560×1600 window, 120 Hz). Each frame
  uploads a new 3840×2160 picture, converts it into a letterboxed quad, uploads
  a caret's worth of UI damage, and draws the UI over it:

  | Layer | CPU per frame (copy + record + submit) | frame interval, median / p95 |
  |---|---|---|
  | 4K NV12 (12 MB) | 0.94 ms | 8.32 / 8.93 ms |
  | 4K P010 (24 MB) | 1.37 ms | 8.31 / 8.95 ms |

  The display's rate held with a 4K picture every frame, which is twice what
  4K60 needs.

## Decision

**Composite with `SDL_GPU` directly, and convert Y'CbCr in the toolkit's own
shaders.**

The renderer fails the plan's condition on fidelity before cost is reached. It
has no I010, the 10-bit format dav1d and VP9 profile 2 decode to, so that
format would need a conversion of its own before upload, the step the GPU path
exists to remove. Its colour pipeline is also SDL's, and can change with an
SDL release. The direct path's is ours, and a test holds it to a reference.

The direct path keeps what D1 listed: frames in flight, cycling transfer
buffers, readback through the same device, and one abstraction for the UI,
`canvas3d`, video and the goldens. The composition itself is two draws.

## Alternatives considered

- **`SDL_CreateGPURenderer`.** Rejected, as above. It was not built to be
  measured: at unequal fidelity its cost does not decide anything, and binding
  the renderer's API only to compare would have been work thrown away.
- **Converting on the CPU and uploading BGRA.** What CPU present does today
  (ADR-0463), with swscale on the decode thread. A 4K BGRA frame is 33 MB to
  upload against NV12's 12 MB, and swscale's conversion costs far more than the
  shader's. Kept as the fallback ladder's last rung, not as the GPU path.

## Consequences

- `YuvLayout`, `YuvMatrix` and `YuvConversion` are in `…gpu.render` for phase 6
  to use. `BuiltInShader` has `YUV2_FRAGMENT` and `YUV3_FRAGMENT`. The shared
  `yuv.hlsli` is hashed into the shader manifest like a source.
- D8's arithmetic holds on this machine: a 4K P010 picture copies into a
  transfer buffer in about a millisecond on the UI thread. Transfer buffers
  written by the decode thread are not needed here.
- Parity with swscale's `SWS_BITEXACT` output (`goldberry-media.md` §8) is
  still phase 6's to show, with real pictures. What is shown here is parity with
  the arithmetic swscale implements, for flat colour and one chroma edge.
- Lavapipe waits for the GPU lane; D3D12 for a Windows host.

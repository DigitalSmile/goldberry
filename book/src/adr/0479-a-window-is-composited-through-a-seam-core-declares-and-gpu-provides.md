# 479. A window is composited through a seam `:core` declares and `:gpu` provides

Date: 2026-09-24

## Status

Accepted. `docs/gpu-plan.md`'s phase 3, and D3 on macOS. It corrects D5.

## Context

Phase 3 makes a window present through the GPU. The window's painted frame
is uploaded, damage only, as a texture, then composited onto the window's
swapchain. Phase 0 measured the switch on macOS: it is reliable and cheap, and
compositing costs less CPU than the window surface (`docs/gpu-plan.md` §4.1).

The two halves live in different modules:

- **`:core`'s half:** the window, the frame loop, pacing, and when to switch.
- **`:gpu`'s half:** the device, the shaders and the draws.

`:gpu` requires `:core`, so `:core` cannot call into `:gpu`. D5 sketched
`GpuLayer.render(GpuFrame …)` in `:core`'s SPI, which cannot compile for the
same reason: `GpuFrame` is a `:gpu` type (ADR-0478).

Nothing needs composition yet either. GPU layers are phase 4. Until then, a
composited window is one that is asked to be.

## Decision

**`:core` declares a seam, `render.composite`, exported to `:gpu` alone.
`:gpu` provides it, and the sdl3 backend finds it with `ServiceLoader`.**

- **The seam.** It has three types:
  - `Compositor`, one per backend: `claim(SdlWindowHandle)` returns a window or
    empty, `unavailable()` says why when empty, and `close()`;
  - `CompositedWindow`: `present(PixelBuffer, damage)`, `lastPresent()` and
    `close()`;
  - `PresentTimings`: the upload, acquire and submit times, the bytes uploaded,
    and whether anything was shown.

  The signatures name `:natives`' window handle, so the export is qualified.
  `:core` `uses` the service and `:gpu` `provides` it (`SdlCompositor`). With
  no `:gpu` on the module path there is no provider, and every window presents
  on the CPU exactly as before. An application needs no `requires` for this;
  being on the module path is enough.
- **The policy.** It comes from two properties:
  - `goldberry.gpu=off|auto`;
  - `goldberry.gpu.composite=never|auto|always`.

  The default, `auto`, composites a window when a GPU layer needs it, so until
  phase 4 it composites nothing and no window changes. `always` composites every
  window from its first frame, which is how the path is run and measured now.
- **The window** (`Sdl3Window`) enters the composited mode in `acquireFrame`,
  the one call made before a frame is painted. It gives up its surface, is
  claimed, and from then on lends no surface. The frame loop paints into its
  own buffer, which it keeps between frames, so partial repaint goes on working.
  `present` hands the frame and its damage to the compositor.
  - A claim that is refused is remembered, and the window stays on the CPU.
  - A present that fails gives the window back, presents that same frame whole
    on the CPU, and stays there.
  - A window with an embedded page stays on the CPU, and leaves the composited
    mode if a page arrives. Whether a page's native view or the swapchain shows
    on top was left unmeasured in phase 0, and on the CPU the answer is known.
- **The compositor** owns the process's one device (D2). It is made at the
  first claim, with `goldberry.gpu.driver` and `goldberry.gpu.debug`, and a
  failure to make it is remembered.
  - Two frames in flight. VSYNC, or MAILBOX then IMMEDIATE when
    `goldberry.backend.vsync=false`.
  - One staging buffer (ADR-0478's, moved to `gpu.render` so both share it) and
    one composite pass, shared by every window.
  - Each window has a `B8G8R8A8_UNORM` UI texture at the frame's size. The first
    frame, and the first after a resize, goes up whole and cycled. After that,
    only the damage goes up, uncycled, keeping the rest.
  - The composite pass clears to opaque black and draws the UI texture 1:1 with
    `quad.vert` and `texture.frag`, sampled nearest, with premultiplied "over".
    The UI's pixels are premultiplied, so over black they keep their colour
    bytes, and the alpha goes opaque. That is what the window surface, which
    ignores alpha, showed. D7's separate `ui.vert`/`ui.frag` are not needed.
  - A swapchain format the bindings do not model is blitted to instead.
- **Pacing.** `FramePacer` stays on for composited windows. Phase 0 found the
  swapchain paces a composited frame, and the first cut stepped the pacer aside
  for them. Measured on the showcase (M1 Pro, 120 Hz, 240 frames):

  | | time | late | paint mean |
  |---|---|---|---|
  | composited, pacer aside | 0.93 s | 0 | 2.88 ms |
  | composited, pacer on | 2.9 s | 14 | 3.83 ms |
  | CPU | 2.8 s | 13 | 5.23 ms |

  With the pacer aside, a frame with no damage presents nothing and waits for
  nothing, and the loop painted such frames about a millisecond apart. With it
  on, a frame that did present has already waited out its interval in the
  acquire, so the pacer holds nothing back; it only stops the frames nobody
  sees.

## Alternatives considered

- **The composite written in `:core` against the natives wrappers.** `:core`
  reads `natives.sdl.gpu` already. But the shaders are `:gpu`'s resources, and
  so is the arithmetic that places a quad. Moving them would put GPU code in
  every application, used or not.
- **A registration call instead of `ServiceLoader`.** Something would have to
  call it, which means an application naming `:gpu`. `ServiceLoader` needs only
  the module path, and `:core` already finds the emoji face that way.
- **Deciding the mode at window creation** (D3's fallback). Not needed on macOS,
  where the switch is reliable. Deciding at the first frame keeps the door open
  for phase 4's attach-time switch.

## Consequences

- **D5 is corrected.** `GpuSurface`, `GpuLayer` and `CompositionMode` come with
  phase 4, their consumer, and not before (ADR-0019's rule). The layer
  interface will be `:gpu`'s, since it renders with `GpuFrame`. `:core` will
  record opaque layer slots in paint order and ask the compositor to draw them.
- **Hysteresis** (D3) comes with phase 4 too. Until layers attach and detach
  there is nothing to switch back from, except a failure, and that is for good.
- `PresentTimings` is kept per window but does not reach `FrameStats` or the
  `hud` yet.
- **Parity is proven at the composite pass.** Random premultiplied frames, which
  cover every byte value and every alpha, keep every colour byte exactly. A
  damage-only upload keeps every pixel outside the damage. The tab-by-tab
  comparison of the showcase in both modes is still to be done by eye, and so is
  a gallery parity test against the CPU goldens.
- **Untested platforms.** Wayland, X11, Windows and lavapipe are untried:
  claiming after SDL's hidden Wayland renderer, in particular. The GPU lane runs
  the backend end to end under `offscreen`.
- **Native image.** An image needs the service registered for `ServiceLoader`.
  That is phase 7's native-image row.

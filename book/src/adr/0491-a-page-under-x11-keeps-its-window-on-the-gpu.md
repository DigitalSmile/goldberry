# 491. A page under X11 keeps its window on the GPU

Date: 2026-09-30

## Status

Accepted. Amends
[ADR-0479](0479-a-window-is-composited-through-a-seam-core-declares-and-gpu-provides.md)'s
rule that a window with an embedded page stays on the CPU. Relates to
[ADR-0442](0442-a-page-is-a-child-window-where-the-window-system-allows-one.md),
[ADR-0445](0445-a-page-is-not-shown-before-it-can-be-seen.md)
and [ADR-0046](0046-what-present-actually-does.md).

## Context

Opening the showcase's web tab on Linux ended the process:

```text
Sdl3Window - "Goldberry — showcase on Linux / amd64" presents on the CPU from now on: a page is embedded in it
WebView - web-view: a page is open inside the window over 2496.0x1107.0 logical at (32.0, 232.0) (scale 1.0)
(java:9977): Gdk-WARNING **: The program 'java' received an X Window System error.
The error was 'BadDrawable (invalid Pixmap or Window parameter)'.
  (Details: serial 252 error_code 9 request_code 14 (core protocol) minor_code 0)
```

Request 14 is `GetGeometry`. GDK's X error handler exits, status 1. It
reproduced on every run.

**What happened.** The window was presenting through the GPU (ADR-0480). The
page was reparented into it, then `Sdl3Backend` moved the window to the CPU,
as ADR-0479 said a window with a page must. The next frame made the window's
first surface. On X11 that surface is SDL's texture framebuffer, and SDL
builds it with its OpenGL renderer. The Vulkan claim had taken the
window's `SDL_WINDOW_OPENGL` flag away, so `GL_CreateRenderer` called
`SDL_ReconfigureWindow`. X11 has no `ReconfigureWindow` hook, so SDL fell back
to `SDL_RecreateWindow`: it destroyed the X window and created another with a
new id. X destroys a window's children with it, and the page was one. GTK's
next request about its own window was `BadDrawable`.

`WindowIdentityTest` shows it without a page: an X11 window claimed, released
and then given a surface changes id (29360179 → 29360196 here).

**Why the window was on the CPU at all.** ADR-0479 left it unmeasured whether
the page or the swapchain shows on top. On X11 that is settled by the
protocol: a child window is stacked above its parent's contents, and the
server clips the parent's presents against it, the Vulkan swapchain's
included.

Keeping the GPU exposed two more defects that the CPU path had hidden:

- **The page never left its parking place.** `web-view` opens its page
  parked off the side of the window (ADR-0445). Its canvas painter polls the
  load state and brings the page over its box, and it asks for the next poll
  with `Host.repaint()`. A GPU window paints into a buffer it keeps, so its
  frames repaint in part, and a frame calls a painter only where it is
  damaged. `repaint` damages nothing, so after the page opened the painter
  never ran again. On the CPU the surface was new after leaving the GPU, and
  a new buffer is a whole repaint, which started the polling.
- **Stale pixels where a page had been.** Wherever the page covers the
  parent, the parent's presents are clipped, so the parent's pixels there
  keep what was last shown before the page arrived: the loading spinner.
  When the page moves away (parked for a modal, scrolled), X exposes the
  region and the parent must present again. SDL reports that as
  `WINDOW_EXPOSED`, and it became `window.repaint()`: no damage, so the
  composited present returned early and showed nothing new.

## Decision

**Under X11 a window keeps the GPU with a page in it.** `PageStacking`
decides by the window system of the page's parent handle. X11 is
`AboveTheSwapchain`. Cocoa and Win32 are `NeedsTheCpu`, with the reason
logged, until someone measures them: a Metal view added after the page
would cover it, and WebView2 over a flip-model swapchain is untested.
`Sdl3Backend.createEmbeddedWebView` calls `stayOnTheCpu` only for
`NeedsTheCpu`. `wantsComposited` no longer excludes windows with pages, since
`stayOnTheCpu` already keeps those on the CPU for good.

**On X11 a window surface is the X server's framebuffer.**
`Sdl3Backend.keepWindowsAcrossTheGpu` sets `SDL_FRAMEBUFFER_ACCELERATION=0`
after `SDL_Init`, under the `x11` driver and a policy that claims windows
(`always`, `auto`: `Composition.claimsWindows`). The native framebuffer touches
no graphics flag, so a window given back from the GPU keeps its id. This still
matters for pages under X11: `auto` gives a window back when its last layer
goes, and `always` gives one back when a present fails. An
`SDL_FRAMEBUFFER_ACCELERATION` already in the environment wins, and the log
warns.

A Vulkan renderer for the framebuffer (`vulkan,opengl`) was tried first. It
changes no flags either, but claiming a window after it segfaulted inside
`libnvidia-glcore` (driver 610.57.04) in `SDL_ClaimWindowForGPUDevice`.

**`web-view` polls with a rebuild.** `WebViewState.pollAgain` schedules a
zero-delay `setState`. A rebuild mints a new painter, and damage compares
painters by identity, so the box is damaged and the painter runs however the
window presents. It is used after opening and on every frame while the page
loads. It stops once the page has shown, so an idle application stays idle.

**An expose re-presents a composited window.** `CompositedWindow.exposed()`
marks the window stale. `SdlCompositedWindow.present` then draws its kept UI
texture to the swapchain even with no damage, uploading nothing, and clears
the mark once a swapchain texture was actually acquired. `Sdl3Backend` calls
it on `WINDOW_EXPOSED`, before the event's repaint.

## Consequences

- Checked on linux-x64 (XWayland under GNOME, NVIDIA 610.57.04). The web tab
  opens with the window on the GPU and no X error. The page draws above the
  swapchain, the spinner goes up and comes down, and the page is placed over
  its box. Closing the window exits 0. With `goldberry.gpu.composite=auto`
  the window presents on the CPU through the X framebuffer, page on top.
- The CPU path on X11 loses SDL's renderer, which waited for vertical blank.
  The frame loop's own pacer paces it. On the showcase's animated home
  screen under `auto` over 600 frames: 19 and 16 late with the X framebuffer,
  against 22 and 29 with SDL's renderer. Mean paint went from 8.4 ms to
  about 9.6 ms.
- Popups share the process-wide hint. The X framebuffer's formats for 24- and
  32-bit visuals are `XRGB8888` and `ARGB8888`, both ones Goldberry paints
  into. A transparent popup on this path was not looked at on screen.
- Tests: `PageStackingTest`, `CompositionTest.claimsWindows`,
  `Sdl3BackendTest.SurfaceKeepsTheWindow`, `WebViewPollingTest` (two of its
  three fail with `host.repaint()` put back), `CompositorTest.exposed`, and
  `WindowIdentityTest` (fails without the hint; it needs
  `-Pgoldberry.gpu.videoDriver=x11` where SDL would choose Wayland).
- Left as found: in `:gpu:gpuTest`, `GpuLayerBackendTest` segfaults in
  `VULKAN_DestroyDevice` when it closes its device, which ends the run. With
  it disabled, `Canvas3dGoldenTest.cube` fails its golden (52% of pixels,
  delta 1), and under Wayland `CompositorTest.givesTheWindowBack` and
  `oneDevice` fail to re-claim their window. All four fail the same way
  without this change. An interactive showcase run that closed the window
  after visiting the media tabs exited with SIGABRT, which may be the first
  of them. It was not reproduced.
- macOS and Windows keep the CPU rule. Measuring them is the way to lift it:
  `PageStacking.of` is the one line to change.

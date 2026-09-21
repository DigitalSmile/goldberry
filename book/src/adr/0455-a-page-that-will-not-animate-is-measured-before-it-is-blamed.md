# 455. A page that will not animate is measured before it is blamed

Date: 2026-09-21

## Status

Accepted.

## Context

A page embedded through §9's `web-view` animated visibly badly on the machine
this was reported from — a 100 Hz panel, an NVIDIA GTX 1660 Ti on driver
610.57.04, and a GNOME Wayland session. The engine's own logging said it had
brought up its DRM path at 100 Hz. The animation was still slow.

Everything about the toolkit's side of that boundary pointed at the toolkit.
The reasoning was available, it was written down already, and it was wrong:

- On Linux the engine runs on GLib's main context, and **nothing drives that
  context except Goldberry's event loop**.
  [`WebViewEngine#pump()`](../../../core/src/main/java/io/github/digitalsmile/goldberry/render/web/WebViewEngine.java)
  is called once per turn of `EventLoop#run`.
- That loop parks in SDL for up to `WEB_VIEW_TIMEOUT`, **8 ms**, and GLib's file
  descriptors are not in that wait. `EventLoop` says so itself: *"Nothing wakes
  this loop when WebKit has work to do."* See [ADR-0441].
- `goldberry_webview_pump` then runs at most **16** `g_main_context_iteration`
  calls and breaks only when the context goes idle — so under an animation it
  always burns all sixteen and returns with work still queued.

Three real constraints, all of them genuinely in the code, and together they
make a complete and plausible story: the page is serviced on a polling clock
with a fixed dispatch quota, so of course it cannot keep up with a 100 Hz panel.

That story predicted 50–100 frames a second. The page was getting **0.3**.

Being two orders of magnitude out is not a detail to reconcile. It means the
mechanism is not the one in the story, and no amount of tuning the two constants
would have found that out — both would have "improved" the number slightly and
confirmed the wrong cause.

## Decision

**Measure the page's own clocks before changing anything, and measure three of
them.**

`:example`'s `webpump` package opens a page whose document counts, and reports
through a binding once a second:

| clock | what it is | what a low reading means |
|---|---|---|
| `timer` | `setInterval(…, 4)`, a plain GLib timer source | the main context is not being drained — **the embedder's fault** |
| `timeline` | how often `document.timeline` advances, sampled *from the timer* | the engine is not compositing |
| `raf` | `requestAnimationFrame` | the engine composites and does not run the page's callbacks |

The middle one is load-bearing and is the reason this is written down. Sampling
the rendering update from a **timer** rather than from a frame callback is what
makes it readable when frame callbacks are the thing that is broken. With only
`raf`, a stalled compositor and a broken animation controller are the same
number.

## What it measured

On the reported machine, in Goldberry and in stock `MiniBrowser` against the
same `webkit2gtk-4.1` build, identically:

```
median 1.0 raf/s, 63.2 timeline/s, 125.0 timer/s — RAF_STARVED
```

- **The pump is fine.** 125 timer callbacks a second, every second, no drift.
- **The engine composites.** The document timeline advances ~63 times a second,
  so CSS animations and transitions run.
- **`requestAnimationFrame` is dead.** ~1 callback a second — the flat, perfectly
  repeatable rate of a fallback timer, not the noisy shape of starvation.

Three controls make that readable rather than merely suggestive:

- **Firefox, same machine, same document: 60 raf/s.** The instrument works.
- **`MiniBrowser`, its own `gtk_main()`, no Goldberry anywhere: the same
  1.0 raf/s.** The embedding is not involved. Both the GTK3
  (`webkit2gtk-4.1`) and GTK4 (`webkitgtk-6.0`) builds of 2.52.6 behave
  identically.
- **A document with no timers whatsoever**, beaconing from inside the frame
  callback itself: **0.3 raf/s**. So the probe's own `setInterval` is not
  starving the frame callback — rAF is in fact *worse* without it, which is
  its own clue: the controller appears only to advance when something else
  churns the run loop.

### What it is not

Swept, each against the no-timer document, each identical at 0.3 raf/s:
`WEBKIT_DISABLE_DMABUF_RENDERER`, `WEBKIT_DMABUF_RENDERER_FORCE_SHM`,
`WEBKIT_DISABLE_COMPOSITING_MODE`, `WEBKIT_FORCE_COMPOSITING_MODE`,
`WEBKIT_DISABLE_SANDBOX_THIS_IS_DANGEROUS`, `LIBGL_ALWAYS_SOFTWARE`,
`GSK_RENDERER=cairo`, `GDK_BACKEND` of either `x11` or `wayland`, and
`__EGL_VENDOR_LIBRARY_FILENAMES` pointed at Mesa so that NVIDIA's EGL is out
of the process entirely.

That last one matters: **this is not the NVIDIA driver.** A GPU stack that has
been replaced with llvmpipe and Mesa EGL and still produces the identical
reading is not the thing producing the reading.

Nor is it the window. The page reports `hasFocus() == 1`,
`visibilityState == "visible"` and a sane `innerWidth`, so it is not occluded,
unmapped or throttled as a background tab. WebGL2 contexts are created
successfully. The display is correctly configured — Mutter reports
`2560x1440@99.946` as current and preferred. The engine's stderr is silent.

### The shape of the fault

Frame callbacks are run **as part of** the rendering update. A timeline that
advances 63 times a second while the callbacks inside it run once says the
rendering update is happening and the animation-callback step within it is
not. That is not a missing vsync — a missing vsync would slow both equally,
and neither lowering nor raising anything on the toolkit's side of the
boundary can reach it.

### What upstream says

No matching report for 2.52.x, and nothing in `webview/webview` describing the
1 Hz symptom — [#502] is Windows/Edge and about window moves breaking a frame
loop.

The **60 Hz** half of the reading is known and unfixed: [#528] is exactly it,
closed with the maintainer saying *"I am not sure why it seems to be
throttling… it might mean we have to dig into webkit core"*, and its last
comment (June 2025) reports the same control this ADR ran — both `MiniBrowser`
builds, every environment variable, Wayland, no success. A different person on
different hardware, a year earlier.

That thread proposes `webkit_settings_set_hardware_acceleration_policy(…,
ALWAYS)`, which **webview 0.12.0 does not call** — it sets only
`javascript_can_access_clipboard`, `enable_write_console_messages_to_stdout`
and `enable_developer_extras`. Deliberately not adopted here: MiniBrowser
reproduces the fault with no webview code in the process, so the setting cannot
be the cause, and adding it would look like a fix while changing nothing.

The venue for the 1 Hz bug is `bugs.webkit.org`, not `webview/webview`.

[#502]: https://github.com/webview/webview/issues/502
[#528]: https://github.com/webview/webview/issues/528

## Consequences

**This is not Goldberry's bug and there is no Goldberry fix.** An engine that
composites at 63 Hz while declining to run the page's frame callbacks is broken
somewhere no embedder can reach. Lowering `WEB_VIEW_TIMEOUT` or raising the
pump's sixteen would have been a change with a rationale, a plausible story, and
no effect — which is the expensive kind of wrong, because it ships.

**The two constants stay as they are.** ADR-0441's reasoning about them is
untouched: they are real ceilings and the fd-wakeup remains the honest fix for
the *cost* of the 8 ms poll. They were simply not what was wrong here, and this
ADR exists so the next reader does not re-derive the same convincing story.

**A workaround exists and belongs in the docs rather than in the code.** CSS
animations, transitions and the Web Animations API all ride the rendering
update, which works; only `requestAnimationFrame` does not. A page that must
animate on this engine animates in CSS. `docs/web-pump-probe.md` says so.

**The probe is not part of any check.** It opens a real window and needs a real
engine, and it answers a question about *a machine* rather than about the code.
`:example:test` has to keep running where there is no WebKit at all, so it is
its own task — `:example:webPumpProbe`.

## Notes

The rendering update measured 63 Hz on a 100 Hz panel, which is a second and
smaller discrepancy this did not chase. It is recorded here because it will
look like a new finding to whoever reads the probe's output next, and it is not.

[ADR-0441]: 0441-a-web-page-is-a-window-not-a-box.md

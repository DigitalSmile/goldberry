# Measuring an embedded page's animation rate

A page in a `web-view` draws into its own platform window. There are no pixels
for the toolkit to count, and
[`FrameStats`](../core/src/main/java/io/github/digitalsmile/goldberry/stats/FrameStats.java)
describes Goldberry's frames rather than the engine's — so when somebody reports
that "the page animates badly", nothing in the toolkit can see it.

The only witness is the page. `:example:webPumpProbe` asks it.

```bash
./gradlew :example:webPumpProbe
./gradlew :example:webPumpProbe -Dgoldberry.webpump.seconds=30
```

It opens a page in a window of its own, animates a box in CSS, and reports three
counts a second through a binding. The summary lands in the log:

```
page animation: 10 reading(s) after the first: median 1.0 raf/s, 63.2 timeline/s,
125.0 timer/s — RAF_STARVED
```

It is **not** part of `:example:test` and never will be: it needs a real window
and a real engine, and it answers a question about the machine it runs on.

## Reading the three clocks

They nest, and each one belongs to somebody different. Read them in this order.

| clock | what it is | a low reading means |
|---|---|---|
| `timer` | `setInterval(…, 4)` — a plain GLib timer source | GLib's main context is not being drained. **Goldberry's fault**, and the only one of the three it can fix |
| `timeline` | how often `document.timeline` advances — one tick per *rendering update* | the engine is being pumped and is not compositing |
| `raf` | `requestAnimationFrame` — the scripted animation controller | the engine composites and does not run the page's callbacks |

`timeline` is sampled **from the timer**, not from a frame callback. That is the
point of it: it stays readable when frame callbacks are the broken thing. With
only `raf` to go on, a stalled compositor and a dead animation controller look
identical, and they have nothing to do with each other.

The probe names the conclusion itself, as `CONTEXT_STARVED`, `RENDERING_STALLED`,
`RAF_STARVED` or `HEALTHY`.

## What a healthy reading looks like

Roughly the display's rate for `raf` and `timeline`, and whatever the engine
clamps `setInterval` to. Firefox on a 100 Hz panel:

```
raf=60.9  timeline=—  timer=235.8
```

## Known: WebKitGTK 2.52.6 does not run `requestAnimationFrame`

Measured 2026-09-21 on a GTX 1660 Ti, NVIDIA 610.57.04, GNOME Wayland,
100 Hz panel — see [ADR-0455]:

```
median 1.0 raf/s, 63.2 timeline/s, 125.0 timer/s — RAF_STARVED
```

The pump is fine, the engine composites on a ~16 ms clock, and
`requestAnimationFrame` fires about once a second — 0.3/s in a document with no
timers in it at all.

Three controls establish that this is nothing to do with the toolkit:

- Firefox, same machine, same document: **60 raf/s** — the probe works.
- Stock `MiniBrowser` on the same `webkit2gtk-4.1`, its own `gtk_main()`, no
  Goldberry: **the same 1.0 raf/s**. Same for the GTK4 `webkitgtk-6.0` build.
- A document with **no timers whatsoever**, beaconing from inside the frame
  callback: **0.3 raf/s**. So the probe's own `setInterval` is not the cause.

Unchanged by every one of: `WEBKIT_DISABLE_DMABUF_RENDERER`,
`WEBKIT_DMABUF_RENDERER_FORCE_SHM`, `WEBKIT_DISABLE_COMPOSITING_MODE`,
`WEBKIT_FORCE_COMPOSITING_MODE`, `WEBKIT_DISABLE_SANDBOX_THIS_IS_DANGEROUS`,
`LIBGL_ALWAYS_SOFTWARE`, `GSK_RENDERER=cairo`, `GDK_BACKEND=x11|wayland`, and
`__EGL_VENDOR_LIBRARY_FILENAMES` pointed at Mesa so NVIDIA's EGL is out of the
process. **It is not the GPU driver** — a stack replaced with llvmpipe and Mesa
EGL gives the identical reading.

Nor is it the window: the page reports `hasFocus() == 1`, `visibilityState ==
"visible"` and a sane `innerWidth`, so it is not occluded or backgrounded. The
display is right too — Mutter reports `2560x1440@99.946` current and preferred.

### Upstream

Nothing in `webview/webview` reports the 1 Hz symptom. The **60 Hz** half is
known and unfixed —
[#528](https://github.com/webview/webview/issues/528), closed, whose June 2025
comment reports the same `MiniBrowser`-plus-every-env-var control reaching the
same dead end on Wayland.

Report the 1 Hz bug to [bugs.webkit.org](https://bugs.webkit.org) rather than to
`webview/webview`: `MiniBrowser` reproduces it with no webview code in the
process.

### The workaround

CSS animations, CSS transitions and the Web Animations API all ride the
rendering update, which works. Only `requestAnimationFrame` does not. A page
that has to animate on this engine animates in CSS:

```css
@keyframes glide {
  from { transform: translateX(0); }
  to   { transform: translateX(400px); }
}
.thing { animation: glide 2s ease-in-out infinite alternate; }
```

Most JavaScript animation libraries drive from `requestAnimationFrame`, so on
this engine they will crawl while a hand-written CSS keyframe glides. That
difference is the symptom to look for before reaching for this probe at all.

## What this probe already ruled out, so you need not

The obvious suspicion is the pump, and the reasoning for it is good enough to be
worth refuting once. The event loop parks in SDL for up to 8 ms with GLib's file
descriptors outside that wait, and `goldberry_webview_pump` runs at most sixteen
`g_main_context_iteration` calls per turn. Both are real ceilings; neither was
the cause here. `timer` reads 125/s with no drift, which is every dispatch the
poll can deliver.

[ADR-0441] holds the reasoning about those two constants and the fd-wakeup that
would remove the 8 ms poll's cost. [ADR-0455] holds why it was not the answer to
this.

[ADR-0441]: ../book/src/adr/0441-a-web-page-is-a-window-not-a-box.md
[ADR-0455]: ../book/src/adr/0455-a-page-that-will-not-animate-is-measured-before-it-is-blamed.md

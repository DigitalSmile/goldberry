# Servo as the engine behind an embedded `web-view`

Investigation and plan, 2026-09-20. Nothing here is built. It exists so that the
decision is made against what Servo *is* today rather than against what
[ADR-0441](../book/src/adr/0441-a-web-page-is-a-window-not-a-box.md) assumed when
it was written this morning.

Status legend as in [`todo-sweep.md`](todo-sweep.md).

## 1. Why this is being re-examined at all

ADR-0441 made a page a **window** rather than a widget, and the whole argument
turned on one property: `webview/webview` cannot render offscreen. From that
followed everything that is now disliked — the page is a foreign platform
window, so nothing the toolkit paints can cover it, a `scroll` viewport cannot
clip it, no golden image can contain it, and on Wayland it cannot even be placed
where a widget is.

An engine that renders into a **buffer** removes all of that at once. The page
becomes an ordinary raster the compositor already knows how to handle: clipped,
overlapped, transformed, golden-tested, and with no GTK in the process at all —
which also dissolves the GTK 3/4 conflict and the `tray-icon` coupling that
ADR-0441 had to accept.

So the question is not "is Servo nicer than WebKit". It is: **is there an engine
with an acceptable licence that gives us pixels?**

## 2. What has actually changed since the entry was parked

The parked `TODO.md` entry said: *"libservo is Rust-only against a deliberately
unstable API, so the module would own a `cdylib` shim and its breakage."* Half of
that is now out of date.

| Claim | State on 2026-09-20 |
|---|---|
| "Rust-only" | **No longer true.** `servo_capi` is an official C API crate in `ffi/capi`, `crate-type = ["cdylib"]`, headers by cbindgen, opaque pointers. Merged 2026-06-11 ([PR #44984](https://github.com/servo/servo/pull/44984)) |
| "a cdylib shim would be ours to maintain" | **No longer true.** Upstream maintains it, and states the goal is "eventually also provide a stable ABI for Servo" |
| "against a deliberately unstable API" | **Still true.** No stability commitment anywhere; the crate is three months old and explicitly incomplete |
| Licence | MPL-2.0 — file-level copyleft, clean beside Apache-2.0 when consumed as a separate shared library |
| Offscreen | `SoftwareRenderingContext` and `OffscreenRenderingContext` both exist, and the software one is exposed to C |

## 3. What `servo_capi` can and cannot do today

Read off `ffi/capi` at `main`. The surface is 50 functions. What matters:

**Present**

- `servo_rendering_context_create_software` — the software renderer, from C
- `servo_builder_*`, `servo_options_*`, `servo_preferences_*` — construction
- `servo_webview_builder_*`, `servo_webview_load` — make a view, point it at a URL
- `servo_spin_event_loop` — pump, which is the shape `goldberry_webview_pump`
  already has
- `servo_webview_paint`
- **`servo_webview_take_screenshot(webview, callback(data, width, height, error, user_data))`**
  — a pixel buffer, handed to a callback, valid for the duration of the call.
  That is exactly the shape `Image`/`PixelBuffer` wants

**Absent — and this is the whole of the problem**

Grepping the crate for `input`, `mouse`, `keyboard`, `scroll`, `wheel`,
`resize`, `size`, `focus`, `touch` and `zoom` returns **nothing**. (`ime` matches
only the substring in `time`-profiling.)

So `servo_capi` today renders **a static, non-interactive page at a fixed size**.
A user could not click a link, type in a field, scroll, or have it follow a
resizing window. That is a page-to-image renderer, not a web view.

The Rust side has all of it — `WebView::notify_input_event` and the rendering
context's resize are ordinary `libservo` API. It is the **C wrapper** that has
not been written, and upstream says so: *"this PR just adds the minimal setup
necessary for creating a webview and spinning the event loop"*, with follow-ups
intended.

## 4. The plan

Five phases. **Phase 0 is the only one worth doing before committing to the
rest**, because it is the one that can kill the idea cheaply.

### Phase 0 — spike, throwaway, timeboxed

Answer three questions and write no repository code:

1. **What does the build actually cost?** Clone Servo, `./mach build` the
   `servo_capi` cdylib on this machine. Record wall time, disk, and the size of
   `libservo_capi.so`. Servo pins Rust 1.97.1 and pulls mozjs (SpiderMonkey) and
   mozangle; the embedding meta-issue still lists *"precompiled dependencies:
   both mozangle and mozjs require further work on shared object support across
   platforms"*, so this may not even link cleanly as a cdylib.
2. **Do the pixels come out?** A ~50-line C program: software context, webview,
   load `https://github.com/digitalsmile/goldberry`, spin, `take_screenshot`,
   write a PNG.
3. **Does Servo render the page we care about?** Look at that PNG. Servo's web
   compatibility is far below WebKit's, and the target is a *GitHub page* —
   heavy CSS, JS-driven. If it renders badly, everything below is moot.

**Exit:** a PNG and three numbers. Stop here if the build is unmanageable or the
page is unusable.

### Phase 1 — the missing C API (upstream, and not in our control)

Write and land, in `servo/servo`:

- `servo_webview_notify_input_event` — pointer, wheel, keyboard
- `servo_webview_resize` (or rendering-context resize)
- whatever focus/IME the widget needs

Mechanical Rust over an existing Rust API, and upstream has said follow-ups are
wanted. **The risk is latency, not difficulty**: this is somebody else's review
queue, and until it lands there is no interactive widget. Vendoring our own copy
of `ffi/capi` is the fallback and takes on exactly the maintenance ADR-0441 was
trying to avoid.

### Phase 2 — `:natives` learns Rust

`libgoldberry-servo`, a **third** optional shared library beside
`libgoldberry-webview`, on the pattern that already works:

- pinned in `gradle/libs.versions.toml` like every other upstream (ADR-0035)
- built only where a Rust toolchain and Servo's dependencies exist; absent
  otherwise, and the build says so rather than failing
- linked into nothing, opened on demand, so Servo is never a load-time
  dependency of the toolkit
- `Capability.WEB_VIEW` gains a sibling, or learns to report *which* engine

Cost: a Rust toolchain on four platforms and in CI, and Servo's build time on
every native leg. This is the largest permanent cost in the plan.

### Phase 3 — the FFM binding

`io.github.digitalsmile.goldberry.natives.servo`, mirroring `natives.webview`
exactly: a `ServoLibrary` loader, a `Servo` owning wrapper, a `calls` package, an
ABI probe bound *before* the rest, qualified export to `:core`. The shape is
known and the mistakes are already documented.

### Phase 4 — the widget, which is the point

This is where every win lands:

- `WebView` becomes a **real `Widget`** with a box, in `widgets.core.web`
- each frame, the screenshot buffer is uploaded as an `Image` and drawn into the
  `Frame` — so it is clipped by `scroll`, covered by `dialog`, `popover` and
  `toast`, and subject to `opacity` and `transform` like any other raster
- pointer and keyboard events route from the existing `PointerRouter` into
  `notify_input_event`
- **a golden image can finally contain a page**, which closes the coverage hole
  `tray-icon` and today's `web-view` both have
- a `web-view` node in markup becomes possible, because there is now something
  for a `row` to size

Watch: `take_screenshot` is a full-surface readback per frame — roughly 4 MB for
1280×800, so ~245 MB/s of copying at 60 Hz. Tolerable, not free, and there is no
damage-rect path in the C API either. Measure it against the frame budget
(ADR-0342) before believing it.

### Phase 5 — the documents

ADR-0441 is **superseded, not deleted**: its Wayland and GTK findings stay true
and are the reason an offscreen engine is worth this much work. A new ADR records
the reversal and what changed underneath it. Then `content-widgets.md` §11,
`core-widgets.md` §9 (a `web-view` that is a widget moves out of `widget.shell`),
`ARCHITECTURE.md` §11.1, `status.md`, `README.md`.

## 5. What to do with `webview/webview`

Keep it, at least until Phase 4 is proven. It works today, it is MIT, it is
73 KB, and "open this page in a real browser window" is a legitimate thing an
application wants that an embedded widget does not replace. If Servo lands well,
the two are a genuine pair: a window for *the desktop's browser experience*, a
widget for *content inside your application*.

## 6. Risks, honestly

| Risk | Severity |
|---|---|
| `servo_capi` has no input or resize; Phase 1 is upstream's queue | **High** — blocks everything interactive |
| No API stability commitment; the crate is 3 months old | **High** — breakage is expected, not hypothetical |
| Servo's web compatibility on a real GitHub page | **High** — and Phase 0 answers it for the cost of an afternoon |
| Rust toolchain on four platforms and in CI | **Medium** — permanent, and the biggest build cost the project would have taken on |
| mozjs/mozangle shared-object support "requires further work" | **Medium** — may block the cdylib on some platforms |
| Per-frame full-surface readback | **Low–medium** — measurable, and measurable early |

## 7. Recommendation

**Do Phase 0 and stop.** It is an afternoon, it changes no repository file, and
it answers the two questions that decide everything: can this be built at all,
and does Servo draw the page you actually want to look at. Every later phase is
expensive and none of it is worth starting if the answer to either is no.

Sources: [servo_capi PR #44984](https://github.com/servo/servo/pull/44984),
[embedding meta-issue #30593](https://github.com/servo/servo/issues/30593),
[OffscreenRenderingContext PR #35465](https://github.com/servo/servo/pull/35465),
[This month in Servo, Feb 2025](https://servo.org/blog/2025/02/19/this-month-in-servo/),
[servo.org](https://servo.org/).

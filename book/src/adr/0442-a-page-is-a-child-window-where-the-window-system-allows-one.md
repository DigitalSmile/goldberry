# 442. A page is a child window, where the window system allows one

Date: 2026-09-20

## Status

Accepted. Amends [ADR-0441](0441-a-web-page-is-a-window-not-a-box.md), which
stands on everything it found and is wrong about what follows from it: a page
**can** be a widget on X11, Windows and macOS, and the reason it cannot on
Wayland is the one ADR-0441 wrote down.

## Context

ADR-0441 made a page a window rather than a widget, and the argument was in two
parts. The first is a fact and has not changed: `webview/webview` cannot render
offscreen, so a page is always a real platform window. The second was a
conclusion, and it does not follow:

> a `web-view` that sat in a layout on X11, Windows and macOS and became a loose
> window on Wayland would be two behaviours wearing one name

That is an argument against a **silent** fallback, not against embedding.
Given the third option — embed where the window system allows it, and say so
plainly where it does not — the objection disappears. Nobody is misled by a
widget that says "this session cannot put a page in a window, and here is why".

What made this worth revisiting is that the alternative engines are worse. An
offscreen renderer would make a page an ordinary raster with none of these sharp
edges, and the survey (`docs/servo-web-view-plan.md`) found: Ultralight is
proprietary and revenue-gated per downstream application; CEF is a 150 MB
vendored prebuilt per platform; and Servo's `servo_capi`, though newly real, has
**no input, resize or scroll** — a page you cannot click.

## Decision

**`WebView` is a widget.** It has a box, it takes part in layout, and the page's
own platform window is made a child of the application's, positioned over that
box and moved with it.

Where the window system does not allow a child window, it **opens nothing** and
paints a message saying why. Not a loose window, not a silent degradation.

| Session | What happens |
|---|---|
| X11 (including XWayland) | `XReparentWindow` — a real widget |
| Windows | `SetParent` — *unverified* |
| macOS | `addSubview:` — *unverified* |
| **Wayland** | **nothing opens; the widget says why** |

`WebPage` and `WebViews.open` stay as they are, for the application that wants a
page in a window of its own. The two are a pair rather than a replacement.

### §12's escape hatch, finally built

`ARCHITECTURE.md` §12 has promised since day one that *"backends expose raw
native window handles for apps embedding external renderers"*, and nothing had
built it. Embedding needs exactly that, so `BackendWindow.nativeHandle()` now
answers a `NativeHandle` — an X11 `Window`, an `HWND` or an `NSWindow*` — over
three newly exported SDL symbols.

**Wayland answers empty on purpose.** There is a `wl_surface` and it is not
reported, because nothing may be done with it and a handle that cannot be
embedded into is an invitation to try.

## Three things found by building it, all of which bit

### 1. The shipped `web-view` was already broken, by HarfBuzz

`libgoldberry` exported 25 `hb_*` symbols from its statically linked HarfBuzz
14.3.1. Anything that pulls in GTK — `web-view` through WebKitGTK, or
`tray-icon` through libayatana-appindicator — brings the system's HarfBuzz
12.3.2. One global symbol namespace: pango's calls to those 25 names bound to
*ours* while its other ~500 bound to the system's, and the process died in
`hb_font_set_var_coords_design`, inside `gtk_init`, before any Goldberry code
ran.

This was not embedding's bug. It was in the detached-window `web-view` that had
already shipped, and it was missed because the C probe that pumped was not
linked against `libgoldberry`, and the FFM probe only worked because a tray had
already initialised GTK so `gtk_init` was a no-op.

**HarfBuzz is the only upstream that collides**: Blend2D, Yoga, SDL and libwebp
share no symbol name with the GTK stack. So the fix is 25 `goldberry_hb_*`
wrappers and no raw `hb_*` export — which is what the superbuild's own header
has always said it wanted: *"an app embedding its own SDL or HarfBuzz must not
collide with ours"*. The export list was the hole in that.

It is a departure from §3.1's "no C glue in between", and the only one.

### 2. The two halves of one process disagreed about the window system

SDL is asked for X11 first on Linux (ADR-0086). GDK, asked nothing, prefers
Wayland whenever `WAYLAND_DISPLAY` is set. On an XWayland desktop that makes the
application's window an X11 window and its GTK surfaces Wayland surfaces —
invisible until something needs the two related, and embedding is exactly that.

A page then refuses to embed on a machine where everything works. Worse, it
depended on *order*: a tray shown first initialised GTK on Wayland and the page
had no say afterwards, which is precisely the showcase's start-up.

So the backend now says which window system it picked, once, before anything can
call `gtk_init` — which on Linux means before the first `tray-icon`.

### 3. An ABI check that runs after the binding is not a check

A stale `libgoldberry-webview.so` failed with *"does not export
goldberry_webview_gtk_conflict"* rather than *"this library is ABI 1 and this
build binds ABI 2"*, because `bind` looked up all ten symbols and threw on the
first missing one long before the version was read. The probe is now bound and
asked **alone**, first.

The same library also took `Goldberry.capabilities()` down with it: a throw from
a static initialiser poisons the class, so the `catch` that handled the first
failure asked again and got a bare `NoClassDefFoundError`. An optional feature
must not be able to break the call that lists features.

## Consequences

### What an embedded page still cannot do

These follow from the page being a window above the frame rather than a layer in
it, and are written on `WebView` itself:

- **Nothing painted can cover it.** A `dialog`, `popover`, `tooltip` or `toast`
  overlapping the page is drawn underneath and is invisible where they meet.
- **A `scroll` viewport does not clip it** — the child clips to the *window*, so
  a page scrolled halfway out is still drawn whole.
- **`opacity`, `transform` and frost do not reach it**, there being no raster.
- **No golden image can contain it.** A picture of this widget is a picture of
  what it paints when there is no page, and it says so.

### A page must be closed before the window it is inside

The X server destroys a window's children with it, so an embedded page torn down
*after* its parent is GTK unwinding a window the server has already reclaimed:

```
Gdk-WARNING: GdkWindow 0x2400003 unexpectedly destroyed
GLib-GObject-CRITICAL: g_signal_handler_disconnect: assertion failed
Gdk-CRITICAL: gdk_frame_clock_end_updating: assertion 'GDK_IS_FRAME_CLOCK' failed
```

This is the **popup problem exactly**, and `Sdl3Window.close` already carried the
answer two lines above where the fix went — *"SDL destroys a window's popups with
it, so after this call their handles are dangling"*. Pages are now closed beside
popups, before `destroyWindow`, and the backend tracks which window each one is
in so it knows what to close.

### Wayland is not waiting for anything

There is no protocol and no ratified proposal. `xdg-foreign` is toplevel
*parenting* and raises `invalid_surface` on anything else; the nearest tracking
thread is [wayland-protocols #194](https://gitlab.freedesktop.org/wayland/wayland-protocols/-/issues/194);
the request dates to a [2012 wayland-devel thread](https://lists.freedesktop.org/archives/wayland-devel/2012-February/002030.html).
The one live idea is [`ext_image_sampler_v1`](https://github.com/canonical/mir/pull/5203),
which would let a client read another surface's contents — that plus our own
compositing would be a real Wayland widget, and it is early and may end up
privileged-only.

An application on a Wayland desktop that wants a page can have one today by
asking SDL for the x11 driver, which runs it under XWayland.

### Input needs no routing

The page is a real child window, so the window system delivers its clicks and
keystrokes to WebKit directly. Nothing forwards events and the pointer router
never sees them — correct, since they were never this toolkit's.

### Where it is unverified

**Windows and macOS.** `SetParent` and `addSubview:` are the calls and neither
is written, because neither can be run here — the tray's situation exactly. The
Linux path was built and exercised: the X server reports the page as a viewable
child of the Goldberry window at the widget's own box.

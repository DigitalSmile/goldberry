# 441. A web page is a window, not a box

Date: 2026-09-20

## Status

**Amended by [ADR-0442](0442-a-page-is-a-child-window-where-the-window-system-allows-one.md)**,
which keeps every finding here and reverses the conclusion: a page *can* be a
widget where the window system allows a child window, and Wayland — for the
reason set out below — is where it cannot. What was wrong was the step from
"Wayland cannot" to "then nowhere": that is an argument against a silent
fallback, and a widget that says plainly why a session cannot show a page misleads
nobody.

The window form described here still ships, for the application that wants a page
in a window of its own.

Accepted. Takes `goldberry-web` out of `docs/content-widgets.md`'s optional
modules, where [ADR-0190](0190-a-content-module-brings-its-own-natives.md) had
put it and where it had been **parked** since, and builds it on
[webview/webview](https://github.com/webview/webview) instead of on Servo — as
the second member of §9's `widget.shell` group, beside `tray-icon`, rather than
as a box in the catalog.

## Context

The parked entry said one thing and it was true: libservo is Rust-only against a
deliberately unstable API, so the module would own a `cdylib` shim and its
breakage. What it never said is that Servo was not the only way to put a page on
screen, and the entry sat unexamined for two milestones because "parked" reads
like an answer.

`webview/webview` is a different proposition in every respect that mattered.
It is MIT, it is a C API over a C++ header, and it brings **no engine of its
own**: it drives WebKitGTK on Linux, WebView2 on Windows and WKWebView on macOS
— libraries the desktop already has. So the two arguments that quarantine a
content module under ADR-0190, a heavy native payload and an attribution or
copyleft obligation, do not apply to it. Nothing is vendored, nothing is
shipped, and the licence question PDFium is parked on does not arise.

That is what takes it out of `content-widgets.md`. What decides its *shape* is a
different argument, and it is entirely about Wayland.

### A page is never pixels the toolkit owns

**`webview/webview` cannot render offscreen.** There is no software surface, no
buffer, no "draw into this": it creates a real platform window and the engine
draws into it. Every other content widget in `content-widgets.md` obeys the
third rule of §11.1 — *everything here rasterizes on the CPU into a buffer* — and
this one cannot, at all, on any platform.

So a page is a platform window, and the only question is where that window may
be put.

### Wayland forbids both ways of putting it somewhere

There are exactly two ways to make a platform window look like a widget, and a
Wayland session refuses both:

| | X11 | Windows | macOS | Wayland |
|---|---|---|---|---|
| Reparent the window into the SDL window | `XReparentWindow` | `SetParent` | `addSubview:` | **no** |
| Align a separate window to a widget's box | yes | yes | yes | **no** |

The first is missing because a Wayland surface belongs to the client that made
it: a subsurface may only be a child of another surface on the *same*
connection, and GTK's WebKit surface and SDL's window are two clients as far as
the compositor is concerned. There is no protocol for it and no plan for one.

The second is missing because a Wayland client is not told where it is and may
not say where it goes. That is not a gap in this toolkit — it is written into
the SPI already:
[`BackendWindow#position()`](../../../core/src/main/java/io/github/digitalsmile/goldberry/render/window/BackendWindow.java)
returns an `Optional` and documents it as *"empty when the platform will not
say"*, and `SDL_SetWindowPosition` is a no-op for toplevels there. A companion
window cannot follow a box it cannot locate.

Wayland is the default session on GNOME, and on the machine this was built on.

### So the widget was the thing that had to go

The first draft of this decision was a companion window aligned to a widget's
box, and it was written up before the positioning half of that table was
checked. It does not survive the check. What was left was a choice between a
`web-view` that is a box on three platforms and a free window on the fourth, and
a `web-view` that is the same thing everywhere.

Two behaviours wearing one name is the failure this project keeps naming — and
the one that breaks here is the common Linux desktop, which is the worst
possible platform to have the degraded path on.

## Decision

**A web page is a window the application opens, not a widget in a layout.**

`WebViews.open(host, page)` takes a [`WebPage`] value and returns a handle: a
real top-level window, transient for the application's own, which the desktop
places and the user moves and closes like any other. It navigates, it evaluates
script, it reports its title and its load state, and it closes. It has no box,
no cascade, no hit test and no place in the element tree.

This is `tray-icon`'s shape exactly
([ADR-0191](0191-a-tray-is-a-menu-somebody-else-draws.md)), which is why
it goes in `widget.shell` beside it — §9's group is already described as *the
one group whose first member is not a widget*, because the desktop draws it. A
page is the second such member, and for the same reason: the engine draws it,
into a window of the platform's own.

It is also the only shape that is honest on all four targets. Nothing is
degraded on Wayland, because nothing was promised that Wayland cannot do.

### The native library is its own, and is loaded on demand

`libgoldberry` does **not** link WebKitGTK, and must not. A load-time dependency
on GTK and WebKit would be carried by every Goldberry application on Linux,
including the overwhelming majority that never open a page, and an application
on a machine without them would fail to load the toolkit at all rather than fail
to open a web view.

So the build produces a second, optional shared library —
`libgoldberry-webview` — which is linked into nothing and opened lazily the
first time a page is asked for. Where the build had no WebKit headers, or the
machine has no WebKit at run time, the library is simply absent:
[`Capability#WEB_VIEW`] is not reported and `WebViews.open` answers empty, which
is the same answer `Host#tray` gives a desktop with no notification area.

That is ADR-0325's pattern, and its reason: *"could not ask" and "asked and was
told nothing" are different facts, and only the first one is fixable.*

### The page is pumped on the UI thread, not run on its own

`webview_run()` takes over a thread with its own loop, and the obvious move — a
dedicated thread per page — is wrong on macOS, where AppKit requires the
process's first thread and SDL already has it. It is also wrong for this
toolkit: a navigation callback that lands on a private thread cannot touch a
widget, and [ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md) is
the rule that everything a listener will touch belongs to the UI thread.

So a page is created on the UI thread and `webview_run` is never called. What
services it is a `goldberry_webview_pump()` the frame loop calls, and its
implementations are deliberately not alike:

- **macOS and Windows: nothing.** SDL's own pump already drains the run loop and
  the thread's message queue, which is what services a `WKWebView` and a
  WebView2 `HWND` created on that thread. `pump` is a no-op that exists so the
  caller has one shape.
- **Linux: `g_main_context_iteration`.** SDL does not drive GLib's main context
  and nothing else will, so the shim iterates it, non-blocking, once per call.

One model, three platforms, and callbacks that arrive where widget code may run.

#### And the loop has to stay awake to do it

Draining GLib once per loop iteration is not enough on its own, and this was
found by reading `EventLoop` rather than by running it: an iteration parks in
`backend.pumpEvents` for the loop's **one-second heartbeat** whenever the
desktop is idle, because SDL has no events and does not know WebKit has any.
A page serviced once a second does not scroll, does not animate and barely
loads.

Nothing can wake the loop from GLib's side. The honest fix is a file descriptor
out of `g_main_context_get_poll_func` handed to SDL to wait on, and SDL has no
API for waiting on somebody else's descriptor.

So while a page is open — and **only** while one is open — the loop's wait is
capped at 8 ms. That is a real cost, a loop waking 125 times a second with
nothing else to do, and it is confined to the lifetime of a page precisely so
that it is invisible to every application that never opens one. Zero would be
the obvious alternative and is a busy loop.

The counter that answers "is a page open" is read *before* anything that could
load the library, so an application with no page neither pays the wakeups nor
maps GTK.

### On Linux the toolkit is a GTK 3 process, because the tray makes it one

Found by pressing the showcase's own button, which took the window down with a
`SIGSEGV` in `gtk_init_check`:

```
GLib-GObject-CRITICAL: cannot register existing type 'GdkDisplayManager'
GLib-CRITICAL: g_once_init_leave_pointer: assertion 'result != 0' failed
C  [libgtk-4.so.1+0x5629a4]  gdk_display_manager_get_default_display+0x4
```

GObject's type registry is **process-global**. `gdk-3` and `gdk-4` both register
a type named `GdkDisplayManager`, so whichever initialises second gets 0 back
from `g_type_register_static`, trips the `g_once_init_leave_pointer` assertion,
and dereferences NULL. Two GTK majors in one process is not a conflict to manage;
it is a crash.

And a Goldberry process is **already** a GTK 3 process whenever it shows a tray
icon: SDL's Linux tray is libayatana-appindicator, which links `libgtk-3`. The
crash log has both, and the chain is exact —

```
SDL tray  →  libayatana-appindicator3  →  libgtk-3
web-view  →  libwebkitgtk-6.0          →  libgtk-4
```

`tray-icon` is an ordinary member of the catalog ([ADR-0191](0191-a-tray-is-a-menu-somebody-else-draws.md)),
so this is not an exotic combination — it is the showcase, and it would be most
applications that use both features.

**So the Linux build links `webkit2gtk-4.1`**, which is WebKitGTK on GTK 3,
sharing the one `libgtk-3` the tray already mapped. That is the reverse of
webview's own CMake preference and of every upstream recommendation, and the
reason is written where the choice is made.

**And the shim refuses rather than trusting that.** Before creating a page it
asks `dlopen(…, RTLD_NOLOAD)` whether the *other* GTK major is already in the
process, and returns NULL if it is — which Java already reports as "the engine
would not start". A build that ends up on 6.0 because 4.1 was unavailable then
declines to open a page in a process that has a tray, instead of killing it. It
costs one `dlopen` that cannot itself load anything, and it is the difference
between a feature politely unavailable and a crash in somebody else's
application.

This is the sharpest edge in the whole decision, and it was invisible until the
button was pressed: the C probe and the FFM probe both opened pages happily,
because neither had a tray and therefore neither had GTK 3.

**Verified after the fix**, on the combination that crashed — a real `Host`, a
real tray up, and a page opened through `Host.webView` exactly as the showcase's
button does:

```
tray shown   = true          ← GTK 3 is in the process
capabilities = [… WEB_VIEW]
page opened  = true
page closed  = true
```

`libgoldberry-webview.so` now carries `NEEDED libgtk-3.so.0`, which is the one
the tray already mapped, and the two coexist because there is only one of them.

### What this couples, and what would uncouple it

`web-view` and `tray-icon` are now a **pair on Linux**: they must agree about
GTK, and the build is what makes them agree. That is a constraint this decision
accepts rather than solves, and it is worth naming because it will be the reason
something breaks later — a distribution that ships only `webkitgtk-6.0`, a
future SDL tray that moves to GTK 4, a third dependency that wants the other
major.

The thing that would sever it for good is running the page in a **separate
process**, where its GTK is nobody else's business. That is a real design and it
is not this one: it needs an IPC protocol for navigation and lifecycle, a
supervised child, and an answer for what happens when it dies. Worth reopening
if web view usage grows past "open the handbook".

## Consequences

### What this is not, said where a reader looks

Written on `WebPage` itself rather than left to be discovered:

- **A page cannot be put in a layout, and there is no `web-view` node in
  markup.** There is nothing for a `row` to size and nothing for KDL to place.
  An application that wants a page beside its widgets opens a window and
  arranges the two, which is what the platform lets it do.
- **Nothing in a frame can cover a page, and nothing in a page can cover a
  frame** beyond what window stacking already does. A `dialog` is modal to the
  application's window and not to the page's.
- **No golden image can see a page.** Every pixel of it belongs to WebKit. This
  is the second entry in the catalog, after `tray-icon`, that `docs/testing.md`
  §14's rule cannot reach — and for the identical reason.
- **A page that is open holds the loop up.** `Goldberry.run()` returns when the
  last window closes, and a page's window is not one of the toolkit's, so an
  application that opens a page and closes its own window must close the page
  too. `WebViews.open` returns something `AutoCloseable` for that reason.

### Where it is unverified

**macOS and Windows are unverified**, in the tray's sense and recorded the same
way: the design says SDL's pump services both, and nothing here has run it. The
Linux leg is the one that was built and exercised.

## Alternatives

**Servo, as the parked entry proposed.** Still Rust-only against an unstable
API, and still a `cdylib` this project would own. The reason to revisit was
never that Servo improved; it is that the goal — a page on screen — had a second
route that needed no engine at all.

**CEF off-screen rendering.** The documented escape hatch, and it stays one. CEF
*does* render into a buffer, which is the one property that would have made a
real `web-view` box possible — but it is a hundred-megabyte vendored binary per
platform, which is ADR-0190's quarantine case in its purest form, and it would
be an eleventh module rather than anything in the catalog. An application that
needs Chromium in a box still embeds CEF itself.

**An embedded box, Wayland excepted.** Rejected above: the platform it fails on
is the default Linux session, and it would need
`ARCHITECTURE.md` §12's native-handle escape hatch — which §12 promises and
nothing has built — to be built first.

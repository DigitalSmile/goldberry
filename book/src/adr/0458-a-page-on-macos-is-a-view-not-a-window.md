# 458. A page on macOS is a view, not a window

Date: 2026-09-23

## Status

Accepted. Amends the macOS row of
[ADR-0442](0442-a-page-is-a-child-window-where-the-window-system-allows-one.md),
which said `addSubview:` and marked it *unverified*. It was never written: the
shim's `goldberry_webview_create_embedded` answered NULL on every platform
except GTK on X11.

## Context

The showcase's Web view tab on macOS showed no page. What it showed instead was
the Wayland notice — *"This session cannot put a web page inside a window…
Running under X11 or XWayland is what makes it work"* — and the log said the
same thing twice:

```
Webview - no page could be opened inside the window: this session does not permit
          embedding. Wayland has no cross-client surface embedding; running on X11
          or XWayland does
WebView - web-view: no page could be put inside the window. Embedding needs a
          native window handle to reparent into, which X11, Win32 and Cocoa give…
```

Two defects, one symptom:

1. **Nothing on macOS could embed.** `libgoldberry-webview.dylib` was built and
   loaded, `Capability.WEB_VIEW` was reported, SDL handed over an `NSWindow*` —
   and the shim's `#else` branch returned NULL with a comment saying the call
   had not been written because it could not be run.
2. **Every refusal was described as Wayland.** Both messages were written on a
   Linux desktop, and a Mac, a Windows machine and a build with no library at
   all were all told to use XWayland.

## Decision

### The page is the engine's `WKWebView`, added to SDL's content view

macOS has no reparenting of windows and does not need it: a view is the unit of
composition there. So an embedded page is a **subview** of the content view of
the `NSWindow` SDL made, placed with `setFrame:`.

**Not `webview_create(debug, sdlWindow)`**, although webview.h accepts a parent
window on Cocoa. What it does with one is `[window setContentView:webview]` — it
*replaces* the content view, and SDL draws and receives events through that view.
Handing SDL's window over would put a page where the whole Goldberry frame was.

So the engine is given a **holder**: a borderless `NSWindow` that is never
ordered front, whose only job is to be somewhere webview.h can put the view it
makes. The view is taken out of the holder and added to SDL's content view. The
holder lives until the page is destroyed, because the engine keeps it as
`m_window` and reads it in its destructor. Destroying detaches the view from
SDL's window first — the content view holds a reference the engine does not
know about — then destroys the engine, then releases the holder.

It is written in C++ against the Objective-C runtime, the way webview.h itself
is, so `goldberry_webview.cc` stays one translation unit with no `.mm` beside it
and no change to the build.

### Placing it takes three conversions and one decision

`goldberry_webview_set_bounds` receives the same numbers on every platform: the
parent window's **device pixels**, origin **top left**. AppKit disagrees on
all of it:

| Java hands over | AppKit wants | Done by |
|---|---|---|
| device pixels | points | dividing by the window's `backingScaleFactor` |
| origin at the top | origin at the bottom, unless the view is flipped | measuring y from the other edge |
| a box that moves only when the widget's box does | a frame that stays put when the window grows | an autoresizing mask whose *bottom* margin stretches |

The last one matters because the widget re-places a page only when its own
logical box changes. Making a window taller moves an unflipped view's top edge
without changing that box, so without the mask the page would drift down until
something else caused a re-layout.

The decision: **a parked page is hidden as well as moved.** Parking
([ADR-0444](0444-a-page-stands-aside-for-a-modal.md),
[ADR-0445](0445-a-page-is-not-shown-before-it-can-be-seen.md)) moves the page
far past the parent's top-left corner, and on X11 the parent clips a child
there. An `NSView` does not reliably clip its subviews — `clipsToBounds` has
defaulted to `NO` since macOS 14 — and a frame above the content view lies in
the title bar. So a frame that does not intersect the content view is also
`setHidden:`. Hiding keeps the document, its layout and its scroll position,
which is everything parking by moving was for.

The page is also kept **on top** of SDL's own subviews on every placement. SDL
keeps a Metal view inside its content view, and one made after the page would
draw the frame over it. Checking costs two messages.

### Load state comes from `loading` and `estimatedProgress`

The same two facts the GTK branch reads, under WKWebView's names, with the
page's `URL` as the tie-breaker for a document that loaded without a network —
`loadHTMLString:` ends on `about:blank`. With that answering, the spinner of
ADR-0445 works on macOS: the page opens parked and appears when it has loaded.

### And a refusal says what applies here

`Webview.embeddingRefused(kind)` words the natives log line by the parent
handle's kind — X11, Cocoa or Win32 — because that is what the shim branched
on. The widget's own notice is `WebViewRefusal`, chosen from the two facts the
widget can see: whether the library is loaded, and `os.name`. No library comes
first on every platform, because nothing below it was asked. Then:

| Platform | What the box says |
|---|---|
| Linux | Wayland cannot embed; run under X11 or XWayland |
| macOS | the engine did not start inside this window; the log says why |
| Windows | embedding is not implemented on this platform yet |

## Alternatives considered

- **Pass SDL's window to `webview_create` and put SDL's content view back
  afterwards.** Two `setContentView:` swaps under SDL's feet, at a moment when
  its Metal layer and tracking areas are attached to that view. It might work;
  nothing about it would be obviously correct, and the holder costs one
  invisible window per page.
- **A `.mm` translation unit using AppKit directly.** More readable, and a
  second compiler mode, a second file and a CMake change for about eighty
  lines. webview.h already shows how to do it from C++.
- **Rely on clipping for parking.** Would need `setClipsToBounds:YES` on SDL's
  content view, which is SDL's to configure, and would still leave the frame
  under the title bar on a window with a full-size content view.
- **Park at 1×1.** Rejected for ADR-0444's reason: it reflows the document and
  loses the scroll position.

## Consequences

- **A page shows on macOS.** Built on macos-aarch64 and run in the showcase with
  `-Pgoldberry.example.screen=web`: the page opened inside the window at scale
  2.0, bound both callbacks, raised the spinner, took it down about three
  seconds later when GitHub had loaded, and was destroyed cleanly when the
  window closed.
- **Keystrokes reach both.** SDL3's `NSApplication` subclass handles key events
  in `Cocoa_DispatchEvent` *and* passes them on to the window, which delivers
  them to the focused `WKWebView`. So typing into a page works, and Goldberry's
  own key handling sees the same keys. On X11 it does not, because the page's
  own X window has the focus. A shortcut that fires while the user types into a
  page is the visible form of this. Not solved here.
- **Clicks reach only the page.** The `WKWebView` is the hit-tested view and
  does not pass a click to its superview, so SDL — and so the pointer router —
  never sees it, which is what ADR-0442 promised. Mouse *motion* still reaches
  SDL, so a hover over the page's area is a hover over the canvas under it.
  That is harmless, since the canvas has no hover.
- **Windows is now the only platform that says "not implemented".** Its row in
  ADR-0442 is still unwritten, and the shim's Win32 branch still answers NULL —
  but now the box says so, instead of sending a Windows user to XWayland.
- **The ABI does not change.** Every exported function keeps its shape; only
  what four of them do on macOS is new. A stale library is therefore not caught
  by the ABI check, and it goes on showing the old refusal until it is rebuilt.

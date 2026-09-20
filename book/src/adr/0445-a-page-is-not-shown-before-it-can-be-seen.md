# 445. A page is not shown before it can be seen

Date: 2026-09-20

## Status

Accepted. Builds on [ADR-0444](0444-a-page-stands-aside-for-a-modal.md), whose
parking mechanism this reuses for a second reason.

## Context

Open the showcase's web tab and the page is a **white rectangle** for as long as
the network takes, and then the page appears in it. Nothing says it is loading,
because nothing can.

The white is WebKit's default document background.
`goldberry_webview_create_embedded` maps the engine's window and reparents it
into the application's inside one call — that is the order [ADR-0442] requires,
because GDK picks its backend during `gtk_init` and a page created before the
backend is settled gets a `wl_surface` that cannot be reparented. So by the time
Java has a handle, a window is already on screen, over the widget's box, showing
an empty document.

The obvious fix is not available. A `spinner` drawn over the page would be drawn
*underneath* it: a page is a platform window above the frame, which is the whole
of what [ADR-0441] and ADR-0442 established and what ADR-0444 had to work around
for `dialog`. There is no raster to composite into.

What ADR-0444 did leave behind is a mechanism: **parking**, which moves the
page's child window past its parent's corner where the parent clips every pixel
of it, without resizing it and therefore without reflowing the document. A
parked page is invisible and the box it will occupy is ordinary frame that
Goldberry paints.

## Decision

**A page stays parked until it has something to show, and a `spinner` is drawn
in the box it will occupy.**

Three parts.

**The page is opened parked**, not parked on the next frame. `open` hands
`Host.embeddedWebView` the parked rectangle rather than the box, because the
shim maps and reparents inside that call — a page created over its box is
visible, and empty, before anything in the widget could move it. One frame of
white is still a flash.

**The widget asks, once a frame, whether the page is ready.**
`goldberry_webview_load_state` answers -1 unknown, 0 not started, 1 loading, 2
finished, and the widget is already calling the shim once a frame from its
painter to keep the page over its box, so the question rides along.

**A stack, so there is somewhere to draw.** The surface is the first child and
stays in flow, so it still sizes the box and still carries the id
`Host.anchor` resolves; the spinner is absolute and covers nothing.

### Polled, not signalled

WebKit has a `load-changed` signal and it is not bound. Binding it means an
upcall stub whose lifetime outlives the Java object that owns it, a callback
arriving from GLib's thread into a widget confined to the UI thread, and a
registration to unwind when the page closes. What it would buy over a poll is
nothing: the consumer is a painter that runs every frame anyway, and one `int`
per frame is not a cost anybody can measure against a frame budget of 16 ms.

### The two facts are joined in C

`webkit_web_view_is_loading` and `webkit_web_view_get_estimated_load_progress`
are combined in the shim rather than in Java, because the interesting state is
the one neither reports alone. A page that has been created and never navigated
— which is every page between `create_embedded` and the `navigate` after it — is
not loading and has made no progress, and it is exactly as unready as one still
fetching. Java would otherwise have to know that `estimated-load-progress` is 0
before the first load and stays at 1 after the last, which is WebKit's business.

### Unknown means show it

`WebLoad.isReady()` answers true for `UNKNOWN`, and that is the load-bearing
default. A build whose engine will not say — Windows and macOS, where the
embedding itself is unwritten — must behave as every build did before there was
a question to ask. A page that never appears at all is a far worse failure than
a white flash.

### The flag is read in `build`, so only `setState` may write it

Stated because the first version of this got it wrong, and the way it failed is
worth keeping. `open` set `loading = true` directly — the page had just been
created parked, and the flag was true, so the code read correctly. But `build`
had already run for that frame and nothing asked it to run again, so no spinner
was ever put in the tree; and the next frame's `loadingIs(true)` found the flag
*already* true and skipped the `setState` that was the only thing left to do.

Both reported symptoms followed from that one line. No spinner, ever — and then,
because a `spinner` is what asks for frames for ever, no frames: the loop went
idle with the page still parked, and the page appeared only when something
unrelated caused a repaint. Moving the pointer was what did it.

So there are two fields. `loading` is what `build` reads and changes only
through `setState`; `loadingWanted` is what has been asked for, because the
`setState` is requested from inside a paint and runs on the next turn of the
loop — every frame in between would otherwise queue the same change again.

### And the poll asks for its own next frame

`Host.repaint()` while the page is held off, rather than relying on the
spinner's own animation to keep the loop turning. A `spinner` does ask for
frames for ever, so the loop would in fact stay awake once one existed — but
that makes this widget's correctness depend on which indicator it happens to
draw, and it does not solve the first frame, which is a deadlock either way: no
frame, no `setState`, no spinner, no frames.

The page's progress changes with no tree change to notice it. Polling something
means asking for the frame that looks again, and it stops the moment the page is
ready, which is what keeps an idle application idle.

## Alternatives considered

**Set the page's background colour** with `webkit_web_view_set_background_color`,
so the flash is the theme's surface rather than white. Cheap, and it treats the
symptom: the box is then a *coloured* rectangle with nothing in it and no
indication that anything is happening. It is not exclusive with this decision
and may still be worth doing; it is not a substitute for it.

**Draw the spinner over the page.** Impossible, and it is the premise of
ADR-0441 rather than a thing that could be tried.

**Hide the page with `gtk_widget_hide`** instead of parking it. The same
alternative ADR-0444 weighed and left as the designed fallback, for the same
reasons: it costs an export and an ABI bump of its own, and whether
`gtk_widget_show` restores an already-reparented window into the same X parent
is the class of question that produced an empty rectangle the first time.
Parking is already built and already tested.

**Wait for `LOADING` before showing a spinner**, rather than treating `IDLE` as
unready. That is a spinner that appears a frame or two *after* the white it was
meant to replace, which is the defect with an extra step.

## Consequences

**No white.** The page is never on screen without content. What is on screen
instead is an empty box and a spinner, which is what a user reads as "this is
coming".

**The page loads while parked.** A clipped-out child window is still mapped, so
WebKit fetches, parses and lays out exactly as before — the page is finished when
it appears rather than appearing and then finishing. Whether it also keeps
*painting* while clipped is the open question ADR-0444 already records, and this
decision makes it apply for longer: a slow page is now parked for the whole of
its load rather than only for the length of a dialog.

**A page that never loads shows a spinner for ever.** WebKit substitutes its own
error document for a page that fails, which ends the load and shows the error —
so this is the case where the *server* accepts the connection and never answers.
There is no timeout, and adding one would mean choosing a number on every
application's behalf.

**`stack` leaves `WidgetParityTest`'s unstyled list**, the way `canvas` did for
ADR-0442 and for the same reason: the toolkit now builds one, so it needs one
class-scoped rule to lay it out. A bare `stack` still gets nothing.

**Webview ABI 5.** One new export. A stale `libgoldberry-webview` is refused with
the message it already had rather than called into.

**Still no automated end-to-end test**, for ADR-0444's reason: no golden image
can contain a page. What is tested is the arithmetic, that `IDLE` and `LOADING`
are both unready, that `UNKNOWN` is not, and that `stage` puts a spinner in the
tree exactly when it is loading. What is **not** testable is the half that
actually broke — that the flag reaches `build` on the right frame — because that
is a fact about frames and there is no frame in a test.

So the widget says so out loud instead: raising and taking down the indicator
are logged at `debug`, which is how the fix above was confirmed and is the only
evidence a running application can give.

```text
22:49:17.930 INFO  web-view: a page is open inside the window over ...
22:49:17.953 DEBUG web-view "web-page": raising the loading spinner
22:49:20.659 DEBUG web-view "web-page": taking down the loading spinner
22:49:20.660 TRACE web-view "web-page" placed over ...
```

[ADR-0441]: 0441-a-web-page-is-a-window-not-a-box.md
[ADR-0442]: 0442-a-page-is-a-child-window-where-the-window-system-allows-one.md

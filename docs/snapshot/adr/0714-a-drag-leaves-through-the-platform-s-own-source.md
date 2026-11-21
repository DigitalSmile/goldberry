# ADR-0714: A drag leaves through the platform's own drag source

- **Status:** Accepted, as the plan for W3.9 of the downstream gaps plan
  (Tessera's T-20, owner's D15). `docs/drag-out.md` holds the phases.
- **Date:** 2026-11-21
- **Relates to:** ADR-0003 and ADR-0041 (SDL as the only desktop backend; this
  is a bounded exception), ADR-0711 (global shortcuts, the same kind of
  exception), ADR-0441 (native window handles)

## Context

Tessera drags Board frames out as pictures and Chat files out as files (T-20).
In-app drag is the pointer router's: a payload, `DropTarget`s, `:drag-over`,
a ghost. Nothing outside the window can receive it. Dropping in from outside
works through SDL's `DROP_*` events.

SDL 3.4.18 is a drop target on all four platforms and a drag source on none.
It does give what a source needs: `SDL_SetX11EventHook` on X11, OLE already
initialised on Windows, the `wl_display` and the window's `wl_surface` on
Wayland, and the `NSWindow` on macOS. Windows' `DoDragDrop` is a modal loop.

## Decision

The owner took the plan's decisions with one change, D1:

1. **D1:** a draggable that has an offer becomes the platform's drag at the
   4 px threshold, as Finder and Explorer do, not when it leaves the window.
   A draggable without one, and every keyboard drag, stays in-app.
2. **D2:** over the toolkit's own windows the drag is still an in-app drag.
   The drag source reports where the pointer is, and those reports drive the
   router's targets with the original payload. A release over one of them
   is an in-app `Drop`, and the `FileDrop` SDL would raise for the
   application's own offer is swallowed. The reports come from the source,
   not from SDL's `DROP_POSITION`, which SDL sends only for the types its
   targets accept.
3. **D3:** `Attributes.dragOut(Supplier<DragOffer>)`, asked once when the drag
   starts, and `onDragOutEnd(Consumer<DragOutcome>)`.
4. **D4:** an offer carries existing files, text, a URL, an image and bytes of
   any named MIME type, each asked for when a target takes it. An image is
   also offered as a temporary PNG file for targets that take only files.
   Files written at the drop (virtual files, file promises, XDS) are not
   taken.
5. **D5:** on Windows, each `IDropSource::GiveFeedback` runs one guarded turn
   of the toolkit's loop, so frames and in-window feedback go on during
   `DoDragDrop`. A second drag or a modal dialog is refused there, and
   closing a window is deferred until the drag ends.
6. **D6:** the picture is the toolkit's own ghost handed to the OS: the
   source's rectangle cut from the window's last presented frame and faded as
   the in-app ghost is. An offer may replace it.
7. **D7:** copy by default; an offer may also allow move and link. The source
   hears one `DragOutcome`: `Delivered(operation)` when another application
   took it (copy, move, link, or null where the platform does not say),
   `Landed(accepted)` when it was let go over one of the application's own
   windows, or `Cancelled`. The toolkit never deletes anything on a move.
8. **D8:** this record is the exception to ADR-0003/0041. It covers the drag
   source and nothing else. The code lives in `natives.desktop.dragout` (and a
   minimal libwayland-client binding in `natives.wayland`), under the SDL
   backend, behind `BackendWindow.startDrag`, which refuses by default. The
   exception is withdrawn if SDL gains a drag source.

## Consequences

- `BackendWindow` grows one method, and the headless backend a scripted
  session for tests. A backend that refuses keeps today's behaviour exactly.
- X11 needs `SDL_SetX11EventHook` exported, so the native ABI goes from 26 to
  27 in phase 2.
- Each platform adds upcall shapes. Each is registered for the native image
  and counted by `ForeignSurfaceTest`.
- Windows and macOS may land written but not run, as W3.8's did. The gaps
  answer and this record say so until a CI lane drives them.
- Draggables with an offer lose the toolkit's own ghost while the pointer is
  in the window: the OS draws the picture from the threshold on.

# ADR-0717: A Windows drag runs inside DoDragDrop, and the loop turns there

- **Status:** Accepted, as phase 4 of `docs/drag-out.md` (W3.9, Tessera's
  T-20). Implements ADR-0714's D4, D5 and D6 on Windows, with the departures
  below. Unverified on Windows until the Windows lane and a hand check run it.
- **Date:** 2026-11-21
- **Relates to:** ADR-0714 (the drag out's decisions), ADR-0716 (the Wayland
  drag), ADR-0003 and ADR-0041 (SDL as the only desktop backend)

## Context

On Windows a drag out is OLE's `DoDragDrop(IDataObject, IDropSource, …)`.
SDL calls `OleInitialize` on its video thread and registers an
`IDropTarget` per window, but has no drag source. `DoDragDrop` is a modal
loop that returns only when the drag is over, so the toolkit's own loop does
not run while it lasts. ADR-0714's D5 (a) chose to run one turn of the
loop from inside it.

The toolkit has no COM objects of its own yet. `Com` (W2.12) calls through
other people's vtables, and the drag needs three that are implemented in
Java: `IDataObject`, `IEnumFORMATETC` and `IDropSource`.

## Decision

1. **COM objects written in Java.** `natives.desktop.windows.ComServer`: a
   block whose first word points at a vtable of upcall stubs, found again by
   its address in a live map, and reference-counted. `IUnknown` is shared.
   Stubs are made once per kind and kept for the process, because the last
   `Release` runs inside one. The pointer handed out is unscoped, so a
   downcall that carries it does not hold the block's arena open across
   that `Release`. Every stub's target catches everything and answers an
   `HRESULT`.
2. **The data object** (`natives.desktop.dragout.ole.OleDataObject`)
   answers each offered format in `HGLOBAL`, made on request on the UI
   thread. It also keeps what `SetData` puts on it (global memory copied,
   streams by reference), because the shell's drag image helper stores the
   picture there and a target's drop helper reads it back. `GlobalMemory` is
   an interface, so the whole object is called through its vtable by tests
   on every platform.
3. **Its own `IEnumFORMATETC`, not `SHCreateStdEnumFmtEtc`.** The plan named
   shell32's. Writing it costs one more small object and makes the
   enumeration testable away from Windows, with the rest of the object.
4. **The formats** (`render.backend.sdl3.WindowsDragData`): `CF_HDROP`, the
   first link as `UniformResourceLocatorW`, text (or the links, one per
   line) as `CF_UNICODETEXT` with CR LF, the image as the registered `PNG`
   and as `CF_DIBV5` (bottom-up, `BI_BITFIELDS`, straight alpha, sRGB), and
   each MIME type of the application's own registered under its own name. As
   on X11 and Wayland, the image is also written to a PNG file, so Explorer,
   which takes only files, can take it.
5. **The picture** through `CLSID_DragDropHelper`'s
   `IDragSourceHelper::InitializeFromBitmap`: the ghost as a top-down 32-bit
   DIB section, premultiplied, with no colour key. This is best effort: a
   refusal leaves OLE's cursors and the drag works the same.
6. **Where the pointer is.** OLE tells a source nothing, so the source asks
   on each of OLE's questions: `GetCursorPos`, `WindowFromPoint`, the
   top-level ancestor, `ScreenToClient`. Over one of the application's own
   windows it reports `over`, and leaving them reports `outside`. Where the
   button came up decides the ending first. A release over the
   application's own window lands there in-app, **whatever SDL's target
   answered**: for an offer of no files or text, SDL's target declines it
   and OLE reports `DRAGDROP_S_CANCEL`. Otherwise `DRAGDROP_S_DROP` is
   `Delivered` with the effect the target reported, and the rest is
   `Cancelled`. Escape always cancels.
7. **The turn (D5 (a)).** Each `QueryContinueDrag` (while the button is
   held) and each `GiveFeedback` runs one turn: the reports queued since the
   last turn, `DragSession.whileHeld()` (which the runtime answers with
   `EventLoop.turnWhileHeld()`: the UI queue, the due timers, the UI queue
   again), then the frames that are due, into the sink of the pump the drag
   started in. A turn never nests. It shares the resize watch's guard, so
   neither draws inside the other.
8. **SDL's queue is not read during the drag.** `SDL_PollEvent` pumps the
   thread's Windows messages, and OLE's modal loop is pumping them already.
   `SDL_PeepEvents` would read without pumping, but it is not exported, and
   it would cost a native ABI bump for events that matter little here: OLE
   has the pointer captured and handles Escape itself. What SDL queues waits
   for the drag to end. The drag then takes every `DROP_*` event until the
   pump after its ending has read them, which swallows the drop SDL's own
   target made of the application's own offer.

Rejected:

- Reading SDL's queue with `SDL_PollEvent` inside the turn: this re-enters
  the message loop OLE is running, and can hand OLE's own tracking messages
  to it out of order.
- A thread of its own for `DoDragDrop`: OLE's drag needs the thread whose
  windows it drags from, and SDL's windows live on the UI thread.
- Taking SDL's `DROP_POSITION` as the position, as on Wayland: SDL's target
  takes only files and text, and would leave a PNG-only offer with no
  in-app feedback. Asking for the pointer works whatever the offer carries.

## Consequences

- Six more upcall shapes (twenty-five in all), each registered for the
  native image through `ForeignSurface`'s owners.
- Nothing has run on Windows yet. The objects are exercised through their
  vtables on every platform. On the Windows lane, `OleClipboardRoundTripTest`
  puts the data object on OLE's clipboard, reads it back through what
  `OleGetClipboard` answers, and hands it a picture through the shell's
  helper. `DoDragDrop` itself needs a held button, and is a hand check:
  a drag into Explorer, a browser, Word, and onto an in-app target.
- SDL never hears the release, since OLE has the pointer captured. SDL
  learns the button is up from the next mouse message, which carries the
  button state, and the router's own rule, that a press the platform
  finished swallows its release, covers what arrives then. The hand check
  confirms it.
- `CF_DIBV5` with straight alpha is the reading of the SDK's documentation
  that most readers share. A target that premultiplies again shows a
  translucent image a little darker. The PNG format and the file are offered
  first.
- Virtual files (`CFSTR_FILEDESCRIPTORW`) stay out, as ADR-0714's D4
  decided.

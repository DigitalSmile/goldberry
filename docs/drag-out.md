# Drag out to the OS — implementation plan

Written 2026-11-21, before any code. This is W3.9 of
[`gaps/goldberry-gaps-plan.md`](gaps/goldberry-gaps-plan.md), Tessera's T-20:
"Board frames as images, files from Chat" dragged out of the window into a
file manager, a mail client or a browser. The owner chose D15 on 2026-11-08:
drag-out on all four platforms, behind `BackendWindow.startDrag(DragOffer)`,
which refuses by default. The plan said this document comes first, because
SDL has no drag source and each platform's path is code of the toolkit's own.
It follows `zero-copy-tiles.md`'s shape: what was found, the decisions with a
status, phases with exit criteria, risks, and a log.

The owner answered D1–D8 on 2026-11-21. ADR-0714, parked in
`docs/snapshot/adr/`, records all eight. Phase 1 is the first to be built.

## 1. Where things stand

Read from the tree on 2026-11-21 (master at `b3752f37`) and from the SDL
3.4.18 sources the superbuild unpacks into `natives/.deps/linux-x64/sdl3-src`.

| Area | State |
|------|-------|
| In-app drag | `PointerRouter` owns it. A press on an element whose `Attributes.draggable()` payload is non-null arms it, `DRAG_THRESHOLD` (4 px) turns it into a `Drag`, and every move runs `aimAt`, which walks up from the element under the pointer to the first `DropTarget` that `accepts` the payload. `:drag-over`, `whileOver`, `onLeave` and `onDrop` follow from that. The keyboard drag (Space, arrows, Enter) is the same machine. `Dragging` is the snapshot the frame reads; `frame.DragGhost` paints the source's own box, faded, at the pointer |
| Leaving the window | `pointerExited` keeps the capture and the drag. A drag that leaves the window and comes back is one gesture, and a release outside is a release over nothing: the drag is put back and nothing is dropped. **Nothing outside the window can receive it** |
| Dropping in from outside | Works. SDL's `DROP_BEGIN`/`DROP_POSITION`/`DROP_FILE`/`DROP_TEXT`/`DROP_COMPLETE` become `Window.onFileDrop`/`onTextDrop` (`input.drop.FileDrop`, `TextDrop`), raised once per gesture at `DROP_COMPLETE`. `DROP_POSITION` is read by `Sdl3Backend` and carries the pointer while an outside drag is over the window |
| The data model next door | `render.clipboard.Clipboard.write(Map<String, byte[]>)` offers bytes per MIME type; `UriList` reads and writes `text/uri-list`; `SdlClipboard` keeps each offer in an arena until SDL's cleanup callback. A drag's data is the same shape: types offered up front, bytes asked for when the target takes them |
| Native handles | `SdlVideo.nativeHandle` gives `NativeWindowHandle` for X11 (`SDL.window.x11.window`), Win32 (`SDL.window.win32.hwnd`) and Cocoa (`SDL.window.cocoa.window`); `SDL.window.x11.display` is read too. **Wayland answers empty on purpose** (no embedding to use it for); `SDL.window.wayland.display` and `SDL.window.wayland.surface` exist in SDL's header and are not read |
| Platform helpers already written | `natives.desktop.windows.Com` (W2.12: calling through vtables, `CoInitializeEx`), `natives.desktop.macos.ObjC` (messages, `defineClass` with methods, protocols, autorelease pools, blocks), `natives.desktop.shortcuts.XlibHotkeys.Xlib` (W3.8: a bound libX11 with an error handler), `natives.x11.XlibCompositing`, `natives.desktop.dbus` |
| Boundaries | `BoundaryTest` keeps `java.lang.foreign` inside `:natives`. Every new upcall shape is counted by `ForeignSurfaceTest` (sixteen today) because the native image registers each one |

What SDL 3.4.18 does, read from its sources:

| Platform | What SDL does | What it leaves to the toolkit |
|----------|---------------|-------------------------------|
| Windows (`SDL_windowsvideo.c`, `SDL_windowswindow.c`) | Calls `OleInitialize` on the video thread, so it is an STA with OLE up, and `RegisterDragDrop`s an `IDropTarget` per window (falling back to `DragAcceptFiles` when OLE will not start) | No `DoDragDrop` anywhere. `DoDragDrop` is a **modal loop**: it returns when the drag ends, and SDL's event queue is not pumped while it runs |
| macOS (`cocoa/*.m`) | Registers its view for dragged types (it is a drop target) | No `beginDraggingSessionWithItems:event:source:`. The session is not modal; AppKit runs it from the ordinary event loop and tells the source what happens through `NSDraggingSource` |
| X11 (`x11/SDL_x11events.c`) | Is an XDND *target*: `XdndAware` on each window, `XdndEnter`/`Position`/`Drop` handled on its own `Display`. **`SDL_SetX11EventHook`** (in `SDL_system.h`, since 3.2) hands an application every `XEvent` before SDL sees it, and SDL skips the event when the hook returns false | No XDND source. During a drag SDL holds the implicit pointer grab from the press, so the motion and the release keep arriving at SDL's window, which the hook can read |
| Wayland (`wayland/SDL_waylanddatamanager.c`, `SDL_waylandevents.c`) | Binds `wl_data_device_manager` and one `wl_data_device` per seat, as a target and as a clipboard owner; loads libwayland-client with `dlopen` (`SDL_WAYLAND_SHARED`) | No `wl_data_device.start_drag`. The press's serial, which `start_drag` needs, is not exposed. SDL publishes `wl_display` and the window's `wl_surface`, so a client of the toolkit's own can share the connection, on an event queue of its own |

`SDL_SetX11EventHook` and `SDL_SetWindowsMessageHook` are not in
`natives/src/main/cmake/exports/goldberry.symbols` yet. Adding the first is a
native ABI bump (26 → 27); the second is not needed by this plan.

## 2. What this plan delivers

A drag of a draggable that has an offer is the platform's own drag from the
4 px threshold on (D1 (b)). It carries files, text, a URL, an image or bytes
of any MIME type the application names. The OS draws the picture that follows
the pointer, and the source hears how the drag ended. Over the toolkit's own
windows it is still an in-app drag, with `:drag-over` and `whileOver`, and a
drop there delivers the original payload, not a `FileDrop` of the
application's own files (D2 (a)). A draggable with no offer, and every
keyboard drag, stays in-app as today.

```java
new Card(frame)
        .draggable(frame)
        .dragOut(() -> DragOffer.image(frame.png()).withFile(frame.exportedPath()))
        .onDragOutEnd(outcome -> log.info("frame left as {}", outcome));
```

Not in this plan: dragging **in** (already done); a drag that starts outside
the toolkit's windows; files that do not exist yet and are written at the drop
(D4 (b), not taken). A drag with an offer that is released over **another** of
the application's windows lands there as an in-app drop, since the session
names the window; a drag without one still stays inside its own window.

## 3. Decisions

### D1. When the OS drag starts (taken: (b))

- **(a) When an in-app drag leaves the window**, as the gaps plan says. Inside
  the window nothing changes: in-app targets keep `:drag-over`, `whileOver` and
  the keyboard drag. A payload with no `dragOut` behaves as today.
- (b) At the threshold, for any draggable with a `dragOut`. This is how
  Finder and Explorer behave. Every in-app target would then have to be driven
  by the OS's drag-over events instead of the router, on all four platforms,
  and the keyboard drag could never leave.
- (c) The application chooses per draggable, (a) or (b).

(a) keeps the in-app machine as the only one while the pointer is inside. The
cost is a short moment at the edge where the in-app ghost is dropped and the
OS's picture appears.

**Taken: (b)**, for draggables that have an offer. The in-app targets are not
given up: D2 (a) drives them from the drag source's own tracking. Draggables
without an offer and the keyboard drag stay as they are.

### D2. Over the toolkit's own windows (taken: (a))

- **(a) It is an in-app drag again.** While an OS drag of the toolkit's own is
  in flight, SDL's `DROP_BEGIN`/`DROP_POSITION` over one of its windows are
  routed into the router as drag moves with the **original payload**. In-app
  targets get `:drag-over` and `whileOver`, and `DROP_COMPLETE` lands as an
  in-app `Drop`. The `FileDrop`/`TextDrop` SDL would have raised for the
  toolkit's own offer is swallowed. On Windows this needs D5 (a), since SDL's
  queue is not pumped during `DoDragDrop` otherwise.

**Taken: (a)**, with D1 (b) it covers the whole drag inside the window. Its
moves are **read by the drag source**, not taken from SDL's `DROP_POSITION`,
because SDL's targets accept only files and text and would stay silent for an
offer of a PNG alone. Each platform's source knows where the pointer is: the
X11 hook's `MotionNotify`, `GetCursorPos` in `GiveFeedback`,
`draggingSession:movedToPoint:`, and the `enter`/`motion` the toolkit's own
`wl_data_device` hears over its own surfaces. The source reports "over this
window at this point" to the session. A release over one of the toolkit's
windows is an in-app drop, whatever SDL's target answered.
- (b) It is an outside drop like any other: the application's own window gets
  a `FileDrop` of the files it just offered.

### D3. How an application offers a drag (taken: (a))

- **(a) A lazy `dragOut(Supplier<DragOffer>)` next to `draggable`**, on
  `Attributes` (so any widget can have one), asked once when the drag leaves
  the window, plus `onDragOutEnd(Consumer<DragOutcome>)`. The payload stays what
  in-app targets see; the offer is only what the outside sees.
- (b) The payload implements an interface (`OffersOutside`) that answers the
  offer.
- (c) One converter per window, `Window.onDragOut(Function<Object,
  Optional<DragOffer>>)`, that sees every payload.

### D4. What an offer can carry (taken: (a))

- **(a) Existing files, text, a URL, an image and bytes of any named MIME
  type.** The data is asked for when the target takes it: each entry is a
  `Supplier<byte[]>`, called on the UI thread, as `SdlClipboard` is asked
  today. Every platform asks at the drop (X11 `SelectionRequest`, Wayland
  `wl_data_source.send`, `IDataObject::GetData`, an `NSPasteboardItem` data
  provider), so this costs nothing. An image is offered as `image/png`, and
  where the target takes only files (Explorer, most file managers), also as a
  PNG written to a temporary file when the drag leaves the window and deleted
  at exit.
- (b) (a) plus **files written at the drop**, for a Chat attachment that is not
  on disk yet: Windows virtual files (`CFSTR_FILEDESCRIPTORW` and
  `CFSTR_FILECONTENTS`), macOS `NSFilePromiseProvider`, X11 XDS, and nothing on
  Wayland, which has no equivalent. Another phase, and a fourth API shape
  for one platform's gap.
- (c) Files only.

### D5. Windows' modal loop (taken: (a))

`DoDragDrop` does not return until the drag ends. While it runs, the
application's windows get messages (OLE pumps them), but the toolkit's loop
does not run: no frame, no animation, no `DROP_POSITION` read.

- **(a) Pump from inside it.** `IDropSource::GiveFeedback` runs on every move,
  on the UI thread. It runs one turn of the toolkit's loop (events and a
  frame), reentrancy-guarded: no new drag, no modal dialog, no second
  `DoDragDrop`. D2 (a) and live animations work on Windows too.
- (b) Accept the freeze. The window shows its last frame until the drop, and
  D2 falls back to (b) on Windows only.

### D6. The picture that follows the pointer (taken: (a))

- **(a) The ghost the toolkit already paints** (`DragGhost`: the source's box,
  faded), rendered into pixels when the drag leaves the window and handed to
  the OS: `IDragSourceHelper::InitializeFromBitmap` on Windows,
  `NSDraggingItem.setDraggingFrame:contents:` on macOS, the icon surface of
  `start_drag` on Wayland (an shm buffer), and an override-redirect ARGB window
  of the toolkit's own that follows the pointer on X11. The offer may replace
  it with a picture of its own (`DragOffer.withImage(PixelBuffer, hotspot)`).
- (b) The application's picture only, no default.
- (c) No picture; the OS's default cursor badge.

### D7. Operations and the outcome (taken: (a))

- **(a) Copy by default. An offer may also allow move and link.** The source
  hears one `DragOutcome`: `Delivered(operation)` when another application
  took it, with the operation null where the platform does not say (Wayland
  before version 3 of the data device, some X11 targets); `Landed(accepted)`
  when it was let go over one of the application's own windows; or
  `Cancelled`. The toolkit never deletes a file on a move. The application
  decides.
- (b) Copy only, and the outcome is just dropped or cancelled.

### D8. The departure from ADR-0003/0041 (taken: (a))

ADR-0003 says no hand-written Win32, Cocoa or Wayland backend, and ADR-0041
keeps two backends. This adds platform code for **one capability** under the
SDL backend, as W3.8's `GlobalShortcuts` did (ADR-0711), not a backend.

- **(a) One ADR records the exception.** It is bounded to the drag source, lives
  in `natives.desktop.dragout`, and is withdrawn if SDL ever gains a drag
  source (libsdl-org/SDL has no issue for one today; the ADR names that as the
  thing to watch).
- (b) Amend ADR-0003 itself to allow "single capabilities SDL does not have".

## 4. Phases

Each phase has its own tests and lands on its own. Platform phases follow the
API phase in the order CI can verify them.

### Phase 1 — the API and the router

Core only, every platform, no native code.

- `input.drop`: `DragOffer` (entries by MIME type, files, the picture, the
  allowed operations), `DragOperation`, `DragOutcome` (sealed), and
  `DragOutSession`, which the backend reports to (entered the window, moved,
  left, ended).
- `Attributes.dragOut` and `onDragOutEnd` (D3), with builders on the widget
  base like `draggable`.
- `BackendWindow.startDrag(DragOffer, DragOutSession)`: a default that returns
  false (refused). `HeadlessWindow` gets a scripted implementation for tests.
- `PointerRouter`: a press on a draggable with an offer that crosses the
  threshold asks the backend window (D1 (b)). If the backend refuses, the drag
  is in-app as today. If it accepts, the router's drag is marked as the OS's:
  no in-app ghost (the OS draws the picture), and the session's reports drive
  it (D2 (a)). "Over this window at this point" runs `aimAt` and `whileOver`
  with the original payload, "left the window" runs `onLeave`, "released over
  this window" lands as an in-app `Drop`, and "ended outside" or "cancelled"
  puts it back and tells the source its `DragOutcome`.
- `Window`: the `FileDrop`/`TextDrop` SDL raises for the toolkit's own offer
  is swallowed while a session is in flight.
- The ghost into pixels (D6 (a)): the source's rectangle cut from the
  window's last presented frame (`BackendWindow.capture()`), faded to the
  in-app ghost's opacity, the hotspot where the press took hold
  (`render.dragout.DragPictures`). Cut rather than painted again, so it costs
  no layout; whatever was under rounded corners comes with it.

**Exit:** the router's tests over `HeadlessWindow`: leaving with and without an
offer, refusal, return and drop on an in-app target, cancel, Escape during a
session, a source unmounted mid-session, and the swallowed `FileDrop`.

### Phase 2 — X11

- `SDL_SetX11EventHook` exported (ABI 27) and bound.
- `natives.desktop.dragout.XdndSource`: owns `XdndSelection` on a small
  input-only window on SDL's own `Display`; on each `MotionNotify` (read in the
  hook during SDL's implicit grab) finds the top-level under the pointer with
  `XdndAware`, sends `XdndEnter`/`Position`/`Leave`/`Drop` (version 5), and reads
  `XdndStatus`/`XdndFinished`; answers `SelectionRequest` for each offered
  target, `INCR` for large data; sets the cursor through
  `XChangeActivePointerGrab`. The picture is an override-redirect ARGB window
  following the pointer.
- Drops on its own windows go to SDL's own target code as today, which is what
  D2 (a) reads.

**Exit:** a test with an XDND target of its own on a private `Xvfb` (as
`XlibHotkeysTest` starts one), driven with XTest: types offered, bytes
received per type, `INCR` for a 1 MB image, `XdndFinished` reported as the
outcome, Escape cancels. The CI lane has Xvfb; this machine does not, so these
tests skip here, as `XlibHotkeysTest` does.

### Phase 3 — Wayland

As built (2026-11-21; ADR-0716). The plan below this paragraph had the
toolkit bind a `wl_data_device` of its own, which mutter answers by unlinking
SDL's: dropping in and the clipboard would stop for the rest of the process.
So `start_drag` goes out on SDL's own data device instead.

- `goldberry_sdl_wayland.c`, compiled with SDL's private include directories
  and definitions: `goldberry_wayland_drag_origin` reads SDL's data device and
  `last_implicit_grab_serial` from SDL's seat list (ABI 28). A stub off
  Wayland.
- `natives.wayland`: a minimal binding of libwayland-client, opened by soname
  so it is the instance SDL loaded. `WaylandQueue` is an event queue of the
  toolkit's own on SDL's `wl_display` with its own registry;
  `WaylandArguments` marshals through `wl_proxy_marshal_array_flags`;
  `WaylandDispatcher` hears every event of every proxy through
  `wl_proxy_add_dispatcher`, one upcall shape; `Descriptors` makes the
  memfd for the icon and writes a target's pipe with `SIGPIPE` blocked.
- `natives.desktop.dragout.WaylandDragSource`: a `wl_data_source` with the
  offered types and actions (version 3 of the manager or refused), the icon
  surface (`WaylandIcon`, compositor bound at version 4 at most so `attach`
  may still carry the hotspot), `start_drag` on SDL's device, `send` written
  on a thread of the drag's own, the queue dispatched once a pump and the
  reports acted on after the dispatch returns.
- `:core`: `WaylandDragOut` and `WaylandDropTracker`. SDL's `DROP_*` events
  are the drag's while it lasts (`PlatformDragOut.takesDrop`): a position
  drives the router, and a "complete" marks a possible landing that a
  `target` before the drop clears and `dnd_finished` confirms.
- `SdlVideo.waylandDragOrigin`; `nativeHandle` stays empty on Wayland.

**What it does not do:** an offer SDL's target declines (no file, no text:
a PNG offered without its file, an application's own MIME type alone) gets
no in-app feedback over the application's windows and is cancelled when let
go there.

**Exit as met:** the objects against a real compositor
(`WaylandCompositorTest`: the registry, a data source with types and
actions, the icon in a shared-memory buffer at a whole and a refused scale,
no protocol error; on GNOME here and beside CI's headless weston),
`WaylandDragSourceTest` (the whole start, `start_drag` on SDL's device
included, from a hidden SDL window, with no protocol error),
`WaylandDragOriginTest` (the C side finds SDL's data device on a real
Wayland window, and nothing on X11), and the ordering rules
(`WaylandDropTrackerTest`, mutter's and weston's orders). `start_drag`
itself needs a press no test can make; the hand check on GNOME is below.

### Phase 4 — Windows

As built (2026-11-21; ADR-0717), written and not yet run on Windows. Where
it departs from the plan below this paragraph: `IEnumFORMATETC` is the
toolkit's own rather than `SHCreateStdEnumFmtEtc`'s, so that the whole data
object is called through its vtables by tests on any platform; the turn runs
from `QueryContinueDrag` as well as `GiveFeedback`, since OLE asks the first
on its timer even when the pointer is still; the turn does not read SDL's
queue, because `SDL_PollEvent` would pump Windows messages inside OLE's
modal loop; and a release over the application's own window lands in-app
whatever SDL's target answered.

- `natives.desktop.windows.ComServer` (a COM object in Java: a vtable of
  upcall stubs shared by a kind, `IUnknown` shared by all, objects found by
  address, the pointer unscoped so the last `Release` may free the block),
  `GlobalMemory` and `Win32GlobalMemory`.
- `natives.desktop.dragout.ole` (not exported): `OleDataObject` (offered
  formats made on request, and what `SetData` leaves, which is how the
  shell's drag image helper keeps the picture), `OleFormatEnumerator`,
  `OleDropSource`, `OleDragImage` (`IDragSourceHelper::InitializeFromBitmap`
  with a top-down DIB section), `OleCalls` (`OleInitialize`, `DoDragDrop`,
  `RegisterClipboardFormatW`, the pointer through `GetCursorPos`,
  `WindowFromPoint`, `GetAncestor` and `ScreenToClient`), `OleStructs`.
- `natives.desktop.dragout`: `OleDragSource` (the facade `:core` calls:
  `open`, then `run`, which returns when the drag is over), `OleDragTracker`
  (the pointer over own windows, the release, Escape, the guarded turn, and
  the ending), `OleFormat`.
- `:core`: `WindowsDragData` (`CF_HDROP`, `UniformResourceLocatorW`,
  `CF_UNICODETEXT` with CR LF, `PNG`, `CF_DIBV5` with straight alpha, own
  MIME types registered by name), `WindowsDragOut` (takes every `DROP_*`
  until the pump after its end, which swallows SDL's echo), `DragReports`
  (shared with X11), `Sdl3Backend.turnWhileHeld`, `DragSession.whileHeld`
  and `EventLoop.turnWhileHeld`.

The plan as written:

- `natives.desktop.dragout.OleDragSource`: `IDataObject` (`GetData`,
  `QueryGetData`, `EnumFormatEtc` through `SHCreateStdEnumFmtEtc`) and
  `IDropSource` (`QueryContinueDrag`, `GiveFeedback`) as vtables of upcalls
  over Java objects, reference-counted. `CF_HDROP` for files, `CF_UNICODETEXT`,
  `UniformResourceLocatorW`, `PNG` and `CF_DIBV5` for images, registered
  formats for the rest. The picture through `CLSID_DragDropHelper`'s
  `IDragSourceHelper`. `DoDragDrop` from the UI thread, where SDL's
  `OleInitialize` already ran.
- D5 (a): one loop turn from `GiveFeedback`, behind a reentrancy guard.

**Exit:** the data object tested in-process (each format read back through its
own vtable, `EnumFormatEtc` complete, references balanced). `DoDragDrop`
driven with `SendInput` on CI's Windows runner, which has an interactive
desktop, into a drop target of the test's own (`RegisterDragDrop` on a second
window). Written to pass there; if the runner's desktop refuses input, the
exit is the in-process half plus a manual check.

### Phase 5 — macOS

As built (2026-11-21; ADR-0718), written and not yet run on a Mac. Where
it departs from the plan below this paragraph: `ObjC` learnt to pass
`NSPoint`, `NSSize` and `NSRect` by value and to return an `NSPoint`;
the class adopts both `NSDraggingSource` and `NSPasteboardItemDataProvider`
(one pasteboard item for every non-file type, a file `NSURL` item per file);
`draggingSession:movedToPoint:` is the in-app position, through
`windowNumberAtPoint:` and `convertPointFromScreen:`; the current event's
type tells Escape from a release; and the source object stays until
`pasteboardFinishedWithDataProvider:`.

- `natives.desktop.macos.ObjC`: `Point`, `Size`, `Rect`, `sendPoint`, and
  `defineClass` over a list of protocols.
- `natives.desktop.dragout.cocoa` (not exported): `CocoaDraggingSource`
  (the class `GoldberryDraggingSource`, its five methods as stubs found by
  the object's address) and `CocoaDragItems` (the pasteboard item, the
  dragging items, the `NSImage`, the window at a point).
- `natives.desktop.dragout`: `CocoaDragSource` (the facade `:core` calls:
  `start` begins the session and returns), `CocoaDragTracker` (the position,
  the ending, the operation masks, the picture's frame), `CocoaDragListener`,
  `PasteboardFormat`.
- `:core`: `MacDragData` (`public.url`, `public.utf8-plain-text`,
  `public.png`, own MIME types), `MacDragOut` (reports through `later`, the
  vertical axis turned over), and `DropEchoes`, the "SDL's drop events are
  the drag's until the pump after its end" bookkeeping, shared with
  `WindowsDragOut`.

The plan as written:

- `natives.desktop.dragout.CocoaDragSource`: a `GoldberryDragSource` class
  defined through `ObjC.defineClass` with `NSDraggingSource`
  (`draggingSession:sourceOperationMaskForDraggingContext:`,
  `draggingSession:endedAtPoint:operation:`), `NSPasteboardItem`s with a data
  provider per type, `NSURL` file URLs for files, and
  `beginDraggingSessionWithItems:event:source:` on SDL's content view with
  `[NSApp currentEvent]`.

**Exit:** a test that builds the items and the source class and reads the
pasteboard types back without starting a session; the session itself is written
against Apple's documentation and marked unverified until CI's macOS lane can
drive it. Posting mouse events there needs accessibility rights the runner does
not grant.

### Phase 6 — the showcase and the guide

A card on the showcase's input screen, beside `DropCard`: a picture and a
file dragged out, the outcome printed. A section in the guide's input
chapter, parked in `docs/snapshot/` until release.

## 5. Risks

- **The serial on Wayland.** `start_drag` needs the serial of the press that
  began the implicit grab. *Answered in phase 3:* SDL keeps it as
  `last_implicit_grab_serial`, which libgoldberry reads (ADR-0716), so no
  early `wl_pointer` is needed.
- **SDL's pointer state after an OS drag.** On Wayland and macOS the platform
  takes the pointer, and SDL may never hear the release, leaving its own button
  state down. Phase 3 and 5 must check `SDL_GetMouseState` after a session
  and, if it is stuck, synthesise the release into the router (not into SDL).
  *On Wayland:* SDL raises every held button when its pointer hears `leave`,
  which the compositor sends as the drag takes the pointer, so the router
  hears the release at the drag's start and `dragOutFinished` finds the
  gesture already closed. *On macOS:* the router closes the gesture at the
  drag's end whether or not the release came; whether SDL's own state stays
  down, and drops the next click's press, is the hand check's to say.
- **Reentrancy on Windows.** A loop turn inside `GiveFeedback` runs
  application code inside a COM call inside `DoDragDrop`. The guard refuses
  anything that would nest: a second drag, a modal file dialog, closing the
  source window (deferred until `DoDragDrop` returns). *As built:* a turn
  never nests (the tracker's flag, the loop's own, and the resize watch's
  guard), a second drag is refused while one is in flight, and a closing
  backend cancels the drag at OLE's next question. A window closed inside a
  turn is not deferred; the reports skip it.
- **SDL's pointer state after a Windows drag.** OLE has the pointer captured,
  so SDL never hears the release. It reads the buttons again from the next
  mouse message, and the router swallows the release of a press the platform
  finished. The hand check confirms it.
- **Two drop targets on Wayland.** *Gone in phase 3:* a second data device
  of the client's own unlinks SDL's on mutter, so the drag uses SDL's, and
  SDL's is the only target over the toolkit's windows (ADR-0716).
- **Native-image upcalls.** Phases 2–5 add upcall shapes. Each is registered
  and counted in `ForeignSurfaceTest`, and the native image's showcase run
  covers each platform's set it can reach.
- **Untested platforms.** As with W3.8, the Windows and macOS sessions may land
  "written, not run". The ADR and the gaps answer say so until a lane drives
  them.

## 6. Documents kept in step

- `gaps/goldberry-gaps-plan.md` §8 (T-20 row) and §10.
- `gaps/answers-tessera.md`, G-20's "Landed." paragraph.
- `docs/snapshot/README.md` and `adr/README-lines.md` for each ADR.
- `docs/snapshot/changelog-breaking-changes.md` if `Attributes` grows a
  component (it does, under D3 (a)).

## 7. Status and log

| Phase | Status |
|-------|--------|
| 1 — API and router | done (2026-11-21) |
| 2 — X11 | done (2026-11-21), without a picture; see the log |
| 3 — Wayland | built (2026-11-21); the hand check on GNOME is open |
| 4 — Windows | built (2026-11-21); the Windows lane is green on it (Snapshot #86); a hand check is open |
| 5 — macOS | built (2026-11-21); the macOS lane is green on it (Snapshot #86); a hand check is open |
| 6 — showcase and guide | done (2026-11-21); the guide section is parked in `docs/snapshot/` |

- **2026-11-21.** Plan written from the tree at `b3752f37` and SDL 3.4.18's
  sources. What shapes it: SDL is a drop target on all four platforms and a
  drag source on none. It does give enough to build one: an X11 event hook,
  OLE already up on Windows, the `wl_display` and `wl_surface` on Wayland, and
  the `NSWindow` on macOS. Windows' `DoDragDrop` is modal, which is D5. D1–D8
  wait for the owner; no code.
- **2026-11-21, later.** The owner answered. D1 (b): the OS drag starts at
  the threshold for a draggable with an offer, not at the window's edge. D2
  through D8 as recommended. D1 (b) moves D2's feedback from SDL's
  `DROP_POSITION` to the drag source's own tracking, which works whatever the
  offer carries, and makes D5's pump the only way Windows shows in-window
  feedback. ADR-0714 (parked) records all eight. Next ADR 0715.
- **2026-11-21, phase 1.** Built in `:core`, nothing native.
  `input.dragout` (exported): `DragOffer`, `DragOperation`, `DragOutcome`
  (sealed: `Delivered(operation)`, `Landed(accepted)`, `Cancelled`), `DragOut`
  (the attribute's value), `DragPicture`, and `DragHandOff`, which the router
  hands the window. `render.dragout` (exported, the backend's half):
  `DragRequest`, `DragSession`, `DragEnd`, `OfferedData` (an offer as MIME
  types and bytes) and `DragPictures` (the ghost cut from the last frame).
  `BackendWindow.startDrag` refuses by default. `Attributes` gained `dragOut`
  as a twelfth component and `Dragging` gained `platform` (both breaking notes
  in the snapshot's changelog). The router hands a pointer drag with an offer
  over at the threshold (`takeDragOut`), the window starts it after the
  event, not inside the router, and `DragFlight` turns the session's reports
  into `platformDragOver`/`platformDragDropped`/`platformDragLeft` on whichever
  window the pointer is over. A press the platform finished before its release
  swallows that release. An echoed drop of the application's own offer is
  swallowed by content, not by time. `HeadlessWindow` records drags as
  `HeadlessDrag` for tests to play the platform. Tests: `DragOutRouterTest`
  (10), `DragOutWindowTest` (6), `DragOfferTest`, `OfferedDataTest`,
  `DragPicturesTest`. Next: phase 2, X11.
- **2026-11-21, phase 2.** X11, built. Native ABI 27 exports
  `SDL_SetX11EventHook`; `natives.desktop.dragout.X11EventHook` binds it (the
  same upcall shape as an event watch, so no new shape; in the qualified
  package, because its handler takes an `XEvent*` and the unqualified
  exports carry no foreign memory). SDL's `Display*` reaches it as an
  address, as `NativeWindowHandle` carries an `HWND`. `natives.desktop.dragout`
  (to `:core` only): `XdndSource`, XDND version 5 as a state machine over an
  `XdndTransport`; `XlibXdnd`, the transport on SDL's own `Display`, with an
  unmapped InputOnly source window and an error handler that swallows
  `BadWindow` on its display for the length of a drag; `X11DragSource`, which
  listens through the hook, reads the pointer with `XQueryPointer` after each
  event (motion comes as XInput2 events, which are not parsed) and closes at
  its ending. In `:core`, `X11DragOut` maps the offer to XDND types (with
  `UTF8_STRING` beside UTF-8 text, and the image also written to a PNG file),
  maps the application's own windows by X id, and hands every report to the
  next pump (`Sdl3Backend.later`), so no drop target runs inside SDL's hook.
  The answer timeout (5 s) is checked once a pump. Found on the way: on
  XWayland, mutter's window manager asks the owner of `XdndSelection` for
  `TARGETS` as soon as it is taken, to bridge the drag to Wayland, so the
  source answers requesters it never sent a message to. **Not yet done on
  X11:** the picture under the pointer (an input-transparent ARGB window,
  which needs libXext's input shape), the `XdndActionAsk` cursor feedback
  (the pointer keeps its arrow), and `INCR` for data larger than one request
  (refused; the limit is the server's big-request size, megabytes). Tests:
  `XdndSourceTest` (23, a fake server), `XlibXdndTest` (3, a real server on
  `$DISPLAY` or a private Xvfb: a message read back, a selection answered, a
  `BadWindow` survived), `X11DragOutTest` (4). Not driven by hand yet: moving
  the real pointer is not something a test here does. Next: phase 3,
  Wayland.
- **2026-11-21, phase 3.** Wayland, built, with a change of approach recorded
  in ADR-0716. Read before writing: mutter's `get_data_device` unlinks a
  client's earlier data device when it asks for another, so the plan's own
  `wl_data_device` would have silenced SDL's drops and clipboard for the
  life of the process (weston keeps both, which is why the protocol looked
  safe). Instead libgoldberry gains one C file compiled against SDL's
  private headers, `goldberry_wayland_drag_origin`, which hands Java SDL's
  data device and `last_implicit_grab_serial` (native ABI 28); the plan's
  early `wl_pointer` for the serial is not needed. Everything else is Java
  over libwayland-client in `natives.wayland`, one upcall shape for all of
  it (nineteen now). Because SDL's device is the only target over the
  application's windows and a Wayland source hears no position, D2's
  feedback comes from SDL's `DROP_*` there, which the backend takes while
  the drag lasts; SDL's 14 ms read of its own echo times out, since the
  source is not dispatched meanwhile. `X11DragOut`'s offer mapping moved to
  `DragOutData`, shared. Tests: `WaylandCompositorTest` (4, a real
  compositor), `WaylandDragOriginTest` (2), `WaylandSourceEventsTest` (5),
  `WaylandArgumentsTest` (3), `DescriptorsTest` (3), `WaylandDropTrackerTest`
  (10), `WaylandDragOutTest` (4), `DragOutDataTest` (5, was
  `X11DragOutTest`), `WaylandDragSourceTest` (1: the whole start from a
  hidden SDL window on GNOME, which ignores a drag with no press's serial,
  with no protocol error on SDL's connection before or after closing).
  **Open:** a hand drag on GNOME (into Nautilus, into a
  browser, and onto an in-app target), which no test here can make. Next:
  phase 4, Windows.
- **2026-11-21, phase 4.** Windows, built and not run there (ADR-0717).
  COM objects in Java over `ComServer`: `OleDataObject`, its own
  `OleFormatEnumerator`, `OleDropSource`, called through their vtables by
  tests on every platform with global memory from the heap. `OleDragSource`
  runs `DoDragDrop` inside the pump that started the drag; its tracker asks
  where the pointer is on every question of OLE's, reports the application's
  own windows, decides the ending from where the button came up, and runs
  one guarded turn: the queued reports, `DragSession.whileHeld` (the loop's
  posted work and due timers, `EventLoop.turnWhileHeld`), and the due frames.
  SDL's queue is not read during the drag, so no native ABI bump: its
  `DROP_*` events are taken until the pump after the end. The picture goes
  through the shell's `IDragSourceHelper`. Six more upcall shapes (twenty-five).
  Tests: `ComServerTest` (3), `OleDataObjectTest` (9), `OleDropSourceTest`
  (2), `OleDragTrackerTest` (11), `OleDragSourceTest` (4),
  `OleClipboardRoundTripTest` (2, Windows only: through OLE's clipboard and
  the shell's image helper), `WindowsDragDataTest` (5), `WindowsDragOutTest`
  (3), `EventLoopHeldTest` (3), and a held turn in `DragOutWindowTest`.
  **Open:** the Windows lane's first run, and a hand drag on Windows into
  Explorer, a browser and Word, and onto an in-app target. Next: phase 5,
  macOS.
- **2026-11-21, phase 5.** macOS, built and not run there (ADR-0718).
  `ObjC` passes structs by value (`NSPoint`, `NSSize`, `NSRect`) and returns
  an `NSPoint`. `GoldberryDraggingSource` is the dragging source and the
  pasteboard item's data provider. Files go as file URLs, everything else
  on one lazily provided pasteboard item, and the picture is an `NSImage`
  over premultiplied BGRA. The session is begun on SDL's content view from
  `[NSApp currentEvent]` and runs in SDL's pump, so the reports go through
  `later` as on X11. The position comes from `movedToPoint:` and the window
  at that point, and the ending from where it was let go, with a key press
  as the current event meaning Escape. The echo bookkeeping is shared with
  Windows (`DropEchoes`). Three more upcall shapes (twenty-eight). Tests:
  `CocoaDragTrackerTest` (6), `CocoaDragSourceTest` (3),
  `CocoaDraggingSourceMacTest` (3, macOS only: the methods sent through the
  runtime with `NSPoint`s by value, the pasteboard item read back through
  the data provider, the `NSImage`), `MacDragDataTest` (3), `MacDragOutTest`
  (3), and `DragOutDataTest` gains the whole scale. **Open:** the macOS
  lane's first run, and a hand drag into Finder, a browser and Mail and onto
  an in-app target, which also answers whether AppKit begins a session from
  the event SDL last handled. Next: phase 6, the showcase and the guide.
- **2026-11-21, phase 6.** The showcase and the guide. `DragOutCard` on the
  Input screen, after `DropCard`: a painted picture (`Landscape`, offered as a
  PNG and as `goldberry-picture.png`) and a note (text and
  `goldberry-note.txt`), each a `Keepsake` payload with a `dragOut` offer from
  `DragOutExports`, which writes the files to a temporary folder when a drag
  starts and offers without the file when it cannot. A tray beside them is an
  in-app drop target for either, and a readout says how the last drag ended.
  Found on the way: an offscreen `Session` never took the router's hand-off,
  so `session.drag` of a draggable with an offer went nowhere. Its `drag` now
  refuses the hand-off, as a backend without a drag does, and the drag goes
  on in-app; a test that drives `router()` itself still plays the platform
  with `takeDragOut()`. The guide's section, `## Dragging out of the window`,
  is parked as `docs/snapshot/guide-input-drag-out.md`, and the parked
  `guide-input-dragging.md` no longer says a drag cannot leave the window.
  No ADR: nothing here is a decision. Tests: `DragOutCardTest` (11), a session
  case in `WidgetDragTest`, the card in `InputChapterTest`, and the search
  index rewritten; the Home and sidebar goldens and the book's
  `screen-forms-*.webp` were retaken for the one more card. **Open for the whole plan:** the Windows and macOS lanes'
  first runs, and hand drags on all four desktops, which the card is now the
  place for.
- **2026-11-21, Snapshot #86** (on `76ccbee7`, phases 4 and 5). The Windows
  and macOS lanes are green: `OleClipboardRoundTripTest` went through OLE's
  clipboard and the shell's image helper on Windows, and
  `CocoaDraggingSourceMacTest` sent its methods through the Objective-C
  runtime on Apple Silicon. The run's one red was the linux-aarch64 media
  library, whose AlmaLinux AppStream mirrors were mid-sync (404 on the
  repodata their index named, three tries running); the `dnf` loops in
  `linux.yml` and `media.yml` now ask AlmaLinux's own server on the third try.
  What no lane can do is still open: a hand drag on each desktop.

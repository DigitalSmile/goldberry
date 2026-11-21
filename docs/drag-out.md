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

- `natives.wayland`: a minimal binding of libwayland-client (looked up by
  soname, so it is the instance SDL loaded): proxies, an event queue of the
  toolkit's own, the interface tables it exports, and listeners as upcalls.
- `natives.desktop.dragout.WaylandDragSource`: its own `wl_registry` on
  SDL's `wl_display`, its own `wl_seat` and `wl_pointer` so that it hears the
  press's serial, a `wl_data_source` with the offered types, and `start_drag`
  from SDL's surface with an icon surface in an shm buffer. `send` writes on a
  worker into the target's file descriptor. `dnd_drop_performed`,
  `dnd_finished` and `cancelled` become the outcome. The queue is dispatched
  every turn of the loop.
- `SdlVideo` reads `SDL.window.wayland.display` and `SDL.window.wayland.surface`
  for this alone; `nativeHandle` stays empty on Wayland.

**Exit:** on CI's headless weston, a Wayland client of the test's own (in the
test fixtures, same binding) that takes the drop and reads each type. weston
must start a drag without a real seat; if its headless backend will not, the
exit is a manual check on GNOME and KDE, recorded in the log.

### Phase 4 — Windows

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
  began the implicit grab. A second `wl_pointer` on a second `wl_seat` proxy
  hears the same events, but only if it exists **before** the press. So it is
  made when the first window opens, not at the drag. Compositors that check
  the serial strictly (mutter does) refuse a wrong one silently.
- **SDL's pointer state after an OS drag.** On Wayland and macOS the platform
  takes the pointer, and SDL may never hear the release, leaving its own button
  state down. Phase 3 and 5 must check `SDL_GetMouseState` after a session
  and, if it is stuck, synthesise the release into the router (not into SDL).
- **Reentrancy on Windows.** A loop turn inside `GiveFeedback` runs
  application code inside a COM call inside `DoDragDrop`. The guard refuses
  anything that would nest: a second drag, a modal file dialog, closing the
  source window (deferred until `DoDragDrop` returns).
- **Two drop targets on Wayland.** The toolkit's own `wl_data_device` and
  SDL's both hear `enter` for drags over the toolkit's windows. Its own ignores
  them; only SDL's is a target.
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
| 2 — X11 | not started |
| 3 — Wayland | not started |
| 4 — Windows | not started |
| 5 — macOS | not started |
| 6 — showcase and guide | not started |

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

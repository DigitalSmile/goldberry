# ADR-0718: A macOS drag is an AppKit session begun on SDL's view

- **Status:** Accepted, as phase 5 of `docs/drag-out.md` (W3.9, Tessera's
  T-20). Implements ADR-0714's D2, D4, D6 and D7 on macOS. Unverified on a
  Mac until the macOS lane and a hand check run it.
- **Date:** 2026-11-21
- **Relates to:** ADR-0714 (the drag out's decisions), ADR-0717 (the Windows
  drag), ADR-0003 and ADR-0041 (SDL as the only desktop backend)

## Context

On macOS a drag out is `-[NSView beginDraggingSessionWithItems:event:source:]`.
SDL's content view is a drop target for files and text, and nothing in SDL
starts a session. Unlike Windows' `DoDragDrop`, the session is not modal:
AppKit runs it from the ordinary event loop, which is SDL's pump, and tells
the source what happens through `NSDraggingSource`.

`ObjC` (W2.12's menu bar and notifier) sends messages and defines classes
whose methods are upcall stubs, but it passed no structs. AppKit's dragging
API needs them: `setDraggingFrame:contents:` takes an `NSRect`,
`draggingSession:endedAtPoint:operation:` is called with an `NSPoint`, and
`convertPointFromScreen:` returns one.

## Decision

1. **`ObjC` passes structs.** `Point`, `Size` and `Rect` records go by
   value as `NSPoint`, `NSSize` and `NSRect`, and `sendPoint` returns an
   `NSPoint`. The only struct returned is sixteen bytes, which comes back in
   registers on arm64 and on x86-64, so `objc_msgSend` carries it. A larger
   struct would need `objc_msgSend_stret` on x86-64, and none is asked for.
   `defineClass` adopts several protocols.
2. **One source object per drag.** `GoldberryDraggingSource`
   (`natives.desktop.dragout.cocoa`) is the `NSDraggingSource` (the operation
   mask, `movedToPoint:`, `endedAtPoint:operation:`) and the
   `NSPasteboardItemDataProvider` (`provideDataForType:`,
   `pasteboardFinishedWithDataProvider:`). The methods are shared stubs that
   find their drag by the object's address.
3. **What is dragged.** Each file is a dragging item of its own, as a file
   `NSURL`. Every other type goes on one `NSPasteboardItem` whose data is made
   when a target asks (`core`'s `MacDragData`): the first link as
   `public.url`, text (or the links, one per line) as
   `public.utf8-plain-text`, the image as `public.png`, and each MIME type of
   the application's own under the identifier `UTType` gives it, which is a
   dynamic one where the system registers none. As on the other platforms,
   the image is also a PNG file.
4. **The picture** is the ghost as an `NSImage` over an `NSBitmapImageRep`
   of premultiplied BGRA (`AlphaFirst | ThirtyTwoBitLittleEndian`), sized in
   points at the window's whole scale. It is framed so that the press's
   hotspot sits under the pointer, in the view's own coordinates, flipped or
   not. Only the first item shows it.
5. **Where the pointer is.** `movedToPoint:` gives a screen point.
   `+[NSWindow windowNumberAtPoint:belowWindowWithWindowNumber:]` names the
   top window there, whoever it belongs to. If it is one of the
   application's, `convertPointFromScreen:` makes the point that window's,
   and the backend turns the vertical axis over into logical pixels from the
   top. AppKit's points are the toolkit's logical pixels.
6. **The ending.** If the current event is a key press when the session
   ends, Escape ended it, and the drag is cancelled wherever the pointer was.
   Otherwise a release over one of the application's windows lands there
   in-app, whatever SDL's view answered. An operation elsewhere is
   `Delivered` (`Generic` alone reads as no operation named), and no
   operation is `Cancelled`. Copy is offered with `Generic` beside it.
7. **Reports arrive inside SDL's pump** and go to the next one through
   `Sdl3Backend.later`, as on X11. No loop turn is needed: AppKit's session
   does not hold the thread.
8. **SDL's echoes.** SDL's view drops the application's own files or text
   on itself. The drag takes every `DROP_*` event while it lasts and through
   the pump after its end. That bookkeeping is now shared with Windows as
   `DropEchoes`.
9. **The source object outlives the drag's end** until the pasteboard says
   `pasteboardFinishedWithDataProvider:`, since a target may read the data
   that late. With files alone there is no pasteboard item, and it goes at
   the end. A closing backend lets it go at once.

Rejected:

- An `NSFilePromiseProvider` for files not yet written: ADR-0714's D4 left
  promised files out.
- Taking SDL's drop events as the position, as on Wayland: SDL's view takes
  only files and text, and would leave other offers without in-app feedback.
- `objc_msgSend_stret` and `NSRect` returns: `windowNumberAtPoint:` and
  `convertPointFromScreen:` answer the question without a frame.

## Consequences

- Three more upcall shapes (twenty-eight in all): the operation mask, and
  the two that take an `NSPoint` by value.
- Nothing has run on a Mac yet. The tracker, the types, the frame and the
  bookkeeping are tested everywhere. On the macOS lane,
  `CocoaDraggingSourceMacTest` sends the source's methods to an object of
  the class, `NSPoint`s by value included, writes its pasteboard item to a
  pasteboard of its own and reads the text back through the data provider,
  and makes the picture's `NSImage`. The session itself needs a held button,
  and is a hand check: into Finder, a browser, Mail, and onto an in-app
  target.
- Whether `[NSApp currentEvent]` is an event AppKit begins a session from,
  when the toolkit handles the move after SDL's pump has returned, is the
  first thing the hand check answers. If AppKit refuses, the drag stays
  in-app, as on a platform with no source.
- Whether SDL's mouse state stays down after a session that took the release
  is the second. The router already closes the gesture when the drag ends
  (`dragOutFinished`), so the application does not see a button held. What
  remains open is a click that SDL might drop because it still holds the
  button down.
- A window whose content does not sit at the bottom of its frame would put
  in-app positions off by that difference. SDL's windows do sit there.

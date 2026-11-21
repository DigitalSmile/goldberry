<!-- One piece. Apply `guide-input-dragging.md` first: this section follows
     the one it adds, and its last paragraph and its `limitations.md` row
     were brought in step with this one on 2026-11-21. -->

<!-- Destination: book/src/guide/input.md, a new section
     `## Dragging out of the window`, after `## Dragging between widgets` and
     before `## Context menus`. Doc comments in `dev.goldberry.input.dragout`,
     `Attributes.dragOut` and the showcase's `DragOutCard` link
     `#dropped-files-and-text` until then; point them at
     `#dragging-out-of-the-window` when this moves. -->

## Dragging out of the window

```java
new Card(frame.title())
        .draggable(frame)
        .dragOut(() -> DragOffer.image(frame::png).withFiles(frame.exportedPath()))
        .onDragOutEnd(outcome -> {
            switch (outcome) {
                case DragOutcome.Delivered _ -> status.show("Sent to another application");
                case DragOutcome.Landed _ -> {}
                case DragOutcome.Cancelled _ -> status.show("Not sent");
            }
        });
```

A draggable that also has a `dragOut` offer is the desktop's own drag once
the pointer has moved four logical pixels with the button down. It can be let
go in a file manager, a browser, a mail client or an editor, which take what
the offer carries. The desktop draws the picture under the pointer: the source
as it was painted, faded, held where the press took hold of it, unless the
offer gives one of its own with `withPicture`.

An offer is any mix of five kinds, each named the way the desktop expects:

| Offer | Carried as |
|-------|------------|
| `files(paths)` | the files themselves, which must exist when the drag starts |
| `link(uri)` | a link, and its text |
| `text(string)` | text |
| `image(png)` | a PNG image, and the same image as a PNG file for the targets that take only files |
| `data(mime, bytes)` | bytes under a type the application names |

`dragOut` takes a supplier, asked once when a drag starts, so a widget that
is built often and dragged rarely pays nothing for its offer. Everything but
the paths is a supplier too, asked for when a target takes it: an image that
is never dropped anywhere is never encoded. Suppliers run on the UI thread.

The target picks what to do with the drag from the operations the offer
allows, `COPY` alone unless `allowing` says otherwise, and the user steers the
pick with the desktop's modifier keys. The toolkit never deletes anything: a
source that hears `MOVE` decides what moving means.

`onDragOutEnd` hears how it ended, once:

| Outcome | Means |
|---------|-------|
| `Delivered(operation)` | another application took it; `operation` is what it says it did, or null where the desktop does not say |
| `Landed(accepted)` | it was let go over one of the application's own windows; `accepted` says whether a drop target there took it |
| `Cancelled()` | it was put back: `Escape`, a release over nothing that takes it, or a target that refused it |

Over the application's own windows the drag is still an in-app drag. Drop
targets light up with `:drag-over` and hear `whileOver` as before, and a drop
there delivers the payload itself, not a `FileDrop` of the application's own
files. The window that started the drag keeps painting while it lasts.

A draggable with no offer stays inside its window, and so does every drag
made from the keyboard. Where the desktop will not start a drag, the gesture
goes on as an in-app drag from where the pointer is, and `onDragOutEnd` hears
nothing. A test session is such a place: `session.drag("frame", "bin")`
carries a draggable with an offer onto a drop target like any other.

| Desktop | Notes |
|---------|-------|
| X11 | the pointer keeps its arrow during the drag, and no picture follows it; data larger than the display server takes in one request is refused |
| Wayland | an offer of a type of the application's own, with no file and no text beside it, is not seen by the application's own drop targets while it is out |
| Windows | the drag runs inside a loop of the desktop's; the window's posted work, timers and frames run in turns inside it |
| macOS | files go as file URLs, everything else is made when a target asks for it |

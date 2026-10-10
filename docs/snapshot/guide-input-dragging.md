<!-- Three pieces, each says where it goes. -->

<!-- 1. Destination: book/src/guide/input.md, a new section
     `## Dragging between widgets`, after `## Dropped files and text` and
     before `## Context menus`. -->

## Dragging between widgets

```java
new Card(ticket.title()).draggable(ticket);

new Column(cards).dropTarget(
        payload -> payload instanceof Ticket,
        drop -> board.move((Ticket) drop.payload(), status)
);
```

`draggable(payload)` and `dropTarget(accepts, onDrop)` are attributes, so any
widget can be either. The router does the rest. A press on a draggable
becomes a drag once the pointer has moved four logical pixels with the button
down, so a press that does not move is still a click. A control inside the
draggable that consumes its press keeps it: a slider in a draggable card drags
its thumb, and the card is picked up from anywhere else.

While the drag lasts, the source's own box follows the pointer, faded, drawn
over everything and invisible to the hit test. The router looks for a target
under the pointer: the nearest node up the tree that carries a drop target
whose `accepts` says yes, skipping the source and what is inside it. That
node matches `:drag-over`. A target that refuses is passed over and gets no
`:drag-over`. Letting go over an accepting target calls its `onDrop` with a
`Drop`: the payload, and the point measured from the corner of the target's
content box. Letting go anywhere else, or pressing `Escape`, puts the drag
back. A press that became a drag is not a click wherever it is let go.

`DropTarget` carries two more hooks for a target that draws where the drop
would land, as a `canvas` does:

```java
var target = DropTarget.of(payload -> payload instanceof Event, drop -> week.move((Event) drop.payload(), drop.at()))
        .whileOver(drop -> week.showSlotAt(drop.at()))
        .onLeave(week::hideSlot);
new Canvas(week::paint).dropTarget(target);
```

`whileOver` hears every move, and `onLeave` hears the drag leave, land or be
put back. For a `canvas` the point is in the painter's own coordinates.

From the keyboard, `Space` on a focused draggable picks it up when something
in the window accepts it. The arrow keys and `Tab` step between the targets
that accept it, in document order, `Space` or `Enter` drops it at the centre
of the one it is over, and `Escape` puts it back. `Drop.fromKeyboard()` tells
the two apart. `Space` is taken before the focused widget hears it, so a
draggable `pressable` is lifted by `Space` and activated by `Enter`.

The payload is the object itself and never leaves the application: this is
not the platform's drag and drop, and a drag cannot leave the window or
cross into a popup. `session.drag("card", "done")` carries one in a test.

<!-- 2. Destination: book/src/guide/styling.md, the pseudo-class table, a new
     row after `:active`. -->

| `:drag-over` | a drag the node accepts is over it | the router |

<!-- 3. Destination: book/src/overview/limitations.md, the `Platform drag and
     drop` row, its last column. -->

A file dropped on a window arrives with its position, and a widget can be
dragged onto another inside the application. Dragging out of the application
is not built

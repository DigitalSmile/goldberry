# ADR-0591: A widget is dragged onto another by attribute, and the router carries it

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** docs/gaps.md (G50),
  [ADR-0589](0589-a-cursor-shape-may-be-the-applications-picture-and-a-drag-follows-its-holders-cursor.md),
  [ADR-0592](0592-a-canvas-places-real-widgets-where-its-painter-drew.md)

## Context

A drag could live inside one widget and nowhere else. A press captures the
pointer for the element it landed on, `Input.onPointer` inside a `canvas`
hears the whole gesture, and nothing else does. The downstream wants a kanban
card dragged from one column onto another, an event block dragged onto
another day of a week grid, and later a mail conversation dragged onto a
rail. Each of those starts in one widget and ends over another, and the
widget that started it cannot see the one it ends over.

Files dropped on a window were the same kind of question, and were answered
in the toolkit, once, as `FileDrop`. The pointer router already owns the
press, the capture, the hit test against the painted frame, `:hover` and
`:active`, and the cursor during a drag. It is the only thing that sees both
ends of the gesture.

## Decision

**Two attributes, and the router does the rest.** `Attributes.draggable(Object
payload)` makes a node something that can be picked up, and
`Attributes.dropTarget(Predicate<Object> accepts, Consumer<Drop> onDrop)` makes
it something that takes a payload. Both are on `Attributed` as chainable steps,
so any widget can be either, as with a tooltip. `dropTarget(DropTarget)` takes
the full form. `Attributes` gains two record components, and its eight-argument
constructor stays for the callers that wrote it.

The new types are in `dev.goldberry.input.drop`, beside `FileDrop` and
`TextDrop`, so no package or module export is new:

- `Drop(Object payload, LogicalPoint at, boolean fromKeyboard)`, the shape the
  entry asked for plus how the drop was made.
- `DropTarget(accepts, onDrop, whileOver, onLeave)`. The two extra hooks are
  optional. `whileOver` hears the payload and the point on every move while
  an accepted drag is over the target, and `onLeave` hears the drag leave,
  land or be put back. They are what a `canvas` needs to draw a drop
  indicator: it hears no pointer events during the drag, because the pointer
  is held by the source.
- `Dragging`, a snapshot of the drag in progress, read from
  `PointerRouter.dragging()`. The frame reads it to draw the ghost, and tests
  read it.

**The gesture, in `PointerRouter`:**

- A primary press whose chain has a draggable arms it, after the press is
  dispatched. It does not arm when the press was consumed by a node strictly
  inside the draggable. The router now records which element consumed the
  last dispatched event, and this is that record's only reader. This is how
  the press-and-drag model resolves against existing widgets: a slider in a
  draggable card consumes its press and drags its thumb. The draggable
  itself, or anything above it, consuming the press does not stop it. A
  `pressable` hears its press and is still a card that can be carried. A
  disabled draggable is not picked up.
- The armed press becomes a drag once the pointer has travelled
  `PointerRouter.DRAG_THRESHOLD`, four logical pixels, from where the button
  went down. Below that it is still a press, so a click on a draggable is
  still a click.
- During the drag the moves are the router's. The source heard `PRESSED` and
  will hear `RELEASED`, but no `MOVED` in between. Each move looks for a
  target: up the chain from the element under the pointer, skipping the source
  and what is inside it, to the first node whose `DropTarget.accepts` says yes
  and which is not disabled. A target that refuses is passed over on the way
  to an ancestor that accepts. What contains the source is not skipped, so a
  card let go over its own column is a drop the application decides about.
- The target alone matches the new `:drag-over` pseudo-class (`DRAG_OVER`),
  not its ancestors as `:hover` does, since only one node would take the drop.
- A release over an accepting target calls `onLeave` and then `onDrop`, after
  the router has cleared the drag and the source has heard its `RELEASED`. A
  press that became a drag is never a `CLICKED`, wherever it is let go.
  Releasing over nothing that accepts drops nothing.
- `Escape` during a drag puts it back, ahead of everything else that hears
  keys, so a dialog under the drag does not also take it as its own Escape.
  The rest of the press is spent and its release is no click.
- `updateRegions` keeps the rule that the router never holds an element that
  is not in the tree: a source that leaves the tree cancels the drag, and a
  target that leaves is let go of.

**`Drop.at` is in the target's own content box**, measured from the corner
inside its padding and mapped through the region's inverse as a press is.
This is what `PointerEvent.content()` is measured from, and for a `canvas` it
is where the painter draws from. So a drop reaches a canvas in the painter's
coordinates, and that is how "the drop's point reaches its `Input`" is met:
the canvas is a target like any other widget. Its `Input` is not involved,
because `Input` hears pointer events and none reach it during a drag.

**The ghost is the source's own box, drawn by the frame.**
`FrameSequence.layOut` appends it to the root box after the tree has rendered
and before layout (`dev.goldberry.frame.DragGhost`). It is a copy of the
outermost box the source painted this frame, absolutely placed so that the
point the pointer grabbed stays under the pointer, at the source's painted
size, at 70% of its opacity. It is made with the new `Box.scenery()`, which
strips every owner and cursor in the subtree. A hit test skips a box with no
owner, so the ghost, which is always under the pointer, never becomes the
thing the pointer is over. A drag move sets the router's styles-dirty flag,
so a window repaints for the ghost even when no pseudo-class changed. The
entry offered "or ask the source for a widget to draw". The source's own box
needs no API and is what a user expects to see moving, so only that was
built.

**The cursor** is ADR-0589's. The source holds the capture, so its own
`cursor` is followed, and `.card:active { cursor: grabbing }` shows the
closed hand for the whole drag.

**From the keyboard.** `Space` on a focused node that is itself draggable
picks it up, provided at least one painted target in the window, or in the
modal in force, accepts the payload. Otherwise the key goes on as before. It
is taken before the focused widget hears it, so a draggable `pressable` is
lifted by `Space` and still activated by `Enter`. A draggable around a
focused control leaves that control its `Space`. During a keyboard drag:

- the arrow keys and `Tab` / `Shift+Tab` step between the accepting targets in
  document order, wrapping;
- `Space` or `Enter` drops on the current one at the centre of its content
  box, with `fromKeyboard` true;
- `Escape` puts it back, and so does a pointer press.

Focus stays on the source throughout. No ghost is drawn for a keyboard drag:
`:drag-over` on the current target is its picture.

`Session.drag(from, to)` and `Session.drag(fromX, fromY, toX, toY)` carry a
drag in a test, stopping halfway so the gesture has a middle.

## Not done

- **Platform drag and drop.** The payload is the object itself and stays in
  the process. Nothing outside the application sees the drag, and a drag
  cannot leave the window or cross into a popup window, which has its own
  router. Dragging out to the desktop, or from another application onto a
  widget, is not built. A file or text dropped from outside still arrives as
  `FileDrop` or `TextDrop` on the window.
- **A widget for the ghost.** The ghost is always the source's own box. A
  `draggable(payload, ghost)` overload can come when something needs a
  different picture.
- **A pseudo-class on the source.** Only the target gets one. A source styles
  its lifted look with `:active`, which it holds for the whole drag.
- **Screen-reader announcements.** The semantics tree has no live region for
  "picked up" or "dropped on". The accessibility bridge is on hold.
- **A showcase card.** For the reason given in ADR-0592: the input chapter is
  pinned by a golden picture, and the guide section a card would link is
  parked.
- **Auto-scroll.** A `scroll` does not scroll when a drag nears its edge. The
  wheel during a drag goes to the source, which holds the capture, so it does
  not scroll the viewport under the pointer either.

## Consequences

- `Selector.PseudoClass` has an eleventh value, `drag-over`. The parser's
  closed set and its error message pick it up from the enum.
- `PointerRouter.dispatch` records the consuming element on every pointer
  event. That is one field write per handler that consumes.
- The ghost costs one tree walk of the root box per frame during a drag, and
  nothing otherwise.
- New tests: `WidgetDragTest` in `:core`, through `Offscreen.session`. A drag
  from A onto B delivers the payload and the content-box point. The target
  alone matches `:drag-over`. A refusing target gets neither the pseudo-class
  nor a drop, and passes the drop to an accepting ancestor. `Escape` cancels,
  and its release is no click. A press and release, and a move under the
  threshold, are clicks. A control that consumes the press keeps it. The
  ghost is placed at the pointer and the hit test sees through it. The
  keyboard lifts with `Space`, steps with `Tab` past a refusing target and
  drops with `Enter`, and `Escape` puts a keyboard drag back. In `:widgets`,
  `CanvasDropTest` shows a `canvas` target hearing `whileOver` and the drop in
  its painter's coordinates, and a widget positioned on a canvas (ADR-0592)
  carried across it and dropped on it.
- The guide sections are parked in `docs/snapshot/guide-input-dragging.md`:
  a new `## Dragging between widgets` in the input chapter, a `:drag-over` row
  in the styling chapter, and the limitations row.
- Where reality differed from the entry: `Drop` carries `fromKeyboard`, and a
  target can have `whileOver` and `onLeave` besides `accepts` and `onDrop`.
  The ghost is the source's box rather than a widget it is asked for. A
  canvas's `Input` is not told about the drop; the canvas is a target with its
  point in the painter's coordinates.

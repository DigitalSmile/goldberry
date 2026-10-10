# ADR-0592: A canvas places real widgets where its painter drew

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** docs/gaps.md (G51),
  [ADR-0575](0575-an-absolute-child-is-placed-against-the-padding-box-inside-the-border.md),
  [ADR-0591](0591-a-widget-is-dragged-onto-another-by-attribute-and-the-router-carries-it.md)

## Context

A `canvas` was a leaf. Its painter drew, its `Input` heard the pointer and the
keyboard, and it had one accessible name. The downstream's week grid paints
its event blocks, which is what makes a week of 200 events one draw. But its
acceptance list asks every control for a semantics name, a focus ring and a
focus order equal to the visual order, and a painted rectangle has none of
those. Today the blocks are reached by pressing, and from the keyboard only
through other screens.

The entry offered two answers: positioned children over a canvas, or a
`week-grid` widget of the toolkit's own. The first is smaller and also serves
a board's selection handles.

A canvas does not scroll itself. "Scrolling" here means a canvas inside a
`scroll`, which moves its box with a transform.

## Decision

**`Canvas.overlay(Function<LogicalSize, List<Positioned>>)`**, with
`record Positioned(Widget widget, LogicalRect at)` in
`dev.goldberry.widgets.core.canvas`, as the entry proposed. `overlay` is a
wither and a fifth record component of `Canvas`, and the four-argument
constructor stays for the callers that wrote it. A canvas with no overlay has
no children and renders exactly as before.

- **The size is the content box's**, the rectangle the painter is told about,
  and `at` is in the painter's coordinates. So the arithmetic that placed a
  block in the painting places its widget, and a widget at the rectangle a
  block was painted at covers it exactly.
- **The children are absolute boxes inside the canvas's box.** `Canvas.children()`
  returns one package-private `CanvasLayer`, a stateful widget with a fixed
  key that holds the measured size. Under it is a `canvas-layer` box, which
  `Canvas.render` pins to the content box with `ContainingBlock.inContentBox`
  and `overflow: hidden`. Under that, one `canvas-item` slot per `Positioned`
  is placed absolutely at its rectangle, and the widget inside fills it: the
  slot is a column that stretches, and the widget grows down it. Because the
  layer is out of flow, the canvas is still sized by its stylesheet alone.
  Because it clips, a widget is cut at the content box as the painter is, for
  the hit test as well as the eye. Because it is inside the canvas's box, it
  scrolls with the canvas.
- **The size arrives through `Measured`.** The `canvas-layer` box is told its
  size after each frame, and the layer rebuilds when that size changes by half
  a pixel or more. This is the `masonry` pattern. Reading the size here cannot
  change it, since every box under the layer is absolute, so the loop settles
  on the second frame. The cost is that **the widgets arrive one frame after
  the canvas is first laid out**, and a resize shows the old placement for one
  frame. The function is also asked again whenever the application rebuilds
  the canvas.
- **Keys.** A slot takes the key of its widget, so a widget whose key comes
  back keeps its element across a rebuild, a resize or a reordering, and with
  it its focus and its state. The test shows the element and its focus kept
  through a resize.
- **Focus order is list order.** The slots are children of the canvas in list
  order, and Tab walks the tree in document order: the canvas first when it
  is focusable, then the widgets as listed. A caller whose list is in visual
  order gets focus in visual order.
- **Hit testing puts the widgets above the painting**, because a box's
  children paint after its own content and the hit test takes the topmost. A
  press beside them lands on the `canvas-layer` box, whose chain bubbles to
  the canvas, so the canvas's `Input` hears it with `content()` in the
  painter's coordinates, as before. **A press on a widget does not reach the
  canvas's `Input`.** Events bubble, and the canvas is an ancestor of every
  widget on it, so the slot consumes every pointer event that reaches it in
  the bubble phase, after the widget has heard it. Without that, a press on an
  event's button would also be a press on the grid, and the week would open a
  quick-add under the event that was clicked. The wheel is let through,
  because it is aimed at whatever scrolls.
- **Semantics** come with the widgets, which are ordinary elements under the
  canvas and are found by role and name like any other.

**With ADR-0591.** A positioned widget can be `draggable` like any other, and
the canvas can be a drop target. An event dragged to another day lands on its
own canvas, which is an ancestor of the source and so not skipped, with the
point in the painter's coordinates. The slot consuming the press does not
stop the pickup, because the slot is outside the draggable. A press that does
not move still activates the widget.

## Not done

- **A `week-grid` widget**, the entry's alternative. The overlay is the
  general answer, and a week grid is the downstream's widget built on it.
- **A first frame with the widgets in it.** It would need the size before
  layout, and a canvas has no size of its own until layout gives it one.
- **No showcase card.** The example's input chapter, where its drag and drop
  cards live, is pinned by the `gallery-input` golden, and its Canvas screen
  is what the guide pictures for `canvas`. Extending either means retaking a
  picture in the same batch as other changes to the gallery, and a card
  linking a guide section that is still parked. It can come with the guide at
  the release.
- **Markup.** A `canvas` from markup still names no painter, and so no overlay.

## Consequences

- `Canvas` has a fifth component, and `children()` is no longer always empty.
  `Canvas.render` lays out only the layer, so a canvas still has no children
  of its own in flow.
- Two CSS types are new and unstyled by the base sheet: `canvas-layer` and
  `canvas-item`. A stylesheet may style them. A rule that gives `canvas-item`
  a size is overridden by the rectangle.
- New tests in `CanvasOverlayTest`: widgets placed at their rectangles from
  the content box's corner, placed again after the canvas widens with a
  focused widget keeping its element and its focus, Tab in list order rather
  than visual order, a press on a widget reaching it and not the canvas's
  `Input`, a press beside them reaching the `Input` in the painter's
  coordinates, a widget clipped at the content box for the hit test, the
  widgets listed by role and name, and a canvas with no overlay having no
  children. `CanvasDropTest` drags a positioned widget across its canvas and
  drops it there.
- The guide section is parked in
  `docs/snapshot/components-drawing-canvas-overlay.md`: a new
  `### Widgets over the drawing` under `## canvas`, and a sentence each for
  its Styling and Keyboard sections.
- Where reality differed from the entry: the widgets arrive a frame late, as
  the measured-size pattern does everywhere in the toolkit. A press on a
  widget is kept from the canvas's `Input` by the toolkit, not by the
  application checking the event's target.

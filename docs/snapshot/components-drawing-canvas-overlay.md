<!-- Three pieces, each says where it goes. -->

<!-- 1. Destination: book/src/components/drawing.md, under `## canvas`, a new
     section `### Widgets over the drawing` after `### Input` and before
     `### Attributes`. -->

### Widgets over the drawing

A painted rectangle has no focus ring, no name a reader can announce and no
place in the Tab order. Where something drawn has to have all three, such as
the events on a week grid, `overlay` places a real widget over it:

```java
new Canvas(week::paint, week).overlay(size -> week.layout(size).blocks().stream()
        .map(block -> new Positioned(
                new Pressable(block.title(), () -> open(block.event())).keyed(block.event().id()),
                block.rect()
        ))
        .toList());
```

The function is handed the size of the content box, the rectangle the painter
is told about, and answers with `Positioned` widgets, each at a rectangle in
the painter's own coordinates. Each widget fills its rectangle. They are
ordinary widgets inside the canvas's box:

- drawn over the painting and clipped to the content box, and scrolled with
  the canvas when a `scroll` moves it;
- hit-tested before the painting, so a press on one goes to it and not to the
  canvas's `Input`, and a press beside it reaches `Input` as before;
- focusable in the order of the list, so a list in visual order is a Tab
  order in visual order;
- listed in the semantics tree under the canvas, with their own roles and
  names.

The list is asked for again whenever the content box changes size and
whenever the canvas is rebuilt. A widget whose key comes back keeps its
element, and with it its focus and its state, so key each one by what it
shows. The widgets arrive one frame after the canvas is first laid out,
because the size they are placed by is the one that frame measured.

A positioned widget can be `draggable` like any other, and the canvas can be
a drop target: an event dragged to another day lands on the canvas with the
point in the painter's coordinates. See
[Dragging between widgets](../guide/input.md#dragging-between-widgets).

<!-- 2. Destination: book/src/components/drawing.md, `## canvas`,
     `### Styling`, at the end of the paragraph. -->

The widgets of an overlay sit in a `canvas-layer` covering the content box,
each in a `canvas-item` at its rectangle.

<!-- 3. Destination: book/src/components/drawing.md, `## canvas`,
     `### Keyboard`, at the end of the paragraph. -->

The widgets of an overlay are Tab stops of their own, after the canvas and in
the order of the list.

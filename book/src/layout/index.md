# How layout works

<p class="gb-lede">Every box in a Goldberry window is placed by flexbox, and this chapter is the vocabulary the eight chapters after it share.</p>

By the end of this chapter you can read a widget tree and say where each box lands. You also know which half of a layout the widget owns and which half the stylesheet owns.

## A row, a stylesheet, and the same tree in Java

<div class="gb-tabs">

```kdl
row id="toolbar" {
    text "Goldberry"
    spacer
    button press="theme" "Theme"
}
```

```css
#toolbar {
  gap: 8px;
  padding: 0 12px;
  align-items: center;
}
```

```java
var toolbar = new Row(
        new Text("Goldberry"),
        new Spacer(),
        new Button("Theme", this::theme)
).withAttributes(Attributes.NONE.id("toolbar"));
```

</div>

The `row` says which way its children go. The stylesheet says everything else: the gap between them, the padding at the ends, and how they line up across the row. The `spacer` takes the space nobody else asked for, so the button sits at the far end.

## Flexbox, from Yoga

Goldberry does not implement a layout algorithm. It binds Yoga, the flexbox engine, and the CSS subset compiles to Yoga's properties. The vocabulary an application sees is the toolkit's own. `Length`, `Insets`, `Limits`, `FlexDirection`, `Justify`, `Align`, `Wrap`, `Position` and `Overflow` live in `dev.goldberry.layout`, and Yoga is translated in one file nothing else touches.

Yoga runs with CSS's defaults rather than its own. A box shrinks when its row is too narrow, because `flex-shrink` is 1 unless a rule says otherwise. Keep that in mind when a control looks squashed. [Sizing with CSS](sizing.md) lists every property the subset accepts.

## Logical pixels

A `px` in a stylesheet is a logical pixel. The window's display scale is applied once, as a transform on the painting context, and nothing above it does arithmetic in device pixels. `8px` of padding is the same distance on a 100% display and a 200% one.

Edges land on whole device pixels. The render tree hands Yoga the window's scale as its point scale factor, and Yoga rounds every edge to the device grid. At 1x a row of 101 points splits 51 and 50. At 2x it splits 50.5 and 50.5, which is the same tree on a finer grid.

## The box model

A box has a width and a height, padding inside its edge, a margin outside it, and a border drawn on the edge. `min-width`, `max-width`, `min-height` and `max-height` bound it. An absolutely positioned child is placed from its parent's border box rather than its padding box, which is where Yoga and CSS differ.

## Three trees

A widget is an immutable record describing what should be on screen. Building it produces an element, which persists across rebuilds and holds state and focus. The element produces a render object, which owns a Yoga node, is laid out, and is painted. Layout runs on the third tree, so a rebuild that re-describes a node with the same type, id and classes keeps its computed style and its Yoga node.

## Text is a measured leaf

Yoga knows nothing about glyphs. A box with text in it carries a measure function. Yoga proposes a width, the paragraph wraps at it and reports a height, and the flexbox algorithm sizes everything around that answer. The paragraph is shaped once, and every re-wrap is arithmetic over the glyphs it already has. That is what makes the callback cheap enough to answer inside a layout pass.

A box therefore has text or children, never both. Yoga asks a measured node for its size and never lays its children out.

## What a widget owns and what CSS owns

A `row` is a row. Its `render` applies `flex-direction` after the stylesheet, so no rule can turn it into a column. That is the one layout property a widget keeps for itself. Gap, padding, margin, alignment, wrapping, growing, shrinking and sizes are all the stylesheet's, and the same tree looks different under a different sheet.

The split holds across the catalogue. A `scroll` clips and translates. A `split-pane` sizes its first pane in pixels, because the divider's fraction is a thing no `flex-grow` can express. A `masonry` deals cards into columns. Each of them leaves every number it does not have to own to CSS.

## Controls do not shrink

CSS's default `flex-shrink: 1` is wrong for a checkbox. The controls declare `flex-shrink: 0`, once, over a list of types. Text is deliberately left off that list. A label shrinks by wrapping, which is what a label should do.

When a widget of your own has a fixed size, give it the same declaration.

```css
my-widget { flex-shrink: 0; }
```

## The layout widgets

<div class="gb-cards">
<a class="gb-card" href="row-and-column.html"><strong>Row and column</strong><span>The two containers. One direction each, and everything else from the stylesheet.</span></a>
<a class="gb-card" href="spacer.html"><strong>Spacer</strong><span>Empty space that takes what is left over.</span></a>
<a class="gb-card" href="stack.html"><strong>Stack</strong><span>One child in flow and the rest drawn over it.</span></a>
<a class="gb-card" href="scroll.html"><strong>Scroll</strong><span>A viewport, its scrollbars, the wheel and the keyboard, and what a frame pays.</span></a>
<a class="gb-card" href="split-pane.html"><strong>Split pane</strong><span>Two panes and a divider you can drag or move with the keyboard.</span></a>
<a class="gb-card" href="masonry.html"><strong>Masonry</strong><span>Cards of unequal height, each under the shortest column.</span></a>
<a class="gb-card" href="affix.html"><strong>Affix</strong><span>A child pinned to the edge of its scroll once it would have left.</span></a>
<a class="gb-card" href="sizing.html"><strong>Sizing with CSS</strong><span>Every layout property the subset accepts, the units, and the tokens.</span></a>
</div>

## For widget authors

A widget that lays out children returns a `Box` from `render`. The direction is a call on the box, the stylesheet arrives as `style`, and the children arrive already rendered.

```java
@Override
public Box render(ComputedStyle style, List<Box> boxes, Context context) {
    return Box.of()
            .children(boxes.toArray(Box[]::new))
            .style(style)
            .direction(FlexDirection.ROW);
}
```

`direction` is called after `style` so that it wins. A box that should take the stylesheet's direction leaves the call out. The rest is in [Writing a widget](../guide/writing-a-widget.md).

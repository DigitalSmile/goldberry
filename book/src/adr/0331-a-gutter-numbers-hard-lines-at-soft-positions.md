# 331. A gutter numbers hard lines at soft positions

Date: 2026-09-16

## Status

Accepted. Closes `docs/gaps.md` G37.

## Context

`text-area` is already an editor pane in everything but one respect: soft wrap at
the control's width, `fill(true)` so it takes the height a container gives it
rather than growing to fit, the bundled monospace behind `class="mono"`, and an
editing model — selection, clipboard, undo, word operations, `Up`/`Down` by visual
line — that is `text-input`'s and needed no second copy.

What it did not have is line numbers, and they cannot be composed from outside.
That is the whole entry: the numbers have to line up with **hard** lines drawn at
**soft**-wrapped positions, and only the thing that laid the text out knows where
those fell. A `Column` of numbers beside the pane is correct until the first line
that wraps, and then every number below it is wrong — which is worse than having
none, because it looks like it works.

## Decision

**One flag on the widget that has the layout.**

```java
public TextArea gutter(boolean on);
```

```kdl
text-area class="mono" gutter=#true fill=#true bind="note.body" change="note.type"
```

A boolean and not a renderer. What a line number *looks* like is the stylesheet's,
and an application that wanted to draw something else in that column is asking for
a different widget rather than a parameter.

### The numbers are one paragraph, not one node per line

This is the part worth recording, because the obvious design does not work.

A widget's children are described in `children()`, which runs **before** anything
is laid out — `render()` is where `Paragraph.layout` happens. So a column of
`text-area-line-number` nodes could only be built from the *previous* frame's
wrap, and would therefore be a frame behind the text on every keystroke that
changed the line structure. Worse, nothing would request the correcting frame: the
rebuild a keystroke causes is the frame that gets it wrong.

So `TextAreaBox` draws the numbers itself, in `render()`, as **one** text box whose
content is a number per hard line and an **empty line per wrap**:

```
"1\n2\n\n\n3"     lines one, two — which wrapped into three — and three
```

Drawn in the control's own font at the control's own line height and inset by the
control's own scroll offset, so the numbers cannot drift from the text by
construction rather than by two pieces of arithmetic that have to be kept
agreeing. Scrolling is one inset for both, which is why they cannot shear.

### The strip is still a node

`TextAreaGutter` — `text-area-gutter` — is a real child and carries the column's
fill. It is pinned top and bottom so it runs the height of the control rather than
stopping where the text does, and pulled out to the border on the left so the
control's own padding sits inside it.

The numbers' **ink** is `--gb-gutter-color`, read through `Paints.Context.color`,
and the room on each side is `--gb-gutter-gap` through `Paints.Context.length`.
That is exactly what those two accessors exist for (ADR-0195, ADR-0251): a widget
drawing something the property set has no declaration for still has to be
themeable. Both are declared on `text-area` in `controls.css`, muted and 8px.

There is **no rule down the gutter's edge**, and that is §8's subset rather than an
oversight: `border` is a shorthand over four edges with no per-edge longhands
(ADR-0107), so `border-right` is not a declaration that exists. One step of surface
tells the column from the text, which is how `table`'s header strip is told from
its body.

### The width comes off the text

The column is measured from the **document's** last line number rather than from
the one on screen, so the text does not slide left and right as a long note is
scrolled, and at least two digits wide, so a note does not visibly shift the first
time it passes line nine. It is measured in the node's own font — a `mono` editor
and a body one have different digits — which is why it is computed in `render` and
handed down rather than guessed at either end.

`AreaEditor.laidOut` gains a `gutter` parameter, and everything that measures
against the content moves with it: the wrap (`contentWidth`), the caret, the
selection rectangles and where a click lands. It is a separate number from the
padding rather than folded into it, because the two are not used the same way —
the padding is on both sides and comes off the wrap twice, the gutter is on one and
comes off once.

## Consequences

- `TextArea` grows two components in this record and two more in ADR-0332. An
  eleven-argument constructor is kept for the call sites that build one
  positionally.
- A `text-area` with no gutter is byte-for-byte the control it was: `gutterWidth`
  is 0, no `TextAreaGutter` is built, and every inset is what it was before.
- `text-area-gutter` is the only new CSS type. There is deliberately no
  `text-area-line-number` node, and the javadoc on both classes says why — so the
  next person to reach for one finds the reason rather than the absence.
- An application cannot change what a line number *says*. Relative numbering, a
  fold marker or a diff gutter would all be different widgets. That is the
  boundary a boolean draws, and it is the one the entry asked for.

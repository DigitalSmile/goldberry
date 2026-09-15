# 318. A line starts where the paint says it does

Date: 2026-09-15

## Status

Accepted. Closes `docs/gaps.md` G30. Finishes
[ADR-0256](0256-a-line-is-placed-by-the-paint-not-by-the-box.md), which gave the
paint an alignment and left everything that *measures* the same text without one.

## Context

ADR-0256 put `text-align` in the one place that had both numbers it needs — a
line's own width and the width of the box — and that place is
`Paragraph.paint`. The indent was a private static method four lines long:

```java
private static double indentOf(double width, double maxWidth, double fraction) {
    if (fraction == 0 || !Double.isFinite(maxWidth)) {
        return 0;
    }
    return Math.max(0, maxWidth - width) * fraction;
}
```

`text.edit.TextGeometry` is the other half of the same subject — *where is the
caret, what did the click land on, what does `Up` mean* — and it measured every x
from the paragraph's origin:

```java
var x = paragraph.widthBetween(line.start(), Math.max(line.start(), offset));
```

So the painter drew each line indented by its share of the box's slack and the
caret was measured as though no line ever moved. The two parted company the moment
the text was not left-aligned: the caret drifted from the glyphs by half the line's
slack under `center` and by all of it under `end`, and the drift **grew as the line
shortened**, which on a wrapped paragraph means every line is wrong by a different
amount.

An application hit it first, because a board's default shape is a centred sticky,
and it did the only thing it could: wrote the indent rule out a second time and
added it to every x it computed and subtracted it from every x it was handed. That
works, and it is exactly the duplication `docs/gaps.md` exists to stop — if
`Paragraph.paint`'s indent ever changes (a justified alignment, an RTL line), the
copy is silently wrong and only a round-trip test says so.

It was never only an application's problem. A `text-area` whose stylesheet centred
it would drift the same way the day anything wires `ComputedStyle.textFlow()` into
the box that draws its value.

## Decision

**The rule moves to the property, and the geometry is told the alignment.**

`TextAlign.indentOf(lineWidth, available)` is now the one implementation:

```java
public double indentOf(double lineWidth, double available) {
    var fraction = fractionOfSlack();
    if (fraction == 0 || !Double.isFinite(available)) {
        return 0;
    }
    return Math.max(0, available - lineWidth) * fraction;
}
```

`Paragraph.paint` calls it. So does every method of `TextGeometry`, each of which
gained a form that takes the width the text was drawn in and the alignment it was
drawn with:

```java
TextGeometry.caretAt(paragraph, layout, offset, wrapWidth, TextAlign.CENTER)
TextGeometry.offsetAt(paragraph, layout, x, y, wrapWidth, TextAlign.CENTER)
TextGeometry.moveLine(paragraph, layout, offset, lines, desiredX, wrapWidth, TextAlign.CENTER)
TextGeometry.selectionRects(paragraph, layout, start, end, wrapWidth, TextAlign.CENTER)
```

The existing shorter forms stay and mean `START`, which is what every caller
written before this assumed.

**`selectionRects` is in the list although the gap did not ask for it.** A
highlight drifts exactly as a caret does and for the same reason — a selection is
geometry the frame already had ([ADR-0301](0301-a-selection-is-geometry-the-frame-already-had.md)) — and
three of four corrected would have been a fourth bug waiting.

**Which indent comes off which x** is the one subtlety. `caretAt` *adds* the
indent of the line the offset is on. `offsetAt` *subtracts* the indent of the line
the `y` lands on, because the x it is given is where the user pressed. `moveLine`
subtracts the **target** line's indent from `desiredX`, not the source line's,
because `desiredX` is an x a caller read off a `Caret` and is therefore already in
the painted space — that is what makes a run of `Down` through lines of different
lengths keep the column it looks like it is keeping.

`Editor` carries a `textAlign` of its own and hands it to all four, so the canvas
editor is correct end to end: the paint, the caret, the hit test, `Up`/`Down` and
the selection move together. Setting it invalidates **no layout** — alignment
changes where a line starts, not where it breaks, which is the whole reason it can
be a late decision.

## Consequences

An application that had written the rule out a second time deletes it and passes
two more arguments. It cannot drift again, because there is no second copy to
drift from.

The `text-area` and `text-input` controls are unchanged and still ignore
`text-align` altogether: both measure their own carets against their own origin,
and both draw their value through `Box.text(paragraph, argb)` with the default
flow, so neither indents its glyphs either. They are consistent today and they are
ready — the day one of them passes `style.textFlow()` down, `TextGeometry` has the
form it needs and the caret follows. Wiring it is not this ADR: those two controls
hold the most delicate geometry in the catalog, and a change there deserves its own
measurements.

## Alternatives considered

**Leave the rule in `Paragraph` and expose it.** A public `Paragraph.indentOf`
would be one implementation too, and it would put a property of `text-align` on the
class that happens to paint. `TextAlign` already owned `fractionOfSlack`; the
distance is the same question one step further on.

**Have `TextGeometry` take a `TextFlow` rather than a `TextAlign`.** A flow also
carries `white-space` and `text-overflow`, and neither means anything to a caret:
the layout it is handed has already broken the lines, and a truncated line has no
caret in the part that was cut. Taking the one value it uses keeps it impossible to
pass a flow that disagrees with the layout.

**Take an origin instead, and let the caller do the arithmetic.** That is what the
application's stopgap was, moved inside the signature — the caller would still need
the rule to compute the origin, per line, which is the thing being centralised.

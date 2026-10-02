# ADR-0536: A border is dashed, dotted or double as written

- **Status:** Accepted. Amends [ADR-0505](0505-a-border-has-four-sides-and-takes-no-room.md)
  ("style is a word inside a shorthand and nothing else", drawn solid) and the
  precedent [ADR-0310](0310-a-shadow-is-a-stack-of-rectangles.md) cites from
  it.
- **Date:** 2026-10-02
- **Relates to:** [ADR-0427](0427-the-shadow-is-cut-out-of-its-box.md),
  [ADR-0529](0529-an-applications-stylesheet-is-lenient-and-loud.md),
  `docs/goldberry-gaps.md` #13

## Context

`border: 1px dashed red` parsed, and the style keyword was thrown away. The
side was drawn solid and a line went to the debug log, which nobody reads.
Deploy Orc's dashed tag, its demo box and its locked-node rings all came out
solid. ADR-0310 used this as its precedent for keeping the first of a shadow
list: "drawing something is the more useful of the two wrong answers". That
was a fair reading while the painter had nothing better to draw. Now it does.
`paint.geom.Dasher` cuts a path into a dash pattern, and `Frame.strokePath`
strokes one.

## Decision

**`Border.Line` carries a `Style`. Solid, dashed, dotted and double are drawn
as CSS draws them. The four bevelled styles are drawn solid, and the parser
says so once at warn.**

- `Border.Style` has CSS's eight keywords. A shorthand that names none is
  `SOLID`, which is how every rule the toolkit ships is written. The width
  and colour longhands keep a side's style, and `none`/`hidden` are still a
  zero width.
- A uniform solid border keeps its one stroked rectangle, and mixed solid
  sides keep their mitred regions. No golden moved.
- A styled side is drawn along its **run**, the centreline from one corner to
  the next (`paint.border.BorderPattern`). At a rounded corner the run starts
  or ends half-way round the arc, so the two sides share the corner. At a
  square corner a dashed horizontal side runs to the outer edge and owns the
  corner. A dotted side ends where its centreline meets its neighbour's.
- **Dashed** is a dash three widths long and a gap the same, butt-capped. The
  gaps are stretched so that a side starts and ends on a dash: a whole dash at
  a square corner, and half a dash at a shared rounded one, so the corner
  carries one dash. A side too short for two dashes is drawn whole.
- **Dotted** is round dots as wide as the side, two widths apart, with one on
  every corner. Each dot is filled as a circle rather than a zero-length
  round-capped dash, because a zero-length subpath is not reliably a round
  cap. A corner shared by two dotted sides gets one dot.
- **Double** is two bands a third of the width each. They are drawn as two
  thinner borders through the region painter, one at the edge and one inset
  by two thirds, so they mitre and follow the corners as a solid side does.
- **Groove, ridge, inset and outset** are drawn solid, with one warning per
  style. CSS leaves their shading to the browser, and no rule here asks for
  one.
- The solid sides of a mixed border are still regions. A styled side keeps
  its width with its ink taken away, so the corners divide as they would if
  every side were solid.

## Consequences

- Deploy Orc's dashed and dotted outlines draw as written.
- A box whose border is solid pays one `isDrawnSolid()` check per paint.
- `outline` reads the style keyword and draws solid, since the focus ring is
  pinned solid by the design system.

## What is not done

- No `border-style` or `border-{side}-style` longhands, as ADR-0505 decided.
  `StyleLint` still reports them.
- A dashed or dotted side on a corner whose two widths differ draws its own
  half of the corner at its own width. The joint shows a step, as it does in
  browsers.

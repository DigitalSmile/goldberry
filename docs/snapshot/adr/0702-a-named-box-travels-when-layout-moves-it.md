# ADR-0702: A named box travels when layout moves it

- **Status:** Accepted. The opt-in is the implementer's choice (option a
  below), made on 2026-11-14 when the owner had not answered; the owner may
  swap it before the release.
- **Date:** 2026-11-14
- **Relates to:** ADR-0600 (CSS's curves in an application's sheet)

## Context

A messenger's call grid rearranges its tiles when someone joins, and its board
zooms to fit. Each tile jumps to its new place (Tessera's G-16, here T-16).
Tessera animates tile transforms by hand from measured bounds, which needs
the bounds of the frame before and a clock it does not own. What it asks for is
FLIP: draw a moved box where it was, then carry it to where layout put it,
with the box's identity surviving a move to another parent.

The plan left two questions open: how a box opts in, and how it keeps its
identity across a parent change. Three ways to opt in were weighed: (a) the
standard `view-transition-name`; (b) a property of Goldberry's own, such as
`layout-transition: tile 300ms`; (c) a wrapper widget with a key and a
timing.

## Decision

**(a) `view-transition-name: <custom-ident> | none`.** It is CSS's own name
for a box's identity across a change, it is a name rather than a position
in the tree, so a tile moved to another parent is still the same tile, and
every property in the cascade stays a CSS one. `auto`, `match-element` and
`default` are dropped with the usual warning.

Where Goldberry departs from browsers, and says so in the guide: a browser
animates only inside `document.startViewTransition()`, with snapshots in
pseudo-elements, while here every layout change that moves a named box moves
it smoothly, drawing the box itself.

**The timing** is the box's own `transition` for `transform` when it
declares one, since what moves the box is a transform. Otherwise it is View
Transitions' default, 250 ms `ease`. Under reduced motion it is instant.
`WidgetRenderer` puts the timing on the name as it builds the box, because it
alone knows the box's transitions and whether motion is reduced.

**The carrier** is `Transform`, which gained a fifth component,
`ViewTransition` (name, duration, easing). `Transform` already travels from
the cascade through `Box` to the render tree, so no new field crosses
`ComputedStyle` or `Box`, and `Transform.isNone()` still answers for the
matrix alone.

**The move** is computed after layout by `LayoutMotion` in the render tree,
over `LayoutTracks` in a new internal package, `motion.layout`. Each name
remembers where its box was laid out. When that changes by more than a
quarter pixel, the box is drawn where it was last drawn and travels to where
it is, translating and stretching (FLIP's "invert, play"). The move is a
matrix on the render object, in the box's own coordinates, which `compose`
applies after the box's own `transform`. Painting, hit testing, damage,
culling ink and layer bounds all compose through there, so a box mid-move is
drawn, clicked and repainted where it is seen. A named box inside a moving
named box takes its ancestor's move off its own and travels its own path. A
box without a name moves with its parent.

**The clock** is the renderer's: `RenderTree.update(frame, box, now)` takes
the frame's time, `FrameSequence` passes `WidgetRenderer.frameMillis()`, and
while anything travels the renderer answers `isAnimating()`, so a window, a
filmstrip and a session keep producing frames until it lands. The old
`update(frame, box)` has no clock and puts every box straight where it is.

## Consequences

- A tree without a named box costs nothing: reconciliation already visits
  every node and notes a name, and with none the motion pass is skipped.
- A box that changes size is stretched while it travels, its content with
  it. FLIP's scale correction for children is not done.
- A move interrupted by another layout change sets off from where the box
  is drawn, so it never jumps. A name used twice in one frame moves only its
  first box. A box that leaves and comes back arrives in place.
- After a move changes a box's matrix, the ink of its ancestors is measured
  again, and only along those paths. A promoted ancestor's raster is redrawn,
  because it bakes the descendant in.
- Tested by `LayoutTracksTest` and `StretchTest` (the arithmetic),
  `LayoutMotionTest` (the render tree: drawn where it was, halfway, arrived;
  damage over both places; interruption; stretching; instant and clockless;
  a parent change; children following; a nested named box),
  `ViewTransitionNameTest` (the cascade), `ViewTransitionTimingTest` (the
  timing and reduced motion) and `ViewTransitionSessionTest` (end to end
  through a session: the frame loop, and the pointer finding the box where it
  is drawn). `PropertiesTest` and the conformance table list the property.

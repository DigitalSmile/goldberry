# ADR-0603: `display: none` and `contents` are the renderer's, and every box stays a flex box

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-10, CSS-11), `docs/css-support-2026-10-10.md`
  §4

## Context

`display` was not a property at all, and the analysis called `display: none`
the biggest single surprise for a CSS author. Yoga has `YGDisplayNone` and
`YGDisplayContents`, and the binding had the first. A box Yoga is told not to
display still sits in the render tree, though, and the paint walk, the hit
test, the ink pass and the focus order would each have to learn to skip it.

The toolkit already has the behaviour `display: none` describes, under another
name. A widget whose `Styled.isHidden()` is true contributes no box, nothing
under it is rendered, its elements stay mounted with their state, and the
router keeps focus out of it. That is how a background tab is kept alive.

## Decision

**`display` is read as `flex`, `none` or `contents`, and the renderer acts on
it before any box exists.**

- `display: none` is a hidden node a stylesheet chose. After the node's style
  is resolved, `WidgetRenderer` returns no box for it and renders nothing
  under it, exactly as for `isHidden`. The router's `isHidden` walk asks
  `Element.isDisplayNone` as well, so focus cannot move into it and lets go of
  focus that was there. Yoga, the paint walk and the hit test never see the
  node, and none of them changed.
- `display: contents` hands its children's boxes to its parent in place of
  its own, which is what the renderer already does for a composition node.
  Whatever the widget would have drawn in its own box, its text or its icon,
  goes with the box. CSS keeps an element's text as anonymous content; here a
  widget's drawing *is* its box.
- `display: flex` is what every box already is. `block`, `inline`, `grid` and
  the rest are dropped with a warning: every box is a flex box, and a sheet
  that asked for flow layout would get a flex box laid out unlike what it
  wrote.
- `aspect-ratio` (`auto`, a number, or `w / h`) reaches Yoga through
  `Box.aspectRatio`, NaN for `auto`.
- Both live in one record, `dev.goldberry.layout.Presence`, as one component
  of `ComputedStyle`, so the properties still to come of the same kind
  (`visibility`, `pointer-events`, `z-index`) grow that record instead of the
  36 positional constructor calls of `ComputedStyle`.

## Consequences

- No Yoga binding changed, and `YGDisplayContents` is not bound: it would only
  be needed if a box with no box of its own had to reach Yoga.
- `display` is not animatable. A node that becomes `display: none` and comes
  back starts its first frame again, so an `@starting-style` rule runs on its
  return, which is what CSS does too.

## Status

Done 2026-10-11: `Display` and `Presence` in `dev.goldberry.layout`, the
`display` and `aspect-ratio` arms, `Box.aspectRatio` applied in
`RenderObject`, the renderer and router changes, and tests (`DisplayTest`,
`AspectRatioTest`, `PropertiesTest`, the conformance table).

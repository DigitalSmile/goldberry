# ADR-0620: A border takes room, and `box-sizing` is `border-box`

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-47), `docs/css-support-2026-10-10.md`
  §5; supersedes the "takes no room" half of ADR-0505 (a border has four sides
  and takes no room), and with it CSS-36's "a border takes no room here";
  native ABI 23 → 24

## Context

A border here was paint only. `BorderPainter` drew it inside the box over the
padding, Yoga never heard of it, and a stylesheet padded every bordered box at
least as wide as its border. `ContainingBlock.insetFor` shifted an absolute
child's inset by the containing block's border, because Yoga measured the
inset from an outer edge that, as far as it knew, had none. Every place that
needed the content box (text, a `canvas`, the overflow clip, the rounded clip,
the hit test, `background-origin`) took the box less its padding.

`box-sizing` was unknown. Yoga 3.2 has `YGNodeStyleSetBoxSizing`, but
`libgoldberry` did not export it. CSS-47 asked which model to make the default:
CSS's `content-box`, `border-box`, or the current one. The user decided on
2026-10-11: borders take room, `border-box` is the initial value, and
`content-box` is there on request.

## Decision

**A border is laid out, outside the padding, as in CSS. `box-sizing` is read,
and its initial value is `border-box`.**

- `libgoldberry` exports `YGNodeStyleSetBoxSizing` (ABI 24), and
  `natives.yoga.style.BoxSizing` binds `YGBoxSizing`, checked against the
  shim's constant table like every Yoga enum.
- `layout.BoxSizing` rides on `Presence`. It does not inherit.
- `RenderObject.apply` gives each Yoga node the four border widths and the box
  sizing, guarded like every other style call. A change of border width is a
  change of layout.
- Yoga now measures an absolute inset from the padding box itself, so
  `ContainingBlock.insetFor` hands the inset back unchanged, `inContentBox`
  adds the padding only, and `acrossBorderBox` still moves out by the border.
- `paint.BoxEdges.content(box, w, h)` is the content box, border plus
  padding, and every reader of it goes through it: `BoxPainter` (text and
  `canvas`), `BackgroundArea`, `RenderTree.clipFor` and `transform-box`,
  `RoundedClip`, `ClipShapes` and `HitTest`. The text field and the text area
  measure their text from the outer edge by border plus padding.

## Consequences

- A box given a size keeps it, and its content is narrower by the border. A
  box sized by its content grows by its border: a chip, a menu, a tooltip, a
  dialog and a toast are 2px larger with a 1px border, and what is inside them
  sits 1px further in. The toolkit's sheets were not adjusted to cancel it.
  Every golden and book picture that shows such a box was retaken, about two
  hundred.
- `ScaleInvariance`'s budget went from 1.2% to 1.5% of gross pixels. One
  banner stack's fractional line height now crosses a half pixel, so 2x rounds
  it differently from 1x. A geometry fault is still several times over.
- The macOS and Windows `libgoldberry` need a CI rebuild for ABI 24, with
  CSS-49's 23.

## Status

Done 2026-10-11: `YGNodeStyleSetBoxSizing`, `natives.yoga.style.BoxSizing`,
`YogaNode.setBoxSizing`, ABI 24; `layout.BoxSizing`, `Presence.boxSizing`, the
`box-sizing` arm and longhand, `RenderObject`'s border and sizing calls,
`ContainingBlock`, `paint.BoxEdges` and its readers, `TextField`,
`AreaPadding`; `BorderPaintTest`, `ContainingBlockTest`, `TextInputTest`,
`TextAreaTest`, the conformance rows, and the retaken pictures.

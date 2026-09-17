# 350. A gutter strip is outside the clip, and its numbers are inside it

Date: 2026-09-17

## Status

Accepted. Closes `docs/gaps.md` G43, a defect in
[ADR-0331](0331-a-gutter-numbers-hard-lines-at-soft-positions.md). Relies on
[ADR-0272](0272-an-absolute-child-is-placed-inside-the-padding.md)'s
`ContainingBlock`. `text-input`'s matching arithmetic is fixed by
[ADR-0355](0355-a-button-leaves-a-field-counts-both-paddings-and-a-floor-starts-on-its-first-frame.md).

## Context

An application reported two things about `text-area gutter=#true`.

**The strip did not reach the padding.** With `padding: 12px 16px`, the filled
column started 12px down and 16px in, and a band of the field's own background
showed above it and to its left. The insets were right: `-padding` on three
edges, which `ContainingBlock` shifts back to the border box. The clip cut the
strip. `RenderTree.clipFor` clips a box's children to the box less its padding,
which is the content box, and a field is `overflow: hidden` so that scrolled text
stops at the content box. The strip was a child like the text, so the same clip
cut it by exactly the padding.

Changing the clip for every box is not the fix. Borders here are drawn inside
the padding rather than laid out, so "inside the border" is not an edge the
render tree knows, and every scroll view, field and golden is built on the clip
as it is.

**The numbers were said to drift after a wrap.** The report was that with
`--gb-gutter-gap` other than 8 and a non-zero left padding, numbers sat one
visual line too high after the first soft wrap. That could not be reproduced
against this checkout. It was rendered with each combination the report named,
and the numbers and the text wrap at one width in every case. What the
reproduction *did* find is a real width error beside it:
`TextAreaState.contentWidth()` subtracted `2 * leftPadding`. That is right for
every stylesheet the toolkit ships and wrong for `padding: 12px 16px 12px 4px`,
where the text wrapped 12px wider than its room and ran under the right padding.
`visibleRows()` did the same with `2 * topPadding`.

## Decision

**The field draws two layers. A content layer is pinned to the content box and
clips, and the strip is its sibling under a field that does not clip. The wrap
subtracts each padding edge once.**

- **The content layer** is an absolute box with a zero inset on all four edges.
  `ContainingBlock` shifts each edge by the field's padding, which lands the box
  on the content box. It has no padding of its own, so every part inside it (the
  washes, the text, the caret, the composition's rules and the numbers) is placed
  in exactly the coordinates it was before. It is `overflow: hidden`, which is
  the clip the field used to have.
- **The strip** sits beside that layer and is inset from the border's *inner*
  edge: each inset is `borderWidth - padding`. The field no longer clips it, and
  it would otherwise paint over the field's 1px edge. Its two leading corners
  take the field's radius less the border width, so a rounded field keeps its
  curve. Those corners override any radius a stylesheet gives `text-area-gutter`,
  which has no shipped rule for one.
- **`AreaPadding`** carries all four edges from `render` to the state, replacing
  the two-edge record. The wrap subtracts `left + right` and the visible height
  subtracts `top + bottom`.

## Consequences

- `text-area-gutter { background: … }` fills the column from border to border,
  and the stopgap `background: transparent` in the reporting application can go.
- A field with asymmetric padding wraps inside its room. `TextAreaGutterStripTest`
  checks the right padding for ink across five padding and gap combinations, and
  checks that scrolled text still stops at the top padding.
- `gallery-forms` and `gallery-forms-light` changed where the numbered area's
  strip now fills the padding, and nowhere else.
- The box tree under a `text-area` is one level deeper. Tests that walk it look
  in the last child, as `TextAreaGutterTest` does now.
- `text-input` still computes its room as `width - 2 * leftPadding`. Its padding
  is symmetric in every shipped stylesheet, and the same asymmetry would scroll
  the text 12px late rather than wrap it wrong. It is filed in `TODO.md` rather
  than widened into this change.
- If the drift in the report came from a published snapshot older than this
  checkout, this ADR does not close it. The reporter's own rendering test is the
  check.

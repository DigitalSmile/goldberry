# ADR-0064: A rounded rectangle is four cubics, and opacity is a multiply

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/design-system.md` §1.5, §2.1, §2.2; `docs/ARCHITECTURE.md`
  §8; extends [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md) and
  [ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md)

## Context

The design system pins four numbers that nothing in the toolkit could draw:

- **Radii** — 4, 8, 12, `full` (§1.5). `Box` filled axis-aligned rectangles.
- **The focus ring** — 2px `--gb-focus`, 2px offset, following the control's
  radius (§2.2). `controls.css` faked it with a background change and said so in
  a comment.
- **Borders** — the checkbox's glyph is an outlined square before it is a filled
  one, and there was no outline.
- **`:disabled` at 45% opacity, never colour-remapped** (§2.1). `ComputedStyle`
  had parsed `opacity` since the CSS engine landed and `Box.style` dropped it on
  the floor with a comment explaining that group opacity needs a layer.

Every one of these blocked `button` from complying with its own §3 metrics row,
and three of the four blocked `checkbox` from existing at all. They arrive
together because they are drawn by one shape: a rounded rectangle, filled for the
background, stroked inward for the border, stroked outward for the ring.

## Decision

### The path is built from cubics, not bound

Blend2D has a round-rect geometry call. Using it would mean **adding a symbol to
the export list**, and that list has caught the same class of bug three times —
`--exclude-libs,ALL`, then Blend2D's `BL_STATIC` making `BL_API` expand to
nothing, then HarfBuzz's bare `HB_EXTERN` — each of which linked a symbol in and
left it *local*, and each of which was only answered by a CI run across four
targets. The MSVC `.def` and Mach-O `exported_symbols_list` branches are still
answered by the next run rather than by argument.

`bl_path_cubic_to` is already exported and already exercised by 1544 Lucide
icons. A quarter circle as a cubic with control points at `κ = 0.5522847498` of
the radius is off by about one part in 10,000 — at a 12px corner, a thousandth of
a pixel, well inside the golden images' tolerance
([ADR-0050](0050-golden-images-have-a-tolerance.md)). So `RoundRect` builds the
path and **no new symbol crosses the boundary**. The corner works on every
platform on the first run, rather than on the run after the one that found out.

The radius is clamped to half the shorter side, which is CSS's own rule and what
makes `border-radius: 9999px` a pill rather than an error — so §1.5's `full` is
expressible without a keyword.

### Six properties, one record

`border-radius`, `border-width`, `border-color`, `outline-width`,
`outline-color`, `outline-offset`, plus the `border:` and `outline:` shorthands.
They live in one `Decoration` record on both `ComputedStyle` and `Box` rather
than as six components on each, because they are only ever read together: the
painter that strokes a border needs the radius to stroke it along, and the ring
needs both to sit outside them.

Percentages are **refused** rather than carried. A percentage radius resolves
against the box's own size, and the box has no size until Yoga has run — long
after the cascade. `border-radius: 50%` is a dropped declaration with a warning
naming it, rather than a corner that is silently square.

The ring is `outline` rather than a widget's own drawing for one reason: a widget
that drew its own would have to know its own radius to follow it, and §2.2's
numbers would then live in as many places as there are controls. As a rule in the
toolkit-base layer it is written once:

```css
button:focus-visible,
checkbox:focus-visible {
  outline: 2px solid var(--gb-focus);
  outline-offset: 2px;
}
```

CSS outlines are drawn outside the border box and take no space, which is exactly
what a ring at a 2px offset needs — it cannot move a control by existing.

### Opacity multiplies alpha down the subtree

`BoxPainter` accumulates `opacity` as it walks, and hands the visitor a box whose
colours already include every opacity above it. `Box.fade(alpha)` scales the
alpha of the background, the text, the icon, the mark, the border and the ring;
`alpha >= 1` returns the same box, so an ordinary frame allocates nothing.

**This is not CSS group opacity, and the difference is stated rather than
hidden.** The specification renders the subtree into a layer and composites that
layer once, so two overlapping children at `opacity: .5` each show the backdrop
rather than one showing through the other. Multiplying per box differs *exactly
where children overlap*, and nothing in the widget canon overlaps: a control is a
mark, an icon and a label placed side by side by Yoga. The design system asks for
opacity in one place — `:disabled` at 45% — and there the two are
indistinguishable.

`stack` (§11, z-layering) is the widget that will make the difference visible,
and a compositing layer is what it will need. It is also what damage tracking and
the animation overlay (§1.7's "layer promotion") want, so the three arrive
together or not at all.

### A disabled control does not light up

Removing the colour remap exposed a second problem: `button.danger:disabled` and
`button.danger:hover` no longer fought, so a disabled button lightened under the
pointer. CSS would spell the fix `:not(:disabled):hover`, and `:not()` is not in
§8's subset. Writing it out is a rule per variant per state per control — a dozen
selectors, each able to be wrong on its own.

Instead `PointerRouter.mark` — the single choke point for `:hover` and `:active`
— refuses to *set* a state on a disabled widget. Clearing always goes through, so
a button that disables itself in its own press handler does not keep the state.
The `ENTERED`/`EXITED` **events** are not suppressed: this is about what a
control looks like, not what it is told, and a tooltip explaining why something
is disabled needs the event.

## Consequences

- `button` complies with §3's metrics row: height 32, padding-x 12, gap 6,
  radius 8, the design system's ring, and 45% opacity when disabled. Four golden
  images were regenerated and the diff is the argument.
- Disabled is **one number** instead of eight tokens. `--gb-button-disabled-bg`,
  `--gb-button-disabled-text` and `--gb-button-bg-focus` are gone from both
  themes: the first two were the colour remap §2.1 forbids, and the third was the
  focus stand-in. A disabled `danger` button now still reads as dangerous, which
  a remap to one grey surface had made impossible.
- One `BlendPath` is allocated per `paint` call and `reset` between shapes,
  rather than one per rounded corner per box. A path is a native allocation and
  an arena; a window of forty rounded controls would otherwise make eighty of
  them a frame to draw the same four arcs.
- `ComputedStyle.with` is now written with per-field withers instead of
  fourteen-argument positional constructor calls. The old form was correct and
  unreadable — a reader could not tell a case that set `width` from one that set
  `height` without counting commas, which is precisely the mistake the shape
  invites.
- **Still not expressible, and absent rather than approximated:** the
  `body-strong` weight on a button's label, which needs a second Inter face (or
  the variable font's `wght` axis) and the typography-token scale of §1.4; and
  every transition in §1.7, which needs a frame clock and an animation overlay.
  Both are in `book/src/status.md` as open, not faked with a value that looks
  close.
- **Untested claim:** the arcs have only been rasterized on linux-x64. Blend2D
  JITs its pipelines per CPU, so the corners on AVX-512, on an Apple Silicon NEON
  path and under MSVC are answered by the next CI run — which is what the golden
  images with their per-channel *and* area tolerance are for.

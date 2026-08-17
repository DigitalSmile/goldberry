# ADR-0066: A weight is a face, and `color` inherits

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/design-system.md` §1.4, §3; `docs/ARCHITECTURE.md` §6.1,
  §8, §10.1; extends [ADR-0044](0044-one-face-many-sizes.md) and
  [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md)

## Context

Two things arrived together because one turned out to be the other's blocker.

**The bug.** A checkbox's label rendered black on the dark theme — invisible.
`controls.css` says `checkbox { color: var(--gb-text) }`, and the label is a
`text` **child element** that no rule names, so it resolved to
`ComputedStyle.INITIAL`'s black. `button` had never shown this because
`Button.render` copies `style.color()` onto its child boxes by hand and bypasses
the cascade entirely; `checkbox` was the first control whose content was real
elements.

The cause: `StyleResolver` inherited **custom properties only**. Ordinary CSS
inheritance did not exist. Nothing had needed it, because until `checkbox` every
piece of text in the toolkit was either styled by an explicit rule or handed its
colour in Java.

**The gap.** `docs/design-system.md` §3 puts `body-strong` on a button's label
and `controls.css` said, in a comment, that it could not express it. Every one of
§1.4's seven typography tokens is a `font-size`, a `line-height` and a
`font-weight` — and all three **inherit**. So typography could not land until
inheritance did.

## Decision

### Inheritance is a named half of the property set

`ComputedStyle.of(declarations, context, parent)` seeds the inherited properties
from `parent` and starts everything else at `INITIAL`. The inherited half is
`color` and `typography`, listed in one private method so that adding to it is
one edit.

Two properties are **deliberately excluded** although CSS inherits them:

- **`cursor`.** Goldberry inherits it through the stack of painted rectangles
  instead — hit testing reads it off whichever box the pointer is over
  ([ADR-0057](0057-the-cursor-rides-on-the-painted-box.md)). Inheriting it here
  as well would be two mechanisms for one property, and they would disagree the
  first time a box was styled with no element behind it.
- **`opacity`**, which CSS does not inherit at all — its *effect* does, and the
  painter accumulates it down the box tree
  ([ADR-0064](0064-a-rounded-rectangle-is-four-cubics.md)). Inheriting the value
  would apply it once per level: a label under a control at 45% would be drawn at
  20%.

`WidgetRenderer` now **resolves styles on the way down and builds boxes on the
way up**. That is the shape inheritance forces: a child's `color` is its parent's
unless it says otherwise, so the parent's style has to exist first. A node that
is neither `Styled` nor `Paints` passes its ancestor's style straight through
rather than resolving one — it has no type, no id and no classes, so no selector
names it and a cascade walk per composition node per frame would buy nothing.

### A weight is a face, not an axis

Inter and JetBrains Mono ship as **variable** files, and instancing `wght` at
runtime is the smaller download and the more general answer. It needs symbols
bound in **both** libraries — HarfBuzz's `hb_font_set_variations` and Blend2D's
variation settings — which means a new struct layout in the probe and three new
export branches: the ELF version script, the MSVC `.def` and the Mach-O list.
That machinery has caught the same local-symbol bug three times
([ADR-0018](0018-sdl-conventions-stop-at-the-boundary.md),
[ADR-0031](0031-blend2d-and-the-borrowed-buffer.md)) and is only ever answered by
a CI run across four targets.

§1.4 ships **exactly two weights**, 400 and 600, and Principle 3 says a screen
needing a third extends the system rather than improvising one. So
`Inter-SemiBold.ttf` is extracted alongside the variable file: 400 KB, no native
change, and the whole shipped scale covered. `BundledFont.Weight` is a closed
pair, and `Weight.nearest` resolves any CSS number the way CSS's own font
matching does — `font-weight: bold` gets SemiBold rather than nothing. The axis
stays a real optimisation for the day an intermediate weight is specified.

`font-weight` is therefore resolved to a *face* in the cascade rather than
carried as a number, so a weight no file can honour is discovered while styling
and not inside a paint pass.

### `Fonts` is a book, owned like everything else native

The cascade resolves a `Typography` per node; the painter needs a `Font`. Without
a cache, `font-size: 20px` on one heading would re-parse Inter — 681 µs and a
second copy of a megabyte and a half — **every frame**, because the widget tree
is rendered from scratch each time.

`Fonts` caches faces by `BundledFont` and fonts by (face, size), which are the
two levels [ADR-0044](0044-one-face-many-sizes.md) established. It is an ordinary
object an application opens and closes, not a global: these are thread-confined
and hold native memory, so a process-wide cache would have to be per-thread, and
a per-thread cache of native memory is a leak with no hook to free it.

The size is quantized to a thousandth of a pixel before it is used as a key. Two
`13.000000000000002`s from different `em` chains are the same font to any reader,
and a cache that disagreed would open a font per frame and look exactly like a
leak.

`Paints.Context` becomes `Font font(ComputedStyle)` rather than `Font font()`,
because the font is now a property of the node and not of the window.

### Only the first family of a list

`font-family: Inter, sans-serif` takes `Inter` and discards the rest. §6.1 is
explicit that there is no fallback cascade in v1 — a character outside the
bundled faces is `.notdef` **on purpose**, because a cascade across arbitrary
system fonts is what makes text look different on every machine. Honouring the
list would be pretending to a mechanism that does not exist.

A family that matches nothing bundled falls back to Inter rather than throwing:
that is a stylesheet naming a font nobody shipped, and drawing it in Inter beats
a window with no text in it.

## Consequences

- **The label bug is fixed**, with a regression test that asserts the resolved
  colour on both themes rather than eyeballing a golden.
- **`button` is fully §3-compliant.** `body-strong` was the last of the four
  things `controls.css` said it could not express; only §1.7's transitions remain
  in that comment.
- **The theme's type tokens now match §1.4.** They did not: `heading` was 16
  where the table says 15, `body` was 14 where it says 13, and there were no
  line-height tokens at all. The seven sizes, their line heights and the two
  weights are in both themes, and `controls.css` exposes them as classes —
  `.body`, `.body-strong`, `.caption`, `.mono` — so `text class="heading"` picks
  a token rather than a number. The mapping is theme-invariant and the numbers
  are the theme's, which is what keeps a large-text or high-contrast theme an
  alias swap rather than a code path (§10.1, §4).
- `ComputedStyle.INITIAL`'s typography is §1.4's `body` — Inter 400 at 13/18 —
  rather than something neutral. A window with no stylesheet should read as the
  design system; the alternative is a toolkit whose out-of-the-box text is a size
  nobody chose.
- Every golden image was regenerated twice: once for the label colour, once for
  the weight and the corrected sizes.
- `WidgetRenderer` keeps a single-`Font` constructor for benchmarks and for tests
  that are about something other than typography. It ignores `font-family`,
  `font-size` and `font-weight`, and says so.
- **Open:** `text` has no `style="body"` attribute yet.
  `docs/core-widgets.md` §2 asks for one; what ships is the class, which is the
  same thing spelled the way CSS already spells it. Whether the attribute is
  worth a second spelling is a question for when `field` and `form` need labels.
- **Open:** `em` and `rem` still resolve against `CssLength.Context`'s fixed
  numbers rather than against the node's own resolved `font-size`. Nothing in the
  toolkit's own stylesheets uses `em`, so this has no effect today — but
  `font-size: 1.2em` currently means 1.2 × 16 and not 1.2 × the parent's size.

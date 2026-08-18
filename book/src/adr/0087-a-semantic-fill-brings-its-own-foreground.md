# ADR-0087: A semantic fill brings its own foreground

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/ARCHITECTURE.md` §10, `docs/design-system.md` §1.2 §1.5 §3, `docs/core-widgets.md` §3

## Context

`badge` is the ninth entry in §3's control table and the first that is not a
control: "count/status chip, typically composed inside `stack`. Semantics:
text." No focus, no value, no keyboard map, no states. On the face of it the
smallest widget in the catalog after `spinner`.

It is also the first widget that is *allowed to use colour*. §1.2 is strict
about the aurora hues:

> Aurora hues (`nord11–15`) appear **only with semantic meaning**
> (danger/warning/success/info, chart series) or in expressive surfaces — never
> as decoration on controls.

Every widget so far has obeyed the *never* half. A status chip is the first one
whose entire job is the *only* half, so `badge.danger` and `badge.success` are
the point of the widget rather than a skin on it.

Which puts it straight into the other half of §1.2:

> Every text/surface pair meets **WCAG 4.5:1** (3:1 for large text ≥ 20px).
> Contrast is validated in CI against both themes.

Nothing validated anything. There was no contrast check in the repository at
all, and the sentence had been true-by-assertion since the design system was
written.

The moment a chip is filled with a semantic hue, the theme's text token is
wrong. On the dark theme `--gb-text` is `--nord6`, a near-white, and:

| fill | with `--nord6` | with `--nord0` |
|---|---|---|
| `--gb-warning` (`--nord13`) | **1.35** | 8.00 |
| `--gb-success` (`--nord14`) | **1.77** | 6.13 |
| `--gb-info` (`--nord9`) | **2.34** | 4.64 |
| `--gb-danger` (`--nord11`) | **3.55** | **3.05** |

Three of the four hues need the *opposite* end of the palette from the one the
theme is built on. And `--gb-danger` needs something that is not in the palette
at all: it carries no legible small text in either direction.

Writing the check turned up the same defect in shipped code. Seven `button`
colour pairs are below the floor — `button.danger` in both themes and
`button.primary` on light — and `--gb-button-danger-text: var(--nord6)` on
`--gb-button-danger-bg: var(--nord11)` is the 3.55:1 above. It has been there
since the first control shipped.

## Decision

**A filled element's foreground is a property of its fill, not of its theme.**
Each `badge` variant pins its own `--gb-badge-*-text` beside its
`--gb-badge-*-bg`, and for the three light aurora hues that token is `--nord0`
on *both* themes — including the dark one, where every other text token is
`--nord6`. §1.2's palette is theme-invariant, so a pairing that works for
`--nord13` works for it everywhere; the theme does not get a vote.

**Where no palette entry works, the fill is derived until one does.**
`--gb-badge-danger-bg` is `#a0414a` — `--nord11` taken down in lightness until
it clears 4.5:1 under `--nord6` (it reaches 5.44) — and on the light theme
`--gb-badge-accent-bg` is `#4a678b`, which is `--nord10` taken down the same way
and is the value `--gb-button-primary-bg-active` already derives. Both are
written beside the palette entry they came from, the convention
`--gb-button-primary-bg-hover` already uses.

**§1.2's CI validation now exists**, as `ContrastTest`. It resolves every
text-on-fill pair the toolkit ships through the real cascade — base layer,
theme, the same `StyleResolver` a window uses — and computes WCAG 2.1's ratio
from the `background` and `color` that come out. The seven failing `button`
pairs are in a `KNOWN_FAILURES` list that is asserted **exactly**: a pair that
gets fixed fails the test until it is removed, and a pair that newly breaks
fails it immediately.

**And §3's table gained a `badge` row** before any number was written into
`controls.css`: height 20, padding-x 8, radius `full`, `caption`. Principle 3 —
"if a screen needs a value that isn't a token, the system gets extended
deliberately".

## Alternatives considered

**Let `badge` inherit `--gb-text` like everything else.** This is what the
cascade does for free and it is what a first draft looks like. It produces white
text on a pale yellow chip at 1.35:1 — text that is not merely hard to read but
genuinely invisible at 11px on a laptop screen. §1.2's floor is not advisory.

**Make the semantic variants tinted rather than filled** — the hue as text and
border on the theme's own surface, no coloured fill. This is a common badge
style and it dodges the whole question. It does not survive the numbers:
`--nord11` *as text* on `--gb-bg` is 3.05:1, so the variant that most needs to
be readable is the one that fails hardest. Tinting moves the problem, it does
not solve it.

**Ship `danger` as `--nord11` anyway and note the exception.** The chip that
says a service is down is the one chip a user must be able to read. An
accessibility floor with an exception carved out for the highest-urgency case is
not a floor.

**Instance the whole palette against a contrast solver at theme-load time**, so
any fill gets a computed foreground. It removes the hand-written token pairs and
would generalise to application themes. Rejected as premature and as a
determinism risk: §1.1's fifth principle is that the same markup renders
identically on every machine, golden images are the arbiter, and a colour
arrived at by an algorithm at runtime is a colour that changes when the
algorithm is tuned. Two derived hex values reviewed once are cheaper to trust.
The seam is open — the tokens are the only thing widgets consume, so a solver
that emitted them later would change nothing above.

**Fix the seven `button` pairs in this change.** It is the same defect and the
fix is known — each ramp's `:active` end already passes, so the resting fills
move one step darker. Rejected here because it recolours the most visible
control in the toolkit and moves every button golden, and a change that adds a
badge should not also be the change that repaints `button.primary`. It is
recorded in `status.md` and in `KNOWN_FAILURES`, where it is loud rather than
forgotten.

**Scope `ContrastTest` to badges, so it ships green.** This is
[ADR-0082](0082-a-preflight-check-that-cannot-fail-is-not-a-check.md)'s trap
verbatim: a check narrowed to what already passes is not a check. The sweep
covers everything and the exemptions are enumerated.

## Consequences

**§1.2 is enforced for the first time**, and it found something on the first
run. Any future control that puts text on a fill is checked at the moment it is
written, in both themes, with no image to eyeball.

**A theme is now a slightly harder thing to write.** An application supplying
its own theme must supply eleven `--gb-badge-*` tokens, and getting them wrong
is a legibility bug rather than a visual one. Mitigated by the check: an
application that runs `ContrastTest`'s arithmetic over its own theme gets the
same answer. Not mitigated by the toolkit — nothing validates a *third-party*
theme, and that is a real gap.

**Two colours in the theme files are not palette entries.** `#a0414a` and
`#4a678b` are derived values, and a future Nord revision would have to re-derive
them. They are commented with what they came from and what they measure, which
is the most that can be done without a solver.

**The `KNOWN_FAILURES` list is a debt that announces itself.** It cannot rot
silently — it is asserted as an exact set — but it is still seven shipped pairs
below an accessibility floor, in the toolkit's most-used control, and that is
the honest cost of not fixing them here.

**`badge` is in the no-shrink list despite not being a control.** The list's
stated rule is "a control's metrics are fixed"; a badge is there for a different
reason, that its whole content is two or three glyphs and a chip that gave width
back would ellipse `99` into `9` — a clipped label is a nuisance, a clipped
count is a wrong number.

**There is still no minimum width**, so a single-digit chip is a stadium rather
than the circle a badge usually is. §8's subset has no `min-width` at all;
`badge-digits.png` records it rather than a comment claiming it, and it joins
the existing open question about minimum sizes.

**Nothing about a badge animates**, and that is §3.1 being followed rather than
an omission: it has no row, and the preamble says anything not listed does not
animate. `BadgeTest` asserts the empty transition set, so a `transition` added
to the shared control rules cannot reach a chip by accident.

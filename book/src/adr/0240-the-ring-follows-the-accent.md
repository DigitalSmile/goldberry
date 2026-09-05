# 240. The ring follows the accent

Date: 2026-09-05

## Status

Accepted. Pays the first and worst of the nineteen debts
[ADR-0239](0239-a-mark-is-measured-against-the-box-it-is-drawn-in.md) recorded.

## Context

ADR-0239 measured §1.2's non-text floor for the first time and found §2.8's
focus ring below it on every surface of the light theme:

| surface | ratio |
|---|---|
| `--gb-bg` | 1.74:1 |
| `--gb-surface` | 2.00:1 |
| `--gb-surface-2` | 1.64:1 |

That entry named this the one to fix first, and the reason is not the size of the
number. A focus ring is **the only mark in the system with no second means of
being seen**: a control that is hard to make out still has its label, its shape
and its position, and a keyboard user who cannot see the ring has nothing at all.

The cause turns out to be a ramp that was left behind rather than a colour anyone
chose. Both themes set the ring to their accent — except that the light theme's
accent had already moved and the ring had not:

```
nord-dark    --gb-accent: var(--nord8);   --gb-focus: var(--nord8);
nord-light   --gb-accent: var(--nord10);  --gb-focus: var(--nord8);
```

`--nord10` is where the light theme's accent went *for contrast*, down the Frost
ramp from the pale `--nord8`. The focus ring kept the pale one.

## Decision

**`--gb-focus: var(--nord10)` on the light theme** — the ring follows the accent,
which is what the dark theme has always done.

This clears the floor on every surface with room to spare: 3.50:1 on `--gb-bg`,
4.03:1 on `--gb-surface`, 3.31:1 on `--gb-surface-2`.

It is a **palette value rather than an invented one**, which matters here more
than it usually would. The theme files open by saying the raw palette is
theme-invariant and the semantic tokens are what differ; a hand-mixed hex for the
ring would have been a fourth blue in a file with three, and the whole argument
for the fix is that the ring belongs to the accent's ramp rather than to one of
its own.

## The gap it exposed, which is the more useful half

Changing a shipped colour moved **no golden at all**, and that is not because the
change is invisible.

Every focus golden in the catalog is `NORD_DARK` — `segmented-focus`,
`menu-focus`, `menubar-focus`. §2.2's ring had no picture of it on the one theme
where it was broken, which is why nothing caught this in the first place and why
nothing would have caught it coming back. `segmented-focus-light` is new, and it
is the point of this record as much as the token is: a colour with no image is a
colour nothing would notice going wrong again.

The two renderings side by side are the argument. At `--nord8` the ring is a pale
wash that reads as an artefact of the bar's own edge; at `--nord10` it is
unmistakably a ring.

## Alternatives considered

- **Darken past `--nord10`** — `#4c6d94` reaches 4.40:1 and `--nord3` 6.06:1.
  Both clear by more and neither is a Nord colour or the accent, so the ring
  would stop matching the thing it is signalling about. 3.31:1 is a floor cleared,
  not a floor scraped.
- **Lighten the surfaces instead.** `--gb-surface-2` is the binding constraint at
  1.64:1, and moving it moves every panel, card and group-box in the theme to fix
  one ring.
- **A ring with its own contrasting outline** — a light halo around a dark core,
  which is what some systems do to be safe on any backdrop. It needs two colours
  and a second `outline` the subset does not have, for a case §1.2 already
  answers with one number.
- **Fix all nineteen at once.** The other sixteen are control fills and the accent
  ramp, and they move goldens in bulk; keeping this one separate is what makes it
  a reviewable diff, and the exact-set lists in `ContrastTest` are what stop the
  remainder being forgotten.
- **Change `--gb-focus` in both themes** for symmetry. The dark theme's is already
  4.31:1 at its worst and its accent is `--nord8`; changing it would be a
  redesign in search of a rule.

## Consequences

- **`RINGS_BELOW_FLOOR` is empty**, and `ContrastTest`'s exact-set assertion is
  what forced this to be noticed: emptying the token without emptying the list
  failed the build, which is the mechanism ADR-0239 built the lists for working on
  its first use.
- **`segmented-focus-light` is a new golden**, and the catalog's first picture of
  a focus ring on the light theme.
- **Sixteen non-text pairs remain below the floor**, in `MARKS_BELOW_FLOOR` and
  `BOUNDARIES_BELOW_FLOOR`. They are the control fills and the accent-on-border
  pair, they move goldens in bulk, and `TODO.md` carries them.
- **The light theme now has one blue for "this is the accent" and "this is
  focused"**, which is what the dark theme reads as and is the more ordinary
  design anyway.

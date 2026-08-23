# 194. A series colour is derived from Nord, not taken from it

Date: 2026-08-23

## Status

Accepted. The categorical palette for `docs/charts.md`'s chart widgets, measured
rather than chosen.

## Context

`content-widgets.md` §3 says charts take "categorical series colors from aurora +
frost hues" and "sequential/diverging ramps interpolated in OKLCH". Read quickly
that says *use the Nord hues*. Measured, it cannot mean that.

Nord is a **UI** palette: low chroma, high lightness, designed to sit quietly
behind text. Series colours do the opposite job — they carry identity, at small
sizes, next to each other. Checked against the six standard categorical checks,
Nord's nine hues used literally fail five:

| Check | Result |
|---|---|
| Lightness band | **FAIL** — `nord13` at OKLCH L 0.855, `nord8` at 0.775 |
| Chroma floor | **FAIL** — six of eight below C 0.10, so they read as gray |
| CVD separation | WARN — `nord14`↔`nord13` ΔE 6.5 under protanopia |
| Normal-vision floor | **FAIL** — `nord9`↔`nord8` ΔE 8.5 |
| Contrast vs surface | WARN — six of eight below 3:1 on white |

One measurement explains most of it. Nord's frost family spans **23° of hue**
across four members, and `nord9`/`nord10` are **5° apart** — the same hue at two
lightnesses. That is an elevation ramp, and it is why the toolkit uses it as one.
It is not two identities.

This is the same finding ADR-0175 made about the semantic hues, where the theme's
own contrast claim was untrue in five of eight pairs. A palette's stated purpose
is not evidence that it serves a different one.

## Decision

### Eight slots, re-stepped from Nord's hue angles

Each slot keeps a Nord hue's **angle** and takes a chart-specific lightness and
chroma: OKLCH L 0.62 light, L 0.66 dark, C 0.14 clipped into sRGB. So a series
still reads as the Nord hue it came from, and also clears the floor that makes it
legible as identity.

| Slot | Hue | Light | Dark |
|---|---|---|---|
| 1 | `nord14` green | `#679732` | `#73a340` |
| 2 | `nord15` purple | `#b663aa` | `#c46fb7` |
| 3 | `nord13` yellow | `#aa7e05` | `#b88a07` |
| 4 | `nord10` blue | `#4488d8` | `#5094e5` |
| 5 | `nord11` red | `#cc5e6a` | `#da6a76` |
| 6 | `nord8` cyan | `#0796b2` | `#02a3c1` |
| 7 | `nord12` orange | `#cb6443` | `#d9704f` |
| 8 | `nord7` teal | `#0d9999` | `#06a7a7` |

All six checks pass in both modes: worst adjacent CVD ΔE **12.4** (protanopia),
worst normal-vision ΔE **21.9**, every slot ≥ 3:1 on its surface.

**Dark is its own steps, not a flip.** L 0.62 on `--gb-surface` `#3b4252` measures
2.57–2.9:1 — below the 3:1 mark floor — so the dark set is stepped at 0.66, which
is inside the dark lightness band and clears it. Two numbers rather than one
transformation, for the reason ADR-0087 gives about the semantic hues: contrast is
a property of a pair, and the pair is different in each theme.

### The order is searched, not chosen

Adjacent slots are what touch in a stacked bar, a grouped bar and a multi-line
chart, so the *sequence* is the CVD-safety mechanism. All 40 320 orderings were
scored against the validator in both modes and the best kept. This matters
concretely: ordering by Nord's own numbering puts `nord12` orange beside `nord14`
green, which is ΔE **0.8** under deuteranopia — two series a reader cannot tell
apart at all.

### Slots are assigned in order and never cycled

A ninth series is not a generated ninth hue: under CVD it is indistinguishable
from one of the eight, and generating it would break the property the search just
established. Nine series folds the tail into "Other" or becomes small multiples.

### `nord9` gets no slot

Four frost hues yield two usable identities. Leaving `nord9` out is the honest
form of that, rather than shipping eight slots of which two collide.

## Consequences

- **A series red and a danger red are different reds, on purpose.** `--gb-danger`
  means "this is bad"; slot 5 means "this is series 5". A threshold band and a
  series must not be the same colour, or the chart says something it does not mean
  — which is `design-system.md` §1.2's rule about aurora hues carrying semantic
  meaning, read the other way round.
- **The palette is checkable in CI**, like the 3:1 sweep ADR-0175 added. The
  values are a table, the checks are arithmetic, and a slot edited by hand fails
  the same way a contrast regression does.
- **`content-widgets.md` §3's sentence is honoured rather than contradicted.** It
  said *derived*, and this is the derivation; what it did not say is that the
  literal hues fail, which is why this record exists.
- **Eight is the ceiling and it is a real one.** The dashboard-grade scope
  (§3's "deliberately small") and the palette's ceiling agree, which is a
  convenient accident and not an argument: past eight the answer is a different
  form, not more colours.
- **Nothing renders yet.** This is a table and a set of measurements; the widgets
  that use it are M3's remaining work.

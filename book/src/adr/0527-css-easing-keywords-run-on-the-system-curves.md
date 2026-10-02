# ADR-0527: CSS easing keywords run on the system curves

- **Status:** Accepted. Amends [ADR-0353](0353-a-stylesheet-may-name-keyframes.md)
  ("the curves are §1.7's three keywords") and the matching rule for
  `transition` in [ADR-0067](0067-motion-is-an-overlay-on-a-frame-clock.md).
- **Date:** 2026-10-02
- **Relates to:** [ADR-0529](0529-an-applications-stylesheet-is-lenient-and-loud.md),
  `docs/design-system.md` §1.7, `docs/goldberry-gaps.md` #11

## Context

`Easing.parse` knows `ease-enter`, `ease-exit` and `linear`, on purpose: "a
design system where every screen can invent its own curve has no motion
language, only motion". Anything else returned null, and the `transition` and
`animation` shorthands are all or nothing, so one unknown curve dropped the
whole declaration.

Deploy Orc wrote `animation: orc-pulse 900ms ease-in-out infinite alternate
both`, which is ordinary CSS. The whole declaration was dropped and the running
node never pulsed. The curve was the least important word in it: the name, the
duration, the loop and the direction were all fine.

## Decision

**CSS's four keywords are read onto the three system curves, and a timing
function outside the subset drops itself rather than its declaration.**

| Written | Runs as | Because |
|---|---|---|
| `ease` | `ease-enter` | CSS's default, `cubic-bezier(0.25, 0.1, 0.25, 1)`, spends most of its length decelerating |
| `ease-out` | `ease-enter` | `cubic-bezier(0, 0, 0.58, 1)` is the same shape: fast, then settling |
| `ease-in` | `ease-exit` | `cubic-bezier(0.42, 0, 1, 1)` is within a hair of `ease-exit`'s `(0.4, 0, 1, 1)` |
| `ease-in-out` | `ease-enter` | see below |

- `ease-in-out` is symmetric, and the system has no symmetric curve. Of the
  two halves, the ending is the one a viewer reads, as the thing settling into
  place, and `ease-enter` is the curve whose ending is gentle. It is also the
  default, so `ease-in-out` and no curve at all behave alike, which is the least
  surprising reading for a stylesheet that did not mean anything finer. Under
  `alternate` the reversed iterations run the curve backwards, which is CSS's
  rule and gives the accelerating half on the way back.
- `Easing.parse` does the mapping, so `transition`, `animation` and
  `animation-timing-function` all accept the four keywords. Each mapping is
  logged once, at info, naming the curve it became.
- **A curve outside the subset drops itself.** In either shorthand,
  `cubic-bezier(…)`, `steps(…)`, `linear(…)`, `step-start`, `step-end` and any
  other word beginning `ease` are skipped with one warning naming the text, and
  the entry keeps the default `ease-enter`. Every other bad part still drops
  the declaration, as before. The comma split now ignores commas inside a
  function, which `cubic-bezier(0.4, 0, 0.2, 1)` needed.
- `animation-timing-function`, the longhand, still drops a declaration whose
  value is not a curve: there, the curve is the whole declaration.
- The default stays `ease-enter`, not CSS's `ease`. The two now run the same
  curve, so the difference is only in which name the documentation gives.

## Consequences

- `orc-pulse 900ms ease-in-out infinite alternate both` runs: a 900 ms loop on
  `ease-enter`, alternating, filled both ways. `AnimationPropertyTest` holds it.
- The design system's rule survives: there are still three curves, and a
  stylesheet cannot add a fourth. What changed is that CSS's names for roughly
  the same curves are accepted instead of punished.
- §1.7's rules about where each curve is used bind the toolkit's own sheets,
  which keep writing the system names.

# 241. A theme can be audited by whoever wrote it

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0087](0087-a-semantic-fill-brings-its-own-foreground.md).

## Context

The entry is four sentences and the last one is the whole problem:

> Nothing validates an application's own theme. §10 lets an application swap the
> alias tokens, and `ContrastTest` runs over the two themes the toolkit ships. A
> third-party theme that pairs `--gb-badge-warning-bg` with an unreadable
> `--gb-badge-warning-text` is a legibility bug the toolkit will not notice — the
> arithmetic is nine lines and is not exposed as anything an application can call.

`ContrastTest` is a good check and it is in the wrong place to be reused: it
lives in `:widgets`, it is a test, and its nine lines of WCAG arithmetic are
private to it. So the guarantee §1.2 makes stops precisely where §10's
extensibility begins — the toolkit promises legible colour, hands the application
the means to replace all of it, and then has nothing to say.

## Decision

**A new package, `css.contrast`, in `:core` and exported.**

`:core` because a theme is: an application that wants to know whether its colours
are readable should not have to depend on the widget catalog to find out. Its own
package rather than `css` or `css.value` because it is neither a stage of the
engine nor a value type — it is a question asked *about* a resolved cascade,
which is the shape ADR-0172 gave the other four packages.

- **`Contrast`** — the arithmetic and the two floors, `TEXT_FLOOR` 4.5 and
  `NON_TEXT_FLOOR` 3.0. `ContrastTest` now calls it instead of its own copy, and
  that is the point rather than tidiness: an audit an application runs and a
  sweep CI runs that disagreed about the arithmetic would be worse than either
  alone.
- **`ContrastFinding`** — a measured pair, returned for everything measurable
  rather than only failures. An application tuning a theme wants to see how much
  room a pair has; a caller that only wants the failures says so in one `filter`,
  and the other direction is impossible.
- **`ThemeAudit`** — `audit(sheets)` and `failures(sheets)`.

### The pairs are found by convention, not by a list

This is the decision the entry did not anticipate and the one that makes the
feature worth having.

A hard-coded list of the toolkit's own pairs would check a custom theme's
*overrides* and miss everything it added. But the design system already names its
pairs consistently — `--gb-badge-warning-bg` carries `--gb-badge-warning-text`,
`--gb-button-primary-bg` carries `--gb-button-primary-text` — so the rule is
**every `--gb-<name>-bg` with a matching `--gb-<name>-text`**, and an application
that follows the same convention for `--gb-mycard-bg` is checked for free.

The surface pairs are stated explicitly beside it, because `--gb-text` on
`--gb-bg` is the one relationship the convention cannot express: neither token is
named for the other. The two overlap by exactly one — `--gb-bg` ends in `-bg`, so
the convention derives `--gb-text` from it and finds the same pair — which is
deduplicated rather than removed, because both routes are right.

### Substituted, not raw

`StyleResolver.customProperty` rather than the raw token map, for the reason
ADR-0195 gave the chart palette. A theme written the ordinary way says
`--gb-badge-warning-bg: var(--gb-warning)`, and an audit that read tokens without
resolving would decide that is not a colour and skip the pair — auditing a real
theme as having nothing to check, and passing.

### What it will not measure

A translucent colour has no single ratio, because what it composites over decides
the answer. A pair with alpha on either side is **skipped rather than measured**,
and this is not hypothetical: a `hud`'s plate is `#1c212ae6`, deliberately
translucent so the frame shows through. Scoring it would read the alpha off,
treat it as opaque, and report a comfortable pass on a colour nobody receives —
the same trap that keeps `button.ghost` out of `ContrastTest`.

So the audit returns fewer findings than the theme has tokens. That is the honest
number rather than a gap.

## Alternatives considered

- **Expose `ContrastTest`'s sweep as a test fixture.** It resolves *widgets*
  through the cascade, so it needs `:widgets`, a font and a renderer — and the
  question an application swapping tokens can act on is about the tokens, not
  about what a `Badge` did with them.
- **Fail at start-up when a theme does not audit clean.** A stylesheet is data
  and §8's rule for bad data is to drop it and carry on; a theme that refused to
  load over a 4.4:1 badge would be the toolkit overruling a decision that is the
  application's. `failures()` is offered so an application can make that its own
  policy.
- **Log a warning when a theme loads.** The `group-box-title` precedent in
  ADR-0216 is the argument against: a dropped *value* already warns and the wrong
  corners shipped for months anyway. A call an application makes deliberately is
  read; a line in a log is not.
- **Take a `Theme` rather than a list of stylesheets.** `Theme` is the two the
  toolkit ships, and the whole subject here is the third one. A list is what a
  window is given anyway.
- **Put it in `css` beside `Theme`.** That package is the engine's front door —
  `Stylesheet`, `ComputedStyle`, `Theme` — and this is a question asked about the
  result rather than a part of producing it.

## Consequences

- **Both shipped themes audit clean**, at seventeen pairs each — asserted as a
  count as well as a set, because a sweep that quietly stopped finding pairs
  would otherwise pass by measuring nothing.
- **`ThemeAuditTest` proves it on a theme the toolkit has never seen**, including
  the entry's own example: white on `--nord13` at 1.56:1 is caught by name.
- **`--gb-hud-bg` is the shipped example of the alpha rule**, and there is a test
  saying so, because the rule is only credible if the toolkit's own tokens are
  subject to it.
- **`ContrastTest` lost its private arithmetic and its two literal floors.** They
  are `Contrast`'s now, so the number CI asserts and the number an application
  audits against cannot drift apart.
- **This does not cover the non-text floor.** `NON_TEXT_FLOOR` is exported and
  `ThemeAudit` does not use it: the non-text pairs are marks on their own boxes
  and rings on surfaces (ADR-0239), and which token is a *mark* is not something
  a naming convention can tell. Sixteen of those are still below the floor in the
  shipped themes, which is `TODO.md`'s.

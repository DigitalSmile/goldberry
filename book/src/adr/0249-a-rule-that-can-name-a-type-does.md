# 249. A rule that can name a type, does

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0152](0152-the-cascade-looks-at-rules-that-could-match.md).

## Context

> The rule buckets are only as good as the stylesheet. A sheet written entirely
> in classes puts every rule in the untyped bucket and gets none of ADR-0152's
> saving. The toolkit's own sheets are type-first and nothing enforces that they
> stay so.

ADR-0152's saving is that a rule for `button` is never even looked at for a
`text`. Rules are bucketed by the type their **rightmost** compound names; a rule
that names none goes in the bucket nothing can skip, and is checked against every
element of every kind on every resolve.

The entry assumed the toolkit's sheets were type-first. Measuring found **16 of
340** rules untyped, and that they were two families rather than a scattering.

## Decision

**Qualify the seven that had a type to give, and enforce the rest as an exact
list.**

The 16 split cleanly:

- **Seven were `tour`'s parts** — `.tour-title`, `.tour-body`, `.tour-count`,
  `.tour-skip`, `.tour-next` and its two states. The tour builds them from plain
  `Text` and `Button` widgets carrying a class, so every one of them *was*
  matching a known type and simply not saying so. `text.tour-title` matches
  exactly what `.tour-title` matched and lands in a bucket. Seven rules, one word
  each, no behaviour change — the golden corpus is untouched, which is the check
  that it was a re-bucketing rather than a re-selecting.
- **Eight cannot be qualified and should not be.** The typography scale (§1.4) is
  seven ranks an application puts on whatever it likes — that is what makes it a
  scale rather than a widget's parts — and `:root` is the theme's token layer.

**`RuleBucketTest` asserts the remaining eight as an exact set**, on
`ContrastTest`'s terms: a threshold is a number somebody raises, and a count that
fails without naming anything is a count nobody reads. A new untyped rule has to
be argued for in a diff, beside the reason.

Beside it, a second assertion that the sheet is large and nearly all of it is
bucketed — because a check listing eight selectors would pass just as well
against a stylesheet of eight rules.

## Alternatives considered

- **Give `tour`'s parts their own element types**, `tour-title` rather than
  `text.tour-title`. It is the more thorough answer and it makes seven new widget
  types whose only job is to be styled, where the class already says what they
  are and the type qualification costs one word.
- **A threshold instead of a list** — "no more than 5% untyped". It passes while
  the wrong five rules are untyped, and it is raised rather than read the first
  time it fails.
- **Warn at parse time** when a rule names no type. Most of the eight are correct
  and an application's sheet is its own business; a warning that is usually wrong
  is the log ADR-0243 has just finished quietening.
- **Bucket by class as well as by type.** ADR-0152 considered and deferred it,
  and this makes it less pressing rather than more: after the change the untyped
  bucket is eight rules, and a second index is a lot of machinery to skip eight.

## Consequences

- **16 untyped becomes 8**, out of 340 — under one in forty, and every one of
  them named with its reason.
- **`StyleResolver` gains three read-only accessors**: `untypedRuleCount`,
  `ruleCount` and `untypedSelectors`. Public because the lint that reads them is
  in `:example`, beside the two that already check the toolkit's own sheets — a
  sheet's *shape* is the same kind of fact as a dropped declaration.
- **`untypedSelectors` returns the selectors and not a count**, which is the
  difference between a failure that names `.tour-title` and one that says a
  number went up.
- **No golden moved**, which is the evidence that qualifying a class selector
  with the type it was already matching changes what the cascade *looks at* and
  not what it *finds*.

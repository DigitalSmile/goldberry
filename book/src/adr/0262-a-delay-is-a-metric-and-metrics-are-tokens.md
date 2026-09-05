# 262. A delay is a metric, and metrics are tokens

Date: 2026-09-05

## Status

Accepted. Closes the tooltip-delay entry, and builds the half of
`design-system.md` §3's `tooltip` row that nothing had noticed was missing.

## Context

The entry said the delay was blocked twice over:

> §7 says "after delay" and does not say how long, so 500ms is the toolkit's
> number and an application cannot change it — and the obvious shape for one, a
> `--gb-tooltip-delay` custom property, is blocked twice over: **nothing above the
> cascade can read a resolved custom property**, and **a delay is not a paint**,
> so whether the design system should carry durations that are not motion is a
> question for it rather than for this.

Both have expired, and one of them was never true.

**The first expired.** `Paints.Context.length`
([ADR-0251](0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md))
and `BuildContext.token`
([ADR-0254](0254-a-build-may-ask-the-cascade-for-a-number.md)) both read resolved
custom properties from outside the cascade. The launcher holds an `Element`, and
an `Element` *is* a `BuildContext`.

**The second was answered before it was asked.** The entry says §7 "does not say
how long", which is true of `core-widgets.md` — and `design-system.md` §3's
`tooltip` row says it in as many words:

> `tooltip` | padding 6/8; radius 4; `caption`; **delay 500ms show / 100ms
> move-between**

So the design system was never being asked a question. It had answered, twice,
and the code had implemented the first number as a constant and **the second not
at all**.

## The bug this turned up

`Launcher.pointingChanged` scheduled `TOOLTIP_DELAY` for every target, including
one reached from a tooltip that was already showing. So a user reading along a
toolbar was served the full 500ms of hover intent at every button — which is what
§3's second number exists to prevent, and what "100ms move-between" means.

That is not a styling gap. It is a specified behaviour that was never built, and
it was hiding inside an entry about tokens.

## Decision

### `BuildContext.duration`, a third accessor and not a general one

`token` reads a **length**. A delay is not one, and `--gb-tooltip-delay: 500ms`
would answer the fallback through it.

`duration(name, fallbackMillis)` sits beside it, and it is a third accessor
rather than a general `token(String)` for `Paints.Context.length`'s stated
reason: lengths, colours and now durations are values **the cascade already
parses**, and a general reader would invite a caller to reimplement the parser.

It calls `ComputedStyle.durationMillis`, which is the private `ms`/`s` reader
`transition` has always used, made public. Writing a second one was the
alternative and is the thing to avoid: two parsers for one syntax disagree the
day either grows a unit, and this one already refuses a bare `200` for a reason
worth keeping.

### Two tokens, and §3's own numbers as the fallbacks

`--gb-tooltip-delay` and `--gb-tooltip-delay-move` ship in `controls.css`, and
`Launcher` carries 500 and 100 as constants. That is `--gb-list-row-height`'s
arrangement exactly: the token is the catalog's, the fallback is `:core`'s, and
`:core` does not need the catalog to exist.

### The delay is read off the **target**

Not off the window. A custom property inherits down the tree, so asking the node
the tooltip is *for* is the only reading that lets a panel set the delay for what
is inside it — and the only one that is not a global setting wearing a token's
clothes.

### The shorter delay is about moving, not about being fast

`moving` is read **before** `hideTooltip()`, because the hide is what makes it
false. The full delay is hover *intent* — the question "did you mean to stop
here?" — and a user who is already reading tooltips has answered it. A test
asserts that the first tooltip in a row still waits the full one, so the shorter
number stays a statement about moving between rather than a faster tooltip.

## Alternatives considered

- **A setter on `Application` or `Host`.** It makes the delay a program's rather
  than a theme's, and §3 is explicit that component metrics ship as token
  defaults. It also could not have been per-subtree.
- **A unitless token — `--gb-tooltip-delay: 500`.** It would have gone through
  the existing `token`, and it spells a duration as a length. The cascade refuses
  a bare number for `transition` and would be refusing it here in one file and
  accepting it in another.
- **Putting the number in §1.7 with the motion durations.** A tooltip delay is
  not a motion: nothing is moving, and §1.7's durations are how long a *change*
  takes. §3 already had it, which settles where it belongs.
- **A general `token(String)` returning tokens.** ADR-0251's argument, inherited:
  a widget would parse them, and there would be two parsers.
- **Leaving the move-between number.** It was not in the entry, so nobody was
  waiting for it — which is exactly why it would have stayed unbuilt.

## Consequences

- **`ComputedStyle.durationMillis` is public**, and is the second thing that class
  has been asked from outside for the same reason `applies` was: something above
  the cascade has a question only the cascade's own parser can answer honestly.
- **`BuildContext` gained a method**, which every implementer must now provide.
  There is exactly one — `Element` — and the interface is not an extension point
  an application implements, so this is a one-line cost.
- **A specified behaviour that was never built now is**, and the test that covers
  it fails against the old constant. Four new tests: the token honoured, a
  non-duration token ignored rather than guessed at, the move-between delay, and
  the first-hover delay staying long.
- **`TooltipTest`'s app takes extra CSS now**, which is how a token that only
  exists in a stylesheet gets in front of the launcher — and a two-target scene,
  because the case §3's second number is about cannot be produced by one
  full-window node.
- **`--gb-tooltip-delay-move` has no design-system row of its own**, because it is
  half of one that already existed. §3's `tooltip` row is unchanged by this
  record, which is the point.

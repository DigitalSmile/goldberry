# 415. "The root has no colour" is a fact about the sheets

Date: 2026-09-19

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0066](0066-a-weight-is-a-face-and-color-inherits.md). Applies
[ADR-0257](0257-a-diagnostic-is-asked-for-not-logged.md)'s shape and
[ADR-0394](0394-a-diagnostic-that-fires-on-everything-says-nothing.md)'s
constraint.

## Context

> A bare `text` with no ancestor setting `color` renders black, which is
> ADR-0066's deliberate `INITIAL` and a trap all the same: the showcase's new
> gain label was unreadable on the dark theme. A control gets away with saying
> nothing because `controls.css` sets `color` on `checkbox`, `radio`, `toggle`
> and `slider` themselves; a primitive does not. The showcase now sets
> `color: var(--gb-text)` on its root, which is what an application should do —
> but nothing warns one that has not.

Every clause of that is load-bearing, and one of them turns out to be the whole
design.

## Decision

### It is a lint asked for, and the thing it asks about is the root

**Not a per-node check on the resolved colour.** This is the obvious shape and it
is wrong twice.

It is wrong about the *value*: `color: INITIAL` is black because ADR-0066 decided
it should be, and black text on a light theme is correct. A check that fired on a
node resolving to black would be wrong about every light-themed application in
existence. The resolved colour is not evidence of anything.

It is wrong about the *rate*: it would be wrong once per text node per frame.
ADR-0394 took a diagnostic apart for exactly this — 688 reports of which 665 were
the check misunderstanding its own question — and the conclusion there applies
unchanged. A diagnostic that fires on everything says nothing.

What *is* evidence is that **no declaration anywhere set one**. That is not a
property of a pixel; it is a property of the cascade, and `color` inherits, so
one rule on the root settles the entire tree. The question collapses to one node,
asked once:

```java
new StyleLint(everythingLoaded).uncolouredRoot(tree.root())
        .ifPresent(finding -> LOG.warn("{}", finding));
```

The mechanism is the **declared** map rather than a `ComputedStyle`, and that is
the whole trick. A resolved style always has a colour — the initial one if
nothing else — so asking it can only ever report the value, never whether anybody
chose it. `StyleResolver.resolve` returns property names to tokens, and `color` is
a key in it if and only if a declaration won one.

A `color: var(--nothing-defines-this)` is absent from that map too, because
substitution failing takes the declaration with it. That is the right answer
rather than a gap: a root whose colour resolves to nothing has no colour, and the
author who wrote the rule is exactly the person who wants to hear about it.

### It takes the root element, because the application that does this right does
not write `:root`

This is the finding, and it is the reverse of what the entry implies.

`ThemeAudit` answers its question against a synthetic `Root` — no type, no id, no
parent — because a theme is a `:root` layer and nothing else. The obvious move
here was to copy that: build a probe, resolve `color`, report if absent. No
argument, no tree, no application involvement.

It would have reported the showcase as the defect. The showcase writes:

```css
#root {
  flex-direction: column;
  background: var(--gb-bg);
  color: var(--gb-text);
}
```

An **id** selector, on the root widget, which is a perfectly ordinary way to
style a root and is the arrangement the entry itself holds up as "what an
application should do". A synthetic `:root` probe matches none of it.

So a root is whatever the tree's root element is, its selector is the
application's business, and the only thing that can answer "does a rule reach it"
is the element. The check takes one.

The finding names the root the way a selector would — `#root`, or `window`, or
`:root` for a node with neither — so it says what to write and not only what is
missing.

### A node with a parent is refused rather than answered

`uncolouredRoot` throws on an element that is not a root. It would be easy to
answer: resolve, look for `color`, report if absent. It would also be a per-node
diagnostic with the word "root" in the method name, and the first application to
call it in a loop gets the 688 reports back.

## Alternatives considered

- **A `WARN` the first time a `text` resolves the initial colour.** One-shot, so
  the rate problem goes away; the *correctness* problem does not. It fires on
  every light-themed application, once, for something that is not wrong.
- **A start-up check the toolkit runs itself.** It cannot: the toolkit does not
  know when an application has finished loading its sheets, and a check that runs
  at window creation reports the sheet the application is about to add. ADR-0257's
  "nothing calls it for you" is not modesty, it is the only correct time.
- **A `Finding.Kind` on `check(linted)` rather than a method of its own.** The
  root's colour is a fact about everything **in force**, and `check` is
  deliberately about the sheets under scrutiny — `inForceIsNotLinted` asserts that
  "someone else's sheet is not this one's problem". Folding this into `check`
  would make the application's own sheet answer for the theme's omission.
- **Make `ComputedStyle.INITIAL`'s colour `currentColor`-ish, or theme-aware.**
  Reopens ADR-0066, which decided black deliberately and for a good reason: a
  toolkit whose initial colour depends on a theme has no defined rendering for a
  tree with no theme.
- **Set `color` on `text` in `controls.css`, the way controls do.** The narrowest
  fix and the wrong level. It makes `text` work and leaves every other primitive
  — and every widget an application writes — in the same trap, and it puts a
  colour on a node whose whole job is to inherit one.

## Consequences

- **`Finding.Kind.UNCOLOURED_ROOT` exists**, and `isDefect()` is now written as
  `!= UNTYPED_RULE` rather than `== DEAD_DECLARATION`. Unreadable text is a
  defect; the untyped-rule finding remains the only one that is a cost rather
  than a fault. Inverting the test is deliberate — a third kind added later is a
  defect unless somebody argues otherwise, which is the right default for
  something called a finding.
- **Nothing calls it.** An application that never asks gets the behaviour it has
  today, which is the trade ADR-0257 made for the whole package and is why a
  finding is a value rather than a log line.
- **The premise is asserted, not assumed.** `thePremise` resolves a bare `text`
  under an uncoloured root through the real cascade and checks it really does
  come out at `ComputedStyle.INITIAL`'s colour. If ADR-0066 is ever revisited,
  the test that fails is the one describing why this check exists.
- **The showcase is not changed.** It already does the right thing; what it now
  has is something that would have told it.
- **`docs/design-system.md` has no sentence about this.** It is worth one — §1.2
  or §10 could say that an application sets `color` on its root, and that a
  primitive inherits where a control does not — but this ADR does not write it.

# 260. A name is an attribute every widget has

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry on icon-only controls and gives §13's
"role **and name** for every widget" its missing half.

## Context

The entry is specific about the shape of the gap:

> **An icon-only segment has no accessible name, and neither does an icon-only
> button.** §3 requires `name=` for both and the attribute does not exist
> anywhere; §13's semantics are M5's. `Option` refuses a segment with neither a
> label nor an icon, which is the half that can be enforced today, and the other
> half is **a gap the whole catalog shares** rather than one this control
> invented.

Two things in that are worth separating. "§13's semantics are M5's" is about the
**AccessKit bridge** — the thing that carries a name to a screen reader — and
that is still M5's. But `Semantics.role()` and `Semantics.accessibleName()` have
shipped for milestones, `SemanticsSweepTest` already enforces that every
focusable widget answers both, and every control in the catalog already
implements them.

So what was missing was not a subsystem. It was a place to put a name that a
widget cannot work out for itself.

**An icon-only control's label is the empty string by construction.** That is not
an oversight in the widget — the icon is the whole of what is on screen, and
there is no text to derive from. `Button.accessibleName()` returned `label`, so
an icon-only button answered `""`: a control a reader cannot announce, passing a
sweep that only checked for null.

## Decision

**`name=` on `Attributes`**, beside `tooltip` and `context-menu`.

Those two are the precedent and the argument, already written in that file for
`tooltip`:

> Here rather than on each widget because that is what "any widget" means: a
> tooltip is not a property of being a button, and a catalog where each control
> had to remember to carry one would have thirty chances to forget.

A name is the same kind of thing, and more so: §13 asks for one on *everything*,
which is exactly the set `Attributes` covers.

### The label wins where there is one

`accessibleName()` returns the explicit name only when the derived one is empty.
An author who writes both has said the same thing twice, and the one on screen is
the one a sighted user reads aloud to somebody else — so it is the one a reader
should say.

### Blank is absent

`name("   ")` is `null`, which is `tooltip`'s and `contextMenu`'s rule. A reader
announcing three spaces is a reader announcing nothing, at more length.

### Two widgets read it, and the rest have it

`Button` and `Option` are the two §3 names, and they are the two that can be
icon-only. Every other widget now *carries* a name it does not yet read, which is
the right way round: the attribute is on the contract, and a widget that grows a
reason to prefer it needs no new mechanism.

## Alternatives considered

- **A `name` field on each widget that can be icon-only.** Two today, and the
  entry's own words are why not: it is "a gap the whole catalog shares rather
  than one this control invented". Two fields become five, and the fifth is
  forgotten.
- **Deriving a name from the icon's registered name.** `icon="trash"` is not
  "Delete", it is a key in a registry an application filled in; announcing it
  would read a developer's vocabulary out loud. Worse, it would make every
  icon-only control *look* named while being unusable, which is the failure mode
  the sweep exists to catch.
- **Waiting for the AccessKit bridge.** The bridge is what carries a name out;
  it is not what decides there is one to carry. Building it later against a
  catalog that cannot express a name would mean doing this then anyway, having
  shipped controls nobody could name in the meantime.
- **Making `accessibleName()` non-null and refusing an empty one.** It would fail
  the sweep for `tab-close` and `scroll`, which are deliberately named by their
  surroundings — the interface's own javadoc says null is an answer.

## Consequences

- **`Attributes` gained a sixth component**, and with it a five-argument
  constructor beside the three-argument one, for the reason the three-argument
  one exists: the positional form appears in every widget in the catalog and most
  of its tests, and a sixth `null` on all of them is four hundred edits to say
  nothing. A test asserts that **every** wither carries the new field forward,
  because that is the failure a widened record invites and `null` is a legal name
  so nothing else would complain.
- **An icon-only button answers `null` rather than `""`** when nobody named it,
  which is the honest answer and is now distinguishable from "named with the empty
  string".
- **Markup gets it for free** — `Attributes.of` reads `name=` off the node, so
  every widget that inflates through it can be named without touching its
  inflater.
- **`SemanticsSweepTest` is unchanged.** It checks that a name is *declared*, and
  it always passed; what it could not check is that the declared name is
  something a person could hear. That remains a judgement rather than an
  assertion, and the tests that cover it are the two widgets' own.

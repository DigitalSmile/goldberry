# 244. A child may say where it sits

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0111](0111-a-text-box-is-painted-inside-its-padding.md), and takes one of the
two things `stack` is blocked on.

## Context

The entry names the gap and prices it:

> `align-self` is not in §8's subset, which a tab strip's `+` found: a child
> shorter than its row sits at the top of it and there is no per-child way to say
> otherwise. It is the companion of `align-items`, which *is* in the subset, and
> Yoga's `setAlignSelf` is already bound — what it costs is a component in
> `ComputedStyle` and one in `Box`, both of which are records whose every wither
> would have to be revisited.

Two corrections to that framing, both in the toolkit's favour:

- **§8 has listed it all along.** `docs/ARCHITECTURE.md` §8's layout list reads
  `align-items/self/content`, and the sentence naming what is unimplemented says
  only `flex-basis`. So the document claimed this worked. What was missing was
  the implementation, not the sanction.
- **`Align.AUTO` was already waiting for it.** The enum's own comment says
  "`AUTO` only means anything for `align-self`" — a value that existed for a
  property that did not.

The price is real: `ComputedStyle` has 22 hand-written withers and `Box` 27, each
rebuilding its record positionally. Adding a component means editing 47 argument
lists, and `alignItems` and `alignSelf` are **the same type**, so a swap between
them compiles, runs, and is wrong.

## Decision

**One component on each record, `alignSelf`, defaulting to `Align.AUTO`.**

- `ComputedStyle` gains the component, the wither and an `align-self` case in
  `with`.
- `Box` gains the component, a builder method, and carries it through
  `style(ComputedStyle)` so a resolved declaration reaches the box.
- `RenderObject` calls `node.setAlignSelf` beside the existing `setAlignItems`,
  guarded by the same "only if it changed" comparison every other property uses.

`Align.AUTO` is the default on both, which is Yoga's and CSS's: a child that says
nothing is aligned by its parent's `align-items` alone. It is also the only value
that reads differently on a child than on a parent — "defer to my container" — so
`align-self: auto` is a real declaration that undoes a more general rule rather
than a missing one.

### The mechanical edit was scripted, and the safety net already existed

47 argument lists is not a hand edit. The clean sites — 22 in `ComputedStyle`, 25
in `Box` — are one identifier per argument, so they were rewritten by a script
that splits the argument list and inserts at a fixed index. The four that are not
clean carry inline comments containing commas, and were edited by hand.

What makes that safe is that `RecordWitherTest` already exists, from ADR-0181
when `Limits` was added, and it is exactly the check this needs: it asks **every**
wither to set its component to the value it already holds and requires the record
back unchanged. A wither that writes its argument into the wrong slot, reads the
wrong component into a slot, or passes one component twice all fail it. Its
premise — that no two components of one type hold equal values — is asserted by
`componentsAreDistinct`, which is why the fixtures give `alignItems`
`Align.FLEX_END` and `alignSelf` `Align.CENTER`.

So the risk this entry priced was already insured, by a test written the last
time somebody paid it.

## Alternatives considered

- **Group the align properties into a sub-record**, the way `Insets` and `Limits`
  group their four. It is the structural answer to long argument lists and
  `RecordWitherTest`'s own comment says so — and it would rename `box.alignItems()`
  at every call site in the toolkit for a benefit the test already delivers
  completely.
- **Put it only on `Box` and not on `ComputedStyle`.** A widget could then set it
  in `render` and no stylesheet could, which inverts the whole arrangement: the
  point of the entry is that a *document* can say where a child sits.
- **Add `align-content` at the same time**, since §8 lists all three. Nothing in
  the catalog wants one — it decides how wrapped *lines* share the cross axis,
  which matters only to a wrapped row in a box with a fixed height — and that is
  the rule §8's subset has grown by all along.
- **Ship a consumer with it.** The tab strip's `+` is the case that found the
  gap, and changing it moves goldens for a reason unrelated to the mechanism.
  Better as its own diff.

## Consequences

- **`stack` is one blocker lighter.** Its entry named two: the layering half,
  which `WindowRoot` already does with `position: absolute` and Yoga insets, and
  the alignment half, which was this. What remains is `stack` itself.
- **Five layout tests, four of which fail against the old code**, asserted
  against **Yoga's own output** rather than against the record — the property is
  one line in `RenderObject` and the whole risk is whether that line runs, so a
  test reading `box.alignSelf()` back would pass on a box nothing laid out.
- **`auto` is asserted to be indistinguishable from saying nothing**, and beside
  it a test that a non-`auto` value really does move the child — because the
  reason those two agree must not be that nothing is wired up at all.
- **`RecordWitherTest`'s two fixtures gained a value each**, and its coverage
  count went up by two on its own.
- **No golden moved**, because nothing in the catalog declares `align-self` yet.
  That is the honest state of a property added for the widget that will want it.
- **`docs/ARCHITECTURE.md` §8 is now true where it was optimistic**: its list
  included `align-items/self/content` while only the first resolved.

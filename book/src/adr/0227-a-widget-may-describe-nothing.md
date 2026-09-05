# 227. A widget may describe nothing

Date: 2026-08-30

## Status

Accepted. Closes a `TODO.md` entry opened by
[ADR-0175](0175-a-banner-says-its-kind-twice.md).

## Context

`message` took no `bind=`, and the entry that recorded it had already worked out
why the obvious fix was not one:

> A bound banner would be *present and empty* when the value was blank — a
> bordered box with 12px of padding saying nothing — and §8's subset has no
> `display`, so no widget can take itself out of a layout. […] What would close
> this properly is a way for a widget to describe *nothing*, which the element
> tree has no word for and which `collapse`, `group-box` and `field-message` have
> each worked around differently.

Every `build` has to return a widget. So a widget with nothing to show had two
options, and both are wrong in the same way:

- **Describe an empty box.** It takes no room of its own — and it is still a
  child, so a `column` with `gap: 12px` puts twelve pixels round it. The thing
  that vanished leaves a hole. `MessageBox` did exactly this for a dismissed
  banner, and its own documentation recorded the hole as somebody else's number.
- **Have the parent leave it out.** Correct, and it moves the decision one level
  up — so a `message` bound to an empty string can only be described away by
  whoever placed it, which is the application, which is the thing §9's `bind=`
  exists to spare. `Message.summary` returns an `Optional` for this reason.

The surprise on looking at the renderer is that the *mechanism* was already
there. A node that is neither `Styled` nor `Paints` contributes no box: its
children become its parent's directly, which is how every composition node works.
A widget with no children and no paint therefore already renders to nothing. What
was missing was a **name for it**.

## Decision

**`Widget.nothing()`** — a singleton `Leaf` with no children that implements
neither `Styled` nor `Paints`. Zero boxes, zero layout, no selector matches it.
**No new branch anywhere in the renderer or the element tree**: the tree could
always express this, and nothing could say it.

**A method rather than a constant**, and not for taste: a `static final` field on
`Widget` holding an instance of one of its own subtypes makes initialising
`Widget` depend on initialising `Nothing` and back again, which the compiler's
own class-initialisation-cycle analysis refuses.

**It is still an element.** The element stays in the tree, holding its state, its
place in the reconciler, and its subscription. That is the point rather than an
implementation detail: a widget that describes nothing this frame and something
the next is *one node whose value changed*, not a node destroyed and rebuilt. A
banner whose text empties and fills keeps its arrival phase and its binding.

**`message` gains `bind=`.** The value is read with `toString` like every other
bound text; a null or blank value makes the banner not there, and it comes back
when the value does. `text` remains the fallback for **no binding at all** and
not for a blank one — an application whose error property is empty means "there
is no error", and showing the document's placeholder words instead would be a
banner reporting a problem that has gone away.

**The dismissed case converges on it.** `MessageBox` no longer carries a
`departed` flag or a "draw nothing" branch; `MessageState` answers
`Widget.nothing()` once the exit has run out. The gap the container used to keep
round a departed banner is gone, and the record's documented wart with it.

**Not `display: none`.** §8's subset still has no way for a *rule* to take a node
out of a layout. What a widget decides about its own content it may now say; what
a stylesheet decides is unchanged, and this deliberately does not open that door.

## Alternatives considered

- **A nullable return from `build`.** The same meaning, spelled as the thing
  every null-safety convention in this codebase exists to avoid — and `describe`
  would need a branch where a singleton needs none.
- **`Optional<Widget>`.** It puts an allocation and an unwrap on the hottest path
  in the framework to express a case that arises in one widget.
- **A `display` property in §8's subset.** The general answer, and a much larger
  one: it would need the cascade, the layout and the hit test to agree about a
  node that is styled and absent, and it would let a stylesheet remove content —
  which is a different feature with different failure modes.
- **Leaving `message` without `bind=`.** Defensible while `Message.summary`'s
  `Optional` was the only caller; not once a document wants to write
  `message bind="form.error"`, which is the ordinary shape of a bound banner and
  the one a markup-first toolkit should not make impossible.

## Consequences

- **One new package-private record and one static method.** The smallest change
  in this log that closes an entry described as needing a new capability, which
  is what happens when the capability turns out to be a missing name rather than
  a missing mechanism.
- **`Message` grew a component**, so its canonical constructor has six arguments.
  The five-argument form is kept, because every caller written before `bind=`
  existed passes exactly those.
- **`MessageBox` lost one**, and with it a branch in `children()`, a branch in
  `render` and a term in `isAnimating`.
- **A dismissed banner no longer leaves a gap.** A behaviour change, and the one
  the entry was complaining about.
- **The other two workarounds are not converted.** `field-message` draws a styled
  empty box when a field is fine, and switching it to `Widget.nothing()` changes
  the spacing of **every form** — five golden images say so. That is a design
  decision about §4's "message slot", not a bug fix, and it is not this entry's
  to make. The vocabulary is there when somebody wants to make it.
- **A tree whose *root* describes only nothing still throws**, which is the
  answer the renderer already gives a root that describes only composition: a
  window with nothing to paint is a mistake, not a blank screen.

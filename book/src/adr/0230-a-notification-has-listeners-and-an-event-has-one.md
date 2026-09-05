# 230. A notification has listeners, and an event has one

Date: 2026-08-30

## Status

Accepted. Closes a `TODO.md` entry opened by
[ADR-0105](0105-a-tooltip-is-an-attribute-not-a-widget.md).

## Context

`PointerRouter.onPointingChanged` is how something above the router learns that
the hovered or the focused node moved. §7 shows a tooltip "on hover *and on
keyboard focus* after delay", so the thing that opens one has to hear about both;
the router itself opens nothing, because it has no window and no notion of one.

It was **one slot**, and the record said why:

> Not a list: a second listener would be a second thing deciding what a hover
> means, and there is exactly one.

The refusal is defensible and the implementation is not. A slot whose setter is
named `onPointingChanged` reads like a registration and behaves like an
assignment: a second caller silently drops the first. The failure mode is a
tooltip that stops appearing, with nothing anywhere saying that anything was
displaced.

The entry that tracked it stated the price of fixing it as "a real listener list
*and* a decision about what it means for two things to react to one hover".

## Decision

**The decision is that there is nothing to decide, and the reason is what this
record is for: it is a notification, not an event.**

Nothing is passed. Nothing can be consumed. No listener can change what another
sees, because each reads `hovered()` or `focused()` from the router for itself,
and the router's state is the same for all of them. Order is therefore not a
policy — it is registration order because a list has one, and no correct listener
can depend on it.

An **event** would be the thing worth refusing. One that carried a target, or
that could be consumed, would make a second listener a second thing deciding what
a hover means — exactly the objection ADR-0105 raised, aimed at a shape this
facility does not have.

**So: a list, and a `Subscription` back.** The registration is a thing you can
give up, which the slot could not express at all: there was no way to stop
listening.

**Copy-on-write, and not for threads.** A listener may cancel itself, or another,
from inside a notification — a tooltip's listener that fires once and unregisters
is the obvious shape — and iterating a snapshot is what makes that safe without a
copy per notification. Hovers are frequent; registrations are not.

**The launcher keeps its handle and closes it in `shutDown`.** Its router dies
with it, so this is tidiness rather than necessity — and tidiness is what stops
the next caller from assuming it does not have to.

## Alternatives considered

- **Leaving the slot and documenting the hazard.** The hazard is invisible at the
  call site: `onPointingChanged(x)` looks like every other registration in the
  toolkit, and the one that replaces rather than adds is the one nobody expects.
- **Throwing on a second registration.** Honest, and it makes a legitimate second
  consumer impossible rather than merely surprising.
- **A `List<Runnable>` with a `remove(Runnable)`.** It works and it compares
  lambdas by identity, so a caller has to keep the exact reference it passed —
  which is what a `Subscription` is, with the mistake removed.
- **An event object with a target and a `consume()`.** The shape ADR-0105 was
  right to refuse. It buys nothing here: the router's state *is* the answer, and
  a snapshot passed alongside it would be a second copy that could disagree.

## Consequences

- **`onPointingChanged` returns a `Subscription`** and adds instead of replacing.
  There is one caller in the toolkit and it is `Launcher`; no application code
  changes, because the method is on a class an application does not hold.
- **`input` now names `bind.Subscription`.** A general "registration that can be
  undone", already in the module and already the shape the binding layer hands
  out; the alternative was a second identical interface in `input`.
- **Six tests in `PointerRouterTest`** — that two listeners are both told, that
  each reads the router rather than being handed anything, that closing stops one
  and leaves the others, that closing twice is harmless, that a listener may
  cancel itself mid-notification, and that focus counts as pointing moving.
- **The distinction is now written down where it will be read.** The next
  facility that has to choose between a slot and a list has a rule: if the thing
  being delivered can be *consumed*, one listener; if it is only a nudge to go and
  look, a list.

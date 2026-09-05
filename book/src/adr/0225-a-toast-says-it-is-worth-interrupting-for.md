# 225. A toast says it is worth interrupting for

Date: 2026-08-30

## Status

Accepted. **Narrows** a `TODO.md` entry opened by
[ADR-0177](0177-a-toast-is-a-queue-and-the-stack-is-the-widget.md) rather than
closing it: the widget half is finished here, and the announcement itself is
still M5's AccessKit bridge.

## Context

`TODO.md` recorded a toast as not announced, and said the gap was "M5's AccessKit
bridge like every other widget's semantics — and the one place in the catalog
where the absence really costs something".

That last clause is the part worth taking seriously, and it is not a matter of
degree. Every other widget in the catalog is announced because **something
happens to it**: the focus lands on a button, a reader walks onto a row, a value
changes under someone who went looking for it. In every case the reader's own
cursor is the event, and a role and a name are enough.

A toast has no such event. Nobody focuses it — it is not focusable, and making it
so would trap the keyboard in a thing that vanishes in five seconds. Nobody has
to click it. It appears, it is read, it goes. A reader that speaks only what is
reached says nothing at all about the one thing on the screen that exists to be
noticed, and it will still say nothing on the day the bridge lands — because
`Role` and `accessibleName` describe a node, and what is missing is the claim
that its **appearing** is itself worth speaking.

That claim is §7's "live region", and it was unspellable.

## Decision

**`Semantics` gains `live()`**, answering a `Live` of `OFF`, `POLITE` or
`ASSERTIVE`, defaulting to `OFF`.

**On the widget, not derived from the role.** The same role can be live in one
place and not in another, and a role that implied liveness would make the choice
unspellable in the other direction.

**`Role` gains `STATUS`** — a region that reports what just happened rather than
what is true. Neither existing value fits: `GROUP` is a boundary with content in
it and `DIALOG` is somewhere the user *is* until they leave, and a notification is
a sentence that appears and goes.

**`ToastBox` is `POLITE`, and it is the only live region in the catalog.** A
notification waits for the reader to finish the sentence they are on.
`ASSERTIVE` exists and is unused, which is a statement: interrupting is for
something that must be dealt with before anything else, and a toast is by
construction dismissible and transient.

**Its name is its text and not its button's label.** The action button is a
`Button` with a name of its own, so folding the two together would have a reader
say "Undo" twice.

**The stack is not a live region.** Three toasts must be three announcements, not
four.

**Rarity is enforced by a test, not by a convention.** `SemanticsSweepTest`
asserts that `ToastBox` is the *only* class in the catalog that overrides
`live()`. A widget added later that decides it also deserves interrupting has to
come to that test and say why — which is the review this decision is worth
having.

## Alternatives considered

- **Waiting for M5 and doing all of it at once.** It sounds tidier and it makes
  the bridge harder: the bridge would then have to answer "which nodes are live"
  for a catalog of fifty-one widgets, from outside them, at the moment when the
  cost of getting it wrong is highest. The widget knows; recording what it knows
  is cheap now and free later.
- **Putting the live region on the stack**, which is what the ARIA idiom does —
  a container marked live, announced when children are inserted. AccessKit's
  model is per-node, and a stack marked live would announce the stack as well as
  each toast in it.
- **A boolean `isLiveRegion()`.** Two values would make `polite` look like a
  default rather than a choice, and the third value is the one that documents why
  a toast is not it.
- **Making a toast focusable so the ordinary path reaches it.** It is the worst
  option and the one that looks easiest: a Tab stop that disappears after five
  seconds moves the focus somewhere the user did not ask for, and a persistent
  toast becomes a Tab stop between every control and the next.
- **Reusing `Role.GROUP` rather than adding `STATUS`.** It would be a lie of the
  cheap kind — a reader would say "group" where the right word is nothing at all,
  because the sentence *is* the announcement.

## Consequences

- **The entry stays open**, rewritten. Nothing is announced yet, because there is
  no bridge to announce it. What changed is that the remaining work is entirely
  M5's and needs no decision from the catalog.
- **`Semantics` grew a defaulted method**, so no existing implementation changed
  — which is the shape that made this affordable to add before its consumer
  exists.
- **`Role` grew a fifteenth value** for one widget. That is the rule the enum
  states for itself: it grows when a widget arrives that is genuinely none of the
  others.
- **`ASSERTIVE` has no consumer.** Deliberate, and the sweep will notice if that
  changes.
- **`ToastGoldenTest` is unaffected**, because none of this draws anything. That
  is worth saying: this is the second facility in the toolkit (after `Role`
  itself) whose entire value is invisible until a bridge reads it, and whose
  entire cost today is one test.
- **What is still owed to §7 beyond the bridge**: a `message` that changes under
  a reader is arguably live too, and is not — a banner is part of the layout and
  is read in document order, and announcing every re-render of one would be
  worse than silence. If that turns out wrong, the vocabulary is now there to say
  so.

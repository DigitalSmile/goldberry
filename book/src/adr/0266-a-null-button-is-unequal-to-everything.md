# 266. A null button is unequal to everything

Date: 2026-09-05

## Status

Accepted. Closes the `onPointer` guard entry.

## Context

The entry records a bug and then generalises it, and the generalisation is the
part worth acting on:

> **A guard at the top of `onPointer` is a guard on every pointer kind, and the
> kinds do not carry the same fields.** `text-input` tested `button() == PRIMARY`
> there and silently lost every drag, because `PointerRouter.pointerMoved` builds
> its event with a null button — a motion is not a button event (ADR-0168).
> `Slider` asks per kind and reads as a style choice until this happens. **Nothing
> warns**; a `PointerEvent` accessor that is meaningless for the kind in hand
> answers with a default rather than refusing, which is **right for `dragX`'s
> `NaN` and quietly wrong for a null `button`**.

That last sentence is the whole of it, and it is exactly right. The two defaults
are not the same kind of default:

- **`dragX`'s `NaN` is arithmetic.** The meaninglessness *propagates*: every
  comparison against `NaN` is false, in both directions. A caller cannot act on it
  by accident, which is why `Toggle`'s handler can read it with no guard at all.
- **A null button is a reference.** It compares equal to nothing and **unequal to
  everything** — so `button() != PRIMARY` is *true* for a move, and the guard
  fires backwards: the press it was written for keeps working and every drag is
  dropped. Which is precisely what happened.

## Decision

**Report a button read from a kind that carries none. Once, and keep answering
null.**

### Not a refusal

Throwing would turn a lost drag into a window that falls over, from inside an
input handler, on a mistake an application can make in its own widgets. Every
other diagnostic in the toolkit is held to the same rule — `ScrollState`'s nested
scroller, `ScrollContent`'s `flex-grow`, `ListRow`'s pitch — for the reason
ADR-0251 stated: turning a rule into a crash is worse than the rule going unheard.

### Not a sentinel

`Button.NONE` was the other shape and it fixes nothing: `button() == PRIMARY` is
still false and `!= PRIMARY` is still true, so the guard still fires backwards
and now does so against a value that looks deliberate. It would make the null
safe and the *bug* invisible, which is the wrong half.

### Keyed by kind and node type

`(MOVED, text-input)` is one report however long the pointer is over it, and two
widgets making the mistake are two reports. A pointer event is read per event per
handler, so an unguarded warning is a few thousand lines a second on a trackpad —
[ADR-0243](0243-a-missing-token-is-a-message-not-a-stream.md)'s rule, applied at
the highest event rate in the toolkit.

The message says the general thing rather than the local one: *"a guard at the top
of onPointer is a guard on every kind; ask inside the arm that has a button."*

### Nothing in the catalog trips it

All nine `button()` reads in `:core` and `:widgets` are already inside a kind
check — seven in a `switch` arm, and `Toggle`'s behind a short-circuiting `||`
that only reaches it for a `RELEASED`. So the diagnostic fires on the mistake and
on nothing else, which is what makes it worth having at this event rate.

## Alternatives considered

- **`Optional<Button>`.** It makes the mistake unwritable, and it changes the
  signature of the most-called accessor on the busiest event in the toolkit — nine
  call sites, an allocation per read unless the caller is careful, and a
  `switch` arm that already knows the answer having to unwrap it.
- **Throwing.** Discussed above. It is also the one behaviour that would make a
  *third-party* widget's guard take down a window that works today.
- **A compile-time answer — separate types per kind.** `PointerEvent` would become
  a sealed hierarchy and every handler a pattern match. It is the correct design
  and it is a rewrite of the input layer; the entry describes a trap, not a
  request for one.
- **Leaving it.** The entry's own words are the argument against: "`Slider` asks
  per kind and reads as a style choice until this happens."

## Consequences

- **The trap announces itself**, and names the widget and the kind. A `text-input`
  written next month that loses its drags says so on the first move instead of on
  the first bug report.
- **`PointerEvent` gained a static report set** — the sixth in the toolkit, with
  its `forget` for tests. `ADR-0257` already noted the point at which that pattern
  should be extracted; this is past it, and extracting it is worth doing on its
  own rather than inside an entry about pointer events.
- **`button()` costs a null check** on the hot path. It was already a field read;
  it is now a field read and a branch that is not taken for any event that has a
  button.
- **Six tests**, two of which are about the *difference* between this default and
  `dragX`'s — because the entry's insight is that comparison, and a test that only
  checked the warning would lose it.

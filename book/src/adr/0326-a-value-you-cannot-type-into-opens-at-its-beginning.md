# 326. A value you cannot type into opens at its beginning

Date: 2026-09-16

## Status

Accepted. Closes `docs/gaps.md` G34.

## Context

`TextInput.readOnly(true)` is the control for a value somebody has to take off the
screen: a peer invite, a key, an identifier. It takes focus, selects with the
pointer and with `Ctrl+A`, copies with `Ctrl+C`, and refuses every edit. All of
that was already right, and the entry that raised this said so.

What was wrong was where it opened. `TextEdit.of(text)` puts the caret at
`text.length()`, and a field scrolls to keep its caret in view — so a value wider
than the box showed its **tail**. A hundred-and-twenty-character invite read
`…fiahiyvvqd` where a reader wanted `endpointabrq…`.

For an editable field that is right and is not in question: you type at the end of
what is there, and a field that opened at the start would put the caret in front
of the value somebody is about to correct. The case that had never been looked at
is the one where nobody is going to type at all.

## Decision

**A read-only field starts its caret at offset zero.**

`TextEdit.atStart(String)` joins `TextEdit.of(String)` as its mirror — same value,
other end — and `TextInputState.opening(String)` picks between them from
`widget().readOnly()`. It is used in two places, which is the whole change:
`initState`, and `follow()` when the application replaces the value later.

### Why not the API the entry proposed first

The gap offered two shapes:

```java
public TextInput caret(int offset);   // where the caret starts; the view follows it
```

or, in its own words, *"without new API and probably better: a read-only field
starts its caret at 0"*. The second is what landed, and the reason is that the
first is a call site that can be forgotten. Every read-only field in every
application wants the same answer; a parameter makes that answer something each
one has to remember to ask for, and the one that forgets looks exactly like the
bug being fixed here.

`caret(int)` remains available to build if something ever wants a caret
*somewhere else*. Nothing does, and a method with no caller is a method with no
test.

### What it deliberately does not do

A field that becomes read-only **after** it is mounted keeps its caret. The rule
is about where a value *arrives*, not a continuous invariant — a control that
yanked the caret to the start because a flag flipped would be moving a selection
the user may have made.

## Consequences

- One behaviour change, in one direction, for a case where the old behaviour had
  no defender. `ReadOnlyCaretTest` pins both halves: read-only opens at the head,
  editable still opens at the tail.
- `TextEdit` gains a public factory. It is `of`'s mirror and documents which end
  it puts the caret at and why, so the pair reads as a choice rather than as one
  method and an exception.
- `TextInputState.scrolledBy()` is now package-private rather than absent, so
  "the box shows the head of the value" is a number in a test rather than a
  picture. `TextAreaState` already had the same accessor for the same reason.
- `text-area` needs nothing: it has `caretMatters`, which already keeps an
  untouched area at the top of its value whatever the caret says.

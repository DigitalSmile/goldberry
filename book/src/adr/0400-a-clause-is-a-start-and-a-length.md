# 400. A clause is a start and a length

Date: 2026-09-18

## Status

Accepted. Answers W2 and W3 of the whole-tree review recorded in
`docs/review-2026-09-18.md`.

Follows [ADR-0376](0376-one-key-map-three-editors.md), which is where the three
editors agreed on one key map and did not agree on this.

## Context

An input method reports a composition as four numbers: the preedit text, a caret
inside it, and the clause the platform is converting — a start and an extent.
SDL hands them over in `SDL_TextEditingEvent` as `start` and **`length`**, and
`PreeditEvent.length()` is translated and documented as a count of chars.

Everything downstream computed `start + length`. Both `TextInputState` and
`TextAreaState` did, and so did `:core`'s `Editor.onPreedit`. Only the *name* said
otherwise: the parameter was `clauseEnd`, and `TextEditor`'s `@param` described an
end. A seam whose four numbers are only three distinct facts is a seam where one
of them gets ignored, and one was: the early return that decides whether a
composition changed compared the text, the caret and the clause **start**, and
never the clause's extent. An input method that resizes a segment at the same
start — which is what every Japanese IME does while a reader presses the arrow
keys to grow a conversion — changed nothing on screen.

The same two classes carried a second bug of the same family. `clip`, which
enforces `maxLength`, counted **code points** to find the cut and then took
`Math.min(end, room)` in **chars**, undoing the step it had just taken:
`clip("a🎨b", 2)` ended in a lone high surrogate. The comment above it promised
more than code points — "never through a cluster" — and the code delivered less
than one.

Both bugs existed twice because `TextInputState` and `TextAreaState` carried
byte-identical copies of `clip`, `room`, `compose` and `clearPreedit`. The review
found the duplication independently and noted that `form/Carets.java` had been
created to stop exactly this.

## Decision

**The clause is a start and a length, everywhere, and the end is derived once.**

- `clauseEnd` is `clauseLength` in `TextEditor`, `AreaEditor` and both
  implementations. A doc comment cannot change what the platform puts in the
  struct; what it can do is stop disagreeing with it.
- The end — which is what a painter wants, and what `Composing` carries — is
  computed in one place rather than at each of four call sites.
- The early return compares all four numbers.

**A limit cuts on a grapheme boundary.** `clip` steps back to the nearest
boundary from `BreakIterator.getCharacterInstance()` — the same class `TextEdit`
steps a caret with, so a clip and a caret cannot disagree about where a character
is. A surrogate pair survives, and so does a combining accent, which is what the
comment always said.

**The shared arithmetic lives in `widgets/form/parts/`.** `MaxLength` holds `room`
and `clip`; `Preedit` holds the four preedit fields, the clamping, `wouldChange`,
`set`, `clear` and `composingAt`. What was *not* lifted is what actually differed:
a `text-input`'s `compose` refuses a `password`, each `clearPreedit` wraps its own
`setState`, and `accepts` asks a `text-input`'s `filter`. `form.parts` is the
package `module-info` already describes as "public in a package nothing can see",
so none of this becomes API.

## Alternatives considered

- **Making the implementations match the doc** — treating the number as an end.
  It would have meant translating SDL's `length` to an end at the boundary and
  back again for anything that wanted an extent, to satisfy a comment. The
  platform's shape wins.
- **Lifting all four methods.** Two of them differ in a way that matters, and a
  shared method with a boolean for "is this a password" is the duplication back
  in a worse form.

## Consequences

- A composition whose clause grows or shrinks at the same start now redraws. The
  defect was **latent** through today's only caller, which is worth recording
  honestly: `PreeditEvent.caret()` is defined as `clamp(start + max(0, length))`,
  so it *is* the clause end whenever a clause is reported, and the caret
  comparison caught the resize by accident. It bites the moment an event carries
  a caret of its own — which is how IBus and macOS report a caret inside a
  converting clause. The tests therefore drive the editor seam directly, with a
  fixed caret and a changing clause length.
- `maxLength` can now refuse a character that a code-point count would have
  admitted: pasting `a🎨b` into a `maxLength(2)` field yields `a` and not
  `a` + half an emoji. That is a visible behaviour change and the right one.
- Two controls share two classes they did not share before. The next preedit or
  limit bug is one fix rather than two, which is the whole argument — and the
  reason this record exists rather than two commits.

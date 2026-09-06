# 273. A code is a string, and the boxes are a drawing

Date: 2026-09-06

## Status

Accepted. Builds `docs/core-widgets.md` §4's `code-input`.

## Context

§4 gives this widget one paragraph, and it is unusually complete:

> **`code-input`** — the one-time-code field: `length=6` separate
> single-character boxes over one value, `type="digits|alnum"`. It exists as its
> own widget rather than a styled `text-input` because its *editing model* is
> different, and that is the whole of the specification: typing advances,
> `Backspace` on an empty box moves back and clears the previous one, **a paste
> of the full code fills every box at once** (the thing users actually do), and
> focus lands wherever the first empty box is. `complete` fires when the last box
> fills, which is what lets a form submit without a button. `mask=#true` for
> authenticator-style secrecy. Semantics: a single textbox with the whole code as
> its value — six boxes are a drawing, not six fields, and announcing them
> separately would be a lie.

The interesting thing about that paragraph is that its four editing sentences are
not four rules. They are two, seen from four directions.

## Decision

**`CodeEdit` is a string and a box count, and nothing else.**

No caret, no anchor, no undo stack, and no per-box array. The active box is
`min(filled, length - 1)` — **derived**, not held — and everything §4 asks for
falls out of that:

- "Typing advances" is appending.
- "Focus lands wherever the first empty box is" is not implemented at all. It is
  what the derivation *says*, on every frame, with nothing to keep in step.
- "`Backspace` on an empty box moves back and clears the previous one" is
  dropping the last code point, because the box the ring is on is always empty,
  so the box to clear is always the one before it. There is no second case.
- "A paste of the full code fills every box at once" is the same append as
  typing: committed text arrives as a string, one character from a keystroke and
  six from a paste, so one operation does both.

**No holes.** The filled boxes are always a prefix. The alternative — an array of
`length` slots, each filled or not, with a caret that can be moved into the middle
— is what an editing model with a caret would have to be, and it fails on the
sentence §4 ends with: six boxes are announced as a **single textbox with the
whole code as its value**, and a code holding `12` and `56` with a gap between
them has no honest string to announce. A gap is not a state a one-time code has.

**Per-character filtering, which is the toolkit's one exception to
`TextFilter`'s rule.** `TextFilter` is asked about the whole value an edit would
produce and answers yes or no, and its javadoc argues the case: a filter that
rewrote what was typed would move the caret out from under somebody mid-word.
`CodeType` is asked one code point at a time and drops what it does not want,
because neither half of that argument survives here. There is no caret to
disturb, and the case §4 calls "the thing users actually do" is a paste out of
`Your code is 123 456` — which a whole-value filter rejects entirely and a
per-character one turns into six filled boxes. Refusing the paste a user was told
to make is a worse answer than ignoring a space. The alphabets themselves are
`TextFilter`'s, not copies: `TextFilter.ALPHANUMERIC` has said "what `code-input
type=\"alnum\"` will want" since it was written.

**`complete` fires on the edit that filled the last box**, guarded by a flag
rather than by the code being full. A field that raised it whenever it was full
would submit a form again on every rebuild. A `Backspace` clears the flag, so a
mistyped code corrected and finished completes a second time — which is right,
because that is two codes.

**One Tab stop, one textbox, and the boxes are parts.** `CodeField` is the
focusable node, is `Role.TEXT_FIELD`, and is what a document names; `CodeBox` has
no focus, no keys and no semantics. That is §4's last sentence built rather than
quoted.

## Two things §2's metrics row asked for that CSS could not say

- **"Group gap 16 at the midpoint when `length` is even."** §8's selector subset
  has no `:nth-child`, so nothing in a stylesheet can say "wider after the third
  one". The boxes go into `code-group` parts instead — the row of groups carries
  the 16 and a group carries the 8 — so both numbers are written where they are
  read. An odd length is one group, so the outer gap never applies and the row is
  the flat one §2 describes; the tree is the same shape either way, which is what
  keeps the stylesheet from having to know which case it is looking at. The
  alternative was a zero-width spacer between the halves, which turns one 8-point
  gap into two and arrives at 16 by an arithmetic nobody reading the CSS would
  see.
- **"Box 40×48 (36×44)."** The one control in the catalog whose density is two
  numbers rather than a height, so `--gb-code-box-width` and
  `--gb-code-box-height` are both tokens and `density-compact.css` moves both. A
  code box is a character in a frame, and six boxes that narrowed without
  shortening would be a code drawn on graph paper.

## What it does not have, and why

- **No arrow keys.** `text-input` consumes its arrows because it has a caret they
  move, and consuming them is what stops `Left` from walking the focus scope out
  from under somebody editing. This has one insertion point that is a function of
  what is filled, so `Left` would either do nothing visible or move a ring the
  next keystroke moves back. They are left alone, so a code field sits inside an
  arrow-navigated scope and behaves like the single control it announces itself
  as.
- **No copy and no cut.** §4 asks for the paste by name and asks for no way out,
  and a `mask`ed code must not have one for `password`'s reason. Offering it on
  an unmasked field only would be a control whose keys depend on how it is drawn.
- **No caret, so no blink timer.** The focus ring on the active box is what says
  where the next character goes, and §2.2 asks for it to be instant. A window
  with a focused code field therefore asks for **no frames at all**, which is
  §1.7's idle loop holding for one more control — `text-input` had to build a
  530 ms timer to keep that true.
- **No `Validator` seam.** A code is text until something checks it, which is
  exactly what `Validator<String>` already is. The typed-value seam the TODO list
  names is `date-picker`'s to open, not this one's.

## Consequences

- **Five golden images**, because every number in §2's row is a geometry no
  assertion can see: the 16 at the midpoint and not at every gap, the ring on one
  box rather than around the six, a `title` character centred in each box, and
  what a mask draws. `code-input-focus` has its `-light` twin, which
  `FocusGoldenPairTest` requires rather than this record.
- **Fifty tests, and most of them need no widget.** The editing rules are
  `CodeEditTest`'s, against the value, with no font and no frame — `TextEdit`'s
  arrangement, and the reason §4's paragraph is testable sentence by sentence.
- **The Forms screen gained a seventh markup card**, wired the way a real one is:
  `bind=` down, `change=` back up, and `complete=` writing a status line. It is
  the only card on that screen that shows a control raising **two** events, which
  is the difference a screenshot can otherwise not show.
- **`code-box` gained a `filled` class §2 does not ask for.** A row of six
  identical frames says nothing about how far through a code somebody is; the
  stronger edge is the vocabulary `card` already uses for "this edge is the
  meaningful one", and `border-color` is on §1.7's whitelist so it may fade.
- **§4 has three widgets left**, and they are the pickers. Every one of them is
  a typed field plus a popover, which is a different shape from this one: this
  was the leftover that reused the *least*.

## Alternatives considered

- **A styled `text-input`.** §4 rules it out and the rule-out is right: `TextEdit`
  is a string, a caret and an anchor, and this widget would have had to hold all
  three still while re-deriving the box index from the caret on every frame.
- **An array of slots with a movable caret.** Priced above. It is the model a
  code field would need if a code had holes, and it does not.
- **A `:nth-child` selector for the group gap.** Not in §8's subset, and adding
  one for a single metric is a change to the styling language for a widget.
- **Announcing the boxes.** §4 calls this a lie in as many words. The boxes carry
  no `Role` at all.

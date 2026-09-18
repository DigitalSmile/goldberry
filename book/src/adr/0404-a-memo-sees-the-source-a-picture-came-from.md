# 404. A memo sees the source a picture came from

Date: 2026-09-18

## Status

Accepted. Answers H3, H6 and H7 of the whole-tree review recorded in
`docs/review-2026-09-18.md`.

Extends [ADR-0389](0389-a-block-nobody-typed-in-keeps-its-widget.md), whose §4
argued the signature question for handlers and not for sources.

## Context

Three decisions in `:html` came out of the review, and each of them is a case
where the obvious fix is not the right one.

**A memoised block kept its picture for ever.** ADR-0389 lets a
`markdown-view` keep the widget of a block nobody typed in, keyed on a
*wiring signature*. The signature was four presence bits — is there a link
handler, a task handler, an image source, a code highlighter — which is exactly
right for a **handler**: a block asks a handler to do something *later*, so two
handlers that both exist are interchangeable as far as the block is concerned.
An `ImageSource` is not a handler. A block asks it **what to draw, during the
build**, and keeps the answer for as long as the memo keeps the block. Swapping
the source was therefore invisible, and a note went on showing the picture the
old source had returned.

**A selection could not wash a link.** A `Word` that wraps a child widget
returns early from `render` and never calls `WordGeometry.shaped` — the label is
shaped by the `button` that holds it, in the button's own style, and nothing
hands that paragraph back. With no paragraph, `xOf` answered `rect.left()` for
every offset, so both ends of a link were the same place: a selection ending
inside one washed none of it, and a double-click highlighted nothing. (The
copied *text* was already right, which is why nobody noticed.)

**A `<tr>` outside a table lost its cells.** The fold sent it through `rows()`,
which matches only `tr`, sections and `caption`, so a row asked for its rows got
none. Here the HTML Living Standard and this codebase's own rule disagree: "in
body" treats a stray `<tr>` as a parse error, **ignores the tag** and keeps only
its text.

## Decision

**The image source's identity is part of the wiring signature.**
`present * 31 + System.identityHashCode(images)`, computed in `of(...)` — which a
build calls exactly once — so `signature()` is a field read and a note of N
blocks pays one `identityHashCode` per keystroke rather than N. That is what
keeps ADR-0389's promise intact: the memo is cheap because it is asked once per
block per build and answers from a number it already has.

Two costs are written at `signature()` rather than discovered later:

- Replacing the source rebuilds the **whole note** once, not just the blocks
  holding pictures. A swap is not a per-keystroke event, so once is affordable;
  a finer answer would mean asking every block which images it holds.
- A source that answers *differently without being replaced* is not noticed.
  Noticing would mean calling it per image per keystroke, which is the cost
  ADR-0389 exists to avoid. `ImageSource` now tells an application to **hold** a
  source rather than mint one inside `build`.

**An unshaped word is measured across its own rectangle.** `offset 0` is the left
edge and the last offset the right one — exact at both ends, proportional in
between. The alternative was to shape the text a second time in the word's own
style, which would produce widths that are *not the ones drawn*: a wash that is
wrong in a way that looks right. An image, whose text is `""`, still washes as a
whole box or not at all.

**A stray `<tr>` is drawn as a row of its cells.** `Element`'s rule — "nothing is
dropped for being unknown" — is this repository's and not the standard's, and it
is the rule the fold already follows everywhere else: a stray `<p>` in a list and
an unknown tag are both drawn where they are. The text a reader sees is the same
text a browser shows; what differs is that the structure survives.

`HtmlParser` does follow the standard where the standard is about *structure*:
the "in cell" insertion mode closes an open cell for a section, which is H5.

## Alternatives considered

- **Presence rather than identity for the image source**, keeping ADR-0389's
  four bits. It is what was there, and it is the bug.
- **Comparing sources by `equals`.** An `ImageSource` is an interface an
  application implements; most implementations are lambdas or records over a
  path, and requiring value equality of them would be a contract this module
  cannot enforce and applications would silently fail. Identity is honest about
  what is actually being compared.
- **Following the spec for the stray `<tr>`.** It would drop a cell's structure
  to match a parse-error recovery rule written for a browser that has a table
  insertion mode to fall out of. This model has neither.

## Consequences

- A picture whose source was swapped is redrawn. `BlockReuseTest` now holds all
  three invalidation paths the review found missing: the picture after a kept
  block, `tasksSeen` resumed after a kept block, and a memoised link calling the
  **new** handler. Pressing a task box needs the real router, so that test grew
  a mount/router pair.
- A selection that ends inside a link washes the part of it the pointer covered,
  at the cost of a measurement that is proportional rather than glyph-accurate
  inside the word. Nothing in the toolkit needs glyph accuracy there — a wash is
  a rectangle — and the ends, which are what a reader aims at, are exact.
- `<tr>` outside a table renders where a browser renders text and keeps a
  structure a browser throws away. An application reading the model sees the
  cells; a test comparing against a browser's DOM would not match, and nothing
  does that.

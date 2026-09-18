# 389. A block nobody typed in keeps its widget

Date: 2026-09-18

## Status

Accepted, closing `docs/gaps.md` G45.

Uses [ADR-0315](0315-a-rebuild-is-not-a-restyle.md)'s first guard — *the same
description is not a description* — from the caller it was waiting for, and
narrows [ADR-0301](0301-a-selection-is-geometry-the-frame-already-had.md)'s
numbering of words from the document to the block.

## Context

G45 was measured downstream: a note editor with a `markdown-view` beside it,
rebuilt with a new `Document` on every keystroke, as the view's own documentation
says to. One paragraph changes and every stage of the frame grows with the whole
note.

A number from somebody else's application is a number nobody here can re-take, so
the first thing this did was reproduce it in the repository:
`MarkdownFrameBenchmark`, a `markdown-view` bound to a property, inside a `scroll`,
with a character typed into a paragraph in the middle of the note and the four
stages of `FrameStats` timed around it. Mean / worst, in ms, on this machine:

| note | build | style | layout | raster | frame |
|---|---|---|---|---|---|
| 2 kB | 0.45 / 16.44 | 0.29 / 0.96 | 0.34 / 0.86 | 2.22 / 5.21 | 3.30 / 23.47 |
| 50 kB | 8.71 / 18.33 | 7.29 / 12.49 | 4.98 / 7.50 | 3.16 / 5.36 | 24.14 / 43.68 |
| 500 kB | 85.35 / 109.41 | 78.32 / 102.93 | 42.60 / 60.94 | 3.71 / 4.99 | 209.97 / 278.26 |
| 50 kB, **typing a space** | 6.84 / 12.10 | 18.30 / 25.85 | **72.48 / 82.33** | 2.42 / 3.11 | 100.03 / 123.39 |

It reproduces. The absolute numbers are smaller than the entry's — a faster
machine and a narrower preview — and the shape is exactly what it describes: every
stage grows with the note rather than with the paragraph.

The last row is not in the entry and is the interesting one. **A keystroke that
adds a word costs fifteen times the layout of a keystroke that does not**, and in
ordinary prose roughly one keystroke in six is a space. Typing `fo` into `fx` is
cheap; typing the space after the word is 72 ms of Yoga.

md4c is timed alongside as a **control** — the same code whatever the view does —
at 1.06 ms for the 50 kB note and 8.35 ms for the 500 kB one. So the build column
is the view, not the parser, which is what the entry claimed.

### Why the element tree was not already doing this

ADR-0315 put the mechanism in place a fortnight ago:

```java
void update(Widget next) {
    if (next == previous) {          // nothing below this is walked
        if (needsBuild) rebuild();
        return;
    }
```

The same widget instance describing the same node means the node's subtree cannot
have changed, so nothing under it is re-described, re-cascaded or re-measured. A
`scroll` gets this for free because it keeps its children in a record field and
hands the same objects back.

`markdown-view` cannot, because it has nothing to hand back. `MarkdownView.build`
makes a `MarkdownWidgets` per build and walks the whole document with it, so every
block is a new `Column`, every paragraph a new `Row` and every word a new `Word` —
equal to what was there and identical to none of it. The guard never fires and the
element tree re-describes the note top to bottom.

That is the first half. The second half is why re-describing it was so expensive
even where the description was the same.

### A word was numbered by the document, and that is what a space moved

ADR-0301 gave each word an index — its position in document order — and used it
for three things at once: the key the element tree reconciles on, the slot in the
geometry's flat array of entries, and the order a selection reads in.

Insert a space in the first paragraph and every word below it is renumbered. The
element keyed 412 is now asked to be the word that was 411; its text changes, so
its shaped paragraph changes, so the box it renders changes, so Yoga re-measures
it. One row per paragraph is created and one destroyed, because each row has one
more or one fewer key at its edges. Nothing about those paragraphs changed and the
whole tail of the note was re-measured and re-laid-out.

That is the 72 ms, and no amount of matching blocks would have removed it: a block
handed back unbuilt would still hold words whose indices are wrong.

### What could not be fixed here, and is not

`WidgetRenderer.render` walks **every** element every frame and asks every
`Paints` node for a fresh `Box`; the retained render tree then lays out whatever
box tree it is handed. Both are O(document) by construction, and the style cache
and Yoga's own dirty-tracking make the per-node constant small rather than zero.
So the style and layout columns cannot be removed by matching anything — only by
not building the off-screen blocks at all, which the entry rightly says is a
separate decision. This one does not take it.

## Decision

**Four changes, none of them API.** `MarkdownView` is what it was; an application
threads no previous document through anything.

### 1. Entries belong to a block, not to the document

`WordGeometry` stores its entries per block and lays them end to end afterwards,
and a `Word` is **keyed on the entry it reports to** rather than on a number.

An entry is the identity a word is reconciled on, so where it is stored decides
what a keystroke costs. Held per block, a paragraph keeps its own entries however
many words appeared above it: the same objects, in the same order, holding the
same text — so the element tree matches each word to the node that reported its
rectangle, nothing is re-shaped and Yoga sees the same boxes.

The flat view a `Caret` indexes into is rebuilt only when a block is added,
dropped or resized, and costs a reference copy per word. The block number each
entry carries — what a triple-click takes — is written there rather than at
registration, because it is a fact about the document and not about the block.

### 2. `BlockMemo`, positional, over the top-level blocks

One per mounted document, beside the geometry in `SelectableDocument`'s state —
the only thing in either content view that outlives a build. The `i`th entry
answers for the `i`th block and nothing else. It hands back the widget when:

- the source is `equals` to the one it was built from — a record tree, so a deep
  comparison of the text, which is two orders of magnitude cheaper than building
  the widgets again;
- the fold is standing where it stood then;
- the geometry still holds the blocks it was built into.

A block that moved is built again. That is the right answer rather than a missed
optimisation: its words report into a different block's entries.

### 3. A mark is a block boundary, and one number

`WordMinter.Mark` is *how many blocks have been opened* and nothing else. Between
two blocks the words behind the minter are about to be forgotten and the next word
opens a block, so that is the whole of what distinguishes one boundary from
another. The first version of this carried the word count as well, and a paragraph
that gained a word moved the mark of the paragraph after it — which rebuilt the
rest of the note for a number nobody reads again. The test caught it by counting.

The fold's mark wraps the minter's with what only the fold knows: how many task
boxes it has numbered, so a skipped block does not reset a task ordinal, and which
handlers the view has, because a link with no handler is drawn inert and is a
different widget.

### 4. The two things a block is not a function of

- **The handlers.** An application that writes `onLink(this::open)` inside its own
  `build` hands the view a new object every frame, and a button built three
  keystrokes ago would keep calling the first one it saw. So the buttons press
  through `MarkdownWiring` — one object for the life of the view, given the
  current handlers at the start of every build. Their *presence* is in the mark;
  their identity is not.
- **A missing image.** `ImageSource` answers null while it loads and the picture
  arrives on a later frame (ADR-0300). A block that drew alt text says so, and the
  memo does not keep it — so it is asked again every frame until the image lands.

## Alternatives considered

**`MarkdownView.of(next, previous)` and `Block.sourceRange()`**, which is what the
entry proposed. Rejected, and the entry expected it to be: it makes every
application hold the document it last rendered and thread it through its own
state, to answer *less* than the memo does. A diff of two documents says which
blocks are equal; it does not say whether the fold was standing in the same place,
which is the question that actually decides whether a widget can be handed back.
`sourceRange()` is the same story — `equals` on a record tree is the same answer at
the same cost, and does not put an offset on a model that is otherwise about text.

**Compare widgets with `equals` rather than identity.** ADR-0315 rejected it for
being quadratic and the reason has not changed.

**Memoize nested blocks too** — the items of a list, the paragraphs inside a
quotation. The marks would have to nest and the accounting gets much harder, for a
document whose top-level blocks are already paragraph-sized. The top level is
where the blocks are.

**Lazily build only the blocks inside the viewport.** The only thing that fixes
500 kB, and deliberately not built here: the entry says it is a separate decision
and it is right. See the consequences.

## Consequences

**The measurement**, same machine, same benchmark, md4c control at 1.09 ms and
9.81 ms against the 1.06 and 8.35 above:

| note | build | style | layout | raster | frame |
|---|---|---|---|---|---|
| 2 kB | 0.19 / 0.42 | 0.48 / 12.00 | 0.49 / 0.93 | 2.88 / 4.62 | 4.04 / 17.96 |
| 50 kB | **2.05** / 7.43 | 6.68 / 21.57 | 5.10 / 9.45 | 3.01 / 5.87 | **16.83** / 44.32 |
| 500 kB | **16.09** / 24.16 | 109.67 / 146.74 | 62.19 / 77.09 | 5.42 / 6.82 | 193.37 / 254.82 |
| 50 kB, typing a space | **1.41** / 6.28 | **7.17** / 16.95 | **5.93** / 9.23 | 2.93 / 5.66 | **17.44** / 38.13 |

- **The build is the parse now.** 2.05 ms at 50 kB of which md4c is 1.09; 16.09 ms
  at 500 kB of which md4c is 9.81. The view's own share of a keystroke fell from
  7.7 ms to 1.0 at 50 kB and from 77 ms to 6 at 500 kB.
- **A space costs what a letter costs.** Layout 72.48 → 5.93, style 18.30 → 7.17,
  the whole frame 100.03 → 17.44. This is the entry's "the worst is layout",
  answered.
- **50 kB is inside the *preview < 100 ms* budget**, mean and worst, for both kinds
  of keystroke. That is what closes G45.
- **500 kB is not, and the reason is now visible rather than mixed in.** Style and
  layout did not move: at that size they swing between roughly 60 and 180 ms from
  run to run on this machine, before and after alike, tracking the md4c control.
  They are the render walk and the Yoga pass over 106,509 elements, and nothing
  about matching blocks touches them. **Lazily building only what is in the
  viewport is the only thing left that would**, and it is its own entry.

**The guard is a count, not a clock.** `BlockReuseTest` mounts a twelve-paragraph
note and asserts that a keystroke builds **one** block and keeps eleven; that a
*space* does the same; that the untouched blocks hold the same `Widget` instance
and the same `Element`; that at most two paragraphs are shaped; and that what a
copy of the whole document produces is still the document. Each of those fails
without one of the four changes above, and each says the same thing on a machine
somebody else is also using — which a millisecond does not (`docs/testing.md` §4).

**What is now expensive to get wrong.** A fold carries state as it walks, and
anything it carries that is not in its mark is a bug that looks like a rendered
document: a task ordinal that resets, a counter that runs backwards. `tasksSeen`
is in the mark for that reason. A fold that grows a second counter and forgets has
no compiler to tell it, and the symptom is a check box that toggles the wrong line
six paragraphs away.

**`html-view` got the plumbing and not the memo.** Its fold takes the minter and
ignores the memo it is handed. The same lever is available to it and is a separate
piece of work; the measurement that justified this one was taken on Markdown.

**`Word` lost its index**, which was a public component of a record in a package
nothing exports. What reads an index now is the geometry, through the flat view it
owns.

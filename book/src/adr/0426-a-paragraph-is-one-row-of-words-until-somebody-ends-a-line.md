# 426. A paragraph is one row of words until somebody ends a line

Date: 2026-09-19

## Status

Accepted. Amends
[ADR-0295](0295-a-document-is-a-value-and-a-paragraph-is-a-row-of-words.md),
whose last consequence — "No hard break inside a paragraph … two trailing spaces
therefore do nothing in the widget renderer" — is no longer true, and closes the
`book/src/TODO.md` entry that carried it. Everything else ADR-0295 decided about
a paragraph stands: it is still a wrapping row of word widgets, and a paragraph
nobody ended a line inside is still exactly the one row it was.

## Context

Both folds mapped both breaks to the same thing:

```java
case LineBreak _ -> out.add(Words.Fragment.SEPARATOR);   // markdown-view
case "br" -> pending.add(Words.Fragment.SEPARATOR);      // html-view
```

`Fragment.SEPARATOR` ends the token and draws nothing, so a hard break came out
as the space a soft break comes out as. The model has distinguished the two
since ADR-0295 — `LineBreak(boolean hard)`, documented as "whether the author
asked for one" — and every other consumer of the model already honours it:
`MarkdownHtml` writes `<br>\n` for a hard break and a bare newline for a soft
one, and `Inlines.text` appends `'\n'` and `' '`. The widget fold was the only
reader that threw the flag away, so a note and the HTML served from the same
`Document` disagreed about a line ending — which is the precise failure
ADR-0295 wrote a single fold over one model to prevent.

The comment left in its place named the trap and not the way out:

> A `spacer` with `flex-grow` would fill the rest of the line, which is the
> trick this deliberately does not play: it would make a hard break look like
> justified text.

That is still right. A wrapping row breaks where the width runs out; the only
way to force a break *inside* one is to push the remaining width away, and the
words before the break then spread across the full measure. A reader cannot tell
that from justification, and justification is the one thing a renderer with no
shaped runs must never appear to be doing.

What the entry did not say is that the trick is only needed if the paragraph has
to stay one row. It does not.

### What the parser actually produces

Asked directly, md4c is narrower than the entry's two edge cases suggested:

| source | inlines |
|:---|:---|
| `one␠␠\ntwo` | `Text, LineBreak[hard], Text` |
| `one\\\ntwo` | the same |
| `one␠␠\n\\\ntwo` | `Text, LineBreak[hard], LineBreak[hard], Text` |
| `\\\none` | `LineBreak[hard], Text` |
| `one␠␠\n` | `Text` — **no break at all** |
| `*a\\\nb*` | the break is *inside* `Emphasis` |
| `\| a<br>b \|` | `RawHtml`, not a `LineBreak` |

So a *trailing* hard break is not something Markdown can write: two spaces
before the end of a paragraph are stripped. It is reachable only through the
model — which is public — and through `html-view`'s `<p>a<br></p>`. And a
**table cell can never hold one**, because a table row is a single line of
source and `<br>` in a cell is raw markup; that is what makes it safe for a cell
to stay one row.

## Decision

### A hard break is a piece, and `Words` cuts the run at it

`Words.Piece` gains a third variant beside `Fragment` and `Node`:

```java
public sealed interface Piece permits Fragment, Node, Break {}
```

`Break` draws nothing and carries no marks — what it *is* is a boundary — and
`Words.lines(pieces)` returns one list of token widgets per line. The two folds
disagree about what a paragraph is and agree about what a line of mixed faces
has to become, which is the split `Words` has always been: it says where the
lines are and nothing about their shape.

`markdown-view` sends a soft break to `Fragment.SEPARATOR` and a hard one to
`Break.HARD`; `html-view` sends `<br>` to `Break.HARD`. A run with no break in
it is minted by exactly the call it was minted by before — same words, same
order, no boundary — because a fold that *reordered* the minting would move
every selection rectangle in the document. `tokens` keeps its old behaviour for
a `Break` it is handed directly (it ends the token and no more), which is what a
box that is one line by construction wants: a table cell asks for tokens.

### One line is still one `Row`; more than one is a `Column` of them

```
no break   ->  Row  .md-prose .md-line  [words…]
a break    ->  Column .md-prose .md-lines
                 Row .md-line [words…]
                 Row .md-line [words…]
```

The no-break case is the old tree with one class added. That invariant is not
an optimisation, it is the test: a golden image that moves for a document
nobody wrote a break in means this change is wrong.

### The CSS split, which is the decision this ADR exists for

`.md-prose` styled a *row*. A column inheriting it would have taken three
row-shaped declarations onto the other axis, and one of them is not a near miss
but an inversion: `gap: 0.25em` on the row is the space **between two words**,
and on the column it is the space **between two lines**. A paragraph with a
break would have had a different word spacing and a different leading from the
paragraph above it.

So the declarations went where the shape is, and the class that names the
*block* kept nothing:

- **`.md-line`** — `flex-wrap: wrap`, `align-items: baseline`, `gap: 0.25em`.
  The three declarations that were on `.md-prose`, unchanged, on the box that
  lays words out: the paragraph's own row when it is one line, each row of the
  column when it is not.
- **`.md-lines`** — `flex-direction: column`, `gap: 0.25em`,
  `align-items: stretch`. The gap is deliberately the *same number*: 0.25em is
  what a wrapping row already puts between two lines the width ended, so a line
  the author ended sits at the same leading. A value of its own here would give
  one paragraph two leadings and let a reader see which of its breaks were
  typed. `stretch` gives every line the paragraph's full width, so a line after
  a break wraps where the line before it did.
- **`.md-prose`** — **no declarations at all**, and a comment saying why. It is
  the hook, the way `.md-word` has been one since ADR-0295.

`.md-prose` is on the paragraph's box in **both** shapes, and that is what makes
the split safe rather than merely tidy. Three things depend on there being
exactly one of it per paragraph:

- **Typography is inherited.** `md-heading md-h2` lands beside it, on the outer
  box, so a heading's size reaches the words of every one of its lines. Put the
  paragraph class on each line instead and a heading's size would have to be
  repeated per line — or, worse, be nearer the word than the block, which is the
  mistake `markdown.css` already records about setting the size on words.
- **A fill is painted once.** An application that gives `.md-prose` a
  background, a padding or a border expects one box round the paragraph. Had the
  column been the unnamed one and the lines carried `md-prose`, that rule would
  have drawn a panel per line.
- **The flow is identical.** One box per paragraph, no margin on it either way,
  so a paragraph with a break and one without sit in the same place in
  `.markdown`'s 12px column. This is the requirement the whole split serves.

`html.css` takes the same split for the same reasons: `.html-line`,
`.html-lines`, and `.html-prose` as the hook. It is also where the choice is
*visible* in a test — `HtmlViewTest` counts `html-prose` elements to assert "one
paragraph, not three blocks", and that assertion keeps meaning what it says.

The price is stated plainly: **an application's stylesheet that set a geometric
property on `.md-prose` must now name `.md-line`.** `gap: 0` to tighten a
paragraph is the realistic case. The classes are the published contract, so this
is a breaking change to it, taken now while the alternative is a class that
means two different things depending on whether an author pressed space twice.

### A break inside a link does not break the line

A link is one `button.link` — one Tab stop, one hover, one press (ADR-0293,
ADR-0300). A hard break inside its text would have to become two buttons for one
destination, and a reader tabbing through a document would meet the same link
twice. That is a worse lie than a line that did not end where it was typed, so
the break becomes a **space in the label**, in what is drawn and in what is
copied. `Inlines.text` still answers `'\n'` there and is still right to: that
newline is what the `<br>` in the HTML is made of.

A break inside emphasis, strong, strikethrough or an underline *does* split,
with both halves keeping their marks, because those are faces on words rather
than one widget. A link with no destination folds to words and therefore splits
too — there is no button to keep whole.

### An empty line survives, and it costs a word

Two hard breaks in a row are a blank line an author wrote, and a browser draws
one for `one<br><br>two`. A row holding no words measures zero high, so the
blank line would have collapsed and the bug would have survived in miniature.
The empty line therefore gets **one space**, which is the rule a code fence's
blank line already follows in this same fold, for the same reason and with the
same comment.

That space is a minted word, so it is in the copy: `one\n \ntwo`. Every line
after the first also opens a block, which is what puts the newline in a copied
selection at all (ADR-0301) — a hard break that pasted as a space would be
saying the author's line ending was the width of the pane, which is exactly what
a *soft* break means. The cost is that a triple-click takes one line rather than
the whole paragraph. For the documents hard breaks exist for — an address, a
stanza, a signature block — taking the line is the better answer anyway.

## Consequences

- **`book/src/TODO.md`'s entry is closed**, and the fourth bullet of
  `MarkdownWidgets`'s "what this cannot do" list is gone rather than reworded.
  `html-view` got the same fix in the same commit; `MarkdownHtml` was already
  right and was not touched.
- **No golden in `:html` moved.** Both Markdown goldens, both HTML goldens and
  the selection golden are unchanged, which is the evidence for the no-break
  invariant: the documents they draw contain soft breaks and no hard ones.
- **`gallery-markdown` in `:example` moves, and is not re-blessed here.** The
  showcase's sample document says "Two spaces at the end of a line␠␠/ are a hard
  break, which the HTML writer emits as `<br>`" — it has always been a document
  demonstrating the thing that did not work. The preview now breaks the line,
  everything below it shifts down by one, and the pane's caption re-rasterises
  because the paragraph's longest line got shorter and the two panes share the
  width by content. It is the fix, in a picture; whoever takes the showcase's
  goldens next takes it deliberately. Every other test in `:example` passes.
- **A broken paragraph costs one box per line** on top of ADR-0295's word count.
  A note of ordinary prose gains nothing, because nothing about it changed.
- **A table cell is still one row.** It cannot hold a hard break from md4c, and a
  model built by hand that holds one gets the old degradation — the break ends
  the token. Two classes could have been split out of `.md-cell` to make a cell
  break too; a box that is provably one line does not need them.

## Alternatives considered

- **A `spacer` with `flex-grow` in the row.** The obvious trick, named and
  refused by the comment this replaces: it makes the line before the break look
  justified, and a renderer with no shaped runs must not appear to justify.
- **A zero-width "line break" widget.** It would need the layout engine to
  understand it, which means `:core` learning about inline flow — the rich-text
  engine ADR-0295 declined to write, for a paragraph that breaks in two.
- **The column carries no class and the lines carry `md-prose`.** The smallest
  diff, and it puts the paragraph's identity on N boxes: an application's
  background is painted per line, a heading's size is set per line, and
  `html-view`'s "one paragraph, not three blocks" assertions start counting
  lines.
- **A leading of its own for `.md-lines`.** A knob nobody asked for, and the
  first thing it buys is a paragraph whose typed breaks are visibly a different
  distance apart from its wrapped ones.
- **Two buttons for a link with a break in it.** One destination, two Tab stops,
  two hovers; and the label a screen reader is given is cut in half.
- **Dropping an empty line.** It renders `one<br><br>two` as `one<br>two`, which
  is the same class of silent loss as the break doing nothing, only rarer and
  therefore harder to notice.

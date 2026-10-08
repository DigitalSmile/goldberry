# ADR-0586: A paragraph is joined from styled runs, and wraps as one

- **Status:** Accepted
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-021)

## Context

A `text` painted its whole content in one computed style. Part of a paragraph
could not take another colour or weight and stay one wrapped paragraph: a
keyword in gold and bold in the middle of an ability's sentence was out of
reach. A `row` of `text`s does not do it, because each `text` wraps inside its
own box and a sentence split across three of them wraps as three columns.

`text.Paragraph` already shaped one text in several pieces. Font fallback put
emoji in the emoji face and the rest in the text face, concatenated the two
shapings into one run in the base font's design units, and wrapped the result
as one paragraph. Drawing went piece by piece, each in the face that shaped it.

## Decision

**`Paragraph.join(List<Paragraph>)`** puts paragraphs end to end as one. Each
piece keeps its own shaping, emoji segments included, so joining shapes
nothing. Its segments are rebased onto the joined text and glyph run, and
`concatenate` rescales a piece at another size by the ratio of the sizes as well
as of the units per em. The line breaker runs over the whole text. A line is
as tall as the tallest font on it: the largest ascent plus the largest share
below the baseline, read once per piece at the join. `ascentOf` and `heightOf`
answer per line, and a layout's height is their sum. Measurements are in the
first piece's font. A list of one piece answers that piece, and spans that are
themselves joined are flattened.

**Colour and rules are not part of the paragraph.** `text.SpanPaint(start, end,
argb, decorations)` is a range of the text drawn in its own colour and rules.
`Box.Text` carries a list of them beside its colour and flow, and `Box.fade`
fades them. `Paragraph.paint(…, List<SpanPaint>)` draws a line piece by piece,
split wherever a span or a joined piece starts or ends. Each piece's rules sit
where its own font puts them. A run that changes colour on hover is a repaint,
not a shaping.

**Single-style text takes the old path, unchanged.** A paragraph whose pieces
all share one font is uniform: its height is line count times line height, and
with no spans it is drawn by the same code as before. Every existing golden is
byte for byte the same.

**`ParagraphCache.join`** keeps a joined paragraph per list of span instances,
compared by identity, in the same recency order as the shapings. The render tree
keeps a measure callback for as long as its paragraph is the same instance, so a
settled frame joins nothing new. `Paints.Context.join` reaches it, with a default
that joins uncached for a context with no cache.

**`widgets.text.RichText(List<Run> runs, Attributes)`**, markup `rich-text`, and
**`widgets.text.Run(String text, Attributes)`**, markup `run`. `Run` has the
`Run(String, Set<String> classes)` constructor the issue asks for. The runs are
the paragraph's children, so the cascade styles each one as a child node
(`rich-text > run.keyword { … }`), and inheritance, transitions and `:root`
tokens work as for any node. Each run renders a box with its words shaped in its
own font. The `rich-text` reads those boxes without drawing them, joins their
paragraphs through the context, and turns every run whose colour or rules differ
from its own into a `SpanPaint`. Runs that look like the paragraph add nothing,
so an unstyled `rich-text` draws what a `text` draws. Paragraph-level properties
(`white-space`, `text-align`, `overflow-wrap`, padding) are the `rich-text`'s.

`Run` holds `Attributes` rather than a bare class set, so it is chainable like
every other catalogue widget and `run id="…"` does not drop its id.

**`Role.TEXT`**, and the accessible name is the runs' text end to end. No role
in the closed set fitted. `GROUP` is a boundary with content in it, and a styled
paragraph is the content.

**The guide section is parked, and `BookTest` reads it.** `BookTest` wants a
`## `name`` heading and a catalogue row for every `@Markup` name, and the book
is not changed between releases. The section for `rich-text` and `run` is
written in `docs/snapshot/components-rich-text.md`. `BookTest`'s heading and
catalogue rules now also read the headings and catalogue rows of the parked
`docs/snapshot/*.md` files, and the catalogue's links are read as if they were
in `components/index.md`. Every other rule still reads only the book. A name
documented both in the book and in a parked file counts twice and fails, which
is what a release that forgot to empty the folder looks like.

## Alternatives considered

- **A row of word widgets, as `markdown-view` builds.** A wrapping row breaks
  only between widgets, not where the line breaker would. The paragraph has no
  single measure, so no `text-align` and no width to report, and it puts a node
  per word in the tree. The issue's own repro, a row of three `text`s, is the
  coarse form of the same thing.
- **Spans with colour inside the paragraph,** `Span(text, font, argb,
  decoration)` keyed in the cache. A colour transition on a run would then
  re-shape the paragraph every frame, and the cache would hold one paragraph per
  colour.
- **Resolving each run's style by hand** from the `rich-text`'s render, with a
  synthetic style element per run. That is a second cascade path beside the one
  every node takes, without transitions, `@starting-style` or the style cache.
- **Adding headings for `rich-text` and `run` to the book now.** The book is not
  changed between releases. Exempting the names from `BookTest` instead would
  let the section be forgotten at release; reading the parked file keeps the
  rule and checks the text that will go in.

## Consequences

- `Box.Text` has a fourth component, `spans`. The three- and two-argument
  constructors stay, and `Box.style` keeps the spans while it replaces the
  colour and the flow.
- `Paragraph` gains `join`, `ascentOf`, `heightOf` and a `paint` overload taking
  spans. Its editors and fields measure single-font paragraphs as before.
- A kern across a seam between two runs is lost, as it is between a word and an
  emoji. A ligature cannot span two runs.
- Text in a `rich-text` cannot be selected, as text in a `text` cannot.
- `ParagraphJoinTest`, `RichTextTest` and `RichTextGoldenTest`
  (`rich-text-dark`, `rich-text-light`) hold the behaviour. The issue's repro is
  a single box at 300 px whose first line runs past the keyword.

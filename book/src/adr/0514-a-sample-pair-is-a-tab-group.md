# ADR-0514: A sample pair is a tab group

- **Status:** Accepted
- **Date:** 2026-10-01
- **Relates to:** [ADR-0511](0511-the-book-is-a-guide-first-and-the-log-is-its-last-part.md),
  [ADR-0512](0512-the-log-is-read-on-github-and-the-book-is-the-guide.md),
  [ADR-0059](0059-a-control-is-a-record-a-node-and-a-rule.md), `docs/book.md`

## Context

Every widget's section shows a `kdl` sample and then the Java that builds the
same tree (ADR-0511): the same widget said twice, as ADR-0059 promises it can
be. Written one after the other, the two samples double the length of every
section, and a reader who writes markup scrolls past Java in every one of them,
or the other way round. The one place the guide tried side by side,
`gb-pair` on the concept page, did not fit the two on a narrow page.

mdBook 0.5 has no tabs. A preprocessor would be a second tool to install in
the Pages workflow and on every machine that previews the book, for a thing
the page can do for itself.

## Decision

- **A run of adjacent samples that starts with `kdl` and goes on in another
  language is wrapped in `<div class="gb-tabs">`**, in the catalogue and on the
  concept page. Blank lines separate the div from the fences, so mdBook
  renders the fences as it always did, as sibling `<pre>` elements of the div.
- **`goldberry.js` builds the tabs** at load from the fences' languages: a
  `role="tablist"` of buttons labelled KDL, Java, CSS, and the one sample
  that is selected. Picking a language selects it in every group on the page
  and is remembered in `localStorage` for the whole book, so a reader who
  prefers Java reads Java all the way down. Arrow keys move between tabs.
  Without the script, and until it runs, every sample is shown in turn.
- **`BookTest` holds it**: a tab group holds two or more samples in different
  languages and nothing else; in the catalogue and on the concept page, a
  `kdl` fence followed only by blank lines and a `java` fence is inside a
  group; `gb-pair` is gone; the theme's script and stylesheet know the class.
- **The developer guide is not held to it.** A chapter there may put a
  document beside the class that loads it, which is two things rather than
  one said twice, and such a pair stays as prose shows it.

## Alternatives considered

- **`mdbook-tabs` or another preprocessor.** A Rust binary to download in the
  Pages workflow and keep at a version beside mdBook's, and a syntax no other
  Markdown renderer knows, so the chapter on GitHub would show its markers.
  The div is plain HTML that GitHub ignores and mdBook passes through.
- **Side by side.** `gb-pair` tried it. Two samples of forty columns each do
  not fit beside each other in the content column, and the one that lost
  wrapped.
- **Only the markup, with the Java in a reference.** The Java is the half
  that shows the record's constructor, and the guide is for a Java developer.

## Consequences

- Every widget's section is half as long on screen, and a reader sees the
  form they write.
- A reader with scripts off sees both samples, one after the other, as before.
- The samples are still fenced blocks: `BookMarkupTest` inflates every `kdl`
  one and `BookTest` checks every `java` one, and search indexes both.
- `tools/book/visuals.py` wraps a new pair and places a new picture, and is
  safe to run twice. A chapter written by hand follows the same shape.

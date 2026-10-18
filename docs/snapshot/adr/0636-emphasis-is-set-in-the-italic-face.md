# ADR-0636: Emphasis is set in the italic face

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0295](0295-a-document-is-a-value-and-a-paragraph-is-a-row-of-words.md), [ADR-0298](0298-html-is-a-document-and-not-an-engine.md)

## Context

`markdown.css` and `html.css` set emphasis as a faux oblique,
`transform: skewX(-10deg)` on each word. When that rule was written the toolkit
bundled two upright faces and nothing else. ADR-0323 then added an italic at
both weights, so the matrix closed, and `font-style: italic` has resolved to a
drawn face ever since. Both stylesheets said in their comments that the skew
had become a compromise nothing forced. They kept it only because swapping it
would move every Markdown and HTML golden, and they asked for that change to be
made on purpose.

## Decision

**`.md-em`, and `.html-em`, `.html-i`, `.html-cite`, `.html-dfn`, `.html-var`
and `.html-address`, declare `font-style: italic` and nothing else.** A word
inside both emphasis and strong is semibold italic, the second face ADR-0323
shipped for that reason.

The face has its own advances, which the transform did not. An emphasised word
now takes the width its glyphs need, and the rest of the line moves with it.

## Consequences

- Five goldens in `:html` moved (`markdown-light`, `markdown-dark`,
  `markdown-selection`, `html-light`, `html-dark`), and the showcase's
  gallery goldens and book pictures that show a document moved with them.
  Every change is confined to the lines that hold an emphasised word.
- `MarkdownStylesTest` and `HtmlStylesTest` hold each italic class to that one
  declaration, so a skew written back in fails a test rather than only a
  picture.
- The `TODO.md` entry *Emphasis is a faux oblique* moves to *Answered*.
  Strikethrough is still a muted colour in both views, although
  `text-decoration` exists. That is the same kind of picture decision, not
  taken here.

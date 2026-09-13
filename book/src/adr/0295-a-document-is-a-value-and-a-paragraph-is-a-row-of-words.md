# 295. A document is a value, and a paragraph is a row of words

Date: 2026-09-13

## Status

Accepted. The Java half of `docs/gaps.md` G8;
[ADR-0294](0294-a-parser-crosses-the-boundary-once.md) is the native half.
Defers `html-view`, which G8 does not ask for and `book/src/TODO.md` says what
waits on — and which
[ADR-0298](0298-html-is-a-document-and-not-an-engine.md) later built, with no
litehtml under it either, by restating every decision below about a *document*
rather than about Markdown.

## Context

ADR-0294 gets md4c's events into Java. What they should become is a separate
question with three parts, and `docs/content-widgets.md` §1 had answered the
third one already — *"`markdown-view` = md4c parsing to HTML, rendered by the
same litehtml pipeline"* — at a time when litehtml was assumed to be arriving
first.

It is not arriving first. `book/src/TODO.md` says why: litehtml's native
`document_container` draws through `libgoldberry`'s exported C symbols, and that
surface has no gradients, no rounded geometry and — until `canvas` needed it — no
nested state stack. *"The first commit of `goldberry-html` is a widening of the
toolkit's own native surface"*, which is real work, is shared with
`goldberry-vector` and `goldberry-terminal`, and renders no Markdown.

Meanwhile the two things G8 exists for — a note's preview and its HTML — need
neither an engine nor a wider paint surface.

## Decision

### One module, two packages

`goldberry-html` stays one artifact, one `THIRD-PARTY-NOTICES`, one natives
story. Inside it, Markdown gets a package of its own:

```
io.github.digitalsmile.goldberry.markdown          Markdown, MarkdownSyntax
io.github.digitalsmile.goldberry.markdown.model    the document, as records
io.github.digitalsmile.goldberry.markdown.html     MarkdownHtml
io.github.digitalsmile.goldberry.markdown.view     markdown-view
```

The `…html` package that will hold litehtml's `html-view` is not written yet.
Four reasons for the split, none of them taste:

- **Two upstreams with two licences and two lifetimes** — md4c under MIT,
  litehtml under BSD-3 — which is the split ADR-0172 already made inside
  `:natives`: by what a thing is, not by which library it came from.
- **The dependency runs one way.** Markdown *produces* HTML and never reads it.
- **The Markdown half needs no window**, no rasterizer and no native paint
  surface, so it is testable — and shippable — while the other half is blocked.
- **It ships something brd can use before litehtml exists**, which is the whole
  point of doing this now.

### A document is a value

`Markdown.parse` returns a `Document`: a sealed hierarchy of records, `Block` and
`Inline`, pattern-matched with a `switch`. Not a builder, not a visitor, not a
stream of events — the events are ADR-0294's and they stop at one
package-private class, `MarkdownParser`, which is this module's only mention of
md4c.

A value, because a preview is not the only thing an application does with a note.
A word count, an outline, a table of contents, the first paragraph as a summary,
and the HTML a server hands out are all walks of the same tree, and a widget that
hid the parse would make every one of them a second parse.

**Sealed**, so that a node added later is a compile error in every renderer that
has not handled it. "Every renderer that forgot the new node" is otherwise a list
nobody has.

The model is the toolkit's vocabulary rather than md4c's: `CellAlignment.START`
rather than `MD_ALIGN_LEFT`, entities resolved on the way in, a tight list item's
inlines wrapped in a `Paragraph` so that every item has one shape. `WikiLink` is
a node of its own rather than a `Link` with an odd href, because a wiki target
names something in a collection the application owns — which is the reason a
note-taking application turns the extension on.

### HTML is a fold over the model, not md4c's own renderer

md4c ships `md4c-html.c` and it is not used. An application that shows a note
*and* serves it needs both halves to agree — the same dialect, the same entity
resolution, the same decision about what a soft break means — and two renderers,
one in C and one over the model, is how they stop agreeing. `MarkdownHtml` is a
`switch` over the same records `markdown-view` renders, and the compiler is what
keeps the pair honest.

### A paragraph is a wrapping row of words

`markdown-view` builds `column`, `row` and `text` from the catalog, with classes
that `markdown.css` styles. No engine, no second text stack, nothing a theme
cannot restyle.

The load-bearing detail is inline marks. **The text stack shapes one font per
`Paragraph`**, so a line holding both regular and semibold glyphs cannot be one
shaped run. The alternatives were a rich-text layout engine in `:core` — which is
what litehtml would bring, and is `html-view`'s job — or losing `**bold**`
altogether. So an inline run is split at whitespace, each word is a `text` widget
carrying the classes of the marks it is inside, and the row wraps: the line
breaking goes back to CSS, which already had it.

A word is a **token** rather than a fragment, and the golden image is what said
so. `*emphasis*, and` is an emphasised fragment followed by `, and`, so splitting
per fragment put a space in front of the comma — "emphasis , and". A token is
everything between two spaces however many styles it spans, drawn as a row with
no gap inside it.

Three things follow from having no engine, and all three are in the stylesheet
and in `TODO.md` rather than hidden:

- **Emphasis is a faux oblique.** The system ships two upright faces (§6.1), so
  there is no italic to set `*a*` in; `transform: skewX(-10deg)` leans the word
  without moving it. An application with an italic face replaces one rule.
- **Strikethrough is a colour**, because §10's CSS subset has no
  `text-decoration`.
- **A quotation's bar and a task's check box are widgets**, because the subset's
  `border` is uniform — there is no `border-left` — and U+2610 is in neither
  bundled face, so a typed check box renders as the missing-glyph box.

### Nothing is clickable, and nothing fetches

A word does not hear a pointer, so a link is the accent colour and not a
destination; an image is its alt text, because there is no `img` widget and
fetching anything is the application's (ADR-0190). Both are stated in the widget's
javadoc, in the stylesheet, and in `TODO.md`, rather than faked with a rule that
suggests otherwise.

## Consequences

- **G8's two consumers are answered.** `MarkdownHtml.of(source)` is
  `GET /docs/{id}/body.html`; `MarkdownView.of(document)` is the preview. Neither
  waits on litehtml.
- **A rendered document is ordinary widgets**, so it inherits the cascade, the
  theme, the density, the text scale and the golden-image harness for free — and
  an application restyles a document with CSS rather than with a renderer
  subclass.
- **Widget count is the price.** A 500-word note is roughly 500 `text` widgets
  plus their rows. That is fine for a preview pane and is not fine for a book,
  and the number is here rather than discovered later.
- **`markdown-view` is the first widget outside `:widgets`**, which exercises
  ADR-0131's promise: `panels.kdl` names the node, this module declares a
  `WidgetCatalog`, and the showcase never mentions either.
- **An application must add `MarkdownStyles.stylesheet()`** beside
  `Controls.stylesheets(theme)`. `:widgets` does not know Markdown exists, so it
  cannot add them, and a document with no rules renders as unstyled words.
- **`html-view` is still a gap**, and G8 stays open in `docs/gaps.md` with the
  Markdown half struck through. What it waits on is unchanged: rounded geometry
  and gradients on the export list, and a native `document_container`.
- **No hard break inside a paragraph.** A wrapping row has no widget meaning
  "start a new line here", and a `spacer` with `flex-grow` — the obvious trick —
  would make the line before it look justified. Two trailing spaces therefore do
  nothing in the widget renderer, and do the right thing in the HTML one.

## Alternatives considered

- **Wait for litehtml.** The honest reading of `content-widgets.md` §1, and it
  means brd serves raw Markdown for another milestone while the work that
  unblocks it is a native paint surface shared with two modules that do not exist
  yet.
- **One package for HTML and Markdown.** Fewer names, and it makes the cheap half
  hostage to the expensive one: everything in the module would import a package
  whose other half needs a rasterizer and a C++ container.
- **A `String` of HTML instead of a model.** md4c to HTML in C, and a widget that
  renders HTML — which is litehtml again, or a second parser for the HTML this
  module just produced.
- **One `text` per paragraph, marks dropped.** One widget instead of five hundred,
  and `**bold**` renders as bold nothing. The preview's entire job is to show the
  marks.
- **Per-word widgets only where a run is styled**, keeping unstyled prose as one
  `text`. It halves the widget count for plain paragraphs and makes the line
  breaking inconsistent: a wrapped plain run breaks anywhere, a styled one breaks
  at the run boundary. One rule is worth more than the nodes.

# ADR-0511: The book is a guide first, and the log is its last part

- **Status:** Accepted
- **Date:** 2026-10-01
- **Relates to:** [ADR-0509](0509-goldberry-dev-is-the-landing-page-and-the-book-is-its-docs.md),
  [ADR-0001](0001-record-architecture-decisions.md), `docs/book.md`, `docs/site.md`

## Context

ADR-0509 put this book at `goldberry.dev/docs/` and left what it should hold
to a later step. What it held was six loose chapters, written for the next
maintainer, and five hundred decision records. A newcomer landing on `/docs/`
read a page saying the project was pre-M0 and nothing rendered, which stopped
being true a year of commits ago, and had no page that showed a widget.

The landing page promises a guide: it links *Read about widgets*, *Read about
styling* and *The design system*, and each pointed at the book's front page
because there was nowhere else to point. `docs/site.md` listed what the step
had to decide: the structure, the search index at 19.5 MB with the log in it,
a theme to match the page, and whether samples should compile.

Three facts shaped the answer.

- **The repository already knows every widget.** `@Markup("name")` on a record
  is the one place a node name is declared, and the catalogue is generated from
  it. A guide that lists widgets by hand drifts from that set the first time a
  widget is added, and the README's own count already had: it says 72 widgets
  behind 74 names and there are 79.
- **A sample in documentation is read more often than any test**, and a sample
  that no longer inflates is the reader's first failure. `Widgets.inflater()`
  with nothing bound inflates a document the way a preview does, so every
  `kdl` block can be checked for the price of a parse.
- **mdBook 0.5 can keep chapters out of search.** The log is nineteen of the
  index's twenty megabytes, and a search for `button` should find the component
  page and not five hundred records that mention one.

## Decision

The book is a guide in six parts, then the reference, then the log:

| Part | Holds |
|---|---|
| Overview | the concept, the architecture, the limitations |
| Getting started | requirements, installing, the first Java application, the first native application |
| Layout | one chapter per layout widget, with an example each |
| Components | one chapter per family of widgets, every markup name under a heading of its own |
| Performance | what start-up and a frame cost, and how to measure them |
| Developer guide | markup, styling, the design system, text, input, windows, testing, diagnostics, writing a widget, weaving, native image, and contributing |
| Reference | `status.md` and `TODO.md`, unchanged |
| Decision log | the records, unchanged, with the template as the suffix chapter |

The three pages the landing page links by name keep their paths. The guide is
written for a Java developer who has not seen Goldberry; the log keeps its
voice. `docs/book.md` is the style guide, and its rules are what the tests
below check.

**The guide is held by two tests.** `BookTest` in `build-logic` reads the
sources: every chapter the summary lists exists and every page is listed; a
chapter's first heading is its sidebar title; every relative link lands on a
file, and a fragment on a heading; every `@Markup` name in the shipped modules
has exactly one heading that is the name in code marks, in Layout or
Components, and the catalogue page links each; `book.toml` loads the theme and
keeps `adr/` out of search. `BookMarkupTest` in `:example`, the one module with
every catalogue on its path, inflates every fenced `kdl` block in the guide
against `Widgets.inflater()`. A fragment is fenced `kdl,ignore`.

**The theme is the landing page's.** `theme/goldberry.css` recolours mdBook's
`light` and `navy` themes with the tokens from `site/assets/style.css` and
hides the other three. The leaf mark is the favicon. The guide may use seven
HTML blocks the stylesheet defines and no others.

**The log leaves search, and nothing else.** `[output.html.search.chapter]`
disables indexing under `adr/`. The records stay in the book, in the sidebar
folded, and on the print page.

## Alternatives considered

- **A second mdBook for the log, at `/docs/adr/`.** Solves search, and costs
  every record's link from the guide a second site root, a second theme and a
  second build in `pages.yml`. Per-chapter search settings solve the same
  problem inside one book.
- **Generating the Components part from the sources.** The attributes table
  could be read off each widget's `inflate`. A generated chapter has no prose,
  and the prose is the point. The test checks coverage instead, which is the
  half a generator would have guaranteed.
- **Compiling the Java samples.** A sample is a fragment. Compiling one means a
  harness that supplies its imports and its surrounding class, and the names in
  the fragments are checked by reading against the records that declare them.
  Not built; `docs/book.md` says so.
- **Keeping the old chapters where they were and adding a guide beside them.**
  `applications.md`, `weaving.md` and `native.md` are the best pages the book
  had and belong in the Developer guide. They move in the summary and keep
  their paths.

## Consequences

- A newcomer at `/docs/` reads a guide. The log is one part further down.
- Adding a widget now fails `BookTest` until it has a chapter heading and a
  line in the catalogue, which is the drift this record exists to prevent. The
  same test fails on a renamed heading that a link still names.
- Every `kdl` sample is a test. A sample that needs a bound name can still be
  written, because nothing is bound. A sample that cannot inflate without a
  value only Java supplies is fenced `kdl,ignore` and says so.
- `build-logic:test` now reads 50 Markdown files and four modules' sources.
  It took under a second.
- The search index is 2.7 MB instead of 19.5 MB, and a search finds the guide.
- The attribute tables are hand-written against each widget's `inflate` and
  will drift the way prose does. The inflater accepts a property it does not
  read, so `BookMarkupTest` cannot catch a misspelt attribute. That is a gap
  in the inflater as much as in the test, and it is recorded in `TODO.md`.
- The guide's house style differs from the log's. A record keeps its
  parentheses and dashes. A chapter cites a record in parentheses and writes
  the rest in plain sentences.

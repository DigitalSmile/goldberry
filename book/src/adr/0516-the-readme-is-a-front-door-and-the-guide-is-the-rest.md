# ADR-0516: The README is a front door, and the guide is the rest

- **Status:** Accepted
- **Date:** 2026-10-01
- **Relates to:** [ADR-0509](0509-goldberry-dev-is-the-landing-page-and-the-book-is-its-docs.md),
  [ADR-0511](0511-the-book-is-a-guide-first-and-the-log-is-its-last-part.md),
  [ADR-0512](0512-the-log-is-read-on-github-and-the-book-is-the-guide.md),
  `docs/book.md`

## Context

`README.md` had grown to 1,820 lines: a quick start, then a chapter each on
windows, layout, text, editing, icons, images, Markdown, every control, the
render tree, input, painting across threads, the GPU, the showcase and
logging — written before the book existed, as the only prose a reader could
reach. Since ADR-0509 the project has a site, and since ADR-0511 the book is a
guide first: getting started, layout, components, performance, a developer
guide, and the decision log as its last part. Every subject the README's
chapters cover has a page there — windows and threads under the guide and
performance, every control under components, logging under the guide — kept
current by `BookTest` and `BookMarkupTest`, which the README never was.

Two documents saying the same thing drift, and nothing checked the README
beyond trailing whitespace: its widget counts, its list of showcase screens
and its timings were prose, where the book's samples inflate and its pictures
are goldens.

## Decision

The README is the project's front door and nothing more. It carries, in under a
hundred lines:

- the banner, the badges, and one paragraph on what Goldberry is;
- four bullets on why — start-up, declarative widgets, real layout and styling,
  three peer platforms — and the pre-release notice, with the one limitation a
  reader must know before choosing it (no screen-reader support, ADR-0440);
- a quick start: the Gradle coordinates and the three-line window, each
  pointing at the book page that continues it;
- how to build from source, in three commands, pointing at the contributing
  guide for the rest;
- the documentation table and the licence.

Everything else moved nowhere, because it was already somewhere: the book, for
what a user reads; `docs/ARCHITECTURE.md`, for the design; the decision log,
for the reasoning. Where a README chapter and its book page disagreed, the book
page is the one that stays, because it is the one the tests read.

## Alternatives considered

- **Keep the long README and fix its drift.** It would drift again, for the
  same reason: nothing checks it. The book's samples inflate and its pictures
  are goldens; the README's were prose.
- **Move the chapters into the book first, then cut.** Checked chapter by
  chapter, and not needed: each one's subject already has a page, written when
  the book was. What the README had and the book did not was detail of the
  kind the decision log holds, and each such paragraph already cited its ADR.
- **A README that is the book's introduction, included verbatim.** mdBook
  cannot include a file from outside `book/src`, and a README is read on
  GitHub, where the book's admonitions and lede paragraphs do not render.

## Consequences

- A reader who lands on GitHub sees what the project is, how to add it and
  where to read on, on one screen, with no scrolling past a widget catalogue.
- The README has no numbers in it that an ADR could later correct. Start-up is
  "milliseconds", and the measured figures live with their method in ADR-0506
  and the performance chapter.
- `checkMarkdown` still covers it (trailing whitespace, final newline), and
  `pages.yml` still drops it from the site, where the book's introduction is the
  front page instead.
- The git history keeps the long README. Nothing in it is lost; it is simply
  not the README's job any more.

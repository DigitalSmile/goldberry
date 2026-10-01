# ADR-0512: The log is read on GitHub, and the book is the guide

- **Status:** Accepted. Amends [ADR-0511](0511-the-book-is-a-guide-first-and-the-log-is-its-last-part.md)
- **Date:** 2026-10-01
- **Relates to:** [ADR-0509](0509-goldberry-dev-is-the-landing-page-and-the-book-is-its-docs.md),
  [ADR-0001](0001-record-architecture-decisions.md), `docs/book.md`

## Context

ADR-0511 kept the five hundred records in the book as its last part, folded in
the sidebar and out of search. Read on goldberry.dev/docs/, they were still
five hundred chapters a newcomer scrolls past, a print page that is almost all
log, and a sidebar whose last fold is longer than the guide. The records are
written for the next maintainer, who reads them where the code is.

Two other things about the book were wrong for a reader and are settled here
with the same commit: the type was set in `rem` against a root mdBook scales to
ten pixels, so body text was ten pixels tall, and the content column was
capped at 860 pixels on a page that has a sidebar already.

## Decision

- **The decision log is not built into the book.** The records stay in
  `book/src/adr/`, where every link in the repository already points, and are
  read on GitHub. `SUMMARY.md` lists none of them, so mdBook ignores them.
  `adr/README.md`, which GitHub shows for the directory, carries the
  conventions and the list of every record; `DecisionLogTest` holds every
  record to a line in it.
- **A chapter links a record on GitHub**, as
  `https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/<file>`.
  `BookTest` refuses a relative link into `adr/` and checks that a GitHub link
  names a record that exists.
- **The book is full width, and its type is sized in pixels**: 17 px body
  text, headings from 38 px down.
- **The book has the landing page's header.** `theme/goldberry.js` replaces
  mdBook's centred title with the leaf, the name, the six parts of the guide
  and a GitHub link, at load, inside mdBook's own menu bar. mdBook's buttons
  stay at both ends. A script rather than a copied `index.hbs`, so an mdBook
  upgrade changes the template under it rather than beside it.

## Alternatives considered

- **Moving the records to `docs/adr/`.** Cleaner on paper, and a rename of
  five hundred files that two thousand links, `DecisionLogTest`, the README
  and every design document name by path. Nothing is gained that
  not listing them does not already give.
- **A second mdBook for the log.** ADR-0511 rejected it for the cost of a
  second site; it would now be a second site for something GitHub renders.
- **Copying mdBook's `index.hbs` to add the header.** The whole template has
  to be copied and kept in step with the pinned version. A script that finds
  the menu bar by id survives a template change, and degrades to mdBook's own
  title if the id goes.

## Consequences

- The sidebar is the guide. Search indexes the guide alone, with no
  per-chapter exclusion to maintain.
- A record is one click further from a chapter than it was: GitHub rather than
  the same site. The link text still says which record.
- `docs/adr/` no longer exists on the site. The landing page's *Decision log*
  link goes to the directory on GitHub.
- A new record needs a line in `adr/README.md` instead of in `SUMMARY.md`.
- The template line in `SUMMARY.md` and the `[output.html.search.chapter]`
  setting are gone with the part they served.

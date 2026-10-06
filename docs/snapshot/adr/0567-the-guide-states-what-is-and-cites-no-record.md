# ADR-0567: The guide states what is, and cites no record

- **Status:** Accepted
- **Date:** 2026-10-06
- **Relates to:**
  [ADR-0511](0511-the-book-is-a-guide-first-and-the-log-is-its-last-part.md),
  [ADR-0512](0512-the-log-is-read-on-github-and-the-book-is-the-guide.md),
  [ADR-0541](0541-a-doc-comment-says-the-rule-and-links-the-guide-and-never-cites-the-log.md),
  `docs/book.md`, `docs/doc-neutral-2026-10-06.md`

## Context

The guide at goldberry.dev/docs/ was written chapter by chapter as the toolkit
was built, and it read that way. A heading said "The natives jar is a module
now". A paragraph explained that GraalVM had refused some reflection entries
and that an application's build "had to strip them", before saying what the
toolkit does today. A struck-through "No CI job" was followed by "Built" and
the run that proved it. About 1,900 sentences ended in a citation of the
decision log, and every widget's section closed with a *Read more* list of
records. Reviews, sweeps, gap ids, milestones, run numbers and dates were
named in the text.

A reader of the guide is a Java developer with a window to build. They did
not watch the toolkit being built and cannot follow a record number, a gap id
or a date. A sentence that narrates a change tells them something was once
different, which is not a fact about the toolkit they have. A citation at the
end of every sentence breaks the line of the prose and sends them to a document
written for a different reader. Doc comments were already held to the same
rule by ADR-0541: a doc comment says the rule and links the guide, and never
cites the log. The guide was the one document still written in the
maintainer's voice.

## Decision

The guide describes the toolkit as it is. Every chapter `SUMMARY.md` lists,
except the two reference pages `status.md` and `TODO.md`, and the `README.md`:

- states each fact in the present tense, and does not narrate a change, a
  former behaviour, a bug, a failure, a plan or a promise;
- cites no record, lists no *Read more* of records, and does not name the
  decision log outside the contributing chapters, where the log is the subject;
- strikes nothing through;
- names no review, sweep, batch, gap, milestone, run or date, and no working
  document under `docs/`, outside the contributing chapters;
- states a limitation in one sentence, with its reason in words when the
  sentence has one, and invents nothing to fill the place of a dropped citation.

Facts, samples, HTML blocks, tables, headings and links to chapters and public
documents are kept as they were. A heading that was itself narrative is renamed
only when nothing links its anchor.

`BookTest` holds every chapter outside the reference pages to the rule: no
record number or record link, no strikethrough, no gap id, no working document,
and the bare word "ADR" only under `contributing/`. The style guide in
`docs/book.md` withdraws the citation parenthesis and the *Read more* list.

The two reference pages stay as they are. `status.md` is a status table and
`TODO.md` a list of what is not built with the reasons; both are the development
record by design, and a neutral version of either would be a different document.

## Alternatives considered

- **Keep the citations, drop only the narrative.** A citation is the shortest
  form of narrative: it says a decision was made and sends the reader to the
  minutes. Doc comments gave them up for the same reason, and keeping them in
  the guide would have left the guide the one place a reader meets the log by
  accident.
- **Keep the *Read more* lists as a bibliography.** They were lists of record
  titles, which read as a changelog of the widget. A reader who wants the log
  has its index on GitHub, one click from the repository link every page
  carries.
- **Rewrite `status.md` and `TODO.md` too.** Their content is the status of the
  work and the list of what is missing, with the dates and the reasons. Without
  those they are not status or TODO pages. They are listed under *Reference*
  and a reader who opens them gets what the title says.

## Consequences

- The guide reads as documentation of a product rather than as a diary of its
  construction. A reader who has not seen the repository cannot tell from it
  that anything was ever different.
- The reasons behind a limitation are, where a sentence carried them, still in
  the guide in words. Where the reason lived only in the record, the guide now
  states the limitation alone, and the record is found from the log's index.
- Every link from a doc comment or a showcase card into the guide still lands,
  because headings were kept. The one heading renamed, "The natives jar is a
  module now", had one link, in the guide, which was updated.
- A chapter that cites a record, strikes an item through or names a gap fails
  `./gradlew :build-logic:test` with the line quoted.

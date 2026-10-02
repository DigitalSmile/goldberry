# ADR-0518: A doc comment explains the object and links the guide

- **Status:** Accepted. Amends the "How the guide links a record" rule of
  [ADR-0512](0512-the-log-is-read-on-github-and-the-book-is-the-guide.md) for
  Java sources.
- **Date:** 2026-10-01
- **Relates to:** [ADR-0001](0001-record-architecture-decisions.md),
  [ADR-0343](0343-the-published-javadoc-is-linted.md),
  [ADR-0405](0405-check-generates-the-published-javadoc.md),
  [ADR-0497](0497-every-package-says-what-it-is-and-is-null-marked.md),
  [ADR-0512](0512-the-log-is-read-on-github-and-the-book-is-the-guide.md),
  [ADR-0516](0516-the-readme-is-a-front-door-and-the-guide-is-the-rest.md),
  `book/src/contributing/doc-comments.md`, `docs/doc-comments-2026-10-01.md`

## Context

The decision log is written for the maintainer, and the source was written the
same way. On 2026-10-01, 1,556 of the repository's Java files cited a record:
5,176 lines of `(ADR-0481)`, `see the ADR`, `which is what ADR-0063 forbids`.
Many more quoted a working document at itself, `docs/core-widgets.md §2: "…"`,
or carried relative links `../../../../../book/src/adr/…` that resolve from no
javadoc site.

The reader of a doc comment is not, most of the time, a maintainer with the
repository open. The comment is shown as a tooltip in an IDE over a name from
the published jar, or on a javadoc site after a release. That reader has a
browser and nothing else. `ADR-0481` gives them a number with no page behind
it; ADR-0512 keeps the log on GitHub and out of the built book, so there is no
address to give. `§2` of a document under `docs/` is the same: a reference into
a tree they do not have.

The comments also narrate. A type's comment that opens *The ADR describes a
pending reveal "set when the selection changes…" and only the second half was
ever written* is a changelog entry, accurate and useless to someone who wants
to know what a `Tabs` is and how to make one. The user-facing documentation
exists: the guide at `goldberry.dev/docs` has a chapter for every widget, for
styling, input, windows, testing, weaving and the native image, and each
chapter's *Read more* links the records behind it. The source did not point
there. The one place a user starts, the tooltip, was the one place that led
nowhere.

Doclint already holds every `[Type]` link to resolve (ADR-0343) and `check`
runs it (ADR-0405). Nothing held what a comment said, or where it sent its
reader.

## Decision

A doc comment is written for the reader of the published javadoc. It says what
the object is in a sentence a user would write, how to use it, and how it works
in plain words. Its last paragraph links the chapter of the guide that covers
the type, as an absolute `https://goldberry.dev/docs/…` link anchored to a
heading when one fits. The reason for a surprising choice is a sentence in the
comment; the history behind it is the record, which the chapter links. The
`package-info` of every package in a published module carries such a link, so
no page of the javadoc is more than a click from the guide.

A source does not cite a record. Not by number, not by relative link, not by
the word. `ADR-0063` becomes *data flows down and events flow up, so the widget
never writes the value it shows*, and the chapter on choices links the record
for anyone who wants to argue with it. A reference to a working document under
`docs/` is replaced the same way: by the rule itself and a link to the chapter
that states it. A test's comment keeps its account of what broke, because that
is what a test is for, and loses the citation.

`SourceDocsTest` in build-logic holds four rules as text: no record number in
a Java, Gradle or workflow source outside the test that manages the log; every
`goldberry.dev/docs` link in a Java source lands on a chapter the summary lists
and a heading that chapter has; every `package-info.java` of a published module
links the guide; and no Java source names a working document under `docs/` by
file. `tools/book/guide_links.py` reports the same, plus a bare section sign
and a `///` line the formatter would break, so a batch can be checked without
Gradle. `book/src/contributing/doc-comments.md` is the house
style, with a table of which chapter each package links.

The rewrite that applied this to the 1,556 files is recorded in
`docs/doc-comments-2026-10-01.md`.

## Alternatives considered

**Keep the citations and publish the log.** ADR-0512 took the log out of the
built book because its 517 pages swamped the guide's search and its titles
read as a changelog. Putting it back to make `ADR-0481` clickable would undo
that for the sake of a reader who, having clicked, lands on a page of forces
and alternatives when they wanted to know what the method does. The record is
the right thing for a maintainer and the wrong thing for a tooltip.

**Turn each citation into a GitHub link.** Clickable, and still the wrong
destination for the same reason. It also hard-codes the repository's host and
branch into 5,000 lines of source, which is a rename away from being 5,000
dead links; the relative links this replaces were exactly that.

**Leave the comments alone and link the guide from the `package-info` only.**
Cheap, and it would have left every comment saying *see ADR-0481* to a reader
who cannot. The number is the problem, not the absence of a link beside it.

**Strip the number and keep the sentence.** The first pass considered a
mechanical `sed`. It would have left *see the ADR* with no ADR, *§2 says* with
no §2, and a thousand sentences whose reason had been the citation. The
rewrite had to carry the reason into the sentence or drop the sentence, which
is editorial work per comment, not a substitution.

## Consequences

**Easy now.** A reader of the published javadoc reaches the guide from any
package and most types in one click, and the chapter they reach links the
records behind it, so the full chain exists in the direction a reader walks
it. A new type's comment has a shape to follow and a table to look the chapter
up in. A renamed heading breaks a build, not a reader.

**Harder now.** The maintainer reading source no longer sees which record a
line implements; they read the sentence, follow the guide link, and read the
chapter's *Read more*. That is one hop further than before, by design: the
maintainer has the repository and the user does not, and the comment is
written for the one who does not. Where a comment's reason was *only* the
citation, the rewrite had to find the reason in the record and write it down,
and some of those sentences are now longer than the number was.

**Expensive to reverse.** The 5,176 lines are rewritten, not transformed, and
there is no mapping back to the numbers they carried. `git log` has the
previous text. `SourceDocsTest` refuses a record number in a source, so a
citation cannot creep back in by habit; relaxing that is a one-line change to
the test and a reversal of this record.

**Still open.** The test holds the rules it can read as text. It does not
judge whether a comment is clear, or whether the chapter linked is the best
one; the table in the house style is advice, and the review is the check. A
section sign is reported by the script and not the test, because a citation
of a public standard (`ISO/IEC 18004 §7.9`) is a fact a reader can follow.

# Recording a decision

<p class="gb-lede">A choice with alternatives and costs gets one immutable record, numbered next in line, and the build checks that it is there.</p>

## Why the log exists

`docs/ARCHITECTURE.md` says what the design is, in the present tense, with the reasoning compressed out. Six months on, nobody can tell which lines are considered choices and which are placeholders that survived because nobody revisited them. The decision log is the reasoning: one record per choice, with the forces that pushed on it, what was decided, what else was on the table, and what it costs. The point is that the reasoning survives the people who did it. That is [ADR-0001](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0001-record-architecture-decisions.md), and the log is the last part of this book.

A record is reviewed in the same pull request as the code that implements it. Commit messages are keyed to changes, and a decision made across five commits is unrecoverable from them.

## When a change needs one

A record is for a **choice**: there were alternatives, each had a cost, and the next maintainer could undo the choice without knowing what it was load-bearing for. A bug fix is a commit. It becomes a record when fixing it meant choosing between designs, or when the bug was invisible for a reason worth writing down. [ADR-0357](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0357-a-test-that-paints-asks-for-the-library-and-a-download-asks-twice.md) is one of those: the fix was two lines, and the rule it established applies to every test written since.

Two tests of whether a change needs a record:

- Could a reviewer ask "why not the other way?" and need more than a sentence?
- Would someone a year from now be tempted to reverse it?

A record with no costs listed has not been thought through. If there is nothing to put under Consequences, there was no decision.

## The template

Start from [the template](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0000-template.md). Its sections, in order:

| Section | What goes there |
|---|---|
| Title | `# ADR-NNNN: Title`, or `# NNN. Title`. Both house styles are in use |
| Status | `Proposed`, `Accepted`, or `Superseded by ADR-NNNN`. A bullet in the early records, a `## Status` section in the later ones |
| Date | The day it was recorded |
| Relates to | The `ARCHITECTURE.md` section, and the records it leans on or amends |
| Context | The forces at play. What makes this a decision rather than an obvious call. The constraints stated honestly, including the ones about time or taste |
| Decision | What was decided, in the active voice. One paragraph if possible |
| Alternatives considered | What else was on the table, and the specific reason each was rejected. "It was worse" is not a reason |
| Consequences | What becomes easy, what becomes hard, and what is now expensive to reverse. The costs, not only the benefits. This is the section future readers come for |

## Numbering

Records are numbered in the order they are **recorded**, not the order they were made. The next record takes the next free number, and the numbers run without a gap. One decision per file, named `NNNN-kebab-case-title.md`.

`DecisionLogTest` in build-logic holds the log to its own rules:

- the numbers are contiguous from `0001`, so a citation resolves to exactly one record;
- the heading carries the number the file name carries, in either house style;
- the status appears in the first fourteen lines, in one of the three spellings the log uses;
- every record has a line in the log's own `README.md`, so it is reachable from the directory GitHub shows.

```sh
./gradlew :build-logic:test --tests '*DecisionLogTest*'
```

> [!WARNING]
> A number reserved and not used goes red. Two records written in parallel take consecutive numbers, and whichever lands second renumbers if the first took its number.

## Status values

| Status | Meaning |
|---|---|
| Proposed | Written down, not yet agreed. An open question |
| Accepted | Agreed and in force |
| Superseded | Replaced. The record names what replaced it |

## Supersession

Records are immutable once accepted. A decision that turns out to be wrong is not edited. A new record supersedes it, the old one gains a `Superseded by ADR-NNNN` line in its status, and the wrong turn stays visible. [ADR-0012](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0012-native-ci-runners-with-a-pinned-glibc.md) replaced [ADR-0011](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0011-zig-cross-compilation-toolchain.md) that way, and [ADR-0510](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0510-publish-under-dev-goldberry.md) replaced [ADR-0009](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0009-publish-under-io-github-digitalsmile.md) and kept its text verbatim apart from the status line.

A record may also **amend** one without superseding it: a matrix that lost two rows, a mechanism that stayed while the numbers changed. The amending record says so in its status, and the amended one gains a blockquote pointing forward. ADR-0012 carries one from [ADR-0041](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0041-three-platforms-four-artifacts-two-backends.md).

The log will contain records that are wrong. That is the intended behaviour.

## How the guide links a record

From a chapter of this book, a link is relative and ends in `.md`:

```markdown
[ADR-0063](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0063-data-flows-down-events-flow-up.md)
```

From `docs/`, the path is `../book/src/adr/NNNN-slug.md`. A Java doc comment does not cite a record at all: its reader has a tooltip or a javadoc site, not the repository, so the comment states the rule in plain words and links the chapter of this guide that covers it. The chapter's *Read more* is where the record is linked. That is [ADR-0518](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0518-a-doc-comment-explains-the-object-and-links-the-guide.md), and [Writing a doc comment](doc-comments.md) is the house style.

Every chapter's *Read more* names the records behind it, and a widget's chapter links the record for each rule it states. A record that nothing links is a record nobody will find.

## The title

A title states the decision as a sentence, so the table of contents reads as a list of what was decided:

- *A version is a year and a count*
- *A package is a role, and the module is the fence*
- *Every package says what it is, and is null-marked*
- *A preflight check that cannot fail is not a check*

Not *Versioning*, and not *Use calendar versions*. The sentence is the decision, and a reader scanning the log should be able to stop at the title.

## Status and TODO

Two pages beside the log are updated when a record lands:

- [Status](../status.md) says what is built, milestone by milestone. A record that completes a piece moves its row.
- [TODO](../TODO.md) says what is deferred, known-broken, or specified and unbuilt. An entry leaves the top half when a record answers it and moves to *Answered* rather than being deleted, because each one records a trap somebody hit and the reasoning that got out of it. A record that closes an entry says so in its status, as [ADR-0508](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0508-ffmpegs-source-is-published-beside-its-binaries-from-the-same-place.md) does.

`docs/ARCHITECTURE.md` §17.1 lists where the design documents disagree with each other, and a record that settles one strikes the entry through rather than deleting it.

## Writing one

<div class="gb-steps">
<div><b>1</b><p>Take the next free number. The highest file in book/src/adr/ plus one.</p></div>
<div><b>2</b><p>Copy 0000-template.md to NNNN-kebab-case-title.md and fill every section. Consequences last, and honestly.</p></div>
<div><b>3</b><p>Add its line to the list at the end of book/src/adr/README.md, in the same shape as the line above it.</p></div>
<div><b>4</b><p>Link it from the chapter, the status row or the TODO entry it changes, and from any record it supersedes or amends.</p></div>
<div><b>5</b><p>Run the build-logic tests and checkMarkdown. Put the record in the same pull request as the code.</p></div>
</div>

```sh
./gradlew :build-logic:test checkMarkdown
```

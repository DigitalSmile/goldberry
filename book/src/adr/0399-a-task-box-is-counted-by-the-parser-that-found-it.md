# 399. A task box is counted by the parser that found it

Date: 2026-09-18

## Status

Accepted. Answers H1 of the whole-tree review recorded in
`docs/review-2026-09-18.md`.

Follows [ADR-0300](0300-a-document-is-read-and-the-application-answers.md),
which decided that toggling a task rewrites one character of the source.

## Context

`markdown-view` renders `- [ ] milk` as a checkbox, and a reader who clicks it
calls `Markdown.toggleTask(index)`. The index is **md4c's**: it is counted by the
fold that walks the parsed document and mints a widget per task item. The
character it rewrote was found by something else entirely — a line-at-a-time
regular expression in `Tasks`, anchored `^\s{0,3}[-*+] \[[ xX]\]`, counting its
own way to the `index`th match.

Two counts, one ordinal. They agree on a flat list and disagree on almost
anything else, because whether `- [ ]` on a line is a task box depends on facts a
line does not carry:

- how deeply the list is nested — CommonMark allows a sub-item four or more
  spaces in, which md4c counts and `\s{0,3}` rejects;
- whether a `>` is in front of it;
- whether it is inside a fenced block or an indented code block, where it is
  text and not a box at all.

So ticking the second box in any list with sub-tasks ticked a different box. The
doc comment at `Markdown.java` described a *third* failure — indented code
blocks being mistaken for tasks — which was not the one that happened.

Widening the indent bound would have fixed the sub-task case and left the quoted
one and the code-block one exactly as they were. There is no pattern that can be
made to agree, because the disagreement is not about the pattern.

## Decision

**`Tasks` asks md4c where the box is.**

`MD_BLOCK_LI_DETAIL.task_mark_offset` is the offset of the `[ ]` mark in the
source md4c was handed. It was already plumbed through `:natives` to
`BlockDetail.Item.taskMarkOffset()` and read by nothing. `toggleTask` now walks
the event stream for task items, takes the `index`th offset, and rewrites that
one character.

ADR-0300's constraint is preserved and is worth restating, because the old code's
comment conflated two things: the rule is that a toggle is **a one-character
edit of the source**, not that it is *a scan rather than a parse*. The document
stays the model; nothing is re-serialised; a reader's own formatting, their
trailing spaces and their hard line breaks survive a tick, which is the whole
point of the rule.

One detail that is easy to get wrong and is now written at the conversion:
`task_mark_offset` counts **UTF-8 bytes** of what md4c was handed, and a Java
`String` counts UTF-16 code units. Without the conversion, a note with an emoji
anywhere above its task list edits a character to the right of the box.

The dialect is `MarkdownSyntax.gitHub()` internally, because a task box *is* that
dialect: under plain CommonMark the same text contains no tasks and there are no
ordinals to count.

## Alternatives considered

- **A better regular expression.** Rejected above: three known failure modes, and
  the next one is whatever CommonMark allows that nobody has typed yet.
- **Carrying the offset on the widget.** The view already knows where each task
  came from, so `onTask` could hand back an offset rather than an index. It is a
  better shape and it is a public API change — `Markdown.toggleTask(int)` is
  documented and used — so it is not what a defect fix should do. The signature
  is unchanged.

## Consequences

- `toggleTask` parses the document once per tick. That is a keystroke's worth of
  work for an answer nothing cheaper can give, and it is the same parse the view
  does on every edit anyway.
- Nested, quoted and code-fenced documents tick the right box.
  `TasksTest.NESTED` holds all three cases, and `AgreesWithMd4c` runs over it as
  well as over the flat document.
- `Markdown`'s doc comment now describes the failure that existed rather than one
  that did not.

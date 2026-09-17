# 343. The published javadoc is linted

Date: 2026-09-17

## Status

Accepted. Closes `TODO.md`'s "the published javadoc is built with doclint off".

## Context

`goldberry.publish` turned doclint off for the javadoc jar Central requires,
with a comment counting ~120 errors and naming two causes. With the lint on
and every published module built, the count was exact — 120 — and the causes
were the two the comment named, in different proportions than it guessed:

| Kind | Count | Where |
|---|---|---|
| `invalid use of @param` / `@return` | 100 (the cap; 425 tag lines in fact) | `:natives`' `…Calls` holders |
| `reference not found` | 20 | `:core`, `:widgets`, `:html`, `:natives` |

The first is one idiom. Every foreign function in `:natives` is a nested
`public static final class` with one `call` method, and the class's `///`
comment carried the `@param` and `@return` lines for that method. Doclint
reads a `@param` on a class as a type parameter that does not exist. The
second is twenty `[Foo]` links to a type in another package — resolvable in
an IDE, which follows imports the way a reader does, and not by javadoc,
which needs the qualified name or an import the file did not have. Three of
them were also wrong: a method renamed (`repeat()` for `isRepeat()`,
`model()` for `models()`), a signature that had grown a parameter, and a
class that does not exist (`ItemCheck`, for what is `ItemLead`).

Beside the errors were 412 warnings, all in doclint's `missing` group: no
`@param` for this parameter, no `@return`, no comment on this member.

## Decision

**The errors are fixed at the source and the lint stays on, less `missing`.**

- The 425 tag lines moved from the holder class to its `call` method, under
  a one-line summary naming the C function — mechanically, one script over
  thirty files, checked by the lint that would have refused a name that no
  longer matched a parameter.
- The twenty references are qualified, corrected, or — where the type is in a
  module this one cannot see, `Backend` from `:natives` or `ListView` from
  `:core` — turned into a code span, which is what they always were.
- `goldberry.publish` passes `-Xdoclint:all,-missing`. `missing` is left out
  on purpose: it wants a `@param x the x` for every parameter, and this
  codebase documents in prose. A `///` sentence that says what a method does
  is the documentation, and four hundred tag lines restating parameter names
  would be noise that hides the sentence. The three other groups —
  `accessibility`, `html`, `reference`, `syntax` — are what catch a link that
  rots or a tag on the wrong thing, and those are on.

Two things worth knowing for next time. Palantir's formatter **wraps a `///`
line over 120 characters by breaking it**, and the continuation is not a
comment — the compiler error is `<identifier> expected` on the line after. A
qualified name pushed three lines over, each a build failure until rewrapped
by hand. And a `2>&1 > file` redirect sends javadoc's errors to the terminal
and nothing to the file; the order is `> file 2>&1`.

## Consequences

- `./gradlew javadoc` fails on a broken `[link]` or a misplaced tag, on every
  module that publishes. The release's last step cannot be the first to find
  one.
- A new `…Calls` holder documents its `call` method, not its class.
- A link to a type in another package is spelled in full; a link to a type in
  another module is a code span.

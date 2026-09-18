# Answering the review of 2026-09-18

Working notes for the fixes that came out of [`review-2026-09-18.md`](review-2026-09-18.md).
The review is the record of what was found; this file is the record of what was
done about it, entry by entry, in the order §12 suggested.

The review's own numbering is kept — `C1`…`C22` for `:core`, `W1`…`W15` for
`:widgets`, `H1`…`H7` for `:html`, `N1`…`N7` for `:natives`/`:weaver`,
`B1`…`B12` for the build — so a row here can be read beside the paragraph that
produced it. Nothing in the review's text was edited; a fixed entry is answered
here rather than struck through there.

## How this is being worked

**Not one ADR per entry.** The `docs/gaps.md` batches take one ADR per entry
because each entry is a feature with a decision inside it. Most of the rows
below are defects, and "the loop should terminate" is not a decision. An ADR is
written where a fix *chose* between two defensible behaviours — cascade order,
what an unmounted element is told, what the weaver preserves — and the rest are
recorded here with the test that now holds them.

**One wave at a time, in the review's suggested order.** Parsers that hang or
crash first, then what a user can see, then the crash paths, then the weaver and
the build, then the cascade and the charts, then the tests, then the prose.

## Status

| Wave | What | State |
|------|------|-------|
| 1 | C1–C3, C22 parsers; W1–W3 tabs and the two editors; H1, H2 markdown; B1–B12 build and CI | **done** except N1–N3, which moved to wave 2 |
| 2 | N1–N3 the weaver; C7, C15, C16, C19, C20 — the pointer, the popups and the overlays | in progress |
| 3 | C4, C5, C10, C11, C21 — cascade, lint, damage, editing, the animation shorthand; W4–W8 — the scrollbar, the ticks and the charts | in progress |
| 4 | the remaining `:core`, `:widgets`, `:html`, `:natives` rows | open |
| 5 | §6 and §11 — the tests: what should not run under `check`, what asserts nothing, the parity sweep of §11.5 | open |
| 6 | §7 dead code and duplication; §8 the prose | open |

## 1. `:core`

| # | State | What landed |
|---|-------|-------------|
| C1 | **done** | The dispatch in `next()` sent *any* backslash into `identLike()`; a `\` that begins no escape is a `<delim-token>` now, per CSS Syntax 3 §4.3.1. It was an **OOM, not a hang** — the loop allocates a token per turn. `CssTokenizerTest$Escapes.backslashNewlineOutsideAString`, bounded at 500 ms. |
| C2 | **done** | `takeSlashdash()` skipped trivia to end of input and returned `true` regardless, leaving every caller to peek at nothing. It refuses a dangling `/-` with a positioned `KdlSyntaxException`. `KdlParserTest$Comments.danglingSlashdash`, four cases. Only `/-` *inside* a node crashed; a bare `/-` document already threw. |
| C3 | **done** | `Character.isSurrogate((char) code)` kept the low sixteen bits, so the test was applied to the wrong number. A private `isSurrogate(int)` leaves the spec's three cases exactly. `CssTokenizerTest$Escapes.supplementaryEscape`. |
| C4 | open | |
| C5 | open | |
| C6 | open | |
| C7 | open | |
| C8 | open | |
| C9 | open | |
| C10 | open | |
| C11 | open | |
| C12 | open | |
| C13 | open | |
| C14 | open | |
| C15 | open | |
| C16 | open | |
| C17 | open | |
| C18 | open | |
| C19 | open | |
| C20 | open | |
| C21 | open | |
| C22 | **done** | The guard admitted only a *bare* identifier. It is `startsIdentifier() \|\| startsQuotedOrRawString()` now — raw keys are legal KDL 2.0 too, which the entry did not mention. `KdlParserTest$Values.quotedPropertyNames`. |

## 2. `:widgets`

| # | State | What landed |
|---|-------|-------------|
| W1 | **done** | `pendingReveal` is written in `build()`, not `select()`: a strip does not decide its own selection, so a bound value, `Ctrl+Tab` and an application list change all arrive as a rebuild. `TabRevealTest`, three tests. Note ADR-0120's "exactly one header per build carries the callback" is no longer true (ADR-0372/0377). |
| W2 | **done** | The cut moves back to a `BreakIterator.getCharacterInstance()` boundary — the same class a caret steps with. Lifted to `form/parts/MaxLength`. `MaxLengthTest` (9), plus paste cases in both controls. [ADR-0400](../book/src/adr/0400-a-clause-is-a-start-and-a-length.md). |
| W3 | **done** | `clauseEnd` is `clauseLength` throughout: SDL puts a length in the struct and both implementations always computed `start + length`. Lifted to `form/parts/Preedit`. Latent through today's only caller — `PreeditEvent.caret()` *is* the clause end — so the tests drive the seam directly. [ADR-0400](../book/src/adr/0400-a-clause-is-a-start-and-a-length.md). |
| W4 | open | |
| W5 | open | |
| W6 | open | |
| W7 | open | |
| W8 | open | |
| W9 | open | |
| W10 | open | |
| W11 | open | |
| W12 | open | |
| W13 | open | |
| W14 | open | |
| W15 | open | |

## 3. `:html`

| # | State | What landed |
|---|-------|-------------|
| H1 | **done** | Asks md4c for `task_mark_offset` rather than re-deriving the position with a pattern; converted from UTF-8 bytes to UTF-16 units. `TasksTest.NESTED` covers a four-space sub-task, a quoted box and a fenced one. [ADR-0399](../book/src/adr/0399-a-task-box-is-counted-by-the-parser-that-found-it.md). |
| H2 | **done** | `marker()` mints a word as a side effect; `item` now calls it only on the branch that keeps it. `SelectionTest.aTaskListHasNoPhantomBullet`. |
| H3 | open | |
| H4 | open | |
| H5 | open | |
| H6 | open | |
| H7 | open | |

## 4. `:natives` and `:weaver`

| # | State | What landed |
|---|-------|-------------|
| N1 | open | |
| N2 | open | |
| N3 | open | |
| N4 | open | |
| N5 | open | |
| N6 | open | |
| N7 | open | |
| §4 tail | open | dead members, needless `assumeTrue`, and the doc counts in `book/src/native.md` |

## 5. Build, CI and example

| # | State | What landed |
|---|-------|-------------|
| B1 | **done** | Depends on `tasks.matching { it.name == 'blessGoldens' }` per subproject — a live view. [ADR-0398](../book/src/adr/0398-the-build-declares-what-it-actually-writes.md). |
| B2 | **done** | The weave tasks declare a stamp file rather than javac's directory, so Gradle stops discarding the compiler's incremental state. `:widgets:compileJava` is up to date on a second build again. [ADR-0398](../book/src/adr/0398-the-build-declares-what-it-actually-writes.md). |
| B3 | **done** | `:emoji` forwards `goldberry.native.required` like `:core`, `:widgets` and `:html`, and joins the native leg on all three platforms. |
| B4 | **done** | The triptych lists gain `html/`, `emoji/` and (on linux) `example/`. |
| B5 | **done** | The nightly `coverage` job is gone: it was `linux.yml`'s `java` job step for step, and its comment described a scale sweep `-Pgoldberry.skipNative` cannot draw. |
| B6 | **done** | Two root tasks, `checkMarkdown` and `formatMarkdown`. Spotless refuses a target outside its project and the root cannot apply a build-logic plugin. [ADR-0398](../book/src/adr/0398-the-build-declares-what-it-actually-writes.md). |
| B7 | **done** | **The review's workaround does not work.** `requires static transitive` fails identically on PMD 7.19.0 and 7.20.0 — the grammar takes one modifier, and the order is not what it objects to. `module-info.java` is excluded from PMD on purpose, with the reason at the exclusion. [ADR-0398](../book/src/adr/0398-the-build-declares-what-it-actually-writes.md). |
| B8 | **done** | `AppMenu.Handlers` carries `startTour` and `about`; `Showcase` has an about box. `ShowcaseShellTest.eachRowRunsItsOwnHandler` presses every row and asserts which handler fired. |
| B9 | **done** | The last tour stop says thirteen screens and ten digits. Every other count in the example follows. |
| B10 | **done** | `:emoji` joins `jacocoAggregation`. |
| B11 | **done** | `--baseline` is gone; the file it named has never existed. |
| B12 | **done** | `pitestPlugin` removed; `pmd` and `pitestJunit5` are catalog entries now. |

## 6. Tests, §7 smells, §8 prose

| Item | State | What landed |
|------|-------|-------------|
| §6 should not run under `check` | open | |
| §6 asserts nothing | open | |
| §6 duplicated scaffolding | open | |
| §7 dead code | open | |
| §7 duplication | open | |
| §8 contradicts the code | open | |
| §8 stale doc comments | open | |
| §8 comments on the wrong member | open | |
| §11.1 the five habits | open | |
| §11.2 parameterized merges | open | |
| §11.3 fragile assertions | open | |
| §11.5 the parity sweep | open | |

## Records written

| ADR | What it decides |
|-----|-----------------|
| [0398](../book/src/adr/0398-the-build-declares-what-it-actually-writes.md) | A task declares the files it owns and only those — the weaver's stamp, the prose tasks, PMD's exclusion, `blessGoldens` |
| [0399](../book/src/adr/0399-a-task-box-is-counted-by-the-parser-that-found-it.md) | `toggleTask` asks md4c where the box is; supersedes one sentence of ADR-0300 |
| [0400](../book/src/adr/0400-a-clause-is-a-start-and-a-length.md) | A preedit clause is a start and a length; a limit cuts on a grapheme boundary; both live in `form/parts` |

## What the review got wrong

Kept because a review is a record and being wrong in a traceable way is worth
more than being quietly corrected.

- **C1 is an `OutOfMemoryError`, not a hang.** The loop allocates a token per
  turn, so an unfixed tree dies in seconds. It matters for the test: a five-second
  bound never fires, because the heap fills first and takes the whole test
  executor with it.
- **C2's line reference is the call site.** `takeSlashdash()` contains no
  `peek()`; the unguarded one is in `node()`. The fix still belongs in
  `takeSlashdash()`, because `nodes()` calls it with the same assumption. And a
  bare `/-` document already threw the right exception — only `/-` inside a node
  crashed.
- **C22 is wider than quoted keys.** The guard also excluded *raw* strings, which
  KDL 2.0 admits as property keys.
- **B7's workaround does not work.** `requires static transitive` was tried
  against PMD 7.19.0 and 7.20.0 and fails identically. PMD takes one modifier;
  the order is not the objection.
- **H1's frame invites too small a fix.** The four-space indent is one symptom of
  three; a block quote is the same bug and the entry does not mention it.
- **W3 is latent through today's only caller.** `PreeditEvent.caret()` is defined
  as the clause end whenever a clause is reported, so the caret comparison caught
  every resize by accident. The contract is still wrong and bites the moment an
  event carries a caret of its own.

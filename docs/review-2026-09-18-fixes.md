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
| 1 | C1–C3, C22 parsers; W1–W3 tabs and the two editors; H1, H2 markdown; N1–N3 the weaver; B1–B3 build and CI | in progress |
| 2 | C7, C9, C15, C16, C19, W9 — crash paths and stale pointer state | open |
| 3 | C4, C5, C10, C11, W4–W8 — cascade, damage, editing, charts | open |
| 4 | the remaining `:core`, `:widgets`, `:html`, `:natives` rows | open |
| 5 | §6 and §11 — the tests: what should not run under `check`, what asserts nothing, the parity sweep of §11.5 | open |
| 6 | §7 dead code and duplication; §8 the prose | open |

## 1. `:core`

| # | State | What landed |
|---|-------|-------------|
| C1 | open | |
| C2 | open | |
| C3 | open | |
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
| C22 | open | |

## 2. `:widgets`

| # | State | What landed |
|---|-------|-------------|
| W1 | open | |
| W2 | open | |
| W3 | open | |
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
| H1 | open | |
| H2 | open | |
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
| B1 | open | |
| B2 | open | |
| B3 | open | |
| B4 | open | |
| B5 | open | |
| B6 | open | |
| B7 | open | |
| B8 | open | |
| B9 | open | |
| B10 | open | |
| B11 | open | |
| B12 | open | |

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

None yet.

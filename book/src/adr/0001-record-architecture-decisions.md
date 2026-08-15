# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-08-15

## Context

`docs/ARCHITECTURE.md` describes the system as intended, and describes it well —
but a design document has a structural weakness: it says what the design *is*, in
the present tense, with the reasoning compressed out. Six months on, nobody can
tell which lines are considered choices with rejected alternatives behind them
and which are placeholders that survived because nobody revisited them.

That distinction matters most for the decisions that are expensive to reverse:
CPU rasterization, SDL3 as the only desktop backend, the three-tree widget model.
Each of those will look questionable to someone at some point, and without the
reasoning written down they will either be re-litigated from scratch or undone by
someone who did not know what they were load-bearing for.

## Decision

Keep an architecture decision log in `book/`, one immutable record per decision,
following the conventions in [About these records](index.md). The design document
stays the description of the system; the book becomes the description of the
reasoning. New features and changes get a record before or alongside the code.

## Alternatives considered

- **Reasoning inline in `docs/ARCHITECTURE.md`.** Rejected: the document is
  already long, and interleaving rationale with specification makes both harder
  to read. It also has no way to express "this was decided, then reversed."
- **Commit messages and PR descriptions.** Rejected: they are keyed to changes,
  not to decisions, and a decision made across five commits is unrecoverable.
- **A wiki.** Rejected: it drifts from the code because it is not reviewed with it.

## Consequences

- Decisions become reviewable in the same pull request as the code that
  implements them.
- There is now a per-change cost: a feature that changes the architecture needs a
  record, and writing an honest consequences section takes real thought.
- The log will contain records that are wrong. That is the intended behaviour —
  they get superseded, not deleted, and the wrong turn stays visible.

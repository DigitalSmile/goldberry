# About these records

An **architecture decision record** captures one significant choice: the forces
that pushed on it, what was decided, and what the decision costs. The point is
that the reasoning survives the people who did the reasoning.

## Conventions

- One decision per file, named `NNNN-kebab-case-title.md`, numbered in the order
  they were *recorded* rather than the order they were made.
- Records are immutable once accepted. A decision that turns out to be wrong is
  not edited — a new record supersedes it, and the old one gains a
  `Superseded by ADR-NNNN` line so the history stays readable.
- Every record states its consequences, including the bad ones. A record with no
  costs listed has not been thought through.
- Start from [the template](0000-template.md).

## Status values

| Status | Meaning |
|---|---|
| Proposed | Written down, not yet agreed. Open question. |
| Accepted | Agreed and in force. |
| Superseded | Replaced; see the record that replaced it. |

## Recorded retroactively

ADR-0002 through ADR-0005 document decisions that were already made in
`docs/ARCHITECTURE.md` before this log existed. They are written up here because
they are the load-bearing ones: every later choice leans on them, and a reader
who does not know *why* CPU rasterization or SDL3-only was chosen will keep
proposing to undo them.

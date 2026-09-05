# 245. The second surface stays, and says so

Date: 2026-09-05

## Status

Accepted. Answers the `TODO.md` entry opened by
[ADR-0168](0168-a-field-is-a-well-and-a-drag-is-a-selection.md).

## Context

The entry asked one question and said what answering it needed:

> `--gb-surface-2` has now been mistaken for an elevation three times — by
> `card` (ADR-0166), by `text-input` and by `select` (ADR-0168). It means "the
> second surface" and promises no direction, and each consumer that assumed
> otherwise was wrong on one theme only. […] what is unresolved is whether
> `--gb-surface-2` should keep existing at all, and that needs a look at what
> still reads it.

## The look

Five things in the toolkit read it, and **not one of them wants a direction**:

| reader | what it is |
|---|---|
| `--gb-badge-bg` | a default chip's fill |
| `scroll:hover scrollbar` | the track's plate while the pointer is over it |
| `group-box-title` | the header band above a body |
| `skeleton-bar` | a placeholder bar |
| `split-divider.collapsed` | a divider flush against an edge |

Every one wants a plate that is merely *distinct* from what is under it. The
three that were wrong wanted "raised" or "sunken" and have `--gb-surface-raised`
and `--gb-surface-sunken` now.

## Decision

**It stays**, because five live consumers want exactly what it promises, and
naming it something else would not have prevented a single one of the three
mistakes — those were consumers reaching for a direction from a token that never
offered one.

**And the trap becomes an asserted fact rather than a comment.** `ThemeTest`
gains three cases:

- `--gb-surface-raised` is never *darker* than `--gb-surface`, on either theme.
  Equal is allowed: the light theme's is the same white, because there is nowhere
  lighter to go and the edge carries the elevation instead (ADR-0166).
- `--gb-surface-sunken` is never *lighter*, composited over the surface — it is
  `rgba(0, 0, 0, …)` in both files by design, so comparing its raw value would be
  comparing a black nobody paints.
- **`--gb-surface-2` takes opposite directions in the two themes** — a step *up*
  from `--gb-surface` on dark and *down* on light. That is exactly why each of
  the three consumers looked right to whoever wrote it and wrong to everybody on
  the other theme, and it is now something a test says out loud.

The definition in both theme files carries the same sentence, so the next reader
meets it where the token is rather than in an ADR.

## Alternatives considered

- **Delete it and give the five consumers their own tokens.** Five tokens whose
  values would all be `--gb-surface-2`, and a sixth consumer would still have to
  pick one. The problem was never the token's existence.
- **Rename it `--gb-surface-distinct`.** More honest and it moves every
  reference for a benefit the assertion delivers. A name cannot stop somebody
  reaching for a direction; a failing test can.
- **Make the two themes agree on the direction**, so the token could promise
  one. Then it is `--gb-surface-raised` under a second name, and the light
  theme's ramp has nowhere to put it — the reason `--gb-surface-raised` is white
  on white there in the first place.

## Consequences

- **The elevation tests would have caught all three original bugs**, which is the
  test to write when a class of mistake has happened three times: not one that
  checks the three fixed sites, but one that checks the property they violated.
- **If the `--gb-surface-2` direction test ever fails**, the themes have been made
  to agree and the question this record answers is worth reopening. Its message
  says so, because the tempting fix is to edit the assertion.
- **The `-2` reads are five and shrinking is not a goal.** `card`, `text-input`
  and `select` left because they wanted something else, not because the token is
  bad.

# ADR-0531: A step may say it is not done

- **Status:** Accepted. Amends [ADR-0344](0344-a-list-of-steps-writes-where-each-one-stands.md).
- **Date:** 2026-10-02
- **Relates to:** [ADR-0356](0356-a-connector-grows-from-where-you-were-and-an-entry-has-a-marker-slot.md),
  `docs/goldberry-gaps.md` entry 10

## Context

ADR-0344 made a step's state a function of the list's current index: before it
is done, at it is current, after it is upcoming. The one word a step kept for
itself was `error`.

Deploy Orc's sign-in wizard broke that model. Its pages are independent
accounts, and a user may skip any of them. On the Kubernetes page the indicator
ticked GitLab and Grafana as done while their descriptions said "Not signed
in". The position said the user had been there. Only the application knew that
nothing had been finished there.

## Decision

**A step may say whether it is complete. Unsaid, the position decides, as
before.**

- `Step.complete(@Nullable Boolean)` and `WizardPage.complete(@Nullable Boolean)`,
  and `complete=` in markup on both, read as a flag. Null, or an attribute that
  is not a boolean, means "by position". `WizardState` hands the page's value
  to the step it builds.
- `StepState` gains `INCOMPLETE`: a step before the current one that says it is
  not complete. A step after the current one that says it is complete is
  `DONE`, which is what going back to an earlier page looks like. One that says
  it is not complete is `UPCOMING`. The current step is `CURRENT` whatever it
  says, and `error` still wins over everything.
- The connector after a step is filled when that step is `DONE`. That was
  already the rule in effect. It is now written as the state rather than as the
  index, so an incomplete step leaves its line empty.
- `incomplete` is a class on the step and on its marker, like the other four.
  `controls.css` draws it as the upcoming ring with the number, in the text's
  ink rather than the muted one: visited, not ticked. The accessible name ends
  with "incomplete", because the colour alone cannot say it.

## Alternatives considered

- **A class the application adds to the page.** It would colour the step and
  leave the tick in the marker, and a reader would still hear "done".
- **`StepState` as an attribute.** ADR-0344 refused that for a good reason: a
  document could then describe two current steps. `complete` cannot. It can
  only move a step between done and not done.

## Consequences

- A wizard whose pages may be skipped draws them as they are. Nothing changes
  for one that does not say `complete`.
- `Step` and `WizardPage` have one more record component, so their canonical
  constructors take one more argument. The short constructors and the chained
  setters are unchanged.

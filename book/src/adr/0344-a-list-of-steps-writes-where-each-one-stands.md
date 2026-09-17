# 344. A list of steps writes where each one stands, and a wizard moves nothing

Date: 2026-09-17

## Status

Accepted. Builds `docs/core-widgets.md` §6's `steps` and `wizard`, the two
`nav` widgets that `status.md` listed as "not started" after `breadcrumbs`.

## Context

§6 specifies three widgets over one model — "an ordered list of steps, a
current index, and which of them are reachable" — and `breadcrumbs` was the
first (ADR-0306). Its one hard-won rule was that the *trail* decides which
crumb is current and a document cannot: an invariant written on every build
rather than hoped for. `steps` has the same invariant with one more word in
it, and `wizard` is `steps` plus a page plus a bar with, as §6 puts it, "*no*
validation, no navigation policy and no data".

Two sentences in §6 needed a decision rather than a transcription. "The widget
never decides reachability itself, because only the application knows whether
step 3 is valid yet" — so a press has to be gated twice, once by the list and
once by the step. And "advancing moves focus to the new step's first control,
because a keyboard user who pressed Next and stayed on the button has not
moved" — which is a thing a widget cannot describe and has to ask for.

## Decision

**The list writes index, count and state onto every step; a step keeps
`error` and `reachable` for itself; a press needs both the list and the step
to allow it; and a wizard reports Back, Next and Finish and moves nothing.**

- **`steps`** is a `Widget.Stateless` composition building a `StepList`
  (the CSS type, per ADR-0109). On every build it hands each `Step` its
  position, the total, and a `StepState` — `DONE` before the index, `CURRENT`
  at it, `UPCOMING` after — unless the step says `error`, which overrides all
  three: a step that failed is neither done nor merely upcoming, wherever the
  index is. The current step is `:checked` and every state is also a class,
  because a stylesheet wants to colour four and `:checked` names one. A
  `StepConnector` between each pair is filled behind a done step, so the line
  is drawn from where you have been and stops where you are.
- **A step is pressable only when the list is `clickable` and the step is
  `reachable`**, and then reports its index through `change`, as a number.
  Otherwise it is a picture: it takes no focus, because a row of Tab stops that
  do nothing is worse than none. `current` is read through `bind` or written by
  the application (ADR-0063); the list moves nothing.
- **The marker carries the state without colour.** A done step has a tick, a
  failed one a cross, the other two their number — §6's "colour alone cannot
  carry `error`", and the accessible name says it too: `Payment, step 2 of 4,
  current`. The number is a child of the disc rather than its own text, so the
  stylesheet's centring applies to it — the first golden had every number in
  the disc's top-left corner. The label is the same shape for the same
  reason: a cell as tall as the disc with its text centred, because a padding
  that assumed the line-height was wrong by two pixels at the theme's 18px
  and would have been wrong again at every density.
- **`wizard`** is `Widget.Stateful` with one fact in its state: the page it
  last showed. It makes one `Step` per `WizardPage` and hands them to the
  standalone `Steps` — "a wizard's indicator is the standalone one and cannot
  drift from it" — builds the current page's children into a `WizardContent`
  and nothing for the others, and writes Back then Next-or-Finish into a
  `WizardActions` bar. Back is disabled rather than absent on the first page,
  so the bar does not change shape; a wizard given no `back=` has no Back at
  all. The bar is `dialog-actions` under another name: canonical order,
  affirmative-right, and a Windows theme reverses it with one declaration.
- **Advancing asks for focus.** When the current index changes under a mounted
  wizard, the state asks the host to focus the content area on a zero-delay
  timer, exactly as a dialog does on opening (ADR-0176); the content node takes
  no focus itself, so `Host#focus` resolves to the first control inside. Not on
  the first build — a window opening on page one must not take the keyboard —
  and the timer is cancelled on unmount. The content's id is the wizard's own
  with `-content` after it, or a name of the state's own for a wizard the
  document did not name, because a focus request needs an id.
- **A wizard's indicator is a picture unless `goTo` is wired**, in which case a
  reachable step forwards its index — the one way to move backwards two pages
  at once, and still the application's move to make.

## Consequences

- `steps`, `step`, `wizard` and `page` are markup; `step-marker`,
  `step-body`, `step-label`, `step-description`, `step-connector`,
  `wizard-content` and `wizard-actions` are parts, CSS-selectable and not
  constructible.
- §3's "connector fill `transform: scaleX` base" is a colour transition
  instead: a line that grows from one end needs a transform origin the subset
  does not express. `book/src/TODO.md` has it.
- `Role` has no list; the container answers `GROUP` and the items `ROW`, as
  `breadcrumbs` does, until the AccessKit bridge.
- The showcase's Navigation screen has a card with both, on one index.

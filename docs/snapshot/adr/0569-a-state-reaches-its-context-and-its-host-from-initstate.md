# ADR-0569: A state reaches its context, and its host, from initState

- **Status:** Accepted
- **Date:** 2026-10-06
- **Relates to:** the Gwent clone's issue list (GB-013)

## Context

`State` has `initState`, `didUpdateWidget` and `dispose`, and the host
reaches a state only through the `BuildContext` that `build` is handed. A
state that starts something timed when it appears, such as a match screen
that plays its opening on the host's timers, had nowhere to do it before the
first build. A build is meant to be a pure function of the widget, the state
and the context, so scheduling from it is the wrong place. The downstream
marked the opening as due in `initState` and scheduled it from the first
build.

The element already knows its host when the state is mounted.
`ElementTree` holds the host before it makes the root element, and
`Element`'s constructor calls `State.mount`, which runs `initState`. Only an
accessor was missing.

## Decision

**`State.context()` returns the state's element, as a `BuildContext`, from
`initState` until `dispose`.** It is the same object `build` is handed, so
`context().host()` answers in `initState`, and so do `findAncestor`,
`findAncestorState` and `token`. Before mount and after dispose it throws
`IllegalStateException`, for the reason `setState` does: a context kept past
`dispose` is a callback that outlived its widget.

It is `protected final`, like `widget()` and `setState`: a state's own view
of where it is, not something another object reads off it.

## Alternatives considered

- **`State.host()`**, the issue's first wording. It is a shorthand for
  `context().host()` that would sit beside the context itself, and the
  context also answers the other questions a state asks before it builds.
- **A `didMount()` hook after the first build.** The issue's second option.
  It helps only when the host is unknown until the build, and it is known at
  mount. It would also add a lifecycle step whose only use is to wait for
  something that has already happened.

## Consequences

- A state can set a timer in `initState` and cancel it in `dispose`, with
  nothing to remember between the two but the handle.
- `StateContextTest` holds it: the host found in `initState` is the
  session's, a timer set there fires at its own time, the context is the one
  `build` is handed, and it is refused outside the lifetime.
- `State` gains one method; no existing override is affected.

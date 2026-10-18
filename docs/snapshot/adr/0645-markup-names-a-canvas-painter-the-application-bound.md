# ADR-0645: Markup names a canvas painter the application bound

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0043](0043-icons-are-stroked-paths.md)

## Context

`TODO.md` carried *Markup cannot name a painter*. A `canvas` node inflated to a
surface that drew nothing. The entry knew the shape was a registry the
application owns, as `icon` and `action` have. It left open whether a painter is
a *value* a registry holds or a *method* on a model, the question `@Action`
answered for commands.

That question had since been answered three times, by widgets that each needed
a document to name an object the application holds: `field validator=`,
`form controller=` and `canvas3d renderer=`. All three resolve through
`Wiring.handle` against `Named`, the registry for objects that neither change
nor close. `canvas3d` is the closest relative there is: a surface whose drawing
is the application's.

## Decision

**A painter is a value, named the way `canvas3d` names its renderer.**
`canvas painter="name" input="name"` resolves both through `Wiring.handle`:
`painter=` to a `Painter` (a `StyledPainter` is one) and `input=` to an `Input`.
Both are optional. A canvas that names neither is the surface it always was. A
name bound to the wrong type, or unknown to a strict registry, is refused by the
registry's own message.

A method on a model was rejected. What a document wants is the drawing, and the
drawing is an object the application already holds: a chart's `paint`, a
board's renderer. Binding `chart::paint` as a value costs one line, while a
method lookup would need a new annotation and a second binding path for one
widget.

## Consequences

- The guide's "Markup names no painter" paragraph and the canvas attribute
  table change, parked in `docs/snapshot`.
- `CanvasTest` holds the plain and styled painter, the input, and the refusal.

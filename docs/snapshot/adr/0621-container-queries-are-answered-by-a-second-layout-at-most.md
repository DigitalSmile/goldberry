# ADR-0621: Container queries are answered by a second layout, at most

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-41), `docs/css-support-2026-10-10.md`
  §5; the cascade and layout order every frame follows

## Context

A frame here styles the element tree top-down, builds the boxes, and then lays
them out with Yoga. A container query needs the container's laid-out size
before its descendants' styles are known, which that order cannot give. CSS-41
asked whether the cascade should run inside layout (in Yoga's measure
callbacks) or run twice. The user decided on 2026-10-11: run twice, bounded,
restyling only the subtrees under containers whose size changed, and never a
third time.

## Decision

**A frame lays out, records every query container's content-box size, restyles
what is under the containers whose size changed, and lays out once more. A
frame never lays out a third time.**

- `dev.goldberry.css.container` holds the model: `ContainerType` (`normal`,
  `size`, `inline-size`), `Containment` (type and names, on `Presence`, not
  inherited), `QueryContainer` (what an element is as a container, and the size
  it was laid out at), `ContainerQuery` (a name and a condition) and
  `ContainerQueries` (its parser). `container-type`, `container-name` and the
  `container` shorthand are read. The package is exported.
- A query's condition reuses `MediaCondition`. `width`, `height`,
  `inline-size`, `block-size`, `aspect-ratio` and `orientation`, with their
  range forms, are answered against the container's content box.
  `ContainerQueries` rewrites the logical names to the physical ones, notes
  whether the block axis is read (which an `inline-size` container cannot
  answer), and marks anything else, a `style()` query or a media feature, as
  `Unsupported`. A strict sheet refuses that; a lenient one keeps the rule and
  never applies it.
- The parser carries the open `@container` blocks onto each rule
  (`StyleRule.containers`), nested blocks all at once. `@container` inside a
  style rule stays refused, as the other nested at-rules are.
- `StyleResolver` skips a contained rule whose queries do not all hold for the
  element: each asks the nearest ancestor that is a container for the axes it
  reads, with the name when one is written. With none, or with one not laid out
  yet, the query does not hold. `StyleResolver.readsContainers()` is true when
  a sheet has a contained rule or a container unit; the toolkit's own sheets
  have neither.
- `StyleElement.queryContainer()` is answered by `Element` from its resolved
  `Containment` and the content-box size `laidOutAs` recorded.
  `WidgetRenderer.settleContainers(render)` walks the laid-out boxes, records
  each container's content box (its border and padding taken off), and on a
  change throws away the styles below it (`invalidateStyleBelow`). It answers
  whether anything was restyled. `FrameSequence.layOut` and `Popup` then
  render and lay out once more.
- The container units `cqw`, `cqh`, `cqi`, `cqb`, `cqmin` and `cqmax` are
  resolved through `CssLength.Context.container`, which the renderer sets to the
  nearest container's content box. On an axis no container answers for, they
  fall back to the window's, as CSS falls back to the small viewport. `calc()`
  reads them through the same path.

## Consequences

- No containment is enforced. CSS makes a `size` container's size independent
  of its contents so that a query cannot change what it asks about. Here a
  container sized by its content can be resized by what its queries decide.
  The second layout shows that resize, and the frame after settles on it,
  rather than the frame looping.
- A first frame whose containers changed costs a second render and layout;
  every later frame with containers that kept their size costs one walk of
  the laid-out boxes.
- `HostedWindow`'s popup measuring pass does not run the second layout. It
  measures a popup's natural size once, as it did.

## Status

Done 2026-10-11: `css.container`, `Presence.containment`, the three
properties, `StyleRule.containers`, the parser's `container`,
`StyleResolver.readsContainers` and the containment check, `CssLength`'s
container units and `Context.container`, `Element.queryContainer`,
`laidOutAs` and `invalidateStyleBelow`, `WidgetRenderer.settleContainers`,
`FrameSequence`, `Popup`; `ContainerQueryTest`, `ContainerRestyleTest`,
`PropertiesTest`, the conformance rows.

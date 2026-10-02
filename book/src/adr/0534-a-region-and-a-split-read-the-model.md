# ADR-0534: A region and a split read the model

- **Status:** Accepted. Amends [ADR-0367](0367-a-document-places-a-list-it-cannot-describe.md).
- **Date:** 2026-10-02
- **Relates to:** [ADR-0063](0063-data-flows-down-events-flow-up.md),
  [ADR-0165](0165-a-divider-translates-and-a-rotation-has-three-brakes.md),
  `docs/goldberry-gaps.md` entry 21

## Context

ADR-0367 let a document place a `list`, a `table` or a `tree` that the model
builds, through `Bound`. Nothing else could be placed that way. Deploy Orc
needed regions whose content changes with the model: a detail pane, a toolbar
per mode. It built `Live`, which rebuilds on model paths, with a `register` per
region.

`split-pane` read `position` as a literal number. A split whose position
belongs to the model, restored from saved settings, had to be built in Java.
The application built both its split panes that way.

## Decision

**`slot bind="…"` places any widget the model holds. `split-pane` is
`Bindable`, and reads its position from the bound number.**

- `Bound` carries `@Markup("slot")`, and `slot` inflates to
  `new Bound(source, Widget.class, attributes)`. It is the same composition
  node as `list`, over `Widget` itself, so no new type and no new rebuild
  mechanism are needed. The element already subscribes to a widget's binding.
- `Bound` draws a widget that is not `Attributed` as it is. Before this, every
  type `Bound` was used for was `Attributed`. Now any widget may arrive, and one
  with nowhere to put the document's id and classes is still drawn.
- `SplitPane` gains a `source` component and implements `Bindable<SplitPane>`.
  `resolvedPosition()` is the bound number, clamped to `0..1`, when it is a
  finite number, and `position` otherwise. Markup reads `bind=`. The
  constructor without `source` is kept.
- **With `resize=`, the split is controlled.** The bound number is where the
  divider is, and a drag is reported through the action. This is `slider`'s
  arrangement: the model is written by the application, never by the widget
  (ADR-0063).
- **With `bind=` alone, the split owns its position and takes the model's when
  it changes.** The state remembers the last bound number it adopted. A
  rebuild with the same number leaves a drag alone, and a new number moves the
  divider there. So a saved layout restores, and a divider the user moved is
  not snapped back by an unrelated rebuild.

## Alternatives considered

- **Writing the bound value back when the user drags.** That would make
  `bind=` two-way for one widget, while every other `bind=` in the toolkit is
  read-only. `resize=` already names the way back, and a model that wants the
  number stored writes it there.
- **A bound split with no `resize=` that does not move**, as a bound slider
  with a handler that does nothing does not move. A divider that cannot be
  dragged is a broken layout, not a read-only value, and nothing in the
  document says it should be frozen.
- **A `slot` widget of its own** beside `Bound`. It would be the same record
  with a different name, and `WidgetParityTest` would exempt both for the same
  reason.

## Consequences

- `slot` is a markup name with no CSS type of its own, exempt from parity for
  `list`'s reason. The guide documents it under Collections, beside the three
  it generalises.
- ADR-0367's "the application model holds widgets for these three" now covers
  any region. The model holds a `Widget` and replaces it.
- The application's `Live` and its Java-built split panes can go.

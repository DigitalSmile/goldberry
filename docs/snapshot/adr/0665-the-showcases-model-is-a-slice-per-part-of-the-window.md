# ADR-0665: The showcase's model is a slice per part of the window

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0136 (an application is values, actions and views), ADR-0137 (a model keeps its fields), ADR-0139 (actions are annotated as actions), ADR-0110 (the showcase is a gallery of screens), ADR-0547 (a member reached by reflection says so to Qodana)

## Context

`ShowcaseModel` had grown to 1,192 lines: 44 `@Bind` fields and 56 action methods in
one class, from the window's history and the bar's readings to a colour
picker's hex and the Markdown screen's source. Every screen and card took the
whole of it, so `RoadCard` could reach the theme and `TrayCard` the tab strip,
and nothing in a signature said what a card read or wrote. A change to the
Forms screen's fields was a change to the class every screen depends on.

Nothing in the toolkit asked for one class. `Application.models()` is a list,
`Wiring.of` merges the registries of as many models as it is handed and refuses
a path or an action claimed twice, and the weaver rewrites a write to a `@Bind`
field wherever it is, so an action nested in any model notifies.

## Decision

**The showcase's state is eight `@Model` slices, each in its own package under
`dev.goldberry.example.model` with its `@Actions` record nested in it, and two
records hold them together for Java.**

| Package | Slice | What it holds |
|---|---|---|
| `model.shell` | `ShellModel` | the screen showing, Back and Forward, the reading size, the sidebar, the bar's readings, the HUD flag |
| `model.appearance` | `AppearanceModel` | the theme in its two spellings, the density, the scroll bars — every one a restyle |
| `model.controls` | `ControlsModel` | the road counter, the verse, the half-ticked box, the gain, the chips, the tags |
| `model.forms` | `FormsModel` | the Forms screen's fields, the signup form's controller and the port's rule |
| `model.pickers` | `PickersModel` | the departure's date and time, and the colour |
| `model.documents` | `DocumentsModel` | the Markdown and HTML sources, what their links reported, the shipped picture |
| `model.navigation` | `NavigationModel` | the chapters in the tab strip and the breadcrumbs' path |
| `model.sheets` | `SheetsModel` | the Icons and Emoji screens' queries |

- **Every path and action name is unchanged**, so no document changed. The
  slices publish disjoint names, which `Wiring.of` already enforces.
- **`ShowcaseModel` and `ShowcaseActions` are records of the slices.** They are
  not models; `slices()` on each is what `Showcase.models()` lists, values
  first, then actions, then `WindowActions`. `ShowcaseActions(ShowcaseModel)`
  builds every slice's actions over that model, and `write(model)` says
  whether a set of actions writes the given model's slices.
- **The players are not a slice.** Nothing about a player is bound, so
  `ShowcasePlayers` in `dev.goldberry.example.media` holds the two players and
  the Java PCM decoder, registers them as named objects and closes them.
- **Named objects are registered by the slice that owns them**:
  `FormsModel.register`, `DocumentsModel.register` and
  `ShowcasePlayers.register`, composed by `ShowcaseModel.named()`.
- **A card takes the slice it uses.** `RoadCard` and `TagsCard` take
  `ControlsModel`; `TrayCard`, `ViewportCard`, `ThemeCards` and `DesignCards`
  `AppearanceModel`; `TabsCard` and `TrailCard` `NavigationModel`; the two
  sheets `SheetsModel`. The shell (`Screen`, `AppMenu`, `GalleryContext`)
  keeps the aggregates, because it touches several slices.
- **Each slice package is opened to `dev.goldberry.core`** in `module-info`,
  for the reflective binder, as the old package was.
- `Showcase` exposes `model()` and `actions()`, which the test fixtures read
  instead of scanning `models()` for one model of a type.

## Consequences

- A card's signature says which slice it reads; a change to one slice
  recompiles the cards that use it rather than every screen.
- `Models.observable`, `Models.refresh` and `Models.actions` take `Object`, so
  handing them the aggregate instead of the slice compiles and fails at run
  time. Every call names the slice; `ShowcaseModelTest` asserts each path is on
  the slice the split put it on, and the test fixture `ShowcaseWiring` resolves
  a path across the slices the way the window's merged registry does.
- `ShowcaseModelTest` also holds the shape: every `@Model` component is listed
  by `slices()`, every slice's actions are nested in it and listed in the same
  order, no two slices share a name, and every slice's package is opened to
  `dev.goldberry.core` in `module-info.java` — the one failure the class-path
  suite could not otherwise see.
- `Screen` subscribes to three paths on the shell and one on the appearance
  rather than four on one model.
- A new slice is a package, a class with a nested `Actions` record, a component
  on both records and an `opens` line; the test fails on a missed one.

# ADR-0532: An icon is the size of its box

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** [ADR-0347](0347-an-icon-only-button-is-a-circle-and-float-is-a-place.md),
  [ADR-0390](0390-a-turned-shape-is-a-path-and-the-frame-can-compose.md),
  `docs/goldberry-gaps.md` entry 19

## Context

Icons were drawn only inside `button`, `chip`, `tab` and the other controls
with an icon slot. An `Icon` is a path built at one size, and a control's slot
draws it at that size, so a stylesheet could not size one. Every icon in Deploy
Orc was 16 px where its prototype used 11 to 18. The application wrote its own
`Glyph` widget for a standalone icon.

`Icon.bundled` throws `NoSuchElementException` for a name the set does not
have. That is right for a name in the source and wrong for one that comes from
a server or a configuration file. The application caught `RuntimeException`
around it and drew a dot instead.

## Decision

**`icon` is a widget whose box is sized by the stylesheet. The outline is drawn
under a scale that fits it to the box. `Icon.find` looks a name up without
throwing.**

- `Icon.find(name, size)` returns `Optional<Icon>`. A size that is not positive
  and finite is still refused, because that is a mistake in the program.
- `IconView`, in a new package `dev.goldberry.widgets.core.icon`, is `@Markup("icon")`.
  Its argument is the icon's name. It is looked up in the application's `Icons`
  registry first, through the new `Icons.find`, whatever the registry's
  strictness, and then in the bundled set at Lucide's 24-unit grid. Java takes
  either a name or any `Icon`.
- The box is `width` and `height`, 16 by default in `controls.css`. The painter
  scales the outline to the smaller side and centres it along the other, with
  `Frame.concat`. The stroke scales with the outline, which is how Lucide is
  drawn, so an 11 px icon is the 24 px drawing, thinner. The colour is the
  box's `color`.
- A name that neither the registry nor the set has is an empty box of the same
  size with the class `missing`. It is not an error, because a document being
  edited is reloaded on every keystroke. The stylesheet may draw a fallback on
  `icon.missing`.
- An icon with no `name=` is decorative and has no semantics. One with a name
  is a `FIGURE` with that name. These are two parts, as for `image`, because
  having semantics is a question about the type.

Nothing native is held. `Icon` has been a `Path` value since it stopped owning
a `BlendPath`, and the frame turns it into the rasterizer's path for one call.
So the widget needs no state and no lifecycle, and markup can build one. Markup
can still not build an `Icon` for a `button`'s `icon=` attribute. That
registry is unchanged.

## Alternatives considered

- **Rebuilding the path at the box's size on every paint.** It gives the same
  pixels for more work, and the box's size is only known at paint time.
- **A per-size cache of icons.** It is not needed once the transform does the
  scaling, and a cache keyed by size grows with every size a resize passes
  through.
- **Throwing from markup for an unknown name when the registry is strict.**
  Strictness is about names the application must register. The bundled set
  needs no registration, and a name that is in neither is the application's to
  show or not.

## Consequences

- The application's `Glyph` and the `try`/`catch` around `Icon.bundled` can go.
- `Icons.SLOT` is still the size of the catalog's fixed slots. Its doc comment
  no longer claims that an icon cannot be rescaled.
- `icon` is a new markup name. The guide documents it under Canvas, images and
  QR codes.

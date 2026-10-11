# ADR-0610: A stylesheet may ship a font face

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-31, CSS-32), ADR-0595,
  `docs/css-support-2026-10-10.md` §5

## Context

An application ships a face in Java: `Application.fonts()` answers a list of
`FontSource`s, read once when the window's book of fonts opens. A theme written
in CSS, the shape every downloadable theme and every design system's web
export has, names its faces with `@font-face`, which the parser refused with
the rest of the at-rules it did not have. The plan put it after `@import`,
because both need a sheet to know where it was read from.

The book of fonts is built before any renderer and was immutable after. A
theme swap builds a new renderer over the same book.

## Decision

**`@font-face` is read into the sheet, and a renderer built over a book of
fonts adds the sheet's faces to it, as if the application had shipped them.**

- The parser reads `font-family`, `src`, `font-weight` and `font-style` into a
  `css.FontFaceRule`, kept on `Stylesheet.fontFaces()`. `src` takes the first
  `url()` the toolkit reads: no `format()`, or `truetype`, `opentype` and their
  `-variations` forms. `local()` and WOFF are passed over, and a face with
  nothing else is refused as outside the subset. A weight range takes its first
  number; `oblique` is refused, as `font-style: oblique` is. `unicode-range`,
  `font-display` and the metric overrides are read and set aside.
- A sheet read as a resource resolves `src` beside itself, as its `url()`s and
  its `@import`s are, to a `classpath:` address; one read from text keeps
  what it says, a `classpath:` address or a file path. `FontSource.address`
  opens either.
- `WidgetRenderer`, built over a `Fonts` book, calls `Fonts.register` for each
  face before anything is shaped. A face whose family, weight and style the
  book already has is not added, so a theme swap that registers the same faces
  again changes nothing. A face whose file is not there is kept, drawn in the
  UI face and reported once, as a shipped one is.
- `@font-face` inside `@media` is outside the subset.

## Consequences

- The book of fonts is no longer fixed at start-up: `Fonts.shipped()` grows,
  copy on write, on the thread that draws with the book.
- A face is never taken back. A theme that ships a face and is swapped out
  leaves it registered, and harmless: nothing asks for its family any more.
- `unicode-range` is the next piece, through the fallback faces of ADR-0595.
  Until then a face is used for every character its `cmap` has.

## Status

Done 2026-10-11: `FontFaceRule`, the parser's `fontFace`, `Stylesheet.fontFaces`
and its anchoring, `FontSource.address`, `Fonts.register`, the renderer's
registration, and tests (`FontFaceTest`, the conformance table).

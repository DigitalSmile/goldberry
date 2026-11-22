# ADR-0744: `svg-view` keeps a document's bytes, and draws it in its CSS `color`

- **Status:** Accepted
- **Date:** 2026-11-23
- **Relates to:** ADR-0740 (D3, `currentColor` from CSS), ADR-0742 (`Svg`),
  ADR-0743 (the frame draws an SVG image at the device pixels it covers)

## Context

Phase 4 of `docs/vector-svg.md` is `svg-view`: an SVG document as a widget,
given as a source or inline, with `fit`, `alt` and `decorative` as `image` has
them, its loading and error states, and `currentColor` taken from the view's CSS
`color` (D3). An `image` of the same file already draws sharp since ADR-0743,
with `currentColor` black. What `svg-view` adds is the colour, and the colour is
only known when the view is rendered, from its computed style, which a `:hover`
rule or a theme switch changes without the view's source changing.

## Decision

1. **`dev.goldberry.vector.view`**, exported: `SvgView` (`@Markup("svg-view")`),
   `SvgSource` (a file, a resource, text, bytes; `classpath:` in markup) and
   `VectorStyles`, whose `vector.css` gives `svg-view` `image`'s loading and
   error looks. `:vector` applies the weave plugin, so the catalog is generated
   and found as `:media`'s is.
2. **A view keeps the document's bytes**, read and checked on a virtual thread
   (parsed once in black, for its natural size and to know it is a document).
   At render, the document is drawn in the style's `color`: the bytes parsed
   again with it, through `Svg.parse(bytes, argb)`, and remembered by document
   and colour (128 drawings for the process). A theme has a handful of colours
   a view is drawn in, and an icon parses in a fraction of a millisecond.
3. **The drawing is an `Image` that carries its `Svg`**, placed by `Fit` as
   `image` places a picture, so the frame draws it at the device pixels it
   covers (ADR-0743). A new colour is a new image, so the painter differs and
   the box is drawn again.
4. **Markup**: `src=` a path, or the document written as the node's argument,
   exactly one; `alt=` or `decorative=#true`, as `image` requires; `fit=`;
   children refused.
5. **`image`'s sizing is `IntrinsicSize`**, public in
   `dev.goldberry.widgets.core.image`: the natural size, one axis following the
   other, a percentage width with the height a frame late. `image` and
   `svg-view` both size themselves by it.
6. **The failure parts are `svg-view-icon` and `svg-view-alt`**, types of their
   own styled by `vector.css`, rather than `image`'s.

## Consequences

- `svg-view` differs from `image` in one thing a reader can see:
  `currentColor`. An application that wants a document's own colours whatever
  the theme can use either.
- A document is held twice while a view shows it: its bytes in the view, and
  ThorVG's copy in each colour's `Svg`. For an icon this is a few kilobytes.
- `vector.css` is added by the application, as `media.css` is.
- A colour first seen at render parses on the UI thread. For a large document
  drawn in many colours that is a cost a frame pays once per colour.

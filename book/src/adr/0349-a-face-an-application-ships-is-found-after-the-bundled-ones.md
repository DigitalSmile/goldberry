# 349. A face an application ships is found after the bundled ones

Date: 2026-09-17

## Status

Accepted. Closes `docs/gaps.md` G39. Extends
[ADR-0066](0066-a-weight-is-a-face-and-color-inherits.md) and
[ADR-0323](0323-an-italic-is-a-face-and-the-matrix-closes.md).

## Context

An application's landing page sets display text in Forum and running text in
Golos Text. Its desktop client should use the same two faces. A stylesheet could
name only the faces `BundledFont` enumerates, so `font-family: Forum` resolved to
nothing and the title was drawn in Inter.

A canvas could open the bytes with `Font.of(byte[], size)` and draw a title
itself. That title would not select, wrap or follow `font-size` like every other
label, which makes it a second text stack. A face has to reach four places
together: the cascade, `Paragraph` layout, the text-input caret and the glyph
cache. All four already go through one `Fonts` book, so the book is where a face
has to be added.

## Decision

**`FontSource` describes a shipped face. `Application.fonts()` lists them, and
`Fonts.bundled(List<FontSource>)` searches them after the bundled faces.**

- **`assets.Face`** is a sealed interface over `BundledFont` and `FontSource`: a
  family, one of the two weights, and upright or italic. `Face.match` is the
  matching rule `BundledFont.of` already had (family, then style, then weight,
  then the family's upright regular), moved so that both kinds share it. With
  two copies, a shipped family would fall back differently from Inter the first
  time one of them changed.
- **The matrix stays closed.** A shipped face is one of the two weights and one
  of the two styles. §1.4 ships two weights, and Principle 3 applies to an
  application's faces too.
- **Bundled first.** `Fonts.of(Typography)` asks `BundledFont.of` and only then
  the shipped list, so a file an application calls `Inter` is never drawn. It is
  logged when the book is opened, rather than refused, because the stylesheet
  still gets the face it named. Two sources for the same corner *are* refused,
  since one of them could never be drawn and nothing could say which.
- **Lazy, and forgiving at draw time.** The bytes are a `Supplier<byte[]>`, read
  the first time the face is asked for. `FontSource.resource(…)` reads a
  resource beside a class, the way `Stylesheet.resource` does. A source that
  cannot be read or parsed is logged once, remembered as unusable and drawn in
  the UI face. That happens inside a render pass, where a thrown exception would
  mean a window with no text.
- **Read once.** The launcher opens its book with `application.fonts()` before
  `start`, like the stylesheets. `Offscreen.fonts(List<FontSource>)` does the
  same for a render, so a render test paints what the window paints.

## Consequences

- `font-family: Forum` works everywhere a bundled family does: in labels, fields,
  the caret, `canvas` painters (through `CanvasStyle.font()`), `markdown-view` and
  offscreen renders.
- `BundledFont.of` keeps its signature and its answers. `BundledFontTest`,
  `ItalicFaceTest` and `FontsTest` pass unchanged.
- There is no markup form. Faces belong to the application, like its
  stylesheets.
- Font licences are the application's to ship. The toolkit's
  `THIRD-PARTY-NOTICES.md` covers only what the toolkit bundles.

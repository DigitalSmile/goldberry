# Closing G44–G49

Working notes for the eighth batch of `docs/gaps.md` entries, which arrived on
2026-09-18 from a notes application and a chat client. One ADR per entry, in
`book/src/adr/`.

This batch is unlike the seven before it in one way worth naming: **two of the
six are not missing features.** G44 and G45 are measurements — a style pass and
a layout pass that grow with the size of a document rather than with the size of
the change — and what they ask for is not an API but a cost.

Status legend: **done** means the code, tests and ADR have landed.
**in progress** means it is being built now. **open** means not started.

| Item | What it asks for | ADR | Status |
|------|------------------|-----|--------|
| G44  | a `text-area` style pass that does not grow with its text | — | in progress |
| G45  | a `markdown-view` that restyles only the block that changed | — | in progress |
| G46  | a canvas transform that composes with the one it is painted under | [0390](../book/src/adr/0390-a-turned-shape-is-a-path-and-the-frame-can-compose.md) | done |
| G47  | a `qr-code` widget | — | in progress |
| G48  | a viewport that opens at its end and stays put | [0392](../book/src/adr/0392-a-timeline-opens-at-its-end-and-keeps-the-readers-line.md) | done |
| G49  | emoji that are pictures rather than boxes | [0393](../book/src/adr/0393-an-emoji-is-routed-by-the-text-and-drawn-in-layers.md) | done |

## What each one touched

### G49 — emoji, routed and in colour

Two halves, and either one alone would have left boxes on screen: nothing split
the text, and the face that shipped had no colour in it.

- `core` `text/itemize/`: `Itemizer`, `TextRun`, `Slot`. UTS #51's rules through
  `java.lang.Character` — `isEmoji`, `isEmojiPresentation`, `isEmojiModifier` —
  plus the *sequence* rules a per-character split gets wrong: U+FE0F and U+FE0E,
  ZWJ families, skin tones, tag sequences, keycaps, and the `#` of `🎉#ship`
  that must not be swallowed.
- `core` `text/font/sfnt/`: `TableDirectory` and `ColorLayers` — `COLR` version 0
  and `CPAL`, read in Java for `FaceCoverage`'s reason. `COLR` version 1 is read
  as version 0, which draws its gradients flat.
- `core` `paint/GlyphFace`: reads the colour table once per **typeface**, not per
  size. `paint/GlyphPen`: one branch, and a face with no colour in it takes
  exactly the path it always took.
- `core` `text/font/Font#emoji(Font)` and `Fonts`, which opens the emoji face
  lazily at each size and joins it to every font it hands out. The trap recorded
  in the code: the emoji font is an entry in the same map, so it is opened
  *before* the map is written to.
- `core` `text/Paragraph`: segments. One measurement over up to two shapings,
  with the emoji face's advances rescaled into the base font's design units —
  Inter is 2048 to the em and OpenMoji is 1000, and a prefix sum needs one unit.
- `assets` `Asset.OPENMOJI` and `emoji` `OpenMojiFont`: the COLRv0 build,
  2.5 MB against the monochrome build's 1.4 MB.
- `example` `ui/EmojiScreen`: a line of prose with emoji in it, in **no named
  font**, which is the thing an application actually writes.
- Tests: `core` `text/itemize/ItemizerTest` (16 sequence cases),
  `core` `text/font/sfnt/ColorLayersTest` (fonts assembled in the test, including
  the malformed ones), `emoji` `ColourEmojiTest` — which asserts on **hues** and
  not on colours, because black text on white produces a dozen distinct greys and
  not one of them is a hue.

### G46 — a turned shape

- `core` `paint/geom/Transformer` and `Path.transformed(Affine)` with
  `rotated`/`translated`/`scaled`. `css.value.Affine` is reused rather than
  duplicated: a second matrix type would have to agree with the one hit testing
  already inverts.
- `ArcTo` is **decomposed**, not adjusted: radii and angle come back from a
  closed-form 2×2 SVD, and `sweep` flips under a mirroring matrix.
- `core` `paint/Frame#concat`: the frame mirrors its own logical matrix in Java
  and assigns through the `ASSIGN` op already bound, so no native symbol and no
  new enumerator were needed. `BL_TRANSFORM_OP_TRANSFORM` would have meant
  editing the shim and rebuilding the library.
- `example` `motion/Rotated` **deleted**, and with it a defect: it was only
  correct for pure rotations, so a scale or a mirror through it produced a
  plausible wrong shape.
- Tests: `core` `paint/geom/TransformerTest` (18), `core` `paint/FrameConcatTest`
  (6, on pixels).

### G48 — a timeline

- `core` `input/handler/Anchored`, the **fourth geometry facility** after `Extent`, `Measured` and
  `Located`, with `PointerRouter.notifyAnchored()` as a third walk. The first three report a property
  of one frame; this one reports a property of a *pair*, which is the only way to tell twelve lines
  added above from twelve added below. It remembers where the reader's first whole row sits **inside
  the content box**, in layout coordinates, so the scroll transform cancels and growth at the bottom
  reports exactly zero.
- `widgets` `core/scroll`: `ScrollAnchor`, `ScrollStick` (half a logical pixel, the figure
  `ScrollController.Position` already used), `ScrollState.shiftBy` — which bypasses the glide, because a
  240 ms slide per logged line is not what a console wants.
- `core` `kdl/KdlNode#flagProperty`: `preserve-on-prepend` is three states, not two, because its default
  depends on `anchor`. `booleanProperty` folds absent into false, which is the wrong answer here.
- It needs **keyed** rows, and that is written down rather than hidden: children matched by position are
  not the same node after a prepend.
- Tests: `widgets` `ScrollTimelineTest` (17), `example` `ConsoleScreenTest` (4). The rows in the test are
  a whole number of pixels tall on purpose — Yoga snaps positions to the pixel grid, so the anchor is
  exact whatever the heights are but a neighbouring row can land a rounded pixel away.
- Showcase: a `Console` card on the Navigation screen, and `gallery-navigation.png` re-blessed.

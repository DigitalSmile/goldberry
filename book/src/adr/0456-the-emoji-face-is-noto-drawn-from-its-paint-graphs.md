# 456. The emoji face is Noto, drawn from its paint graphs

Date: 2026-09-23

## Status

Accepted. Supersedes the choice of face in
[ADR-0384](0384-the-emoji-face-is-an-artifact-an-application-opts-into.md) and
the choice of format in
[ADR-0393](0393-an-emoji-is-routed-by-the-text-and-drawn-in-layers.md); the
artifact boundary of the first and the itemization of the second stand.

## Context

`goldberry-emoji` shipped OpenMoji's COLRv0 build: flat-coloured layers, drawn
a layer at a time. The request was to switch to **Noto Color Emoji** — the face
Android and ChromeOS draw, whose pictures are the ones most readers expect — and
to keep it in colour.

Google publishes Noto Color Emoji two ways, and the choice between them is the
one ADR-0393 made for OpenMoji, with different numbers:

| Build | Format | Size | At 200% |
|---|---|---|---|
| `NotoColorEmoji.ttf` | `CBDT` — 136 px PNG strikes | 10.7 MB | scaled bitmaps, soft |
| `Noto-COLRv1.ttf` | `COLR` v1 — paint graphs of outlines | 5.0 MB | outlines, sharp |

The bitmap build needs a PNG decoder in the font pipeline and blurs above the one
size it was drawn at, on a toolkit whose claim is that it is crisp at every
scale (`docs/ARCHITECTURE.md` §6.2 already rejected it once). The COLRv1 build is
half the size and is outlines — but it is **not** a list of flat layers. Nothing
the toolkit had could draw it: `ColorLayers` reads version 0 records and Noto
ships none, so every emoji would have come out as its bare base outline.

What version 1 is, measured over the face (3,993 colour glyphs, 72,825 shared
layers):

- `PaintGlyph` — fill *inside* an outline — 66,000 times;
- its fill is a solid 58,600 times, a **linear gradient** 3,000, a **radial
  gradient** 4,900, and a gradient under a **transform** of its own 4,200;
- **transforms** wrap glyphs 12,400 times — affine, translate, scale about a
  centre;
- **`PaintComposite`** 567 times, every one of them a flag: the stripes, masked
  `SRC_IN`, with a shading ramp laid over them `SOFT_LIGHT` so the flag waves.

## Decision

### Read the graph in Java, as a sealed tree

`text.font.sfnt` gains `ColorPaints`, the `COLR` version 1 reader, and
`ColorPaint`, the tree it produces — a **sealed interface of records**, so the
painter is an exhaustive `switch` and a node added later stops it compiling
rather than falling through a default that draws nothing.

What is folded at read time, so the painter has fewer cases:

- **Eleven transform formats become one** `Transform` matrix — translate,
  scale, rotate, skew, each with and without a centre.
- **Variable formats read as their defaults.** Every `PaintVar…` is its static
  twin plus a variation index, and the toolkit draws the default instance.
- **Palette indices become colours**, keeping `0xFFFF` — "the text's colour" —
  as a flag.

The **index** — base glyphs, layer offsets, clip boxes, palette — is read when
the face is. A glyph's **graph** is parsed on first use and kept, and layers are
cached by index, because the same eyes recur across a dozen faces. Parsing all
3,993 graphs up front would be work and memory spent on emoji nobody sent;
`ColourEmojiTest.everyGraphReads` parses them all anyway — the whole test class
runs in well under a second — to prove none is malformed.

A malformed graph — an unknown format, an index past a list, a cycle deeper
than 64 — answers null, and the pen draws the glyph as its own outline. That is
`ColorLayers`' rule and it stays.

### Outlines are read too, because a glyph is now a clip

`PaintGlyph` fills *inside* an outline with a paint that may be a gradient, and
the rasterizer's glyph call fills with a colour and nothing else. So the painter
needs the outline as a path, and `GlyphOutlines` reads `glyf` — simple glyphs
with their implied on-curve midpoints, and composites placed by offset and
matrix. It sends to an `OutlineSink` rather than returning a `Path`, because
`Path` is in `paint` and `paint` reads `sfnt`. It is all-or-nothing: a glyph
whose data runs off the table sends nothing, not half a shape.

`GlyphFace` reads outlines only for a face with paint graphs, and caches each
glyph's `Path`. A face of letters never builds one.

### A clip is a fill, and a transform under a glyph is the gradient's

`ColourGlyphPainter` concatenates one matrix — `size / unitsPerEm`, y flipped,
at the glyph's origin — and draws the graph in design units.

The rasterizer has no path clip, and the shipped face does not need one:
**filling an outline with a brush is the same picture as clipping the brush to
the outline**. So `PaintGlyph` over a solid or a gradient is one `fillPath`. A
transform *between* the glyph and its gradient — 4,200 of them — must move the
ramp and not the outline, so it becomes the **gradient's own matrix**, which
`bl_gradient_init_as` already took and nothing had passed.

The one case that is a real clip — a glyph whose fill is itself a picture — is
drawn as the composite it means: the picture offscreen, kept `SRC_IN` to the
outline.

### Composites are two small layers

`PaintComposite` blends two finished pictures, so both are rendered into
`Layer`s sized to the glyph's clip box on the device, snapped outwards to whole
pixels, and the source is blitted onto the backdrop with the font's operator.
Noto's flags nest one composite inside another; the inner one is drawn onto the
outer one's layer, so the offscreen area travels with the surface being drawn
on rather than being fixed to the frame.

### The native layer grows constants, not symbols

Everything needed was already exported: `bl_gradient_init_as` builds radial and
conic gradients from a different `values` struct, and `bl_context_set_comp_op`
takes any operator. What was missing is what the layout verifier guards:

- **`BLRadialGradientValues` and `BLConicGradientValues`** rows, every field
  named — Blend2D's radial puts the *focal* circle, where the first stop sits,
  second, and a COLRv1 radial puts it first. Swapped, both render.
- **Every `BLCompOp` a font may name** — twenty-two constants, not the two the
  face uses, because a font is data the toolkit does not choose. The four HSL
  modes have no Blend2D operator and draw as source-over.

`BlendGradient` gains `radial` and `conic` and an extend mode and a
`BlendMatrix`; `BlendContext` gains `compOp`. No export list changed.

### The asset is a file, not an archive

Noto's release has no assets, and its source zip is the whole repository —
hundreds of megabytes of PNGs for one 5 MB font. `Asset` gains a `Packaging`:
`FILE` pins the font itself, by the tag in its URL and by its checksum, which is
the promise an archive's checksum made.

### The licence, and why the artifact stays

Noto Color Emoji is **SIL OFL 1.1** — the licence Inter and JetBrains Mono are
under. It asks for the licence text to travel with the font and for a modified
font not to be called Noto; it does not ask for credit on screen, which is what
put OpenMoji in an artifact of its own. The artifact stays for ADR-0384's other
reason: five megabytes is a lot to inherit for an application that never draws
an emoji. `NotoColorEmojiFont.CREDIT` remains for an About box that wants it.

## Consequences

**Emoji are Noto's**, shaded and sharp at any scale, in every `Paragraph` drawn
through a `Fonts` book, exactly as ADR-0393 routed them.

**A colour glyph costs more than it did.** A Noto glyph is a dozen filled
outlines where an OpenMoji glyph was fourteen flat layers — comparable — but a
gradient fill builds a native gradient per fill, and a flag allocates two small
offscreen layers. None of it has been benchmarked yet; a wall of flags is the
case to measure first if it ever shows. Caching a rasterized glyph
per size is the optimisation, and it costs the crispness under a transform that
this decision is for, so it is not taken now.

**Written down rather than discovered:**

- A sweep gradient's stops outside one turn are clamped into it, and a sweep
  that repeats or reflects is drawn padded. Noto uses no sweeps.
- A radial gradient whose stops run past a circle of zero radius is clamped at
  zero.
- `PaintColrGlyph` draws the glyph it names without that glyph's own clip box,
  and stops after sixteen references.
- A composite glyph placed by matching points is placed at its origin. No colour
  face uses the form.
- CFF outlines are not read; a COLRv1 face with a `CFF` table draws its glyphs
  as outlines.

**The version 0 reader stays**, for any face that is still version 0, and the
pen asks the graph first where a face carries both.

**An emoji is as tall as Noto makes it.** Noto ascends 950 and descends 250 in
1024 units, so at 14 px an emoji reaches 13.0 above the baseline against
Inter's 13.6 — it sits inside the line box now rather than three-quarters of a
pixel over it, which retires one consequence ADR-0393 recorded.

# ADR-0034: One size, and the design-unit crossing

- **Status:** Accepted
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §6, [ADR-0010](0010-hand-written-ffm-bindings.md), [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md), [ADR-0032](0032-shaping-is-utf16-in-glyphs-out.md), [ADR-0033](0033-assets-are-fetched-and-compiled-not-committed.md)

## Context

All three engines of the M1 slice were bound and none of them had ever met.
Yoga lays out ([ADR-0029](0029-yogas-node-api-and-who-owns-a-node.md)), Blend2D
fills rectangles ([ADR-0031](0031-blend2d-and-the-borrowed-buffer.md)),
HarfBuzz shapes ([ADR-0032](0032-shaping-is-utf16-in-glyphs-out.md)), and real
fonts are in the jar ([ADR-0033](0033-assets-are-fetched-and-compiled-not-committed.md)).
Shaping produced a `GlyphRun` that went nowhere. Nothing had drawn a glyph.

Getting one on screen needs Blend2D's font chain bound — data, face, font — and
`bl_context_fill_glyph_run_d_rgba32`. That much is mechanical. What is not
mechanical is the question the crossing forces, and it has exactly one right
answer:

**In what units are a glyph's positions?**

Both libraries have an opinion, and they are different opinions about the same
four numbers.

HarfBuzz reports advances and offsets in whatever its font's *scale* was set to.
Set no scale and it reports **font design units** — the em grid the outlines were
drawn on, 2048 for Inter. Set the scale to `size * 64` and it reports 26.6 fixed
point, which is the convention every FreeType example uses and which
[ADR-0032](0032-shaping-is-utf16-in-glyphs-out.md) named as the usual choice.

Blend2D reads a `BLGlyphRun` whose `placement_type` decides how it transforms
those numbers. For `BL_GLYPH_PLACEMENT_TYPE_ADVANCE_OFFSET` — the one whose
memory layout matches what HarfBuzz already produces — it multiplies every
placement by the **font matrix**, which is `size / units-per-em`. It has to: a
`BLFont` is a face at a size, and applying the size is what it is for.

So if the shaper has also applied a size, the size is applied twice. For Inter at
16 points that is a factor of `2048 / 16` — **128×**. The text is drawn, it is
simply eight thousand pixels wide and off the edge of the window. Get it wrong
the other way, hand Blend2D pixel-space numbers and tell it they are user units,
and the run collapses into one illegible pile. Neither reports an error. Both
paths through `bl_context_fill_glyph_run_*` return `BL_SUCCESS`.

This is the shape of mistake this codebase has now met several times — a wrong
enum, a wrong stride, a doubly-premultiplied colour — where the wrong answer
renders. It differs in one respect: it is not a fact about the compiled library
that the layout table could check. It is a *convention between two libraries*,
and nothing in either of them can be asked about it.

## Decision

**Shape in design units; put the size on the Blend2D font alone.**

The shaping font is left at HarfBuzz's default scale, `ShapedFont.UNSCALED`, so a
`GlyphRun` is always in font design units. The `BlendFont` carries the size. One
size, in one place, and the font matrix is the only thing that converts.

**`Font` in `:core` owns both and is what maintains the invariant.** It builds a
`ShapedFont` and a `BlendFont` over the same bytes, never scales the first, and
is the only thing that hands one's output to the other. A caller wiring the two
together by hand can still get it wrong; a caller using `Font` cannot.

**The four numbers cross as a copy, not a conversion.** `BLGlyphPlacement` is an
offset and an advance as two `BLPointI` — four `int32`s, in exactly the order and
width `hb_glyph_position_t` reports them. So `Font.draw` copies them straight
across with no arithmetic in between, and `ADVANCE_OFFSET` is chosen precisely
because it is the placement type that needs none.

**`BlendGlyphBuffer` stages them and is reused.** A `BLGlyphRun` is a descriptor
— two pointers into memory the caller owns, plus each array's stride — so the
glyph ids, the placements and the descriptor all live in one arena and cannot
outlive each other. It grows and never shrinks, because a frame draws hundreds of
runs and a paragraph reshapes at every width a layout pass proposes.

**Java converts design units to logical ones itself, in `Font.widthOf`,** by the
same `size / units-per-em`. That is not duplication of Blend2D's matrix: it is
how a measure function answers "how wide is this?" without asking the rasterizer
to draw it first — which is exactly what Yoga will call.

**The origin is the baseline, and it is a `BLPoint` of doubles.** Only the `_d`
variant of the fill is bound; the `_i` one takes integer coordinates, and
rounding a baseline is the thing
[ADR-0031](0031-blend2d-and-the-borrowed-buffer.md) went to some trouble to stop
doing for rectangles. At 1.5× it would quantise line spacing.

**`ShapedFont` gained `unitsPerEm()` and `isDesignUnits()`.** The invariant is
now something an object can be asked about rather than only something a document
asserts. `hb_face_get_upem` is bound for it.

## Alternatives considered

**Shape at `size * 64` and use `BL_GLYPH_PLACEMENT_TYPE_USER_UNITS`.** The
FreeType-shaped option, and it works: convert the 26.6 fixed point to doubles,
accumulate the pen in Java, and hand Blend2D absolute positions. It costs a
conversion and an accumulation per glyph on the hottest path, it makes every
shaping result specific to one size — so a paragraph cache would need an entry
per size — and it moves pen accumulation, which is subtle for marks and
right-to-left runs, out of the library that knows how to do it and into ours.

**Shape at the size and let Blend2D's font be at `units-per-em`,** so the matrix
is the identity. Symmetrical, and it inverts the problem rather than solving it:
metrics from `bl_font_get_metrics` would then come back in design units and every
baseline calculation would need the same conversion, in more places.

**Bind `bl_font_shape` and drop HarfBuzz for text.** Blend2D has its own shaper.
It is not HarfBuzz — no Indic reordering worth the name, thinner OpenType
coverage — and [ADR-0002](0002-cpu-rasterization-with-blend2d.md) chose Blend2D
as a rasterizer, not as a text stack. Taking its shaper would quietly narrow
which scripts the toolkit can claim to support.

**Have `BlendContext.fillGlyphRun` take a HarfBuzz `GlyphRun` directly.** Fewer
types and a shorter path. It also makes the Blend2D binding package depend on the
HarfBuzz one, welding together the two halves that
`docs/ARCHITECTURE.md` §6 keeps apart — and the first thing that would break is
substituting a different shaper.

**Let `Font` scale the shaper and document the hazard.** Cheaper to write. The
hazard is invisible at runtime, so documentation is the one mitigation that
cannot fail loudly.

## Consequences

**A glyph reaches the screen.** The showcase draws two lines of Inter, and
`TextPaintTest` asserts where the ink landed rather than that there is some:
the inked span matches `Font.widthOf` to within a few pixels, which is an
assertion that fails by a factor of 128 if either side of the crossing is wrong.

**A shaping result is size-independent.** The same `GlyphRun` is correct at every
size, which is what the paragraph cache will want when it arrives, and what
`FontTest.shapingIsSizeIndependent` pins down.

**The ABI version is now 8**, and the export list has 148 entries. Four new
struct layouts are registered — `BLPoint`, `BLGlyphRun`, `BLGlyphPlacement`,
`BLFontMetrics` — plus five `BL_GLYPH_PLACEMENT_TYPE_*` constants. `BLGlyphRun`
is the layout row carrying the most weight so far: Java writes every field of it,
and a wrong `size` offset would have Blend2D read a byte count as a glyph count.

**A `Font` costs two copies of the font file.** HarfBuzz copies the bytes and
Blend2D is pointed at a second copy this class owns, because each library holds
its own. That is about a megabyte and a half per `Font` for Inter, and a `Font`
per size — so the showcase's two sizes cost three megabytes of outlines. A shared
face cache is the fix and is not built; nothing above depends on its absence.

**Nothing measures text for layout yet.** `Font.widthOf` is the number a Yoga
measure function would report, and no measure function calls it: the paragraph —
shaping at the width Yoga proposes, breaking lines, reporting a height — is still
the missing piece, and it is now the only one. The showcase places its two lines
against hand-written constants because the layout tree carries no text.

**Line breaking, bidi and font fallback are untouched.** A `GlyphRun` is one run
of one direction in one face. Splitting mixed-direction text into runs, choosing
between the UI and emoji slots per character, and breaking a line at a legal
opportunity are all still ahead, and none of them is HarfBuzz's job.

**Icons still do not draw.** The Lucide table holds SVG path data and Blend2D's
path API — `bl_path_*` and `bl_context_fill_path_*` — is not bound. It was scoped
out deliberately: it shares nothing with the font chain except the context, and
bundling the two would have made the units question above harder to see.

**Deliberately not bound.** `bl_font_get_design_metrics` and
`bl_font_face_get_design_metrics` — the design-unit metrics, which nothing needs
now that the em grid comes from `hb_face_get_upem`; `bl_font_shape` and Blend2D's
own glyph buffer; `bl_font_get_glyph_outlines`, which is how a glyph becomes a
path rather than ink; the stroke and `_ext` style variants of the glyph-run fill;
and `bl_font_create_from_face_with_settings`, which is where variable-font axes
and font features will arrive together.

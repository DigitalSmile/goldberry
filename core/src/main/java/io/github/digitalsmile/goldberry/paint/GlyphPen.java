package io.github.digitalsmile.goldberry.paint;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.natives.blend2d.BlendFont;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendFontMetrics;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendGlyphBuffer;
import io.github.digitalsmile.goldberry.text.ShapedRun;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorLayers;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorPaints;

/// A [GlyphFace] at one size, and the thing that actually puts glyphs on a
/// [Frame] — `docs/gaps.md` G14.
///
/// ## Why it is here and not in `text.font`
///
/// Rasterizing a glyph needs three things: a context, a font and a staged buffer.
/// `paint` owned the first and `text.font` owned the other two, so the only way
/// to join them was a public `Frame.drawGlyphs` taking two `:natives` types —
/// the last leak of the module boundary, in one method, for years (ADR-0290).
///
/// Moving the pen here closes it. `text.font` keeps what it is actually about —
/// shaping, metrics, the fallback chain, the paragraph cache — and hands a
/// [ShapedRun] to this, which is a value in the font's own design units.
///
/// ## The units
///
/// A run's offsets and advances are in the face's **design units**, not the
/// logical coordinates everything else here uses. That asymmetry is the
/// rasterizer's: the font's own matrix (`size / units-per-em`) converts them,
/// which is what lets one shaping result be drawn at any size (ADR-0034).
///
/// Confined to the thread that created it, and must be closed. Closing it leaves
/// its face alone — other sizes are using it.
public final class GlyphPen implements AutoCloseable {

    private final BlendFont font;
    private final BlendGlyphBuffer glyphs;
    private final double size;

    /// The face's colour glyphs, shared with every other size over it.
    private final ColorLayers layers;

    /// The painter for the face's `COLR` version 1 graphs, or null for a face
    /// with none — which is every face but an emoji one.
    private final @Nullable ColourGlyphPainter graphs;

    /// Which glyphs have a graph — the one question asked per glyph.
    private final ColorPaints paints;

    /// Logical units per design unit at this size — `size / unitsPerEm` — for
    /// placing a graph: its painter draws in design units and is told where the
    /// glyph's origin is in logical ones.
    private final double perUnit;

    /// Whether any glyph of this face is coloured, asked once.
    private final boolean coloured;

    private boolean closed;

    private GlyphPen(GlyphFace face, double size) {
        this.size = size;
        this.layers = face.layers();
        this.coloured = face.hasColorGlyphs();
        this.paints = face.paints();
        this.graphs = paints.isEmpty() ? null : new ColourGlyphPainter(face);
        this.perUnit = graphs == null ? 0 : size / face.unitsPerEm();
        this.font = BlendFont.on(face.handle(), size);
        try {
            this.glyphs = BlendGlyphBuffer.create();
        } catch (RuntimeException | Error e) {
            font.close();
            throw e;
        }
    }

    /// A pen over `face` at `size` logical units per em.
    ///
    /// **The face must outlive the pen**, and [#close()] does not close it.
    public static GlyphPen on(GlyphFace face, double size) {
        Objects.requireNonNull(face, "face");
        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException(
                    "a font size must be a positive, finite number of logical units, and " + size + " is not");
        }
        return new GlyphPen(face, size);
    }

    /// The size this pen draws at, in logical units.
    public double size() {
        return size;
    }

    /// How far above the baseline this font reaches, as a positive number of
    /// logical units.
    public double ascent() {
        return metrics().ascent();
    }

    /// How far below, as a positive number.
    public double descent() {
        return metrics().descent();
    }

    /// The face's recommended distance between two baselines.
    public double lineHeight() {
        return metrics().lineHeight();
    }

    /// Where the top of an underline goes, as a **y-down offset from the
    /// baseline** — positive, an underline being under the text.
    ///
    /// The face's own number, scaled to this pen's size. A painter that guessed
    /// instead would be wrong at every size and at every family, which is why this
    /// is here rather than in whoever draws the rectangle (`docs/gaps.md` G27,
    /// [ADR-0321]).
    public double underlinePosition() {
        return metrics().underlinePosition();
    }

    /// How thick that rule is, and **zero from a face that does not say** — a
    /// `post` table too short to carry the pair is legal, so the caller needs a
    /// fallback and cannot be handed a guess dressed as a measurement.
    public double underlineThickness() {
        return metrics().underlineThickness();
    }

    /// The same for a rule **through** the text, and therefore negative: it is
    /// above the baseline.
    public double strikethroughPosition() {
        return metrics().strikethroughPosition();
    }

    /// How thick that one is, zero when the face is silent.
    public double strikethroughThickness() {
        return metrics().strikethroughThickness();
    }

    /// Draws glyphs `[from, to)` of `run` with `(x, baseline)` on the baseline.
    ///
    /// A range rather than a whole run, because a wrapped paragraph is one
    /// shaping cut into lines: the pen starts at `x` for every slice, the
    /// advances inside the range are used and the ones before it are not — which
    /// is what makes re-wrapping at a new width cost no shaping at all.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    /// @throws IndexOutOfBoundsException if the range is not within the run
    public void draw(Frame frame, double x, double baseline, ShapedRun run, int from, int to, int argb) {
        requireUsable();
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(run, "run");
        Objects.checkFromToIndex(from, to, run.length());
        if (from == to) {
            return;
        }

        if (coloured) {
            drawInColour(frame, x, baseline, run, from, to, argb);
            return;
        }

        glyphs.clear();
        for (var i = from; i < to; i++) {
            // Straight across, in design units, with no arithmetic in between.
            // The four numbers the shaper reports per glyph are the four fields
            // the rasterizer's placement holds, in the same order and the same
            // width -- which is the whole reason this loop is a copy and not a
            // conversion.
            glyphs.add(run.glyphId(i), run.xOffset(i), run.yOffset(i), run.xAdvance(i), run.yAdvance(i));
        }
        frame.drawGlyphs(x, baseline, font, glyphs, argb);
    }

    /// The same range, out of a face that has colour glyphs in it.
    ///
    /// A glyph with a `COLR` version 1 paint graph is drawn by
    /// [ColourGlyphPainter]; one with version 0 layers is drawn a layer at a time
    /// here; anything else is staged as the outline it is. A face may carry both
    /// formats for one glyph — the older one for renderers that know no better —
    /// and the graph wins, because it is the picture the font means (ADR-0456).
    ///
    /// ## What it costs, and why that is acceptable
    ///
    /// One rasterizer call **per layer**, where the plain path above makes one
    /// call for the whole range. A Noto glyph is a dozen filled outlines, so a
    /// reaction bar of ten emoji is over a hundred calls rather than ten, and a
    /// flag adds two small offscreen layers for its composite.
    ///
    /// They are not, though, a hundred and forty *passes*: a layer is one small
    /// glyph, and what the rasterizer does is proportional to the ink. A face
    /// with no colour in it never reaches this method at all — the check above is
    /// one field read — so the cost is paid by the text that is actually
    /// coloured, which is a reaction chip and not a paragraph ([ADR-0393]).
    ///
    /// ## Why every placement carries an absolute offset
    ///
    /// The pen advances here, in Java, and every glyph is staged with a **zero
    /// advance** and its position as an offset. That is what lets the buffer be
    /// flushed between two glyphs without the ones after it losing their place:
    /// each staged glyph already knows where it goes, so a flush is a flush and
    /// not a break in the run.
    private void drawInColour(Frame frame, double x, double baseline, ShapedRun run, int from, int to, int argb) {
        glyphs.clear();
        var staged = false;
        var penX = 0;
        var penY = 0;

        for (var i = from; i < to; i++) {
            var glyph = run.glyphId(i);
            if (graphs != null && paints.has(glyph)) {
                if (staged) {
                    frame.drawGlyphs(x, baseline, font, glyphs, argb);
                    glyphs.clear();
                    staged = false;
                }
                // Design units y up, as the shaper reports them; the painter is
                // told where the origin is in logical units, y down.
                var originX = x + (penX + run.xOffset(i)) * perUnit;
                var originY = baseline - (penY + run.yOffset(i)) * perUnit;
                if (graphs.draw(frame, glyph, originX, originY, size, argb)) {
                    penX += run.xAdvance(i);
                    penY += run.yAdvance(i);
                    continue;
                }
                // A graph that could not be read falls through to the older
                // format, and then to the outline.
            }
            var record = layers.find(glyph);
            // A record claiming no layers at all is legal and says nothing, so
            // the glyph is drawn as the outline it also is. Skipping it instead
            // would drop a character because a font table was empty.
            if (record < 0 || layers.layerCount(record) == 0) {
                glyphs.add(glyph, penX + run.xOffset(i), penY + run.yOffset(i), 0, 0);
                staged = true;
            } else {
                if (staged) {
                    // Drawn before the layers rather than after, so that glyphs
                    // come out in the order they were shaped in. Two glyphs of one
                    // run rarely overlap, and when they do -- a mark over a base --
                    // the one that was shaped second belongs on top.
                    frame.drawGlyphs(x, baseline, font, glyphs, argb);
                    glyphs.clear();
                    staged = false;
                }
                var count = layers.layerCount(record);
                for (var layer = 0; layer < count; layer++) {
                    glyphs.clear();
                    glyphs.add(layers.layerGlyph(record, layer), penX + run.xOffset(i), penY + run.yOffset(i), 0, 0);
                    frame.drawGlyphs(x, baseline, font, glyphs, layers.layerArgb(record, layer, argb));
                }
                glyphs.clear();
            }
            penX += run.xAdvance(i);
            penY += run.yAdvance(i);
        }

        if (staged) {
            frame.drawGlyphs(x, baseline, font, glyphs, argb);
        }
    }

    /// Whether the pen has been closed.
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            glyphs.close();
        } finally {
            font.close();
        }
    }

    private BlendFontMetrics metrics() {
        requireUsable();
        return font.metrics();
    }

    private void requireUsable() {
        if (closed) {
            throw new IllegalStateException("this GlyphPen has been closed");
        }
    }

    @Override
    public String toString() {
        return "GlyphPen[" + size + "pt" + (closed ? ", closed" : "") + "]";
    }
}

package io.github.digitalsmile.goldberry.paint;

import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.blend2d.BlendFont;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendGlyphBuffer;
import io.github.digitalsmile.goldberry.text.ShapedRun;

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

    private boolean closed;

    private GlyphPen(GlyphFace face, double size) {
        this.size = size;
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

    private io.github.digitalsmile.goldberry.natives.blend2d.BlendFontMetrics metrics() {
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

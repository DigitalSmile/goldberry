package io.github.digitalsmile.goldberry.text;

import io.github.digitalsmile.goldberry.Frame;
import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendFont;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendGlyphBuffer;
import io.github.digitalsmile.goldberry.natives.harfbuzz.GlyphRun;
import io.github.digitalsmile.goldberry.natives.harfbuzz.ShapedFont;
import io.github.digitalsmile.goldberry.natives.harfbuzz.ShapingBuffer;
import java.util.Objects;

/// One typeface at one size, shaped by HarfBuzz and drawn by Blend2D.
///
/// This is where the text stack's two halves meet. HarfBuzz decides which glyphs
/// a string becomes and where each one goes; Blend2D turns those glyph ids into
/// ink. Neither knows about the other, deliberately (`docs/ARCHITECTURE.md` §6),
/// and the price of that separation is one invariant that has to be maintained
/// by hand — which is the reason this class exists rather than a caller wiring
/// the two together each time.
///
/// ## The invariant
///
/// Blend2D multiplies every glyph placement by the **font matrix**, `size /
/// units-per-em`. So the positions handed to it must be in **font design
/// units** — the units HarfBuzz reports when no scale has been set on it. This
/// class therefore leaves the shaping font [unscaled][ShapedFont#UNSCALED] and
/// puts the size on the Blend2D font alone.
///
/// One size, in one place. Setting a scale on the shaper as well would apply the
/// size twice and produce text about `units-per-em / size` too wide — around
/// 128&times; for a 16-point Inter — with nothing reporting a problem. See
/// ADR-0034.
///
/// It also makes a shaping result **size-independent**: the same [GlyphRun] is
/// correct at every size, which is what a paragraph cache will want when it
/// arrives.
///
/// ## Sizes and coordinates
///
/// The size is in *logical* units, like everything an application writes: a
/// 16-point font is 16 points whether the display runs at 100% or 150%, because
/// the frame's context carries the scale. Metrics come back in the same units.
///
/// ## Cost
///
/// The font's bytes are copied twice — once into HarfBuzz, once into Blend2D —
/// because each library owns its own. For Inter that is about a megabyte and a
/// half per `Font`, and a `Font` per size, so an application that wants six
/// sizes of one family currently pays for six copies. A shared face cache is the
/// obvious fix and is not built yet; nothing above this depends on it not being.
///
/// Confined to the thread that created it, and must be closed.
public final class Font implements AutoCloseable {

    private final ShapedFont shaper;
    private final BlendFont painter;

    /// Reused across calls, both of them: a paragraph reshapes on every width a
    /// layout pass proposes, and allocating native memory inside a measure
    /// callback is the one place it is least affordable.
    private final ShapingBuffer text;
    private final BlendGlyphBuffer glyphs;

    private final double size;
    private final int unitsPerEm;

    private boolean closed;

    private Font(byte[] data, double size) {
        this.size = size;

        // Built in order and unwound in reverse: each of these owns native
        // memory, and a failure partway through must not leak what came before.
        this.shaper = ShapedFont.fromBytes(data);
        try {
            // Deliberately NOT scaled. See the invariant above.
            this.unitsPerEm = shaper.unitsPerEm();
            this.painter = BlendFont.fromBytes(data, size);
        } catch (RuntimeException | Error e) {
            shaper.close();
            throw e;
        }
        try {
            this.text = ShapingBuffer.create();
        } catch (RuntimeException | Error e) {
            painter.close();
            shaper.close();
            throw e;
        }
        try {
            this.glyphs = BlendGlyphBuffer.create();
        } catch (RuntimeException | Error e) {
            text.close();
            painter.close();
            shaper.close();
            throw e;
        }
    }

    /// Loads a font from the bytes of a font file.
    ///
    /// @param data a font file's contents
    /// @param size the em size, in logical units
    public static Font of(byte[] data, double size) {
        Objects.requireNonNull(data, "data");
        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException(
                    "a font size must be a positive, finite number of logical units, and "
                            + size + " is not");
        }
        return new Font(data, size);
    }

    /// One of the faces bundled in `goldberry-core`.
    public static Font bundled(BundledFont font, double size) {
        Objects.requireNonNull(font, "font");
        return of(BundledAssets.font(font), size);
    }

    /// Shapes `text` into glyphs.
    ///
    /// Direction, script and language are guessed from the text itself, which is
    /// right for a run of one language and is not a substitute for splitting
    /// mixed-direction text into runs first — that splitting is not HarfBuzz's
    /// job and is not done here.
    ///
    /// The result is in font design units and is therefore correct at any size:
    /// it is this face's shaping of that string, not this `Font`'s.
    public GlyphRun shape(CharSequence text) {
        requireUsable();
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            return GlyphRun.EMPTY;
        }
        this.text.reset();
        this.text.addText(text);
        this.text.guessSegmentProperties();
        return this.text.shape(shaper);
    }

    /// How wide a shaped run is, in logical units.
    ///
    /// The sum of the advances, not the extent of the ink: a trailing space
    /// moves the pen and draws nothing, and a layout pass has to account for it.
    public double widthOf(GlyphRun run) {
        requireUsable();
        Objects.requireNonNull(run, "run");
        return toLogical(run.totalXAdvance());
    }

    /// How wide `text` is once shaped, in logical units.
    public double widthOf(CharSequence text) {
        return widthOf(shape(text));
    }

    /// Draws `text` with `(x, baseline)` on the baseline.
    ///
    /// Shapes and draws in one step, which is the convenient thing and not the
    /// efficient one: text drawn every frame should be shaped once and drawn
    /// through [#draw(Frame, double, double, GlyphRun, int)].
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void draw(Frame frame, double x, double baseline, CharSequence text, int argb) {
        draw(frame, x, baseline, shape(text), argb);
    }

    /// Draws an already-shaped run with `(x, baseline)` on the baseline.
    ///
    /// `baseline` is the line the letters sit on, so the top of the line is
    /// `baseline - ascent()`.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void draw(Frame frame, double x, double baseline, GlyphRun run, int argb) {
        Objects.requireNonNull(run, "run");
        draw(frame, x, baseline, run, 0, run.length(), argb);
    }

    /// Draws glyphs `[from, to)` of an already-shaped run.
    ///
    /// A range rather than a whole run, because a wrapped paragraph is one
    /// shaping cut into lines: [Paragraph] shapes its text once and draws each
    /// line as a slice of that single run, which is what makes re-wrapping at a
    /// new width cost no shaping at all.
    ///
    /// The pen starts at `x` for every slice — the advances inside the range are
    /// used, the ones before it are not.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    /// @throws IndexOutOfBoundsException if the range is not within the run
    public void draw(Frame frame, double x, double baseline, GlyphRun run, int from, int to, int argb) {
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
            // The four numbers HarfBuzz reports per glyph are the four fields
            // BLGlyphPlacement holds, in the same order and the same width --
            // which is the whole reason this loop is a copy and not a
            // conversion.
            glyphs.add(
                    run.glyphId(i),
                    run.xOffset(i), run.yOffset(i),
                    run.xAdvance(i), run.yAdvance(i));
        }
        frame.drawGlyphs(x, baseline, painter, glyphs, argb);
    }

    /// The size this font was created at, in logical units.
    public double size() {
        return size;
    }

    /// The face's units per em — the grid its outlines are designed on, and the
    /// units a [GlyphRun] from [#shape] is in.
    public int unitsPerEm() {
        return unitsPerEm;
    }

    /// How far above the baseline this font reaches, as a positive number of
    /// logical units.
    public double ascent() {
        requireUsable();
        return painter.metrics().ascent();
    }

    /// How far below the baseline it reaches, also positive.
    public double descent() {
        requireUsable();
        return painter.metrics().descent();
    }

    /// The distance from one baseline to the next, as the font itself specifies
    /// it — not a `line-height` a style may impose on top.
    public double lineHeight() {
        requireUsable();
        return painter.metrics().lineHeight();
    }

    /// Converts a measurement in design units to logical units.
    ///
    /// The same `size / units-per-em` Blend2D applies to the glyphs themselves.
    /// Doing it here as well is not duplication — it is how Java answers "how
    /// wide is this?" without asking the rasterizer to draw it first, which is
    /// exactly what a Yoga measure function has to do.
    ///
    /// Public because [Paragraph] measures in design units throughout — integers,
    /// exact, and independent of the size — and converts once at the end.
    public double toLogical(double designUnits) {
        return designUnits * size / unitsPerEm;
    }

    /// Whether the font has been closed.
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
            try {
                text.close();
            } finally {
                try {
                    painter.close();
                } finally {
                    shaper.close();
                }
            }
        }
    }

    private void requireUsable() {
        if (closed) {
            throw new IllegalStateException("this Font has been closed");
        }
    }

    @Override
    public String toString() {
        return "Font[" + size + "pt, " + unitsPerEm + " upem" + (closed ? ", closed" : "") + "]";
    }
}

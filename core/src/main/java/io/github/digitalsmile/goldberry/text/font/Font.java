package io.github.digitalsmile.goldberry.text.font;

import org.jspecify.annotations.Nullable;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.paint.GlyphPen;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.text.ShapedRun;
import io.github.digitalsmile.goldberry.text.TextDirection;
import io.github.digitalsmile.goldberry.natives.harfbuzz.ShapedFont;
import io.github.digitalsmile.goldberry.natives.harfbuzz.ShapingBuffer;
import java.util.Objects;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.flow.TextOverflow;

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
/// It also makes a shaping result **size-independent**: the same [ShapedRun] is
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
/// A typeface is still two copies of the file — once into HarfBuzz, once into
/// Blend2D, because each library owns its own — but it is two copies per
/// **face**, not per size. [FontFace] is the shared part, and [#on(FontFace,
/// double)] is how several sizes take it: four sizes of Inter cost three
/// megabytes rather than twelve (ADR-0044).
///
/// [#bundled(BundledFont, double)] and [#of(byte[], double)] still parse a face
/// of their own and close it with the font, which is the right shape for one
/// size and the wrong one for four.
///
/// Confined to the thread that created it, and must be closed.
public final class Font implements AutoCloseable {

    private final FontFace face;

    /// Whether this font parsed its own face and therefore has to close it.
    private final boolean ownsFace;

    private final ShapedFont shaper;
    private final GlyphPen painter;

    /// Reused across calls, both of them: a paragraph reshapes on every width a
    /// layout pass proposes, and allocating native memory inside a measure
    /// callback is the one place it is least affordable.
    private final ShapingBuffer text;

    private final double size;
    private final int unitsPerEm;

    private boolean closed;

    /// [#ellipsisWidth()]'s memo. NaN is "not asked yet", which no width can be.
    private double ellipsisWidth = Double.NaN;

    private Font(FontFace face, boolean ownsFace, double size) {
        this.face = face;
        this.ownsFace = ownsFace;
        this.size = size;

        // Built in order and unwound in reverse: each of these owns native
        // memory, and a failure partway through must not leak what came before.
        //
        // The shaper and the units per em come from the face and are shared with
        // every other size over it. Deliberately NOT scaled -- see the invariant
        // above.
        this.shaper = face.shaper();
        this.unitsPerEm = face.unitsPerEm();
        try {
            this.painter = GlyphPen.on(face.painter(), size);
        } catch (RuntimeException | Error e) {
            closeFaceIfOwned();
            throw e;
        }
        try {
            this.text = ShapingBuffer.create();
        } catch (RuntimeException | Error e) {
            painter.close();
            closeFaceIfOwned();
            throw e;
        }
    }

    /// A font at `size` over a face somebody else owns.
    ///
    /// **The face must outlive this font**, and closing this one leaves the face
    /// alone: other sizes are using it. This is the constructor to reach for
    /// when an application wants more than one size of a family, which is every
    /// application (ADR-0044).
    ///
    /// @param face the typeface, which is not closed by [#close()]
    /// @param size the em size, in logical units
    public static Font on(FontFace face, double size) {
        Objects.requireNonNull(face, "face");
        requireUsableSize(size);
        return new Font(face, false, size);
    }

    /// Loads a font from the bytes of a font file, parsing a face of its own.
    ///
    /// The face is closed with the font. For more than one size of the same
    /// file, [FontFace] and [#on(FontFace, double)] share the parse and the
    /// bytes instead.
    ///
    /// @param data a font file's contents
    /// @param size the em size, in logical units
    public static Font of(byte[] data, double size) {
        Objects.requireNonNull(data, "data");
        requireUsableSize(size);
        return new Font(FontFace.of("<bytes>", data), true, size);
    }

    /// One of the faces bundled in `goldberry-core`, at one size.
    ///
    /// Parses its own face, so two sizes are two parses and two copies of the
    /// file. `FontFace.bundled(font)` plus [#on(FontFace, double)] is the pair
    /// that does not.
    public static Font bundled(BundledFont font, double size) {
        Objects.requireNonNull(font, "font");
        requireUsableSize(size);
        return new Font(FontFace.bundled(font), true, size);
    }

    /// The typeface this font draws with, shared with every other size over it.
    public FontFace face() {
        return face;
    }

    private static void requireUsableSize(double size) {
        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException(
                    "a font size must be a positive, finite number of logical units, and "
                            + size + " is not");
        }
    }

    private void closeFaceIfOwned() {
        if (ownsFace) {
            face.close();
        }
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
    public ShapedRun shape(CharSequence text) {
        return shape(text, null);
    }

    /// Shapes `text`, **forcing the direction** rather than guessing it.
    ///
    /// Script and language are still guessed — they are facts about the
    /// characters, and Arabic joins whichever way the glyphs are then ordered.
    /// What the direction changes is the *order* the run comes back in.
    ///
    /// One caller: [Paragraph], which asks for `LTR` on text HarfBuzz would have
    /// guessed `RTL` for, because its measurements are prefix sums in **logical**
    /// order and a visually ordered run would make every one of them measure the
    /// wrong glyphs. That is an approximation with a name and a plan
    /// (ADR-0218);
    /// real bidi is run splitting, and this is not it.
    ///
    /// @param direction the direction to shape in, or null to guess
    public ShapedRun shape(CharSequence text, @Nullable TextDirection direction) {
        requireUsable();
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            return ShapedRun.EMPTY;
        }
        this.text.reset();
        this.text.addText(text);
        this.text.guessSegmentProperties();
        if (direction != null) {
            // The one place the two direction vocabularies meet, and a `switch`
            // rather than an ordinal for the reason every other translation in
            // the toolkit is one: the shaper numbers left-to-right as 4
            // (ADR-0282).
            this.text.setDirection(
                    switch (direction) {
                        case LTR -> io.github.digitalsmile.goldberry.natives.harfbuzz.enums.TextDirection.LTR;
                        case RTL -> io.github.digitalsmile.goldberry.natives.harfbuzz.enums.TextDirection.RTL;
                    });
        }
        return copyOut(this.text.shape(shaper));
    }

    /// The shaper's run as the toolkit's.
    ///
    /// One pass over the glyphs, into six arrays. A copy rather than a view,
    /// because the thing being copied out of is `:natives`' and the thing being
    /// handed to an application must not be (ADR-0282) — and because shaping
    /// already allocates six arrays of its own, so this is a doubling of
    /// something that happens once per text change rather than once per frame.
    private static ShapedRun copyOut(io.github.digitalsmile.goldberry.natives.harfbuzz.GlyphRun run) {
        var length = run.length();
        if (length == 0) {
            return ShapedRun.EMPTY;
        }
        var glyphIds = new int[length];
        var clusters = new int[length];
        var xAdvances = new int[length];
        var yAdvances = new int[length];
        var xOffsets = new int[length];
        var yOffsets = new int[length];
        for (var i = 0; i < length; i++) {
            glyphIds[i] = run.glyphId(i);
            clusters[i] = run.cluster(i);
            xAdvances[i] = run.xAdvance(i);
            yAdvances[i] = run.yAdvance(i);
            xOffsets[i] = run.xOffset(i);
            yOffsets[i] = run.yOffset(i);
        }
        return ShapedRun.of(glyphIds, clusters, xAdvances, yAdvances, xOffsets, yOffsets);
    }

    /// How wide a shaped run is, in logical units.
    ///
    /// The sum of the advances, not the extent of the ink: a trailing space
    /// moves the pen and draws nothing, and a layout pass has to account for it.
    public double widthOf(ShapedRun run) {
        requireUsable();
        Objects.requireNonNull(run, "run");
        // The cast is explicit because the advance is a 26.6 fixed-point `long`
        // and `toLogical` takes a double: the widening was happening anyway, and
        // saying so is the difference between a conversion and an accident.
        return toLogical((double) run.totalXAdvance());
    }

    /// How wide `text` is once shaped, in logical units.
    public double widthOf(CharSequence text) {
        return widthOf(shape(text));
    }

    /// How wide this font draws [TextOverflow#MARK], in logical units.
    ///
    /// Memoised, because it is asked **per painted label per frame** — every
    /// truncated cell in a list wants it — and the answer is a fact about the
    /// face and the size, both of which a [Font] fixes for its whole life. One
    /// shaping of one character, once.
    ///
    /// Not `volatile` and not synchronized, for the reason the rest of this class
    /// is neither: a font holds native handles and is confined to the thread that
    /// made it, so the only reader of this field is the thread that wrote it.
    ///
    /// Here rather than in [Paragraph] because it belongs to the *font*: a
    /// paragraph would memoise it once per distinct string, which is once per
    /// cache entry for a number that never differs between them.
    public double ellipsisWidth() {
        if (Double.isNaN(ellipsisWidth)) {
            ellipsisWidth = widthOf(TextOverflow.MARK);
        }
        return ellipsisWidth;
    }

    /// Draws `text` with `(x, baseline)` on the baseline.
    ///
    /// Shapes and draws in one step, which is the convenient thing and not the
    /// efficient one: text drawn every frame should be shaped once and drawn
    /// through [#draw(Frame, double, double, ShapedRun, int)].
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
    public void draw(Frame frame, double x, double baseline, ShapedRun run, int argb) {
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
    public void draw(Frame frame, double x, double baseline, ShapedRun run, int from, int to, int argb) {
        requireUsable();
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(run, "run");
        Objects.checkFromToIndex(from, to, run.length());
        if (from == to) {
            return;
        }

        // The copy into the rasterizer's staged buffer is the pen's, in `paint`,
        // beside the context it draws into -- which is what closed the last of
        // the module boundary's leaks (ADR-0290).
        painter.draw(frame, x, baseline, run, from, to, argb);
    }

    /// The size this font was created at, in logical units.
    public double size() {
        return size;
    }

    /// The face's units per em — the grid its outlines are designed on, and the
    /// units a [ShapedRun] from [#shape] is in.
    public int unitsPerEm() {
        return unitsPerEm;
    }

    /// How far above the baseline this font reaches, as a positive number of
    /// logical units.
    public double ascent() {
        requireUsable();
        return painter.ascent();
    }

    /// How far below the baseline it reaches, also positive.
    public double descent() {
        requireUsable();
        return painter.descent();
    }

    /// The distance from one baseline to the next, as the font itself specifies
    /// it — not a `line-height` a style may impose on top.
    public double lineHeight() {
        requireUsable();
        return painter.lineHeight();
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
            text.close();
        } finally {
            try {
                painter.close();
            } finally {
                // The shaper belongs to the face, not to this font: closing it
                // here would break every other size over the same face.
                closeFaceIfOwned();
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

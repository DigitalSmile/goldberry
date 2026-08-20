package io.github.digitalsmile.goldberry.text;

import io.github.digitalsmile.goldberry.Frame;
import io.github.digitalsmile.goldberry.natives.harfbuzz.GlyphRun;
import io.github.digitalsmile.goldberry.natives.yoga.MeasureFunction;
import io.github.digitalsmile.goldberry.natives.yoga.MeasureMode;
import io.github.digitalsmile.goldberry.natives.yoga.MeasuredSize;
import java.text.Bidi;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A run of text that knows how to wrap itself, and therefore how to be laid out.
///
/// This is the piece the M1 slice was missing. Yoga cannot see inside a leaf: it
/// proposes a width and asks how tall the content came out, and the answer for
/// text is "shape it, break it into lines, count them". That question is asked
/// from C, several times per layout pass, through the `YGSize` upcall proven in
/// ADR-0017 — so the answer has to be cheap.
///
/// ## Shaped once, wrapped many times
///
/// The text is shaped **once**, when the paragraph is created, and never again.
/// Wrapping is then pure arithmetic over that one `GlyphRun`: a line is a range
/// of glyphs, and re-wrapping at a new width produces new ranges over the same
/// glyphs. A measure callback therefore costs a scan, not a shaping pass.
///
/// That is only possible because shaping happens in font design units
/// (ADR-0034), which makes the run independent of the size it will be drawn at —
/// so it is also independent of the width it will be wrapped to.
///
/// ## What it does not do yet
///
/// **One direction, one face, one style.** A paragraph is a single left-to-right
/// run in one font. Mixed-direction text is refused at construction rather than
/// mis-wrapped: prefix widths are accumulated in logical order, and in a
/// right-to-left run HarfBuzz returns glyphs in *visual* order, so every
/// measurement would be quietly wrong. Splitting text into directional runs is
/// `java.text.Bidi`'s job and is still ahead — the check here uses the same class
/// that will do it.
///
/// **Breaks are not re-shaped.** Each line is a slice of the whole paragraph's
/// shaping, so a kern between the last character of one line and the first of the
/// next is included where a per-line shaping would drop it. The error is a
/// fraction of a pixel and it buys wrapping that costs no shaping; re-shaping
/// each line is the fix if it ever shows.
///
/// Confined to its font's thread. Not immutable — it memoises the last wrap —
/// but it holds no native resources of its own, so there is nothing to close.
public final class Paragraph {

    /// What [#layout] is passed when there is no width constraint at all.
    public static final double UNCONSTRAINED = Double.POSITIVE_INFINITY;

    private final Font font;
    private final String text;

    /// The whole paragraph, shaped once, in design units.
    private final GlyphRun run;

    /// `advanceBefore[o]` is the advance, in design units, of every glyph whose
    /// cluster is before text offset `o`. Prefix sums, so the width of any range
    /// is one subtraction — which is what keeps the measure callback cheap.
    private final int[] advanceBefore;

    /// `glyphBefore[o]` is how many glyphs come before text offset `o`. The same
    /// trick, for turning a text range into a glyph range.
    private final int[] glyphBefore;

    /// The last wrap, kept because Yoga asks for the same width repeatedly within
    /// a pass and the paint that follows asks for it once more. One entry rather
    /// than a map: the access pattern is a run of identical widths, and a map
    /// would cost a hash of a `double` to serve the same hit.
    private double memoWidth = Double.NaN;
    private TextLayout memo;

    private Paragraph(Font font, String text) {
        this.font = font;
        this.text = text;
        this.run = font.shape(text);

        var length = text.length();
        this.advanceBefore = new int[length + 1];
        this.glyphBefore = new int[length + 1];

        // Glyphs are in logical order here -- guaranteed, because a
        // right-to-left paragraph was refused above. Several glyphs can share a
        // cluster (a mark over a base) and a cluster can span several characters
        // (a ligature, a surrogate pair), so this walks glyphs and fills the
        // offsets each one covers.
        var advance = 0;
        var glyph = 0;
        var offset = 0;
        for (var i = 0; i < run.length(); i++) {
            var cluster = Math.clamp(run.cluster(i), 0, length);
            // Every offset from the last cluster up to this one is "before" the
            // glyphs counted so far. Offsets inside a ligature land here too and
            // get the width up to its start, which is the only honest answer:
            // there is no width for half a ligature, and no legal break inside
            // one either.
            while (offset <= cluster) {
                advanceBefore[offset] = advance;
                glyphBefore[offset] = glyph;
                offset++;
            }
            advance += run.xAdvance(i);
            glyph++;
        }
        while (offset <= length) {
            advanceBefore[offset] = advance;
            glyphBefore[offset] = glyph;
            offset++;
        }
    }

    /// Shapes `text` with `font`, ready to be wrapped.
    ///
    /// @throws UnsupportedOperationException if the text contains right-to-left
    ///         characters — see the note on this class. Refused here, at
    ///         construction, rather than during a paint pass
    public static Paragraph of(Font font, String text) {
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(text, "text");

        if (Bidi.requiresBidi(text.toCharArray(), 0, text.length())) {
            throw new UnsupportedOperationException(
                    "this paragraph contains right-to-left text, which needs bidi run splitting"
                            + " before it can be wrapped: HarfBuzz returns those glyphs in visual"
                            + " order, so measuring a prefix of the text would measure the wrong"
                            + " glyphs. Splitting into directional runs is not built yet.");
        }
        return new Paragraph(font, text);
    }

    /// Breaks the text into lines that fit in `maxWidth` logical units.
    ///
    /// Greedy, which is what every browser does: each line takes as much as fits
    /// and no more. A word longer than the whole width is **not** broken — it
    /// overflows on a line of its own, because hyphenation and mid-word breaking
    /// are decisions a style should make rather than a layout engine.
    ///
    /// @param maxWidth the width to fit in, or [#UNCONSTRAINED] for one line per
    ///                 explicit newline and no wrapping at all
    public TextLayout layout(double maxWidth) {
        if (Double.isNaN(maxWidth)) {
            throw new IllegalArgumentException(
                    "a NaN width would wrap every line to nothing; pass Paragraph.UNCONSTRAINED"
                            + " for no constraint");
        }
        // NaN never equals itself, so the first call always misses.
        if (maxWidth == memoWidth) {
            return memo;
        }

        var lines = new ArrayList<TextLine>();
        // Hard breaks first: BreakIterator offers a break after a newline but
        // does not say it is mandatory, and a paragraph that silently joined its
        // own lines would be wrong in a way only long text reveals.
        var paragraphStart = 0;
        while (paragraphStart <= text.length()) {
            var newline = text.indexOf('\n', paragraphStart);
            var paragraphEnd = newline < 0 ? text.length() : newline;
            wrap(paragraphStart, paragraphEnd, maxWidth, lines);
            if (newline < 0) {
                break;
            }
            paragraphStart = newline + 1;
        }

        var widest = 0.0;
        for (var line : lines) {
            widest = Math.max(widest, line.width());
        }

        var layout = new TextLayout(lines, widest, lines.size() * font.lineHeight());
        memoWidth = maxWidth;
        memo = layout;
        return layout;
    }

    /// A [MeasureFunction] that reports this paragraph's size to Yoga.
    ///
    /// Attach it to a leaf node and the flexbox algorithm treats the text as
    /// content: it proposes a width, this wraps at that width, and the height
    /// that comes back is what the row or column is sized around.
    ///
    /// The width reported back is the widest line, except under
    /// [MeasureMode#EXACTLY] where the parent has already decided. Reporting the
    /// *available* width instead would make every paragraph claim the full
    /// column even when it wrapped well short of it, and a centred parent would
    /// then centre empty space.
    public MeasureFunction measureFunction() {
        return (width, widthMode, height, heightMode) -> {
            var available = switch (widthMode) {
                // Yoga passes NaN with UNDEFINED, so `width` must not be read.
                case UNDEFINED -> UNCONSTRAINED;
                case EXACTLY, AT_MOST -> (double) width;
            };
            var layout = layout(available);
            var measured = widthMode == MeasureMode.EXACTLY ? width : (float) layout.width();
            return new MeasuredSize(measured, (float) layout.height());
        };
    }

    /// Draws the paragraph with its first line's **top** at `(x, top)`.
    ///
    /// The top, not the baseline — a paragraph is placed against a box, and a box
    /// has a top. Each line's baseline is derived from it by the font's ascent,
    /// which is the one place that conversion belongs.
    ///
    /// @param maxWidth the width to wrap at; pass what layout gave the box
    /// @param argb     a colour as `0xAARRGGBB`, not premultiplied
    public void paint(Frame frame, double x, double top, double maxWidth, int argb) {
        Objects.requireNonNull(frame, "frame");

        var layout = layout(maxWidth);
        var lineHeight = font.lineHeight();
        var ascent = font.ascent();

        for (var i = 0; i < layout.lines().size(); i++) {
            var line = layout.lines().get(i);
            if (line.isEmpty()) {
                continue;
            }
            font.draw(frame, x, top + ascent + i * lineHeight,
                    run, line.glyphStart(), line.glyphEnd(), argb);
        }
    }

    /// The font this paragraph was shaped with.
    public Font font() {
        return font;
    }

    /// The text, unchanged.
    public String text() {
        return text;
    }

    /// The whole paragraph as one shaped run, in design units.
    ///
    /// A [TextLine]'s glyph range indexes into this.
    public GlyphRun glyphs() {
        return run;
    }

    // --- wrapping -------------------------------------------------------------

    /// Breaks `[start, end)` — one hard line — into as many soft lines as it
    /// takes, appending each.
    private void wrap(int start, int end, double maxWidth, List<TextLine> lines) {
        if (start == end) {
            // A blank line. It draws nothing and still takes a line's height,
            // which is what a reader means by a blank line.
            lines.add(new TextLine(start, end, glyphBefore[start], glyphBefore[start], 0));
            return;
        }

        var breaks = BreakIterator.getLineInstance();
        breaks.setText(text.substring(start, end));

        var lineStart = start;
        // The furthest break that still fits on the line being built. Below
        // `lineStart` means "nothing yet", which is the case that decides
        // whether an over-long word overflows or is dropped.
        var lastFitting = -1;

        // `first()` is always offset zero, which is the line start rather than a
        // place to break, so the walk begins at the one after it.
        breaks.first();
        for (var candidate = breaks.next(); candidate != BreakIterator.DONE;
                candidate = breaks.next()) {

            var offset = start + candidate;
            if (widthOf(lineStart, offset) <= maxWidth) {
                lastFitting = offset;
                continue;
            }

            if (lastFitting > lineStart) {
                // Break at the last place that fitted, then reconsider this
                // candidate against the new line -- it is the next line's
                // content, not something to skip.
                lines.add(lineFor(lineStart, lastFitting));
                lineStart = lastFitting;
                lastFitting = -1;

                if (widthOf(lineStart, offset) <= maxWidth) {
                    lastFitting = offset;
                    continue;
                }
            }

            // A single unbreakable chunk wider than the whole line. It goes on a
            // line of its own and overflows: breaking inside it would be a
            // hyphenation decision, which is a style's to make and not a layout
            // engine's.
            lines.add(lineFor(lineStart, offset));
            lineStart = offset;
        }

        if (lineStart < end) {
            lines.add(lineFor(lineStart, end));
        }
    }

    /// Builds a line, trimming trailing whitespace out of the glyph range and the
    /// width but not out of the text range.
    private TextLine lineFor(int start, int end) {
        var visible = end;
        while (visible > start && Character.isWhitespace(text.charAt(visible - 1))) {
            visible--;
        }
        return new TextLine(
                start, end,
                glyphBefore[start], glyphBefore[visible],
                widthOf(start, visible));
    }

    /// The width of `[start, end)` in logical units — one subtraction, which is
    /// what the prefix sums are for.
    private double widthOf(int start, int end) {
        return font.toLogical(advanceBefore[end] - advanceBefore[start]);
    }

    // --- caret geometry -------------------------------------------------------

    /// The width of the text in `[start, end)`, in logical units.
    ///
    /// The public form of the subtraction wrapping is built on, and **the only
    /// thing a caret needs**: a caret sitting before offset `o` on a line is
    /// `widthBetween(line.start(), o)` from that line's left edge, and a
    /// selection highlight from `a` to `b` is a rectangle between those two
    /// numbers. There is no `caretX(offset)` here because it would be this
    /// method with one argument fixed, and a paragraph that wrapped has no single
    /// left edge to fix it to.
    ///
    /// Offsets inside a ligature or a surrogate pair report the width up to the
    /// cluster's start, which is the same answer wrapping gets and for the same
    /// reason: there is no width for half a ligature. Callers that want a caret
    /// to land somewhere legal ask [#offsetAt] rather than rounding themselves.
    ///
    /// @throws IndexOutOfBoundsException if either offset is outside the text
    /// @throws IllegalArgumentException  if `end` is before `start`
    public double widthBetween(int start, int end) {
        Objects.checkIndex(start, text.length() + 1);
        Objects.checkIndex(end, text.length() + 1);
        if (end < start) {
            throw new IllegalArgumentException(
                    "a text range cannot end before it starts: " + start + ".." + end);
        }
        return widthOf(start, end);
    }

    /// The offset in `[lineStart, lineEnd]` whose caret sits nearest `x`.
    ///
    /// The other direction of [#widthBetween], and what a click in a text field
    /// asks: `x` is measured from the **line's** left edge, and the answer is a
    /// text offset the caret can legally occupy.
    ///
    /// ## Nearest, and why that is the whole rule
    ///
    /// Every editor puts the caret *after* a character clicked on its right half
    /// and *before* one clicked on its left. That is not a separate rule — it is
    /// what "nearest caret position" already means, because the two caret
    /// positions bracketing a glyph are its edges and the midpoint is where the
    /// nearer one changes. So there is no half-advance arithmetic here, only a
    /// walk and a minimum.
    ///
    /// ## Boundaries, not offsets
    ///
    /// The walk steps by **grapheme cluster** — `java.text.BreakIterator`'s
    /// character instance, the same class the wrap uses for lines — so a click
    /// can never land between the two halves of a surrogate pair or between a
    /// letter and the accent over it. Those offsets exist in the string and are
    /// not places a caret can be; returning one would put the next keystroke
    /// inside a character.
    ///
    /// An `x` left of the line is `lineStart` and one right of it is `lineEnd`,
    /// which is what dragging a selection off the end of a field should do.
    ///
    /// @param lineStart the first offset of the line, from [TextLine#start()]
    /// @param lineEnd   one past its last, from [TextLine#end()]
    /// @param x         the distance from the line's left edge, in logical units
    /// @throws IndexOutOfBoundsException if either offset is outside the text
    /// @throws IllegalArgumentException  if `lineEnd` is before `lineStart`
    public int offsetAt(int lineStart, int lineEnd, double x) {
        Objects.checkIndex(lineStart, text.length() + 1);
        Objects.checkIndex(lineEnd, text.length() + 1);
        if (lineEnd < lineStart) {
            throw new IllegalArgumentException(
                    "a line cannot end before it starts: " + lineStart + ".." + lineEnd);
        }
        if (lineStart == lineEnd || !(x > 0)) {
            // NaN lands here too, which is the right home for it: a click at an
            // unknown position is a click at the start.
            return lineStart;
        }

        var graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);

        var best = lineStart;
        var bestDistance = Math.abs(x - widthOf(lineStart, lineStart));
        for (var offset = graphemes.following(lineStart);
                offset != BreakIterator.DONE && offset <= lineEnd;
                offset = graphemes.next()) {

            var distance = Math.abs(x - widthOf(lineStart, offset));
            if (distance > bestDistance) {
                // Advances are non-negative, so the distance to the target falls
                // and then rises. Once it has risen the answer is behind us --
                // and stopping here is what keeps a click near the start of a
                // long line from walking the whole line.
                break;
            }
            bestDistance = distance;
            best = offset;
        }
        return best;
    }
}

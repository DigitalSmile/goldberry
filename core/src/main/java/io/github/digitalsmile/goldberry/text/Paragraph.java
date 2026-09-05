package io.github.digitalsmile.goldberry.text;

import java.text.Bidi;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.harfbuzz.GlyphRun;
import io.github.digitalsmile.goldberry.natives.harfbuzz.enums.TextDirection;
import io.github.digitalsmile.goldberry.natives.yoga.measure.MeasureFunction;
import io.github.digitalsmile.goldberry.natives.yoga.measure.MeasureMode;
import io.github.digitalsmile.goldberry.natives.yoga.measure.MeasuredSize;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.text.flow.TextFlow;
import io.github.digitalsmile.goldberry.text.flow.TextOverflow;
import io.github.digitalsmile.goldberry.text.font.Font;

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
/// **One direction, one face, one style.** A paragraph is a single run in one
/// font, measured as prefix sums in **logical** order. Text that needs bidi — any
/// right-to-left character, which `java.text.Bidi.requiresBidi` detects — is
/// therefore shaped with the direction forced to `LTR`, so the glyphs come back
/// in the order the measurements assume. Every width, every caret position and
/// every hit test is then self-consistent, and the text is drawn in the **wrong
/// visual order**: it is mirrored, not reordered.
///
/// That is an approximation, and [#isBidiApproximate()] is how a caller asks
/// whether it is in force. It replaced an exception, because a paragraph that
/// refused meant a field a user pasted Arabic into took the window down with it
/// (ADR-0218).
/// The real fix is splitting text into directional runs — `java.text.Bidi`'s job,
/// with the same class already here — and it is still ahead.
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

    private static final org.slf4j.Logger LOG = Logs.of(Paragraph.class);

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
    private @Nullable TextLayout memo;

    /// Whether the text was shaped in logical order because it needed bidi.
    private final boolean bidiApproximate;

    private Paragraph(Font font, String text, boolean bidiApproximate) {
        this.font = font;
        this.text = text;
        this.bidiApproximate = bidiApproximate;
        // Forced to logical order when the text would otherwise come back
        // visually ordered. Guessed as usual when it would not, so every
        // paragraph the toolkit has ever drawn is shaped exactly as before.
        this.run = font.shape(text, bidiApproximate ? TextDirection.LTR : null);

        var length = text.length();
        this.advanceBefore = new int[length + 1];
        this.glyphBefore = new int[length + 1];

        // Glyphs are in logical order here -- guaranteed, because the shaping
        // above forced `LTR` for anything HarfBuzz would have ordered visually.
        // Several glyphs can share a cluster (a mark over a base) and a cluster
        // can span several characters (a ligature, a surrogate pair), so this
        // walks glyphs and fills the offsets each one covers.
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
    /// **Never refuses.** Text that needs bidi is shaped in logical order and
    /// drawn mirrored rather than throwing — see the note on this class and
    /// [#isBidiApproximate()]. It used to throw, and what that cost was a window
    /// taken down by a paste (ADR-0218).
    public static Paragraph of(Font font, String text) {
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(text, "text");

        var approximate = Bidi.requiresBidi(text.toCharArray(), 0, text.length());
        if (approximate) {
            // Once per distinct string, because a paragraph is shaped once and
            // held by `ParagraphCache`. Loud, because what it says is that
            // something on screen is drawn in the wrong order -- and quiet
            // enough not to be a per-frame log, because nothing here runs per
            // frame.
            LOG.warn(
                    "drawing \"{}\" in logical order: it contains right-to-left text, and bidi"
                            + " run splitting is not built — the glyphs are shaped correctly and their"
                            + " order is mirrored",
                    text);
        }
        return new Paragraph(font, text, approximate);
    }

    /// Whether this paragraph's text needed bidi and did not get it.
    ///
    /// True means the glyphs are right and their **order** is not: the text was
    /// shaped left-to-right because every measurement here is a prefix sum in
    /// logical order. Measurements, carets and hit tests all agree with what is
    /// drawn — they are consistent with each other and with a reading order the
    /// text does not have.
    public boolean isBidiApproximate() {
        return bidiApproximate;
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
    public @Nullable TextLayout layout(double maxWidth) {
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
        return measureFunction(TextFlow.NORMAL);
    }

    /// The same, told what the box's `white-space` resolved to.
    ///
    /// Under [io.github.digitalsmile.goldberry.text.flow.WhiteSpace#NOWRAP] the
    /// width Yoga offers is **ignored**: the paragraph reports the width it
    /// actually wants, and the box is then free to be laid out narrower than its
    /// own content. That is the whole of what `nowrap` buys, and it is what makes
    /// a cut label possible at all — a box with text is a measured leaf, so
    /// narrowing it re-measures the paragraph, and a paragraph that answers "as
    /// wide as I offered" can never overflow anything ([ADR-0235]).
    ///
    /// `text-overflow` is deliberately not read here. An ellipsised line is drawn
    /// short and measured long: measuring the truncation would let the ellipsis
    /// decide the width that caused it ([ADR-0255]).
    ///
    /// [MeasureMode#EXACTLY] still wins under either value, because a parent that
    /// has already decided a width is not asking.
    public MeasureFunction measureFunction(TextFlow flow) {
        Objects.requireNonNull(flow, "flow");
        var wraps = flow.wraps();
        return (width, widthMode, height, heightMode) -> {
            var available =
                    switch (widthMode) {
                        // Yoga passes NaN with UNDEFINED, so `width` must not be read.
                        case UNDEFINED -> UNCONSTRAINED;
                        case EXACTLY, AT_MOST -> wraps ? (double) width : UNCONSTRAINED;
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
        paint(frame, x, top, maxWidth, argb, TextFlow.NORMAL);
    }

    /// The same, told what the box's `white-space` and `text-overflow` resolved
    /// to.
    ///
    /// Three drawings, from one pair of keywords:
    ///
    /// - **wrapping** — the paragraph is laid out at `maxWidth` and every line is
    ///   drawn. What every box did before either property existed.
    /// - **`nowrap`** — laid out unconstrained and drawn at its natural width,
    ///   past `maxWidth` where it is longer. Whether that overhang is visible is
    ///   an ancestor's `overflow` to decide, and this method neither knows nor
    ///   needs to: the clip is already on the context when it is called.
    /// - **`nowrap` with `ellipsis`** — the same, except that a line wider than
    ///   `maxWidth` is drawn up to the last grapheme that leaves room for
    ///   [TextOverflow#MARK], and the mark is drawn after it.
    ///
    /// The ellipsis is applied **per line** rather than to the last line of the
    /// box, which is where CSS puts it. Every consumer in the catalog is a
    /// single-line label, so the two agree wherever it is used today, and per-line
    /// is the reading that stays true of a `nowrap` paragraph with hard newlines
    /// in it — where CSS would leave every line but the last running off the edge.
    ///
    /// @param maxWidth the width to wrap at, or to truncate at; pass what layout
    ///                 gave the box
    /// @param argb     a colour as `0xAARRGGBB`, not premultiplied
    /// @param flow     what the cascade said about breaking and marking
    public void paint(Frame frame, double x, double top, double maxWidth, int argb, TextFlow flow) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(flow, "flow");

        var layout = layout(flow.wraps() ? maxWidth : UNCONSTRAINED);
        var lineHeight = font.lineHeight();
        var ascent = font.ascent();
        var ellipsis = flow.ellipsises();
        var slack = flow.textAlign().fractionOfSlack();

        for (var i = 0; i < layout.lines().size(); i++) {
            var line = layout.lines().get(i);
            if (line.isEmpty()) {
                continue;
            }
            var baseline = top + ascent + i * lineHeight;
            if (!ellipsis || line.width() <= maxWidth) {
                font.draw(
                        frame,
                        x + indentOf(line.width(), maxWidth, slack),
                        baseline,
                        run,
                        line.glyphStart(),
                        line.glyphEnd(),
                        argb);
                continue;
            }
            // A truncated line fills the box by construction, so there is no
            // slack to share and `text-align` has nothing to say about it.
            paintTruncated(frame, x, baseline, maxWidth, argb, line);
        }
    }

    /// How far in from the box's leading edge a line of `width` starts.
    ///
    /// **Per line**, which is what `text-align` means: a centred paragraph
    /// centres each of its lines in the same box rather than centring the block
    /// they make up.
    ///
    /// Clamped at zero, and both reasons are real. A line **wider** than its box
    /// — every `nowrap` line that overflows — would otherwise be pulled *left* by
    /// `text-align: end`, hiding its beginning instead of its end; and `maxWidth`
    /// is [#UNCONSTRAINED] wherever a caller is measuring rather than placing,
    /// which would make the offset infinite.
    private static double indentOf(double width, double maxWidth, double fraction) {
        if (fraction == 0 || !Double.isFinite(maxWidth)) {
            return 0;
        }
        return Math.max(0, maxWidth - width) * fraction;
    }

    /// Draws one over-long line as much of itself as fits, then the ellipsis.
    ///
    /// The mark is measured through [Font#ellipsisWidth()] rather than laid out
    /// as text, because it is not part of this paragraph: it belongs to the box
    /// the paragraph did not fit in. Room for it is taken off the top, so the
    /// mark always lands inside `maxWidth` — an ellipsis that itself overflowed
    /// would say "there is more" by hanging off the edge, which is the thing it
    /// exists to stop.
    ///
    /// **A line with no room even for the mark still draws the mark.** The
    /// alternative is a cell that goes blank as it narrows, which reads as a
    /// missing value rather than as a truncated one.
    private void paintTruncated(Frame frame, double x, double baseline, double maxWidth, int argb, TextLine line) {
        var mark = font.ellipsisWidth();
        var room = maxWidth - mark;
        var cut = room > 0 ? offsetFitting(line.start(), line.end(), room) : line.start();

        // Trailing whitespace before the mark, so a cut at a word boundary reads
        // as `Save as…` rather than `Save as …`. The glyph range already drops
        // it from the *width*; what it does not do is stop the pen advancing
        // over it, which is what would put the gap in.
        while (cut > line.start() && Character.isWhitespace(text.charAt(cut - 1))) {
            cut--;
        }

        if (cut > line.start()) {
            font.draw(frame, x, baseline, run, line.glyphStart(), glyphBefore[cut], argb);
        }
        font.draw(frame, x + widthOf(line.start(), cut), baseline, TextOverflow.MARK, argb);
    }

    /// The last grapheme boundary in `[lineStart, lineEnd]` whose prefix is no
    /// wider than `width`.
    ///
    /// [#offsetAt]'s sibling and its opposite: that one rounds to the *nearest*
    /// caret position, which is what a click means, and this one never rounds up,
    /// which is what "as much as fits" means. A truncation that rounded to the
    /// nearest would return half a character more than it had room for on every
    /// second label.
    ///
    /// Steps by grapheme cluster for [#offsetAt]'s reason — a cut between the two
    /// halves of a surrogate pair, or between a letter and the accent over it, is
    /// not a place text can end.
    ///
    /// @param lineStart the first offset of the line, from [TextLine#start()]
    /// @param lineEnd   one past its last, from [TextLine#end()]
    /// @param width     the room available, in logical units
    /// @return an offset in `[lineStart, lineEnd]`; `lineStart` when not even one
    ///         grapheme fits
    /// @throws IndexOutOfBoundsException if either offset is outside the text
    /// @throws IllegalArgumentException  if `lineEnd` is before `lineStart`
    public int offsetFitting(int lineStart, int lineEnd, double width) {
        Objects.checkIndex(lineStart, text.length() + 1);
        Objects.checkIndex(lineEnd, text.length() + 1);
        if (lineEnd < lineStart) {
            throw new IllegalArgumentException("a line cannot end before it starts: " + lineStart + ".." + lineEnd);
        }
        if (lineStart == lineEnd || !(width > 0)) {
            // NaN lands here too, and "no room" is the honest answer to an
            // unknown width — the caller draws the mark alone.
            return lineStart;
        }

        var graphemes = BreakIterator.getCharacterInstance();
        graphemes.setText(text);

        var fitting = lineStart;
        for (var offset = graphemes.following(lineStart);
                offset != BreakIterator.DONE && offset <= lineEnd;
                offset = graphemes.next()) {

            if (widthOf(lineStart, offset) > width) {
                // Advances are non-negative, so once a prefix is too wide every
                // longer one is too. Stopping here is what keeps truncating a
                // short label out of a long paragraph from walking the paragraph.
                break;
            }
            fitting = offset;
        }
        return fitting;
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
        for (var candidate = breaks.next(); candidate != BreakIterator.DONE; candidate = breaks.next()) {

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
        return new TextLine(start, end, glyphBefore[start], glyphBefore[visible], widthOf(start, visible));
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
            throw new IllegalArgumentException("a text range cannot end before it starts: " + start + ".." + end);
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
            throw new IllegalArgumentException("a line cannot end before it starts: " + lineStart + ".." + lineEnd);
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

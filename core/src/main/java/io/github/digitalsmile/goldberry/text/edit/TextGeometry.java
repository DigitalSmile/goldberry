package io.github.digitalsmile.goldberry.text.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.TextLayout;
import io.github.digitalsmile.goldberry.text.TextLine;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;

/// Where a caret **is** on a paragraph that has been laid out.
///
/// [TextEdit] is the model — a string, a caret and an anchor — and it is
/// deliberately one-dimensional: it knows that the caret is at offset 17 and
/// nothing about where 17 landed on a screen. Everything in this class is the
/// other half, and it is the half that needs the font, the shaping and the width
/// the text wrapped at (ADR-0285).
///
/// Three questions, and every text editor asks exactly these:
///
/// - **Where do I draw the caret?** [#caretAt]
/// - **What did the user click on?** [#offsetAt]
/// - **What does `Up` mean?** [#moveLine] — which is not
///   "offset minus a line's worth of characters", because lines are not the same
///   length, and is why a `TextEdit` cannot answer it alone.
///
/// and one more that only a *selection* needs: [#selectionRects], which is a
/// rectangle per visual line rather than one box, because a selection that spans
/// a wrap is L-shaped.
///
/// ## Coordinates
///
/// Everything here is in the paragraph's own space: `(0, 0)` is the top-left of
/// the first line, y grows downwards, and one line is
/// [Font#lineHeight()][io.github.digitalsmile.goldberry.text.font.Font#lineHeight()]
/// tall. A caller drawing at an offset, inside a padding, or under a canvas
/// transform adds its own translation — which is the whole reason this takes no
/// origin.
///
/// Every method is static and every one takes the paragraph and its layout
/// together. They must be the *same* layout the text was painted with: a caret
/// measured against one wrap width and drawn against another is the bug this
/// class exists to make visible rather than possible.
///
/// ## Alignment
///
/// Each of the four questions has a second form that takes the width the text was
/// drawn in and its [TextAlign], and those are the ones to reach for in anything
/// that is not left-aligned. `Paragraph.paint` indents every line by its own share
/// of the box's slack; the three-argument forms measure from the paragraph's
/// origin, so under `text-align: center` the caret drifted away from the glyphs by
/// half the line's slack and grew as the line shortened (`docs/gaps.md` G30,
/// ADR-0318).
///
/// The short forms are kept and mean [TextAlign#START], which is what every caller
/// written before this assumed. The indent itself is
/// [TextAlign#indentOf(double, double)] — one implementation, shared with the
/// paint, because two copies of "where does this line start" is the bug rather
/// than the fix.
public final class TextGeometry {

    private TextGeometry() {}

    /// How far in `line` was drawn — the one place this class turns an alignment
    /// into a distance, and it does not compute it: [TextAlign#indentOf] is the
    /// rule, shared with the paint.
    private static double indentOf(TextLine line, double wrapWidth, TextAlign align) {
        return align.indentOf(line.width(), wrapWidth);
    }

    /// Where a caret goes, in the paragraph's own coordinates.
    ///
    /// @param x      the caret's left edge — a caret has no width here, because
    ///               how thick to draw one is a design decision and not a
    ///               measurement
    /// @param top    the top of the line it is on
    /// @param height the line's height, which is what a caret is as tall as
    /// @param line   which visual line it landed on, for a caller that scrolls
    public record Caret(double x, double top, double height, int line) {

        /// The caret as a rectangle `width` wide — what a frame fills.
        public LogicalRect rect(double width) {
            return LogicalRect.of((float) x, (float) top, (float) width, (float) height);
        }
    }

    /// Which visual line `offset` is on.
    ///
    /// The **last** line that starts at or before it, which decides the one
    /// ambiguous case: an offset at a soft wrap is on the line that begins there,
    /// so the caret at the end of a wrapped line is drawn at the start of the next
    /// one. Both answers are defensible and editors disagree; what matters is that
    /// painting, hit-testing and `Up`/`Down` all ask this method and therefore
    /// agree with each other.
    ///
    /// @throws IllegalArgumentException if the layout has no lines
    public static int lineOf(TextLayout layout, int offset) {
        Objects.requireNonNull(layout, "layout");
        var lines = layout.lines();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a paragraph that was laid out has at least one line");
        }
        for (var i = lines.size() - 1; i > 0; i--) {
            if (offset >= lines.get(i).start()) {
                return i;
            }
        }
        return 0;
    }

    /// Where the caret at `offset` is drawn, in text that starts at the leading
    /// edge of its box.
    ///
    /// @throws IndexOutOfBoundsException if the offset is outside the text
    public static Caret caretAt(Paragraph paragraph, TextLayout layout, int offset) {
        return caretAt(paragraph, layout, offset, Paragraph.UNCONSTRAINED, TextAlign.START);
    }

    /// Where the caret at `offset` is drawn, in text the painter aligned.
    ///
    /// @param wrapWidth the width the text was drawn in — what layout gave the
    ///                  box, and the same number `Paragraph.paint` was passed
    /// @param align     what the cascade said about `text-align`
    /// @throws IndexOutOfBoundsException if the offset is outside the text
    public static Caret caretAt(Paragraph paragraph, TextLayout layout, int offset, double wrapWidth, TextAlign align) {
        Objects.requireNonNull(paragraph, "paragraph");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(align, "align");
        Objects.checkIndex(offset, paragraph.text().length() + 1);

        var index = lineOf(layout, offset);
        var line = layout.lines().get(index);
        var lineHeight = paragraph.font().lineHeight();
        // `widthBetween` is the one measurement a caret may be built on: it
        // subtracts two prefix widths of the *same* shaping rather than
        // re-measuring a substring, so a caret in the middle of a ligature or a
        // kerned pair lands where the glyphs actually are.
        var x = paragraph.widthBetween(line.start(), Math.max(line.start(), offset));
        return new Caret(indentOf(line, wrapWidth, align) + x, index * lineHeight, lineHeight, index);
    }

    /// The offset a point lands on — a click, or a drag.
    ///
    /// A `y` above the paragraph is the first line and one below it is the last,
    /// which is what dragging a selection off the top or bottom of a field should
    /// do. Within a line it is [Paragraph#offsetAt] and so lands on a grapheme
    /// boundary rather than between the halves of one.
    public static int offsetAt(Paragraph paragraph, TextLayout layout, double x, double y) {
        return offsetAt(paragraph, layout, x, y, Paragraph.UNCONSTRAINED, TextAlign.START);
    }

    /// The offset a point lands on, in text the painter aligned.
    ///
    /// The `x` is where the user pressed, so the line's own indent comes **off**
    /// it before the line is asked — the mirror of what [#caretAt] adds, which is
    /// what makes the two round-trip.
    ///
    /// @param wrapWidth the width the text was drawn in
    /// @param align     what the cascade said about `text-align`
    public static int offsetAt(
            Paragraph paragraph, TextLayout layout, double x, double y, double wrapWidth, TextAlign align) {
        Objects.requireNonNull(paragraph, "paragraph");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(align, "align");
        var lines = layout.lines();
        if (lines.isEmpty()) {
            return 0;
        }
        var lineHeight = paragraph.font().lineHeight();
        var index = (int) Math.floor(y / lineHeight);
        index = Math.clamp(index, 0, lines.size() - 1);
        var line = lines.get(index);
        return paragraph.offsetAt(line.start(), line.end(), x - indentOf(line, wrapWidth, align));
    }

    /// `Up`, `Down`, `PageUp`, `PageDown` — `lines` visual lines from `offset`,
    /// keeping the column.
    ///
    /// **The column is an `x`, not a character count.** Walking down through a
    /// short line and out the other side must come back to the column it started
    /// in, which is what `desiredX` is for: a caller keeps the x it had when the
    /// run of vertical movement began and hands the same one back each time. Pass
    /// `Double.NaN` to start a new run from wherever the caret is now.
    ///
    /// Moving off the top goes to the start of the text and off the bottom to the
    /// end, rather than doing nothing — the caret ends up somewhere the user
    /// asked for in both cases.
    public static int moveLine(Paragraph paragraph, TextLayout layout, int offset, int lines, double desiredX) {
        return moveLine(paragraph, layout, offset, lines, desiredX, Paragraph.UNCONSTRAINED, TextAlign.START);
    }

    /// `Up`, `Down`, `PageUp`, `PageDown` in text the painter aligned.
    ///
    /// **`desiredX` is in the painted space**, because it is the x a caller read
    /// off a [Caret] — so it is the target line's indent that comes off it, and
    /// not the one the caret started on. That is what makes a run of `Down`
    /// through lines of different lengths keep the column it looks like it is
    /// keeping.
    ///
    /// @param wrapWidth the width the text was drawn in
    /// @param align     what the cascade said about `text-align`
    public static int moveLine(
            Paragraph paragraph,
            TextLayout layout,
            int offset,
            int lines,
            double desiredX,
            double wrapWidth,
            TextAlign align) {

        Objects.requireNonNull(paragraph, "paragraph");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(align, "align");
        var all = layout.lines();
        if (all.isEmpty()) {
            return 0;
        }
        var from = lineOf(layout, offset);
        var target = from + lines;
        if (target < 0) {
            return 0;
        }
        if (target >= all.size()) {
            return paragraph.text().length();
        }
        var column = Double.isNaN(desiredX)
                ? caretAt(paragraph, layout, offset, wrapWidth, align).x()
                : desiredX;
        var line = all.get(target);
        return paragraph.offsetAt(line.start(), line.end(), column - indentOf(line, wrapWidth, align));
    }

    /// The rectangles covering `start`..`end`, one per visual line.
    ///
    /// Empty when the two are equal: a caret is not a selection, and a caller
    /// painting a zero-width highlight would draw a second caret in the
    /// selection's colour.
    ///
    /// A line whose selection runs past its last character is extended by a
    /// space's width, so that a selected newline is visible as the end of a line
    /// rather than as nothing at all — which is what every editor draws and what
    /// nobody can name until it is missing.
    public static List<LogicalRect> selectionRects(Paragraph paragraph, TextLayout layout, int start, int end) {
        return selectionRects(paragraph, layout, start, end, Paragraph.UNCONSTRAINED, TextAlign.START);
    }

    /// The rectangles covering `start`..`end` in text the painter aligned.
    ///
    /// A highlight drifts exactly as a caret does, and for the same reason — a
    /// selection is geometry the frame already had ([ADR-0301]), and this is that
    /// geometry told where the glyphs went.
    ///
    /// @param wrapWidth the width the text was drawn in
    /// @param align     what the cascade said about `text-align`
    public static List<LogicalRect> selectionRects(
            Paragraph paragraph, TextLayout layout, int start, int end, double wrapWidth, TextAlign align) {
        Objects.requireNonNull(paragraph, "paragraph");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(align, "align");
        var from = Math.min(start, end);
        var to = Math.max(start, end);
        Objects.checkIndex(from, paragraph.text().length() + 1);
        Objects.checkIndex(to, paragraph.text().length() + 1);
        if (from == to) {
            return List.of();
        }

        var lineHeight = paragraph.font().lineHeight();
        var rects = new ArrayList<LogicalRect>();
        var lines = layout.lines();
        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var lineFrom = Math.max(from, line.start());
            var lineTo = Math.min(to, line.end());
            if (lineFrom >= lineTo && !(from <= line.start() && to > line.end())) {
                continue;
            }
            lineFrom = Math.clamp(lineFrom, line.start(), line.end());
            lineTo = Math.clamp(lineTo, line.start(), line.end());

            var indent = indentOf(line, wrapWidth, align);
            var left = indent + paragraph.widthBetween(line.start(), lineFrom);
            var right = indent + paragraph.widthBetween(line.start(), lineTo);
            if (to > line.end()) {
                // The selection continues past this line, so the break itself is
                // inside it.
                right += paragraph.font().widthOf(" ");
            }
            if (right <= left) {
                continue;
            }
            rects.add(
                    LogicalRect.of((float) left, (float) (i * lineHeight), (float) (right - left), (float) lineHeight));
        }
        return List.copyOf(rects);
    }
}

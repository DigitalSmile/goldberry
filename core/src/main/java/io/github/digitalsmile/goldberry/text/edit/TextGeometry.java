package io.github.digitalsmile.goldberry.text.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.TextLayout;

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
public final class TextGeometry {

    private TextGeometry() {}

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

    /// Where the caret at `offset` is drawn.
    ///
    /// @throws IndexOutOfBoundsException if the offset is outside the text
    public static Caret caretAt(Paragraph paragraph, TextLayout layout, int offset) {
        Objects.requireNonNull(paragraph, "paragraph");
        Objects.requireNonNull(layout, "layout");
        Objects.checkIndex(offset, paragraph.text().length() + 1);

        var index = lineOf(layout, offset);
        var line = layout.lines().get(index);
        var lineHeight = paragraph.font().lineHeight();
        // `widthBetween` is the one measurement a caret may be built on: it
        // subtracts two prefix widths of the *same* shaping rather than
        // re-measuring a substring, so a caret in the middle of a ligature or a
        // kerned pair lands where the glyphs actually are.
        var x = paragraph.widthBetween(line.start(), Math.max(line.start(), offset));
        return new Caret(x, index * lineHeight, lineHeight, index);
    }

    /// The offset a point lands on — a click, or a drag.
    ///
    /// A `y` above the paragraph is the first line and one below it is the last,
    /// which is what dragging a selection off the top or bottom of a field should
    /// do. Within a line it is [Paragraph#offsetAt] and so lands on a grapheme
    /// boundary rather than between the halves of one.
    public static int offsetAt(Paragraph paragraph, TextLayout layout, double x, double y) {
        Objects.requireNonNull(paragraph, "paragraph");
        Objects.requireNonNull(layout, "layout");
        var lines = layout.lines();
        if (lines.isEmpty()) {
            return 0;
        }
        var lineHeight = paragraph.font().lineHeight();
        var index = (int) Math.floor(y / lineHeight);
        index = Math.clamp(index, 0, lines.size() - 1);
        var line = lines.get(index);
        return paragraph.offsetAt(line.start(), line.end(), x);
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
        Objects.requireNonNull(paragraph, "paragraph");
        Objects.requireNonNull(layout, "layout");
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
        var column = Double.isNaN(desiredX) ? caretAt(paragraph, layout, offset).x() : desiredX;
        var line = all.get(target);
        return paragraph.offsetAt(line.start(), line.end(), column);
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
        Objects.requireNonNull(paragraph, "paragraph");
        Objects.requireNonNull(layout, "layout");
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

            var left = paragraph.widthBetween(line.start(), lineFrom);
            var right = paragraph.widthBetween(line.start(), lineTo);
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

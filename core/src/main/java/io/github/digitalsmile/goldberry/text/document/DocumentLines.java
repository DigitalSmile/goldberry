package io.github.digitalsmile.goldberry.text.document;

import java.util.AbstractList;
import java.util.Arrays;

import io.github.digitalsmile.goldberry.text.TextLine;

/// A [TextDocument] broken into visual lines at one width.
///
/// A `List<TextLine>` so that everything written against
/// [io.github.digitalsmile.goldberry.text.Paragraph#layout]'s lines keeps
/// working — and a **computed** one rather than a built one, because a document
/// of ten thousand lines would otherwise allocate ten thousand records per
/// frame to answer two questions about three of them.
///
/// ## The offsets are the document's, the glyphs are a line's
///
/// [TextLine#start] and [TextLine#end] index the whole text, which is what every
/// caret, selection and hit test here is expressed in. The glyph range does
/// **not**: it indexes the hard line's own paragraph, because that is the run
/// the glyphs are in and there is no document-wide run to index.
///
/// So this answers *where* and never *what to draw*. A caller that wants glyphs
/// takes the slice of the text between two line starts and shapes that, which is
/// what `text-area` does with the rows on screen ([ADR-0388]).
public final class DocumentLines extends AbstractList<TextLine> {

    private final TextDocument document;
    private final double width;

    /// Where each hard line's first visual line is; one longer than the hard
    /// line count, so the last entry is the total.
    private final int[] visualStarts;

    DocumentLines(TextDocument document, double width, int[] visualStarts) {
        this.document = document;
        this.width = width;
        this.visualStarts = visualStarts;
    }

    /// How many visual lines the whole document occupies.
    @Override
    public int size() {
        return visualStarts[visualStarts.length - 1];
    }

    /// Visual line `index`, in the whole text's offsets.
    @Override
    public TextLine get(int index) {
        var k = hardLineOf(index);
        var start = document.startOf(k);
        var local = localLine(k, index - visualStarts[k]);
        return new TextLine(
                start + local.start(), start + local.end(), local.glyphStart(), local.glyphEnd(), local.width());
    }

    /// Which hard line visual line `index` belongs to.
    public int hardLineOf(int index) {
        var at = Arrays.binarySearch(visualStarts, Math.clamp(index, 0, Math.max(0, size() - 1)));
        var k = at >= 0 ? at : -at - 2;
        // A hard line that wrapped has several visual lines and only the first
        // of them is in the table, so a miss lands on the line before it. An
        // exact hit can still be a run of empty hard lines, none of which it is:
        // the table repeats no value, because every hard line takes at least one
        // visual line.
        return Math.clamp(k, 0, document.hardLineCount() - 1);
    }

    /// Where hard line `k`'s first visual line is.
    public int firstVisualOf(int k) {
        return visualStarts[k];
    }

    /// Which visual line `offset` is on — the last one that starts at or before
    /// it, which puts a caret sitting exactly on a wrap at the start of the line
    /// it is about to type into.
    public int indexOf(int offset) {
        var k = document.hardLineAt(offset);
        var local = offset - document.startOf(k);
        var lines = document.paragraphOf(k).layout(width);
        var within = 0;
        for (var i = 0; i < lines.lines().size(); i++) {
            if (lines.lines().get(i).start() <= local) {
                within = i;
            }
        }
        return visualStarts[k] + within;
    }

    /// The line `index` as its own paragraph sees it.
    private TextLine localLine(int k, int within) {
        var lines = document.paragraphOf(k).layout(width);
        if (lines.lines().isEmpty()) {
            return new TextLine(0, 0, 0, 0, 0);
        }
        return lines.lines().get(Math.clamp(within, 0, lines.lines().size() - 1));
    }
}

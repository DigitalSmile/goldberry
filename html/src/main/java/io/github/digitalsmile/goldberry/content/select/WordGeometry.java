package io.github.digitalsmile.goldberry.content.select;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.text.Paragraph;

/// Where every word of a rendered document ended up, and what it says.
///
/// The thing a document needs in order to be **selectable** and the one thing a
/// widget cannot work out for itself: `build` and `render` both run before Yoga, so
/// a word describing itself has no idea where it is (ADR-0080). Three facilities
/// answer geometry questions in this toolkit and only one carries a position —
/// [io.github.digitalsmile.goldberry.input.handler.Located] — so each [Word] reports
/// its own rectangle here, once a frame, when it changes (ADR-0301).
///
/// ## What is registered when
///
/// | | arrives | from |
/// |---|---|---|
/// | text, prefix, block | at **build**, in document order | the fold |
/// | the shaped paragraph | at **render** | [Word#render] |
/// | the rectangle and its clip | after **layout**, on a change | [Word#located] |
///
/// The index is the word's position in document order and is assigned by the build,
/// so the three sources agree without any of them knowing about the others.
///
/// ## It is mutable, and confined to the UI thread
///
/// Everything here happens inside a frame on the thread that owns the window. The
/// entries are overwritten in place rather than rebuilt, because a scroll moves six
/// hundred rectangles and allocating six hundred records per frame to say so would be
/// the cost this whole file exists to avoid.
public final class WordGeometry {

    /// One word: what it says, where it is, and what it is part of.
    ///
    /// A class rather than a record because a frame updates two of its fields and
    /// keeps the rest — see the note about allocation above.
    static final class Entry {

        private String text = "";

        /// What separates this word from the one before it when both are copied —
        /// `""` inside a token, `" "` between words, `"\n"` at a block boundary.
        private String prefix = "";

        /// Which block this word belongs to, so a triple-click can take one.
        private int block;

        private @Nullable Paragraph paragraph;

        private @Nullable LogicalRect rect;

        /// What the nearest `overflow` above it confines it to, so a word scrolled
        /// out of sight is not hit by a pointer over the thing it scrolled under.
        private @Nullable LogicalRect clip;

        String text() {
            return text;
        }

        int block() {
            return block;
        }

        @Nullable
        LogicalRect rect() {
            return rect;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /// How many words this build registered — the entries past it are last build's
    /// and are not visible to anything.
    private int size;

    /// Whether the document this build described says something different from the
    /// one before it.
    private boolean changed;

    /// Starts a build. Everything after this call re-registers from index zero.
    void beginBuild() {
        size = 0;
        changed = false;
    }

    /// Registers the `index`th word of the document being built.
    ///
    /// @return the entry, so [Word] can hold it rather than look it up per frame
    Entry word(int index, String text, String prefix, int block) {
        while (entries.size() <= index) {
            entries.add(new Entry());
            changed = true;
        }
        var entry = entries.get(index);
        if (!entry.text.equals(text) || !entry.prefix.equals(prefix) || entry.block != block) {
            // A word that says something different is a different document, and a
            // selection measured against the old one would highlight the wrong
            // characters. Recorded rather than acted on here: what to do about it is
            // the selection's decision (see `SelectableDocument`).
            changed = true;
            entry.text = text;
            entry.prefix = prefix;
            entry.block = block;
            entry.paragraph = null;
        }
        size = Math.max(size, index + 1);
        return entry;
    }

    /// Ends a build, and says whether the document changed under the selection.
    boolean endBuild() {
        if (size != entries.size()) {
            // Shorter than last time: the tail is stale and must not be hit-tested.
            entries.subList(size, entries.size()).clear();
            changed = true;
        }
        return changed;
    }

    /// The shaped paragraph a word drew with, which is what maps an **x** to a
    /// character offset.
    static void shaped(Entry entry, Paragraph paragraph) {
        entry.paragraph = paragraph;
    }

    /// Where the last frame put a word, in the window's own coordinates.
    static void placed(Entry entry, LogicalRect rect, LogicalRect clip) {
        entry.rect = rect;
        entry.clip = clip;
    }

    /// How many words the document has.
    public int size() {
        return size;
    }

    String textOf(int index) {
        return entries.get(index).text;
    }

    int blockOf(int index) {
        return entries.get(index).block;
    }

    /// The caret at the end of the document — what `Ctrl+A` selects up to.
    Caret end() {
        return size == 0
                ? Caret.NONE
                : new Caret(size - 1, entries.get(size - 1).text.length());
    }

    /// The word and character the pointer is over, or the nearest one.
    ///
    /// **Nearest rather than nothing**, because a drag spends most of its time in the
    /// gaps: between two words, past the end of a line, in a margin. A selection that
    /// stopped when the pointer left a word would be unusable, so the fall-back is the
    /// word whose rectangle is closest — vertically first, which is what makes
    /// dragging down the left margin select whole lines.
    Caret at(double x, double y) {
        Entry nearest = null;
        var nearestIndex = 0;
        var nearestDistance = Double.MAX_VALUE;
        for (var index = 0; index < size; index++) {
            var entry = entries.get(index);
            var rect = entry.rect;
            if (rect == null || (entry.clip != null && !contains(entry.clip, x, y))) {
                // Never laid out, or scrolled out of sight under something that clips
                // it -- either way the pointer is not over it however close it looks.
                continue;
            }
            if (contains(rect, x, y)) {
                return new Caret(index, offsetIn(entry, x));
            }
            var distance = distanceTo(rect, x, y);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entry;
                nearestIndex = index;
            }
        }
        if (nearest == null) {
            return Caret.NONE;
        }
        // Past the end of a word rather than inside it: a drag that stopped in the
        // margin to the right of a line means "to the end of that line".
        var rect = nearest.rect;
        var after = rect != null && x > rect.right();
        return new Caret(nearestIndex, after ? nearest.text.length() : offsetIn(nearest, x));
    }

    /// Which character of a word an **x** falls on.
    ///
    /// [Paragraph#offsetAt] is the text stack's own answer — the same arithmetic a
    /// caret in a `text-input` uses — so a selection lands between the glyphs a reader
    /// sees rather than at a proportion of the box.
    private static int offsetIn(Entry entry, double x) {
        var rect = entry.rect;
        var paragraph = entry.paragraph;
        if (rect == null || paragraph == null) {
            return 0;
        }
        return paragraph.offsetAt(0, entry.text.length(), x - rect.left());
    }

    /// Where a caret sits inside its word, in window coordinates.
    private static double xOf(Entry entry, int offset) {
        var rect = entry.rect;
        if (rect == null) {
            return 0;
        }
        var paragraph = entry.paragraph;
        if (paragraph == null) {
            return rect.left();
        }
        return rect.left() + paragraph.widthBetween(0, Math.min(offset, entry.text.length()));
    }

    /// The rectangles a selection covers, in window coordinates.
    ///
    /// One per word rather than one per line, which is what makes this arithmetic
    /// instead of a line-breaking algorithm: the words already **are** the lines, laid
    /// out by the wrapping row they are in, so a selection is the union of the boxes
    /// it touches and the two partial ones at its ends.
    List<LogicalRect> rectangles(Caret from, Caret to) {
        if (from.isNone() || to.isNone() || from.equals(to)) {
            return List.of();
        }
        var start = from.min(to);
        var end = from.max(to);
        var rectangles = new ArrayList<LogicalRect>();
        for (var index = start.word(); index <= end.word() && index < size; index++) {
            var entry = entries.get(index);
            var rect = entry.rect;
            if (rect == null) {
                continue;
            }
            var left = index == start.word() ? xOf(entry, start.offset()) : rect.left();
            var right = index == end.word() ? xOf(entry, end.offset()) : (double) rect.right();
            if (right > left) {
                rectangles.add(LogicalRect.of((float) left, rect.top(), (float) (right - left), rect.height()));
            }
        }
        return bridged(rectangles);
    }

    /// The same rectangles with the **spaces between words** covered.
    ///
    /// A paragraph is a wrapping row of word boxes and the spaces in it are the row's
    /// `gap` — so a selection drawn from the boxes alone is a row of stripes with white
    /// gaps between them, which reads as several selections rather than one. Where two
    /// selected words sit on the same line with only a space between them, the first is
    /// stretched to meet the second.
    ///
    /// The bridge is capped at half a line's height, which is comfortably more than the
    /// 0.25em a paragraph puts between words and comfortably less than the padding
    /// between two table cells — so a selection across a row does not paint over the
    /// rule between them.
    private static List<LogicalRect> bridged(List<LogicalRect> rectangles) {
        if (rectangles.size() < 2) {
            return List.copyOf(rectangles);
        }
        var joined = new ArrayList<LogicalRect>(rectangles.size());
        for (var index = 0; index < rectangles.size(); index++) {
            var rect = rectangles.get(index);
            if (index + 1 < rectangles.size()) {
                var next = rectangles.get(index + 1);
                var gap = next.left() - rect.right();
                if (Math.abs(next.top() - rect.top()) < 0.5 && gap > 0 && gap <= rect.height() / 2) {
                    rect = LogicalRect.of(rect.left(), rect.top(), next.left() - rect.left(), rect.height());
                }
            }
            joined.add(rect);
        }
        return List.copyOf(joined);
    }

    /// The selected text, with the separators the document implies.
    ///
    /// The prefixes are the fold's: `""` between two pieces of one word, `" "` between
    /// words, `"\n"` at the start of a block. A selection that pasted `Thequickbrown`
    /// would be a selection nobody uses twice, and the renderer is the only thing that
    /// knows where the spaces went — a wrapping row draws them as gaps rather than as
    /// characters.
    String text(Caret from, Caret to) {
        if (from.isNone() || to.isNone() || from.equals(to)) {
            return "";
        }
        var start = from.min(to);
        var end = from.max(to);
        var text = new StringBuilder();
        for (var index = start.word(); index <= end.word() && index < size; index++) {
            var entry = entries.get(index);
            if (index > start.word()) {
                text.append(entry.prefix);
            }
            var word = entry.text;
            var left = index == start.word() ? Math.min(start.offset(), word.length()) : 0;
            var right = index == end.word() ? Math.min(end.offset(), word.length()) : word.length();
            if (right > left) {
                text.append(word, left, right);
            }
        }
        return text.toString();
    }

    /// The whole of the word at `caret`, for a double-click.
    Caret[] wordAt(Caret caret) {
        if (caret.isNone() || caret.word() >= size) {
            return new Caret[] {Caret.NONE, Caret.NONE};
        }
        return new Caret[] {
            new Caret(caret.word(), 0),
            new Caret(caret.word(), textOf(caret.word()).length())
        };
    }

    /// The whole of the block at `caret`, for a triple-click — every word the fold
    /// gave the same block number to, which is one paragraph, heading, cell or line of
    /// a fence.
    Caret[] blockAt(Caret caret) {
        if (caret.isNone() || caret.word() >= size) {
            return new Caret[] {Caret.NONE, Caret.NONE};
        }
        var block = blockOf(caret.word());
        var first = caret.word();
        while (first > 0 && blockOf(first - 1) == block) {
            first--;
        }
        var last = caret.word();
        while (last + 1 < size && blockOf(last + 1) == block) {
            last++;
        }
        return new Caret[] {new Caret(first, 0), new Caret(last, textOf(last).length())};
    }

    private static boolean contains(LogicalRect rect, double x, double y) {
        return x >= rect.left() && x <= rect.right() && y >= rect.top() && y <= rect.bottom();
    }

    /// How far a point is from a rectangle, with **vertical distance dominating**.
    ///
    /// A line's worth of pixels is weighted so that a pointer below a line picks a word
    /// on the line under it rather than one far along the line above — which is what a
    /// drag down the margin of a document has to do.
    private static double distanceTo(LogicalRect rect, double x, double y) {
        var dx = Math.max(0, Math.max(rect.left() - x, x - rect.right()));
        var dy = Math.max(0, Math.max(rect.top() - y, y - rect.bottom()));
        return dy * 1000 + dx;
    }
}

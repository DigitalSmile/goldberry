package dev.goldberry.content.select;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.paint.InlineRanges;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.Paragraph;

/// Where every word of a rendered document ended up, and what it says.
///
/// The fold registers logical words and block boundaries in document order.
/// [ParagraphLine] reports the shaped run and its visible fragments. List
/// markers and preformatted lines report their own bounds through [Word].
///
/// ## Entries belong to a block, not to the document
///
/// The words are stored **per block** and flattened afterwards, which looks like a
/// detail and is what makes a keystroke cheap. An entry is the identity a word is
/// reconciled and memoized on, so where it is stored decides what a keystroke costs:
/// with one flat array, typing a space in the first paragraph gives every word in the
/// note a new entry and the document is rebuilt, re-measured and re-laid-out from the
/// cursor down. Held per block, a block that did not change keeps its own entries
/// however many words appeared above it, and the only thing that moves is the flat
/// view — which is rebuilt from the blocks and costs a reference copy per word.
///
/// Selection, layout and pointer events use this geometry on the UI thread.
public final class WordGeometry {

    /// One word: what it says, where it is, and what it is part of.
    ///
    /// A class rather than a record because a frame updates two of its fields and
    /// keeps the document identity across frames.
    ///
    /// Public because [Word]'s canonical constructor names it, and a record's
    /// canonical constructor is as visible as the record. The package is not
    /// exported, so nothing outside this module sees it either way.
    public static final class Entry {

        private String text = "";

        /// What separates this word from the one before it when both are copied —
        /// `""` inside a token, `" "` between words, `"\n"` at a block boundary.
        private String prefix = "";

        /// Which block this word belongs to, so a triple-click can take one.
        private int block;

        private @Nullable Paragraph paragraph;
        private int startInParagraph;

        private @Nullable LogicalRect rect;

        /// What the nearest `overflow` above it confines it to, so a word scrolled
        /// out of sight is not hit by a pointer over the thing it scrolled under.
        private @Nullable LogicalRect clip;

        private List<InlineRanges.Piece> pieces = List.of();

        private List<LogicalRect> separator = List.of();

        boolean spaceBefore() {
            return prefix.equals(" ");
        }

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

    /// One block's words, in the order the fold minted them.
    ///
    /// A class of its own so that the **list** is the thing a block owns: a fold that
    /// skipped a block it had already built leaves this untouched, and the entries in
    /// it are the same objects the widgets it kept are still holding.
    static final class Block {

        private final List<Entry> words = new ArrayList<>();

        /// How many of them this build registered. Left alone for a block the fold
        /// did not re-walk, which is what makes skipping one safe.
        private int size;
    }

    private final List<Block> blocks = new ArrayList<>();

    /// The words of every block, end to end — what a [Caret] indexes into.
    ///
    /// Derived, and rebuilt only when the blocks change shape.
    private List<Entry> entries = List.of();

    /// How many blocks this build registered or kept.
    private int blockCount;

    /// Whether the document this build described says something different from the
    /// one before it.
    private boolean changed;

    /// Whether a block was added, dropped or resized, so the flat view is stale.
    private boolean reshaped;

    /// Starts a build. Everything after this call re-registers from block zero.
    void beginBuild() {
        blockCount = 0;
        changed = false;
        reshaped = false;
    }

    /// Starts the `index`th block of the document being built, emptying it.
    ///
    /// @return the block, so the minter can hold it rather than look it up per word
    Block beginBlock(int index) {
        while (blocks.size() <= index) {
            blocks.add(new Block());
            changed = true;
            reshaped = true;
        }
        blockCount = Math.max(blockCount, index + 1);
        var block = blocks.get(index);
        block.size = 0;
        return block;
    }

    /// Keeps every block up to but not including `through`, exactly as it was.
    ///
    /// What a memoized block is: the fold did not mint its words again, so nothing
    /// here has been told about them and they must not be treated as dropped.
    ///
    /// **A kept block is never shortened, and that is what makes this safe.** The tail
    /// of a block's words is dropped in [#endBuild] when the fold walked it and minted
    /// fewer than last time, and `size` is only reset by [#beginBlock] — which the
    /// minter calls and a kept block does not reach. So the entries a kept widget is
    /// holding are still in the list, in the same order, whoever else moved.
    ///
    /// **So the block count is the whole question.** There used to be a scan here for
    /// a block left with no words, and there is no build that can produce one:
    /// [WordMinter] opens a block only in the line above the one that registers its
    /// first word, so every block this list holds has at least one, and [#endBuild]
    /// drops the tail a shorter document no longer has rather than emptying it.
    ///
    /// @return false when this geometry no longer holds them, so the fold has to
    ///         build them after all
    boolean keepBlocks(int through) {
        if (through > blocks.size()) {
            return false;
        }
        blockCount = Math.max(blockCount, through);
        return true;
    }

    /// Registers the `position`th word of `block`.
    ///
    /// @return the entry, so [Word] can hold it rather than look it up per frame
    Entry word(Block block, int position, String text, String prefix) {
        while (block.words.size() <= position) {
            block.words.add(new Entry());
            changed = true;
            reshaped = true;
        }
        var entry = block.words.get(position);
        if (!entry.text.equals(text) || !entry.prefix.equals(prefix)) {
            // A word that says something different is a different document, and a
            // selection measured against the old one would highlight the wrong
            // characters. Recorded rather than acted on here: what to do about it is
            // the selection's decision (see `SelectableDocument`).
            changed = true;
            entry.text = text;
            entry.prefix = prefix;
            entry.paragraph = null;
        }
        block.size = Math.max(block.size, position + 1);
        return entry;
    }

    /// Ends a build, and says whether the document changed under the selection.
    boolean endBuild() {
        if (blockCount != blocks.size()) {
            // Fewer blocks than last time: the tail is stale and must not be
            // hit-tested.
            blocks.subList(blockCount, blocks.size()).clear();
            changed = true;
            reshaped = true;
        }
        for (var block : blocks) {
            if (block.size != block.words.size()) {
                block.words.subList(block.size, block.words.size()).clear();
                changed = true;
                reshaped = true;
            }
        }
        if (reshaped) {
            flatten();
        }
        return changed;
    }

    /// Lays the blocks' words end to end, and tells each word which block it is in.
    ///
    /// The block number is written here rather than at registration because it is a
    /// fact about the document and not about the block: a block that kept its entries
    /// may still have moved down the page.
    private void flatten() {
        var flat = new ArrayList<Entry>();
        for (var index = 0; index < blocks.size(); index++) {
            for (var entry : blocks.get(index).words) {
                entry.block = index;
                flat.add(entry);
            }
        }
        entries = flat;
    }

    /// The shaped paragraph a word drew with, which is what maps an **x** to a
    /// character offset.
    static void shaped(Entry entry, Paragraph paragraph) {
        shaped(entry, paragraph, 0);
    }

    static void shaped(Entry entry, Paragraph paragraph, int start) {
        entry.paragraph = paragraph;
        entry.startInParagraph = start;
    }

    /// Where the last frame put a word, in the window's own coordinates.
    static void placed(Entry entry, LogicalRect rect, LogicalRect clip) {
        entry.separator = List.of();
        entry.pieces = List.of(new InlineRanges.Piece(0, entry.text.length(), rect, false, 0));
        entry.rect = rect;
        entry.clip = clip;
    }

    static void hidden(Entry entry) {
        entry.rect = null;
        entry.separator = List.of();
        entry.pieces = List.of();
    }

    static void separator(Entry entry, List<InlineRanges.Piece> pieces, float left, float top) {
        entry.separator =
                pieces.stream().map(piece -> piece.bounds().offsetBy(left, top)).toList();
    }

    static void placed(Entry entry, List<InlineRanges.Piece> pieces, float left, float top, LogicalRect clip) {
        entry.clip = clip;
        entry.pieces = pieces.stream()
                .map(piece -> new InlineRanges.Piece(
                        piece.start(), piece.end(), piece.bounds().offsetBy(left, top), piece.rtl(), piece.gapAfter()))
                .toList();
        entry.rect = entry.pieces.isEmpty() ? null : entry.pieces.getFirst().bounds();
    }

    public int size() {
        return entries.size();
    }

    String textOf(int index) {
        return entries.get(index).text;
    }

    int blockOf(int index) {
        return entries.get(index).block;
    }

    /// The caret at the end of the document — what `Ctrl+A` selects up to.
    Caret end() {
        return entries.isEmpty()
                ? Caret.NONE
                : new Caret(entries.size() - 1, entries.getLast().text.length());
    }

    /// The word and character the pointer is over, or the nearest one.
    ///
    /// **Nearest rather than nothing**, because a drag spends most of its time in the
    /// gaps: between two words, past the end of a line, in a margin. A selection that
    /// stopped when the pointer left a word would be unusable, so the fall-back is the
    /// word whose rectangle is closest — vertically first, which is what makes
    /// dragging down the left margin select whole lines.
    Caret at(double x, double y) {
        int nearest = -1;
        InlineRanges.Piece closest = null;
        var best = Double.MAX_VALUE;
        for (var index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            if (entry.clip != null && !contains(entry.clip, x, y)) {
                continue;
            }
            for (var piece : entry.pieces) {
                var distance = distanceTo(piece.bounds(), x, y);
                if (distance == 0) {
                    return new Caret(index, offsetIn(entry, piece, x));
                }
                if (distance < best) {
                    nearest = index;
                    closest = piece;
                    best = distance;
                }
            }
        }
        return closest == null ? Caret.NONE : new Caret(nearest, offsetIn(entries.get(nearest), closest, x));
    }

    private static int offsetIn(Entry entry, InlineRanges.Piece piece, double x) {
        if (entry.text.isEmpty()) {
            return 0;
        }
        var paragraph = Objects.requireNonNull(entry.paragraph);
        var base = entry.startInParagraph;
        var local = x - piece.bounds().left() - (piece.rtl() ? piece.gapAfter() : 0);
        return paragraph.offsetAt(base + piece.start(), base + piece.end(), local) - base;
    }

    /// The visible pieces of the selected range, including its shaped spaces.
    List<LogicalRect> rectangles(Caret from, Caret to) {
        if (from.isNone() || to.isNone() || from.equals(to)) {
            return List.of();
        }
        var start = from.min(to);
        var end = from.max(to);
        var rectangles = new ArrayList<LogicalRect>();
        for (var index = start.word(); index <= end.word() && index < entries.size(); index++) {
            var entry = entries.get(index);
            var rect = entry.rect;
            if (rect == null) {
                continue;
            }
            if (index > start.word()) {
                rectangles.addAll(entry.separator);
            }
            var fromOffset = index == start.word() ? start.offset() : 0;
            var paragraph = Objects.requireNonNull(entry.paragraph);
            for (var piece : entry.pieces) {
                var a = Math.max(fromOffset, piece.start());
                var b = index == end.word() ? Math.min(end.offset(), piece.end()) : piece.end();
                if (a >= b) {
                    continue;
                }
                var bounds = piece.bounds();
                var base = entry.startInParagraph;
                var x = piece.rtl()
                        ? paragraph.widthBetween(base + b, base + piece.end())
                        : paragraph.widthBetween(base + piece.start(), base + a);
                var extra = b == piece.end() ? piece.gapAfter() : 0;
                if (piece.rtl() && b < piece.end()) {
                    x += piece.gapAfter();
                }
                rectangles.add(LogicalRect.of(
                        bounds.left() + (float) x,
                        bounds.top(),
                        (float) (paragraph.widthBetween(base + a, base + b) + extra),
                        bounds.height()));
            }
        }
        return List.copyOf(rectangles);
    }

    /// The selected text, with the separators the document implies.
    String text(Caret from, Caret to) {
        if (from.isNone() || to.isNone() || from.equals(to)) {
            return "";
        }
        var start = from.min(to);
        var end = from.max(to);
        var text = new StringBuilder();
        for (var index = start.word(); index <= end.word() && index < entries.size(); index++) {
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
        if (caret.isNone() || caret.word() >= entries.size()) {
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
        if (caret.isNone() || caret.word() >= entries.size()) {
            return new Caret[] {Caret.NONE, Caret.NONE};
        }
        var block = blockOf(caret.word());
        var first = caret.word();
        while (first > 0 && blockOf(first - 1) == block) {
            first--;
        }
        var last = caret.word();
        while (last + 1 < entries.size() && blockOf(last + 1) == block) {
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

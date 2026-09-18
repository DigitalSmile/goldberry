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
/// The entry is the word's identity and is handed out by the build, so the three
/// sources agree without any of them knowing about the others.
///
/// ## Entries belong to a block, not to the document
///
/// The words are stored **per block** and flattened afterwards, which looks like a
/// detail and is the whole of [ADR-0389]. An entry is the identity a word is
/// reconciled and memoized on, so where it is stored decides what a keystroke costs:
/// with one flat array, typing a space in the first paragraph gives every word in the
/// note a new entry and the document is rebuilt, re-measured and re-laid-out from the
/// cursor down. Held per block, a block that did not change keeps its own entries
/// however many words appeared above it, and the only thing that moves is the flat
/// view — which is rebuilt from the blocks and costs a reference copy per word.
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
        entry.paragraph = paragraph;
    }

    /// Where the last frame put a word, in the window's own coordinates.
    static void placed(Entry entry, LogicalRect rect, LogicalRect clip) {
        entry.rect = rect;
        entry.clip = clip;
    }

    /// How many words the document has.
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
        Entry nearest = null;
        var nearestIndex = 0;
        var nearestDistance = Double.MAX_VALUE;
        for (var index = 0; index < entries.size(); index++) {
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
    ///
    /// **A word that drew a child has no paragraph of its own**, and then the box is
    /// all there is — see [#across].
    private static int offsetIn(Entry entry, double x) {
        var rect = entry.rect;
        if (rect == null) {
            return 0;
        }
        var paragraph = entry.paragraph;
        if (paragraph == null) {
            var length = entry.text.length();
            if (length == 0 || rect.width() <= 0) {
                return 0;
            }
            return Math.clamp(Math.round((x - rect.left()) / rect.width() * length), 0, length);
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
            return across(entry, rect, offset);
        }
        return rect.left() + paragraph.widthBetween(0, Math.min(offset, entry.text.length()));
    }

    /// An offset inside a word that **drew something instead of text**, as a
    /// proportion of the box it landed in.
    ///
    /// A link is a `button` and an image a `picture` ([Word]), so those words never
    /// reach [#shaped] and have no paragraph to measure against: the label is shaped by
    /// the control, in the control's own style, and nothing hands that paragraph back
    /// here. Answering `rect.left()` for every offset — which this did — makes the two
    /// ends of a link the same place, so a selection ending inside one washes nothing
    /// of it and a double-click on one highlights nothing at all.
    ///
    /// Proportional rather than exact, and deliberately: the *ends* are what a reader
    /// can see are right — offset zero is the left edge and the last offset is the
    /// right one, which is the whole of a double-click and of a drag across a link —
    /// and a caret halfway through a label lands within a glyph of where it belongs.
    /// An image carries no text (a copy of one is nothing), so its every offset is its
    /// left edge and a selection either covers the box or does not.
    private static double across(Entry entry, LogicalRect rect, int offset) {
        var length = entry.text.length();
        if (length == 0) {
            return rect.left();
        }
        return rect.left() + rect.width() * (double) Math.clamp(offset, 0, length) / length;
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
        for (var index = start.word(); index <= end.word() && index < entries.size(); index++) {
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

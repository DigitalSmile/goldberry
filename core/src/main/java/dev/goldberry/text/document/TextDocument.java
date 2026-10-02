package dev.goldberry.text.document;

import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.text.Paragraph;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.font.Font;

/// A text shaped one hard line at a time, and re-shaped one hard line at a time
/// when it changes.
///
/// ```java
/// TextDocument document = null;
/// // every frame, with the text as it is now:
/// document = TextDocument.of(font, text, document, line -> Paragraph.of(font, line));
/// DocumentLines rows = document.lines(wrapWidth);
/// ```
///
/// A [Paragraph] shapes its whole string at once and keeps two `int`s per
/// character over it. That is right for a label and wrong for a document: a
/// 500 kB note is half a million characters of shaping, and every keystroke
/// produces a different string, so a whole-text paragraph pays all of it again
/// on every key. Wrapping is already per hard line — [Paragraph#layout] splits on
/// `\n` first and breaks each piece on its own — so nothing is lost by shaping
/// the lines separately, and a keystroke then touches one of them.
///
/// [#of] is handed the document the last frame built. It compares the two strings
/// from both ends, a scan with no allocation, widens the changed span to whole
/// hard lines and rebuilds only those. Every other line keeps the [Paragraph] it
/// had, and its wrap memo with it, so re-laying out a document whose fifth line
/// changed re-breaks the fifth line and reads a memo for the rest. The comparison
/// is two passes over the characters against shaping all of them, which is the
/// ratio this class is for.
///
/// It is not a rope and not an editing structure: the text is one `String` and
/// an edit still allocates a new one. What it removes is the shaping and the
/// wrapping of the parts that did not change. Offsets are `char` indices into the
/// whole text; a hard line's own [Paragraph] has offsets local to that line, and
/// [#startOf] is the difference between the two.
///
/// Confined to one thread, like the fonts and the paragraph cache behind it.
///
/// Read more:
/// [Keeping frames cheap](https://goldberry.dev/docs/performance/frames.html#edit-a-large-note-in-a-text-area).
public final class TextDocument {

    /// Where a hard line's glyphs come from.
    ///
    /// A function rather than a `ParagraphCache`, so that this class does not
    /// decide who caches: a widget hands it the renderer's cache and gets the
    /// sharing the rest of the frame gets, and a test hands it `Paragraph::of`
    /// and gets none.
    @FunctionalInterface
    public interface Shaper {

        /// The shaping of one hard line — no newline in it, possibly empty.
        Paragraph shape(String line);
    }

    private final Font font;
    private final String text;

    /// The hard lines, newline excluded. `hard[k]` is `text[starts[k]..endOf(k))`.
    private final String[] hard;

    /// One shaped paragraph per hard line, in the same order.
    private final Paragraph[] shaped;

    /// Where each hard line's first character is, in [#text].
    private final int[] starts;

    /// The width [#lines] was broken at, or `NaN` before anything asked.
    private double wrapWidth = Double.NaN;

    /// The breaking [#lines] used, with the width it is the memo's key.
    private TextFlow wrapFlow = TextFlow.NORMAL;

    private @Nullable DocumentLines lines;

    private TextDocument(Font font, String text, String[] hard, Paragraph[] shaped, int[] starts) {
        this.font = font;
        this.text = text;
        this.hard = hard;
        this.shaped = shaped;
        this.starts = starts;
    }

    /// `text` shaped with `font`, re-using whatever `previous` already shaped.
    ///
    /// Pass the document the last frame produced as `previous` and this is
    /// incremental; pass null and it shapes everything. Either way the result
    /// describes `text` exactly — `previous` is an optimisation and never an
    /// input to the answer.
    ///
    /// @param font     the face and size to shape with
    /// @param text     the whole document
    /// @param previous last frame's document, or null
    /// @param shaper   what turns one hard line into glyphs
    public static TextDocument of(Font font, String text, @Nullable TextDocument previous, Shaper shaper) {
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(shaper, "shaper");
        if (previous != null && previous.font == font) {
            if (previous.text.equals(text)) {
                // The same document, and usually the same instance — a frame in
                // which nothing was typed. Everything, wrap memo included, is
                // still valid.
                return previous;
            }
            return previous.after(text, shaper);
        }
        return whole(font, text, shaper);
    }

    /// Every hard line shaped from scratch — a document opened, or a theme
    /// change that produced a different [Font].
    private static TextDocument whole(Font font, String text, Shaper shaper) {
        var starts = lineStarts(text);
        var hard = new String[starts.length];
        var shaped = new Paragraph[starts.length];
        for (var k = 0; k < starts.length; k++) {
            hard[k] = text.substring(starts[k], endOf(text, starts, k));
            shaped[k] = shaper.shape(hard[k]);
        }
        return new TextDocument(font, text, hard, shaped, starts);
    }

    /// This document, after the text became `next`.
    private TextDocument after(String next, Shaper shaper) {
        var old = text;
        var shortest = Math.min(old.length(), next.length());
        var prefix = 0;
        while (prefix < shortest && old.charAt(prefix) == next.charAt(prefix)) {
            prefix++;
        }
        var suffix = 0;
        while (suffix < shortest - prefix
                && old.charAt(old.length() - 1 - suffix) == next.charAt(next.length() - 1 - suffix)) {
            suffix++;
        }

        // Widened to whole hard lines: a change inside a line re-shapes that
        // line, and the bracket above can sit anywhere in it.
        var first = hardLineAt(prefix);
        var last = hardLineAt(old.length() - suffix);
        var delta = next.length() - old.length();
        var regionStart = starts[first];
        var regionEnd = endOf(old, starts, last) + delta;

        var replaced = lineStarts(next, regionStart, regionEnd);
        var count = hard.length - (last - first + 1) + replaced.length;
        var nextHard = new String[count];
        var nextShaped = new Paragraph[count];
        var nextStarts = new int[count];

        // Everything before the change keeps its paragraph, its string and its
        // offset — nothing about it moved.
        System.arraycopy(hard, 0, nextHard, 0, first);
        System.arraycopy(shaped, 0, nextShaped, 0, first);
        System.arraycopy(starts, 0, nextStarts, 0, first);

        for (var i = 0; i < replaced.length; i++) {
            var start = replaced[i];
            var end = i + 1 < replaced.length ? replaced[i + 1] - 1 : regionEnd;
            nextStarts[first + i] = start;
            nextHard[first + i] = next.substring(start, end);
            nextShaped[first + i] = shaper.shape(nextHard[first + i]);
        }

        // Everything after it keeps its paragraph and moves by the edit's length.
        var tail = hard.length - (last + 1);
        var into = first + replaced.length;
        System.arraycopy(hard, last + 1, nextHard, into, tail);
        System.arraycopy(shaped, last + 1, nextShaped, into, tail);
        for (var i = 0; i < tail; i++) {
            nextStarts[into + i] = starts[last + 1 + i] + delta;
        }
        return new TextDocument(font, next, nextHard, nextShaped, nextStarts);
    }

    /// Where every hard line of `text` starts.
    private static int[] lineStarts(String text) {
        return lineStarts(text, 0, text.length());
    }

    /// Where every hard line of `text[from..to)` starts, `from` included.
    ///
    /// A range that ends on a newline would be a line that has not ended, which
    /// is why callers pass the position **before** the newline: the caller knows
    /// where the region's last line stops and this only finds the breaks inside
    /// it.
    private static int[] lineStarts(String text, int from, int to) {
        var count = 1;
        for (var i = from; i < to; i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        var starts = new int[count];
        starts[0] = from;
        var at = 1;
        for (var i = from; i < to; i++) {
            if (text.charAt(i) == '\n') {
                starts[at++] = i + 1;
            }
        }
        return starts;
    }

    /// One past hard line `k`'s last character, the newline excluded.
    private static int endOf(String text, int[] starts, int k) {
        return k + 1 < starts.length ? starts[k + 1] - 1 : text.length();
    }

    /// The whole text, as one string.
    public String text() {
        return text;
    }

    /// The font every hard line was shaped with.
    public Font font() {
        return font;
    }

    /// How many lines somebody typed — the newlines, plus one.
    public int hardLineCount() {
        return hard.length;
    }

    /// Hard line `k`'s shaping. Its offsets are **local** to that line.
    public Paragraph paragraphOf(int k) {
        return shaped[k];
    }

    /// Where hard line `k` starts, in the whole text.
    public int startOf(int k) {
        return starts[k];
    }

    /// One past hard line `k`'s last character, the newline excluded.
    public int endOf(int k) {
        return endOf(text, starts, k);
    }

    /// Which hard line `offset` is in — the last one that starts at or before it.
    ///
    /// An offset sitting **on** a newline belongs to the line that newline ends,
    /// which is what the search gives: the next line starts one past it.
    public int hardLineAt(int offset) {
        var at = Arrays.binarySearch(starts, Math.clamp(offset, 0, text.length()));
        return at >= 0 ? at : Math.max(0, -at - 2);
    }

    /// This document broken into visual lines at `width`.
    ///
    /// Memoised at one width, like [Paragraph#layout]'s own memo and against the
    /// same caller: a control asks at the width it was measured at, every frame,
    /// and that answer does not change until the control does.
    ///
    /// @param width the width to wrap at, or [Paragraph#UNCONSTRAINED]
    public DocumentLines lines(double width) {
        return lines(width, TextFlow.NORMAL);
    }

    /// This document broken into visual lines at `width`, cutting a word wider
    /// than the line where `flow` allows it -- `overflow-wrap` and `word-break`.
    ///
    /// The control that draws the text with `flow` has to ask with it too: the
    /// rows here are what its caret, selection and scroll are measured against,
    /// and a word the paint cuts and these rows do not would draw one more line
    /// than the control believes it has.
    ///
    /// @param width the width to wrap at, or [Paragraph#UNCONSTRAINED]
    /// @param flow  what the cascade said about breaking inside a word; only
    ///              that half of it is read
    public DocumentLines lines(double width, TextFlow flow) {
        Objects.requireNonNull(flow, "flow");
        var held = lines;
        if (held != null && width == wrapWidth && breaksAlike(flow, wrapFlow)) {
            return held;
        }
        var visual = new int[hard.length + 1];
        for (var k = 0; k < hard.length; k++) {
            var layout = shaped[k].layout(width, flow);
            // A hard line always occupies at least one visual line, blank or not.
            visual[k + 1] = visual[k] + Math.max(1, layout.lineCount());
        }
        var built = new DocumentLines(this, width, flow, visual);
        lines = built;
        wrapWidth = width;
        wrapFlow = flow;
        return built;
    }

    /// Whether two flows break lines in the same places. Alignment and
    /// decoration do not move a break, so a change to either keeps the memo.
    private static boolean breaksAlike(TextFlow a, TextFlow b) {
        return a.overflowWrap() == b.overflowWrap() && a.wordBreak() == b.wordBreak();
    }

    /// The width of `[from, to)`, both offsets into the whole text.
    ///
    /// Both are clamped into the hard line `from` is on: a range is measured
    /// along a line, and a caller asking across a newline is asking for a
    /// distance that does not exist.
    public double widthBetween(int from, int to) {
        var k = hardLineAt(from);
        var start = starts[k];
        var end = endOf(k);
        var a = Math.clamp(from, start, end) - start;
        var b = Math.clamp(to, start, end) - start;
        return shaped[k].widthBetween(Math.min(a, b), Math.max(a, b));
    }

    /// The offset in `[lineStart, lineEnd]` whose caret sits nearest `x`.
    ///
    /// [Paragraph#offsetAt]'s answer, in the whole text's offsets. The line is
    /// one visual line, so it lies inside one hard line and one paragraph
    /// answers it — which is also what keeps a click cheap: the grapheme walk
    /// behind it reads one line rather than one document.
    public int offsetAt(int lineStart, int lineEnd, double x) {
        var k = hardLineAt(lineStart);
        var start = starts[k];
        var end = endOf(k);
        var a = Math.clamp(lineStart, start, end) - start;
        var b = Math.clamp(lineEnd, start, end) - start;
        return start + shaped[k].offsetAt(Math.min(a, b), Math.max(a, b), x);
    }
}

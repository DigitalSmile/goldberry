package dev.goldberry.text.font;

import java.util.Arrays;
import java.util.Objects;

/// The characters one face has glyphs for, as a table cheap enough to ask about
/// every character of a paragraph.
///
/// ```java
/// Coverage inter = fonts.faceOf(BundledFont.UI).coverage();
/// inter.covers('A');                     // true
/// inter.covers(0x4E2D);                  // false: 中 is not in Inter
/// inter.coversAll("Hello 中文", 0, 5);    // true
/// ```
///
/// Read once per typeface, from its `cmap`, when the face is opened, and kept
/// with it: every size over the face shares it. A paragraph asks it whether the
/// face it was given can draw its text before deciding to look anywhere else,
/// and that question is a scan with no allocation in it, so text the face
/// covers costs one pass over the characters and nothing more.
///
/// The table is the face's ranges merged and sorted, searched by bisection,
/// with the first 256 code points also held as a bitmap: Latin text is answered
/// by a shift and a mask, and anything else by about ten comparisons for a Latin
/// face and about fifteen for a CJK one.
///
/// Not every character needs a glyph. Controls, format characters such as the
/// joiners and the bidi marks, line and paragraph separators and the variation
/// selectors are answered by the shaper with no glyph of their own, so a face
/// that lacks them still covers text that contains them.
///
/// Immutable, and safe to share between threads.
///
/// Read more:
/// [Faces, fonts and the book](https://goldberry.dev/docs/guide/text.html#faces-fonts-and-the-book).
public final class Coverage {

    /// A face this could not read, or one with no characters in it.
    static final Coverage EMPTY = new Coverage(new int[0], new int[0], new long[4]);

    /// Where each range starts, ascending, with no two ranges touching.
    private final int[] starts;

    /// Where each range ends, inclusive, index for index with [#starts].
    private final int[] ends;

    /// U+0000 to U+00FF, one bit each.
    private final long[] latin1;

    private Coverage(int[] starts, int[] ends, long[] latin1) {
        this.starts = starts;
        this.ends = ends;
        this.latin1 = latin1;
    }

    /// What a font file has glyphs for: its `cmap` read.
    ///
    /// Empty rather than an exception for a file this cannot read, for the
    /// reason [FaceCoverage#codePoints] gives.
    ///
    /// @param font the face's bytes
    public static Coverage of(byte[] font) {
        Objects.requireNonNull(font, "font");
        return FaceCoverage.coverage(font);
    }

    /// A coverage of the inclusive ranges `ranges`, given as start and end pairs
    /// in any order, overlapping or not.
    static Coverage ofRanges(int[] ranges) {
        var count = ranges.length / 2;
        if (count == 0) {
            return EMPTY;
        }
        var pairs = new long[count];
        for (var i = 0; i < count; i++) {
            pairs[i] = ((long) ranges[2 * i] << 32) | (ranges[2 * i + 1] & 0xFFFF_FFFFL);
        }
        // Sorted by start, which is the high half; code points are never
        // negative, so the signed order is the right one.
        Arrays.sort(pairs);

        var starts = new int[count];
        var ends = new int[count];
        var merged = 0;
        for (var pair : pairs) {
            var start = (int) (pair >>> 32);
            var end = (int) pair;
            if (merged > 0 && start <= ends[merged - 1] + 1) {
                ends[merged - 1] = Math.max(ends[merged - 1], end);
            } else {
                starts[merged] = start;
                ends[merged] = end;
                merged++;
            }
        }

        var latin1 = new long[4];
        for (var i = 0; i < merged && starts[i] <= 0xFF; i++) {
            for (var code = starts[i]; code <= Math.min(ends[i], 0xFF); code++) {
                latin1[code >>> 6] |= 1L << code;
            }
        }
        return new Coverage(Arrays.copyOf(starts, merged), Arrays.copyOf(ends, merged), latin1);
    }

    /// Whether the face has a glyph for `codePoint`.
    public boolean covers(int codePoint) {
        if (codePoint >= 0 && codePoint <= 0xFF) {
            return (latin1[codePoint >>> 6] & (1L << codePoint)) != 0;
        }
        var low = 0;
        var high = starts.length - 1;
        while (low <= high) {
            var middle = (low + high) >>> 1;
            if (codePoint < starts[middle]) {
                high = middle - 1;
            } else if (codePoint > ends[middle]) {
                low = middle + 1;
            } else {
                return true;
            }
        }
        return false;
    }

    /// Whether the face has a glyph for every character in `[start, end)` of
    /// `text` that needs one.
    ///
    /// A scan and nothing else: no allocation, and it stops at the first
    /// character the face lacks.
    ///
    /// @throws IndexOutOfBoundsException if the range is not within the text
    public boolean coversAll(CharSequence text, int start, int end) {
        Objects.checkFromToIndex(start, end, text.length());
        var at = start;
        while (at < end) {
            var codePoint = Character.codePointAt(text, at);
            if (!covers(codePoint) && needsGlyph(codePoint)) {
                return false;
            }
            at += Character.charCount(codePoint);
        }
        return true;
    }

    /// Whether a shaper draws `codePoint` with a glyph of the face's own, rather
    /// than with none at all.
    ///
    /// False for controls, format characters (the joiners, the bidi marks, the
    /// soft hyphen), the line and paragraph separators and the variation
    /// selectors. A face that lacks them has lost nothing, and a paragraph that
    /// went looking for another face because of a newline would split itself
    /// on every line.
    public static boolean needsGlyph(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> false;
            default -> !isVariationSelector(codePoint);
        };
    }

    private static boolean isVariationSelector(int codePoint) {
        return (codePoint >= 0xFE00 && codePoint <= 0xFE0F) || (codePoint >= 0xE0100 && codePoint <= 0xE01EF);
    }

    /// Whether the face covers nothing: a `cmap` this could not read, or bytes
    /// that are not a font.
    public boolean isEmpty() {
        return starts.length == 0;
    }

    /// How many characters the face covers.
    public int size() {
        var total = 0;
        for (var i = 0; i < starts.length; i++) {
            total += ends[i] - starts[i] + 1;
        }
        return total;
    }

    /// How many ranges the table holds after merging: what a lookup bisects.
    int ranges() {
        return starts.length;
    }

    /// Every covered code point, ascending.
    int[] codePoints() {
        var points = new int[size()];
        var at = 0;
        for (var i = 0; i < starts.length; i++) {
            for (var code = starts[i]; code <= ends[i]; code++) {
                points[at++] = code;
            }
        }
        return points;
    }

    @Override
    public String toString() {
        return "Coverage[" + size() + " characters in " + starts.length + " ranges]";
    }
}

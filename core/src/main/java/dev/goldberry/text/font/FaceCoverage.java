package dev.goldberry.text.font;

import java.nio.ByteBuffer;
import java.util.Arrays;

import dev.goldberry.text.font.sfnt.TableDirectory;

/// Which characters a font file has glyphs for: its `cmap`, read.
///
/// ```java
/// int[] emoji = FaceCoverage.codePoints(BundledAssets.font(BundledFont.EMOJI));
/// ```
///
/// One question, asked by anything that offers characters rather than drawing
/// the ones it was handed: an emoji picker listing what it can show, a
/// diagnostic asking whether a face covers a script, a test asserting that the
/// shipped emoji face still has the characters a screen names. Shaping answers
/// "what does this text look like"; this answers "what is in here at all", and
/// the answer is the font's own contents rather than a list kept beside it.
/// Every open [FontFace] holds the same answer as a [Coverage], which is how a
/// paragraph knows to look for a fallback face.
///
/// The reader is Java rather than a HarfBuzz binding because the table is
/// simpler than the binding would be: two subtable formats cover every font
/// this century, both are arrays of ranges, and the whole reader fits on a page
/// with no native memory in it. Format 4 is the Basic Multilingual Plane as
/// segments of 16-bit ranges, which is what every Latin face uses; format 12 is
/// the whole of Unicode as 32-bit groups, which a face with emoji needs because
/// the planes above `0xFFFF` do not fit format 4 at all. A subtable in any other
/// format is skipped.
///
/// Read more: [Emoji](https://goldberry.dev/docs/guide/text.html#emoji).
public final class FaceCoverage {

    /// The `cmap` table's tag, as the four bytes a font writes it.
    private static final int CMAP = TableDirectory.tag('c', 'm', 'a', 'p');

    private FaceCoverage() {}

    /// Every code point `font` has a glyph for, in order.
    ///
    /// Empty rather than an exception for a file this cannot read: a font with no
    /// `cmap`, a collection, a table in a format not handled, or bytes that are
    /// not a font at all. The caller is asking what is in a face, and "nothing I
    /// can tell you" is an answer it can act on; a picker shows no characters
    /// rather than failing to open.
    ///
    /// The bytes are the face's, as `BundledAssets.font` hands them over.
    /// [Coverage#of] is the same answer as ranges, which is the form to keep.
    ///
    /// @param font the face's bytes
    /// @return the code points, ascending
    public static int[] codePoints(byte[] font) {
        return coverage(font).codePoints();
    }

    /// The same answer as a [Coverage]: what [Coverage#of] and every open face
    /// hold.
    static Coverage coverage(byte[] font) {
        var cmap = TableDirectory.table(font, CMAP);
        if (cmap == null) {
            return Coverage.EMPTY;
        }
        try {
            return Coverage.ofRanges(read(cmap));
        } catch (RuntimeException e) {
            // A truncated or malformed subtable, which is an ordinary thing to be
            // handed. Every read below is bounds-checked by the slice, so the
            // failure arrives here rather than as a wrong answer.
            return Coverage.EMPTY;
        }
    }

    /// Every covered range in the `cmap` slice [TableDirectory] handed back, as
    /// start and end pairs, in no particular order and possibly overlapping.
    ///
    /// A slice and not the whole file, which is what finding the table through
    /// [TableDirectory#table] buys: the offsets a subtable record holds are from the
    /// table's own start, exactly as the specification writes them, and the slice
    /// ends where the table ends, so a record claiming a subtable past the last
    /// byte of the `cmap` is refused here rather than read out of whatever follows
    /// it.
    private static int[] read(ByteBuffer in) {
        // Every subtable rather than the first: a face with emoji has both a
        // format 4 for the BMP and a format 12 for everything, and reading only
        // the first would lose every character above 0xFFFF. The two overlap,
        // and the ranges are merged where they are kept.
        var found = new Ranges();
        var subtables = Short.toUnsignedInt(in.getShort(2));
        for (var i = 0; i < subtables; i++) {
            var record = 4 + i * 8;
            var offset = in.getInt(record + 4);
            if (offset < 0 || offset + 4 > in.limit()) {
                continue;
            }
            switch (Short.toUnsignedInt(in.getShort(offset))) {
                case 4 -> format4(in, offset, found);
                case 12 -> format12(in, offset, found);
                default -> {
                    // Formats 0, 2, 6 and 13, none of which a face anybody asks
                    // this about encodes its characters in.
                }
            }
        }
        return found.toArray();
    }

    /// Format 4: segments of 16-bit ranges, each with a delta or an index into
    /// the glyph array.
    private static void format4(ByteBuffer in, int offset, Ranges found) {
        var segments = Short.toUnsignedInt(in.getShort(offset + 6)) / 2;
        var ends = offset + 14;
        var starts = ends + segments * 2 + 2;
        var deltas = starts + segments * 2;
        var ranges = deltas + segments * 2;

        for (var segment = 0; segment < segments; segment++) {
            var end = Short.toUnsignedInt(in.getShort(ends + segment * 2));
            var start = Short.toUnsignedInt(in.getShort(starts + segment * 2));
            if (start > end) {
                continue;
            }
            var delta = in.getShort(deltas + segment * 2);
            var rangeOffset = Short.toUnsignedInt(in.getShort(ranges + segment * 2));
            // 0xFFFF is the segment terminator every format 4 table ends with,
            // and it is not a character.
            var last = Math.min(end, 0xFFFE);
            if (last < start) {
                continue;
            }
            if (rangeOffset == 0) {
                // A delta maps the whole segment, and at most one code point in
                // it lands on glyph 0: the range, with that one left out.
                var none = -delta & 0xFFFF;
                if (none < start || none > last) {
                    found.add(start, last);
                } else {
                    if (none > start) {
                        found.add(start, none - 1);
                    }
                    if (none < last) {
                        found.add(none + 1, last);
                    }
                }
                continue;
            }
            for (var code = start; code <= last; code++) {
                var at = ranges + segment * 2 + rangeOffset + (code - start) * 2;
                if (at + 2 > in.limit()) {
                    continue;
                }
                var glyph = Short.toUnsignedInt(in.getShort(at));
                if (glyph != 0 && ((glyph + delta) & 0xFFFF) != 0) {
                    found.add(code, code);
                }
            }
        }
    }

    /// Format 12: groups of 32-bit ranges, which is how anything above the BMP
    /// is encoded.
    private static void format12(ByteBuffer in, int offset, Ranges found) {
        var groups = in.getInt(offset + 12);
        for (var group = 0; group < groups; group++) {
            var at = offset + 16 + group * 12;
            if (at + 12 > in.limit()) {
                return;
            }
            var start = in.getInt(at);
            var end = in.getInt(at + 4);
            var glyph = in.getInt(at + 8);
            if (start < 0 || end < start || glyph == 0 || start > Character.MAX_CODE_POINT) {
                continue;
            }
            // A group may be enormous in a broken file; Unicode's last code point
            // is what bounds it.
            found.add(start, Math.min(end, Character.MAX_CODE_POINT));
        }
    }

    /// Start and end pairs, growing, with a code point that continues the last
    /// range extending it rather than starting another: format 4 is read a code
    /// point at a time, and a face of forty thousand characters is a few hundred
    /// ranges.
    private static final class Ranges {

        private int[] pairs = new int[64];
        private int size;

        void add(int start, int end) {
            if (size > 0 && start == pairs[size - 1] + 1) {
                pairs[size - 1] = end;
                return;
            }
            if (size == pairs.length) {
                pairs = Arrays.copyOf(pairs, size * 2);
            }
            pairs[size++] = start;
            pairs[size++] = end;
        }

        int[] toArray() {
            return Arrays.copyOf(pairs, size);
        }
    }
}

package io.github.digitalsmile.goldberry.text.font;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/// Which characters a font file has glyphs for — its `cmap`, read.
///
/// ## What it is for
///
/// One question, asked by anything that offers characters rather than drawing
/// the ones it was handed: an emoji picker listing what it can show, a
/// diagnostic asking whether a face covers a script, a test asserting that the
/// shipped emoji face still has the characters a screen names. Shaping answers
/// "what does this text look like"; this answers "what is in here at all"
/// ([ADR-0386]).
///
/// ## Why it is Java, and thirty lines of it
///
/// HarfBuzz has `hb_face_collect_unicodes`, and binding it would mean a set
/// object, an iterator and their lifetimes crossing FFM for a question asked
/// once per face. The table itself is simpler than that binding: two subtable
/// formats cover every font this century, both are arrays of ranges, and the
/// whole reader fits on a page with no native memory in it — which is
/// [io.github.digitalsmile.goldberry.image.gif.GifDecoder]'s argument for owning
/// a small format rather than linking one.
///
/// ## The two formats
///
/// **Format 4** is the BMP: segments of 16-bit ranges, which is what every Latin
/// face uses. **Format 12** is the whole of Unicode as 32-bit groups, which is
/// what a face with emoji in it needs — the planes above `0xFFFF` do not fit
/// format 4 at all. A subtable in any other format is skipped: formats 0, 2, 6
/// and 13 exist and none of them is how a modern face encodes the characters
/// anybody asks this about.
public final class FaceCoverage {

    /// The `cmap` table's tag, as the four bytes a font writes it.
    private static final int CMAP = tag('c', 'm', 'a', 'p');

    /// `ttcf` — a collection, which this does not read.
    private static final int COLLECTION = tag('t', 't', 'c', 'f');

    private FaceCoverage() {}

    /// Every code point `font` has a glyph for, in order.
    ///
    /// **Empty rather than an exception** for a file this cannot read: a font
    /// with no `cmap`, a collection, a table in a format not handled, or bytes
    /// that are not a font at all. The caller is asking what is in a face, and
    /// "nothing I can tell you" is an answer it can act on — a picker shows no
    /// characters rather than failing to open.
    ///
    /// The bytes are the face's, as
    /// [io.github.digitalsmile.goldberry.assets.BundledAssets#font]
    /// hands them over.
    ///
    /// @param font the face's bytes
    /// @return the code points, ascending
    public static int[] codePoints(byte[] font) {
        Objects.requireNonNull(font, "font");
        try {
            return read(ByteBuffer.wrap(font).order(ByteOrder.BIG_ENDIAN));
        } catch (RuntimeException e) {
            // A truncated or malformed file, which is an ordinary thing to be
            // handed. Every read below is bounds-checked by the buffer, so the
            // failure arrives here rather than as a wrong answer.
            return new int[0];
        }
    }

    private static int[] read(ByteBuffer in) {
        var version = in.getInt(0);
        if (version == COLLECTION) {
            // A `.ttc` holds several faces and this API names none of them.
            return new int[0];
        }
        var tables = Short.toUnsignedInt(in.getShort(4));
        var cmap = -1;
        for (var i = 0; i < tables; i++) {
            var record = 12 + i * 16;
            if (in.getInt(record) == CMAP) {
                cmap = in.getInt(record + 8);
                break;
            }
        }
        if (cmap <= 0 || cmap >= in.limit()) {
            return new int[0];
        }

        // The best subtable rather than the first: a face with emoji has both a
        // format 4 for the BMP and a format 12 for everything, and reading only
        // the first would lose every character above 0xFFFF.
        SortedSet<Integer> found = new TreeSet<>();
        var subtables = Short.toUnsignedInt(in.getShort(cmap + 2));
        for (var i = 0; i < subtables; i++) {
            var record = cmap + 4 + i * 8;
            var offset = cmap + in.getInt(record + 4);
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

        var points = new int[found.size()];
        var at = 0;
        for (var point : found) {
            points[at++] = point;
        }
        return points;
    }

    /// Format 4: segments of 16-bit ranges, each with a delta or an index into
    /// the glyph array.
    private static void format4(ByteBuffer in, int offset, SortedSet<Integer> found) {
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
            for (var code = start; code <= end && code <= 0xFFFF; code++) {
                // 0xFFFF is the segment terminator every format 4 table ends
                // with, and it is not a character.
                if (code == 0xFFFF) {
                    continue;
                }
                int glyph;
                if (rangeOffset == 0) {
                    glyph = (code + delta) & 0xFFFF;
                } else {
                    var at = ranges + segment * 2 + rangeOffset + (code - start) * 2;
                    if (at + 2 > in.limit()) {
                        continue;
                    }
                    glyph = Short.toUnsignedInt(in.getShort(at));
                    if (glyph != 0) {
                        glyph = (glyph + delta) & 0xFFFF;
                    }
                }
                if (glyph != 0) {
                    found.add(code);
                }
            }
        }
    }

    /// Format 12: groups of 32-bit ranges, which is how anything above the BMP
    /// is encoded.
    private static void format12(ByteBuffer in, int offset, SortedSet<Integer> found) {
        var groups = in.getInt(offset + 12);
        for (var group = 0; group < groups; group++) {
            var at = offset + 16 + group * 12;
            if (at + 12 > in.limit()) {
                return;
            }
            var start = in.getInt(at);
            var end = in.getInt(at + 4);
            var glyph = in.getInt(at + 8);
            if (start < 0 || end < start || glyph == 0) {
                continue;
            }
            // A group may be enormous in a broken file; Unicode's last code point
            // is what bounds it.
            for (var code = start; code <= Math.min(end, Character.MAX_CODE_POINT); code++) {
                found.add(code);
            }
        }
    }

    private static int tag(char a, char b, char c, char d) {
        return (a << 24) | (b << 16) | (c << 8) | d;
    }
}

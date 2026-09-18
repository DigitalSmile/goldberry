package io.github.digitalsmile.goldberry.text.font.sfnt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// The first twelve bytes of a font file, and the index that follows them.
///
/// Every reader in this package starts here: a font is a header saying how many
/// tables there are, then one sixteen-byte record per table giving its four-byte
/// tag, its offset and its length. Finding a table is a linear scan of at most a
/// few dozen records, done once per face.
///
/// ## Why a slice and not an offset
///
/// [#table] hands back a **slice** rather than a position, so every offset a
/// reader then follows is the offset the specification writes — the tables all
/// number from their own start. A reader given the whole file has to add a base
/// to each one and gets no bounds check at the table's end, which is exactly the
/// mistake a malformed font is waiting for.
public final class TableDirectory {

    /// `ttcf` — a font collection, which none of these readers opens.
    ///
    /// A collection holds several faces behind one header and this API names
    /// none of them, so it is refused rather than guessed at.
    private static final int COLLECTION = tag('t', 't', 'c', 'f');

    private TableDirectory() {}

    /// A four-byte table tag, as a font writes it.
    public static int tag(char a, char b, char c, char d) {
        return (a << 24) | (b << 16) | (c << 8) | d;
    }

    /// The bytes of one table, or null when the font has no such table.
    ///
    /// **Null rather than an exception** for anything unreadable: a collection, a
    /// truncated directory, a record pointing past the end of the file, or bytes
    /// that are not a font at all. Every caller here is asking *whether* a face
    /// has something, and a face that does not is ordinary.
    ///
    /// @param font the face's bytes, as
    ///        [io.github.digitalsmile.goldberry.assets.BundledAssets#font] hands
    ///        them over
    /// @param tag  the table's tag, from [#tag]
    public static @Nullable ByteBuffer table(byte[] font, int tag) {
        Objects.requireNonNull(font, "font");
        try {
            return locate(ByteBuffer.wrap(font).order(ByteOrder.BIG_ENDIAN), tag);
        } catch (RuntimeException e) {
            // Every read below is bounds-checked by the buffer, so a truncated or
            // malformed file arrives here rather than as a wrong answer.
            return null;
        }
    }

    private static @Nullable ByteBuffer locate(ByteBuffer in, int tag) {
        if (in.getInt(0) == COLLECTION) {
            return null;
        }
        var tables = Short.toUnsignedInt(in.getShort(4));
        for (var i = 0; i < tables; i++) {
            var record = 12 + i * 16;
            if (in.getInt(record) != tag) {
                continue;
            }
            var offset = in.getInt(record + 8);
            var length = in.getInt(record + 12);
            if (offset < 0 || length < 0 || offset + length > in.limit()) {
                return null;
            }
            return in.slice(offset, length).order(ByteOrder.BIG_ENDIAN);
        }
        return null;
    }
}

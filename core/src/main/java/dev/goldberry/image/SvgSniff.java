package dev.goldberry.image;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/// Whether some bytes are an SVG document: an `<svg` root after the prologue an
/// editor writes.
///
/// Text has no magic number, so the sniff reads what a document may start
/// with: a UTF-8 byte order mark, white space, an XML declaration and other
/// processing instructions, comments and a doctype (its internal subset
/// included), and then the root element, which must be `svg`. An XML document
/// whose root is something else is not SVG, and an HTML page with an `<svg>`
/// in its body is not one either: its root is `html`.
///
/// It reads at most [#LIMIT] bytes. A prologue longer than that, which would be
/// a licence the size of a book, is not looked past.
final class SvgSniff {

    /// The most bytes read: 64 KiB.
    static final int LIMIT = 64 * 1024;

    private SvgSniff() {}

    /// Whether `bytes`, from their position, are an SVG document. The position
    /// is not moved.
    static boolean isSvg(ByteBuffer bytes) {
        var start = bytes.position();
        var end = start + Math.min(bytes.remaining(), LIMIT);
        var at = startsWith(bytes, start, end, "\u00EF\u00BB\u00BF") ? start + 3 : start;
        while (true) {
            at = skipSpace(bytes, at, end);
            if (startsWith(bytes, at, end, "<?")) {
                at = past(bytes, at + 2, end, "?>");
            } else if (startsWith(bytes, at, end, "<!--")) {
                at = past(bytes, at + 4, end, "-->");
            } else if (startsWith(bytes, at, end, "<!DOCTYPE")) {
                at = pastDoctype(bytes, at + 9, end);
            } else {
                return startsWith(bytes, at, end, "<svg") && at + 4 < end && endsName(bytes.get(at + 4));
            }
            if (at < 0) {
                return false;
            }
        }
    }

    private static int skipSpace(ByteBuffer bytes, int at, int end) {
        while (at < end && isSpace(bytes.get(at))) {
            at++;
        }
        return at;
    }

    /// Past the first `terminator` from `at`, or -1 when there is none in reach.
    private static int past(ByteBuffer bytes, int at, int end, String terminator) {
        for (var i = at; i < end; i++) {
            if (startsWith(bytes, i, end, terminator)) {
                return i + terminator.length();
            }
        }
        return -1;
    }

    private static int pastDoctype(ByteBuffer bytes, int at, int end) {
        var depth = 0;
        byte quote = 0;
        for (var i = at; i < end; i++) {
            var b = bytes.get(i);
            if (quote != 0) {
                if (b == quote) {
                    quote = 0;
                }
            } else if (b == '"' || b == '\'') {
                quote = b;
            } else if (b == '[') {
                depth++;
            } else if (b == ']') {
                depth--;
            } else if (b == '>' && depth <= 0) {
                return i + 1;
            }
        }
        return -1;
    }

    private static boolean isSpace(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }

    private static boolean endsName(byte b) {
        return isSpace(b) || b == '>' || b == '/';
    }

    /// Whether the bytes at `at` are `text`, each char one byte (Latin-1).
    private static boolean startsWith(ByteBuffer bytes, int at, int end, String text) {
        var expected = text.getBytes(StandardCharsets.ISO_8859_1);
        if (at < 0 || at + expected.length > end) {
            return false;
        }
        for (var i = 0; i < expected.length; i++) {
            if (bytes.get(at + i) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}

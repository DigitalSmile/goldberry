package io.github.digitalsmile.goldberry.assets.svg;

/// Enough of SVG's path-data grammar to make one fragment safe to put after
/// another.
///
/// [io.github.digitalsmile.goldberry.assets.IconCompiler] turns every shape in an
/// icon into a run of path data and joins them. That is only sound when each run
/// starts from a point of its own, and SVG has a rule that makes it look sound
/// when it is not:
///
/// > If a relative `moveto` (`m`) appears as the first element of the path, then
/// > it is treated as a pair of absolute coordinates.
/// >
/// > — SVG 1.1 §8.3.2, repeated verbatim in SVG 2 §9.3.3
///
/// **"Of the path"**, not "of the file". Inside its own `<path d="m2 16 …">` the
/// `m` is measured from the origin; concatenated behind another subpath it is
/// measured from wherever that one's pen stopped. Lucide writes its icons this
/// way — 481 of the 1544 bundled ones have a relative `moveto` opening a subpath
/// that is not the first — so a third of the icon set drew its second half
/// somewhere off the 24×24 viewBox (ADR-0302).
///
/// This is a rewrite of one command and not a parser. Everything after the
/// opening `moveto` is passed through byte for byte, because the numbers are the
/// upstream's and rounding them here would be a precision loss for no gain —
/// the same reason `IconCompiler` copies a `d` attribute rather than
/// re-emitting it.
public final class SvgPathData {

    private SvgPathData() {
    }

    /// `data` with its opening `moveto` made absolute, so it may follow another
    /// subpath unchanged.
    ///
    /// Data that already opens with `M` comes back identical — the common case,
    /// and the one the shape conversions in [SvgShapes] all produce.
    ///
    /// ## Why the trailing pairs move to an explicit `l`
    ///
    /// `m2 16 4.5-9 4.5 9` is a `moveto` followed by **two implicit `lineto`s**,
    /// and SVG says an implicit repeat inherits the case of the command that
    /// opened it: after `m` they are relative, after `M` they are absolute.
    /// Rewriting the letter alone would move the pen correctly and then draw the
    /// rest of the subpath to two absolute points nobody meant — an icon that is
    /// wrong in a *different* way, which is worse than the bug being fixed
    /// because it looks deliberate. So the pairs get an `l` of their own.
    ///
    /// @param data one shape's path data, which may be empty
    /// @return the same geometry, anchored absolutely
    /// @throws IllegalArgumentException if the opening `moveto` has no coordinate
    ///         pair, which is data no renderer can read
    public static String absoluteStart(String data) {
        var at = skipSeparators(data, 0);
        if (at >= data.length() || data.charAt(at) != 'm') {
            return data;
        }

        var x = skipSeparators(data, at + 1);
        var xEnd = scanNumber(data, x, "the x of an opening moveto");
        var y = skipSeparators(data, xEnd);
        var yEnd = scanNumber(data, y, "the y of an opening moveto");

        var rest = skipSeparators(data, yEnd);
        var trailing = rest < data.length() && isNumberStart(data.charAt(rest))
                // The implicit repeats were relative linetos and must stay so.
                ? " l" + data.substring(rest)
                : data.substring(yEnd);

        return "M" + data.substring(x, xEnd) + " " + data.substring(y, yEnd) + trailing;
    }

    /// Whether `data` opens a subpath of its own, absolutely — what
    /// [#absoluteStart] guarantees and what concatenation needs.
    public static boolean startsAbsolutely(String data) {
        var at = skipSeparators(data, 0);
        return at < data.length() && data.charAt(at) == 'M';
    }

    /// The end of the SVG number starting at `from`.
    ///
    /// SVG's number is not Java's: `1-2` is two numbers and `1.5.5` is two
    /// numbers, because a sign and a second decimal point both *end* the one
    /// being read. Splitting on whitespace instead would read either as one
    /// (see `SvgPath` in `:core`, which learned the same lesson).
    private static int scanNumber(String data, int from, String what) {
        var at = from;
        if (at < data.length() && (data.charAt(at) == '+' || data.charAt(at) == '-')) {
            at++;
        }
        var digits = skipDigits(data, at);
        var any = digits > at;
        at = digits;
        if (at < data.length() && data.charAt(at) == '.') {
            var fraction = skipDigits(data, at + 1);
            any |= fraction > at + 1;
            at = fraction;
        }
        if (!any) {
            throw new IllegalArgumentException(
                    what + " is missing at offset " + from + " of \"" + data + "\"");
        }
        // An `e` with no digits after it is not an exponent — it is the next
        // command letter, and swallowing it would lose a whole subpath.
        if (at < data.length() && (data.charAt(at) == 'e' || data.charAt(at) == 'E')) {
            var exponent = at + 1;
            if (exponent < data.length() && (data.charAt(exponent) == '+' || data.charAt(exponent) == '-')) {
                exponent++;
            }
            var exponentDigits = skipDigits(data, exponent);
            if (exponentDigits > exponent) {
                at = exponentDigits;
            }
        }
        return at;
    }

    private static int skipDigits(String data, int from) {
        var at = from;
        while (at < data.length() && data.charAt(at) >= '0' && data.charAt(at) <= '9') {
            at++;
        }
        return at;
    }

    /// A comma is whitespace in path data, and so is any run of the two.
    private static int skipSeparators(String data, int from) {
        var at = from;
        while (at < data.length() && (Character.isWhitespace(data.charAt(at)) || data.charAt(at) == ',')) {
            at++;
        }
        return at;
    }

    private static boolean isNumberStart(char c) {
        return (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.';
    }
}

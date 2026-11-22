package dev.goldberry.vector.document;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.regex.Pattern;

import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.vector.ViewBox;

/// A document's root `<svg>` start tag: where it is, what its attributes say,
/// and the same document with some of them replaced.
///
/// Read by this module rather than asked of ThorVG for two things ThorVG's C
/// API does not say: whether the document has a `viewBox` and how it keeps its
/// aspect, and where the root is, so that a colour or a size can be given to it
/// before ThorVG parses the document.
///
/// Only the prologue and the root tag are read: an optional UTF-8 byte order
/// mark, white space, an XML declaration and processing instructions, comments
/// and a doctype. Anything else before `<svg` is not a document this module
/// draws.
public final class RootTag {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /// A `color` declaration in a `style` attribute.
    private static final Pattern STYLE_COLOR = Pattern.compile("(?:^|;)\\s*color\\s*:", Pattern.CASE_INSENSITIVE);

    /// One attribute of the root tag.
    ///
    /// @param name  as written, a prefix and all
    /// @param value as written, between its quotes; character references are
    ///              not expanded
    /// @param start the offset of the name
    /// @param end   the offset just past the closing quote
    public record Attribute(String name, String value, int start, int end) {
        public Attribute {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }
    }

    private final byte[] document;
    /// The offset just past `<svg`.
    private final int nameEnd;
    /// The offset of the tag's `>` or `/>`.
    private final int end;
    private final List<Attribute> attributes;

    private RootTag(byte[] document, int nameEnd, int end, List<Attribute> attributes) {
        this.document = document;
        this.nameEnd = nameEnd;
        this.end = end;
        this.attributes = List.copyOf(attributes);
    }

    /// Finds the root tag of `document`.
    ///
    /// @throws ImageDecodeException when the bytes do not start, after a
    ///                              prologue, with an `<svg` tag that closes
    public static RootTag find(byte[] document) {
        Objects.requireNonNull(document, "document");
        var at = startsWith(document, 0, BOM) ? BOM.length : 0;
        while (true) {
            at = skipSpace(document, at);
            if (startsWith(document, at, "<?")) {
                at = past(document, at, "?>", "an unclosed processing instruction");
            } else if (startsWith(document, at, "<!--")) {
                at = past(document, at + 4, "-->", "an unclosed comment");
            } else if (startsWith(document, at, "<!DOCTYPE")) {
                at = pastDoctype(document, at);
            } else if (startsWith(document, at, "<svg") && at + 4 < document.length && endsName(document[at + 4])) {
                return readTag(document, at + 4);
            } else {
                throw new ImageDecodeException("not an SVG document: no <svg> root element"
                        + (at < document.length ? " where byte " + at + " starts" : ""));
            }
        }
    }

    /// The root's attributes, in document order.
    public List<Attribute> attributes() {
        return attributes;
    }

    /// The offset just past the root's `<svg`.
    public int nameEnd() {
        return nameEnd;
    }

    /// The offset of the root tag's `>` or `/>`.
    public int end() {
        return end;
    }

    /// The value of the attribute called `name`, or empty.
    public Optional<String> attribute(String name) {
        Objects.requireNonNull(name, "name");
        for (var attribute : attributes) {
            if (attribute.name().equals(name)) {
                return Optional.of(attribute.value());
            }
        }
        return Optional.empty();
    }

    /// The `viewBox`, or empty when there is none or it is not four numbers with
    /// a width and a height above zero.
    public Optional<ViewBox> viewBox() {
        return attribute("viewBox").flatMap(ViewBox::parse);
    }

    /// Whether `preserveAspectRatio` is `none`: the view box stretched to the
    /// viewport rather than placed in it.
    public boolean stretches() {
        var value = attribute("preserveAspectRatio").map(String::strip).orElse("");
        if (value.startsWith("defer")) {
            value = value.substring("defer".length()).strip();
        }
        return value.equals("none") || value.startsWith("none ");
    }

    /// Whether the root gives `currentColor` a value of its own, as a `color`
    /// attribute or a `color` in its `style`.
    public boolean namesItsColor() {
        return attribute("color").isPresent()
                || attribute("style")
                        .map(style -> STYLE_COLOR.matcher(style).find())
                        .orElse(false);
    }

    /// The document with `replacements` on its root: each attribute named there
    /// is removed from the tag, and all of them are written at its end in their
    /// order. The rest of the document is copied as it is. With no replacements,
    /// the document itself, which the caller must not change.
    ///
    /// @param replacements attribute names and values; a value is written
    ///                     between double quotes, so it must not hold one
    public byte[] with(SequencedMap<String, String> replacements) {
        Objects.requireNonNull(replacements, "replacements");
        if (replacements.isEmpty()) {
            return document;
        }
        var out = new ByteArrayOutputStream(document.length + 64 * replacements.size());
        out.write(document, 0, nameEnd);
        for (var attribute : attributes) {
            if (!replacements.containsKey(attribute.name())) {
                out.write(' ');
                out.write(document, attribute.start(), attribute.end() - attribute.start());
            }
        }
        replacements.forEach((name, value) -> {
            if (value.indexOf('"') >= 0) {
                throw new IllegalArgumentException("an attribute value with a double quote in it: " + value);
            }
            out.writeBytes((' ' + name + "=\"" + value + '"').getBytes(StandardCharsets.UTF_8));
        });
        out.write(document, end, document.length - end);
        return out.toByteArray();
    }

    /// [#with(SequencedMap)] for one attribute.
    public byte[] with(String name, String value) {
        var replacements = new LinkedHashMap<String, String>();
        replacements.put(name, value);
        return with(replacements);
    }

    /// `rgba(r,g,b,a)` for `argb`, the one form of a colour with alpha that
    /// ThorVG reads: commas, and alpha from 0 to 1.
    public static String cssColor(int argb) {
        var alpha = (argb >>> 24) / 255.0;
        return String.format(
                Locale.ROOT,
                "rgba(%d,%d,%d,%s)",
                (argb >> 16) & 0xFF,
                (argb >> 8) & 0xFF,
                argb & 0xFF,
                alpha == 1 ? "1" : String.format(Locale.ROOT, "%.4f", alpha));
    }

    /// The same document with its root given `argb` as `color`, unless it names
    /// its own.
    public byte[] withCurrentColor(int argb) {
        return namesItsColor() ? document : with("color", cssColor(argb));
    }

    /// A map of replacements, in the order given, for [#with(SequencedMap)].
    public static SequencedMap<String, String> replacements(String... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("names and values come in pairs");
        }
        var map = new LinkedHashMap<String, String>();
        for (var i = 0; i < namesAndValues.length; i += 2) {
            map.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return map;
    }

    private static RootTag readTag(byte[] document, int nameEnd) {
        var attributes = new ArrayList<Attribute>();
        var at = nameEnd;
        while (true) {
            at = skipSpace(document, at);
            if (at >= document.length) {
                throw malformed("the <svg> start tag does not close", at);
            }
            if (document[at] == '>' || startsWith(document, at, "/>")) {
                return new RootTag(document, nameEnd, at, attributes);
            }
            if (at > nameEnd && !isSpace(document[at - 1])) {
                throw malformed("no space between two attributes of <svg>", at);
            }
            var nameStart = at;
            while (at < document.length && !endsName(document[at]) && document[at] != '=') {
                at++;
            }
            if (at == nameStart) {
                throw malformed("an attribute of <svg> with no name", at);
            }
            var name = new String(document, nameStart, at - nameStart, StandardCharsets.UTF_8);
            at = skipSpace(document, at);
            if (at >= document.length || document[at] != '=') {
                throw malformed("the attribute " + name + " of <svg> has no value", at);
            }
            at = skipSpace(document, at + 1);
            if (at >= document.length || (document[at] != '"' && document[at] != '\'')) {
                throw malformed("the value of " + name + " on <svg> is not quoted", at);
            }
            var quote = document[at];
            var valueStart = at + 1;
            var valueEnd = valueStart;
            while (valueEnd < document.length && document[valueEnd] != quote) {
                valueEnd++;
            }
            if (valueEnd >= document.length) {
                throw malformed("the value of " + name + " on <svg> does not close", valueStart);
            }
            var value = new String(document, valueStart, valueEnd - valueStart, StandardCharsets.UTF_8);
            at = valueEnd + 1;
            attributes.add(new Attribute(name, value, nameStart, at));
        }
    }

    /// Past a doctype, its internal subset and any quoted literal in it.
    private static int pastDoctype(byte[] document, int at) {
        var depth = 0;
        byte quote = 0;
        for (var i = at + 2; i < document.length; i++) {
            var b = document[i];
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
        throw malformed("an unclosed doctype", at);
    }

    private static int past(byte[] document, int from, String terminator, String what) {
        var bytes = terminator.getBytes(StandardCharsets.US_ASCII);
        for (var i = from; i + bytes.length <= document.length; i++) {
            if (startsWith(document, i, bytes)) {
                return i + bytes.length;
            }
        }
        throw malformed(what, from);
    }

    private static ImageDecodeException malformed(String what, int at) {
        return new ImageDecodeException("not an SVG document: " + what + " at byte " + at);
    }

    private static int skipSpace(byte[] document, int at) {
        while (at < document.length && isSpace(document[at])) {
            at++;
        }
        return at;
    }

    private static boolean isSpace(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }

    private static boolean endsName(byte b) {
        return isSpace(b) || b == '>' || b == '/';
    }

    private static boolean startsWith(byte[] document, int at, String ascii) {
        return startsWith(document, at, ascii.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean startsWith(byte[] document, int at, byte[] prefix) {
        if (at < 0 || at + prefix.length > document.length) {
            return false;
        }
        for (var i = 0; i < prefix.length; i++) {
            if (document[at + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}

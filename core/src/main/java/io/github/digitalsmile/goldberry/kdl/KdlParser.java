package io.github.digitalsmile.goldberry.kdl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/// Reads [KDL 2.0][spec] markup into [KdlNode]s.
///
/// Hand-written, for the reason [ADR-0010] gives about the FFM bindings: KDL 2.0
/// has no mature Java parser, and the language is small enough that owning one is
/// cheaper than depending on one — and this one has to produce the source
/// positions §9 requires, which a general-purpose parser typically discards.
///
/// ## What is supported
///
/// Nodes with arguments, properties and children; bare and quoted identifiers;
/// quoted strings with escapes; raw strings (`#"…"#`); numbers in decimal, hex,
/// octal and binary with `_` separators; the `#true` / `#false` / `#null` /
/// `#inf` / `#-inf` / `#nan` keywords; `//` and nesting `/* */` comments; the
/// `/-` slashdash comment on nodes, arguments, properties and child blocks;
/// semicolon-separated nodes; and `\` line continuations.
///
/// ## What is refused, and why
///
/// - **Type annotations** — `(u8)123`. Parsed far enough to be recognised and
///   then refused by name. They exist for schema-typed KDL; the widget schema of
///   §9 has no use for one, and accepting-then-discarding is how a document that
///   says something the toolkit ignores looks like it worked.
/// - **Multi-line quoted strings** — `"""…"""`. Their dedent rules are subtle and
///   no widget attribute has asked for one. Refused with a message naming the
///   feature rather than failing as a mismatched quote.
///
/// Both follow the rule the CSS engine set: a documented subset that says so
/// loudly beats a permissive parser that drops what it does not understand.
///
/// [spec]: https://github.com/kdl-org/kdl/blob/main/SPEC.md
public final class KdlParser {

    private final String source;
    private int index;
    private int line = 1;
    private int column = 1;

    private KdlParser(String source) {
        this.source = source;
    }

    /// Parses a whole document.
    ///
    /// @throws KdlSyntaxException if the markup is not in the supported subset
    public static List<KdlNode> parse(String markup) {
        Objects.requireNonNull(markup, "markup");
        var parser = new KdlParser(normalizeNewlines(markup));
        var nodes = parser.nodes(false);
        parser.skipTrivia(true);
        if (!parser.atEnd()) {
            throw parser.error("unexpected " + parser.describeHere());
        }
        return nodes;
    }

    /// The spec preprocesses CR and CRLF into LF. Doing it once means no later
    /// rule has to know there are three spellings of a newline.
    private static String normalizeNewlines(String source) {
        return source.indexOf('\r') < 0 ? source : source.replace("\r\n", "\n").replace('\r', '\n');
    }

    /// Nodes until the end of input, or until the `}` closing a child block.
    private List<KdlNode> nodes(boolean inChildBlock) {
        var nodes = new ArrayList<KdlNode>();
        while (true) {
            skipTrivia(true);
            if (atEnd()) {
                return nodes;
            }
            if (peek() == '}') {
                if (!inChildBlock) {
                    throw error("unexpected \"}\" with no open child block");
                }
                return nodes;
            }
            // A slashdash before a node comments the whole node out, children
            // and all -- so it is parsed and then discarded, not skipped
            // textually.
            var commented = takeSlashdash();
            var node = node();
            if (!commented) {
                nodes.add(node);
            }
        }
    }

    private KdlNode node() {
        refuseTypeAnnotation();
        var startLine = line;
        var startColumn = column;
        var name = identifier("a node name");

        var arguments = new ArrayList<KdlValue>();
        var properties = new LinkedHashMap<String, KdlValue>();
        var children = new ArrayList<KdlNode>();

        while (true) {
            // Only trivia *within* a line separates a node's parts; a newline
            // ends the node unless a "\" continued it.
            var sawSpace = skipTrivia(false);
            if (atEnd() || peek() == '\n' || peek() == ';' || peek() == '}') {
                if (!atEnd() && (peek() == '\n' || peek() == ';')) {
                    advance();
                }
                break;
            }
            if (peek() == '{') {
                children.addAll(childBlock());
                // Nothing may follow a child block on the same line except the
                // end of the node.
                skipTrivia(false);
                if (!atEnd() && (peek() == ';' || peek() == '\n')) {
                    advance();
                }
                break;
            }
            if (!sawSpace) {
                throw error("expected a space before " + describeHere());
            }

            var commented = takeSlashdash();
            if (peek() == '{') {
                var discarded = childBlock();
                if (!commented) {
                    children.addAll(discarded);
                }
                continue;
            }
            var entry = entry();
            if (commented) {
                continue;
            }
            if (entry.key() != null) {
                properties.put(entry.key(), entry.value());
            } else {
                arguments.add(entry.value());
            }
        }

        return new KdlNode(name, arguments, properties, children, startLine, startColumn);
    }

    /// An argument or a property, told apart by whether an `=` follows.
    private record Entry(String key, KdlValue value) {
    }

    private Entry entry() {
        refuseTypeAnnotation();
        // A property key is an identifier; an argument may be an identifier-
        // shaped string too, so which one this is only becomes clear at the "=".
        if (startsIdentifier() && !startsNumber() && peek() != '#') {
            var startIndex = index;
            var startLine = line;
            var startColumn = column;
            var word = identifier("an identifier");
            if (!atEnd() && peek() == '=') {
                advance();
                refuseTypeAnnotation();
                return new Entry(word, value());
            }
            // Not a property after all. A bare word is not a legal argument in
            // KDL 2.0 -- only #keywords, numbers and strings are -- so rewind
            // and let value() produce the right complaint.
            index = startIndex;
            line = startLine;
            column = startColumn;
            return new Entry(null, value());
        }

        var value = value();
        if (!atEnd() && peek() == '=') {
            advance();
            // "1=2": the left side of a property has to be a name.
            throw error("a property name must be an identifier, not " + value);
        }
        return new Entry(null, value);
    }

    private List<KdlNode> childBlock() {
        expect('{');
        var children = nodes(true);
        skipTrivia(true);
        expect('}');
        return children;
    }

    private KdlValue value() {
        if (atEnd()) {
            throw error("expected a value");
        }
        var c = peek();
        if (c == '"') {
            return new KdlValue.Str(quotedString());
        }
        if (c == '#') {
            return hashValue();
        }
        if (startsNumber()) {
            return new KdlValue.Num(number());
        }
        throw error("expected a value, found " + describeHere());
    }

    /// `#true`, `#false`, `#null`, `#inf`, `#-inf`, `#nan`, or a raw string.
    private KdlValue hashValue() {
        if (index + 1 < source.length() && (source.charAt(index + 1) == '"' || source.charAt(index + 1) == '#')) {
            return new KdlValue.Str(rawString());
        }
        advance(); // #
        var word = new StringBuilder();
        while (!atEnd() && (Character.isLetterOrDigit(peek()) || peek() == '-')) {
            word.append(advance());
        }
        return switch (word.toString()) {
            case "true" -> new KdlValue.Bool(true);
            case "false" -> new KdlValue.Bool(false);
            case "null" -> KdlValue.Null.NULL;
            case "inf" -> new KdlValue.Num(Double.POSITIVE_INFINITY);
            case "-inf" -> new KdlValue.Num(Double.NEGATIVE_INFINITY);
            case "nan" -> new KdlValue.Num(Double.NaN);
            default -> throw error("unknown keyword \"#" + word + "\"");
        };
    }

    /// `#"…"#`, `##"…"##`, and so on: the fence length is the number of `#`.
    private String rawString() {
        var fence = 0;
        while (!atEnd() && peek() == '#') {
            advance();
            fence++;
        }
        expect('"');
        var closing = "\"" + "#".repeat(fence);
        var text = new StringBuilder();
        while (true) {
            if (atEnd()) {
                throw error("unterminated raw string");
            }
            if (peek() == '"' && source.startsWith(closing, index)) {
                for (var i = 0; i < closing.length(); i++) {
                    advance();
                }
                return text.toString();
            }
            text.append(advance());
        }
    }

    private String quotedString() {
        if (source.startsWith("\"\"\"", index)) {
            throw error("multi-line strings (\"\"\") are not part of the widget schema");
        }
        var startLine = line;
        var startColumn = column;
        advance(); // "
        var text = new StringBuilder();
        while (true) {
            if (atEnd()) {
                throw new KdlSyntaxException("unterminated string", startLine, startColumn);
            }
            var c = peek();
            if (c == '"') {
                advance();
                return text.toString();
            }
            if (c == '\n') {
                throw new KdlSyntaxException("unterminated string", startLine, startColumn);
            }
            if (c == '\\') {
                advance();
                text.append(escape());
                continue;
            }
            text.append(advance());
        }
    }

    private String escape() {
        if (atEnd()) {
            throw error("a string ends with a backslash");
        }
        var c = advance();
        return switch (c) {
            case 'n' -> "\n";
            case 'r' -> "\r";
            case 't' -> "\t";
            case '\\' -> "\\";
            case '"' -> "\"";
            case 'b' -> "\b";
            case 'f' -> "\f";
            case 's' -> " ";
            case '/' -> "/";
            case 'u' -> unicodeEscape();
            // A backslash before a newline is a line continuation inside a
            // string and contributes nothing.
            case '\n' -> "";
            default -> throw error("unknown escape \"\\" + c + "\"");
        };
    }

    private String unicodeEscape() {
        expect('{');
        var hex = new StringBuilder();
        while (!atEnd() && peek() != '}') {
            hex.append(advance());
        }
        expect('}');
        try {
            var code = Integer.parseInt(hex.toString(), 16);
            return new String(Character.toChars(code));
        } catch (RuntimeException e) {
            throw error("\\u{" + hex + "} is not a Unicode scalar value");
        }
    }

    private double number() {
        var text = new StringBuilder();
        if (peek() == '+' || peek() == '-') {
            text.append(advance());
        }
        if (peek() == '0' && index + 1 < source.length()) {
            var radix = Character.toLowerCase(source.charAt(index + 1));
            if (radix == 'x' || radix == 'o' || radix == 'b') {
                advance();
                advance();
                var digits = new StringBuilder();
                while (!atEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
                    var c = advance();
                    if (c != '_') {
                        digits.append(c);
                    }
                }
                var base = switch (radix) {
                    case 'x' -> 16;
                    case 'o' -> 8;
                    default -> 2;
                };
                try {
                    var magnitude = Long.parseLong(digits.toString(), base);
                    return text.toString().startsWith("-") ? -magnitude : magnitude;
                } catch (NumberFormatException e) {
                    throw error("\"" + digits + "\" is not a base-" + base + " number");
                }
            }
        }
        while (!atEnd() && (Character.isDigit(peek()) || peek() == '_' || peek() == '.'
                || peek() == 'e' || peek() == 'E'
                || ((peek() == '+' || peek() == '-') && isExponentSign()))) {
            var c = advance();
            if (c != '_') {
                text.append(c);
            }
        }
        try {
            return Double.parseDouble(text.toString());
        } catch (NumberFormatException e) {
            throw error("\"" + text + "\" is not a number");
        }
    }

    /// Whether a `+`/`-` here is an exponent's sign rather than the start of
    /// something else — the `-` of `1e-3`.
    private boolean isExponentSign() {
        var previous = source.charAt(index - 1);
        return previous == 'e' || previous == 'E';
    }

    private String identifier(String what) {
        if (atEnd()) {
            throw error("expected " + what);
        }
        if (peek() == '"') {
            return quotedString();
        }
        if (peek() == '#' && index + 1 < source.length()
                && (source.charAt(index + 1) == '"' || source.charAt(index + 1) == '#')) {
            return rawString();
        }
        if (!startsIdentifier()) {
            throw error("expected " + what + ", found " + describeHere());
        }
        var name = new StringBuilder();
        while (!atEnd() && isIdentifierChar(peek())) {
            name.append(advance());
        }
        return name.toString();
    }

    /// KDL 2.0 excludes these from bare identifiers so that markup never needs a
    /// lookahead to know whether a word is a name.
    private static boolean isIdentifierChar(char c) {
        return !Character.isWhitespace(c) && "\\/(){};[]\"#=".indexOf(c) < 0;
    }

    private boolean startsIdentifier() {
        if (atEnd() || !isIdentifierChar(peek())) {
            return false;
        }
        // A bare identifier may not look like a number: "1abc" is an error, not
        // a name.
        return !startsNumber();
    }

    private boolean startsNumber() {
        if (atEnd()) {
            return false;
        }
        var c = peek();
        if (Character.isDigit(c)) {
            return true;
        }
        if ((c == '+' || c == '-') && index + 1 < source.length()) {
            var next = source.charAt(index + 1);
            return Character.isDigit(next)
                    || (next == '.' && index + 2 < source.length() && Character.isDigit(source.charAt(index + 2)));
        }
        if (c == '.' && index + 1 < source.length()) {
            return Character.isDigit(source.charAt(index + 1));
        }
        return false;
    }

    /// Consumes a `/-` if one is here.
    private boolean takeSlashdash() {
        if (source.startsWith("/-", index)) {
            advance();
            advance();
            skipTrivia(true);
            return true;
        }
        return false;
    }

    /// `(type)`, which this subset refuses rather than discards.
    private void refuseTypeAnnotation() {
        if (!atEnd() && peek() == '(') {
            throw error("type annotations are not part of the widget schema");
        }
    }

    /// Whitespace and comments.
    ///
    /// @param newlines whether a newline counts as trivia. Inside a node it does
    ///                 not — a newline ends the node — unless a `\` continued it.
    /// @return whether anything was skipped
    private boolean skipTrivia(boolean newlines) {
        var skipped = false;
        while (!atEnd()) {
            var c = peek();
            if (c == '\n') {
                if (!newlines) {
                    return skipped;
                }
                advance();
                skipped = true;
            } else if (Character.isWhitespace(c)) {
                advance();
                skipped = true;
            } else if (c == '\\') {
                // An escline: the backslash, any trivia, then a newline that
                // does NOT end the node.
                advance();
                while (!atEnd() && peek() != '\n' && Character.isWhitespace(peek())) {
                    advance();
                }
                if (!atEnd() && peek() == '\n') {
                    advance();
                }
                skipped = true;
            } else if (source.startsWith("//", index)) {
                while (!atEnd() && peek() != '\n') {
                    advance();
                }
                skipped = true;
            } else if (source.startsWith("/*", index)) {
                blockComment();
                skipped = true;
            } else {
                return skipped;
            }
        }
        return skipped;
    }

    /// `/* … */`, which nests — the one place KDL differs from C and the one
    /// place a naive scanner ends the comment early.
    private void blockComment() {
        var startLine = line;
        var startColumn = column;
        var depth = 0;
        while (!atEnd()) {
            if (source.startsWith("/*", index)) {
                advance();
                advance();
                depth++;
            } else if (source.startsWith("*/", index)) {
                advance();
                advance();
                if (--depth == 0) {
                    return;
                }
            } else {
                advance();
            }
        }
        throw new KdlSyntaxException("unterminated block comment", startLine, startColumn);
    }

    private void expect(char c) {
        if (atEnd() || peek() != c) {
            throw error("expected \"" + c + "\", found " + describeHere());
        }
        advance();
    }

    private String describeHere() {
        return atEnd() ? "end of input" : "\"" + peek() + "\"";
    }

    private KdlSyntaxException error(String message) {
        return new KdlSyntaxException(message, line, column);
    }

    private boolean atEnd() {
        return index >= source.length();
    }

    private char peek() {
        return source.charAt(index);
    }

    private char advance() {
        var c = source.charAt(index++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }
}

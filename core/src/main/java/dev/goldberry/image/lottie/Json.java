package dev.goldberry.image.lottie;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/// A strict JSON reader, RFC 8259 and nothing more: the six kinds of value as
/// records, and a parser that refuses what is not JSON rather than guessing.
///
/// The toolkit has no JSON reader of its own and a Lottie document is the first
/// thing that has to read one, so this is the smallest one that does: no
/// binding, no streaming, no comments, no trailing commas. A sticker is at most
/// 64 KB compressed, so a tree of records is the whole document in memory once,
/// read once when the animation is made and never again.
///
/// Depth is bounded, so a document of nested brackets ends in a refusal rather
/// than a stack overflow.
final class Json {

    /// How deep arrays and objects may nest. A Lottie document is a dozen deep;
    /// a hundred times that is somebody else's file.
    static final int MAX_DEPTH = 512;

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    /// One value: an object, an array, a number, a string, a boolean or null.
    sealed interface Value {}

    /// An object, its members in the order the document wrote them.
    record Obj(Map<String, Value> members) implements Value {

        /// The member called `name`, or null when there is none.
        @Nullable
        Value get(String name) {
            return members.get(name);
        }
    }

    /// An array.
    record Arr(List<Value> items) implements Value {}

    /// A number, as a double, which every number in a Lottie document fits.
    record Num(double value) implements Value {}

    /// A string, unescaped.
    record Str(String value) implements Value {}

    /// `true` or `false`.
    record Bool(boolean value) implements Value {}

    /// `null`.
    record Null() implements Value {}

    /// The one value in `text`.
    ///
    /// @throws JsonException if `text` is not exactly one JSON value, with where
    ///         it stopped making sense
    static Value parse(String text) {
        var json = new Json(text);
        json.skipSpace();
        var value = json.value(0);
        json.skipSpace();
        if (json.at != text.length()) {
            throw json.error("text after the value");
        }
        return value;
    }

    /// Text that was meant to be JSON and was not.
    static final class JsonException extends RuntimeException {

        @java.io.Serial
        private static final long serialVersionUID = 1L;

        JsonException(String message) {
            super(message);
        }
    }

    private Value value(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("nesting deeper than " + MAX_DEPTH);
        }
        if (at >= text.length()) {
            throw error("the text ended where a value was expected");
        }
        var c = text.charAt(at);
        return switch (c) {
            case '{' -> object(depth);
            case '[' -> array(depth);
            case '"' -> new Str(string());
            case 't' -> literal("true", new Bool(true));
            case 'f' -> literal("false", new Bool(false));
            case 'n' -> literal("null", new Null());
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield number();
                }
                throw error("'" + c + "' cannot start a value");
            }
        };
    }

    private Obj object(int depth) {
        at++;
        var members = new LinkedHashMap<String, Value>();
        skipSpace();
        if (peek() == '}') {
            at++;
            return new Obj(members);
        }
        while (true) {
            skipSpace();
            if (peek() != '"') {
                throw error("a member name is a string");
            }
            var name = string();
            skipSpace();
            expect(':');
            skipSpace();
            members.put(name, value(depth + 1));
            skipSpace();
            var next = peek();
            at++;
            if (next == '}') {
                return new Obj(members);
            }
            if (next != ',') {
                at--;
                throw error("expected ',' or '}' in an object");
            }
        }
    }

    private Arr array(int depth) {
        at++;
        var items = new ArrayList<Value>();
        skipSpace();
        if (peek() == ']') {
            at++;
            return new Arr(items);
        }
        while (true) {
            skipSpace();
            items.add(value(depth + 1));
            skipSpace();
            var next = peek();
            at++;
            if (next == ']') {
                return new Arr(items);
            }
            if (next != ',') {
                at--;
                throw error("expected ',' or ']' in an array");
            }
        }
    }

    private String string() {
        at++;
        var out = new StringBuilder();
        while (true) {
            if (at >= text.length()) {
                throw error("a string that never ends");
            }
            var c = text.charAt(at++);
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                at--;
                throw error("a control character inside a string");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (at >= text.length()) {
                throw error("a string that ends in an escape");
            }
            var escaped = text.charAt(at++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length()) {
                        throw error("a \\u escape needs four hex digits");
                    }
                    var code = 0;
                    for (var i = 0; i < 4; i++) {
                        var digit = Character.digit(text.charAt(at++), 16);
                        if (digit < 0) {
                            at--;
                            throw error("a \\u escape needs four hex digits");
                        }
                        code = code * 16 + digit;
                    }
                    out.append((char) code);
                }
                default -> {
                    at--;
                    throw error("'\\" + escaped + "' is not an escape");
                }
            }
        }
    }

    private Num number() {
        var start = at;
        if (peek() == '-') {
            at++;
        }
        if (peek() == '0') {
            at++;
        } else if (isDigit(peek())) {
            digits();
        } else {
            throw error("a number needs a digit");
        }
        if (peek() == '.') {
            at++;
            if (!isDigit(peek())) {
                throw error("a fraction needs a digit");
            }
            digits();
        }
        if (peek() == 'e' || peek() == 'E') {
            at++;
            if (peek() == '+' || peek() == '-') {
                at++;
            }
            if (!isDigit(peek())) {
                throw error("an exponent needs a digit");
            }
            digits();
        }
        var value = Double.parseDouble(text.substring(start, at));
        if (!Double.isFinite(value)) {
            at = start;
            throw error("a number too large for a double");
        }
        return new Num(value);
    }

    private void digits() {
        while (isDigit(peek())) {
            at++;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private Value literal(String word, Value value) {
        if (!text.startsWith(word, at)) {
            throw error("not a value");
        }
        at += word.length();
        return value;
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("expected '" + c + "'");
        }
        at++;
    }

    /// The character at the cursor, or NUL at the end, which nothing valid is.
    private char peek() {
        return at < text.length() ? text.charAt(at) : '\0';
    }

    private void skipSpace() {
        while (at < text.length()) {
            var c = text.charAt(at);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            at++;
        }
    }

    private JsonException error(String what) {
        return new JsonException("not JSON at character " + at + ": " + what);
    }
}

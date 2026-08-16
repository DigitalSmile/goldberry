package io.github.digitalsmile.goldberry.css;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Turns stylesheet text into [Token]s.
///
/// Follows [CSS Syntax Level 3][spec] for everything it implements, because the
/// awkward parts of CSS tokenization are awkward for reasons — `#ff0000` is not
/// an identifier, `-5px` is one token and `- 5px` is three, `var (x)` is not a
/// function call — and a hand-waved tokenizer gets each of them subtly wrong in
/// a way that only shows up in somebody's stylesheet months later.
///
/// ## What is deferred, and why
///
/// - **`url()` unquoted form.** `url(foo.png)` tokenizes by its own rules that no
///   other function shares. Goldberry has no property that takes a URL yet;
///   `url("foo.png")` will tokenize as a normal function when one arrives.
/// - **CDO/CDC (`<!--`, `-->`).** They exist so 1996 browsers could hide CSS
///   inside HTML comments. There is no such thing here.
/// - **Unicode ranges.** Only meaningful inside `@font-face`, which §6.1 does not
///   have: fonts are resolved through the asset catalog, not by the stylesheet.
/// - **`bad-string` / `bad-url` recovery tokens.** The spec emits these so a
///   browser can carry on; [CssSyntaxException] explains why this does not.
///
/// Everything else in the token grammar is here, including escapes and non-ASCII
/// identifiers.
///
/// [spec]: https://www.w3.org/TR/css-syntax-3/#tokenization
public final class CssTokenizer {

    /// What the spec calls the replacement character: what a NULL, a surrogate,
    /// or an out-of-range escape becomes.
    private static final char REPLACEMENT = '�';

    private final String source;

    private int index;
    private int line = 1;
    private int column = 1;

    private CssTokenizer(String source) {
        this.source = source;
    }

    /// Tokenizes a whole stylesheet.
    ///
    /// The returned list always ends with an [TokenType#EOF] token, so a parser
    /// can look one token ahead without checking bounds.
    ///
    /// @throws CssSyntaxException if the text cannot be tokenized at all — an
    ///         unterminated string, or a newline inside one
    public static List<Token> tokenize(String source) {
        Objects.requireNonNull(source, "source");
        return new CssTokenizer(normalizeNewlines(source)).run();
    }

    /// Whether `text` is a valid CSS identifier.
    ///
    /// Public through [Token#isIdentifierLike()], which is the only caller that
    /// matters: telling `#main` from `#ff0000`.
    static boolean isIdentifier(String text) {
        if (text.isEmpty()) {
            return false;
        }
        var i = 0;
        if (text.charAt(0) == '-') {
            if (text.length() == 1) {
                // A lone "-" is a delim, not a name.
                return false;
            }
            // "--custom" is a valid ident: the second hyphen is what makes the
            // custom-property namespace legal rather than a hack.
            if (text.charAt(1) == '-') {
                i = 2;
            } else {
                i = 1;
                if (!isIdentStart(text.charAt(1))) {
                    return false;
                }
                i = 2;
            }
        } else if (isIdentStart(text.charAt(0))) {
            i = 1;
        } else {
            return false;
        }
        for (; i < text.length(); i++) {
            if (!isIdentChar(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /// The spec preprocesses the input stream: CRLF and CR become LF, and a form
    /// feed becomes LF. Doing it once here means no later rule has to think
    /// about three spellings of "newline".
    private static String normalizeNewlines(String source) {
        if (source.indexOf('\r') < 0 && source.indexOf('\f') < 0) {
            return source;
        }
        return source.replace("\r\n", "\n").replace('\r', '\n').replace('\f', '\n');
    }

    private List<Token> run() {
        var tokens = new ArrayList<Token>();
        while (true) {
            var token = next();
            tokens.add(token);
            if (token.is(TokenType.EOF)) {
                return tokens;
            }
        }
    }

    private Token next() {
        skipComments();
        var startLine = line;
        var startColumn = column;
        if (atEnd()) {
            return Token.of(TokenType.EOF, "", startLine, startColumn);
        }

        var c = peek();
        if (isWhitespace(c)) {
            var text = new StringBuilder();
            while (!atEnd() && isWhitespace(peek())) {
                text.append(advance());
            }
            return Token.of(TokenType.WHITESPACE, text.toString(), startLine, startColumn);
        }
        if (c == '"' || c == '\'') {
            return string(startLine, startColumn);
        }
        if (c == '#') {
            advance();
            // A hash is only a hash if something name-like follows; a bare "#"
            // is a delim. This is what makes "#" in a media query harmless.
            if (!atEnd() && (isIdentChar(peek()) || startsEscape())) {
                return Token.of(TokenType.HASH, consumeName(), startLine, startColumn);
            }
            return Token.of(TokenType.DELIM, "#", startLine, startColumn);
        }
        if (c == '@') {
            advance();
            if (startsIdentifier()) {
                return Token.of(TokenType.AT_KEYWORD, consumeName(), startLine, startColumn);
            }
            return Token.of(TokenType.DELIM, "@", startLine, startColumn);
        }
        if (c == '+' || c == '.' || c == '-') {
            if (startsNumber()) {
                return numeric(startLine, startColumn);
            }
            if (c == '-' && startsIdentifier()) {
                return identLike(startLine, startColumn);
            }
            advance();
            return Token.of(TokenType.DELIM, String.valueOf(c), startLine, startColumn);
        }
        if (isDigit(c)) {
            return numeric(startLine, startColumn);
        }
        if (isIdentStart(c) || c == '\\') {
            return identLike(startLine, startColumn);
        }

        advance();
        return switch (c) {
            case ':' -> Token.of(TokenType.COLON, ":", startLine, startColumn);
            case ';' -> Token.of(TokenType.SEMICOLON, ";", startLine, startColumn);
            case ',' -> Token.of(TokenType.COMMA, ",", startLine, startColumn);
            case '{' -> Token.of(TokenType.OPEN_BRACE, "{", startLine, startColumn);
            case '}' -> Token.of(TokenType.CLOSE_BRACE, "}", startLine, startColumn);
            case '(' -> Token.of(TokenType.OPEN_PAREN, "(", startLine, startColumn);
            case ')' -> Token.of(TokenType.CLOSE_PAREN, ")", startLine, startColumn);
            case '[' -> Token.of(TokenType.OPEN_BRACKET, "[", startLine, startColumn);
            case ']' -> Token.of(TokenType.CLOSE_BRACKET, "]", startLine, startColumn);
            default -> Token.of(TokenType.DELIM, String.valueOf(c), startLine, startColumn);
        };
    }

    /// `/* ... */`, discarded. Loops because two comments can be adjacent, and
    /// the token after them must not be a phantom whitespace.
    private void skipComments() {
        while (index + 1 < source.length() && peek() == '/' && source.charAt(index + 1) == '*') {
            var startLine = line;
            var startColumn = column;
            advance();
            advance();
            var closed = false;
            while (!atEnd()) {
                if (peek() == '*' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                    advance();
                    advance();
                    closed = true;
                    break;
                }
                advance();
            }
            if (!closed) {
                throw new CssSyntaxException("unterminated comment", startLine, startColumn);
            }
        }
    }

    private Token string(int startLine, int startColumn) {
        var quote = advance();
        var text = new StringBuilder();
        while (true) {
            if (atEnd()) {
                throw new CssSyntaxException("unterminated string", startLine, startColumn);
            }
            var c = peek();
            if (c == quote) {
                advance();
                return Token.of(TokenType.STRING, text.toString(), startLine, startColumn);
            }
            if (c == '\n') {
                // The spec calls this a bad-string and recovers. A newline inside
                // a string is always a missing quote, and guessing where it was
                // meant to close is how one typo silently eats the next ten rules.
                throw new CssSyntaxException("unterminated string", startLine, startColumn);
            }
            if (c == '\\') {
                if (index + 1 < source.length() && source.charAt(index + 1) == '\n') {
                    // A backslash-newline inside a string is a line continuation
                    // and contributes nothing.
                    advance();
                    advance();
                    continue;
                }
                advance();
                text.append(consumeEscape());
                continue;
            }
            text.append(advance());
        }
    }

    private Token identLike(int startLine, int startColumn) {
        var name = consumeName();
        if (!atEnd() && peek() == '(') {
            advance();
            return Token.of(TokenType.FUNCTION, name, startLine, startColumn);
        }
        return Token.of(TokenType.IDENT, name, startLine, startColumn);
    }

    private Token numeric(int startLine, int startColumn) {
        var raw = new StringBuilder();
        if (peek() == '+' || peek() == '-') {
            raw.append(advance());
        }
        while (!atEnd() && isDigit(peek())) {
            raw.append(advance());
        }
        if (!atEnd() && peek() == '.' && index + 1 < source.length() && isDigit(source.charAt(index + 1))) {
            raw.append(advance());
            while (!atEnd() && isDigit(peek())) {
                raw.append(advance());
            }
        }
        if (!atEnd() && (peek() == 'e' || peek() == 'E') && hasExponent()) {
            raw.append(advance());
            if (peek() == '+' || peek() == '-') {
                raw.append(advance());
            }
            while (!atEnd() && isDigit(peek())) {
                raw.append(advance());
            }
        }

        var value = Double.parseDouble(raw.toString());
        if (!atEnd() && peek() == '%') {
            advance();
            return new Token(TokenType.PERCENTAGE, raw.toString(), value, "", startLine, startColumn);
        }
        if (startsIdentifier()) {
            // Lowercased: units are case-insensitive, and every later comparison
            // is simpler if that is settled once, here.
            var unit = consumeName().toLowerCase(java.util.Locale.ROOT);
            return new Token(TokenType.DIMENSION, raw.toString(), value, unit, startLine, startColumn);
        }
        return new Token(TokenType.NUMBER, raw.toString(), value, "", startLine, startColumn);
    }

    /// Whether an `e` here begins an exponent rather than a unit — `1e5` against
    /// `1em`.
    private boolean hasExponent() {
        var i = index + 1;
        if (i < source.length() && (source.charAt(i) == '+' || source.charAt(i) == '-')) {
            i++;
        }
        return i < source.length() && isDigit(source.charAt(i));
    }

    /// Whether the input at the cursor begins a number: `1`, `.5`, `-2`, `+.3`.
    private boolean startsNumber() {
        var c = peek();
        if (isDigit(c)) {
            return true;
        }
        if (c == '.') {
            return index + 1 < source.length() && isDigit(source.charAt(index + 1));
        }
        if (c == '+' || c == '-') {
            if (index + 1 >= source.length()) {
                return false;
            }
            var second = source.charAt(index + 1);
            if (isDigit(second)) {
                return true;
            }
            return second == '.' && index + 2 < source.length() && isDigit(source.charAt(index + 2));
        }
        return false;
    }

    /// Whether the input at the cursor begins an identifier.
    private boolean startsIdentifier() {
        if (atEnd()) {
            return false;
        }
        var c = peek();
        if (c == '-') {
            if (index + 1 >= source.length()) {
                return false;
            }
            var second = source.charAt(index + 1);
            return isIdentStart(second) || second == '-' || startsEscapeAt(index + 1);
        }
        return isIdentStart(c) || startsEscape();
    }

    private boolean startsEscape() {
        return startsEscapeAt(index);
    }

    /// A `\` begins an escape unless a newline follows it.
    private boolean startsEscapeAt(int at) {
        if (at >= source.length() || source.charAt(at) != '\\') {
            return false;
        }
        return at + 1 >= source.length() || source.charAt(at + 1) != '\n';
    }

    private String consumeName() {
        var name = new StringBuilder();
        while (!atEnd()) {
            var c = peek();
            if (isIdentChar(c)) {
                name.append(advance());
            } else if (startsEscape()) {
                advance();
                name.append(consumeEscape());
            } else {
                break;
            }
        }
        return name.toString();
    }

    /// Consumes an escape, the `\` already eaten.
    ///
    /// Hex escapes take up to six digits and one optional trailing whitespace,
    /// which is the rule that makes `\41 B` mean "AB" rather than "A B".
    private String consumeEscape() {
        if (atEnd()) {
            // A trailing backslash. The spec substitutes the replacement
            // character rather than failing.
            return String.valueOf(REPLACEMENT);
        }
        var c = peek();
        if (!isHexDigit(c)) {
            return String.valueOf(advance());
        }
        var hex = new StringBuilder();
        while (hex.length() < 6 && !atEnd() && isHexDigit(peek())) {
            hex.append(advance());
        }
        if (!atEnd() && isWhitespace(peek())) {
            advance();
        }
        var code = Integer.parseInt(hex.toString(), 16);
        if (code == 0 || code > Character.MAX_CODE_POINT || Character.isSurrogate((char) code)) {
            return String.valueOf(REPLACEMENT);
        }
        return new String(Character.toChars(code));
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

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /// Non-ASCII counts as a name start, which is what lets a stylesheet use a
    /// class named in a language that is not English.
    private static boolean isIdentStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c >= 0x80;
    }

    private static boolean isIdentChar(char c) {
        return isIdentStart(c) || isDigit(c) || c == '-';
    }
}

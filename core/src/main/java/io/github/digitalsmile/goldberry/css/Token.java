package io.github.digitalsmile.goldberry.css;

/// One CSS token, and where it came from.
///
/// The source position is on every token rather than reconstructed later,
/// because the only thing an author wants from a stylesheet error is the line
/// and column, and by the time a cascade has run the token is long gone.
///
/// @param type    what kind of token this is
/// @param text    the token's value with escapes resolved and quotes removed —
///                the identifier's name, the string's contents, the hash's value
/// @param numeric the value of a [TokenType#NUMBER], [TokenType#PERCENTAGE] or
///                [TokenType#DIMENSION]; 0 otherwise
/// @param unit    the lowercased unit of a [TokenType#DIMENSION] — `px`, `em`;
///                empty otherwise
/// @param line    1-based line
/// @param column  1-based column of the token's first character
public record Token(
        TokenType type, String text, double numeric, String unit, int line, int column) {

    public Token {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(text, "text");
        java.util.Objects.requireNonNull(unit, "unit");
    }

    static Token of(TokenType type, String text, int line, int column) {
        return new Token(type, text, 0, "", line, column);
    }

    /// Whether this token is `type` — the test a parser makes most often.
    public boolean is(TokenType candidate) {
        return type == candidate;
    }

    /// Whether this is a [TokenType#DELIM] holding exactly `c`.
    ///
    /// `>` and `.` and `*` all arrive as delims, so almost every selector
    /// decision is this question.
    public boolean isDelim(char c) {
        return type == TokenType.DELIM && text.length() == 1 && text.charAt(0) == c;
    }

    /// Whether this token's [#text()] is a valid CSS identifier.
    ///
    /// What separates `#main` (an id selector) from `#ff0000` (a colour that is
    /// not one). The spec draws the same line in the same place.
    public boolean isIdentifierLike() {
        return CssTokenizer.isIdentifier(text);
    }

    /// Whether this is an ident equal to `name`, ignoring case.
    ///
    /// CSS keywords are case-insensitive; the values of custom properties are
    /// not, which is why this is a method on the token rather than a blanket
    /// lowercasing in the tokenizer.
    public boolean isIdent(String name) {
        return type == TokenType.IDENT && text.equalsIgnoreCase(name);
    }

    /// This token as CSS text — what it would have to be written as to tokenize
    /// back to itself.
    ///
    /// [#text()] alone is not that: a hash holds `ff0000` without its `#`, and a
    /// dimension holds `16` with the unit kept apart. Anything reassembling a
    /// value — a serialized `ComputedStyle`, a diff between two stylesheets on
    /// hot reload, a message naming the value that failed to parse — needs the
    /// spelling back.
    public String cssText() {
        return switch (type) {
            case HASH -> "#" + text;
            case AT_KEYWORD -> "@" + text;
            case FUNCTION -> text + "(";
            case DIMENSION -> text + unit;
            case PERCENTAGE -> text + "%";
            case WHITESPACE -> " ";
            // Re-quoted with the escape a round trip needs; the tokenizer
            // resolved the original away.
            case STRING -> "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            case EOF -> "";
            default -> text;
        };
    }

    /// A short description for an error message: `ident "flex"`, `"}"`, `end of
    /// input`.
    public String describe() {
        return switch (type) {
            case EOF -> "end of input";
            case WHITESPACE -> "whitespace";
            case IDENT -> "ident \"" + text + "\"";
            case FUNCTION -> "function \"" + text + "(\"";
            case AT_KEYWORD -> "at-keyword \"@" + text + "\"";
            case HASH -> "hash \"#" + text + "\"";
            case STRING -> "string \"" + text + "\"";
            case NUMBER -> "number " + text;
            case PERCENTAGE -> "percentage " + text + "%";
            case DIMENSION -> "dimension " + text + unit;
            default -> "\"" + text + "\"";
        };
    }

    @Override
    public String toString() {
        return describe() + " at " + line + ":" + column;
    }
}

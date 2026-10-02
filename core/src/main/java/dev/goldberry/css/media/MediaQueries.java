package dev.goldberry.css.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.render.desktop.SystemTheme;

/// Reads an `@media` prelude into a [MediaCondition].
///
/// ```java
/// MediaCondition condition = MediaQueries.parse(preludeTokens);
/// ```
///
/// Never throws. A query it cannot read, whether for a feature outside the
/// subset or for a mistake in the writing, comes back as
/// [MediaCondition.Unsupported] with the reason, which never holds. That is
/// CSS's rule for a query list: one unreadable query becomes `not all` and the
/// others in the same list still count. The stylesheet parser decides what to
/// do with the reason, which is to warn in an application's sheet and to
/// refuse in the toolkit's own.
///
/// Read more: [Media queries](https://goldberry.dev/docs/guide/styling.html#media-queries).
public final class MediaQueries {

    /// What `em` and `rem` mean in a media query: CSS takes the initial font
    /// size rather than any element's, and the initial size is 16px.
    private static final double EM = 16;

    private final List<Token> tokens;
    private int index;

    private MediaQueries(List<Token> tokens) {
        this.tokens = tokens;
    }

    /// The condition `prelude` states: the tokens between `@media` and its `{`.
    public static MediaCondition parse(List<Token> prelude) {
        var queries = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : prelude) {
            if (token.is(TokenType.OPEN_PAREN) || token.is(TokenType.FUNCTION)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth--;
            }
            if (depth == 0 && token.is(TokenType.COMMA)) {
                queries.add(current);
                current = new ArrayList<>();
            } else {
                current.add(token);
            }
        }
        queries.add(current);
        if (queries.size() == 1) {
            return query(queries.getFirst());
        }
        return new MediaCondition.Or(queries.stream().map(MediaQueries::query).toList());
    }

    /// One query of a comma-separated list.
    private static MediaCondition query(List<Token> written) {
        var text = text(written);
        var tokens = significant(written);
        if (tokens.isEmpty()) {
            // `@media { … }`, and the empty query between two commas, which CSS
            // reads as `all`.
            return text.isEmpty() ? MediaCondition.ALWAYS : unsupported(text, "an empty query");
        }
        try {
            var parser = new MediaQueries(tokens);
            var condition = parser.mediaQuery();
            if (parser.index != tokens.size()) {
                throw new Unreadable("unexpected " + tokens.get(parser.index).describe());
            }
            return condition;
        } catch (Unreadable e) {
            return unsupported(text, Objects.requireNonNullElse(e.getMessage(), "unreadable"));
        }
    }

    private static MediaCondition unsupported(String text, String reason) {
        return new MediaCondition.Unsupported(text, reason);
    }

    /// `[only | not] type [and condition]`, or a bare condition.
    private MediaCondition mediaQuery() {
        var first = peek();
        if (first != null && first.is(TokenType.IDENT)) {
            var word = lower(first);
            var after = peekAt(1);
            if (!word.equals("not") || after == null || !after.is(TokenType.OPEN_PAREN)) {
                var negated = false;
                if (word.equals("only")) {
                    index++;
                } else if (word.equals("not")) {
                    negated = true;
                    index++;
                }
                var type = mediaType(expectIdent("a media type"));
                MediaCondition result = type;
                var next = peek();
                if (next != null) {
                    if (!isKeyword(next, "and")) {
                        throw new Unreadable("expected \"and\" after the media type, found " + next.describe());
                    }
                    index++;
                    result = new MediaCondition.And(List.of(type, conditionWithoutOr()));
                }
                return negated ? new MediaCondition.Not(result) : result;
            }
        }
        return condition();
    }

    private static MediaCondition mediaType(Token type) {
        return switch (lower(type)) {
            case "all", "screen" -> MediaCondition.ALWAYS;
            // `print` and the retired types are real types that a window is
            // not, which CSS reads as "never" rather than as a mistake.
            case "print", "speech", "tv", "projection", "handheld", "braille", "embossed", "aural", "tty" ->
                MediaCondition.NEVER;
            default -> throw new Unreadable("unknown media type \"" + type.text() + "\"");
        };
    }

    /// `not (…)`, or `(…)` joined by all `and` or all `or`.
    private MediaCondition condition() {
        if (isKeyword(peek(), "not")) {
            index++;
            return new MediaCondition.Not(inParens());
        }
        var first = inParens();
        var between = peek();
        if (between == null) {
            return first;
        }
        var joiner = lower(between);
        if (!joiner.equals("and") && !joiner.equals("or")) {
            throw new Unreadable("expected \"and\" or \"or\", found " + between.describe());
        }
        var parts = new ArrayList<MediaCondition>();
        parts.add(first);
        while (peek() != null) {
            if (!isKeyword(peek(), joiner)) {
                throw new Unreadable("\"and\" and \"or\" cannot be mixed without parentheses");
            }
            index++;
            parts.add(inParens());
        }
        return joiner.equals("and") ? new MediaCondition.And(parts) : new MediaCondition.Or(parts);
    }

    /// After a media type and its `and`: `(…) and (…)`, no `or`.
    private MediaCondition conditionWithoutOr() {
        if (isKeyword(peek(), "not")) {
            index++;
            return new MediaCondition.Not(inParens());
        }
        var parts = new ArrayList<MediaCondition>();
        parts.add(inParens());
        while (isKeyword(peek(), "and")) {
            index++;
            parts.add(inParens());
        }
        return parts.size() == 1 ? parts.getFirst() : new MediaCondition.And(parts);
    }

    /// `( condition )` or `( feature )`.
    private MediaCondition inParens() {
        var open = peek();
        if (open == null || !open.is(TokenType.OPEN_PAREN)) {
            throw new Unreadable("expected \"(\", found " + (open == null ? "the end" : open.describe()));
        }
        index++;
        var inner = peek();
        MediaCondition result;
        if (inner != null && (inner.is(TokenType.OPEN_PAREN) || isKeyword(inner, "not"))) {
            result = condition();
        } else {
            result = feature();
        }
        var close = peek();
        if (close == null || !close.is(TokenType.CLOSE_PAREN)) {
            throw new Unreadable("expected \")\", found " + (close == null ? "the end" : close.describe()));
        }
        index++;
        return result;
    }

    /// `name: value`, or `name op value` for a size.
    private MediaCondition feature() {
        var name = lower(expectIdent("a media feature"));
        var next = peek();
        if (next != null && next.is(TokenType.COLON)) {
            index++;
            return plain(name);
        }
        if (next != null && next.is(TokenType.DELIM)) {
            var comparison = comparison();
            var px = length();
            return switch (name) {
                case "width" -> new MediaCondition.Width(comparison, px);
                case "height" -> new MediaCondition.Height(comparison, px);
                default -> throw new Unreadable("\"" + name + "\" is not a feature this toolkit can compare");
            };
        }
        throw new Unreadable("the media feature \"" + name + "\" needs a value");
    }

    /// `name: value`.
    private MediaCondition plain(String name) {
        return switch (name) {
            case "min-width" -> new MediaCondition.Width(MediaCondition.Comparison.AT_LEAST, length());
            case "max-width" -> new MediaCondition.Width(MediaCondition.Comparison.AT_MOST, length());
            case "width" -> new MediaCondition.Width(MediaCondition.Comparison.EQUAL, length());
            case "min-height" -> new MediaCondition.Height(MediaCondition.Comparison.AT_LEAST, length());
            case "max-height" -> new MediaCondition.Height(MediaCondition.Comparison.AT_MOST, length());
            case "height" -> new MediaCondition.Height(MediaCondition.Comparison.EQUAL, length());
            case "orientation" ->
                switch (lower(expectIdent("portrait or landscape"))) {
                    case "portrait" -> new MediaCondition.Orientation(true);
                    case "landscape" -> new MediaCondition.Orientation(false);
                    default -> throw new Unreadable("orientation is portrait or landscape");
                };
            case "prefers-color-scheme" ->
                switch (lower(expectIdent("light or dark"))) {
                    case "light" -> new MediaCondition.ColorScheme(SystemTheme.LIGHT);
                    case "dark" -> new MediaCondition.ColorScheme(SystemTheme.DARK);
                    default -> throw new Unreadable("prefers-color-scheme is light or dark");
                };
            case "prefers-reduced-motion" ->
                switch (lower(expectIdent("reduce or no-preference"))) {
                    case "reduce" -> new MediaCondition.ReducedMotion(true);
                    case "no-preference" -> new MediaCondition.ReducedMotion(false);
                    default -> throw new Unreadable("prefers-reduced-motion is reduce or no-preference");
                };
            default ->
                throw new Unreadable("\"" + name + "\" is not a media feature this toolkit has; it has width,"
                        + " height, orientation, prefers-color-scheme and prefers-reduced-motion");
        };
    }

    /// `<`, `<=`, `=`, `>=` or `>`, as one or two delimiter tokens.
    private MediaCondition.Comparison comparison() {
        var first = peek();
        if (first == null || !first.is(TokenType.DELIM)) {
            throw new Unreadable("expected a comparison");
        }
        index++;
        var second = peek();
        var equals = second != null && second.isDelim('=');
        if (first.isDelim('=')) {
            return MediaCondition.Comparison.EQUAL;
        }
        if (equals) {
            index++;
        }
        if (first.isDelim('<')) {
            return equals ? MediaCondition.Comparison.AT_MOST : MediaCondition.Comparison.LESS;
        }
        if (first.isDelim('>')) {
            return equals ? MediaCondition.Comparison.AT_LEAST : MediaCondition.Comparison.GREATER;
        }
        throw new Unreadable("\"" + first.text() + "\" is not a comparison");
    }

    /// A length in `px`, `em` or `rem`, or a unitless zero, in logical pixels.
    private double length() {
        var token = peek();
        if (token == null) {
            throw new Unreadable("expected a length");
        }
        index++;
        if (token.is(TokenType.NUMBER) && token.numeric() == 0) {
            return 0;
        }
        if (token.is(TokenType.DIMENSION)) {
            switch (token.unit().toLowerCase(Locale.ROOT)) {
                case "px" -> {
                    return token.numeric();
                }
                case "em", "rem" -> {
                    return token.numeric() * EM;
                }
                default -> {}
            }
        }
        throw new Unreadable(token.describe() + " is not a length in px, em or rem");
    }

    private Token expectIdent(String what) {
        var token = peek();
        if (token == null || !token.is(TokenType.IDENT)) {
            throw new Unreadable("expected " + what + ", found " + (token == null ? "the end" : token.describe()));
        }
        index++;
        return token;
    }

    private @Nullable Token peek() {
        return index < tokens.size() ? tokens.get(index) : null;
    }

    private @Nullable Token peekAt(int ahead) {
        var at = index + ahead;
        return at < tokens.size() ? tokens.get(at) : null;
    }

    private static boolean isKeyword(@Nullable Token token, String word) {
        return token != null && token.is(TokenType.IDENT) && token.text().equalsIgnoreCase(word);
    }

    private static String lower(Token token) {
        return token.text().toLowerCase(Locale.ROOT);
    }

    /// The tokens that mean something: no whitespace, and `and(` read as `and`
    /// followed by `(`, which is how the tokenizer hands over a keyword written
    /// against its parenthesis.
    private static List<Token> significant(List<Token> written) {
        var out = new ArrayList<Token>();
        for (var token : written) {
            if (token.is(TokenType.WHITESPACE)) {
                continue;
            }
            if (token.is(TokenType.FUNCTION)) {
                out.add(new Token(TokenType.IDENT, token.text(), 0, "", token.line(), token.column()));
                out.add(new Token(TokenType.OPEN_PAREN, "(", 0, "", token.line(), token.column()));
                continue;
            }
            out.add(token);
        }
        return out;
    }

    private static String text(List<Token> written) {
        var out = new StringBuilder();
        for (var token : written) {
            out.append(token.cssText());
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    /// A query that cannot be read, caught by [#query] and turned into
    /// [MediaCondition.Unsupported] with this message as the reason.
    private static final class Unreadable extends RuntimeException {

        Unreadable(String message) {
            super(message, null, false, false);
        }
    }
}

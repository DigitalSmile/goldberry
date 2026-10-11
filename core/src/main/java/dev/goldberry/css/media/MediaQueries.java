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

    /// One feature inside parentheses: `name: value`, `name` alone in a
    /// boolean context, `name op value`, `value op name`, or
    /// `value op name op value`.
    private MediaCondition feature() {
        var first = peek();
        if (first != null && !first.is(TokenType.IDENT)) {
            return valueFirst();
        }
        var name = lower(expectIdent("a media feature"));
        var next = peek();
        if (next != null && next.is(TokenType.COLON)) {
            index++;
            return plain(name);
        }
        if (next != null && next.is(TokenType.DELIM)) {
            var comparison = comparison();
            return condition(name, comparison, value(name));
        }
        if (next == null || next.is(TokenType.CLOSE_PAREN)) {
            return bool(name);
        }
        throw new Unreadable("the media feature \"" + name + "\" needs a value");
    }

    /// `400px <= width` and `400px <= width <= 800px`: a value, a comparison
    /// and a name, read as the name's comparison turned round, and a second
    /// comparison after it when there is one.
    private MediaCondition valueFirst() {
        // The value's kind is the name's, which comes after it: find the name,
        // then read from the start.
        var start = index;
        var at = index;
        while (at < tokens.size() && !tokens.get(at).is(TokenType.IDENT)) {
            at++;
        }
        if (at >= tokens.size()) {
            throw new Unreadable("a range names a feature");
        }
        var name = lower(tokens.get(at));
        index = start;
        var low = value(name);
        var lowComparison = comparison();
        if (!isKeyword(peek(), name)) {
            throw new Unreadable("expected \"" + name + "\" in the range");
        }
        index++;
        var lower = condition(name, flipped(lowComparison), low);
        if (peek() == null || peek().is(TokenType.CLOSE_PAREN)) {
            return lower;
        }
        var highComparison = comparison();
        if (isLess(lowComparison) != isLess(highComparison) || lowComparison == MediaCondition.Comparison.EQUAL) {
            throw new Unreadable("a range runs one way: both comparisons are < or both are >");
        }
        var upper = condition(name, highComparison, value(name));
        return new MediaCondition.And(List.of(lower, upper));
    }

    private static boolean isLess(MediaCondition.Comparison comparison) {
        return comparison == MediaCondition.Comparison.LESS || comparison == MediaCondition.Comparison.AT_MOST;
    }

    /// The comparison that says the same thing with its two sides swapped:
    /// `400px <= width` is `width >= 400px`.
    private static MediaCondition.Comparison flipped(MediaCondition.Comparison comparison) {
        return switch (comparison) {
            case LESS -> MediaCondition.Comparison.GREATER;
            case AT_MOST -> MediaCondition.Comparison.AT_LEAST;
            case EQUAL -> MediaCondition.Comparison.EQUAL;
            case AT_LEAST -> MediaCondition.Comparison.AT_MOST;
            case GREATER -> MediaCondition.Comparison.LESS;
        };
    }

    /// A range feature compared with `value`.
    private static MediaCondition condition(String name, MediaCondition.Comparison comparison, double value) {
        return switch (name) {
            case "width" -> new MediaCondition.Width(comparison, value);
            case "height" -> new MediaCondition.Height(comparison, value);
            case "aspect-ratio" -> new MediaCondition.AspectRatio(comparison, value);
            case "resolution" -> new MediaCondition.Resolution(comparison, value);
            default -> throw new Unreadable("\"" + name + "\" is not a feature this toolkit can compare");
        };
    }

    /// A range feature's value: a length for a size, a ratio for
    /// `aspect-ratio`, a resolution for `resolution`.
    private double value(String name) {
        return switch (name) {
            case "width", "height" -> length();
            case "aspect-ratio" -> ratio();
            case "resolution" -> resolution();
            default -> throw new Unreadable("\"" + name + "\" is not a feature this toolkit can compare");
        };
    }

    /// A feature in a boolean context, `(hover)`: true unless its value would
    /// be the one that means "none".
    private static MediaCondition bool(String name) {
        return switch (name) {
            case "hover" -> new MediaCondition.HoverQuery(true, false);
            case "any-hover" -> new MediaCondition.HoverQuery(true, true);
            case "pointer" -> new MediaCondition.PointerQuery(null, false);
            case "any-pointer" -> new MediaCondition.PointerQuery(null, true);
            case "prefers-contrast" -> new MediaCondition.ContrastQuery(null, false);
            case "forced-colors" -> new MediaCondition.ForcedColors(true);
            case "prefers-reduced-transparency" -> new MediaCondition.ReducedTransparency(true);
            case "prefers-reduced-motion" -> new MediaCondition.ReducedMotion(true);
            case "width", "height", "aspect-ratio", "resolution" -> MediaCondition.ALWAYS;
            default -> throw new Unreadable("\"" + name + "\" is not a media feature this toolkit reads alone");
        };
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
            case "aspect-ratio" -> new MediaCondition.AspectRatio(MediaCondition.Comparison.EQUAL, ratio());
            case "min-aspect-ratio" -> new MediaCondition.AspectRatio(MediaCondition.Comparison.AT_LEAST, ratio());
            case "max-aspect-ratio" -> new MediaCondition.AspectRatio(MediaCondition.Comparison.AT_MOST, ratio());
            case "resolution" -> new MediaCondition.Resolution(MediaCondition.Comparison.EQUAL, resolution());
            case "min-resolution" -> new MediaCondition.Resolution(MediaCondition.Comparison.AT_LEAST, resolution());
            case "max-resolution" -> new MediaCondition.Resolution(MediaCondition.Comparison.AT_MOST, resolution());
            case "pointer", "any-pointer" ->
                new MediaCondition.PointerQuery(
                        switch (lower(expectIdent("none, coarse or fine"))) {
                            case "none" -> MediaContext.Pointer.NONE;
                            case "coarse" -> MediaContext.Pointer.COARSE;
                            case "fine" -> MediaContext.Pointer.FINE;
                            default -> throw new Unreadable(name + " is none, coarse or fine");
                        },
                        name.startsWith("any-"));
            case "hover", "any-hover" ->
                new MediaCondition.HoverQuery(
                        switch (lower(expectIdent("hover or none"))) {
                            case "hover" -> true;
                            case "none" -> false;
                            default -> throw new Unreadable(name + " is hover or none");
                        },
                        name.startsWith("any-"));
            case "prefers-contrast" ->
                switch (lower(expectIdent("no-preference, more, less or custom"))) {
                    case "no-preference" ->
                        new MediaCondition.ContrastQuery(MediaContext.Contrast.NO_PREFERENCE, false);
                    case "more" -> new MediaCondition.ContrastQuery(MediaContext.Contrast.MORE, false);
                    case "less" -> new MediaCondition.ContrastQuery(MediaContext.Contrast.LESS, false);
                    case "custom" -> new MediaCondition.ContrastQuery(null, true);
                    default -> throw new Unreadable("prefers-contrast is no-preference, more, less or custom");
                };
            case "forced-colors" ->
                switch (lower(expectIdent("none or active"))) {
                    case "none" -> new MediaCondition.ForcedColors(false);
                    case "active" -> new MediaCondition.ForcedColors(true);
                    default -> throw new Unreadable("forced-colors is none or active");
                };
            case "prefers-reduced-transparency" ->
                switch (lower(expectIdent("reduce or no-preference"))) {
                    case "reduce" -> new MediaCondition.ReducedTransparency(true);
                    case "no-preference" -> new MediaCondition.ReducedTransparency(false);
                    default -> throw new Unreadable("prefers-reduced-transparency is reduce or no-preference");
                };
            case "color-gamut" ->
                switch (lower(expectIdent("srgb, p3 or rec2020"))) {
                    case "srgb" -> new MediaCondition.ColorGamut(true);
                    case "p3", "rec2020" -> new MediaCondition.ColorGamut(false);
                    default -> throw new Unreadable("color-gamut is srgb, p3 or rec2020");
                };
            default ->
                throw new Unreadable("\"" + name + "\" is not a media feature this toolkit has; it has width,"
                        + " height, aspect-ratio, orientation, resolution, pointer, any-pointer, hover, any-hover,"
                        + " prefers-color-scheme, prefers-reduced-motion, prefers-contrast, forced-colors,"
                        + " prefers-reduced-transparency and color-gamut");
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

    /// A ratio: `16/9`, `16 / 9`, or one number, which is that over 1.
    private double ratio() {
        var first = peek();
        if (first == null || !first.is(TokenType.NUMBER) || !(first.numeric() > 0)) {
            throw new Unreadable("expected a ratio, a positive number or two");
        }
        index++;
        var slash = peek();
        if (slash != null && slash.isDelim('/')) {
            index++;
            var second = peek();
            if (second == null || !second.is(TokenType.NUMBER) || !(second.numeric() > 0)) {
                throw new Unreadable("a ratio's second number is positive");
            }
            index++;
            return first.numeric() / second.numeric();
        }
        return first.numeric();
    }

    /// A resolution, as device pixels per logical pixel: `2dppx`, `2x`,
    /// `192dpi`, `75.6dpcm`.
    private double resolution() {
        var token = peek();
        if (token == null || !token.is(TokenType.DIMENSION)) {
            throw new Unreadable("expected a resolution in dppx, x, dpi or dpcm");
        }
        index++;
        return switch (token.unit().toLowerCase(Locale.ROOT)) {
            case "dppx", "x" -> token.numeric();
            case "dpi" -> token.numeric() / 96;
            case "dpcm" -> token.numeric() * 2.54 / 96;
            default -> throw new Unreadable(token.describe() + " is not a resolution");
        };
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

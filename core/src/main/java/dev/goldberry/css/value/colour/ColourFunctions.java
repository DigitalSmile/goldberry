package dev.goldberry.css.value.colour;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;

/// Reads CSS Color 4's colour functions: `hsl()`, `hsla()`, `hwb()`, `lab()`,
/// `lch()`, `oklab()`, `oklch()` and `color()`.
///
/// ```java
/// Integer red = ColourFunctions.parse(tokens); // oklch(62.8% 0.2577 29.23)
/// ```
///
/// Each takes three components and an optional alpha after a `/`. `hsl()` and
/// `hsla()` also take the legacy form with commas. A component may be `none`,
/// which is zero. A percentage means what CSS says it means for that component,
/// and a hue is a number of degrees or an angle in `deg`, `rad`, `grad` or `turn`.
///
/// `rgb()` is not here: [dev.goldberry.css.value.CssColor] has always read it.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#colour).
public final class ColourFunctions {

    private ColourFunctions() {}

    /// Whether `name`, lowercased, is a function this class reads.
    public static boolean reads(String name) {
        return switch (name) {
            case "hsl", "hsla", "hwb", "lab", "lch", "oklab", "oklch", "color" -> true;
            default -> false;
        };
    }

    /// The colour these tokens write, or null if they are not one.
    ///
    /// @param tokens a function token, its arguments and its `)`, with no
    ///               whitespace and nothing after
    public static @Nullable Integer parse(List<Token> tokens) {
        if (tokens.size() < 2
                || !tokens.getFirst().is(TokenType.FUNCTION)
                || !tokens.getLast().is(TokenType.CLOSE_PAREN)) {
            return null;
        }
        var name = tokens.getFirst().text().toLowerCase(Locale.ROOT);
        var inside = tokens.subList(1, tokens.size() - 1);
        if (name.equals("color")) {
            return color(inside);
        }
        var arguments = Arguments.of(inside, name.startsWith("hsl"));
        if (arguments == null || arguments.components().size() != 3) {
            return null;
        }
        var c = arguments.components();
        var alpha = arguments.alpha();
        return switch (name) {
            case "hsl", "hsla" -> {
                var h = hue(c.get(0));
                var s = fraction(c.get(1), 100);
                var l = fraction(c.get(2), 100);
                yield h == null || s == null || l == null || alpha == null ? null : ColourSpaces.hsl(h, s, l, alpha);
            }
            case "hwb" -> {
                var h = hue(c.get(0));
                var w = fraction(c.get(1), 100);
                var k = fraction(c.get(2), 100);
                yield h == null || w == null || k == null || alpha == null ? null : ColourSpaces.hwb(h, w, k, alpha);
            }
            case "lab" -> {
                var l = scaled(c.get(0), 100);
                var a = scaled(c.get(1), 125);
                var b = scaled(c.get(2), 125);
                yield l == null || a == null || b == null || alpha == null ? null : ColourSpaces.lab(l, a, b, alpha);
            }
            case "lch" -> {
                var l = scaled(c.get(0), 100);
                var ch = scaled(c.get(1), 150);
                var h = hue(c.get(2));
                yield l == null || ch == null || h == null || alpha == null ? null : ColourSpaces.lch(l, ch, h, alpha);
            }
            case "oklab" -> {
                var l = scaled(c.get(0), 1);
                var a = scaled(c.get(1), 0.4);
                var b = scaled(c.get(2), 0.4);
                yield l == null || a == null || b == null || alpha == null ? null : ColourSpaces.oklab(l, a, b, alpha);
            }
            case "oklch" -> {
                var l = scaled(c.get(0), 1);
                var ch = scaled(c.get(1), 0.4);
                var h = hue(c.get(2));
                yield l == null || ch == null || h == null || alpha == null
                        ? null
                        : ColourSpaces.oklch(l, ch, h, alpha);
            }
            default -> null;
        };
    }

    /// `color(<space> c1 c2 c3 [/ alpha])`.
    private static @Nullable Integer color(List<Token> inside) {
        if (inside.isEmpty() || !inside.getFirst().is(TokenType.IDENT)) {
            return null;
        }
        var space = inside.getFirst().text().toLowerCase(Locale.ROOT);
        var arguments = Arguments.of(inside.subList(1, inside.size()), false);
        if (arguments == null || arguments.components().size() != 3 || arguments.alpha() == null) {
            return null;
        }
        var values = new double[3];
        for (var i = 0; i < 3; i++) {
            var value = scaled(arguments.components().get(i), 1);
            if (value == null) {
                return null;
            }
            values[i] = value;
        }
        double alpha = arguments.alpha();
        return switch (space) {
            case "srgb" -> ColourSpaces.srgb(values[0], values[1], values[2], alpha);
            case "srgb-linear" -> ColourSpaces.linearSrgb(values[0], values[1], values[2], alpha);
            case "display-p3" -> ColourSpaces.displayP3(values[0], values[1], values[2], alpha);
            case "xyz", "xyz-d65" -> ColourSpaces.xyzD65(values[0], values[1], values[2], alpha);
            case "xyz-d50" -> ColourSpaces.xyzD50(values[0], values[1], values[2], alpha);
            default -> null;
        };
    }

    /// A hue: a number of degrees, an angle, or `none`.
    private static @Nullable Double hue(Token token) {
        if (token.isIdent("none")) {
            return 0.0;
        }
        if (token.is(TokenType.NUMBER)) {
            return token.numeric();
        }
        if (!token.is(TokenType.DIMENSION)) {
            return null;
        }
        return switch (token.unit().toLowerCase(Locale.ROOT)) {
            case "deg" -> token.numeric();
            case "rad" -> Math.toDegrees(token.numeric());
            case "grad" -> token.numeric() * 0.9;
            case "turn" -> token.numeric() * 360;
            default -> null;
        };
    }

    /// A component where a percentage is of `hundredPercent` and a number is as
    /// written: `lab()`'s lightness, `oklch()`'s chroma.
    private static @Nullable Double scaled(Token token, double hundredPercent) {
        if (token.isIdent("none")) {
            return 0.0;
        }
        if (token.is(TokenType.NUMBER)) {
            return token.numeric();
        }
        if (token.is(TokenType.PERCENTAGE)) {
            return token.numeric() / 100 * hundredPercent;
        }
        return null;
    }

    /// A component in `0..1` written as a percentage, or as a number of
    /// `numberScale` for 100%: `hsl()`'s saturation is `50%` or `50`.
    private static @Nullable Double fraction(Token token, double numberScale) {
        if (token.isIdent("none")) {
            return 0.0;
        }
        if (token.is(TokenType.PERCENTAGE)) {
            return token.numeric() / 100;
        }
        if (token.is(TokenType.NUMBER)) {
            return token.numeric() / numberScale;
        }
        return null;
    }

    /// A function's components and its alpha, which defaults to 1.
    ///
    /// @param components the components before any `/`
    /// @param alpha      the alpha in `0..1`, or null when it was written and is
    ///                   not one
    private record Arguments(
            List<Token> components, @Nullable Double alpha) {

        /// The modern form, `a b c / alpha`, or when `legacy` is allowed the
        /// comma form, `a, b, c, alpha`. The two cannot be mixed.
        static @Nullable Arguments of(List<Token> inside, boolean legacy) {
            var tokens =
                    inside.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
            var commas = tokens.stream().filter(t -> t.is(TokenType.COMMA)).count();
            if (commas > 0) {
                if (!legacy || tokens.stream().anyMatch(t -> t.isDelim('/') || t.isIdent("none"))) {
                    return null;
                }
                var parts = new ArrayList<Token>();
                for (var i = 0; i < tokens.size(); i++) {
                    var token = tokens.get(i);
                    if (i % 2 == 1) {
                        if (!token.is(TokenType.COMMA)) {
                            return null;
                        }
                    } else {
                        parts.add(token);
                    }
                }
                if (tokens.getLast().is(TokenType.COMMA) || parts.size() < 3 || parts.size() > 4) {
                    return null;
                }
                return new Arguments(parts.subList(0, 3), parts.size() == 4 ? alpha(parts.get(3)) : Double.valueOf(1));
            }
            var slash = -1;
            for (var i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).isDelim('/')) {
                    if (slash >= 0) {
                        return null;
                    }
                    slash = i;
                }
            }
            if (slash < 0) {
                return new Arguments(tokens, 1.0);
            }
            if (slash != tokens.size() - 2) {
                return null;
            }
            return new Arguments(tokens.subList(0, slash), alpha(tokens.getLast()));
        }

        private static @Nullable Double alpha(Token token) {
            if (token.isIdent("none")) {
                return 0.0;
            }
            if (token.is(TokenType.NUMBER)) {
                return Math.max(0, Math.min(1, token.numeric()));
            }
            if (token.is(TokenType.PERCENTAGE)) {
                return Math.max(0, Math.min(1, token.numeric() / 100));
            }
            return null;
        }
    }
}

package dev.goldberry.css.value.colour;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.calc.Maths;

/// The relative colour syntax: a colour function that starts `from` another
/// colour, whose channels it can name.
///
/// ```css
/// --gb-accent-soft: oklch(from var(--gb-accent) calc(l + 0.1) c h);
/// --gb-scrim:       rgb(from var(--gb-surface) r g b / 60%);
/// --gb-tint:        hsl(from var(--gb-accent) h s 92%);
/// ```
///
/// Each function names the channels of its own space: `r g b` for `rgb()`,
/// `h s l` for `hsl()`, `h w b` for `hwb()`, `l a b` for `lab()` and
/// `oklab()`, `l c h` for `lch()` and `oklch()`, `r g b` or `x y z` for
/// `color()`, and `alpha` for all of them. A name is the origin colour's value
/// as a number in the units the function reads plainly (0 to 255 for
/// `rgb()`'s channels, 0 to 100 for `hsl()`'s saturation and lightness, degrees
/// for a hue), so `calc()` can do arithmetic on it.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#colour).
public final class RelativeColour {

    private RelativeColour() {}

    /// Whether `tokens` are a colour function written `from` another colour.
    public static boolean isRelative(List<Token> tokens) {
        if (!ColourTokens.isFunction(tokens)) {
            return false;
        }
        for (var token : ColourTokens.inside(tokens)) {
            if (!token.is(TokenType.WHITESPACE)) {
                return token.isIdent("from");
            }
        }
        return false;
    }

    /// The colour `tokens` write, or null.
    ///
    /// @param tokens  the function, from its name to its `)`
    /// @param colours reads the origin colour, and the function once its
    ///                channels are numbers
    public static @Nullable Integer parse(List<Token> tokens, Function<List<Token>, @Nullable Integer> colours) {
        if (!isRelative(tokens)) {
            return null;
        }
        var function = tokens.getFirst();
        var name = function.text().toLowerCase(Locale.ROOT);
        var words = ColourTokens.words(ColourTokens.inside(tokens));
        // `from`, the origin colour, and for `color()` the space.
        if (words.size() < 2) {
            return null;
        }
        var origin = colours.apply(words.get(1));
        if (origin == null) {
            return null;
        }
        var rest = words.subList(2, words.size());
        String spaceName = null;
        if (name.equals("color")) {
            if (rest.isEmpty()
                    || rest.getFirst().size() != 1
                    || !rest.getFirst().getFirst().is(TokenType.IDENT)) {
                return null;
            }
            spaceName = rest.getFirst().getFirst().text().toLowerCase(Locale.ROOT);
            rest = rest.subList(1, rest.size());
        }
        var channels = channels(name, spaceName, origin);
        if (channels == null) {
            return null;
        }
        // The function again, each channel name and each sum a plain number.
        var rebuilt = new ArrayList<Token>();
        rebuilt.add(function);
        if (spaceName != null) {
            rebuilt.add(words.get(2).getFirst());
            rebuilt.add(space(function));
        }
        for (var word : rest) {
            var value = value(word, channels, function);
            if (value == null) {
                return null;
            }
            rebuilt.add(value);
            rebuilt.add(space(function));
        }
        rebuilt.add(new Token(TokenType.CLOSE_PAREN, ")", 0, "", function.line(), function.column()));
        return colours.apply(rebuilt);
    }

    /// The origin's channels by name, in the units `name` reads them in.
    private static @Nullable Map<String, Double> channels(String name, @Nullable String spaceName, int origin) {
        var named = new HashMap<String, Double>();
        named.put("alpha", (origin >>> 24) / 255.0);
        switch (name) {
            case "rgb", "rgba" -> put(named, ColourSpace.SRGB.coordinates(origin), 255, 255, 255, "r", "g", "b");
            case "hsl", "hsla" -> put(named, ColourSpace.HSL.coordinates(origin), 1, 100, 100, "h", "s", "l");
            case "hwb" -> put(named, ColourSpace.HWB.coordinates(origin), 1, 100, 100, "h", "w", "b");
            case "lab" -> put(named, ColourSpace.LAB.coordinates(origin), 1, 1, 1, "l", "a", "b");
            case "lch" -> put(named, ColourSpace.LCH.coordinates(origin), 1, 1, 1, "l", "c", "h");
            case "oklab" -> put(named, ColourSpace.OKLAB.coordinates(origin), 1, 1, 1, "l", "a", "b");
            case "oklch" -> put(named, ColourSpace.OKLCH.coordinates(origin), 1, 1, 1, "l", "c", "h");
            case "color" -> {
                var space = spaceName == null ? null : ColourSpace.parse(spaceName);
                if (space == null || space.hue() >= 0 || space == ColourSpace.LAB || space == ColourSpace.OKLAB) {
                    return null;
                }
                var xyz = space == ColourSpace.XYZ_D50 || space == ColourSpace.XYZ_D65;
                put(named, space.coordinates(origin), 1, 1, 1, xyz ? "x" : "r", xyz ? "y" : "g", xyz ? "z" : "b");
            }
            default -> {
                return null;
            }
        }
        return named;
    }

    private static void put(
            Map<String, Double> named,
            double[] coordinates,
            double scale0,
            double scale1,
            double scale2,
            String name0,
            String name1,
            String name2) {
        named.put(name0, coordinates[0] * scale0);
        named.put(name1, coordinates[1] * scale1);
        named.put(name2, coordinates[2] * scale2);
    }

    /// One word of the function: a channel's name as its number, a math
    /// function over names folded to one, a `/`, or anything else as written.
    private static @Nullable Token value(List<Token> word, Map<String, Double> channels, Token at) {
        if (word.size() == 1) {
            var token = word.getFirst();
            if (token.is(TokenType.IDENT)) {
                var channel = channels.get(token.text().toLowerCase(Locale.ROOT));
                if (channel != null) {
                    return number(channel, at);
                }
            }
            return token;
        }
        if (!Maths.isMath(word)) {
            return null;
        }
        var substituted = new ArrayList<Token>(word.size());
        for (var token : word) {
            var channel = token.is(TokenType.IDENT) ? channels.get(token.text().toLowerCase(Locale.ROOT)) : null;
            substituted.add(channel == null ? token : number(channel, token));
        }
        var folded = Maths.fold(substituted, CssLength.Context.DEFAULT);
        if (folded == null) {
            return null;
        }
        return switch (folded.kind()) {
            case NUMBER -> number(folded.value(), at);
            case ANGLE ->
                new Token(TokenType.DIMENSION, folded.value() + "deg", folded.value(), "deg", at.line(), at.column());
            case LENGTH ->
                folded.isPercentOnly() || (folded.value() == 0 && folded.percent() == 0)
                        ? new Token(
                                TokenType.PERCENTAGE,
                                folded.percent() + "%",
                                folded.percent(),
                                "",
                                at.line(),
                                at.column())
                        : null;
            case TIME -> null;
        };
    }

    private static Token number(double value, Token at) {
        return new Token(TokenType.NUMBER, Double.toString(value), value, "", at.line(), at.column());
    }

    private static Token space(Token at) {
        return new Token(TokenType.WHITESPACE, " ", 0, "", at.line(), at.column());
    }
}

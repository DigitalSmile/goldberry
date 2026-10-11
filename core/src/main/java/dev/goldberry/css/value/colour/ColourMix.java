package dev.goldberry.css.value.colour;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.colour.ColourMixing.HueMethod;

/// `color-mix()`: two colours mixed in a colour space, as much of each as its
/// percentage says.
///
/// ```css
/// --gb-accent-hover: color-mix(in oklch, var(--gb-accent), black 12%);
/// --gb-scrim:        color-mix(in srgb, var(--gb-surface) 60%, transparent);
/// ```
///
/// The space is named after `in`, and a space with a hue may say which way
/// round it goes; with no `in` it is OKLab, as CSS Color 5 says. A colour
/// with no percentage takes what the other leaves of 100%, and both without
/// are half and half. Two percentages that add up to more than 100% are scaled
/// down to it; to less, and the mix is that much transparent.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#colour).
public final class ColourMix {

    private ColourMix() {}

    /// The colour `tokens` write, or null.
    ///
    /// @param tokens  `color-mix(` to its `)`
    /// @param colours reads one colour argument
    public static @Nullable Integer parse(List<Token> tokens, Function<List<Token>, @Nullable Integer> colours) {
        if (!ColourTokens.isFunction(tokens) || !tokens.getFirst().text().equalsIgnoreCase("color-mix")) {
            return null;
        }
        var arguments = ColourTokens.commas(ColourTokens.inside(tokens));
        var space = ColourSpace.OKLAB;
        var hue = HueMethod.SHORTER;
        var first = 0;
        if (arguments.size() == 3) {
            var method = ColourTokens.words(arguments.getFirst());
            if (method.size() < 2 || method.size() > 4 || !isWord(method.getFirst(), "in")) {
                return null;
            }
            var named = method.get(1).size() == 1 && method.get(1).getFirst().is(TokenType.IDENT)
                    ? ColourSpace.parse(method.get(1).getFirst().text())
                    : null;
            if (named == null) {
                return null;
            }
            space = named;
            if (method.size() == 4) {
                var way = method.get(2).size() == 1
                        ? HueMethod.parse(method.get(2).getFirst().text())
                        : null;
                if (way == null || !isWord(method.get(3), "hue") || space.hue() < 0) {
                    return null;
                }
                hue = way;
            } else if (method.size() == 3) {
                return null;
            }
            first = 1;
        } else if (arguments.size() != 2) {
            return null;
        }
        var a = component(arguments.get(first), colours);
        var b = component(arguments.get(first + 1), colours);
        if (a == null || b == null) {
            return null;
        }
        double p1;
        double p2;
        if (Double.isNaN(a.percent()) && Double.isNaN(b.percent())) {
            p1 = 50;
            p2 = 50;
        } else if (Double.isNaN(a.percent())) {
            p2 = b.percent();
            p1 = 100 - p2;
        } else if (Double.isNaN(b.percent())) {
            p1 = a.percent();
            p2 = 100 - p1;
        } else {
            p1 = a.percent();
            p2 = b.percent();
        }
        var sum = p1 + p2;
        if (!(sum > 0) || p1 < 0 || p2 < 0) {
            return null;
        }
        var mixed = ColourMixing.mix(a.argb(), b.argb(), p2 / sum, space, hue);
        if (sum >= 100) {
            return mixed;
        }
        var alpha = (int) Math.round((mixed >>> 24) * sum / 100);
        return alpha << 24 | (mixed & 0x00FFFFFF);
    }

    /// One colour and its percentage, NaN when it has none.
    private record Component(int argb, double percent) {}

    private static @Nullable Component component(
            List<Token> argument, Function<List<Token>, @Nullable Integer> colours) {
        var words = ColourTokens.words(argument);
        if (words.isEmpty() || words.size() > 2) {
            return null;
        }
        var percent = Double.NaN;
        List<Token> colour = words.getFirst();
        if (words.size() == 2) {
            // The percentage may come before the colour or after it.
            var before = percentage(words.getFirst());
            var after = percentage(words.getLast());
            if (before != null) {
                percent = before;
                colour = words.getLast();
            } else if (after != null) {
                percent = after;
            } else {
                return null;
            }
            if (percent < 0 || percent > 100) {
                return null;
            }
        }
        var argb = colours.apply(colour);
        return argb == null ? null : new Component(argb, percent);
    }

    private static @Nullable Double percentage(List<Token> word) {
        return word.size() == 1 && word.getFirst().is(TokenType.PERCENTAGE)
                ? word.getFirst().numeric()
                : null;
    }

    private static boolean isWord(List<Token> word, String text) {
        return word.size() == 1
                && word.getFirst().is(TokenType.IDENT)
                && word.getFirst().text().toLowerCase(Locale.ROOT).equals(text);
    }
}

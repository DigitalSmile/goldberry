package dev.goldberry.css.compositing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.DoubleFunction;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;

/// Reads a `filter` value: `none`, or a list of filter functions separated by
/// spaces. Empty for anything else, `url(#filter)` included, which names an
/// SVG element no box here has.
public final class FilterParser {

    private FilterParser() {}

    /// The filters `value` lists, empty for `none`, or empty `Optional` when it
    /// is not a filter list.
    ///
    /// @param currentColour the element's `color`, which a `drop-shadow()` with
    ///                      no colour of its own is drawn in
    public static Optional<List<FilterFunction>> parse(
            List<Token> value, CssLength.Context context, int currentColour) {
        var words = words(value);
        if (words.size() == 1
                && words.getFirst().size() == 1
                && words.getFirst().getFirst().isIdent("none")) {
            return Optional.of(List.of());
        }
        if (words.isEmpty()) {
            return Optional.empty();
        }
        var filters = new ArrayList<FilterFunction>();
        for (var word : words) {
            var head = word.getFirst();
            if (!head.is(TokenType.FUNCTION)) {
                return Optional.empty();
            }
            var filter = function(head.text().toLowerCase(Locale.ROOT), inner(word), context, currentColour);
            if (filter == null) {
                return Optional.empty();
            }
            filters.add(filter);
        }
        return Optional.of(List.copyOf(filters));
    }

    private static @Nullable FilterFunction function(
            String name, List<Token> args, CssLength.Context context, int currentColour) {
        var tokens = args.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        try {
            return switch (name) {
                case "blur" -> {
                    if (tokens.isEmpty()) {
                        yield new FilterFunction.Blur(0);
                    }
                    var radius = pixels(args, context);
                    yield radius == null ? null : new FilterFunction.Blur(radius);
                }
                case "brightness" -> amount(tokens, 1, FilterFunction.Brightness::new);
                case "contrast" -> amount(tokens, 1, FilterFunction.Contrast::new);
                case "grayscale" -> amount(tokens, 1, FilterFunction.Grayscale::new);
                case "invert" -> amount(tokens, 1, FilterFunction.Invert::new);
                case "opacity" -> amount(tokens, 1, FilterFunction.Opacity::new);
                case "saturate" -> amount(tokens, 1, FilterFunction.Saturate::new);
                case "sepia" -> amount(tokens, 1, FilterFunction.Sepia::new);
                case "hue-rotate" -> {
                    if (tokens.isEmpty()) {
                        yield new FilterFunction.HueRotate(0);
                    }
                    var degrees = tokens.size() == 1 ? angle(tokens.getFirst()) : null;
                    yield degrees == null ? null : new FilterFunction.HueRotate(degrees);
                }
                case "drop-shadow" -> dropShadow(args, context, currentColour);
                default -> null;
            };
        } catch (IllegalArgumentException e) {
            // A negative amount or radius: the function is out of range, and
            // the declaration is dropped with it.
            return null;
        }
    }

    /// `name(amount)`: a number, a percentage, or nothing for `fallback`.
    private static @Nullable FilterFunction amount(
            List<Token> tokens, double fallback, DoubleFunction<FilterFunction> make) {
        if (tokens.isEmpty()) {
            return make.apply(fallback);
        }
        if (tokens.size() != 1) {
            return null;
        }
        var token = tokens.getFirst();
        if (token.is(TokenType.NUMBER)) {
            return make.apply(token.numeric());
        }
        if (token.is(TokenType.PERCENTAGE)) {
            return make.apply(token.numeric() / 100);
        }
        return null;
    }

    /// An angle in degrees, or a bare zero.
    private static @Nullable Double angle(Token token) {
        if (token.is(TokenType.NUMBER) && token.numeric() == 0) {
            return 0.0;
        }
        return token.is(TokenType.DIMENSION) ? CssLength.degrees(token.numeric(), token.unit()) : null;
    }

    /// `drop-shadow(<colour>? <dx> <dy> <blur>?)`, the colour first or last.
    private static @Nullable FilterFunction dropShadow(List<Token> args, CssLength.Context context, int currentColour) {
        var words = words(args);
        if (words.isEmpty()) {
            return null;
        }
        Integer colour;
        if (pixels(words.getFirst(), context) == null) {
            colour = CssColor.parse(words.getFirst());
            words = words.subList(1, words.size());
        } else if (pixels(words.getLast(), context) == null) {
            colour = CssColor.parse(words.getLast());
            words = words.subList(0, words.size() - 1);
        } else {
            colour = currentColour;
        }
        if (colour == null || words.size() < 2 || words.size() > 3) {
            return null;
        }
        var lengths = new ArrayList<Double>();
        for (var word : words) {
            var length = pixels(word, context);
            if (length == null) {
                return null;
            }
            lengths.add(length);
        }
        return new FilterFunction.DropShadow(
                lengths.get(0), lengths.get(1), lengths.size() == 3 ? lengths.get(2) : 0, colour);
    }

    /// A length in logical pixels: no percentage, no `auto`.
    private static @Nullable Double pixels(List<Token> word, CssLength.Context context) {
        var length = CssLength.parse(word, context);
        return length instanceof Length.Points points ? (double) points.value() : null;
    }

    private static List<Token> inner(List<Token> call) {
        var last = call.getLast().is(TokenType.CLOSE_PAREN) ? call.size() - 1 : call.size();
        return call.subList(1, last);
    }

    /// `value` split at the spaces outside a function.
    private static List<List<Token>> words(List<Token> value) {
        var words = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : value) {
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && token.is(TokenType.WHITESPACE)) {
                if (!current.isEmpty()) {
                    words.add(List.copyOf(current));
                    current.clear();
                }
            } else {
                current.add(token);
            }
        }
        if (!current.isEmpty()) {
            words.add(List.copyOf(current));
        }
        return words;
    }
}

package dev.goldberry.css.value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.motion.Easing;
import dev.goldberry.motion.easing.CubicBezier;
import dev.goldberry.motion.easing.PiecewiseLinear;
import dev.goldberry.motion.easing.Steps;

/// Reads a CSS timing function: a keyword, `cubic-bezier()`, `steps()` or
/// `linear()`.
///
/// ```java
/// Easing curve = CssEasing.parse(part); // null when the part is not one
/// ```
///
/// The design system's three names are always read. The rest, CSS's keywords
/// and its three functions, are read here and kept out of the toolkit's own
/// sheets by the strict parse, which refuses them ([#isSystemCurve]).
///
/// Read more:
/// [Transition and animation](https://goldberry.dev/docs/guide/styling.html#transition-and-animation).
public final class CssEasing {

    private CssEasing() {}

    /// The curve one part of a value names, or null.
    ///
    /// @param part one space-separated part: an identifier, or a function token
    ///             through its `)`; whitespace inside is allowed
    public static @Nullable Easing parse(List<Token> part) {
        var tokens = part.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.isEmpty()) {
            return null;
        }
        var first = tokens.getFirst();
        if (tokens.size() == 1 && first.is(TokenType.IDENT)) {
            return Easing.parse(first.text());
        }
        if (!first.is(TokenType.FUNCTION) || !tokens.getLast().is(TokenType.CLOSE_PAREN)) {
            return null;
        }
        var arguments = arguments(tokens.subList(1, tokens.size() - 1));
        if (arguments == null) {
            return null;
        }
        try {
            return switch (first.text().toLowerCase(Locale.ROOT)) {
                case "cubic-bezier" -> cubicBezier(arguments);
                case "steps" -> steps(arguments);
                case "linear" -> linear(arguments);
                default -> null;
            };
        } catch (IllegalArgumentException e) {
            // A control point's x outside 0..1, too few steps: CSS calls these
            // invalid, which is null here, and the record says why in its
            // message for a reader who constructs one directly.
            return null;
        }
    }

    /// Whether `part` names one of the design system's three curves:
    /// `ease-enter`, `ease-exit` or `linear`. What the toolkit's own sheets may
    /// write.
    public static boolean isSystemCurve(List<Token> part) {
        var tokens = part.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1 || !tokens.getFirst().is(TokenType.IDENT)) {
            return false;
        }
        for (var curve : Easing.BUILT_IN) {
            if (curve.cssName().equalsIgnoreCase(tokens.getFirst().text())) {
                return true;
            }
        }
        return false;
    }

    /// Whether `part` is a timing function of any kind, readable or not: a
    /// keyword `Easing` knows, or one of the three functions. What the strict
    /// parse looks for, and what a shorthand skips with a warning when it cannot
    /// read it.
    public static boolean looksLikeEasing(List<Token> part) {
        var tokens = part.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.isEmpty()) {
            return false;
        }
        var first = tokens.getFirst();
        if (first.is(TokenType.FUNCTION)) {
            var name = first.text().toLowerCase(Locale.ROOT);
            return name.equals("cubic-bezier") || name.equals("steps") || name.equals("linear");
        }
        if (tokens.size() != 1 || !first.is(TokenType.IDENT)) {
            return false;
        }
        var word = first.text().toLowerCase(Locale.ROOT);
        return Easing.parse(word) != null || word.startsWith("ease") || word.startsWith("step-");
    }

    private static Easing cubicBezier(List<List<Token>> arguments) {
        if (arguments.size() != 4) {
            throw new IllegalArgumentException("cubic-bezier() takes four numbers");
        }
        var values = new double[4];
        for (var i = 0; i < 4; i++) {
            values[i] = number(arguments.get(i));
        }
        return new CubicBezier(values[0], values[1], values[2], values[3]);
    }

    private static Easing steps(List<List<Token>> arguments) {
        if (arguments.isEmpty() || arguments.size() > 2) {
            throw new IllegalArgumentException("steps() takes a count and a position");
        }
        var count = arguments.getFirst();
        if (count.size() != 1
                || !count.getFirst().is(TokenType.NUMBER)
                || count.getFirst().numeric() % 1 != 0) {
            throw new IllegalArgumentException("steps() counts in whole steps");
        }
        var position = Steps.Position.JUMP_END;
        if (arguments.size() == 2) {
            var word = arguments.get(1);
            if (word.size() != 1 || !word.getFirst().is(TokenType.IDENT)) {
                throw new IllegalArgumentException("a step position is a keyword");
            }
            position = switch (word.getFirst().text().toLowerCase(Locale.ROOT)) {
                case "jump-start", "start" -> Steps.Position.JUMP_START;
                case "jump-end", "end" -> Steps.Position.JUMP_END;
                case "jump-none" -> Steps.Position.JUMP_NONE;
                case "jump-both" -> Steps.Position.JUMP_BOTH;
                default ->
                    throw new IllegalArgumentException(
                            "no step position " + word.getFirst().text());
            };
        }
        return new Steps((int) count.getFirst().numeric(), position);
    }

    /// `linear(0, 0.25 75%, 1)`: each stop an output and up to two inputs, the
    /// missing inputs filled in as CSS says. The first defaults to 0% and the
    /// last to 100%; an input below one before it is raised to it; a run of
    /// missing inputs is spread evenly between the two either side.
    private static Easing linear(List<List<Token>> arguments) {
        var outputs = new ArrayList<Double>();
        var inputs = new ArrayList<@Nullable Double>();
        for (var stop : arguments) {
            Double output = null;
            var percentages = new ArrayList<Double>();
            for (var token : stop) {
                if (token.is(TokenType.NUMBER) && output == null) {
                    output = token.numeric();
                } else if (token.is(TokenType.PERCENTAGE) && percentages.size() < 2) {
                    percentages.add(token.numeric() / 100);
                } else {
                    throw new IllegalArgumentException("a linear() stop is a number and up to two percentages");
                }
            }
            if (output == null) {
                throw new IllegalArgumentException("a linear() stop needs its output");
            }
            if (percentages.isEmpty()) {
                outputs.add(output);
                inputs.add(null);
            }
            for (var percentage : percentages) {
                outputs.add(output);
                inputs.add(percentage);
            }
        }
        if (outputs.size() < 2) {
            throw new IllegalArgumentException("linear() needs two points");
        }
        // NaN for a missing input from here on: primitives, so nothing unboxes
        // a stop that has not been filled in yet.
        var at = new double[inputs.size()];
        for (var i = 0; i < at.length; i++) {
            var input = inputs.get(i);
            at[i] = input == null ? Double.NaN : input;
        }
        if (Double.isNaN(at[0])) {
            at[0] = 0;
        }
        if (Double.isNaN(at[at.length - 1])) {
            at[at.length - 1] = 1;
        }
        var largest = Double.NEGATIVE_INFINITY;
        for (var i = 0; i < at.length; i++) {
            if (!Double.isNaN(at[i])) {
                largest = Math.max(largest, at[i]);
                at[i] = largest;
            }
        }
        for (var i = 1; i < at.length; i++) {
            if (!Double.isNaN(at[i])) {
                continue;
            }
            var from = i - 1;
            var to = i;
            while (Double.isNaN(at[to])) {
                to++;
            }
            for (var k = i; k < to; k++) {
                at[k] = at[from] + (at[to] - at[from]) * (k - from) / (to - from);
            }
        }
        var points = new ArrayList<PiecewiseLinear.Point>(outputs.size());
        for (var i = 0; i < outputs.size(); i++) {
            points.add(new PiecewiseLinear.Point(outputs.get(i), at[i]));
        }
        return new PiecewiseLinear(points);
    }

    private static double number(List<Token> argument) {
        if (argument.size() != 1 || !argument.getFirst().is(TokenType.NUMBER)) {
            throw new IllegalArgumentException("expected a number");
        }
        return argument.getFirst().numeric();
    }

    /// A function's arguments split on commas, whitespace dropped, or null when
    /// an argument is empty.
    private static @Nullable List<List<Token>> arguments(List<Token> inside) {
        var arguments = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        for (var token : inside) {
            if (token.is(TokenType.COMMA)) {
                if (current.isEmpty()) {
                    return null;
                }
                arguments.add(List.copyOf(current));
                current.clear();
            } else if (!token.is(TokenType.WHITESPACE)) {
                current.add(token);
            }
        }
        if (current.isEmpty()) {
            return arguments.isEmpty() ? List.of() : null;
        }
        arguments.add(List.copyOf(current));
        return arguments;
    }
}

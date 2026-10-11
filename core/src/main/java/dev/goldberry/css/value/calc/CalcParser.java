package dev.goldberry.css.value.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;

/// Reads a math function into a [CalcNode] tree.
///
/// ```java
/// CalcNode tree = CalcParser.parse(tokens); // clamp(4px, 1em + 2vw, 24px)
/// ```
///
/// The grammar is CSS Values 4's: a sum of products of values, where a value is
/// a number, a percentage, a dimension, `e` or `pi`, a parenthesised sum, or
/// another math function. `+` and `-` must have whitespace on both sides, which
/// is what tells `1px - 2px` from `1px -2px`, two values with nothing between
/// them. `infinity` and `NaN` are refused: neither is a value a box can have.
///
/// Whether the units add up is not the parser's question. `calc(1px + 2)` reads
/// as a tree and fails when [Folding] is asked to add a length to a number.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
public final class CalcParser {

    private final List<Token> tokens;
    private int index;

    private CalcParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /// Whether `name`, lowercased, is a math function this reads.
    public static boolean reads(String name) {
        return switch (name) {
            case "calc", "min", "max", "clamp", "round", "mod", "rem", "abs", "sign" -> true;
            default -> false;
        };
    }

    /// Whether `token` starts a math function.
    public static boolean starts(Token token) {
        return token.is(TokenType.FUNCTION) && reads(token.text().toLowerCase(Locale.ROOT));
    }

    /// The tree `value` writes, or null when it is not exactly one math
    /// function, whitespace around it allowed.
    public static @Nullable CalcNode parse(List<Token> value) {
        var from = 0;
        var to = value.size();
        while (from < to && value.get(from).is(TokenType.WHITESPACE)) {
            from++;
        }
        while (to > from && value.get(to - 1).is(TokenType.WHITESPACE)) {
            to--;
        }
        if (from == to || !starts(value.get(from))) {
            return null;
        }
        var parser = new CalcParser(value.subList(from, to));
        var node = parser.function();
        return node != null && parser.index == parser.tokens.size() ? node : null;
    }

    /// A math function, its name already checked; the parser stands on it.
    private @Nullable CalcNode function() {
        var name = tokens.get(index).text().toLowerCase(Locale.ROOT);
        index++;
        var arguments = arguments();
        if (arguments == null) {
            return null;
        }
        return switch (name) {
            case "clamp" -> clamp(arguments);
            case "round" -> round(arguments);
            default -> {
                var nodes = expressions(arguments);
                yield nodes == null ? null : plain(name, nodes);
            }
        };
    }

    /// A function whose arguments are all expressions.
    private static @Nullable CalcNode plain(String name, List<CalcNode> arguments) {
        return switch (name) {
            case "calc" -> arguments.size() == 1 ? arguments.getFirst() : null;
            case "min" -> new CalcNode.Min(arguments);
            case "max" -> new CalcNode.Max(arguments);
            case "mod" -> arguments.size() == 2 ? new CalcNode.Mod(arguments.get(0), arguments.get(1)) : null;
            case "rem" -> arguments.size() == 2 ? new CalcNode.Rem(arguments.get(0), arguments.get(1)) : null;
            case "abs" -> arguments.size() == 1 ? new CalcNode.Abs(arguments.getFirst()) : null;
            case "sign" -> arguments.size() == 1 ? new CalcNode.Sign(arguments.getFirst()) : null;
            default -> null;
        };
    }

    /// The trees of `arguments`, or null when one of them is a keyword.
    private static @Nullable List<CalcNode> expressions(List<Argument> arguments) {
        var nodes = new ArrayList<CalcNode>(arguments.size());
        for (var argument : arguments) {
            if (!(argument instanceof Expression(var node))) {
                return null;
            }
            nodes.add(node);
        }
        return nodes;
    }

    /// `clamp()`'s three arguments, either bound possibly `none`.
    private static @Nullable CalcNode clamp(List<Argument> arguments) {
        if (arguments.size() != 3
                || !(arguments.get(1) instanceof Expression(var value))
                || arguments.get(0) instanceof Strategy
                || arguments.get(2) instanceof Strategy) {
            return null;
        }
        return new CalcNode.Clamp(bound(arguments.get(0)), value, bound(arguments.get(2)));
    }

    /// A `clamp()` bound, null for `none`.
    private static @Nullable CalcNode bound(Argument argument) {
        return argument instanceof Expression(var node) ? node : null;
    }

    /// `round()`: an optional strategy, then the value and an optional step.
    private static @Nullable CalcNode round(List<Argument> arguments) {
        var strategy = CalcNode.Rounding.NEAREST;
        var rest = arguments;
        if (!rest.isEmpty() && rest.getFirst() instanceof Strategy(var named)) {
            strategy = named;
            rest = rest.subList(1, rest.size());
        }
        var nodes = expressions(rest);
        if (nodes == null || nodes.isEmpty() || nodes.size() > 2) {
            return null;
        }
        return new CalcNode.Round(strategy, nodes.getFirst(), nodes.size() == 2 ? nodes.get(1) : null);
    }

    /// The comma-separated arguments up to the function's `)`, which is
    /// consumed. An argument may be a bare `none` or a rounding keyword, which
    /// only `clamp()` and `round()` accept and the others refuse.
    private @Nullable List<Argument> arguments() {
        var arguments = new ArrayList<Argument>();
        while (true) {
            skipWhitespace();
            var keyword = keywordArgument();
            Argument argument;
            if (keyword != null) {
                argument = keyword;
            } else {
                var node = sum();
                if (node == null) {
                    return null;
                }
                argument = new Expression(node);
            }
            arguments.add(argument);
            skipWhitespace();
            if (at(TokenType.COMMA)) {
                index++;
                continue;
            }
            if (at(TokenType.CLOSE_PAREN)) {
                index++;
                return arguments;
            }
            return null;
        }
    }

    /// `none` or a rounding strategy standing alone as an argument.
    private @Nullable Argument keywordArgument() {
        if (!at(TokenType.IDENT)) {
            return null;
        }
        var next = index + 1;
        while (next < tokens.size() && tokens.get(next).is(TokenType.WHITESPACE)) {
            next++;
        }
        if (next < tokens.size()
                && !tokens.get(next).is(TokenType.COMMA)
                && !tokens.get(next).is(TokenType.CLOSE_PAREN)) {
            return null;
        }
        Argument marker = switch (tokens.get(index).text().toLowerCase(Locale.ROOT)) {
            case "none" -> None.NONE;
            case "nearest" -> new Strategy(CalcNode.Rounding.NEAREST);
            case "up" -> new Strategy(CalcNode.Rounding.UP);
            case "down" -> new Strategy(CalcNode.Rounding.DOWN);
            case "to-zero" -> new Strategy(CalcNode.Rounding.TO_ZERO);
            default -> null;
        };
        if (marker != null) {
            index++;
        }
        return marker;
    }

    /// Products joined by `+` and `-`, each with whitespace on both sides.
    private @Nullable CalcNode sum() {
        var first = product();
        if (first == null) {
            return null;
        }
        var terms = new ArrayList<CalcNode>();
        terms.add(first);
        while (true) {
            var save = index;
            if (!at(TokenType.WHITESPACE)) {
                break;
            }
            skipWhitespace();
            var minus = atDelim('-');
            if (!minus && !atDelim('+')) {
                index = save;
                break;
            }
            index++;
            if (!at(TokenType.WHITESPACE)) {
                return null;
            }
            skipWhitespace();
            var term = product();
            if (term == null) {
                return null;
            }
            terms.add(minus ? new CalcNode.Negate(term) : term);
        }
        return terms.size() == 1 ? first : new CalcNode.Sum(terms);
    }

    /// Values joined by `*` and `/`, whitespace optional.
    private @Nullable CalcNode product() {
        var first = value();
        if (first == null) {
            return null;
        }
        var factors = new ArrayList<CalcNode>();
        factors.add(first);
        while (true) {
            var save = index;
            skipWhitespace();
            var divide = atDelim('/');
            if (!divide && !atDelim('*')) {
                index = save;
                break;
            }
            index++;
            skipWhitespace();
            var factor = value();
            if (factor == null) {
                return null;
            }
            factors.add(divide ? new CalcNode.Invert(factor) : factor);
        }
        return factors.size() == 1 ? first : new CalcNode.Product(factors);
    }

    /// One value: a number, a percentage, a dimension, a constant, a
    /// parenthesised sum or a nested math function.
    private @Nullable CalcNode value() {
        if (index >= tokens.size()) {
            return null;
        }
        var token = tokens.get(index);
        return switch (token.type()) {
            case NUMBER -> {
                index++;
                yield new CalcNode.Plain(token.numeric());
            }
            case PERCENTAGE -> {
                index++;
                yield new CalcNode.Percentage(token.numeric());
            }
            case DIMENSION -> {
                index++;
                yield new CalcNode.Dimension(token.numeric(), token.unit().toLowerCase(Locale.ROOT));
            }
            case IDENT -> constant(token);
            case OPEN_PAREN -> parenthesised();
            case FUNCTION -> reads(token.text().toLowerCase(Locale.ROOT)) ? function() : null;
            default -> null;
        };
    }

    private @Nullable CalcNode constant(Token token) {
        var value = switch (token.text().toLowerCase(Locale.ROOT)) {
            case "e" -> Math.E;
            case "pi" -> Math.PI;
            default -> Double.NaN;
        };
        if (Double.isNaN(value)) {
            return null;
        }
        index++;
        return new CalcNode.Plain(value);
    }

    private @Nullable CalcNode parenthesised() {
        index++;
        skipWhitespace();
        var inner = sum();
        skipWhitespace();
        if (inner == null || !at(TokenType.CLOSE_PAREN)) {
            return null;
        }
        index++;
        return inner;
    }

    private void skipWhitespace() {
        while (at(TokenType.WHITESPACE)) {
            index++;
        }
    }

    private boolean at(TokenType type) {
        return index < tokens.size() && tokens.get(index).is(type);
    }

    private boolean atDelim(char c) {
        return index < tokens.size() && tokens.get(index).isDelim(c);
    }

    /// One argument of a math function: an expression, or one of the two
    /// keywords an argument may be.
    private sealed interface Argument {}

    /// An expression.
    private record Expression(CalcNode node) implements Argument {}

    /// `none`, which only a `clamp()` bound may be.
    private enum None implements Argument {
        NONE
    }

    /// A rounding keyword, which only `round()`'s first argument may be.
    private record Strategy(CalcNode.Rounding rounding) implements Argument {}
}

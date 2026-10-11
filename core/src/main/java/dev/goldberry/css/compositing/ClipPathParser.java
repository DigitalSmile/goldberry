package dev.goldberry.css.compositing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.Corners;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;

/// Reads a `clip-path` value: `none`, a basic shape, a reference box, or a
/// shape and a box in either order.
///
/// Empty for anything else, `url(#mask)` included: a clip drawn by an SVG
/// element names something no box here has.
public final class ClipPathParser {

    private ClipPathParser() {}

    /// The clip `value` names, or empty when it is not one.
    public static Optional<ClipPath> parse(List<Token> value, CssLength.Context context) {
        var parts = words(value);
        if (parts.size() == 1 && isIdent(parts.getFirst(), "none")) {
            return Optional.of(ClipPath.NONE);
        }
        ClipPath.Shape shape = null;
        ReferenceBox box = null;
        for (var part : parts) {
            var first = part.getFirst();
            if (first.is(TokenType.FUNCTION) && shape == null) {
                shape = shape(first.text().toLowerCase(Locale.ROOT), inner(part), context);
                if (shape == null) {
                    return Optional.empty();
                }
            } else if (part.size() == 1 && first.is(TokenType.IDENT) && box == null) {
                box = ReferenceBox.parse(first.text());
                if (box == null) {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        }
        if (shape != null) {
            return Optional.of(new ClipPath.Shaped(shape, box == null ? ReferenceBox.BORDER_BOX : box));
        }
        return box == null ? Optional.empty() : Optional.of(new ClipPath.Boxed(box));
    }

    private static ClipPath.@Nullable Shape shape(String name, List<Token> args, CssLength.Context context) {
        return switch (name) {
            case "inset" -> inset(args, context);
            case "circle" -> circle(args, context);
            case "ellipse" -> ellipse(args, context);
            case "polygon" -> polygon(args, context);
            case "path" -> path(args);
            default -> null;
        };
    }

    /// `inset(1 to 4 lengths [round <border-radius>])`.
    private static ClipPath.@Nullable Shape inset(List<Token> args, CssLength.Context context) {
        var words = words(args);
        var round = -1;
        for (var i = 0; i < words.size(); i++) {
            if (isIdent(words.get(i), "round")) {
                round = i;
                break;
            }
        }
        var sides = new ArrayList<Length>();
        for (var word : round < 0 ? words : words.subList(0, round)) {
            var length = lengthPercentage(word, context);
            if (length == null) {
                return null;
            }
            sides.add(length);
        }
        var corners = Corners.SQUARE;
        if (round >= 0) {
            var radii = new ArrayList<Token>();
            for (var word : words.subList(round + 1, words.size())) {
                if (!radii.isEmpty()) {
                    radii.add(new Token(TokenType.WHITESPACE, " ", 0, "", 0, 0));
                }
                radii.addAll(word);
            }
            var parsed = Corners.parse(radii, context);
            if (radii.isEmpty() || parsed.isEmpty()) {
                return null;
            }
            corners = parsed.get();
        }
        return switch (sides.size()) {
            case 1 -> new ClipPath.Inset(sides.get(0), sides.get(0), sides.get(0), sides.get(0), corners);
            case 2 -> new ClipPath.Inset(sides.get(0), sides.get(1), sides.get(0), sides.get(1), corners);
            case 3 -> new ClipPath.Inset(sides.get(0), sides.get(1), sides.get(2), sides.get(1), corners);
            case 4 -> new ClipPath.Inset(sides.get(0), sides.get(1), sides.get(2), sides.get(3), corners);
            default -> null;
        };
    }

    /// `circle([radius] [at position])`.
    private static ClipPath.@Nullable Shape circle(List<Token> args, CssLength.Context context) {
        var words = words(args);
        var at = indexOfAt(words);
        var radii = at < 0 ? words : words.subList(0, at);
        var position = at < 0 ? ClipPath.Position.CENTER : position(words.subList(at + 1, words.size()), context);
        if (position == null || radii.size() > 1) {
            return null;
        }
        var radius = radii.isEmpty() ? ClipPath.Radius.CLOSEST_SIDE : radius(radii.getFirst(), context);
        return radius == null ? null : new ClipPath.Circle(radius, position);
    }

    /// `ellipse([rx ry] [at position])`.
    private static ClipPath.@Nullable Shape ellipse(List<Token> args, CssLength.Context context) {
        var words = words(args);
        var at = indexOfAt(words);
        var radii = at < 0 ? words : words.subList(0, at);
        var position = at < 0 ? ClipPath.Position.CENTER : position(words.subList(at + 1, words.size()), context);
        if (position == null) {
            return null;
        }
        if (radii.isEmpty()) {
            return new ClipPath.Ellipse(ClipPath.Radius.CLOSEST_SIDE, ClipPath.Radius.CLOSEST_SIDE, position);
        }
        if (radii.size() != 2) {
            return null;
        }
        var rx = radius(radii.get(0), context);
        var ry = radius(radii.get(1), context);
        return rx == null || ry == null ? null : new ClipPath.Ellipse(rx, ry, position);
    }

    /// `polygon([fill-rule,] x y, x y, …)`.
    private static ClipPath.@Nullable Shape polygon(List<Token> args, CssLength.Context context) {
        var items = commas(args);
        var evenOdd = false;
        if (!items.isEmpty()) {
            var rule = fillRule(items.getFirst());
            if (rule != null) {
                evenOdd = rule;
                items = items.subList(1, items.size());
            }
        }
        var points = new ArrayList<ClipPath.Position>();
        for (var item : items) {
            var words = words(item);
            if (words.size() != 2) {
                return null;
            }
            var x = lengthPercentage(words.get(0), context);
            var y = lengthPercentage(words.get(1), context);
            if (x == null || y == null) {
                return null;
            }
            points.add(new ClipPath.Position(x, y));
        }
        return points.size() < 3 ? null : new ClipPath.Polygon(evenOdd, points);
    }

    /// `path([fill-rule,] "data")`.
    private static ClipPath.@Nullable Shape path(List<Token> args) {
        var items = commas(args);
        var evenOdd = false;
        if (items.size() == 2) {
            var rule = fillRule(items.getFirst());
            if (rule == null) {
                return null;
            }
            evenOdd = rule;
            items = items.subList(1, 2);
        }
        if (items.size() != 1) {
            return null;
        }
        var data = meaningful(items.getFirst());
        if (data.size() != 1
                || !data.getFirst().is(TokenType.STRING)
                || data.getFirst().text().isBlank()) {
            return null;
        }
        return new ClipPath.PathData(evenOdd, data.getFirst().text());
    }

    /// `evenodd` as true, `nonzero` as false, anything else as null.
    private static @Nullable Boolean fillRule(List<Token> item) {
        var tokens = meaningful(item);
        if (tokens.size() != 1) {
            return null;
        }
        if (tokens.getFirst().isIdent("evenodd")) {
            return true;
        }
        if (tokens.getFirst().isIdent("nonzero")) {
            return false;
        }
        return null;
    }

    private static ClipPath.@Nullable Radius radius(List<Token> word, CssLength.Context context) {
        if (isIdent(word, "closest-side")) {
            return ClipPath.Radius.CLOSEST_SIDE;
        }
        if (isIdent(word, "farthest-side")) {
            return new ClipPath.Radius.ToSide(true);
        }
        var length = lengthPercentage(word, context);
        if (length == null || negative(length)) {
            return null;
        }
        return new ClipPath.Radius.Of(length);
    }

    /// A position of one or two values: keywords, lengths and percentages.
    private static ClipPath.@Nullable Position position(List<List<Token>> words, CssLength.Context context) {
        if (words.isEmpty() || words.size() > 2) {
            return null;
        }
        if (words.size() == 1) {
            var only = words.getFirst();
            if (isIdent(only, "top") || isIdent(only, "bottom")) {
                var y = keyword(only, false);
                return y == null ? null : new ClipPath.Position(Length.percent(50), y);
            }
            var x = horizontal(only, context);
            return x == null ? null : new ClipPath.Position(x, Length.percent(50));
        }
        var first = words.get(0);
        var second = words.get(1);
        // `top left` reads as `left top`: a vertical keyword first names y.
        if ((isIdent(first, "top") || isIdent(first, "bottom"))
                && second.size() == 1
                && second.getFirst().is(TokenType.IDENT)) {
            var swap = first;
            first = second;
            second = swap;
        }
        var x = horizontal(first, context);
        var y = vertical(second, context);
        return x == null || y == null ? null : new ClipPath.Position(x, y);
    }

    private static @Nullable Length horizontal(List<Token> word, CssLength.Context context) {
        var keyword = keyword(word, true);
        return keyword != null ? keyword : lengthPercentage(word, context);
    }

    private static @Nullable Length vertical(List<Token> word, CssLength.Context context) {
        var keyword = keyword(word, false);
        return keyword != null ? keyword : lengthPercentage(word, context);
    }

    /// `left`, `center` or `right` across, `top`, `center` or `bottom` down,
    /// as the percentages they are.
    private static @Nullable Length keyword(List<Token> word, boolean across) {
        if (word.size() != 1 || !word.getFirst().is(TokenType.IDENT)) {
            return null;
        }
        return switch (word.getFirst().text().toLowerCase(Locale.ROOT)) {
            case "center" -> Length.percent(50);
            case "left" -> across ? Length.percent(0) : null;
            case "right" -> across ? Length.percent(100) : null;
            case "top" -> across ? null : Length.percent(0);
            case "bottom" -> across ? null : Length.percent(100);
            default -> null;
        };
    }

    /// A length or a percentage, never `auto`.
    private static @Nullable Length lengthPercentage(List<Token> word, CssLength.Context context) {
        var length = CssLength.parse(word, context);
        return length instanceof Length.Keyword ? null : length;
    }

    private static boolean negative(Length length) {
        return switch (length) {
            case Length.Points points -> points.value() < 0;
            case Length.Percent percent -> percent.value() < 0;
            case Length.Mixed mixed -> mixed.points() < 0 && mixed.percent() <= 0;
            case Length.Keyword _ -> true;
        };
    }

    private static int indexOfAt(List<List<Token>> words) {
        for (var i = 0; i < words.size(); i++) {
            if (isIdent(words.get(i), "at")) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isIdent(List<Token> word, String name) {
        return word.size() == 1 && word.getFirst().isIdent(name);
    }

    /// The tokens between a function's name and its closing parenthesis.
    private static List<Token> inner(List<Token> call) {
        var last = call.getLast().is(TokenType.CLOSE_PAREN) ? call.size() - 1 : call.size();
        return call.subList(1, last);
    }

    /// `value` split at the spaces outside a function: one entry per word.
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

    /// `value` split at the commas outside a function.
    private static List<List<Token>> commas(List<Token> value) {
        var items = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : value) {
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && token.is(TokenType.COMMA)) {
                items.add(List.copyOf(current));
                current.clear();
            } else {
                current.add(token);
            }
        }
        items.add(List.copyOf(current));
        return items;
    }

    private static List<Token> meaningful(List<Token> value) {
        return value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
    }
}

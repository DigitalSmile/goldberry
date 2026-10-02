package dev.goldberry.css.background;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;

/// Reads the `background` properties: the shorthand, `background-image`
/// and `background-position`.
///
/// Every method answers null for a value it cannot read whole, so the cascade
/// drops the declaration and says so, as it does for every other property.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
public final class BackgroundParser {

    private static final Length CENTRE = Length.percent(50);

    private BackgroundParser() {}

    /// The `background` shorthand: a comma list of layers, each a gradient or
    /// `none`, the last of which may also name the colour.
    ///
    /// A shorthand resets what it does not name: the colour to transparent and
    /// the position to zero. `background: none` is no fill at all.
    public static @Nullable Background shorthand(List<Token> value, CssLength.Context context) {
        var entries = commas(value);
        var layers = new ArrayList<GradientLayer>();
        var colour = CssColor.TRANSPARENT;
        for (var i = 0; i < entries.size(); i++) {
            var last = i == entries.size() - 1;
            var parts = spaces(entries.get(i));
            if (parts.isEmpty()) {
                return null;
            }
            GradientLayer image = null;
            var none = false;
            Integer named = null;
            for (var part : parts) {
                if (part.size() == 1 && part.getFirst().isIdent("none") && image == null && !none) {
                    none = true;
                    continue;
                }
                var gradient = gradient(part, context);
                if (gradient != null && image == null && !none) {
                    image = gradient;
                    continue;
                }
                var asColour = CssColor.parse(part);
                // Only the last layer has a colour, which is CSS's rule.
                if (asColour != null && last && named == null) {
                    named = asColour;
                    continue;
                }
                return null;
            }
            if (image != null) {
                layers.add(image);
            }
            if (named != null) {
                colour = named;
            }
        }
        return Background.of(colour, layers, BackgroundPosition.ZERO);
    }

    /// `background-image`: `none`, or a comma list of gradients, top first.
    public static @Nullable List<GradientLayer> images(List<Token> value, CssLength.Context context) {
        var entries = commas(value);
        if (entries.size() == 1) {
            var only = spaces(entries.getFirst());
            if (only.size() == 1
                    && only.getFirst().size() == 1
                    && only.getFirst().getFirst().isIdent("none")) {
                return List.of();
            }
        }
        var layers = new ArrayList<GradientLayer>();
        for (var entry : entries) {
            var parts = spaces(entry);
            if (parts.size() != 1) {
                return null;
            }
            var gradient = gradient(parts.getFirst(), context);
            if (gradient == null) {
                return null;
            }
            layers.add(gradient);
        }
        return List.copyOf(layers);
    }

    /// `background-position`: one or two components, each a length, a
    /// percentage or a keyword.
    ///
    /// Only a length moves anything — see [BackgroundPosition] for why a
    /// percentage and a keyword resolve to zero. One component is the
    /// horizontal one, or the vertical one when it is `top` or `bottom`.
    public static @Nullable BackgroundPosition position(List<Token> value, CssLength.Context context) {
        var parts = spaces(value);
        if (parts.isEmpty() || parts.size() > 2) {
            return null;
        }
        var first = component(parts.getFirst(), context);
        if (first == null) {
            return null;
        }
        if (parts.size() == 1) {
            return first.vertical()
                    ? new BackgroundPosition(0, first.offset())
                    : new BackgroundPosition(first.offset(), 0);
        }
        var second = component(parts.get(1), context);
        if (second == null) {
            return null;
        }
        // `top left` is written either way round; a keyword says which axis.
        if (first.vertical() || second.horizontal()) {
            if (first.horizontal() || second.vertical()) {
                return null;
            }
            return new BackgroundPosition(second.offset(), first.offset());
        }
        return new BackgroundPosition(first.offset(), second.offset());
    }

    /// One `background-position` component.
    ///
    /// @param offset     how far it moves a layer, which only a length does
    /// @param horizontal whether it is `left` or `right`
    /// @param vertical   whether it is `top` or `bottom`
    private record Component(double offset, boolean horizontal, boolean vertical) {}

    private static @Nullable Component component(List<Token> part, CssLength.Context context) {
        if (part.size() == 1 && part.getFirst().is(TokenType.IDENT)) {
            return switch (part.getFirst().text().toLowerCase(Locale.ROOT)) {
                case "left", "right" -> new Component(0, true, false);
                case "top", "bottom" -> new Component(0, false, true);
                case "center" -> new Component(0, false, false);
                default -> null;
            };
        }
        return switch (CssLength.parse(part, context)) {
            case Length.Points(var points) -> new Component(points, false, false);
            case Length.Percent _ -> new Component(0, false, false);
            case null, default -> null;
        };
    }

    /// One gradient function, or null if `part` is not one this reads.
    ///
    /// `linear-gradient`, `radial-gradient` and their `repeating-` forms.
    /// A transition hint (a bare position between two stops) and the colour
    /// interpolation methods of CSS Images 4 are not read.
    public static @Nullable GradientLayer gradient(List<Token> part, CssLength.Context context) {
        if (part.size() < 2
                || !part.getFirst().is(TokenType.FUNCTION)
                || !part.getLast().is(TokenType.CLOSE_PAREN)) {
            return null;
        }
        var name = part.getFirst().text().toLowerCase(Locale.ROOT);
        var repeating = name.startsWith("repeating-");
        var kind = repeating ? name.substring("repeating-".length()) : name;
        var arguments = commas(part.subList(1, part.size() - 1));
        try {
            return switch (kind) {
                case "linear-gradient" -> linear(arguments, repeating, context);
                case "radial-gradient" -> radial(arguments, repeating, context);
                default -> null;
            };
        } catch (IllegalArgumentException e) {
            // A value the records refuse — fewer than two stops — is a value
            // that does not parse.
            return null;
        }
    }

    private static GradientLayer.@Nullable Linear linear(
            List<List<Token>> arguments, boolean repeating, CssLength.Context context) {
        var first = spaces(arguments.getFirst());
        GradientLayer.Direction direction = new GradientLayer.Direction.Angle(Math.PI);
        var from = 0;
        var radians = first.size() == 1 ? angle(first.getFirst()) : null;
        if (!first.isEmpty()
                && first.getFirst().size() == 1
                && first.getFirst().getFirst().isIdent("to")) {
            direction = towards(first.subList(1, first.size()));
            if (direction == null) {
                return null;
            }
            from = 1;
        } else if (radians != null) {
            direction = new GradientLayer.Direction.Angle(radians);
            from = 1;
        }
        var stops = stops(arguments.subList(from, arguments.size()), context);
        return stops == null ? null : new GradientLayer.Linear(direction, stops, repeating);
    }

    /// `to <side>` or `to <side> <side>`, read as a direction.
    private static GradientLayer.@Nullable Direction towards(List<List<Token>> words) {
        if (words.isEmpty() || words.size() > 2) {
            return null;
        }
        var horizontal = 0;
        var vertical = 0;
        for (var word : words) {
            if (word.size() != 1 || !word.getFirst().is(TokenType.IDENT)) {
                return null;
            }
            switch (word.getFirst().text().toLowerCase(Locale.ROOT)) {
                case "left" -> horizontal = horizontal == 0 ? -1 : 2;
                case "right" -> horizontal = horizontal == 0 ? 1 : 2;
                case "top" -> vertical = vertical == 0 ? -1 : 2;
                case "bottom" -> vertical = vertical == 0 ? 1 : 2;
                default -> {
                    return null;
                }
            }
        }
        if (horizontal == 2 || vertical == 2) {
            return null;
        }
        if (horizontal != 0 && vertical != 0) {
            return new GradientLayer.Direction.Corner(horizontal > 0, vertical > 0);
        }
        // A side is an angle: up is 0 and the angle turns clockwise.
        var degrees = horizontal > 0 ? 90 : horizontal < 0 ? 270 : vertical > 0 ? 180 : 0;
        return new GradientLayer.Direction.Angle(Math.toRadians(degrees));
    }

    private static GradientLayer.@Nullable Radial radial(
            List<List<Token>> arguments, boolean repeating, CssLength.Context context) {
        var first = spaces(arguments.getFirst());
        var from = 0;
        Boolean circle = null;
        GradientLayer.Extent extent = null;
        var sizes = new ArrayList<Length>();
        var centreX = CENTRE;
        var centreY = CENTRE;
        // The first argument is the shape when it is not a colour stop.
        if (!first.isEmpty() && CssColor.parse(first.getFirst()) == null) {
            from = 1;
            var at = first.size();
            for (var i = 0; i < first.size(); i++) {
                if (first.get(i).size() == 1 && first.get(i).getFirst().isIdent("at")) {
                    at = i;
                    break;
                }
            }
            for (var word : first.subList(0, at)) {
                var keyword = word.size() == 1 && word.getFirst().is(TokenType.IDENT)
                        ? word.getFirst().text().toLowerCase(Locale.ROOT)
                        : "";
                switch (keyword) {
                    case "circle", "ellipse" -> {
                        if (circle != null) {
                            return null;
                        }
                        circle = keyword.equals("circle");
                    }
                    case "closest-side", "closest-corner", "farthest-side", "farthest-corner" -> {
                        if (extent != null || !sizes.isEmpty()) {
                            return null;
                        }
                        extent = GradientLayer.Extent.valueOf(
                                keyword.toUpperCase(Locale.ROOT).replace('-', '_'));
                    }
                    default -> {
                        var size = CssLength.parse(word, context);
                        var isSize = size instanceof Length.Points || size instanceof Length.Percent;
                        if (!isSize || extent != null || sizes.size() == 2) {
                            return null;
                        }
                        sizes.add(size);
                    }
                }
            }
            if (at < first.size()) {
                var centre = centre(first.subList(at + 1, first.size()), context);
                if (centre == null) {
                    return null;
                }
                centreX = centre[0];
                centreY = centre[1];
            }
        }
        if (circle == null) {
            circle = sizes.size() == 1;
        }
        Length radiusX = null;
        Length radiusY = null;
        if (!sizes.isEmpty()) {
            // A circle is one length, never a percentage; an ellipse is two.
            if (circle ? sizes.size() != 1 || sizes.getFirst() instanceof Length.Percent : sizes.size() != 2) {
                return null;
            }
            radiusX = sizes.getFirst();
            radiusY = sizes.getLast();
        } else if (extent == null) {
            extent = GradientLayer.Extent.FARTHEST_CORNER;
        }
        var stops = stops(arguments.subList(from, arguments.size()), context);
        return stops == null
                ? null
                : new GradientLayer.Radial(circle, extent, radiusX, radiusY, centreX, centreY, stops, repeating);
    }

    /// The `at <position>` of a radial gradient, as two lengths.
    private static Length @Nullable [] centre(List<List<Token>> words, CssLength.Context context) {
        if (words.isEmpty() || words.size() > 2) {
            return null;
        }
        var x = CENTRE;
        var y = CENTRE;
        var horizontalSet = false;
        var verticalSet = false;
        for (var i = 0; i < words.size(); i++) {
            var word = words.get(i);
            if (word.size() == 1 && word.getFirst().is(TokenType.IDENT)) {
                switch (word.getFirst().text().toLowerCase(Locale.ROOT)) {
                    case "left" -> {
                        x = Length.percent(0);
                        horizontalSet = true;
                    }
                    case "right" -> {
                        x = Length.percent(100);
                        horizontalSet = true;
                    }
                    case "top" -> {
                        y = Length.percent(0);
                        verticalSet = true;
                    }
                    case "bottom" -> {
                        y = Length.percent(100);
                        verticalSet = true;
                    }
                    case "center" -> {
                        // Whichever axis is left: centre is already the default.
                    }
                    default -> {
                        return null;
                    }
                }
                continue;
            }
            var length = CssLength.parse(word, context);
            if (!(length instanceof Length.Points) && !(length instanceof Length.Percent)) {
                return null;
            }
            // A length is horizontal first, vertical second.
            if (i == 0 && !horizontalSet) {
                x = length;
                horizontalSet = true;
            } else if (!verticalSet) {
                y = length;
                verticalSet = true;
            } else {
                return null;
            }
        }
        return new Length[] {x, y};
    }

    /// The colour stops: each `<colour> [<position> [<position>]]`, two
    /// positions being two stops of one colour.
    private static @Nullable List<GradientLayer.ColorStop> stops(
            List<List<Token>> arguments, CssLength.Context context) {
        var stops = new ArrayList<GradientLayer.ColorStop>();
        for (var argument : arguments) {
            var parts = spaces(argument);
            if (parts.isEmpty() || parts.size() > 3) {
                return null;
            }
            var colour = CssColor.parse(parts.getFirst());
            if (colour == null) {
                return null;
            }
            if (parts.size() == 1) {
                stops.add(new GradientLayer.ColorStop(colour, null));
                continue;
            }
            for (var position : parts.subList(1, parts.size())) {
                var length = CssLength.parse(position, context);
                if (!(length instanceof Length.Points) && !(length instanceof Length.Percent)) {
                    return null;
                }
                stops.add(new GradientLayer.ColorStop(colour, length));
            }
        }
        return stops.size() < 2 ? null : List.copyOf(stops);
    }

    /// An angle in radians, or null; a bare zero is an angle, as CSS allows.
    private static @Nullable Double angle(List<Token> part) {
        if (part.size() != 1) {
            return null;
        }
        var token = part.getFirst();
        if (token.is(TokenType.NUMBER) && token.numeric() == 0) {
            return 0.0;
        }
        if (!token.is(TokenType.DIMENSION)) {
            return null;
        }
        return switch (token.unit().toLowerCase(Locale.ROOT)) {
            case "deg" -> Math.toRadians(token.numeric());
            case "rad" -> token.numeric();
            case "grad" -> token.numeric() * Math.PI / 200;
            case "turn" -> token.numeric() * 2 * Math.PI;
            default -> null;
        };
    }

    /// Splits on the commas that are not inside a function.
    private static List<List<Token>> commas(List<Token> value) {
        var entries = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : value) {
            depth = depth(token, depth);
            if (depth == 0 && token.is(TokenType.COMMA)) {
                entries.add(List.copyOf(current));
                current.clear();
            } else {
                current.add(token);
            }
        }
        entries.add(List.copyOf(current));
        return entries;
    }

    /// Splits on the whitespace that is not inside a function.
    private static List<List<Token>> spaces(List<Token> value) {
        var parts = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : value) {
            depth = depth(token, depth);
            if (depth == 0 && token.is(TokenType.WHITESPACE)) {
                if (!current.isEmpty()) {
                    parts.add(List.copyOf(current));
                    current.clear();
                }
            } else {
                current.add(token);
            }
        }
        if (!current.isEmpty()) {
            parts.add(List.copyOf(current));
        }
        return parts;
    }

    /// The nesting depth after `token`. A closing parenthesis is counted
    /// before the token is placed, so it lands inside the function it closes.
    private static int depth(Token token, int depth) {
        if (token.is(TokenType.OPEN_PAREN) || token.is(TokenType.FUNCTION)) {
            return depth + 1;
        }
        if (token.is(TokenType.CLOSE_PAREN)) {
            return Math.max(0, depth - 1);
        }
        return depth;
    }
}

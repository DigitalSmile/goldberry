package dev.goldberry.css.background;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.image.CssImage;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.calc.Maths;
import dev.goldberry.css.value.calc.Quantity;
import dev.goldberry.css.value.colour.ColourMixing;
import dev.goldberry.css.value.colour.ColourSpace;
import dev.goldberry.layout.Length;

/// Reads the `background` properties: the shorthand, `background-image`,
/// `background-position`, `background-size` and `background-repeat`.
///
/// Every method answers null for a value it cannot read whole, so the cascade
/// drops the declaration and says so, as it does for every other property.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
public final class BackgroundParser {

    private static final Length CENTRE = Length.percent(50);

    private BackgroundParser() {}

    /// The `background` shorthand: a comma list of layers, each a gradient, a
    /// `url()` or `none` with an optional position and size
    /// (`<position> [/ <size>]`), repeat, attachment and one or two boxes; the
    /// last may also name the colour.
    ///
    /// A shorthand resets what it does not name: the colour to transparent, the
    /// position to the top left, every size to `auto`, every repeat to
    /// `repeat`, the origin to the padding box and the clip to the border box.
    /// One box is both the origin and the clip; two are the origin, then the
    /// clip. `background: none` is no fill at all. The only attachment is
    /// `scroll`, which is what a box does anyway.
    public static @Nullable Background shorthand(List<Token> value, CssLength.Context context) {
        var entries = commas(value);
        var layers = new ArrayList<CssImage>();
        var positions = new ArrayList<BackgroundPosition>();
        var sizes = new ArrayList<BackgroundSize>();
        var repeats = new ArrayList<BackgroundRepeat>();
        var origins = new ArrayList<BackgroundBox>();
        var clips = new ArrayList<BackgroundBox>();
        var colour = CssColor.TRANSPARENT;
        Layer last = null;
        for (var i = 0; i < entries.size(); i++) {
            var layer = layer(entries.get(i), i == entries.size() - 1, context);
            if (layer == null) {
                return null;
            }
            last = layer;
            if (layer.image() != null) {
                layers.add(layer.image());
                positions.add(layer.position());
                sizes.add(layer.size());
                repeats.add(layer.repeat());
                origins.add(layer.origin());
                clips.add(layer.clip());
            }
            if (layer.colour() != null) {
                colour = layer.colour();
            }
        }
        if (layers.isEmpty() && last != null) {
            // No picture at all: the last layer's clip still cuts the colour.
            clips.add(last.clip());
        }
        return Background.of(
                colour,
                layers,
                collapse(positions, Background.ZERO),
                collapse(sizes, Background.AUTO_SIZE),
                collapse(repeats, Background.REPEAT),
                collapse(origins, Background.PADDING_BOX),
                collapse(clips, Background.BORDER_BOX));
    }

    /// One layer of the shorthand, its parts at their initial values where it
    /// does not name them.
    private record Layer(
            @Nullable CssImage image,
            BackgroundPosition position,
            BackgroundSize size,
            BackgroundRepeat repeat,
            BackgroundBox origin,
            BackgroundBox clip,
            @Nullable Integer colour) {}

    private static @Nullable Layer layer(List<Token> entry, boolean last, CssLength.Context context) {
        var parts = slashes(spaces(entry));
        if (parts.isEmpty()) {
            return null;
        }
        CssImage image = null;
        var none = false;
        Integer named = null;
        BackgroundPosition position = null;
        BackgroundSize size = null;
        var attached = false;
        var boxes = new ArrayList<BackgroundBox>();
        var words = new ArrayList<List<Token>>();
        var i = 0;
        while (i < parts.size()) {
            var part = parts.get(i);
            if (part.size() == 1 && part.getFirst().isIdent("none") && image == null && !none) {
                none = true;
                i++;
                continue;
            }
            var picture = image(part, context);
            if (picture != null && image == null && !none) {
                image = picture;
                i++;
                continue;
            }
            if (position == null && PositionParser.isWord(part, context)) {
                // The position's words run until something else; a `/` after
                // them is the size.
                var end = i;
                while (end < parts.size() && end - i < 4 && PositionParser.isWord(parts.get(end), context)) {
                    end++;
                }
                position = PositionParser.read(parts.subList(i, end), context);
                if (position == null) {
                    return null;
                }
                i = end;
                if (i < parts.size() && isSlash(parts.get(i))) {
                    var sizeEnd = i + 1;
                    while (sizeEnd < parts.size() && sizeEnd - i <= 2 && isSizeWord(parts.get(sizeEnd), context)) {
                        sizeEnd++;
                    }
                    size = size(parts.subList(i + 1, sizeEnd), context);
                    if (size == null) {
                        return null;
                    }
                    i = sizeEnd;
                }
                continue;
            }
            if (isRepeatWord(part) && words.size() < 2) {
                words.add(part);
                i++;
                continue;
            }
            if (part.size() == 1 && part.getFirst().isIdent("scroll") && !attached) {
                attached = true;
                i++;
                continue;
            }
            var box = part.size() == 1 && part.getFirst().is(TokenType.IDENT)
                    ? BackgroundBox.named(part.getFirst().text())
                    : null;
            if (box != null && boxes.size() < 2) {
                boxes.add(box);
                i++;
                continue;
            }
            var asColour = CssColor.parse(part);
            // Only the last layer has a colour, which is CSS's rule.
            if (asColour != null && last && named == null) {
                named = asColour;
                i++;
                continue;
            }
            return null;
        }
        var repeat = BackgroundRepeat.REPEAT;
        if (!words.isEmpty()) {
            var read = repeat(words);
            if (read == null) {
                return null;
            }
            repeat = read;
        }
        return new Layer(
                image,
                position == null ? BackgroundPosition.ZERO : position,
                size == null ? BackgroundSize.AUTO : size,
                repeat,
                boxes.isEmpty() ? BackgroundBox.PADDING_BOX : boxes.getFirst(),
                boxes.isEmpty() ? BackgroundBox.BORDER_BOX : boxes.getLast(),
                named);
    }

    /// `list`, or `initial` when every entry is `initial`'s one: so a
    /// shorthand that names nothing compares equal to one never written.
    private static <T> List<T> collapse(List<T> list, List<T> initial) {
        for (var entry : list) {
            if (!entry.equals(initial.getFirst())) {
                return List.copyOf(list);
            }
        }
        return initial;
    }

    /// `parts` with every `/` its own part, so `3px/10px` reads as `3px / 10px`.
    private static List<List<Token>> slashes(List<List<Token>> parts) {
        var split = new ArrayList<List<Token>>();
        for (var part : parts) {
            var current = new ArrayList<Token>();
            var depth = 0;
            for (var token : part) {
                depth = depth(token, depth);
                if (depth == 0 && token.isDelim('/')) {
                    if (!current.isEmpty()) {
                        split.add(List.copyOf(current));
                        current.clear();
                    }
                    split.add(List.of(token));
                } else {
                    current.add(token);
                }
            }
            if (!current.isEmpty()) {
                split.add(List.copyOf(current));
            }
        }
        return split;
    }

    private static boolean isSlash(List<Token> part) {
        return part.size() == 1 && part.getFirst().isDelim('/');
    }

    private static boolean isSizeWord(List<Token> part, CssLength.Context context) {
        return (part.size() == 1
                        && (part.getFirst().isIdent("cover") || part.getFirst().isIdent("contain")))
                || sizeLength(part, context) != null;
    }

    /// One layer of `background-image`: a gradient or a `url()`, or null.
    public static @Nullable CssImage image(List<Token> part, CssLength.Context context) {
        var url = CssImage.url(part);
        return url != null ? url : gradient(part, context);
    }

    /// `background-repeat`: a comma list, one per layer, each `repeat`,
    /// `no-repeat`, `repeat-x`, `repeat-y`, `space` or `round`, or two of
    /// `repeat`, `no-repeat`, `space` and `round` for the two axes.
    public static @Nullable List<BackgroundRepeat> repeats(List<Token> value) {
        var list = new ArrayList<BackgroundRepeat>();
        for (var entry : commas(value)) {
            var read = repeat(spaces(entry));
            if (read == null) {
                return null;
            }
            list.add(read);
        }
        return List.copyOf(list);
    }

    /// `background-size`: a comma list, one per layer, each `cover`, `contain`,
    /// or one or two of a length, a percentage of the box and `auto`. One value
    /// is the width, and the height follows the picture's shape.
    public static @Nullable List<BackgroundSize> sizes(List<Token> value, CssLength.Context context) {
        var list = new ArrayList<BackgroundSize>();
        for (var entry : commas(value)) {
            var size = size(spaces(entry), context);
            if (size == null) {
                return null;
            }
            list.add(size);
        }
        return List.copyOf(list);
    }

    /// One layer's size, from its words.
    private static @Nullable BackgroundSize size(List<List<Token>> parts, CssLength.Context context) {
        if (parts.size() == 1 && parts.getFirst().size() == 1) {
            var only = parts.getFirst().getFirst();
            if (only.isIdent("cover")) {
                return BackgroundSize.COVER;
            }
            if (only.isIdent("contain")) {
                return BackgroundSize.CONTAIN;
            }
        }
        if (parts.isEmpty() || parts.size() > 2) {
            return null;
        }
        var width = sizeLength(parts.getFirst(), context);
        var height = parts.size() == 2 ? sizeLength(parts.get(1), context) : Length.AUTO;
        if (width == null || height == null) {
            return null;
        }
        return new BackgroundSize(BackgroundSize.Kind.LENGTHS, width, height);
    }

    /// One axis of a `background-size`: a length that is not negative, a
    /// percentage, or `auto`.
    private static @Nullable Length sizeLength(List<Token> part, CssLength.Context context) {
        return switch (CssLength.parse(part, context)) {
            case Length.Points points when points.value() >= 0 -> points;
            case Length.Percent percent when percent.value() >= 0 -> percent;
            case Length.Keyword keyword when keyword == Length.AUTO -> keyword;
            case null, default -> null;
        };
    }

    private static boolean isRepeatWord(List<Token> part) {
        return part.size() == 1
                && part.getFirst().is(TokenType.IDENT)
                && BackgroundRepeat.named(part.getFirst().text()) != null;
    }

    /// One layer's repeat, from one keyword or two.
    private static @Nullable BackgroundRepeat repeat(List<List<Token>> words) {
        if (words.isEmpty() || words.size() > 2) {
            return null;
        }
        var values = new ArrayList<BackgroundRepeat>();
        for (var word : words) {
            if (word.size() != 1 || !word.getFirst().is(TokenType.IDENT)) {
                return null;
            }
            var named = BackgroundRepeat.named(word.getFirst().text());
            if (named == null) {
                return null;
            }
            values.add(named);
        }
        if (values.size() == 1) {
            return values.getFirst();
        }
        // Two values are an axis each, and `repeat-x` and `repeat-y` say both.
        var across = values.getFirst();
        var down = values.getLast();
        if (across.x() != across.y() || down.x() != down.y()) {
            return null;
        }
        return new BackgroundRepeat(across.x(), down.y());
    }

    /// `background-image`: `none`, or a comma list of gradients and `url()`
    /// pictures, top first.
    public static @Nullable List<CssImage> images(List<Token> value, CssLength.Context context) {
        var entries = commas(value);
        if (entries.size() == 1) {
            var only = spaces(entries.getFirst());
            if (only.size() == 1
                    && only.getFirst().size() == 1
                    && only.getFirst().getFirst().isIdent("none")) {
                return List.of();
            }
        }
        var layers = new ArrayList<CssImage>();
        for (var entry : entries) {
            var parts = spaces(entry);
            if (parts.size() != 1) {
                return null;
            }
            var layer = image(parts.getFirst(), context);
            if (layer == null) {
                return null;
            }
            layers.add(layer);
        }
        return List.copyOf(layers);
    }

    /// One `background-position`: one to four words, keywords, lengths and
    /// percentages, as CSS reads them.
    public static @Nullable BackgroundPosition position(List<Token> value, CssLength.Context context) {
        return PositionParser.read(spaces(value), context);
    }

    /// `background-position`: a comma list, one per layer.
    public static @Nullable List<BackgroundPosition> positions(List<Token> value, CssLength.Context context) {
        var list = new ArrayList<BackgroundPosition>();
        for (var entry : commas(value)) {
            var position = PositionParser.read(spaces(entry), context);
            if (position == null) {
                return null;
            }
            list.add(position);
        }
        return List.copyOf(list);
    }

    /// `background-position-x` or `-y`: a comma list, one per layer, each with
    /// only the one axis set.
    public static @Nullable List<BackgroundPosition> axis(
            List<Token> value, boolean horizontal, CssLength.Context context) {
        var list = new ArrayList<BackgroundPosition>();
        for (var entry : commas(value)) {
            var position = PositionParser.axis(spaces(entry), horizontal, context);
            if (position == null) {
                return null;
            }
            list.add(position);
        }
        return List.copyOf(list);
    }

    /// `background-origin` or `background-clip`: a comma list of
    /// `border-box`, `padding-box` and `content-box`.
    public static @Nullable List<BackgroundBox> boxes(List<Token> value) {
        var list = new ArrayList<BackgroundBox>();
        for (var entry : commas(value)) {
            var parts = spaces(entry);
            var box = parts.size() == 1
                            && parts.getFirst().size() == 1
                            && parts.getFirst().getFirst().is(TokenType.IDENT)
                    ? BackgroundBox.named(parts.getFirst().getFirst().text())
                    : null;
            if (box == null) {
                return null;
            }
            list.add(box);
        }
        return List.copyOf(list);
    }

    /// `positions` with each entry's one axis replaced by `axis`'s, entry by
    /// entry, the shorter list repeated.
    public static List<BackgroundPosition> withAxis(
            List<BackgroundPosition> positions, List<BackgroundPosition> axis, boolean horizontal) {
        var count = Math.max(positions.size(), axis.size());
        var list = new ArrayList<BackgroundPosition>(count);
        for (var i = 0; i < count; i++) {
            var position = positions.get(i % positions.size());
            var from = axis.get(i % axis.size());
            list.add(horizontal ? position.withX(from) : position.withY(from));
        }
        return List.copyOf(list);
    }

    /// One gradient function, or null if `part` is not one this reads.
    ///
    /// `linear-gradient`, `radial-gradient`, `conic-gradient` and their
    /// `repeating-` forms, with transition hints between the stops and an
    /// `in <colour-space> [<method> hue]` in the first argument.
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
                case "conic-gradient" -> conic(arguments, repeating, context);
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
        var words = spaces(arguments.getFirst());
        var interpolation = interpolation(words);
        if (interpolation == null) {
            return null;
        }
        var first = interpolation.rest();
        GradientLayer.Direction direction = new GradientLayer.Direction.Angle(Math.PI);
        var from = interpolation.named() && first.isEmpty() ? 1 : 0;
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
        } else if (!first.isEmpty() && interpolation.named()) {
            return null;
        }
        var stops = stops(arguments.subList(from, arguments.size()), part -> length(part, context));
        return stops == null
                ? null
                : new GradientLayer.Linear(direction, stops, repeating, interpolation.interpolation());
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
        var words = spaces(arguments.getFirst());
        var interpolation = interpolation(words);
        if (interpolation == null) {
            return null;
        }
        var first = interpolation.rest();
        var from = interpolation.named() ? 1 : 0;
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
        var stops = stops(arguments.subList(from, arguments.size()), part -> length(part, context));
        return stops == null
                ? null
                : new GradientLayer.Radial(
                        circle,
                        extent,
                        radiusX,
                        radiusY,
                        centreX,
                        centreY,
                        stops,
                        repeating,
                        interpolation.interpolation());
    }

    /// `conic-gradient([from <angle>] [at <position>] [in <space>], stops)`,
    /// its stops placed by angle or by percentage of a turn.
    private static GradientLayer.@Nullable Conic conic(
            List<List<Token>> arguments, boolean repeating, CssLength.Context context) {
        var words = spaces(arguments.getFirst());
        var interpolation = interpolation(words);
        if (interpolation == null) {
            return null;
        }
        var first = interpolation.rest();
        var from = interpolation.named() ? 1 : 0;
        var start = 0.0;
        var centreX = CENTRE;
        var centreY = CENTRE;
        if (!first.isEmpty()
                && (first.getFirst().size() == 1)
                && (first.getFirst().getFirst().isIdent("from")
                        || first.getFirst().getFirst().isIdent("at"))) {
            from = 1;
            var at = first.size();
            for (var i = 0; i < first.size(); i++) {
                if (first.get(i).size() == 1 && first.get(i).getFirst().isIdent("at")) {
                    at = i;
                    break;
                }
            }
            if (at > 0) {
                if (at != 2) {
                    return null;
                }
                var radians = angle(first.get(1));
                if (radians == null) {
                    return null;
                }
                start = radians;
            }
            if (at < first.size()) {
                var centre = centre(first.subList(at + 1, first.size()), context);
                if (centre == null) {
                    return null;
                }
                centreX = centre[0];
                centreY = centre[1];
            }
        } else if (!first.isEmpty() && interpolation.named()) {
            return null;
        }
        var stops = stops(arguments.subList(from, arguments.size()), BackgroundParser::turn);
        return stops == null
                ? null
                : new GradientLayer.Conic(start, centreX, centreY, stops, repeating, interpolation.interpolation());
    }

    /// A conic stop's position: an angle, as a percentage of a turn, or a
    /// percentage. Never a length.
    private static @Nullable Length turn(List<Token> part) {
        var radians = angle(part);
        if (radians != null) {
            return Length.percent((float) (Math.toDegrees(radians) / 3.6));
        }
        var percent = CssLength.parse(part, CssLength.Context.DEFAULT);
        return percent instanceof Length.Percent ? percent : null;
    }

    /// A linear or radial stop's position: a length or a percentage.
    private static @Nullable Length length(List<Token> part, CssLength.Context context) {
        var length = CssLength.parse(part, context);
        return length instanceof Length.Points || length instanceof Length.Percent ? length : null;
    }

    /// The first argument's words with any `in <space> [<method> hue]` taken
    /// out, at its start or its end.
    ///
    /// @param rest          the other words, in order
    /// @param interpolation what was named, or sRGB
    /// @param named         whether there was an `in` at all
    private record Interpolated(List<List<Token>> rest, GradientLayer.Interpolation interpolation, boolean named) {}

    private static @Nullable Interpolated interpolation(List<List<Token>> words) {
        var in = -1;
        for (var i = 0; i < words.size(); i++) {
            if (words.get(i).size() == 1 && words.get(i).getFirst().isIdent("in")) {
                in = i;
                break;
            }
        }
        if (in < 0) {
            return new Interpolated(words, GradientLayer.Interpolation.SRGB, false);
        }
        if (in + 1 >= words.size() || !isWord(words.get(in + 1))) {
            return null;
        }
        var space = ColourSpace.parse(words.get(in + 1).getFirst().text());
        if (space == null) {
            return null;
        }
        var end = in + 2;
        var hue = ColourMixing.HueMethod.SHORTER;
        if (end + 1 < words.size()
                && isWord(words.get(end))
                && words.get(end + 1).size() == 1
                && words.get(end + 1).getFirst().isIdent("hue")) {
            var method = ColourMixing.HueMethod.parse(words.get(end).getFirst().text());
            // Only a space with a hue has a way round it.
            if (method == null || space.hue() < 0) {
                return null;
            }
            hue = method;
            end += 2;
        }
        // At the start or the end of the argument, never in the middle of it.
        if (in != 0 && end != words.size()) {
            return null;
        }
        var rest = new ArrayList<List<Token>>(words.subList(0, in));
        rest.addAll(words.subList(end, words.size()));
        return new Interpolated(List.copyOf(rest), new GradientLayer.Interpolation(space, hue), true);
    }

    private static boolean isWord(List<Token> part) {
        return part.size() == 1 && part.getFirst().is(TokenType.IDENT);
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
                var keyword = word.getFirst().text().toLowerCase(Locale.ROOT);
                switch (keyword) {
                    // An axis is said once: `left right` and `10px left` are refused.
                    case "left", "right" -> {
                        if (horizontalSet) {
                            return null;
                        }
                        x = Length.percent(keyword.equals("left") ? 0 : 100);
                        horizontalSet = true;
                    }
                    case "top", "bottom" -> {
                        if (verticalSet) {
                            return null;
                        }
                        y = Length.percent(keyword.equals("top") ? 0 : 100);
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
            if (i == 0) {
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
    /// positions being two stops of one colour, or a hint, a position alone
    /// between two of them.
    ///
    /// @param positions reads a position, or answers null for one it refuses
    private static @Nullable List<GradientLayer.ColorStop> stops(
            List<List<Token>> arguments, Function<List<Token>, @Nullable Length> positions) {
        var stops = new ArrayList<GradientLayer.ColorStop>();
        for (var argument : arguments) {
            var parts = spaces(argument);
            if (parts.isEmpty() || parts.size() > 3) {
                return null;
            }
            var colour = CssColor.parse(parts.getFirst());
            if (colour == null) {
                var hint = parts.size() == 1 ? positions.apply(parts.getFirst()) : null;
                // A hint sits between two colours, never first or after another.
                if (hint == null || stops.isEmpty() || stops.getLast().hint()) {
                    return null;
                }
                stops.add(GradientLayer.ColorStop.hint(hint));
                continue;
            }
            if (parts.size() == 1) {
                stops.add(new GradientLayer.ColorStop(colour, null));
                continue;
            }
            for (var position : parts.subList(1, parts.size())) {
                var length = positions.apply(position);
                if (length == null) {
                    return null;
                }
                stops.add(new GradientLayer.ColorStop(colour, length));
            }
        }
        if (stops.size() < 2 || stops.getLast().hint()) {
            return null;
        }
        return List.copyOf(stops);
    }

    /// An angle in radians, or null; a bare zero is an angle, as CSS allows, and
    /// so is a math function that folds to one.
    private static @Nullable Double angle(List<Token> part) {
        if (Maths.isMath(part)) {
            var math = Maths.fold(part, CssLength.Context.DEFAULT);
            return math != null && math.kind() == Quantity.Kind.ANGLE ? Math.toRadians(math.value()) : null;
        }
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

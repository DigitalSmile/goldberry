package dev.goldberry.css.image;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;

/// Reads the `border-image` properties: the shorthand and its five longhands.
///
/// Every method answers null for a value it cannot read whole, so the cascade
/// drops the declaration and says so, as it does for every other property.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#border-outline-and-shadow).
public final class BorderImageParser {

    private BorderImageParser() {}

    /// `border-image-source` on `into`: `none` or one `url("…")`. A gradient is
    /// not read here: a border image is cut from a picture's pixels.
    public static @Nullable BorderImage source(List<Token> value, BorderImage into) {
        var items = items(value);
        if (items.size() != 1) {
            return null;
        }
        var item = items.getFirst();
        if (isIdent(item, "none")) {
            return into.source(null);
        }
        var url = CssImage.url(item);
        return url == null ? null : into.source(url);
    }

    /// `border-image-slice` on `into`: one to four numbers or percentages, top
    /// first, and `fill` before or after them.
    public static @Nullable BorderImage slice(List<Token> value, BorderImage into) {
        var slice = slice(items(value));
        return slice == null ? null : into.slice(slice);
    }

    /// `border-image-width` on `into`: one to four of a number, a length, a
    /// percentage and `auto`, none negative.
    public static @Nullable BorderImage widths(List<Token> value, CssLength.Context context, BorderImage into) {
        var widths = four(items(value), item -> width(item, context));
        return widths == null ? null : into.widths(widths);
    }

    /// `border-image-outset` on `into`: one to four of a number and a length,
    /// none negative.
    public static @Nullable BorderImage outsets(List<Token> value, CssLength.Context context, BorderImage into) {
        var outsets = four(items(value), item -> outset(item, context));
        return outsets == null ? null : into.outsets(outsets);
    }

    /// `border-image-repeat` on `into`: one or two of `stretch`, `repeat` and
    /// `round`, across first. `space` is not read.
    public static @Nullable BorderImage repeat(List<Token> value, BorderImage into) {
        var repeat = repeat(items(value));
        return repeat == null ? null : into.repeat(repeat[0], repeat[1]);
    }

    /// The `border-image` shorthand:
    /// `<source> || <slice> [/ <width> | / <width>? / <outset>]? || <repeat>`.
    ///
    /// Like every shorthand it resets what it does not name, so
    /// `border-image: url("…") 48 fill / 48px` leaves the outset at zero and the
    /// repeat at `stretch`.
    public static @Nullable BorderImage shorthand(List<Token> value, CssLength.Context context) {
        var items = items(value);
        if (items.isEmpty()) {
            return null;
        }
        var result = BorderImage.NONE;
        var sourceSeen = false;
        var sliceSeen = false;
        var repeatSeen = false;
        var at = 0;
        while (at < items.size()) {
            var item = items.get(at);
            if (!sourceSeen && isIdent(item, "none")) {
                sourceSeen = true;
                at++;
                continue;
            }
            var url = CssImage.url(item);
            if (url != null) {
                if (sourceSeen) {
                    return null;
                }
                result = result.source(url);
                sourceSeen = true;
                at++;
                continue;
            }
            if (repeatWord(item) != null) {
                if (repeatSeen) {
                    return null;
                }
                var end = at;
                while (end < items.size() && end - at < 2 && repeatWord(items.get(end)) != null) {
                    end++;
                }
                var read = repeat(items.subList(at, end));
                if (read == null) {
                    return null;
                }
                result = result.repeat(read[0], read[1]);
                repeatSeen = true;
                at = end;
                continue;
            }
            if (sliceWord(item)) {
                if (sliceSeen) {
                    return null;
                }
                sliceSeen = true;
                var end = at;
                while (end < items.size() && sliceWord(items.get(end))) {
                    end++;
                }
                var slice = slice(items.subList(at, end));
                if (slice == null) {
                    return null;
                }
                result = result.slice(slice);
                at = end;
                if (at < items.size() && isSlash(items.get(at))) {
                    // `/ <width>`, `/ <width> / <outset>` or `/ / <outset>`.
                    at++;
                    var widthEnd = at;
                    while (widthEnd < items.size() && width(items.get(widthEnd), context) != null) {
                        widthEnd++;
                    }
                    var widthItems = items.subList(at, widthEnd);
                    at = widthEnd;
                    var outsetItems = List.<List<Token>>of();
                    if (at < items.size() && isSlash(items.get(at))) {
                        at++;
                        var outsetEnd = at;
                        while (outsetEnd < items.size() && outset(items.get(outsetEnd), context) != null) {
                            outsetEnd++;
                        }
                        outsetItems = items.subList(at, outsetEnd);
                        at = outsetEnd;
                        if (outsetItems.isEmpty()) {
                            return null;
                        }
                    } else if (widthItems.isEmpty()) {
                        return null;
                    }
                    if (!widthItems.isEmpty()) {
                        var widths = four(widthItems, part -> width(part, context));
                        if (widths == null) {
                            return null;
                        }
                        result = result.widths(widths);
                    }
                    if (!outsetItems.isEmpty()) {
                        var outsets = four(outsetItems, part -> outset(part, context));
                        if (outsets == null) {
                            return null;
                        }
                        result = result.outsets(outsets);
                    }
                }
                continue;
            }
            return null;
        }
        return result;
    }

    private static BorderImage.@Nullable Slice slice(List<List<Token>> items) {
        var fill = false;
        var lines = new ArrayList<Length>();
        for (var i = 0; i < items.size(); i++) {
            var item = items.get(i);
            if (isIdent(item, "fill")) {
                // Before the numbers or after them, and once.
                if (fill || (i != 0 && i != items.size() - 1)) {
                    return null;
                }
                fill = true;
                continue;
            }
            if (item.size() != 1) {
                return null;
            }
            var token = item.getFirst();
            if (token.is(TokenType.NUMBER) && token.numeric() >= 0) {
                lines.add(Length.points((float) token.numeric()));
            } else if (token.is(TokenType.PERCENTAGE) && token.numeric() >= 0) {
                lines.add(Length.percent((float) token.numeric()));
            } else {
                return null;
            }
        }
        var sides = sides(lines);
        return sides == null
                ? null
                : new BorderImage.Slice(sides.get(0), sides.get(1), sides.get(2), sides.get(3), fill);
    }

    private static BorderImage.Repeat @Nullable [] repeat(List<List<Token>> items) {
        if (items.isEmpty() || items.size() > 2) {
            return null;
        }
        var across = repeatWord(items.getFirst());
        var down = repeatWord(items.getLast());
        if (across == null || down == null) {
            return null;
        }
        return new BorderImage.Repeat[] {across, down};
    }

    private static BorderImage.@Nullable Repeat repeatWord(List<Token> item) {
        if (item.size() != 1 || !item.getFirst().is(TokenType.IDENT)) {
            return null;
        }
        return switch (item.getFirst().text().toLowerCase(Locale.ROOT)) {
            case "stretch" -> BorderImage.Repeat.STRETCH;
            case "repeat" -> BorderImage.Repeat.REPEAT;
            case "round" -> BorderImage.Repeat.ROUND;
            default -> null;
        };
    }

    private static boolean sliceWord(List<Token> item) {
        if (isIdent(item, "fill")) {
            return true;
        }
        return item.size() == 1
                && (item.getFirst().is(TokenType.NUMBER) || item.getFirst().is(TokenType.PERCENTAGE));
    }

    private static BorderImage.@Nullable Extent width(List<Token> item, CssLength.Context context) {
        if (isIdent(item, "auto")) {
            return BorderImage.Extent.AUTO;
        }
        return extent(item, context, true);
    }

    private static BorderImage.@Nullable Extent outset(List<Token> item, CssLength.Context context) {
        return extent(item, context, false);
    }

    /// A number, a length, or — when `percent` — a percentage; none negative.
    private static BorderImage.@Nullable Extent extent(List<Token> item, CssLength.Context context, boolean percent) {
        if (item.size() != 1) {
            return null;
        }
        var token = item.getFirst();
        if (token.is(TokenType.NUMBER)) {
            return token.numeric() >= 0
                    ? new BorderImage.Extent(BorderImage.Extent.Kind.NUMBER, token.numeric())
                    : null;
        }
        if (token.is(TokenType.PERCENTAGE)) {
            return percent && token.numeric() >= 0
                    ? new BorderImage.Extent(BorderImage.Extent.Kind.PERCENT, token.numeric())
                    : null;
        }
        if (CssLength.parse(item, context) instanceof Length.Points(var points) && points >= 0) {
            return new BorderImage.Extent(BorderImage.Extent.Kind.LENGTH, points);
        }
        return null;
    }

    /// One to four values, each read by `each`, as top, right, bottom and left.
    private static <T> @Nullable List<T> four(List<List<Token>> items, Function<List<Token>, @Nullable T> each) {
        var values = new ArrayList<T>();
        for (var item : items) {
            var read = each.apply(item);
            if (read == null) {
                return null;
            }
            values.add(read);
        }
        return sides(values);
    }

    /// CSS's 1-4 side rule: one is every side, two are vertical then
    /// horizontal, three add a bottom, four go round from the top.
    private static <T> @Nullable List<T> sides(List<T> values) {
        return switch (values.size()) {
            case 1 -> List.of(values.getFirst(), values.getFirst(), values.getFirst(), values.getFirst());
            case 2 -> List.of(values.get(0), values.get(1), values.get(0), values.get(1));
            case 3 -> List.of(values.get(0), values.get(1), values.get(2), values.get(1));
            case 4 -> List.copyOf(values);
            default -> null;
        };
    }

    private static boolean isIdent(List<Token> item, String name) {
        return item.size() == 1 && item.getFirst().isIdent(name);
    }

    private static boolean isSlash(List<Token> item) {
        return item.size() == 1 && item.getFirst().isDelim('/');
    }

    /// The value as items: each token outside a function, or a whole function,
    /// with the whitespace between them dropped and a `/` an item of its own.
    private static List<List<Token>> items(List<Token> value) {
        var items = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        var depth = 0;
        for (var token : value) {
            if (depth == 0) {
                if (token.is(TokenType.WHITESPACE)) {
                    flush(items, current);
                    continue;
                }
                if (token.isDelim('/')) {
                    flush(items, current);
                    items.add(List.of(token));
                    continue;
                }
                if (!token.is(TokenType.FUNCTION) && !token.is(TokenType.OPEN_PAREN)) {
                    flush(items, current);
                    items.add(List.of(token));
                    continue;
                }
            }
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            }
            current.add(token);
            if (depth == 0) {
                flush(items, current);
            }
        }
        flush(items, current);
        return items;
    }

    private static void flush(List<List<Token>> items, List<Token> current) {
        if (!current.isEmpty()) {
            items.add(List.copyOf(current));
            current.clear();
        }
    }
}

package dev.goldberry.css.background;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;

/// Reads a `<bg-position>`: one to four words, keywords and lengths, into a
/// [BackgroundPosition]; and one axis of one, for `background-position-x` and
/// `-y`.
///
/// ```css
/// background-position: center;               /* 50% 50% */
/// background-position: 10px top;             /* 10px 0% */
/// background-position: right 8px bottom 4px; /* 100% - 8px, 100% - 4px */
/// ```
///
/// Answers null for anything CSS would not read, so the declaration drops.
final class PositionParser {

    private PositionParser() {}

    /// One word of a position: a keyword, or a length or percentage.
    private record Word(@Nullable String keyword, @Nullable Length length) {

        boolean horizontal() {
            return "left".equals(keyword) || "right".equals(keyword);
        }

        boolean vertical() {
            return "top".equals(keyword) || "bottom".equals(keyword);
        }

        boolean centre() {
            return "center".equals(keyword);
        }

        boolean isLength() {
            return length != null;
        }
    }

    /// An axis: a length and a percentage of the room left over.
    private record Axis(double pixels, double percent) {

        static final Axis CENTRE = new Axis(0, 50);
    }

    /// Whether `part` could be one word of a position: what the `background`
    /// shorthand asks to know where a position starts and stops.
    static boolean isWord(List<Token> part, CssLength.Context context) {
        return word(part, context) != null;
    }

    /// A whole position from its words, or null.
    static @Nullable BackgroundPosition read(List<List<Token>> parts, CssLength.Context context) {
        if (parts.isEmpty() || parts.size() > 4) {
            return null;
        }
        var words = new ArrayList<Word>();
        for (var part : parts) {
            var word = word(part, context);
            if (word == null) {
                return null;
            }
            words.add(word);
        }
        return switch (words.size()) {
            case 1 -> one(words.getFirst());
            case 2 -> two(words.getFirst(), words.getLast());
            default -> edges(words);
        };
    }

    /// One axis of a position, `background-position-x` or `-y`: a keyword of
    /// that axis or `center`, a length, or a side and an offset from it.
    static @Nullable BackgroundPosition axis(List<List<Token>> parts, boolean horizontal, CssLength.Context context) {
        if (parts.isEmpty() || parts.size() > 2) {
            return null;
        }
        var first = word(parts.getFirst(), context);
        if (first == null) {
            return null;
        }
        Axis axis;
        if (parts.size() == 1) {
            if (first.isLength()) {
                axis = length(first);
            } else if (first.centre() || (horizontal ? first.horizontal() : first.vertical())) {
                axis = keyword(first);
            } else {
                return null;
            }
        } else {
            var second = word(parts.getLast(), context);
            if (second == null
                    || !second.isLength()
                    || first.isLength()
                    || !(horizontal ? first.horizontal() : first.vertical())) {
                return null;
            }
            axis = offset(first, second);
        }
        return horizontal
                ? new BackgroundPosition(axis.pixels(), 0, axis.percent(), 0)
                : new BackgroundPosition(0, axis.pixels(), 0, axis.percent());
    }

    private static BackgroundPosition one(Word word) {
        if (word.vertical()) {
            return position(Axis.CENTRE, keyword(word));
        }
        return position(word.isLength() ? length(word) : keyword(word), Axis.CENTRE);
    }

    private static @Nullable BackgroundPosition two(Word a, Word b) {
        if (!a.isLength() && !b.isLength()) {
            // `top left` is written either way round; a keyword says which axis.
            var swapped = a.vertical() || b.horizontal();
            var x = swapped ? b : a;
            var y = swapped ? a : b;
            if (x.vertical() || y.horizontal()) {
                return null;
            }
            return position(keyword(x), keyword(y));
        }
        // A length is across first and down second; a keyword beside it must
        // be one that can sit on its axis.
        if (a.isLength() && (b.isLength() || b.vertical() || b.centre())) {
            return position(length(a), b.isLength() ? length(b) : keyword(b));
        }
        if (b.isLength() && (a.horizontal() || a.centre())) {
            return position(keyword(a), length(b));
        }
        return null;
    }

    /// Three or four words: two keywords, one or both with an offset after it.
    private static @Nullable BackgroundPosition edges(List<Word> words) {
        Axis x = null;
        Axis y = null;
        var centres = 0;
        var i = 0;
        while (i < words.size()) {
            var side = words.get(i);
            if (side.isLength()) {
                return null;
            }
            var offset = i + 1 < words.size() && words.get(i + 1).isLength() ? words.get(i + 1) : null;
            i += offset == null ? 1 : 2;
            if (side.centre()) {
                if (offset != null) {
                    return null;
                }
                centres++;
                continue;
            }
            var axis = offset == null ? keyword(side) : offset(side, offset);
            if (side.horizontal()) {
                if (x != null) {
                    return null;
                }
                x = axis;
            } else {
                if (y != null) {
                    return null;
                }
                y = axis;
            }
        }
        if ((x == null ? 1 : 0) + (y == null ? 1 : 0) != centres) {
            return null;
        }
        return position(x == null ? Axis.CENTRE : x, y == null ? Axis.CENTRE : y);
    }

    private static BackgroundPosition position(Axis x, Axis y) {
        return new BackgroundPosition(x.pixels(), y.pixels(), x.percent(), y.percent());
    }

    private static Axis keyword(Word word) {
        return switch (String.valueOf(word.keyword())) {
            case "right", "bottom" -> new Axis(0, 100);
            case "center" -> Axis.CENTRE;
            default -> new Axis(0, 0);
        };
    }

    private static Axis length(Word word) {
        return switch (word.length()) {
            case Length.Points(var points) -> new Axis(points, 0);
            case Length.Percent(var percent) -> new Axis(0, percent);
            case Length.Mixed(var points, var percent) -> new Axis(points, percent);
            case null, default -> new Axis(0, 0);
        };
    }

    /// `side offset`: from the left or the top, the offset; from the right or
    /// the bottom, 100% less it.
    private static Axis offset(Word side, Word offset) {
        var by = length(offset);
        return side.keyword() != null
                        && (side.keyword().equals("right") || side.keyword().equals("bottom"))
                ? new Axis(0 - by.pixels(), 100 - by.percent())
                : by;
    }

    private static @Nullable Word word(List<Token> part, CssLength.Context context) {
        if (part.size() == 1 && part.getFirst().is(TokenType.IDENT)) {
            var keyword = part.getFirst().text().toLowerCase(Locale.ROOT);
            return switch (keyword) {
                case "left", "right", "top", "bottom", "center" -> new Word(keyword, null);
                default -> null;
            };
        }
        return switch (CssLength.parse(part, context)) {
            case Length.Points points -> new Word(null, points);
            case Length.Percent percent -> new Word(null, percent);
            case Length.Mixed mixed -> new Word(null, mixed);
            case null, default -> null;
        };
    }
}

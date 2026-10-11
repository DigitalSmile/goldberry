package dev.goldberry.css.property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.layout.Direction;

/// The logical properties, `margin-inline`, `padding-block-start`,
/// `inset-inline-end`, `inline-size` and the rest, as the physical ones they
/// are here.
///
/// ```css
/// .chip { padding-inline: 8px; margin-block: 2px 4px }   /* left and right; top, then bottom */
/// ```
///
/// There is one writing mode, horizontal, so the inline axis is across and the
/// block axis is down and starts at the top. The inline axis starts at the left,
/// or at the right in a box whose `direction` is `rtl`. Each logical property
/// is therefore one physical one, or two for a shorthand of a start and an end,
/// once the direction is known, and the table says so with an [Alias] or a
/// [Shorthand], written left to right: `inherit` and `initial` mean what they
/// mean for the physical property.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#properties).
public final class LogicalProperties {

    private LogicalProperties() {}

    /// Each one-to-one logical name, and the physical property it is.
    private static final Map<String, String> ONE = Map.ofEntries(
            Map.entry("margin-inline-start", "margin-left"),
            Map.entry("margin-inline-end", "margin-right"),
            Map.entry("margin-block-start", "margin-top"),
            Map.entry("margin-block-end", "margin-bottom"),
            Map.entry("padding-inline-start", "padding-left"),
            Map.entry("padding-inline-end", "padding-right"),
            Map.entry("padding-block-start", "padding-top"),
            Map.entry("padding-block-end", "padding-bottom"),
            Map.entry("inset-inline-start", "left"),
            Map.entry("inset-inline-end", "right"),
            Map.entry("inset-block-start", "top"),
            Map.entry("inset-block-end", "bottom"),
            Map.entry("inline-size", "width"),
            Map.entry("block-size", "height"),
            Map.entry("min-inline-size", "min-width"),
            Map.entry("max-inline-size", "max-width"),
            Map.entry("min-block-size", "min-height"),
            Map.entry("max-block-size", "max-height"));

    /// Each start-and-end shorthand, and the physical start and end it writes.
    private static final Map<String, List<String>> TWO = Map.ofEntries(
            Map.entry("margin-inline", List.of("margin-left", "margin-right")),
            Map.entry("margin-block", List.of("margin-top", "margin-bottom")),
            Map.entry("padding-inline", List.of("padding-left", "padding-right")),
            Map.entry("padding-block", List.of("padding-top", "padding-bottom")),
            Map.entry("inset-inline", List.of("left", "right")),
            Map.entry("inset-block", List.of("top", "bottom")));

    /// Whether `name` is a logical property.
    public static boolean isLogical(String name) {
        return ONE.containsKey(name) || TWO.containsKey(name);
    }

    /// One physical declaration a logical one is.
    ///
    /// @param property the physical property
    /// @param value    its value
    public record Physical(String property, List<Token> value) {

        public Physical {
            Objects.requireNonNull(property, "property");
            value = List.copyOf(value);
        }
    }

    /// The physical declarations `name: value` is, or null when `value` is not
    /// one or two values for a shorthand of two.
    ///
    /// @throws IllegalArgumentException if `name` is not a logical property
    public static @Nullable List<Physical> physical(String name, List<Token> value) {
        return physical(name, value, Direction.LTR);
    }

    /// The physical declarations `name: value` is in a box running
    /// `direction`, or null when `value` is not one or two values for a
    /// shorthand of two. In a right-to-left box the inline start is the right.
    ///
    /// @throws IllegalArgumentException if `name` is not a logical property
    public static @Nullable List<Physical> physical(String name, List<Token> value, Direction direction) {
        var mirror = direction == Direction.RTL && name.contains("inline");
        var one = ONE.get(name);
        if (one != null) {
            return List.of(new Physical(mirror ? mirrored(one) : one, value));
        }
        var two = TWO.get(name);
        if (two == null) {
            throw new IllegalArgumentException(name + " is not a logical property");
        }
        var words = words(value);
        if (words.isEmpty() || words.size() > 2) {
            return null;
        }
        var start = mirror ? mirrored(two.getFirst()) : two.getFirst();
        var end = mirror ? mirrored(two.getLast()) : two.getLast();
        return List.of(new Physical(start, words.getFirst()), new Physical(end, words.getLast()));
    }

    /// `margin-left` as `margin-right` and the other way round, `left` as
    /// `right`; any other name as itself.
    private static String mirrored(String physical) {
        if (physical.endsWith("left")) {
            return physical.substring(0, physical.length() - "left".length()) + "right";
        }
        if (physical.endsWith("right")) {
            return physical.substring(0, physical.length() - "right".length()) + "left";
        }
        return physical;
    }

    /// The table's entries: an alias for each one-to-one name, a shorthand for
    /// each pair.
    static List<Property> table() {
        var all = new ArrayList<Property>();
        ONE.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> all.add(new Alias(entry.getKey(), entry.getValue())));
        TWO.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> all.add(new Shorthand(entry.getKey(), entry.getValue())));
        return all;
    }

    /// The value's words, split at the spaces outside a function.
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

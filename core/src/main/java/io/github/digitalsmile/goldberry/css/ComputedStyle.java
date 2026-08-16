package io.github.digitalsmile.goldberry.css;

import io.github.digitalsmile.goldberry.natives.log.Logs;
import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.Justify;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

/// Every property a node resolved to, typed.
///
/// The end of the CSS pipeline and the start of the rendering one: ADR-0004 gives
/// each render object a `YGNode` and one of these. What arrives is a map of
/// property names to tokens; what leaves is values Yoga and Blend2D can be handed
/// without either of them knowing CSS exists.
///
/// ## The property split
///
/// §8 calls the split a design invariant, and it is visible in the field list:
/// [#direction()], [#justifyContent()], [#width()] and the rest **compile to
/// Yoga**, while [#background()], [#color()] and [#opacity()] are **resolved for
/// paint**. Nothing here does both, and nothing here is a string.
///
/// ## What is not here yet
///
/// §8's full list also has `flex-wrap`, `margin`, `min/max`, `position`, `inset`,
/// `aspect-ratio`, `overflow`, borders, shadows, transforms, transitions and the
/// font properties. They are absent because
/// [io.github.digitalsmile.goldberry.layout.Box] cannot express them yet, and a
/// property that resolves into nothing is a property with no test that means
/// anything. Each arrives with the thing that paints it.
///
/// Immutable, and every field has a default, so a node with no matching rules is
/// still a usable style rather than a null.
public record ComputedStyle(
        // --- layout: compiled to Yoga ---
        FlexDirection direction,
        Justify justifyContent,
        Align alignItems,
        StyleLength width,
        StyleLength height,
        StyleLength padding,
        StyleLength gap,
        double flexGrow,
        // --- paint: resolved into pixels ---
        int background,
        int color,
        double opacity) {

    private static final Logger LOG = Logs.of(ComputedStyle.class);

    /// What a node with no declarations looks like.
    ///
    /// Matches Yoga's own defaults for the layout half and "invisible, black
    /// text, fully opaque" for the paint half — deliberately not Nord, because a
    /// default that is already themed makes a missing stylesheet look like a
    /// working one.
    public static final ComputedStyle INITIAL = new ComputedStyle(
            FlexDirection.ROW,
            Justify.FLEX_START,
            Align.STRETCH,
            StyleLength.UNDEFINED,
            StyleLength.UNDEFINED,
            StyleLength.points(0),
            StyleLength.points(0),
            0,
            CssColor.TRANSPARENT,
            0xFF000000,
            1.0);

    public ComputedStyle {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(justifyContent, "justifyContent");
        Objects.requireNonNull(alignItems, "alignItems");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(padding, "padding");
        Objects.requireNonNull(gap, "gap");
    }

    /// Builds a style from resolved declarations.
    ///
    /// A declaration whose value will not parse is **dropped with a warning**,
    /// not fatal. This runs per node per restyle, inside the frame loop, and the
    /// rest of the node's style is still perfectly good — the same reasoning that
    /// makes an unresolvable `var()` a warning rather than an exception, and the
    /// opposite of the parse-time strictness in [CssSyntaxException].
    ///
    /// @param declarations property name to value tokens, as [StyleResolver]
    ///                     returns them
    /// @param context      what `em` and `rem` resolve against
    public static ComputedStyle of(Map<String, List<Token>> declarations, CssLength.Context context) {
        Objects.requireNonNull(declarations, "declarations");
        Objects.requireNonNull(context, "context");

        var style = INITIAL;
        for (var entry : declarations.entrySet()) {
            style = style.with(entry.getKey(), entry.getValue(), context);
        }
        return style;
    }

    /// One declaration applied, or this style unchanged if it does not apply.
    private ComputedStyle with(String property, List<Token> value, CssLength.Context context) {
        return switch (property) {
            case "flex-direction" -> keyword(value, FlexDirection.class)
                    .map(v -> new ComputedStyle(v, justifyContent, alignItems, width, height,
                            padding, gap, flexGrow, background, color, opacity))
                    .orElseGet(() -> dropped(property, value));

            case "justify-content" -> keyword(value, Justify.class)
                    .map(v -> new ComputedStyle(direction, v, alignItems, width, height,
                            padding, gap, flexGrow, background, color, opacity))
                    .orElseGet(() -> dropped(property, value));

            case "align-items" -> keyword(value, Align.class)
                    .map(v -> new ComputedStyle(direction, justifyContent, v, width, height,
                            padding, gap, flexGrow, background, color, opacity))
                    .orElseGet(() -> dropped(property, value));

            case "width" -> length(value, context)
                    .map(v -> new ComputedStyle(direction, justifyContent, alignItems, v, height,
                            padding, gap, flexGrow, background, color, opacity))
                    .orElseGet(() -> dropped(property, value));

            case "height" -> length(value, context)
                    .map(v -> new ComputedStyle(direction, justifyContent, alignItems, width, v,
                            padding, gap, flexGrow, background, color, opacity))
                    .orElseGet(() -> dropped(property, value));

            case "padding" -> length(value, context)
                    .map(v -> new ComputedStyle(direction, justifyContent, alignItems, width, height,
                            v, gap, flexGrow, background, color, opacity))
                    .orElseGet(() -> dropped(property, value));

            case "gap" -> length(value, context)
                    .map(v -> new ComputedStyle(direction, justifyContent, alignItems, width, height,
                            padding, v, flexGrow, background, color, opacity))
                    .orElseGet(() -> dropped(property, value));

            case "flex-grow" -> number(value)
                    .filter(v -> v >= 0)
                    .map(v -> new ComputedStyle(direction, justifyContent, alignItems, width, height,
                            padding, gap, v, background, color, opacity))
                    .orElseGet(() -> dropped(property, value));

            case "background", "background-color" -> colour(value)
                    .map(v -> new ComputedStyle(direction, justifyContent, alignItems, width, height,
                            padding, gap, flexGrow, v, color, opacity))
                    .orElseGet(() -> dropped(property, value));

            case "color" -> colour(value)
                    .map(v -> new ComputedStyle(direction, justifyContent, alignItems, width, height,
                            padding, gap, flexGrow, background, v, opacity))
                    .orElseGet(() -> dropped(property, value));

            case "opacity" -> number(value)
                    .map(v -> Math.max(0, Math.min(1, v)))
                    .map(v -> new ComputedStyle(direction, justifyContent, alignItems, width, height,
                            padding, gap, flexGrow, background, color, v))
                    .orElseGet(() -> dropped(property, value));

            // Not an error. §8's property list is longer than this record, and a
            // stylesheet naming `box-shadow` before it is implemented should not
            // stop a window opening -- but it is logged, because "my shadow does
            // nothing" needs an answer.
            default -> {
                LOG.debug("ignoring unsupported property \"{}\"", property);
                yield this;
            }
        };
    }

    private ComputedStyle dropped(String property, List<Token> value) {
        LOG.warn("dropping \"{}\": {} is not a valid value", property, text(value));
        return this;
    }

    private static java.util.Optional<Integer> colour(List<Token> value) {
        return java.util.Optional.ofNullable(CssColor.parse(value));
    }

    private static java.util.Optional<StyleLength> length(List<Token> value, CssLength.Context context) {
        return java.util.Optional.ofNullable(CssLength.parse(value, context));
    }

    private static java.util.Optional<Double> number(List<Token> value) {
        return java.util.Optional.ofNullable(CssLength.parseNumber(value));
    }

    /// A CSS keyword mapped onto a Yoga enum by name: `space-between` onto
    /// `SPACE_BETWEEN`, `flex-start` onto `FLEX_START`.
    ///
    /// By name rather than by a hand-written table because the two vocabularies
    /// already agree — Yoga's enums are the CSS names — and a table would be a
    /// second place for them to drift apart.
    private static <E extends Enum<E>> java.util.Optional<E> keyword(List<Token> value, Class<E> type) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1 || !tokens.getFirst().is(TokenType.IDENT)) {
            return java.util.Optional.empty();
        }
        var name = tokens.getFirst().text().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return java.util.Optional.of(Enum.valueOf(type, name));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    private static String text(List<Token> value) {
        var out = new StringBuilder();
        value.forEach(t -> out.append(t.cssText()));
        return out.toString();
    }
}

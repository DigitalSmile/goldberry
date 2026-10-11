package dev.goldberry.css.value;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.calc.CalcParser;
import dev.goldberry.css.value.calc.ContextUnits;
import dev.goldberry.css.value.calc.Folding;
import dev.goldberry.css.value.calc.Quantity;
import dev.goldberry.layout.Length;

/// Reads a CSS length into the [Length] the layout engine takes.
///
/// ```java
/// Length width = CssLength.parse(tokens, CssLength.Context.DEFAULT);
/// ```
///
/// The units are `px`, `%`, `em`, `rem`, `ch`, `ex`, the absolute units (`in`,
/// `cm`, `mm`, `Q`, `pt`, `pc`), the viewport units (`vw`, `vh`, `vmin`, `vmax`
/// and their `s`, `l` and `d` forms) and the keyword `auto`. `px` is a
/// **logical** pixel: the window's scale is applied by the paint context, not
/// here, so a length in a stylesheet means the same thing on a 150% display as
/// on a 100% one.
///
/// `em` and `rem` are resolved at parse time against a font size rather than
/// carried as units into Yoga, because Yoga has no concept of a font size.
/// That makes them relative to the font size *in force where the declaration
/// lands*, which is what CSS says and what a caller must supply.
///
/// Every relative unit is resolved here, when the style is computed, against
/// the [Context]: the font size, the root's, the face's `ch` and `ex`, and the
/// window's size. A sheet that uses a viewport unit is restyled when the window
/// is resized.
///
/// A math function (`calc()`, `min()`, `max()`, `clamp()` and the rest) is
/// folded here too. What it folds to is a length, a percentage, or both at once:
/// `calc(100% - 8px)` is a [Length.Mixed], which a painted property resolves
/// against its box and a layout property refuses, because the layout engine
/// takes a length or a percentage and never the two together.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
public final class CssLength {

    private CssLength() {}

    /// The context a relative length needs.
    ///
    /// Both font sizes are what the **root** resolves against, and each stops
    /// meaning that one step down: `em` becomes the element's own computed size
    /// inside [dev.goldberry.css.ComputedStyle#of], and `rem` becomes the root
    /// element's computed size, which the renderer puts here with
    /// [#withRootFontSize] once the root has resolved.
    ///
    /// @param fontSize     the font size the root's own `em` resolves against
    /// @param rootFontSize the root element's computed font size, for `rem` —
    ///                     until the root has computed one, the configured value
    ///                     its own `font-size` declaration resolves `rem` against
    /// @param viewport     the window's logical size, for `vw` and the rest
    /// @param fontUnits    `ch` and `ex` in the face being styled
    /// @param container the size the container units are a percentage of: the
    ///                  nearest query container's content box, or
    ///                  [Viewport#UNKNOWN] for the window's
    public record Context(
            float fontSize, float rootFontSize, Viewport viewport, FontUnits fontUnits, Viewport container) {

        /// The default before the root has resolved: 16 logical pixels, which is
        /// what every browser and every design system starts from. No window and
        /// no face, so a viewport unit is not a length under it and `ch` is half
        /// an em.
        public static final Context DEFAULT = new Context(16, 16);

        public Context {
            Objects.requireNonNull(viewport, "viewport");
            Objects.requireNonNull(fontUnits, "fontUnits");
            Objects.requireNonNull(container, "container");
        }

        /// A context whose container units are the window's.
        public Context(float fontSize, float rootFontSize, Viewport viewport, FontUnits fontUnits) {
            this(fontSize, rootFontSize, viewport, fontUnits, Viewport.UNKNOWN);
        }

        /// This context with the container units a percentage of `size`.
        public Context withContainer(Viewport size) {
            return size.equals(container) ? this : new Context(fontSize, rootFontSize, viewport, fontUnits, size);
        }

        /// Two font sizes, with no window and no face.
        public Context(float fontSize, float rootFontSize) {
            this(fontSize, rootFontSize, Viewport.UNKNOWN, FontUnits.FALLBACK);
        }

        /// This context with `rem` meaning `size`.
        ///
        /// The one thing the renderer changes about a context per frame, beside
        /// the window. `em` has its own wither, [#withFontSize], for the cascade,
        /// which derives the element's own size per node.
        public Context withRootFontSize(float size) {
            return size == rootFontSize ? this : new Context(fontSize, size, viewport, fontUnits, container);
        }

        /// This context with `em` meaning `size`.
        public Context withFontSize(float size) {
            return size == fontSize ? this : new Context(size, rootFontSize, viewport, fontUnits, container);
        }

        /// This context under a window of `width` by `height` logical pixels.
        public Context withViewport(double width, double height) {
            var next = new Viewport(width, height);
            return next.equals(viewport) ? this : new Context(fontSize, rootFontSize, next, fontUnits, container);
        }

        /// This context measuring `ch` and `ex` with `units`.
        public Context withFontUnits(FontUnits units) {
            return units.equals(fontUnits) ? this : new Context(fontSize, rootFontSize, viewport, units, container);
        }

        /// This context measuring `ch` and `ex` in the face `family` names at
        /// `weight`, from the same source of measures.
        public Context withFace(String family, int weight) {
            return withFontUnits(fontUnits.face(family, weight));
        }
    }

    /// Parses a length.
    ///
    /// @return the length, or null if these tokens are not one
    public static @Nullable Length parse(List<Token> value, Context context) {
        var math = CalcParser.parse(value);
        if (math != null) {
            return length(Folding.fold(math, ContextUnits.of(context)));
        }
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1) {
            return null;
        }
        var token = tokens.getFirst();

        if (token.isIdent("auto")) {
            return Length.AUTO;
        }
        if (token.is(TokenType.PERCENTAGE)) {
            return Length.percent((float) token.numeric());
        }
        if (token.is(TokenType.NUMBER)) {
            // Unitless zero is the one number CSS accepts as a length, because
            // "0" has no direction to be wrong about. Anything else unitless is
            // an author error worth surfacing rather than guessing px for.
            return token.numeric() == 0 ? Length.points(0) : null;
        }
        if (!token.is(TokenType.DIMENSION)) {
            return null;
        }
        var pixels = pixels(token.numeric(), token.unit(), context);
        return pixels == null ? null : Length.points((float) (double) pixels);
    }

    /// A folded math expression as a length: pixels, a percentage, or both.
    private static @Nullable Length length(@Nullable Quantity quantity) {
        if (quantity == null || quantity.kind() != Quantity.Kind.LENGTH) {
            return null;
        }
        if (!quantity.hasPercent()) {
            return Length.points((float) quantity.value());
        }
        return quantity.isPercentOnly()
                ? Length.percent((float) quantity.percent())
                : Length.mixed((float) quantity.value(), (float) quantity.percent());
    }

    /// `value` in any length unit, relative or absolute, in logical pixels under
    /// `context`, or null when `unit` is not a length unit, or is a viewport unit
    /// and no window has said how big it is.
    public static @Nullable Double pixels(double value, String unit, Context context) {
        var absolute = absolute(value, unit);
        if (absolute != null) {
            return absolute;
        }
        var lower = unit.toLowerCase(Locale.ROOT);
        if (Viewport.isUnit(lower)) {
            return context.viewport().isKnown() ? context.viewport().resolve(value, lower) : null;
        }
        if (isContainerUnit(lower)) {
            // The nearest container's size on each axis, or the window's where
            // it has none -- an `inline-size` container has no block axis -- as
            // CSS falls back to the small viewport.
            var width = Double.isFinite(context.container().width())
                    ? context.container().width()
                    : context.viewport().width();
            var height = Double.isFinite(context.container().height())
                    ? context.container().height()
                    : context.viewport().height();
            var basis = switch (lower) {
                case "cqw", "cqi" -> width;
                case "cqh", "cqb" -> height;
                case "cqmin" -> Math.min(width, height);
                default -> Math.max(width, height);
            };
            return Double.isFinite(basis) ? value * basis / 100 : null;
        }
        return switch (lower) {
            case "em" -> value * context.fontSize();
            case "rem" -> value * context.rootFontSize();
            case "ch" -> value * context.fontUnits().ch() * context.fontSize();
            case "ex" -> value * context.fontUnits().ex() * context.fontSize();
            default -> null;
        };
    }

    /// Whether `unit` is a container unit: `cqw`, `cqh`, `cqi`, `cqb`,
    /// `cqmin` or `cqmax`.
    ///
    /// @param unit lowercased
    public static boolean isContainerUnit(String unit) {
        return switch (unit) {
            case "cqw", "cqh", "cqi", "cqb", "cqmin", "cqmax" -> true;
            default -> false;
        };
    }

    /// `value` in an angle unit, in degrees, or null when `unit` is not one.
    public static @Nullable Double degrees(double value, String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "deg" -> value;
            case "rad" -> Math.toDegrees(value);
            case "grad" -> value * 0.9;
            case "turn" -> value * 360;
            default -> null;
        };
    }

    /// `value` in an absolute unit, in logical pixels, or null when `unit` is
    /// not one.
    ///
    /// CSS's own fixed ratios, anchored at 96 pixels to the inch: a point is
    /// 1/72 of an inch, a pica 12 points, and the metric units follow from 2.54
    /// centimetres to the inch. A logical pixel is CSS's pixel, so `1in` is 96
    /// of them on any display.
    public static @Nullable Double absolute(double value, String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "px" -> value;
            case "in" -> value * 96;
            case "pt" -> value * 96 / 72;
            case "pc" -> value * 16;
            case "cm" -> value * 96 / 2.54;
            case "mm" -> value * 96 / 25.4;
            case "q" -> value * 96 / 101.6;
            default -> null;
        };
    }

    /// Parses a plain number — `flex-grow: 1`, `opacity: 0.5`, `calc(1 / 3)`.
    ///
    /// A percentage is accepted for `opacity`, where CSS allows both spellings,
    /// and divided into the 0..1 the property means.
    ///
    /// @return the number, or null if these tokens are not one
    public static @Nullable Double parseNumber(List<Token> value) {
        var math = CalcParser.parse(value);
        if (math != null) {
            // Only a number's own units can be in it, so any context reads it.
            var folded = Folding.fold(math, ContextUnits.of(Context.DEFAULT));
            if (folded == null) {
                return null;
            }
            return switch (folded.kind()) {
                case NUMBER -> folded.value();
                case LENGTH -> folded.isPercentOnly() ? folded.percent() / 100 : null;
                case ANGLE, TIME -> null;
            };
        }
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1) {
            return null;
        }
        var token = tokens.getFirst();
        if (token.is(TokenType.NUMBER)) {
            return token.numeric();
        }
        if (token.is(TokenType.PERCENTAGE)) {
            return token.numeric() / 100;
        }
        return null;
    }
}

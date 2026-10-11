package dev.goldberry.css.value.calc;

import java.util.Objects;

import dev.goldberry.css.value.CssLength;

/// What a dimension in a math expression is worth under a length context: a
/// length in any unit [CssLength#pixels] reads, an angle, or a time.
///
/// ```java
/// Quantity q = Folding.fold(tree, ContextUnits.of(context));
/// ```
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
public final class ContextUnits {

    private ContextUnits() {}

    /// The units under `context`.
    public static Folding.Units of(CssLength.Context context) {
        Objects.requireNonNull(context, "context");
        return (value, unit) -> {
            var pixels = CssLength.pixels(value, unit, context);
            if (pixels != null) {
                return Quantity.pixels(pixels);
            }
            var degrees = CssLength.degrees(value, unit);
            if (degrees != null) {
                return Quantity.degrees(degrees);
            }
            return switch (unit) {
                case "ms" -> Quantity.millis(value);
                case "s" -> Quantity.millis(value * 1000);
                default -> null;
            };
        };
    }
}

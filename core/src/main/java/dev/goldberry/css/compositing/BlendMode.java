package dev.goldberry.css.compositing;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// How a box's pixels combine with what is already drawn under it: CSS's
/// `mix-blend-mode`.
///
/// The separable modes, which the rasterizer has as compositing operators, and
/// `plus-lighter`. `hue`, `saturation`, `color` and `luminosity` mix colour
/// channels across each other, which no operator here does, and are refused
/// with a reason.
public enum BlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    DARKEN,
    LIGHTEN,
    COLOR_DODGE,
    COLOR_BURN,
    HARD_LIGHT,
    SOFT_LIGHT,
    DIFFERENCE,
    EXCLUSION,
    PLUS_LIGHTER;

    /// The keyword as CSS writes it.
    public String cssName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /// The mode a keyword names, or null for one outside this set.
    public static @Nullable BlendMode parse(String name) {
        for (var mode : values()) {
            if (mode.cssName().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return null;
    }

    /// Whether `name` is one of CSS's non-separable modes, which are refused
    /// rather than unknown.
    public static boolean isNonSeparable(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "hue", "saturation", "color", "luminosity" -> true;
            default -> false;
        };
    }
}

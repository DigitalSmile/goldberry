package dev.goldberry.css.container;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// Whether a box answers container queries, and along which axes: CSS's
/// `container-type`.
public enum ContainerType {

    /// Not a container. The initial value.
    NORMAL,

    /// A container for both axes: `width`, `height` and the rest.
    SIZE,

    /// A container for the inline axis only: `width` and `inline-size`.
    INLINE_SIZE;

    /// The keyword, or null: `normal`, `size` or `inline-size`.
    public static @Nullable ContainerType parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "normal" -> NORMAL;
            case "size" -> SIZE;
            case "inline-size" -> INLINE_SIZE;
            default -> null;
        };
    }
}

package dev.goldberry.mic.view.draw;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// Which way a level meter fills: left to right, or bottom to top.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public enum MeterOrientation {

    /// Fills from the left edge to the right.
    HORIZONTAL,

    /// Fills from the bottom edge to the top, as a mixer's channel strip does.
    VERTICAL;

    /// The orientation markup names, `horizontal` or `vertical`; nothing named
    /// is horizontal.
    ///
    /// @throws IllegalArgumentException for any other name
    public static MeterOrientation named(@Nullable String name) {
        if (name == null) {
            return HORIZONTAL;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "horizontal" -> HORIZONTAL;
            case "vertical" -> VERTICAL;
            default ->
                throw new IllegalArgumentException("orientation is horizontal or vertical, not \"" + name + "\"");
        };
    }
}

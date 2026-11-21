package dev.goldberry.mic.view.draw;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// How a spectrum is drawn: a bar per band, or a line through them.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public enum SpectrumShape {

    /// A bar per band, rising from the bottom edge.
    BARS,

    /// A line through the bands' tops, with the area beneath it shaded.
    LINE;

    /// The shape markup names, `bars` or `line`; nothing named is bars.
    ///
    /// @throws IllegalArgumentException for any other name
    public static SpectrumShape named(@Nullable String name) {
        if (name == null) {
            return BARS;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "bars" -> BARS;
            case "line" -> LINE;
            default -> throw new IllegalArgumentException("shape is bars or line, not \"" + name + "\"");
        };
    }
}

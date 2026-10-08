package dev.goldberry.css.background;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// Whether a picture in a box's background is tiled — CSS's
/// `background-repeat`.
///
/// ```css
/// .panel { background-image: url("classpath:/ui/leather.png"); background-repeat: repeat }
/// .crest { background-image: url("classpath:/ui/crest.png"); background-repeat: no-repeat }
/// ```
///
/// A tiled picture starts where `background-position` puts it and covers the
/// box in both directions from there. A gradient is the size of its box, so
/// this changes nothing about one.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
public enum BackgroundRepeat {

    /// Tiled across and down: CSS's initial value.
    REPEAT(true, true),

    /// Tiled across only.
    REPEAT_X(true, false),

    /// Tiled down only.
    REPEAT_Y(false, true),

    /// Drawn once.
    NO_REPEAT(false, false);

    private final boolean across;
    private final boolean down;

    BackgroundRepeat(boolean across, boolean down) {
        this.across = across;
        this.down = down;
    }

    /// Whether the picture is tiled across the box.
    public boolean across() {
        return across;
    }

    /// Whether the picture is tiled down the box.
    public boolean down() {
        return down;
    }

    /// The value tiled across as `across` says and down as `down` says.
    public static BackgroundRepeat of(boolean across, boolean down) {
        return across ? (down ? REPEAT : REPEAT_X) : (down ? REPEAT_Y : NO_REPEAT);
    }

    /// A keyword as CSS spells it, or null: `repeat`, `no-repeat`, `repeat-x`,
    /// `repeat-y`.
    public static @Nullable BackgroundRepeat named(String keyword) {
        return switch (keyword.toLowerCase(Locale.ROOT)) {
            case "repeat" -> REPEAT;
            case "no-repeat" -> NO_REPEAT;
            case "repeat-x" -> REPEAT_X;
            case "repeat-y" -> REPEAT_Y;
            default -> null;
        };
    }

    /// The keyword CSS writes for this value.
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}

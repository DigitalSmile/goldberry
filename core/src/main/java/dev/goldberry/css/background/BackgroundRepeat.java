package dev.goldberry.css.background;

import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// How a picture in a box's background is tiled — CSS's `background-repeat`,
/// an answer for each axis.
///
/// ```css
/// .panel { background-image: url("classpath:/ui/leather.png"); background-repeat: repeat }
/// .crest { background-image: url("classpath:/ui/crest.png"); background-repeat: no-repeat }
/// .studs { background-image: url("classpath:/ui/stud.png"); background-repeat: space round }
/// ```
///
/// A tiled picture starts where `background-position` puts it and covers the
/// box in both directions from there. `space` fits as many whole tiles as the
/// box holds and shares the room left over between them; `round` stretches the
/// tile until a whole number of them fit. A gradient is the size of its box,
/// so this changes nothing about one.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
///
/// @param x how the picture is tiled across
/// @param y how it is tiled down
public record BackgroundRepeat(Tiling x, Tiling y) {

    /// Tiled across and down: CSS's initial value.
    public static final BackgroundRepeat REPEAT = new BackgroundRepeat(Tiling.REPEAT, Tiling.REPEAT);

    /// Tiled across only.
    public static final BackgroundRepeat REPEAT_X = new BackgroundRepeat(Tiling.REPEAT, Tiling.NO_REPEAT);

    /// Tiled down only.
    public static final BackgroundRepeat REPEAT_Y = new BackgroundRepeat(Tiling.NO_REPEAT, Tiling.REPEAT);

    /// Drawn once.
    public static final BackgroundRepeat NO_REPEAT = new BackgroundRepeat(Tiling.NO_REPEAT, Tiling.NO_REPEAT);

    /// How one axis is tiled.
    public enum Tiling {
        /// As many tiles as cover the box, the last ones cut.
        REPEAT,
        /// Once.
        NO_REPEAT,
        /// As many whole tiles as fit, the room left over shared between them.
        SPACE,
        /// The tile stretched until a whole number of them fit.
        ROUND;

        /// The keyword CSS writes for this value.
        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public BackgroundRepeat {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
    }

    /// Whether the picture is drawn more than once across the box.
    public boolean across() {
        return x != Tiling.NO_REPEAT;
    }

    /// Whether the picture is drawn more than once down the box.
    public boolean down() {
        return y != Tiling.NO_REPEAT;
    }

    /// The value tiled across as `across` says and down as `down` says.
    public static BackgroundRepeat of(boolean across, boolean down) {
        return new BackgroundRepeat(across ? Tiling.REPEAT : Tiling.NO_REPEAT, down ? Tiling.REPEAT : Tiling.NO_REPEAT);
    }

    /// A single keyword as CSS spells it, or null: `repeat`, `no-repeat`,
    /// `repeat-x`, `repeat-y`, `space`, `round`.
    public static @Nullable BackgroundRepeat named(String keyword) {
        return switch (keyword.toLowerCase(Locale.ROOT)) {
            case "repeat-x" -> REPEAT_X;
            case "repeat-y" -> REPEAT_Y;
            default -> {
                var tiling = tiling(keyword);
                yield tiling == null ? null : new BackgroundRepeat(tiling, tiling);
            }
        };
    }

    /// One axis's keyword, or null: `repeat`, `no-repeat`, `space`, `round`.
    public static @Nullable Tiling tiling(String keyword) {
        return switch (keyword.toLowerCase(Locale.ROOT)) {
            case "repeat" -> Tiling.REPEAT;
            case "no-repeat" -> Tiling.NO_REPEAT;
            case "space" -> Tiling.SPACE;
            case "round" -> Tiling.ROUND;
            default -> null;
        };
    }

    /// The value as CSS writes it, as short as it goes.
    @Override
    public String toString() {
        if (x == y) {
            return x.toString();
        }
        if (x == Tiling.REPEAT && y == Tiling.NO_REPEAT) {
            return "repeat-x";
        }
        if (x == Tiling.NO_REPEAT && y == Tiling.REPEAT) {
            return "repeat-y";
        }
        return x + " " + y;
    }
}

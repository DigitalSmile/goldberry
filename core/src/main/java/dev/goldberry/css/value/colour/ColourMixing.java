package dev.goldberry.css.value.colour;

import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// Two colours mixed in a colour space, as CSS Color 4 interpolates them: what
/// a gradient `in oklch` draws between its stops, and what `color-mix()` is.
///
/// ```java
/// int halfway = ColourMixing.mix(red, blue, 0.5, ColourSpace.OKLCH, HueMethod.SHORTER);
/// ```
///
/// Premultiplied, so a transparent end fades the other's colour rather than
/// dragging it through the transparent one's. A hue is never premultiplied; a
/// grey's hue is powerless and takes the other colour's, so a fade from grey
/// to blue stays on blue's hue instead of passing through red.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#colour).
public final class ColourMixing {

    private ColourMixing() {}

    /// Which way round a hue goes from one colour to another.
    public enum HueMethod {
        /// The short way, less than half a turn. The default.
        SHORTER,
        /// The long way, more than half a turn.
        LONGER,
        /// Always upwards.
        INCREASING,
        /// Always downwards.
        DECREASING;

        /// The method a CSS keyword before `hue` names, or null.
        public static @Nullable HueMethod parse(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "shorter" -> SHORTER;
                case "longer" -> LONGER;
                case "increasing" -> INCREASING;
                case "decreasing" -> DECREASING;
                default -> null;
            };
        }
    }

    /// `from` and `to` mixed `t` of the way from one to the other in `space`.
    ///
    /// @param t 0 for `from`, 1 for `to`
    public static int mix(int from, int to, double t, ColourSpace space, HueMethod hue) {
        Objects.requireNonNull(space, "space");
        Objects.requireNonNull(hue, "hue");
        var fromAlpha = (from >>> 24) / 255.0;
        var toAlpha = (to >>> 24) / 255.0;
        var alpha = fromAlpha + (toAlpha - fromAlpha) * t;
        if (alpha <= 0) {
            return 0;
        }
        var a = space.coordinates(from);
        var b = space.coordinates(to);
        var hueIndex = space.hue();
        if (hueIndex >= 0) {
            // A powerless hue takes the other's; both powerless, neither moves.
            if (space.isAchromatic(a) && !space.isAchromatic(b)) {
                a[hueIndex] = b[hueIndex];
            } else if (space.isAchromatic(b) && !space.isAchromatic(a)) {
                b[hueIndex] = a[hueIndex];
            }
            b[hueIndex] = a[hueIndex] + turned(b[hueIndex] - a[hueIndex], hue);
        }
        var mixed = new double[3];
        for (var i = 0; i < 3; i++) {
            if (i == hueIndex) {
                mixed[i] = a[i] + (b[i] - a[i]) * t;
            } else {
                var premultiplied = a[i] * fromAlpha + (b[i] * toAlpha - a[i] * fromAlpha) * t;
                mixed[i] = premultiplied / alpha;
            }
        }
        return space.argb(mixed, alpha);
    }

    /// The hue difference `delta`, in degrees, taken the way `method` says.
    static double turned(double delta, HueMethod method) {
        var d = delta % 360;
        return switch (method) {
            case SHORTER -> d > 180 ? d - 360 : d < -180 ? d + 360 : d;
            case LONGER -> {
                if (d > 0 && d < 180) {
                    yield d - 360;
                }
                if (d < 0 && d > -180) {
                    yield d + 360;
                }
                yield d;
            }
            case INCREASING -> d < 0 ? d + 360 : d;
            case DECREASING -> d > 0 ? d - 360 : d;
        };
    }
}

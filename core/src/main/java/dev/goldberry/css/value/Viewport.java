package dev.goldberry.css.value;

/// The window's logical size, which the viewport units are a percentage of.
///
/// ```java
/// var viewport = new Viewport(1280, 800); // 50vw is 640, 10vmin is 80
/// ```
///
/// A desktop window has one size, so `svh`, `lvh` and `dvh` all mean `vh`
/// here, and their `w` forms `vw`: the small, large and dynamic viewports differ
/// only where a browser's toolbars come and go.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
///
/// @param width  the window's logical width, or NaN when nothing has said
/// @param height the window's logical height, or NaN likewise
public record Viewport(double width, double height) {

    /// No window yet. A viewport unit read against it is not a length, and the
    /// declaration is dropped until a window says how big it is.
    public static final Viewport UNKNOWN = new Viewport(Double.NaN, Double.NaN);

    /// Whether a window has said how big it is.
    public boolean isKnown() {
        return Double.isFinite(width) && Double.isFinite(height);
    }

    /// Whether `unit` is a viewport unit.
    ///
    /// @param unit lowercased
    public static boolean isUnit(String unit) {
        return switch (unit) {
            case "vw",
                    "vh",
                    "vmin",
                    "vmax",
                    "svw",
                    "svh",
                    "lvw",
                    "lvh",
                    "dvw",
                    "dvh",
                    "vi",
                    "vb",
                    "svmin",
                    "svmax",
                    "lvmin",
                    "lvmax",
                    "dvmin",
                    "dvmax" -> true;
            default -> false;
        };
    }

    /// `value` of `unit`, in logical pixels: `50vw` is half the width. NaN when
    /// this viewport is not known.
    ///
    /// `vi` is the inline axis and `vb` the block axis, which with one writing
    /// mode are the width and the height.
    ///
    /// @param unit lowercased, one [#isUnit] accepts
    /// @throws IllegalArgumentException if `unit` is not a viewport unit
    public double resolve(double value, String unit) {
        var basis = switch (unit) {
            case "vw", "svw", "lvw", "dvw", "vi" -> width;
            case "vh", "svh", "lvh", "dvh", "vb" -> height;
            case "vmin", "svmin", "lvmin", "dvmin" -> Math.min(width, height);
            case "vmax", "svmax", "lvmax", "dvmax" -> Math.max(width, height);
            default -> throw new IllegalArgumentException(unit + " is not a viewport unit");
        };
        return value * basis / 100;
    }
}

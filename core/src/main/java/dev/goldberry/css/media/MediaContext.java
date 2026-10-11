package dev.goldberry.css.media;

import java.util.Objects;

import dev.goldberry.render.desktop.SystemTheme;

/// What an `@media` condition is asked about: the window's size and density,
/// the pointer the user has, and the desktop's preferences.
///
/// ```java
/// var context = MediaContext.UNKNOWN.size(1280, 800).colorScheme(SystemTheme.DARK).resolution(2);
/// ```
///
/// The renderer keeps one and tells its resolver when it changes: the window's
/// logical size and its scale on every frame, the desktop's theme when it says
/// it changed, and the rest when it is built. A condition that answers
/// differently under the new context is what makes the cascade run again; a
/// resize that crosses no breakpoint costs nothing.
///
/// Read more: [Media queries](https://goldberry.dev/docs/guide/styling.html#media-queries).
///
/// @param width         the window's logical width, or NaN when nothing has
///                      said, under which no width condition holds
/// @param height        the window's logical height, or NaN likewise
/// @param colorScheme   what `prefers-color-scheme` answers: the desktop's
///                      theme, and light where the desktop does not say
/// @param reducedMotion what `prefers-reduced-motion: reduce` answers
/// @param resolution    device pixels per logical pixel, `resolution`'s `dppx`;
///                      NaN when nothing has said
/// @param input         what `pointer`, `hover` and their `any-` forms answer
/// @param preferences   what `prefers-contrast`, `forced-colors` and
///                      `prefers-reduced-transparency` answer
public record MediaContext(
        double width,
        double height,
        SystemTheme colorScheme,
        boolean reducedMotion,
        double resolution,
        Input input,
        Preferences preferences) {

    /// No window yet, a light desktop with no preferences, full motion, and a
    /// mouse: what a renderer starts from before the first frame tells it its
    /// size.
    public static final MediaContext UNKNOWN = new MediaContext(
            Double.NaN, Double.NaN, SystemTheme.LIGHT, false, Double.NaN, Input.DESKTOP, Preferences.NONE);

    public MediaContext {
        Objects.requireNonNull(colorScheme, "colorScheme");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(preferences, "preferences");
    }

    /// A window's size, theme and motion, on a desktop with a mouse and no
    /// other preference.
    public MediaContext(double width, double height, SystemTheme colorScheme, boolean reducedMotion) {
        this(width, height, colorScheme, reducedMotion, Double.NaN, Input.DESKTOP, Preferences.NONE);
    }

    /// This, at a different window size.
    public MediaContext size(double width, double height) {
        return new MediaContext(width, height, colorScheme, reducedMotion, resolution, input, preferences);
    }

    /// This, under a different desktop theme.
    public MediaContext colorScheme(SystemTheme value) {
        return new MediaContext(width, height, value, reducedMotion, resolution, input, preferences);
    }

    /// This, with motion reduced or not.
    public MediaContext reducedMotion(boolean value) {
        return new MediaContext(width, height, colorScheme, value, resolution, input, preferences);
    }

    /// This, at `dppx` device pixels per logical pixel.
    public MediaContext resolution(double dppx) {
        return new MediaContext(width, height, colorScheme, reducedMotion, dppx, input, preferences);
    }

    /// This, with other pointing devices.
    public MediaContext input(Input value) {
        return new MediaContext(width, height, colorScheme, reducedMotion, resolution, value, preferences);
    }

    /// This, with other desktop preferences.
    public MediaContext preferences(Preferences value) {
        return new MediaContext(width, height, colorScheme, reducedMotion, resolution, input, value);
    }

    /// How precise a pointer is: `pointer` and `any-pointer`.
    public enum Pointer {
        /// No pointing device.
        NONE,
        /// A finger, or anything as imprecise.
        COARSE,
        /// A mouse, a touchpad, a pen.
        FINE
    }

    /// The pointing devices: the primary one, and whether any device at all is
    /// coarse or fine, and hovers.
    ///
    /// @param pointer  the primary device's precision
    /// @param hover    whether the primary device can hover
    /// @param anyFine  whether any device is fine
    /// @param anyCoarse whether any device is coarse
    /// @param anyHover whether any device can hover
    public record Input(Pointer pointer, boolean hover, boolean anyFine, boolean anyCoarse, boolean anyHover) {

        /// A mouse: fine, hovering, and nothing else. What a desktop has.
        public static final Input DESKTOP = new Input(Pointer.FINE, true, true, false, true);

        /// A touch screen alone: coarse, and nothing hovers.
        public static final Input TOUCH = new Input(Pointer.COARSE, false, false, true, false);

        public Input {
            Objects.requireNonNull(pointer, "pointer");
        }
    }

    /// How much contrast the desktop asks for: `prefers-contrast`.
    public enum Contrast {
        /// No preference, which is also what a desktop that does not say gets.
        NO_PREFERENCE,
        /// More contrast.
        MORE,
        /// Less contrast.
        LESS
    }

    /// The desktop's display preferences.
    ///
    /// @param contrast            `prefers-contrast`
    /// @param forcedColors        whether the desktop forces its own colours,
    ///                            Windows' high-contrast mode: `forced-colors`
    /// @param reducedTransparency `prefers-reduced-transparency: reduce`
    public record Preferences(Contrast contrast, boolean forcedColors, boolean reducedTransparency) {

        /// No preference at all.
        public static final Preferences NONE = new Preferences(Contrast.NO_PREFERENCE, false, false);

        public Preferences {
            Objects.requireNonNull(contrast, "contrast");
        }
    }
}

package dev.goldberry.css.media;

import java.util.Objects;

import dev.goldberry.render.desktop.SystemTheme;

/// What an `@media` condition is asked about: the window's size and the
/// desktop's two preferences.
///
/// ```java
/// var context = MediaContext.UNKNOWN.size(1280, 800).colorScheme(SystemTheme.DARK);
/// ```
///
/// The renderer keeps one and tells its resolver when it changes: the window's
/// logical size on every frame, the desktop's theme when it says it changed,
/// and reduced motion when the renderer is told to reduce. A condition that
/// answers differently under the new context is what makes the cascade run
/// again; a resize that crosses no breakpoint costs nothing.
///
/// Read more: [Media queries](https://goldberry.dev/docs/guide/styling.html#media-queries).
///
/// @param width         the window's logical width, or NaN when nothing has
///                      said, under which no width condition holds
/// @param height        the window's logical height, or NaN likewise
/// @param colorScheme   what `prefers-color-scheme` answers: the desktop's
///                      theme, and light where the desktop does not say
/// @param reducedMotion what `prefers-reduced-motion: reduce` answers
public record MediaContext(double width, double height, SystemTheme colorScheme, boolean reducedMotion) {

    /// No window yet, a light desktop and full motion: what a renderer starts
    /// from before the first frame tells it its size.
    public static final MediaContext UNKNOWN = new MediaContext(Double.NaN, Double.NaN, SystemTheme.LIGHT, false);

    public MediaContext {
        Objects.requireNonNull(colorScheme, "colorScheme");
    }

    /// This, at a different window size.
    public MediaContext size(double width, double height) {
        return new MediaContext(width, height, colorScheme, reducedMotion);
    }

    /// This, under a different desktop theme.
    public MediaContext colorScheme(SystemTheme value) {
        return new MediaContext(width, height, value, reducedMotion);
    }

    /// This, with motion reduced or not.
    public MediaContext reducedMotion(boolean value) {
        return new MediaContext(width, height, colorScheme, value);
    }
}

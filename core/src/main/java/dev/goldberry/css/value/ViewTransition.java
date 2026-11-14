package dev.goldberry.css.value;

import java.util.Objects;

import dev.goldberry.motion.Easing;

/// `view-transition-name`, and how long a box so named takes to move when
/// layout moves it.
///
/// ```css
/// call-tile { view-transition-name: tile; transition: transform 300ms ease-out }
/// ```
///
/// A named box that layout puts somewhere else, or makes another size, is not
/// drawn there at once: it is drawn where it was, and moves and stretches to
/// where it is now. The name is its identity across frames, so a box that moves
/// to another parent still finds where it was, as long as the name is one no
/// other box has.
///
/// The timing is the box's own `transition` for `transform` when it declares
/// one, since what moves is a transform, and View Transitions' default
/// otherwise: 250 milliseconds, `ease`. Reduced motion makes it instant.
///
/// The cascade carries the name. [dev.goldberry.widget.WidgetRenderer] puts
/// the timing on it when it builds the box, because only it knows the box's
/// transitions and whether motion is reduced.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#transform).
///
/// @param name           the identity, unique among the boxes on screen
/// @param durationMillis how long a move takes; zero snaps
/// @param easing         the curve it takes
public record ViewTransition(String name, double durationMillis, Easing easing) {

    /// View Transitions' own duration.
    public static final double DEFAULT_DURATION_MILLIS = 250;

    /// View Transitions' own curve, CSS's `ease`.
    public static final Easing DEFAULT_EASING =
            Objects.requireNonNull(Easing.parse("ease"), "CSS's ease is a built-in curve");

    public ViewTransition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(easing, "easing");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a view transition name is not blank");
        }
        if (!Double.isFinite(durationMillis) || durationMillis < 0) {
            throw new IllegalArgumentException(
                    "a duration is a non-negative number of milliseconds, not " + durationMillis);
        }
    }

    /// `name`, at the default timing.
    public static ViewTransition named(String name) {
        return new ViewTransition(name, DEFAULT_DURATION_MILLIS, DEFAULT_EASING);
    }

    /// This name, moving over `durationMillis` along `easing`.
    public ViewTransition timed(double durationMillis, Easing easing) {
        return new ViewTransition(name, durationMillis, easing);
    }

    /// Whether a move snaps rather than travels.
    public boolean isInstant() {
        return durationMillis == 0;
    }
}

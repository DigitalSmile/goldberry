package dev.goldberry.render.display;

import java.util.Objects;

import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalRect;

/// One display connected to the desktop, as the platform describes it.
///
/// Every rectangle is in the desktop's coordinates: the space a window's
/// [dev.goldberry.Window#position()] is in, where the primary display's
/// top-left is usually the origin and a display to its left has a negative
/// `x`.
///
/// ```java
/// for (var display : host.displays()) {
///     System.out.println(display.name() + " " + display.bounds());
/// }
/// ```
///
/// Read more: [Displays](https://goldberry.dev/docs/guide/windows.html#displays).
///
/// @param id           the platform's number for it, good **for this run
///                     only**: a display unplugged and plugged back gets a new
///                     one. Remember a display by [#name] between runs.
/// @param name         the name the platform gives it — a monitor's model, as a
///                     rule — or empty where it gives none
/// @param bounds       its whole extent
/// @param usableBounds the part a window may usefully occupy: the bounds less a
///                     taskbar, a dock or a panel
/// @param scale        the scale a window on it is drawn at
/// @param primary      whether it is the display the desktop calls primary
public record Display(
        long id, String name, LogicalRect bounds, LogicalRect usableBounds, DisplayScale scale, boolean primary) {

    public Display {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(usableBounds, "usableBounds");
        Objects.requireNonNull(scale, "scale");
    }
}

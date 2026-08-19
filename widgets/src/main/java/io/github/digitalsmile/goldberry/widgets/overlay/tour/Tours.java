package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import java.util.List;
import java.util.Objects;

/// Starting a tour — the half of §5's `tour` that needs a [Host].
///
/// A tour is declared as a list of [Stop]s and *started* by a call, exactly as a
/// `menu` is declared and opened by one
/// ([ADR-0106](../../../../../../../../book/src/adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md)):
/// both need to resolve a target id against the painted frame and to put
/// something on the window, and neither is a thing a widget tree can do to
/// itself.
///
/// ```java
/// Tours.start(host, List.of(
///         new Stop("save-button", "Saving", "Everything is written here."),
///         new Stop("theme-picker", "Themes", "And the whole window follows.")));
/// ```
public final class Tours {

    private Tours() {
    }

    /// Starts `stops` over `host`'s window, returning the overlay so a caller can
    /// end it early.
    ///
    /// A tour with no stops is not started, and that is deliberately not an
    /// error: a tour assembled from a filtered list is empty exactly when nothing
    /// in it applies, and throwing would make "nothing to show you" a crash.
    public static Overlay start(Host host, List<Stop> stops) {
        return start(host, stops, () -> { });
    }

    /// The same, with something to run when the tour ends — however it ends.
    ///
    /// Called for a tour that was finished, one that was skipped and one that ran
    /// out of stops it could find. An application that wants to know *how* it
    /// ended wants a different API than a tour; what this is for is putting the
    /// "don't show me again" flag away.
    public static Overlay start(Host host, List<Stop> stops, Runnable onEnd) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(stops, "stops");
        Objects.requireNonNull(onEnd, "onEnd");
        if (stops.isEmpty()) {
            onEnd.run();
            return null;
        }
        // A holder, because the widget needs to remove the overlay that does not
        // exist until the widget has been built -- the same knot `Menus` unties
        // the same way.
        var handle = new Overlay[1];
        var tour = new Tour(List.copyOf(stops), host, () -> {
            if (handle[0] != null) {
                handle[0].remove();
            }
            onEnd.run();
        });
        handle[0] = host.fill(tour);
        return handle[0];
    }
}

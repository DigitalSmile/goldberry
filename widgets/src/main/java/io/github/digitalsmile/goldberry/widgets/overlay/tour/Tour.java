package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;

/// A guided sequence of popovers over real widgets — `docs/core-widgets.md` §5.
///
/// Started through [Tours#start], which is where the [Host] comes from.
///
/// ## What it is made of
///
/// ```
/// tour              fills the window, holds which stop is showing
/// ├── tour-veil     four bands around the target, dimming everything else
/// └── tour-stop     the popover: title, body, Back/Next/Skip
/// ```
///
/// The veil is **four rectangles rather than one with a hole**, because §8's
/// subset has no path and no mask, and four boxes need neither
/// ([ADR-0121](../../../../../../../../book/src/adr/0121-a-tour-is-a-veil-and-a-sequence.md)).
/// It is also why the target stays interactive: nothing covers it, so a tour that
/// says "click here" can be obeyed without the tour having to arrange an
/// exception to itself.
///
/// ## The target is read every frame
///
/// A stop names a widget by id and the anchor is resolved from the painted frame
/// on **every build**, not once when the stop opens. A window that resizes, a
/// list that scrolls, a panel that reflows — all of them move the thing being
/// described, and a veil cut where the widget used to be is worse than no veil.
///
/// A target that is not in the tree is **skipped with a warning** rather than
/// throwing, which §5 asks for outright: a tour is documentation, and
/// documentation going stale must not take the window down.
///
/// @param stops  the sequence, in order
/// @param host   the window this is running over
/// @param onEnd  what to run when the tour finishes, is skipped, or runs out of
///               targets it can find
public record Tour(List<Stop> stops, Host host, Runnable onEnd) implements Widget.Stateful {

    public Tour {
        stops = List.copyOf(stops == null ? List.of() : stops);
    }

    @Override
    public State<?> createState() {
        return new TourState();
    }
}

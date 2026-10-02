package dev.goldberry.widgets.overlay.tour;

import org.jspecify.annotations.Nullable;

import dev.goldberry.widgets.core.scroll.ScrollController;

/// One step of a [Tour]: a target named by id, a title and a body, shown on a
/// card with Back, Next and Skip.
///
/// The target is named rather than referenced, for a context menu's reason:
/// an application holds ids, not elements, and a tour is usually written far away
/// from the widgets it describes — often in a different file, and often before
/// they exist.
///
/// ## The viewport is usually nobody's business but the tour's
///
/// A tour scrolls its target into view, and finds the viewport to move by
/// walking up from the *target*, which
/// [dev.goldberry.widgets.core.scroll.ScrollScope] does. So leaving [#scroll]
/// null is the ordinary case, and [#within] is for
/// the application that means a **different** viewport from the innermost one.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#tours).
///
/// @param targetId the `id=` of the widget this stop is about
/// @param title    the heading of the popover
/// @param body     what it says
/// @param scroll   the viewport to move, or null to use whichever one encloses
///                 the target — which is what almost every stop wants
public record Stop(
        String targetId,
        String title,
        String body,
        @Nullable ScrollController scroll) {

    /// Written out so that the parameters taking null for a default can say so.
    public Stop(String targetId, @Nullable String title, @Nullable String body, @Nullable ScrollController scroll) {
        // Non-null by contract, and checked anyway: a caller without NullAway can
        // pass null, and TourTest holds it to the same refusal as a blank id.
        //noinspection ConstantValue
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException(
                    "a tour stop names the widget it describes; without an id it describes nothing");
        }
        title = title == null ? "" : title;
        body = body == null ? "" : body;
        this.targetId = targetId;
        this.title = title;
        this.body = body;
        this.scroll = scroll;
    }

    /// A stop that lets the tour find the viewport, which is the usual form.
    public Stop(String targetId, String title, String body) {
        this(targetId, title, body, null);
    }

    /// This stop, told which viewport to scroll rather than letting it be found.
    ///
    /// For the application that means an outer viewport: the walk from the target
    /// chooses the innermost one, and a row inside a list inside a page is a case
    /// where the interesting move is the page's.
    public Stop within(ScrollController controller) {
        return new Stop(targetId, title, body, controller);
    }
}

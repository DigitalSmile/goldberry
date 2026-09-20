package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController;

/// One step of a [Tour] — `docs/core-widgets.md` §5: "each stop names a target by
/// id, a title, a body, and Back/Next/Skip".
///
/// The target is named rather than referenced, for `menu`'s reason
/// (ADR-0108):
/// an application holds ids, not elements, and a tour is usually written far away
/// from the widgets it describes — often in a different file, and often before
/// they exist.
///
/// ## The viewport is usually nobody's business but the tour's
///
/// §5 asks a tour to scroll its target into view, and [#scroll] used to be the
/// only way it could: a tour was told which viewport to move because it had no
/// way of finding out. It finds out now — the walk is up from the *target*, which
/// [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollScope] does
/// ([ADR-0439]) — so leaving it null is the ordinary case and [#within] is for
/// the application that means a **different** viewport from the innermost one.
///
/// @param targetId the `id=` of the widget this stop is about
/// @param title    the heading of the popover
/// @param body     what it says
/// @param scroll   the viewport to move, or null to use whichever one encloses
///                 the target — which is what almost every stop wants
public record Stop(String targetId, String title, String body, ScrollController scroll) {

    public Stop {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException(
                    "a tour stop names the widget it describes; without an id it describes nothing");
        }
        title = title == null ? "" : title;
        body = body == null ? "" : body;
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
